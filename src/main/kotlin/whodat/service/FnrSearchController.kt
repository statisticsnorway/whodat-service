package no.ssb.whodat.service

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.serde.annotation.Serdeable
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import no.ssb.whodat.gcp.GCPSecretManagerClient
import org.slf4j.LoggerFactory
import whodat.security.WhodatServiceRole
import whodat.service.MaskinportenGuardianClient
import java.util.Base64
import kotlin.reflect.full.memberProperties

@Serdeable
data class WhodatVariables(
    val navn: String? = null,
    val kjoenn: String? = null,
    val foedselsdato: String? = null,
    val foedselsaarFraOgMed: String? = null,
    val foedselsaarTilOgMed: String? = null,
    val adressenavn: String? = null,
    val husnummer: String? = null,
    val postnummer: String? = null,
    val kommunenummer: String? = null,
    val fylkesnummer: String? = null,
)

@Secured(WhodatServiceRole.USER)
@Controller()
open class FnrSearchController(
    private val cacheManager: CacheManager<String>,
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

    private val cache: SyncCache<String> by lazy {
        cacheManager.getCache("maskinporten-token-cache")
    }

    @Cacheable("maskinporten-token-cache")
    open suspend fun maskinPortenTokenKeyExchange(): String =
        withContext(Dispatchers.IO) {
            cache.get("token", String::class.java) {
                log.info("Fetching maskinporten token")
                val maskinPortenGuardianAuth: String = toBase64(gcpSecretManagerClient.authString())
                val keycloakResponse =
                    keycloakClient.fetchAccessToken(
                        "Basic $maskinPortenGuardianAuth",
                        mapOf(
                            "grant_type" to "client_credentials",
                        ),
                    )
                val maskinPortenResponse =
                    maskinPortenGuardianClient.fetchAccessToken(
                        authorization = "Bearer ${keycloakResponse.accessToken}",
                        emptyMap(),
                    )
                maskinPortenResponse.accessToken
            }
        }

    @Post("/search")
    suspend fun searchFnr(
        @Body request: SearchRequest,
    ): HttpResponse<List<FregClientResponse>> =
        coroutineScope {
            log.info(
                "Received request with fields \"{}\"",
                FregClientRequest::class
                    .memberProperties
                    .filter { it.get(request) != null }
                    .joinToString(", ") { it.name },
            )
            val limiter = Semaphore(50)
            limiter.withPermit {
                val maskinPortenToken = withContext(Dispatchers.IO) { maskinPortenTokenKeyExchange() }

                val results =
                    fregClient.searchFnr("Bearer $maskinPortenToken", request)
            }

            return@coroutineScope HttpResponse.ok(results)
        }
}
