package whodat.security

import com.nimbusds.jwt.JWTClaimNames
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Replaces
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.token.DefaultRolesFinder
import io.micronaut.security.token.RolesFinder
import io.micronaut.security.token.config.TokenConfiguration
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import whodat.accessgroups.CloudIdentityService
import whodat.accessgroups.Membership

@ConfigurationProperties("app-roles")
data class StaticRolesConfig(
    val userGroupNames: List<String>? = null,
    val trustedIssuers: List<String>? = null,
)

@Singleton
@Replaces(bean = DefaultRolesFinder::class)
@ExecuteOn(TaskExecutors.BLOCKING)
class WhodatRolesFinder(
    private val tokenConfiguration: TokenConfiguration,
    private val rolesConfig: StaticRolesConfig,
    private val cloudIdentityService: CloudIdentityService,
    private val applicationContext: ApplicationContext
) : RolesFinder {
    private val log = LoggerFactory.getLogger(WhodatRolesFinder::class.java)

    override fun resolveRoles(attributes: Map<String, Any>): List<String> {
        return runBlocking {
            // We need to run this in a blocking manner, since we do not control the interface.
            val roles = mutableSetOf<String>()
            val trustedIssuer = isTrustedIssuer(attributes)

            val email: String? =
                if (attributes[tokenConfiguration.nameKey] == null && trustedIssuer) {
                    // Expects three-letter initials only in "sub" claim
                    attributes["sub"]?.toString()?.plus("@ssb.no")
                } else {
                    attributes[tokenConfiguration.nameKey]?.toString()
                }

            if (email == null) {
                return@runBlocking emptyList()
            }

            rolesConfig.userGroupNames?.map { group ->
                val groupMembers = cloudIdentityService.listMembers(group)
                val activeEnvironments = applicationContext.environment.activeNames
                val userIsGroupMember = groupMembers.any { it.preferredMemberKey?.id == email}
                when {
                    group == "whodat-service-m2m@ssb.no" && userIsGroupMember  ->
                        roles.add(WhodatServiceRole.USER)
                    activeEnvironments.contains("naisprod") && trustedIssuer && userIsGroupMember  ->
                        roles.add(WhodatServiceRole.USER)
                    activeEnvironments.contains("naistest") && (trustedIssuer || userIsGroupMember) ->
                        roles.add(WhodatServiceRole.USER)
                    activeEnvironments.contains("local") ->
                        roles.add(WhodatServiceRole.USER)
                }
            }

            if (roles.isEmpty()) {
                log.info("Could not resolve any roles for user $email")
            }
            log.debug("Resolved roles {} for user {}", roles, email)

            return@runBlocking roles.toList()
        }
    }

    private fun isTrustedIssuer(attributes: Map<String, Any>): Boolean =
        rolesConfig.trustedIssuers?.contains(attributes[JWTClaimNames.ISSUER].toString()) == true
}
