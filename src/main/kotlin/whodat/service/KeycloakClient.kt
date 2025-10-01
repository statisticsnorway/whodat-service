package no.ssb.whodat.service

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.http.HttpHeaders.CONTENT_TYPE
import io.micronaut.http.HttpHeaders.USER_AGENT
import io.micronaut.http.MediaType.TEXT_PLAIN
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Headers
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class KeycloakAccessTokenResponse(
    @field:JsonProperty("access_token") val accessToken: String,
)

@Client(id = "keycloak")
@Headers(
    Header(name = USER_AGENT, value = "Keycloak HTTP Client"),
)
interface KeycloakClient {
    @Post("/realms/ssb/protocol/openid-connect/token")
    @Headers(
        Header(name = CONTENT_TYPE, value = "application/x-www-form-urlencoded"),
    )
    @Consumes(TEXT_PLAIN)
    suspend fun fetchAccessToken(
        @Header authorization: String,
        @Body body: Map<String, String>, // pass {"grant_type": "client_credentials"}
    ): KeycloakAccessTokenResponse
}
