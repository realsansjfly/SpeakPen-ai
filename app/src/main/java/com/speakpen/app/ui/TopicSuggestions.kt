package com.speakpen.app.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.speakpen.app.R
import com.speakpen.app.data.ConversationTopic
import com.speakpen.app.data.TopicBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

private const val HOME_TAG = "SpeakPenHome"

// Public JVM class: Android's JavaScript bridge can invoke it on older WebView releases too.
internal class HomeBridge(val action: (String, String) -> Unit) {
    @JavascriptInterface fun confirm(id: String) = action("confirm", id)
    @JavascriptInterface fun refresh() = action("refresh", "")
    @JavascriptInterface fun selection(value: Boolean) = action("selection", value.toString())
    @JavascriptInterface fun demo() = action("demo", "")
    @JavascriptInterface fun failed(reason: String) = action("failed", reason.take(120))
}

@Composable
fun TopicSuggestions(sessionId: String, visitVersion: Long, visitActive: Boolean, nextBatch: () -> TopicBatch, onError: (String) -> Unit,
    active: Boolean, enabled: Boolean, onConfirm: (String) -> Boolean, onSelectionFocus: (Boolean) -> Unit,
    demoAvailable: Boolean, onDemo: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var batch by remember(sessionId) { mutableStateOf<TopicBatch?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var load by remember { mutableStateOf(HomePageLoad()) }
    var attempt by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ConversationTopic?>(null) }
    var webSelected by remember { mutableStateOf(false) }
    var confirmError by remember { mutableStateOf(false) }
    var refreshFailures by remember { mutableIntStateOf(0) }
    val motion = rememberAmbientMotionEnabled()
    val fontScale = LocalDensity.current.fontScale
    val latestFocus by rememberUpdatedState(onSelectionFocus)
    LaunchedEffect(selected, webSelected) { latestFocus(selected != null || webSelected) }
    DisposableEffect(Unit) { onDispose { latestFocus(false) } }
    suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        try { batch = withContext(Dispatchers.IO) { nextBatch() } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { refreshFailures++; onError("话题暂时无法读取，请重试换一批。") }
        finally { refreshing = false }
    }
    // A drawer animation temporarily changes visibility. It must pause motion, not replace topics.
    LaunchedEffect(sessionId, visitVersion) { refresh() }
    LaunchedEffect(attempt) {
        delay(8_000)
        if (load.phase == HomePagePhase.LOADING) {
            Log.e(HOME_TAG, "Local home render timed out; keeping native topic selector")
            load = load.fail("render_timeout")
            webSelected = false
        }
    }
    Box(modifier.fillMaxWidth().fillMaxHeight()) {
        if (load.phase != HomePagePhase.FAILED) key(attempt) {
            HomeCylinderView(batch, active && visitActive && selected == null, enabled, motion, fontScale, demoAvailable, refreshFailures,
                renderAccepted = load.phase == HomePagePhase.READY,
                onRendered = { width, height, count -> load = load.rendered(true, width, height, count, batch?.topics?.size ?: 0) },
                onFailure = { reason -> load = load.fail(reason); webSelected = false },
                onSelection = { webSelected = it },
                onConfirm = { id -> batch?.topics?.firstOrNull { it.id == id }?.let { enabled && visitActive && onConfirm(it.text) } == true },
                onRefresh = { if (enabled && !refreshing) scope.launch { refresh() } },
                onDemo = { if (enabled && demoAvailable) onDemo() }, modifier = Modifier.matchParentSize())
        }
        // A GPU can return a valid DOM and still fail to composite CSS 3D. Keep recovery native.
        if (load.phase == HomePagePhase.READY && !webSelected && selected == null) {
            TextButton(onClick = { load = load.fail("manual_list") }, enabled = enabled,
                modifier = Modifier.align(Alignment.TopEnd)) { Text("列表", color = Muted) }
        }
        if (load.showNative) {
            Column(Modifier.matchParentSize().background(Paper).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("今天，想聊点什么？", Modifier.padding(top = 20.dp, bottom = 14.dp), style = MaterialTheme.typography.headlineMedium)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(batch?.topics.orEmpty(), key = { it.id }) { topic ->
                        TextButton(onClick = { selected = topic; confirmError = false }, enabled = enabled && !refreshing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(topic.text, Modifier.fillMaxWidth(), color = Ink, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (batch == null) item {
                        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                            if (refreshing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            else Text("暂无话题，请重试换一批。", color = Muted)
                        }
                    }
                }
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { scope.launch { refresh() } }, enabled = enabled && !refreshing) { Text("换一批") }
                    Button(onClick = { selected = batch?.topics?.randomOrNull(); confirmError = false },
                        enabled = enabled && !refreshing && batch?.topics?.isNotEmpty() == true) { Text("随便！") }
                }
                if (load.phase == HomePagePhase.FAILED) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (load.reason != "manual_list") Text("动效暂不可用", Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodySmall)
                        else Spacer(Modifier.weight(1f))
                        TextButton(onClick = { load = HomePageLoad(); attempt++ }, enabled = enabled && selected == null) {
                            Text(if (load.reason == "manual_list") "显示动效" else "重试动效")
                        }
                    }
                }
                if (demoAvailable) TextButton(onClick = onDemo, enabled = enabled) { Text("先体验示例对话") }
            }
        }
    }
    selected?.let { topic ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(topic.text) },
            text = { Text(if (confirmError) "暂时无法开启，请检查服务后重试。" else "开启对话？") },
            confirmButton = { TextButton(onClick = { if (enabled && visitActive && onConfirm(topic.text)) selected = null else confirmError = true }, enabled = enabled && visitActive) { Text("确定") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("取消") } })
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun HomeCylinderView(batch: TopicBatch?, active: Boolean, enabled: Boolean, motion: Boolean, fontScale: Float, demoAvailable: Boolean, refreshFailures: Int,
    renderAccepted: Boolean, onRendered: (Int, Int, Int) -> Unit, onFailure: (String) -> Unit, onSelection: (Boolean) -> Unit,
    onConfirm: (String) -> Boolean, onRefresh: () -> Unit, onDemo: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }
    var loaded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(false) }
    var disposed by remember { mutableStateOf(false) }
    val view = remember(context) {
        runCatching { WebView(context) }.onFailure { Log.e(HOME_TAG, "WebView provider unavailable", it) }.getOrNull()
    }
    val latestFailure by rememberUpdatedState(onFailure)
    val latestRendered by rememberUpdatedState(onRendered)
    val latestSelection by rememberUpdatedState(onSelection)
    val latestConfirm by rememberUpdatedState(onConfirm)
    val latestRefresh by rememberUpdatedState(onRefresh)
    val latestDemo by rememberUpdatedState(onDemo)
    val latestEnabled by rememberUpdatedState(enabled && renderAccepted)
    if (view == null) { LaunchedEffect(Unit) { latestFailure("webview_unavailable") }; return }
    BackHandler(selected) { view.evaluateJavascript("window.SpeakPenHome && window.SpeakPenHome.cancelSelection()", null) }
    LaunchedEffect(refreshFailures, loaded) {
        if (loaded && refreshFailures > 0) view.evaluateJavascript("window.SpeakPenHome && window.SpeakPenHome.refreshFailed()", null)
    }
    val payload = JSONObject().apply {
        put("active", active && renderAccepted); put("enabled", enabled); put("motion", motion)
        put("fontScale", fontScale); put("demoAvailable", demoAvailable)
        batch?.let { current -> put("batch", JSONObject().put("revision", current.revision).put("topics", JSONArray().apply {
            current.topics.forEach { put(JSONObject().put("id", it.id).put("text", it.text)) }
        })) }
    }.toString()
    LaunchedEffect(payload, loaded) {
        if (!loaded) return@LaunchedEffect
        // Some WebViews report page completion before layout. Retry the viewport probe briefly.
        repeat(12) { probe ->
            if (disposed) return@LaunchedEffect
            val script = """(function(){try{var h=window.SpeakPenHome;if(!h||typeof h.update!=='function')return {api:false};
                h.update($payload);return {api:true,width:document.documentElement.clientWidth,height:document.documentElement.clientHeight,
                topics:h.inspect().topicIds.length};}catch(e){return {api:false,error:String(e).slice(0,120)};}})()"""
            view.evaluateJavascript(script) { result ->
                if (disposed) return@evaluateJavascript
                val state = runCatching { JSONObject(result) }.getOrNull()
                if (state?.optBoolean("api") != true) {
                    Log.e(HOME_TAG, "Local page script unavailable: ${state?.optString("error") ?: result.take(120)}")
                    latestFailure("script_unavailable")
                } else {
                    val width = state.optInt("width"); val height = state.optInt("height"); val topics = state.optInt("topics")
                    if (width > 0 && height > 0 && topics > 0) {
                        view.postVisualStateCallback(probe.toLong(), object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                if (!disposed) { Log.d(HOME_TAG, "Local home rendered: ${width}x$height topics=$topics"); latestRendered(width, height, topics) }
                            }
                        })
                    }
                }
            }
            if (renderAccepted) return@LaunchedEffect
            delay(150)
        }
    }
    DisposableEffect(view) {
        onDispose {
            disposed = true
            main.removeCallbacksAndMessages(null)
            view.removeJavascriptInterface("SpeakPenNative")
            view.stopLoading()
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
    }
    AndroidView(modifier = modifier.fillMaxSize(), factory = {
        view.apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.rgb(248, 250, 249))
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setOnTouchListener { touched, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> touched.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touched.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = false
                allowFileAccess = false; allowContentAccess = false; blockNetworkLoads = true
                setSupportMultipleWindows(false); javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                textZoom = 100
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d(HOME_TAG, "JS ${message.messageLevel()} line=${message.lineNumber()}: ${message.message().take(180)}")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(web: WebView, url: String?) {
                    if (!disposed && url == HomePageResources.document) loaded = true
                }
                override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest) = request.url.toString() != HomePageResources.document
                // R.font TTFs are raw streams too; AssetManager.open only addresses assets/, not res/font/.
                @SuppressLint("ResourceType")
                override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse = try {
                    when (HomePageResources.route(request.url.toString(), request.method)) {
                        "document" -> WebResourceResponse("text/html", "UTF-8", context.assets.open("home/topic-cylinder.html"))
                        "regular" -> WebResourceResponse("font/ttf", null, context.resources.openRawResource(R.font.nunito_regular))
                        "bold" -> WebResourceResponse("font/ttf", null, context.resources.openRawResource(R.font.nunito_bold))
                        else -> WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                    }
                } catch (e: Exception) {
                    Log.e(HOME_TAG, "Bundled resource failed", e)
                    main.post { if (!disposed) latestFailure("asset_unavailable") }
                    WebResourceResponse("text/plain", "UTF-8", 500, "Asset unavailable", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                }
                override fun onReceivedError(web: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && !disposed) { Log.e(HOME_TAG, "Main page error: ${error.errorCode}"); latestFailure("page_error_${error.errorCode}") }
                }
                override fun onReceivedHttpError(web: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame && !disposed) latestFailure("page_http_${response.statusCode}")
                }
                override fun onRenderProcessGone(web: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (!disposed) { Log.e(HOME_TAG, "WebView renderer gone; crashed=${detail.didCrash()}"); latestFailure("renderer_gone") }
                    return true // Parent removes this AndroidView and retains the working native home.
                }
            }
            addJavascriptInterface(HomeBridge { action, value -> main.post {
                if (disposed) return@post
                when (action) {
                    "selection" -> { selected = value == "true"; latestSelection(selected) }
                    "confirm" -> if (!latestEnabled || !latestConfirm(value)) evaluateJavascript("window.SpeakPenHome.confirmRejected()", null)
                    "refresh" -> if (latestEnabled && !selected) latestRefresh()
                    "demo" -> if (latestEnabled) latestDemo()
                    "failed" -> { Log.e(HOME_TAG, "Local home runtime failure: $value"); latestFailure("script_runtime_error") }
                }
            } }, "SpeakPenNative")
            Log.i(HOME_TAG, "Loading offline home, WebView=${WebView.getCurrentWebViewPackage()?.versionName}")
            loadUrl(HomePageResources.document)
        }
    }, update = { web -> web.importantForAccessibility = if (renderAccepted) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS })
}
