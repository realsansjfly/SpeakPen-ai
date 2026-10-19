package com.speakpen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakpen.app.R

val Paper = Color(0xFFF8FAF9)
val Ink = Color(0xFF21382C)
val Muted = Color(0xFF64756C)
val Leaf = Color(0xFF58CC02)
val Sky = Color(0xFF1CB0F6)
val Amber = Color(0xFFFFC84A)
val SoftGreen = Color(0xFFEDF8E7)
val VoiceGreen = Color(0xFFDCF8C6)
val SoftBlue = Color(0xFFEAF6FF)
val Line = Color(0xFFE4EBE6)
private val PracticeFont = FontFamily(Font(R.font.nunito_regular), Font(R.font.nunito_bold, FontWeight.Bold), Font(R.font.nunito_extra_bold, FontWeight.ExtraBold))

@Composable
fun SpeakPenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF357A10), onPrimary = Color.White,
            primaryContainer = SoftGreen, onPrimaryContainer = Ink,
            secondary = Color(0xFF167CA6), secondaryContainer = SoftBlue,
            background = Paper, surface = Color.White, surfaceContainer = Color(0xFFF0F5F1),
            surfaceContainerHigh = Color(0xFFEAF1EC), onSurface = Ink, onSurfaceVariant = Muted, outline = Line,
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = PracticeFont, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontFamily = PracticeFont, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
            titleLarge = TextStyle(fontFamily = PracticeFont, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
            titleMedium = TextStyle(fontFamily = PracticeFont, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontFamily = PracticeFont, fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontFamily = PracticeFont, fontSize = 14.sp, lineHeight = 22.sp),
            labelLarge = TextStyle(fontFamily = PracticeFont, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            labelSmall = TextStyle(fontFamily = PracticeFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        ), shapes = Shapes(medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), content = content,
    )
}
@Composable fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelSmall, color = Muted)
}
@Composable fun InlineNotice(text: String, isError: Boolean = false, onClose: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(if (isError) MaterialTheme.colorScheme.errorContainer else SoftBlue, RoundedCornerShape(16.dp)).padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
        Text(text, Modifier.weight(1f).padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else Ink)
        if (onClose != null) TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("关闭") }
    }
}

