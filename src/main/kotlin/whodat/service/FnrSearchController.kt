package no.ssb.whodat.service

import io.micronaut.cache.CacheManager
import io.micronaut.cache.SyncCache
import io.micronaut.cache.annotation.Cacheable
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

@Serdeable
data class WhodatModifiers(
    val inkluderOppholdsadresse: Boolean? = null,
    val soekFonetisk: Boolean? = null,
    val inkluderDoede: Boolean? = null,
    val opplysningsgrunnlag: String? = null,
    val maksTreff: Int? = null,
)

@Serdeable
data class SearchRequest(
    val whodatVariables: List<WhodatVariables>,
    val whodatModifiers: WhodatModifiers,
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
            log.info("Received request with ${request.whodatVariables.size} number of rows")
            val requestSemaphore = Semaphore(1000)

            val futures =
                request.whodatVariables.map {
                    async {
                        requestSemaphore.withPermit {
                            val maskinPortenToken =
                                withContext(Dispatchers.IO) {
                                    return@withContext maskinPortenTokenKeyExchangeBlocking()
                                }
                            fregClient.searchFnr(
                                "Bearer $maskinPortenToken",
                                FregClientRequest.from(it, request.whodatModifiers),
                            )
                        }
                    }
                }
            return@coroutineScope HttpResponse.ok(futures.awaitAll())
        }
}
