package com.signapp.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FrontHand
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.signapp.GestureCandidate
import com.signapp.HandyUiState
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
    onAddSpace: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
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
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
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

            CandidatesCard(candidates = uiState.candidates)

            SessionCard(
                sessionText = uiState.sessionText,
                gestureCount = uiState.gestureCount
            )

            ActionsRow(
                onReset = onReset,
                onSpeak = onSpeak,
                onDeleteLastWord = onDeleteLastWord,
                onAddSpace = onAddSpace,
                onCopy = onCopy,
                onShare = onShare
            )
        }
    }
}

@Composable
private fun HeaderSection(gestureCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "HANDY",
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HandyColors.TextPrimary,
                letterSpacing = (-1).sp
            )
            Text(
                text = "Sign Language Recognition",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = HandyColors.TextSecondary
            )
        }

        Box(
            modifier = Modifier
                .width(160.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HandyColors.SurfaceSecondary)
                .border(1.dp, HandyColors.Border, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = null,
                    tint = HandyColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "29 gestures",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HandyColors.TextPrimary
                    )
                    Text(
                        text = "loaded",
                        fontSize = 11.sp,
                        color = HandyColors.TextSecondary
                    )
                }
            }
        }
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
            .height(420.dp)
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
private fun PredictionCard(
    gesture: String,
    confidence: Float,
    gestureScale: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(HandyColors.GradientStart, HandyColors.GradientEnd),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(1.dp, HandyColors.Border, RoundedCornerShape(28.dp))
            .padding(24.dp)
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
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = HandyColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.scale(gestureScale)
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (confidence > 0f) "${(confidence * 100).toInt()}%" else "—",
                        fontSize = 32.sp,
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

            // Right: circular gesture icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(HandyColors.SurfaceSecondary)
                    .border(1.dp, HandyColors.Border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.FrontHand,
                    contentDescription = "Gesture icon",
                    tint = if (confidence > 0f) HandyColors.Accent
                           else HandyColors.TextSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

@Composable
private fun CandidatesCard(candidates: List<GestureCandidate>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(HandyColors.Surface)
            .border(1.dp, HandyColors.Border, RoundedCornerShape(28.dp))
            .padding(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TOP CANDIDATES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HandyColors.TextSecondary,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "CONFIDENCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HandyColors.TextSecondary,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            if (candidates.isEmpty()) {
                Text(
                    text = "Waiting for gesture…",
                    fontSize = 14.sp,
                    color = HandyColors.TextSecondary.copy(alpha = 0.45f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                candidates.forEachIndexed { i, candidate ->
                    if (i > 0) Spacer(Modifier.height(18.dp))
                    CandidateRow(rank = i + 1, candidate = candidate, isTop = i == 0)
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    rank: Int,
    candidate: GestureCandidate,
    isTop: Boolean
) {
    val animatedProgress by animateFloatAsState(
        targetValue = candidate.confidence,
        animationSpec = tween(500),
        label = "progress$rank"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "$rank",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = HandyColors.TextSecondary.copy(alpha = 0.45f),
            modifier = Modifier.width(14.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = candidate.name,
                fontSize = if (isTop) 15.sp else 14.sp,
                fontWeight = if (isTop) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isTop) HandyColors.TextPrimary else HandyColors.TextSecondary
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(HandyColors.ProgressTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (isTop) HandyColors.Accent
                            else HandyColors.Accent.copy(alpha = 0.45f)
                        )
                )
            }
        }

        Text(
            text = "${(candidate.confidence * 100).toInt()}%",
            fontSize = 13.sp,
            fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
            color = if (isTop) HandyColors.Accent else HandyColors.TextSecondary,
            modifier = Modifier.width(38.dp)
        )
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
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 26.sp,
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
    onAddSpace: () -> Unit,
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
            Text(text = "Reset", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                    text = { Text("Add space", color = HandyColors.TextPrimary, fontSize = 14.sp) },
                    onClick = { onAddSpace(); showMenu = false }
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
                candidates = listOf(
                    GestureCandidate("Victory", 0.98f),
                    GestureCandidate("Open Palm", 0.82f),
                    GestureCandidate("Thumb Up", 0.65f),
                    GestureCandidate("Closed Fist", 0.42f),
                ),
                sessionWords = listOf("Victory", "Open Palm"),
                gestureCount = 7
            ),
            previewView = null,
            overlayView = null,
            onReset = {},
            onSpeak = {},
            onCameraSwitch = {},
            onDeleteLastWord = {},
            onAddSpace = {},
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
            onAddSpace = {},
            onCopy = {},
            onShare = {}
        )
    }
}