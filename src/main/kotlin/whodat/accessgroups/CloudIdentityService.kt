package whodat.accessgroups

import com.google.auth.oauth2.GoogleCredentials
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.gcp.GoogleCloudConfiguration
import io.micronaut.http.HttpStatus
import jakarta.inject.Singleton

@Singleton
open class CloudIdentityService(
    private val cloudIdentityClient: CloudIdentityClient,
    private val gcloudConfig: GoogleCloudConfiguration
) {
    @Cacheable(value = ["cloud-identity-service-cache"], parameters = ["groupEmail"])
    open suspend fun listMembers(groupEmail: String): List<Membership> {
        println(groupEmail)
        val creds = GoogleCredentials.getApplicationDefault()
        //println(creds)
        val authString: String = creds.requestMetadata["Authorization"]?.get(0)!!
        println(authString)
        val response = cloudIdentityClient.lookup(
            authString,
            creds.quotaProjectId,
            groupEmail
        )
        if (response.status == HttpStatus.OK) {
            val lookup = response.body()
            val groupId = lookup.groupName
            return fetchMemberships(groupId)
        } else {
            throw Exception("UNEXPECTED HTTP RESPONSE ${response.status}")
        }
    }

    /**
     * Paginate through all memberships of a group.
     *
     * @param groupId the id of the group
     * @return the list of all memberships
     */
    private suspend fun fetchMemberships(groupId: String?): List<Membership> {
        val creds = GoogleCredentials.getApplicationDefault()
        val authString: String = creds.requestMetadata["Authorization"]?.get(0)!!
        val allMemberships: MutableList<Membership> = mutableListOf()
        if (groupId.isNullOrBlank()) return allMemberships

        var pageToken: String? = null
        do {
            val resp: MembershipResponse = cloudIdentityClient.listMembers(authString,creds.quotaProjectId,groupId, pageToken)
            // memberships is guaranteed non-null
            println("MEMBERSHIPS:")
            println(resp.memberships)
            allMemberships.addAll(resp.memberships)
            pageToken = resp.nextPageToken
        } while (pageToken != null)

        return allMemberships
    }
}
