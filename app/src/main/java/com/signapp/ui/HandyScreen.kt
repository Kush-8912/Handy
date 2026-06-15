package com.signapp.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FrontHand
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.signapp.HandyUiState
import com.signapp.LlmPhase
import com.signapp.OverlayView
import com.signapp.ui.theme.HandyColors
import com.signapp.ui.theme.HandyTheme
import kotlinx.coroutines.launch

@Composable
fun HandyScreen(
    uiState: HandyUiState,
    previewView: PreviewView?,
    overlayView: OverlayView?,
    onReset: () -> Unit,
    onSpeak: () -> Unit,
    onCameraSwitch: () -> Unit,
    onDeleteLastWord: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onTranslate: () -> Unit = {},
    onSelectReply: (String) -> Unit = {},
    onCopyReply: (String) -> Unit = {},
    onSpeakReply: (String) -> Unit = {},
    onSuggestGestures: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val flashAlpha = remember { Animatable(0f) }
    val gestureScale = remember { Animatable(1f) }

    LaunchedEffect(uiState.gestureCount) {
        if (uiState.gestureCount > 0) {
            launch {
                flashAlpha.snapTo(0.08f)
                flashAlpha.animateTo(0f, animationSpec = tween(600))
            }
            launch {
                gestureScale.snapTo(1.04f)
                gestureScale.animateTo(1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HandyColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 36.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderSection(gestureCount = uiState.gestureCount)

            CameraCard(
                previewView = previewView,
                overlayView = overlayView,
                flashAlpha = flashAlpha.value,
                onCameraSwitch = onCameraSwitch
            )

            PredictionCard(
                gesture = uiState.currentGesture,
                confidence = uiState.confidence,
                gestureScale = gestureScale.value
            )

            SessionCard(
                sessionText = uiState.sessionText,
                gestureCount = uiState.gestureCount
            )

            if (uiState.llmPhase != LlmPhase.IDLE) {
                LlmResultCard(
                    phase = uiState.llmPhase,
                    translation = uiState.llmTranslation,
                    intent = uiState.llmIntent,
                    replyOptions = uiState.llmReplyOptions,
                    selectedReply = uiState.llmSelectedReply,
                    onSelectReply = onSelectReply,
                    onCopyReply = onCopyReply,
                    onSpeakReply = onSpeakReply
                )
            }

            ActionsRow(
                onReset = onReset,
                onSpeak = onSpeak,
                onDeleteLastWord = onDeleteLastWord,
                onCopy = onCopy,
                onShare = onShare
            )

            TranslateButton(
                modelReady = uiState.modelReady,
                modelCopyProgress = uiState.modelCopyProgress,
                modelError = uiState.modelError,
                hasSession = uiState.sessionWords.any { it.isNotEmpty() },
                isRunning = uiState.llmPhase == LlmPhase.TRANSLATING || uiState.isSuggesting,
                onClick = onTranslate
            )

            if (uiState.modelReady) {
                GestureSuggestionCard(
                    isSuggesting = uiState.isSuggesting,
                    suggestion = uiState.gestureSuggestion,
                    onSuggest = onSuggestGestures
                )
            }
        }
    }
}

@Composable
private fun HeaderSection(gestureCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // HANDY + compact pill on the same line
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HANDY",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HandyColors.TextPrimary,
                letterSpacing = (-1).sp,
                maxLines = 1
            )

            // Compact pill badge — wraps its own content, never clips subtitle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(HandyColors.SurfaceSecondary)
                    .border(1.dp, HandyColors.Border, RoundedCornerShape(100.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(Color(0xFF22C55E), CircleShape)
                )
                Text(
                    text = "$gestureCount gesture${if (gestureCount == 1) "" else "s"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = HandyColors.TextSecondary
                )
            }
        }

        // Subtitle sits below, full width, never truncated
        Text(
            text = "Sign Language Recognition",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = HandyColors.TextSecondary
        )
    }
}

@Composable
private fun CameraCard(
    previewView: PreviewView?,
    overlayView: OverlayView?,
    flashAlpha: Float,
    onCameraSwitch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF0A0A0A))
            .border(1.dp, HandyColors.Border, RoundedCornerShape(28.dp))
    ) {
        if (previewView != null) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D1117)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = "Camera",
                    tint = HandyColors.TextSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        if (overlayView != null) {
            AndroidView(
                factory = { overlayView },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Commit flash overlay
        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HandyColors.Accent.copy(alpha = flashAlpha))
            )
        }

        // LIVE badge — top-left
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(HandyColors.Accent, CircleShape)
            )
            Text(
                text = "LIVE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }

        // Camera switch — top-right
        IconButton(
            onClick = onCameraSwitch,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector = Icons.Filled.Cameraswitch,
                contentDescription = "Switch camera",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun GestureIcon(active: Boolean, modifier: Modifier = Modifier) {
    val tint = if (active) HandyColors.Accent else HandyColors.TextSecondary.copy(alpha = 0.35f)
    Icon(Icons.Outlined.FrontHand, null, tint = tint, modifier = modifier)
}

@Composable
private fun PredictionCard(
    gesture: String,
    confidence: Float,
    gestureScale: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(HandyColors.GradientStart, HandyColors.GradientEnd),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(1.dp, HandyColors.Border, RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: labels + gesture name + confidence
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "CURRENT PREDICTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HandyColors.TextSecondary,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = gesture,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = HandyColors.TextPrimary,
                    maxLines = 2,
                    lineHeight = 28.sp,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.scale(gestureScale)
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (confidence > 0f) "${(confidence * 100).toInt()}%" else "—",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = HandyColors.Accent
                    )
                    Text(
                        text = "CONFIDENCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HandyColors.TextSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
            }

            // Right: open palm icon — glows on active prediction
            val active = confidence > 0f
            val glowAlpha by animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = tween(400),
                label = "glowAlpha"
            )
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .drawBehind {
                        drawCircle(HandyColors.Accent.copy(alpha = 0.06f * glowAlpha), radius = size.minDimension / 2 + 20.dp.toPx())
                        drawCircle(HandyColors.Accent.copy(alpha = 0.10f * glowAlpha), radius = size.minDimension / 2 + 10.dp.toPx())
                        drawCircle(HandyColors.Accent.copy(alpha = 0.16f * glowAlpha), radius = size.minDimension / 2 + 3.dp.toPx())
                    }
                    .clip(CircleShape)
                    .background(
                        if (active) HandyColors.Accent.copy(alpha = 0.10f * glowAlpha)
                        else HandyColors.SurfaceSecondary
                    )
                    .border(
                        1.dp,
                        if (active) HandyColors.Accent.copy(alpha = 0.6f) else HandyColors.Border,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                GestureIcon(
                    active = active,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionCard(sessionText: String, gestureCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(HandyColors.Surface)
            .border(1.dp, HandyColors.Border, RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SESSION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HandyColors.TextSecondary,
                    letterSpacing = 1.2.sp
                )
                if (gestureCount > 0) {
                    Text(
                        text = "$gestureCount gesture${if (gestureCount == 1) "" else "s"}",
                        fontSize = 11.sp,
                        color = HandyColors.TextSecondary.copy(alpha = 0.55f)
                    )
                }
            }
            Text(
                text = sessionText.ifBlank { "—" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp,
                color = if (sessionText.isBlank()) HandyColors.TextSecondary.copy(alpha = 0.3f)
                        else HandyColors.TextPrimary
            )
        }
    }
}

@Composable
private fun ActionsRow(
    onReset: () -> Unit,
    onSpeak: () -> Unit,
    onDeleteLastWord: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.weight(1.4f).height(60.dp),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = HandyColors.Surface,
                contentColor = HandyColors.TextSecondary
            ),
            border = BorderStroke(1.dp, HandyColors.Border)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(text = "Reset", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }

        Button(
            onClick = onSpeak,
            modifier = Modifier.weight(2.2f).height(60.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HandyColors.Accent,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(text = "Speak", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Box {
            OutlinedButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(60.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = HandyColors.Surface,
                    contentColor = HandyColors.TextSecondary
                ),
                border = BorderStroke(1.dp, HandyColors.Border)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = "More options",
                    modifier = Modifier.size(22.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Delete last word", color = HandyColors.TextPrimary, fontSize = 14.sp) },
                    onClick = { onDeleteLastWord(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Copy session", color = HandyColors.TextPrimary, fontSize = 14.sp) },
                    onClick = { onCopy(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Share session", color = HandyColors.TextPrimary, fontSize = 14.sp) },
                    onClick = { onShare(); showMenu = false }
                )
            }
        }
    }
}

@Composable
private fun LlmResultCard(
    phase: LlmPhase,
    translation: String,
    intent: String,
    replyOptions: List<String>,
    selectedReply: String,
    onSelectReply: (String) -> Unit,
    onCopyReply: (String) -> Unit,
    onSpeakReply: (String) -> Unit
) {
    val isTranslating = phase == LlmPhase.TRANSLATING
    val hasTranslation = translation.isNotEmpty()
    val generatingReplies = isTranslating && hasTranslation && replyOptions.isEmpty()

    // Blinking cursor animation for loading state
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f, label = "cursorAlpha",
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 900; 1f at 0; 1f at 450; 0f at 451 },
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0D1829), Color(0xFF0F1115)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(1.dp, HandyColors.Accent.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ── Translation header ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✨  AI TRANSLATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HandyColors.Accent,
                    letterSpacing = 1.2.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Intent badge
                    if (intent.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(HandyColors.Accent.copy(alpha = 0.12f))
                                .border(1.dp, HandyColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(100.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = intent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HandyColors.Accent
                            )
                        }
                    }
                    if (isTranslating && !hasTranslation) {
                        Text(
                            text = "translating…",
                            fontSize = 11.sp,
                            color = HandyColors.TextSecondary.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            // Translation text
            val displayText = when {
                hasTranslation -> translation
                isTranslating  -> if (cursorAlpha > 0.5f) "▌" else " "
                else           -> "—"
            }
            Text(
                text = displayText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = HandyColors.TextPrimary,
                lineHeight = 22.sp
            )

            // ── Reply chips section ─────────────────────────────────────────
            if (hasTranslation) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HandyColors.Border))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💬  SUGGESTED REPLIES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HandyColors.TextSecondary,
                        letterSpacing = 1.2.sp
                    )
                    if (generatingReplies) {
                        Text(
                            text = "generating…",
                            fontSize = 11.sp,
                            color = HandyColors.TextSecondary.copy(alpha = 0.55f)
                        )
                    }
                }

                if (replyOptions.isNotEmpty()) {
                    val labels = listOf("Formal", "Casual", "Empathetic")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        replyOptions.forEachIndexed { idx, option ->
                            val isSelected = option == selectedReply
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) HandyColors.Accent.copy(alpha = 0.14f)
                                        else HandyColors.SurfaceSecondary
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) HandyColors.Accent.copy(alpha = 0.55f)
                                        else HandyColors.Border,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onSelectReply(option) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = labels.getOrElse(idx) { "Option ${idx + 1}" },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) HandyColors.Accent
                                                else HandyColors.TextSecondary.copy(alpha = 0.6f),
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = option,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = HandyColors.TextPrimary,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }

                    // Actions for selected reply
                    if (selectedReply.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onCopyReply(selectedReply) },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = HandyColors.Surface,
                                    contentColor = HandyColors.TextSecondary
                                ),
                                border = BorderStroke(1.dp, HandyColors.Border)
                            ) {
                                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy Reply", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { onSpeakReply(selectedReply) },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HandyColors.Accent.copy(alpha = 0.15f),
                                    contentColor = HandyColors.Accent
                                ),
                                elevation = ButtonDefaults.buttonElevation(0.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Speak Reply", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else if (!generatingReplies) {
                    // Fallback when parsing produced nothing
                    Text(
                        text = "No replies generated",
                        fontSize = 13.sp,
                        color = HandyColors.TextSecondary.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/** Renders basic markdown: **bold**, bullet lines (- / *), numbered lists, headings (#). */
@Composable
private fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    color: Color = HandyColors.TextPrimary
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        text.trim().lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) return@forEach

            val isHeading  = line.matches(Regex("^#{1,3}\\s+.*"))
            val isBullet   = line.matches(Regex("^[-*]\\s+.*"))
            val isNumbered = line.matches(Regex("^\\d+\\.\\s+.*"))

            val content = when {
                isHeading  -> line.replace(Regex("^#{1,3}\\s+"), "")
                isBullet   -> "•  " + line.replace(Regex("^[-*]\\s+"), "")
                else       -> line
            }

            val annotated = buildAnnotatedString {
                // split on ** pairs for bold
                val parts = content.split("**")
                parts.forEachIndexed { i, part ->
                    if (i % 2 == 1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                            append(part)
                        }
                    } else {
                        // strip leftover single * used for italic — show as plain
                        append(part.replace(Regex("(?<!\\*)\\*(?!\\*)([^*\n]+)(?<!\\*)\\*(?!\\*)"), "$1"))
                    }
                }
            }

            Text(
                text = annotated,
                fontSize = if (isHeading) (fontSize.value + 2).sp else fontSize,
                fontWeight = if (isHeading) FontWeight.SemiBold else FontWeight.Normal,
                color = color,
                lineHeight = (fontSize.value * 1.6f).sp
            )
        }
    }
}

@Composable
private fun GestureSuggestionCard(
    isSuggesting: Boolean,
    suggestion: String,
    onSuggest: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(HandyColors.Surface)
            .border(1.dp, HandyColors.Border, RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "🤟  SUGGEST GESTURES TO SIGN",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = HandyColors.TextSecondary,
                letterSpacing = 1.2.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "Type what you want to say…",
                            fontSize = 14.sp,
                            color = HandyColors.TextSecondary.copy(alpha = 0.4f)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = HandyColors.TextPrimary,
                        unfocusedTextColor = HandyColors.TextPrimary,
                        focusedContainerColor = HandyColors.SurfaceSecondary,
                        unfocusedContainerColor = HandyColors.SurfaceSecondary,
                        focusedIndicatorColor = HandyColors.Accent,
                        unfocusedIndicatorColor = HandyColors.Border
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (inputText.isNotBlank() && !isSuggesting) {
                            onSuggest(inputText)
                        }
                    })
                )
                Button(
                    onClick = { if (inputText.isNotBlank()) onSuggest(inputText) },
                    enabled = inputText.isNotBlank() && !isSuggesting,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(56.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HandyColors.Accent,
                        contentColor = Color.White,
                        disabledContainerColor = HandyColors.Surface,
                        disabledContentColor = HandyColors.TextSecondary.copy(alpha = 0.4f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text(
                        text = if (isSuggesting) "…" else "Suggest",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (suggestion.isNotBlank()) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HandyColors.Border))
                MarkdownText(text = suggestion, fontSize = 14.sp, color = HandyColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun TranslateButton(
    modelReady: Boolean,
    modelCopyProgress: Float?,
    modelError: String?,
    hasSession: Boolean,
    isRunning: Boolean,
    onClick: () -> Unit
) {
    val enabled = modelReady && hasSession && !isRunning
    val label = when {
        modelError != null          -> "Model not found in assets"
        modelCopyProgress != null   -> "Copying model… ${(modelCopyProgress * 100).toInt()}%"
        !modelReady                 -> "Loading model…"
        isRunning                   -> "Generating…"
        else                        -> "✨  Translate to Natural Language"
    }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (enabled) HandyColors.Accent.copy(alpha = 0.08f)
                             else HandyColors.Surface,
            contentColor = if (enabled) HandyColors.Accent
                           else HandyColors.TextSecondary.copy(alpha = 0.45f),
            disabledContentColor = HandyColors.TextSecondary.copy(alpha = 0.4f),
            disabledContainerColor = HandyColors.Surface
        ),
        border = BorderStroke(
            1.dp,
            if (enabled) HandyColors.Accent.copy(alpha = 0.45f) else HandyColors.Border
        )
    ) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF050505,
    widthDp = 393,
    heightDp = 852,
    name = "Handy — Victory gesture"
)
@Composable
private fun HandyScreenPreview() {
    HandyTheme {
        HandyScreen(
            uiState = HandyUiState(
                currentGesture = "Victory",
                confidence = 0.98f,
                sessionWords = listOf("Victory", "Open Palm"),
                gestureCount = 7
            ),
            previewView = null,
            overlayView = null,
            onReset = {},
            onSpeak = {},
            onCameraSwitch = {},
            onDeleteLastWord = {},
            onCopy = {},
            onShare = {}
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF050505,
    widthDp = 393,
    heightDp = 852,
    name = "Handy — Idle"
)
@Composable
private fun HandyScreenIdlePreview() {
    HandyTheme {
        HandyScreen(
            uiState = HandyUiState(),
            previewView = null,
            overlayView = null,
            onReset = {},
            onSpeak = {},
            onCameraSwitch = {},
            onDeleteLastWord = {},
            onCopy = {},
            onShare = {}
        )
    }
}