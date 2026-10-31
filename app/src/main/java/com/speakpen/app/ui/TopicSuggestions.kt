package com.speakpen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakpen.app.data.TopicBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TopicSuggestions(sessionId: String, visitVersion: Long, visitActive: Boolean, nextBatch: () -> TopicBatch, onError: (String) -> Unit,
    active: Boolean, enabled: Boolean, onSelect: (String) -> Unit,
    onStart: () -> Unit, demoAvailable: Boolean, onDemo: () -> Unit, modifier: Modifier = Modifier) {
    var batch by remember { mutableStateOf<TopicBatch?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        try { batch = withContext(Dispatchers.IO) { nextBatch() } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { onError("话题暂时无法读取，可以重试换一批或直接开始聊天。") }
        finally { refreshing = false }
    }
    LaunchedEffect(sessionId, visitVersion, visitActive) { if (visitActive) refresh() }
    val motion = rememberAmbientMotionEnabled()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxHeight < 390.dp
        val rowCount = if (maxHeight < 260.dp) 1 else if (compact) 2 else 3
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("今天，想聊点什么？", Modifier.padding(horizontal = 24.dp),
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(if (compact) 16.dp else 30.dp))
        batch?.let { current -> key(current.revision) {
        current.topics.chunked(6).take(rowCount).forEachIndexed { index, row ->
            TopicLane(row.map { it.text }, index, current.phases[index], active && enabled && motion, motion, enabled && !refreshing, onSelect)
            if (index < rowCount - 1) Spacer(Modifier.height(12.dp))
        }
        } }
        Spacer(Modifier.height(if (compact) 12.dp else 28.dp))
        TextButton(onClick = { scope.launch { refresh() } }, enabled = enabled && !refreshing,
            modifier = Modifier.heightIn(min = 48.dp)) { Text("换一批") }
        TextButton(onClick = onStart, enabled = enabled, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) { Text("让 AI 先开口") }
        if (demoAvailable && !compact) TextButton(onClick = onDemo, enabled = enabled) { Text("先体验示例对话") }
        }
    }
}

@Composable
private fun TopicLane(topics: List<String>, index: Int, initialPhase: Float, active: Boolean, motion: Boolean, enabled: Boolean, onSelect: (String) -> Unit) {
    val density = LocalDensity.current
    val offset = remember { mutableFloatStateOf(0f) }
    var cycleWidth by remember { mutableIntStateOf(0) }
    LaunchedEffect(cycleWidth) { if (cycleWidth > 0) offset.floatValue = cycleWidth * initialPhase }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val speed = with(density) { (if (index == 1) 26.dp else 32.dp).toPx() }
    LaunchedEffect(active, cycleWidth, pressed, focused) {
        if (!active || cycleWidth == 0 || pressed || focused) return@LaunchedEffect
        var previous = 0L
        while (isActive) withFrameNanos { now ->
            if (previous != 0L) offset.floatValue = (offset.floatValue + ((now - previous) / 1_000_000_000f).coerceAtMost(.05f) * speed) % cycleWidth
            previous = now
        }
    }
    val background = when(index) { 1 -> SoftGreen; 2 -> SoftBlue; else -> MaterialTheme.colorScheme.surface }
    val foreground = when(index) { 1 -> MaterialTheme.colorScheme.onPrimaryContainer; 2 -> MaterialTheme.colorScheme.onSecondaryContainer; else -> Ink }
    @Composable fun Chips(duplicate: Boolean = false) {
        Row(Modifier.then(if (duplicate) Modifier.clearAndSetSemantics { } else Modifier)
            .then(if (!duplicate) Modifier.onSizeChanged { cycleWidth = it.width } else Modifier)
            .padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            topics.forEach { topic ->
                Surface(onClick = { onSelect(topic) }, enabled = enabled, shape = RoundedCornerShape(16.dp), interactionSource = interactions,
                    color = background, contentColor = foreground, border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                    Text(topic, Modifier.padding(horizontal = 18.dp, vertical = 13.dp), fontSize = 15.sp, maxLines = 1)
                }
            }
        }
    }
    if (motion) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp))) {
        Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).graphicsLayer { translationX = -offset.floatValue }) { Chips(); Chips(true) }
    } else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) { Chips() }
}
