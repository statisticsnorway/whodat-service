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
    open suspend fun listMembers(groupEmail: String): List<Membership> {
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
    private suspend fun fetchMemberships(groupId: String?): List<Membership> {
        val allMemberships: MutableList<Membership> = mutableListOf()
        if (groupId.isNullOrBlank()) return allMemberships

        var pageToken: String? = null
        do {
            val resp: MembershipResponse =
                cloudIdentityClient.listMembers(
                    groupId,
                    pageToken,
                )
            // memberships is guaranteed non-null
            allMemberships.addAll(resp.memberships)
            pageToken = resp.nextPageToken
        } while (pageToken != null)

        return allMemberships
    }
}
