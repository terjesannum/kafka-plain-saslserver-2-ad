package org.navit.common.security.authorization

import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.listener.InMemoryListenerConfig
import com.unboundid.ldap.sdk.OperationType
import kafka.security.auth.Acl
import kafka.security.auth.Operation
import kafka.security.auth.PermissionType
import org.amshove.kluent.`should be`
import org.apache.kafka.common.acl.AclOperation
import org.apache.kafka.common.security.auth.KafkaPrincipal
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*


object NAVAuthorizerSpec : Spek({

    // create read allowance for ldap group
    fun cReadAS(ldapGroup: String) : Set<Acl> {
        return setOf(
                Acl(
                        KafkaPrincipal(KafkaPrincipal.USER_TYPE, ldapGroup),
                        PermissionType.fromString("Allow"),
                        "*",
                        Operation.fromJava(AclOperation.READ)
                )
        )
    }

    // create describe allowance for 2 ldap groups
    fun cDescribeAS(ldapGroup1: String, ldapGroup2: String) : Set<Acl> {
        return setOf(
                Acl(
                        KafkaPrincipal(KafkaPrincipal.USER_TYPE, ldapGroup1),
                        PermissionType.fromString("Allow"),
                        "*",
                        Operation.fromJava(AclOperation.DESCRIBE)
                ),
                Acl(
                        KafkaPrincipal(KafkaPrincipal.USER_TYPE, ldapGroup2),
                        PermissionType.fromString("Allow"),
                        "*",
                        Operation.fromJava(AclOperation.DESCRIBE)
                )
        )
    }

    fun createKP (userName: String): KafkaPrincipal {
        return KafkaPrincipal(KafkaPrincipal.USER_TYPE,userName)
    }

    val imConf = InMemoryDirectoryServerConfig("dc=example,dc=com","dc=adeo,dc=example,dc=com")

    imConf.setListenerConfigs(
            InMemoryListenerConfig.createLDAPConfig("LDAP",11389)
    )
    // must bind before compare, equal to non-anonymous access./
    imConf.setAuthenticationRequiredOperationTypes(OperationType.COMPARE)

    val imDS = InMemoryDirectoryServer(imConf)

    imDS.importFromLDIF(true,"src/test/resources/ADUsers.ldif")

    describe("NAVAuthorizer class test specifications") {

        beforeGroup {
            imDS.startListening("LDAP")
        }


        given("a acls with read allowance ") {

            val aclRead = cReadAS("ktACons")

            on("a member user") {

                it("should return true") {

                    val authorizer = NAVAuthorizer()
                    val authorized = authorizer.authorize(createKP("bdoe"),aclRead)

                    authorized.`should be`(true)
                }
            }

            on("a non-member user") {

                it("should return false") {

                    val authorizer = NAVAuthorizer()
                    val authorized = authorizer.authorize(createKP("adoe"),aclRead)

                    authorized.`should be`(false)
                }
            }
        }

        given("a acls with describe allowance - 2 ldap groups") {

            val aclDescribe = cDescribeAS("ktACons","ktAProd")

            on("a member user in group 1") {
                it("should retrn true") {
                    val authorizer = NAVAuthorizer()
                    val authorized = authorizer.authorize(createKP("cdoe"),aclDescribe)

                    authorized.`should be`(true)
                }
            }
            on("a member user in group 2") {
                it("should retrn true") {
                    val authorizer = NAVAuthorizer()
                    val authorized = authorizer.authorize(createKP("adoe"),aclDescribe)

                    authorized.`should be`(true)
                }
            }
            on("a non-member user in any group") {
                it("should retrn true") {
                    val authorizer = NAVAuthorizer()
                    val authorized = authorizer.authorize(createKP("ddoe"),aclDescribe)

                    authorized.`should be`(false)
                }
            }
        }

        afterGroup {
            imDS.shutDown(true)
        }
    }
})