package com.speakpen.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.speakpen.app.ui.*

class MainActivity : ComponentActivity() {
    private lateinit var vm: PracticeViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = ViewModelProvider(this)[PracticeViewModel::class.java]
        intent?.dataString?.let(vm::connect)
        setContent {
            SpeakPenTheme {
                val state by vm.state.collectAsState()
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) vm.clearFeedback() else vm.error("麦克风权限未开启。仍可使用文字，或到系统设置允许录音。")
                }
                val record = {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startRecording()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }
                val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                Scaffold(modifier = Modifier.imePadding(), containerColor = Paper, contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (!keyboardVisible) NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                            val icons = listOf(Icons.Outlined.ChatBubbleOutline, Icons.Outlined.AutoStories, Icons.Outlined.Tune)
                            listOf("聊聊", "复习", "偏好").forEachIndexed { index, title ->
                                NavigationBarItem(selected = state.page == index, onClick = { vm.page(index) }, enabled = !state.voiceActive,
                                    icon = { Icon(icons[index], title) }, label = { Text(title) })
                            }
                        }
                    }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (state.error != null || state.notice != null) Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            InlineNotice(state.error ?: state.notice.orEmpty(), state.error != null, vm::clearFeedback)
                        }
                        // Keep an operation cancellable even when navigating away from chat.
                        if (state.busy != null && state.page != 0) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(state.busy.orEmpty(), Modifier.weight(1f)); TextButton(onClick = vm::cancelOperation) { Text("取消") }
                        }
                        Box(Modifier.weight(1f)) {
                            when (state.page) {
                                0 -> ChatScreen(state, vm, record)
                                1 -> LibraryScreen(state, vm)
                                2 -> SettingsScreen(state, vm)
                            }
                        }
                    }
                    PracticeDialogs(state, vm)
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); intent.dataString?.let(vm::connect)
    }
    override fun onStart() { super.onStart(); vm.foreground() }
    override fun onStop() { vm.background(); super.onStop() }
}




