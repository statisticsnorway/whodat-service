package whodat.service

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import no.ssb.whodat.service.FregClient
import no.ssb.whodat.service.FregClientRequest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import whodat.exceptions.FregUpstreamException

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FregClientTest : TestPropertyProvider {
    companion object {
        private const val INTERNAL_ERROR_RESPONSE = """{"error":"internal"}"""
        private const val BAD_REQUEST_RESPONSE = """{"error":"bad request"}"""
        private const val BAD_REQUEST_STATUS = 400
        private const val INTERNAL_SERVER_ERROR_STATUS = 500

    }

    @Inject
    lateinit var fregClient: FregClient
    private lateinit var server: MockWebServer

    @Volatile
    private var responseStatus: Int = INTERNAL_SERVER_ERROR_STATUS

    @Volatile
    private var responseBody: String = INTERNAL_ERROR_RESPONSE

    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            return MockResponse().setResponseCode(responseStatus).addHeader("Content-Type", "application/json")
                .setBody(responseBody)
        }
    }

    override fun getProperties(): MutableMap<String, String> {
        if (!::server.isInitialized) {
            server = MockWebServer()
            server.dispatcher = dispatcher
            server.start()
        }
        return mutableMapOf("micronaut.http.services.freg.url" to server.url("/").toString())
    }

    @BeforeAll
    fun setUpServer() {
        if (!::server.isInitialized) {
            server = MockWebServer()
            server.dispatcher = dispatcher
            server.start()
        }
    }

    @BeforeEach
    fun resetBetweenTests() {
        responseStatus = INTERNAL_SERVER_ERROR_STATUS
        responseBody = INTERNAL_ERROR_RESPONSE
    }

    @AfterAll
    fun tearDown() {
        if (::server.isInitialized) {
            server.shutdown()
        }
    }

    @Test
    fun `should retry on internal server error`() = runBlocking {
        setResponse(INTERNAL_SERVER_ERROR_STATUS, INTERNAL_ERROR_RESPONSE)
        val attempts = invokeSearchAndExpectFailure()
        assertTrue(attempts > 1, "Expected retry for HTTP 500")
    }


    @Test
    fun `should not retry for non-retryable status`() = runBlocking {

        setResponse(BAD_REQUEST_STATUS, BAD_REQUEST_RESPONSE)
        val attempts = invokeSearchAndExpectFailure()
        assertEquals(1, attempts, "HTTP 400 should not be retried")
    }

    private suspend fun invokeSearchAndExpectFailure(): Int {
        val baseline = server.requestCount
        assertThrows<FregUpstreamException> {
            fregClient.searchFnr(
                req = FregClientRequest(navn = "Test"),
                rowIndex = 1,
                fetchToken = { "Bearer dummy-token" }
            )
        }
        return server.requestCount - baseline
    }

    private fun setResponse(status: Int, body: String) {
        responseStatus = status
        responseBody = body
    }
}