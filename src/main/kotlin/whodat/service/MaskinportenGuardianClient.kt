package whodat.service

import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpHeaders.CONTENT_TYPE
import io.micronaut.http.HttpHeaders.USER_AGENT
import io.micronaut.http.annotation.Body

import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Headers
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class MaskinportenAccessTokenResponse(val accessToken: String)

@Client(id="maskinporten")
@Headers(
    Header(name = USER_AGENT, value = "Maskinporten Guardian HTTP Client"),
)
interface MaskinportenGuardianClient {
    @Post("/maskinporten/access-token")
    @Headers(
        Header(name = CONTENT_TYPE, value = "application/json"),
    )
    @SingleResult
    fun fetchAccessToken(
        @Header authorization: String,
        @Body body: Map<String, String>
    ): MaskinportenAccessTokenResponse
}