package com.speakpen.app.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Pause nonessential motion for both Android's animation switch and touch exploration. */
@Composable
internal fun rememberAmbientMotionEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    fun enabled() = ValueAnimator.areAnimatorsEnabled() && manager?.isTouchExplorationEnabled != true
    var motion by remember { mutableStateOf(enabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { motion = enabled() }
        }
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { motion = enabled() }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { context.contentResolver.unregisterContentObserver(observer); manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return motion
}
