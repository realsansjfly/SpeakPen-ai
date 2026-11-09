package com.speakpen.app

import com.speakpen.app.ui.HomePageLoad
import com.speakpen.app.ui.HomePagePhase
import com.speakpen.app.ui.HomePageResources
import org.junit.Assert.*
import org.junit.Test

class HomePageLoadTest {
    @Test fun onlyExactBundledDocumentAndFontsAreServed() {
        assertEquals("document", HomePageResources.route(HomePageResources.document))
        assertEquals("regular", HomePageResources.route("${HomePageResources.origin}/fonts/nunito-regular.ttf"))
        assertEquals("bold", HomePageResources.route("${HomePageResources.origin}/fonts/nunito-bold.ttf"))
    }

    @Test fun externalOrAmbiguousRequestsNeverLoadThroughTheAssetHandler() {
        val blocked = listOf("http://appassets.androidplatform.net/home/topic-cylinder.html",
            "https://appassets.androidplatform.net.evil.test/home/topic-cylinder.html",
            "https://user@appassets.androidplatform.net/home/topic-cylinder.html",
            "https://appassets.androidplatform.net:443/home/topic-cylinder.html",
            "${HomePageResources.document}?remote=1", "${HomePageResources.document}#other",
            "${HomePageResources.origin}/home/../fonts/nunito-bold.ttf",
            "${HomePageResources.origin}/fonts/%6eunito-bold.ttf", "file:///android_asset/home/topic-cylinder.html",
            "content://home/topic-cylinder.html", "javascript:alert(1)", "not a URI")
        blocked.forEach { assertNull(it, HomePageResources.route(it)) }
        assertNull(HomePageResources.route(HomePageResources.document, "POST"))
    }

    @Test fun pageCompletionWithoutScriptDataAndViewportKeepsNativeHome() {
        val load = HomePageLoad()
        listOf(load.rendered(false, 390, 570, 18, 18), load.rendered(true, 0, 570, 18, 18),
            load.rendered(true, 390, 0, 18, 18), load.rendered(true, 390, 570, 0, 0),
            load.rendered(true, 390, 570, 17, 18)).forEach {
            assertTrue(it.showNative)
            assertEquals(HomePagePhase.LOADING, it.phase)
        }
    }

    @Test fun completeRenderedPageCanReplaceNativeHome() {
        val ready = HomePageLoad().rendered(true, 390, 570, 18, 18)
        assertEquals(HomePagePhase.READY, ready.phase)
        assertFalse(ready.showNative)
    }

    @Test fun timeoutAndRendererFailureCannotBeUndoneByLateSuccess() {
        listOf(HomePageLoad(), HomePageLoad().rendered(true, 390, 570, 18, 18)).forEach { initial ->
            val failed = initial.fail("renderer_or_timeout")
            assertTrue(failed.showNative)
            assertEquals(failed, failed.rendered(true, 390, 570, 18, 18))
            assertFalse(HomePageLoad().rendered(true, 390, 570, 18, 18).showNative)
        }
    }
}
