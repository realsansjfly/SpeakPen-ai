package com.speakpen.app

import com.speakpen.app.network.TunnelDns
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class TunnelDnsTest {
    private fun fakeDns(action: () -> List<InetAddress>) = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = action()
    }
    private val valid = """{"Status":0,"Answer":[{"type":1,"data":"104.16.231.132"},{"type":1,"data":"104.16.230.132"}]}"""
    @Test fun systemDnsIsAlwaysPreferred() {
        var fallbackCalled = false
        val expected = listOf(InetAddress.getByAddress(byteArrayOf(104,16,1,2)))
        val resolver = TunnelDns(fakeDns { expected }) { fallbackCalled = true; valid }
        assertEquals(expected, resolver.lookup("working.trycloudflare.com"))
        assertFalse(fallbackCalled)
    }
    @Test fun failedTunnelDnsCanUseVerifiedPublicIpv4Answers() {
        val resolver = TunnelDns(fakeDns { throw UnknownHostException("ENOTFOUND") }) { valid }
        assertEquals(listOf("104.16.231.132", "104.16.230.132"), resolver.lookup("working.trycloudflare.com").map { it.hostAddress })
    }
    @Test fun unrelatedProviderDomainsNeverUseFallback() {
        var called = false
        val original = UnknownHostException("provider offline")
        val resolver = TunnelDns(fakeDns { throw original }) { called = true; valid }
        try { resolver.lookup("dashscope.aliyuncs.com"); fail("must fail") } catch (error: UnknownHostException) { assertSame(original, error) }
        assertFalse(called)
    }
    @Test fun expiredAddressIsNotReplacedWithHardcodedIp() {
        val original = UnknownHostException("expired")
        val resolver = TunnelDns(fakeDns { throw original }) { """{"Status":3}""" }
        try { resolver.lookup("expired.trycloudflare.com"); fail("must fail") } catch (error: UnknownHostException) { assertSame(original, error) }
    }
    @Test fun resolverFailureReturnsOriginalErrorWithoutLeakingResponse() {
        val original = UnknownHostException("offline")
        val resolver = TunnelDns(fakeDns { throw original }) { throw IllegalStateException("resolver failure") }
        try { resolver.lookup("test.trycloudflare.com"); fail("must fail") } catch (error: UnknownHostException) { assertSame(original, error) }
    }
    @Test fun onlyPublicNumericARecordsAreAccepted() {
        val raw = """{"Status":0,"Answer":[{"type":1,"data":"127.0.0.1"},{"type":1,"data":"10.1.2.3"},{"type":1,"data":"192.168.1.4"},{"type":1,"data":"172.16.0.1"},{"type":1,"data":"169.254.1.1"},{"type":1,"data":"100.64.0.1"},{"type":1,"data":"provider.example"},{"type":1,"data":"999.1.1.1"},{"type":28,"data":"::1"},{"type":1,"data":"104.16.231.132"},{"type":1,"data":"104.16.231.132"}]}"""
        assertEquals(listOf("104.16.231.132"), TunnelDns.parseAddresses(raw).map { it.hostAddress })
    }
}

