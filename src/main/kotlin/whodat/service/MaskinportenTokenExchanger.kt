package whodat.service

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import no.ssb.whodat.gcp.GCPSecretManagerClient
import no.ssb.whodat.service.KeycloakClient
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Base64
import java.util.concurrent.Executor

@Singleton
class MaskinportenTokenExchanger(
    private val maskinPortenGuardianClient: MaskinportenGuardianClient,
    private val keycloakClient: KeycloakClient,
    private val gcpSecretManagerClient: GCPSecretManagerClient,
) {
    private val log = LoggerFactory.getLogger(MaskinportenTokenExchanger::class.java)

    companion object {
        private fun toBase64(auth: String): String = Base64.getEncoder().encodeToString(auth.toByteArray())
    }

    private val cache: AsyncLoadingCache<String, String> =
        Caffeine
            .newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofSeconds(100))
            .buildAsync { key: String, executor: Executor ->
                CoroutineScope(executor.asCoroutineDispatcher()).future {
                    tokenExchange(key)
                }
            }

    suspend fun getToken(key: String): String = cache.get(key).await()

    /*
      Handles the double key exchange for 'maskinporten' and 'maskinporten guardian' which consists of:
        1. Fetching an access token for 'maskinporten' guardian from keycloak
        2. Fetching a 'maskinporten' token from 'maskingporten' guardian

      The entire flow here can be described as keycloak -> 'maskinporten guardian' -> 'maskinporten'

      This method should NOT be accessed directly, but through the cache
     */
    private suspend fun tokenExchange(
        @Suppress("UNUSED_PARAMETER") key: String,
    ): String =
        coroutineScope {
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
