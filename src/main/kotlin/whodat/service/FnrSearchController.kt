package no.ssb.whodat.service

import com.mycompany.model.Folkeregisterettilgjengeliggjoeringpersonv1Folkeregisterperson
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.serde.annotation.Serdeable
import kotlinx.coroutines.*
import no.ssb.whodat.gcp.GCPSecretManagerClient
import whodat.service.MaskinportenGuardianClient
import java.util.Base64

@Serdeable
data class FindPersonsRequest(
    val foedselsEllerDNummer: String,
)

@Controller()
private class FnrSearchController(
    private val maskinPortenGuardianClient: MaskinportenGuardianClient,
    private val keycloakClient: KeycloakClient,
    private val gcpSecretManagerClient: GCPSecretManagerClient,
    private val fregClient: FregClient,
) {
    companion object {
        private fun toBase64(auth: String): String =
            Base64.getEncoder().encodeToString(auth.toByteArray())
    }

    /*
      Handles the double key exchange for 'maskinporten' and 'maskinporten guardian' which consists of:
        1. Fetching an access token for 'maskinporten' guardian from keycloak
        2. Fetching a 'maskinporten' token from 'maskingporten' guardian

      The entire flow here can be described as keycloak -> 'maskinporten guardian' -> 'maskinporten'
     */
    private suspend fun maskinPortenTokenKeyExchange(): String = coroutineScope {
        val maskinPortenGuardianAuth: String = toBase64(gcpSecretManagerClient.authString())
        val keycloakResponse = async {
            keycloakClient.fetchAccessToken(
                "Basic $maskinPortenGuardianAuth",
                mapOf(
                    "grant_type" to "client_credentials",
                ),
            )
        }.await()

        val maskinPortenResponse = async {
            maskinPortenGuardianClient.fetchAccessToken(
                authorization = "Bearer ${keycloakResponse.accessToken}",
                emptyMap(),
            )
        }.await()

        return@coroutineScope maskinPortenResponse.accessToken

    }
    @Post("/search")
    suspend fun searchFnr(
        @Body request: FregClientRequest,
    ): HttpResponse<FregClientResponse> = coroutineScope {
        val maskinPortenToken = maskinPortenTokenKeyExchange()

        val results = fregClient.searchFnr("Bearer $maskinPortenToken", request)
        return@coroutineScope HttpResponse.ok(results)
    }

    @Post("/findpersons")
    suspend fun findPersons(
        @Body request: FindPersonsRequest,
    ): HttpResponse<Folkeregisterettilgjengeliggjoeringpersonv1Folkeregisterperson> = coroutineScope {
        val maskinPortenToken = maskinPortenTokenKeyExchange()

        return@coroutineScope HttpResponse.ok(
        fregClient.findPersons(
            "Bearer $maskinPortenToken",
            request.foedselsEllerDNummer
          )
        )
    }
}
