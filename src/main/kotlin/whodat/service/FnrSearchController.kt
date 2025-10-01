package no.ssb.whodat.service

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.annotation.Secured
import io.micronaut.serde.annotation.Serdeable
import kotlinx.coroutines.*
import no.ssb.whodat.gcp.GCPSecretManagerClient
import org.slf4j.LoggerFactory
import whodat.security.WhodatServiceRole
import whodat.service.MaskinportenGuardianClient
import java.util.Base64
import kotlin.reflect.full.memberProperties

@Serdeable
data class FindPersonsRequest(
    val foedselsEllerDNummer: String,
)

@Secured(WhodatServiceRole.USER)
@Controller()
private class FnrSearchController(
    private val maskinPortenGuardianClient: MaskinportenGuardianClient,
    private val keycloakClient: KeycloakClient,
    private val gcpSecretManagerClient: GCPSecretManagerClient,
    private val fregClient: FregClient,
) {
    private val log = LoggerFactory.getLogger(FnrSearchController::class.java)

    companion object {
        private fun toBase64(auth: String): String = Base64.getEncoder().encodeToString(auth.toByteArray())
    }

    /*
      Handles the double key exchange for 'maskinporten' and 'maskinporten guardian' which consists of:
        1. Fetching an access token for 'maskinporten' guardian from keycloak
        2. Fetching a 'maskinporten' token from 'maskingporten' guardian

      The entire flow here can be described as keycloak -> 'maskinporten guardian' -> 'maskinporten'
     */
    private suspend fun maskinPortenTokenKeyExchange(): String =
        coroutineScope {
            val maskinPortenGuardianAuth: String = toBase64(gcpSecretManagerClient.authString())
            val keycloakResponse =
                async {
                    keycloakClient.fetchAccessToken(
                        "Basic $maskinPortenGuardianAuth",
                        mapOf(
                            "grant_type" to "client_credentials",
                        ),
                    )
                }.await()

            val maskinPortenResponse =
                async {
                    maskinPortenGuardianClient.fetchAccessToken(
                        authorization = "Bearer ${keycloakResponse.accessToken}",
                        emptyMap(),
                    )
                }.await()

            return@coroutineScope maskinPortenResponse.accessToken
        }

    @Post("/search")
    @ExecuteOn(TaskExecutors.BLOCKING)
    suspend fun searchFnr(
        @Body request: FregClientRequest,
    ): HttpResponse<FregClientResponse> =
        coroutineScope {
            log.info(
                "Received request with fields \"{}\"",
                FregClientRequest::class
                    .memberProperties
                    .filter { it.get(request) != null }
                    .joinToString(", ") { it.name },
            )
            val maskinPortenToken = maskinPortenTokenKeyExchange()

            val results = fregClient.searchFnr("Bearer $maskinPortenToken", request)

            return@coroutineScope HttpResponse.ok(results)
        }
}
