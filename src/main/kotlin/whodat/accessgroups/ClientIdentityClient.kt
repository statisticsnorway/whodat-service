package whodat.accessgroups

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.annotation.Serdeable.Deserializable
import whodat.filters.AccessTokenFilterMatcher

@Client(id = "cloud-identity-service")
@AccessTokenFilterMatcher
interface CloudIdentityClient {
    /**
     * Lookup a group by its email address.
     * See: https://cloud.google.com/identity/docs/reference/rest/v1/groups/lookup
     * @param groupKeyId the email address of the group
     */
    @Get("/groups:lookup?groupKey.id={groupKeyId}")
    suspend fun lookup(groupKeyId: String): LookupResponse

    /**
     * List all members of a group.
     * See: https://cloud.google.com/identity/docs/reference/rest/v1/groups.memberships/list
     *
     * @param groupId the id of the group
     * @param pageToken for pagination
     */
    @Get("/groups/{groupId}/memberships")
    suspend fun listMembers(
        groupId: String,
        @QueryValue pageToken: String? = null,
    ): MembershipResponse
}

@Introspected
@Deserializable
class MembershipResponse {
    val memberships: MutableList<Membership>? = null
    val nextPageToken: String? = null
}

@Introspected
@Serdeable.Deserializable
data class LookupResponse(
    /** The resource name of the looked-up Group. */
    val name: String?,
) {
    val groupName: String?
        get() = name?.substringAfterLast('/')
}

@Introspected
@Serdeable.Deserializable
/**
 * A membership within the Cloud Identity Groups API.
 * A Membership defines a relationship between a Group and an entity belonging to that Group,
 * referred to as a "member".
 */
data class Membership(
    val name: String?,
    val preferredMemberKey: EntityKey?,
)

@Introspected
@Deserializable
data class EntityKey(
    val id: String?,
    val namespace: String?,
)
