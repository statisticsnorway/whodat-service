package whodat.service

import io.micronaut.cache.CacheManager
import io.micronaut.cache.SyncCache
import io.micronaut.cache.annotation.Cacheable
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.ssb.whodat.gcp.GCPSecretManagerClient
import no.ssb.whodat.service.KeycloakClient
import org.slf4j.LoggerFactory
import java.util.Base64

@Singleton
class MaskinportenTokenExchanger(
    private val cacheManager: CacheManager<String>,
    private val maskinPortenGuardianClient: MaskinportenGuardianClient,
    private val keycloakClient: KeycloakClient,
    private val gcpSecretManagerClient: GCPSecretManagerClient,
) {
    private val log = LoggerFactory.getLogger(MaskinportenTokenExchanger::class.java)

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
    open suspend fun tokenExchange(): String =
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
}
