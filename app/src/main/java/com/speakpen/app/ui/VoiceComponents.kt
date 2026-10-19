package com.speakpen.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun Waveform(modifier: Modifier = Modifier, active: Boolean = false, level: Float = 0.6f, color: androidx.compose.ui.graphics.Color = Ink) {
    Canvas(modifier) {
        val heights = listOf(0.26f, 0.45f, 0.8f, 0.55f, 1f, 0.65f, 0.35f, 0.72f, 0.92f, 0.5f, 0.28f)
        val slot = size.width / heights.size
        heights.forEachIndexed { i, value ->
            val height = size.height * value * (if (active) 0.35f + level * 0.65f else 0.7f)
            drawRoundRect(color, Offset(i * slot, (size.height - height) / 2), Size(slot * 0.42f, height), CornerRadius(3.dp.toPx()))
        }
    }
}

/** Gesture callbacks are updated without cancelling a press when recording state changes. */
@Composable
fun HoldToTalk(enabled: Boolean, recording: Boolean, chinese: Boolean, onStart: () -> Unit, onEnd: () -> Unit, onCancel: () -> Unit, onTap: () -> Unit) {
    var cancelDrag by remember { mutableStateOf(false) }
    val currentStart by rememberUpdatedState(onStart)
    val currentEnd by rememberUpdatedState(onEnd)
    val currentCancel by rememberUpdatedState(onCancel)
    val currentEnabled by rememberUpdatedState(enabled)
    val haptic = LocalHapticFeedback.current
    Box(Modifier.fillMaxWidth().height(56.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(when { cancelDrag -> MaterialTheme.colorScheme.errorContainer; recording -> Leaf; chinese -> SoftBlue; else -> VoiceGreen })
        .border(1.dp, if (chinese) Sky.copy(alpha = 0.35f) else Leaf.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
        .semantics {
            role = Role.Button
            contentDescription = if (recording) "结束录音" else "按住说话，上滑取消；无障碍可点击开始和结束"
            onClick { onTap(); true }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (currentEnabled || recording) {
                    down.consume()
                    val initialY = down.position.y
                    var released = false
                    currentStart(); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            cancelDrag = change.position.y - initialY < -72.dp.toPx()
                            change.consume()
                            if (!change.pressed) {
                                released = true
                                if (cancelDrag) currentCancel() else currentEnd()
                                break
                            }
                        }
                    } finally {
                        if (!released) currentCancel()
                        cancelDrag = false
                    }
                }
            }
        }, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Mic, null, Modifier.size(21.dp), tint = if (enabled || recording) Ink else Muted)
            Text(when { cancelDrag -> "松开取消"; recording -> "松开发送 · 上滑取消"; !enabled -> "请稍候…"; chinese -> "按住用中文求助"; else -> "按住说话" }, color = if (enabled || recording) Ink else Muted, style = MaterialTheme.typography.labelLarge)
        }
    }
}
