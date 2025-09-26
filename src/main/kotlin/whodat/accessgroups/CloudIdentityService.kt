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
    private val log = LoggerFactory.getLogger(AccessTokenFilter::class.java)

    @Cacheable(value = ["cloud-identity-service-cache"], parameters = ["groupEmail"])
    open suspend fun listMembers(groupEmail: String): List<Membership> {
        try {
            val lookup =
                cloudIdentityClient.lookup(
                    groupEmail,
                )

            val groupId = lookup.groupName
            return fetchMemberships(groupId)
        } catch (e: io.micronaut.http.client.exceptions.HttpClientResponseException) {
            val body = e.response.getBody(String::class.java).orElse("<no body>")
            log.error("Cloud Identity call failed: status=${e.status} body=$body", e)
            throw e
        }
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
            try {
                val resp: MembershipResponse =
                    cloudIdentityClient.listMembers(
                        groupId,
                        pageToken,
                    )
                // memberships is guaranteed non-null
                allMemberships.addAll(resp.memberships)
                pageToken = resp.nextPageToken
            } catch (e: io.micronaut.http.client.exceptions.HttpClientResponseException) {
                val body = e.response.getBody(String::class.java).orElse("<no body>")
                log.error("Cloud Identity call failed: status=${e.status} body=$body", e)
                throw e
            }
        } while (pageToken != null)

        return allMemberships
    }
}
