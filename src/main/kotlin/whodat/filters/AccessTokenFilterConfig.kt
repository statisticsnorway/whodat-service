package no.ssb.whodat.filters

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.serde.annotation.Serdeable

/**
 * Creates a GoogleAuthServiceConfig for each Service configured under
 * gcp.http.client.auth.services.*.audience. The audience can be configured per
 * service and the correct config bean is selected in `AccessTokenFilter` via the service id
 * inside the corresponding request.
 *
 * Requires the user to set the `gcp.http.client.auth.services.*.audience` property with the
 * desired audience to create the corresponding config bean.
 *
 */
@Serdeable
@EachProperty(AccessTokenFilterConfig.PREFIX)
data class AccessTokenFilterConfig(
    @param:Parameter val serviceId: String,
) {
    companion object {
        const val PREFIX = "gcp.http.client.filter.services"
    }

    // Replace audience with scopes
    var scopes: List<String> =
        listOf(
            "https://www.googleapis.com/auth/cloud-identity.groups.readonly",
        )
}
