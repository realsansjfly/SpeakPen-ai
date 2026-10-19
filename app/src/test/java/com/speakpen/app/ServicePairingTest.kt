package com.speakpen.app

import com.speakpen.app.network.ServicePairing
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class ServicePairingTest {
    private val token = "a".repeat(64)
    @Test fun copiedWebsiteEntryWorksWhenBrowserDoesNotLaunchApp() {
        val connection = ServicePairing.parse("  https://new.trycloudflare.com/#token=$token  ")
        assertEquals("https://new.trycloudflare.com/v1", connection.baseUrl)
        assertEquals(token, connection.accessToken)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnpairedWebsiteLink() { ServicePairing.parse("https://new.trycloudflare.com/") }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicatePairingParameters() { ServicePairing.parse("https://new.trycloudflare.com/#token=$token&token=$token") }
    @Test(expected = IllegalArgumentException::class) fun rejectsWebsiteUrlWithEmbeddedCredentials() { ServicePairing.parse("https://user:password@new.trycloudflare.com/#token=$token") }
    @Test fun validLinkCarriesOnlyServiceConnection() {
        val base = "https://example.trycloudflare.com/v1"
        val parsed = ServicePairing.parse("speakpen://connect?url=${URLEncoder.encode(base, "UTF-8")}&token=$token")
        assertEquals(base, parsed.baseUrl)
        assertEquals(token, parsed.accessToken)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsCleartextService() { ServicePairing.validate("http://example.com/v1", token) }
    @Test(expected = IllegalArgumentException::class) fun rejectsProviderKeyAsPairingToken() { ServicePairing.validate("https://example.com/v1", "sk-provider-key") }
    @Test(expected = IllegalArgumentException::class) fun rejectsPasswordInsideServiceAddress() { ServicePairing.validate("https://user:password@example.com/v1", token) }
    @Test(expected = IllegalArgumentException::class) fun rejectsQueryParametersInAddress() { ServicePairing.validate("https://example.com/v1?key=bad", token) }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonServiceLinks() { ServicePairing.parse("https://example.com/v1?token=$token") }
    @Test(expected = IllegalArgumentException::class) fun rejectsMissingApiPrefix() { ServicePairing.validate("https://example.com", token) }
}

