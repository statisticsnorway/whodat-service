package no.ssb.whodat.service
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import no.ssb.whodat.service.FregClient
import no.ssb.whodat.service.FregClientRequest
import no.ssb.whodat.service.FregClientResponse

@MicronautTest
class FregClientIntegrationTest {

    private val mockRequest = FregClientRequest(navn = "VOKAL")
    private val mockResponse = FregClientResponse(foedselsEllerDNummer = listOf("21914697147"))

    @Test
    fun `test searchFnrInternal success`() = runBlocking {
        val mockClient = mockk<FregClient>()
        coEvery { mockClient.searchFnrInternal(any(), any()) } returns mockResponse

        val result = mockClient.searchFnrInternal("Bearer token", mockRequest)
        assertEquals(mockResponse, result)
    }



}
