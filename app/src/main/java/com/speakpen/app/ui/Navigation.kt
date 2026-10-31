package com.speakpen.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel
import com.speakpen.app.R
import com.speakpen.app.data.ConversationMode
import com.speakpen.app.data.ConversationSessions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun PracticeNavigation(state: PracticeState, vm: PracticeViewModel, onRecord: () -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navigateEnabled = !state.voiceActive && !state.settingsSaving
    // Settings return is handled inside its page so an unsaved edit cannot bypass persistence.
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = state.page == 1 && !drawer.isOpen) { vm.page(0) }
    val openDrawer = { scope.launch { drawer.open() }; Unit }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val drawerWidth = maxWidth * .75f
        ModalNavigationDrawer(drawerState = drawer,
            gesturesEnabled = state.page != 2 && navigateEnabled,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(drawerWidth).fillMaxHeight(),
                    drawerContainerColor = Paper, drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0)) {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 20.dp)) {
                            Image(painterResource(R.drawable.ic_launcher), "SpeakPen", Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
                            Spacer(Modifier.height(16.dp))
                            Text("本机学习者", style = MaterialTheme.typography.titleLarge)
                            Text("学习记录保存在这台设备", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        NavigationDrawerItem(label = { Text("积累库") }, selected = state.page == 1,
                            icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                            onClick = { if (navigateEnabled) scope.launch { drawer.close(); vm.page(1) } })
                        HorizontalDivider(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), color = Line)
                        Text("历史练习", Modifier.padding(horizontal = 22.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = Muted)
                        val history = remember(state.sessions) { ConversationSessions.history(state.sessions) }
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            if (history.isEmpty()) item { Text("还没有练习记录", Modifier.padding(12.dp), color = Muted, style = MaterialTheme.typography.bodyMedium) }
                            val today = LocalDate.now()
                            history.groupBy { session ->
                                val day = Instant.ofEpochMilli(session.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                                when (day) { today -> "今天"; today.minusDays(1) -> "昨天"; else -> "更早" }
                            }.forEach { (group, sessions) ->
                            item(key = "group-$group") { Text(group, Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp), color = Muted, style = MaterialTheme.typography.labelSmall) }
                            items(sessions, key = { it.id }) { session ->
                                NavigationDrawerItem(selected = session.id == state.currentSessionId && state.page == 0,
                                    label = { Column(Modifier.padding(vertical = 8.dp)) {
                                        Text(session.draft.takeIf { session.messages.isEmpty() && it.isNotBlank() } ?: session.title,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                        Text(SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(session.updatedAt)) + " · " +
                                            (if (session.draft.isNotBlank()) "草稿" else when (session.mode) {
                                                ConversationMode.FREE -> "自由聊天"; ConversationMode.REVIEW -> "收藏练习"; ConversationMode.SCENARIO -> "场景"
                                            }), color = Muted, style = MaterialTheme.typography.bodySmall)
                                    } }, onClick = {
                                        if (navigateEnabled) scope.launch {
                                            drawer.close()
                                            if (state.busy != null) vm.error("请先等待当前回复，或取消后切换练习。")
                                            else { vm.selectSession(session.id); vm.page(0) }
                                        }
                                    })
                            }
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Line)
                        NavigationDrawerItem(selected = false, label = { Text("设置") },
                            icon = { Icon(Icons.Outlined.Settings, null) }, modifier = Modifier.padding(12.dp),
                            onClick = { if (navigateEnabled) scope.launch { drawer.close(); vm.page(2) } })
                    }
                }
            }) {
            AnimatedContent(targetState = state.page, modifier = Modifier.fillMaxSize(), label = "页面切换",
                transitionSpec = {
                    if (targetState == 0) (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { -it / 12 }) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(220)) { it / 8 })
                    else (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 8 }) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(220)) { -it / 12 })
                }) { page ->
                when (page) {
                    0 -> ChatScreen(state, vm, onRecord, onOpenDrawer = openDrawer,
                        visible = state.page == 0 && drawer.isClosed && !drawer.isAnimationRunning)
                    1 -> Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.page(0) }, enabled = navigateEnabled) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回聊天") }
                            Text("积累库", style = MaterialTheme.typography.titleLarge)
                        }
                        LibraryScreen(state, vm)
                    }
                    2 -> SettingsScreen(state, vm)
                }
            }
        }
    }
}

@Composable
fun ChatHeader(state: PracticeState, vm: PracticeViewModel, onDrawer: () -> Unit, navigationEnabled: Boolean, modeEnabled: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDrawer, enabled = navigationEnabled) { Icon(Icons.Outlined.Menu, "打开历史练习与设置") }
        Spacer(Modifier.weight(1f))
        val scene = state.session.mode == ConversationMode.SCENARIO
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.weight(7f)) {
            BoxWithConstraints(Modifier.padding(4.dp)) {
                val half = maxWidth / 2
                val position by animateDpAsState(if (scene) half else 0.dp, tween(220), label = "模式选中背景")
                if (state.session.mode != ConversationMode.REVIEW) Box(Modifier.offset { IntOffset(position.roundToPx(), 0) }.width(half).height(44.dp)
                    .background(SoftGreen, RoundedCornerShape(12.dp)))
                Row {
                    listOf("自由聊天", "场景").forEachIndexed { index, title ->
                        val chosen = if (index == 1) scene else state.session.mode == ConversationMode.FREE
                        TextButton(onClick = { if (index == 1) { if (!scene) vm.showScenarios(true) }
                            else if (state.session.mode != ConversationMode.FREE) vm.newSession(ConversationMode.FREE) },
                            enabled = modeEnabled, modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics { role = Role.Tab; selected = chosen },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = if (chosen) MaterialTheme.colorScheme.primary else Ink)) {
                            Text(title, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { vm.newSession(state.session.mode, state.session.targetIds) }, enabled = modeEnabled) {
            Icon(Icons.Outlined.Add, "新建对话")
        }
    }
}
