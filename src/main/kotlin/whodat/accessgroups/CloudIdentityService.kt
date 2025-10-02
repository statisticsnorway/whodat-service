package whodat.accessgroups

import io.micronaut.cache.annotation.Cacheable
import io.micronaut.gcp.GoogleCloudConfiguration
import jakarta.inject.Singleton
import no.ssb.whodat.filters.AccessTokenFilter
import org.slf4j.LoggerFactory

@Singleton
open class CloudIdentityService(
    private val cloudIdentityClient: CloudIdentityClient,
    private val gcloudConfig: GoogleCloudConfiguration,
) {
    private val log = LoggerFactory.getLogger(CloudIdentityService::class.java)

    @Cacheable(value = ["cloud-identity-service-cache"], parameters = ["groupEmail"])
    open fun listMembers(groupEmail: String): List<Membership> {
        val lookup =
            cloudIdentityClient.lookup(
                groupEmail,
            )

        val groupId = lookup.groupName
        return fetchMemberships(groupId)
    }

    /**
     * Paginate through all memberships of a group.
     *
     * @param groupId the id of the group
     * @return the list of all memberships
     */
    private fun fetchMemberships(groupId: String?): List<Membership> {
        if (groupId.isNullOrBlank()) return emptyList()

        tailrec fun go(
            pageToken: String?,
            acc: List<Membership>,
        ): List<Membership> {
            val resp = cloudIdentityClient.listMembers(groupId, pageToken)
            val newAcc = acc + resp.memberships
            return if (resp.nextPageToken == null) newAcc else go(resp.nextPageToken, newAcc)
        }
        return go(null, emptyList())
    }
}
