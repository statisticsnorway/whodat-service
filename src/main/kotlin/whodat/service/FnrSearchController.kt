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
import whodat.service.MaskinportenTokenExchanger
import java.util.Base64
import kotlin.random.Random
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
    private val fregClient: FregClient,
    private val maskinportenTokenExchanger: MaskinportenTokenExchanger,
) {
    private val log = LoggerFactory.getLogger(FnrSearchController::class.java)

    @Post("/search")
    suspend fun searchFnr(
        @Body request: SearchRequest,
    ): HttpResponse<List<FregClientResponse>> =
        coroutineScope {
            log.info("Received request with ${request.whodatVariables.size} number of rows")
            val requestSemaphore = Semaphore(1000)

            val futures =
                request.whodatVariables.mapIndexed { i, variable ->
                    async {
                        requestSemaphore.withPermit {
                            // Jitter in order to avoid stampeding FREG service
                            delay(Random.nextLong(0, 10))

                            val token = maskinportenTokenExchanger.getToken("token")
                            fregClient.searchFnr(
                                "Bearer $token",
                                FregClientRequest.from(variable, request.whodatModifiers),
                            )
                        }
                    }
                }
            return@coroutineScope HttpResponse.ok(futures.awaitAll())
        }
}
