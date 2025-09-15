package whodat.accessgroups

import io.micronaut.cache.annotation.Cacheable
import jakarta.inject.Singleton

@Singleton
open class CloudIdentityService(
    private val cloudIdentityClient: CloudIdentityClient,
) {
    @Cacheable(value = ["cloud-identity-service-cache"], parameters = ["groupEmail"])
    open suspend fun listMembers(groupEmail: String): List<Membership> {
        val lookup = cloudIdentityClient.lookup(groupEmail)
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
            val resp = cloudIdentityClient.listMembers(groupId, pageToken)
            // memberships is guaranteed non-null
            allMemberships.addAll(resp.memberships ?: emptyList())
            pageToken = resp.nextPageToken
        } while (pageToken != null)

        return allMemberships
    }
}
