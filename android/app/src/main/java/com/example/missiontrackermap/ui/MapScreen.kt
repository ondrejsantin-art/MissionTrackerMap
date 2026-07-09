package com.example.missiontrackermap.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton

/**
 * The main mission map screen.
 *
 * Displays:
 *  1. The mission map image fullscreen (aspect-ratio preserving, ContentScale.Fit)
 *  2. A blinking red dot at the user's GPS position (in image pixel coordinates,
 *     mapped to screen coordinates using the same Fit scaling geometry)
 *  3. Loading/error states
 */
@Composable
fun MapScreen(
    viewModel: MissionTrackerViewModel,
    modifier: Modifier = Modifier
) {
    val mapBitmap by viewModel.mapBitmap.collectAsState()
    val calibration by viewModel.calibration.collectAsState()
    val dotPosition by viewModel.dotPosition.collectAsState()
    val loadError by viewModel.loadError.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            loadError != null -> {
                // Error state
                ErrorOverlay(message = loadError!!)
            }

            mapBitmap == null -> {
                // Loading state
                LoadingOverlay()
            }

            else -> {
                // Map + dot
                MissionMapContent(
                    bitmap = mapBitmap!!,
                    imageWidth = calibration?.imageWidth?.toFloat() ?: mapBitmap!!.width.toFloat(),
                    imageHeight = calibration?.imageHeight?.toFloat() ?: mapBitmap!!.height.toFloat(),
                    dotPositionInImagePx = dotPosition
                )
            }
        }

        // Overlay the Menu Button in the top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = Color.White
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Application Info") },
                    onClick = {
                        menuExpanded = false
                        showAboutDialog = true
                    }
                )
            }
        }

        // About Application Dialog
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text(text = "Application Info") },
                text = {
                    Column {
                        Text(text = "Author: Ondrej Santin")
                        Text(text = "Email: ondrej.santin@gmail.com")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Version: 1.0")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "License: MIT License")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun MissionMapContent(
    bitmap: ImageBitmap,
    imageWidth: Float,
    imageHeight: Float,
    dotPositionInImagePx: Offset?
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val canvasWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val canvasHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        // Compute ContentScale.Fit geometry:
        // The scale factor keeps aspect ratio, fitting entirely within the canvas.
        val scaleX = canvasWidthPx / imageWidth
        val scaleY = canvasHeightPx / imageHeight
        val fitScale = min(scaleX, scaleY)

        // The image is centered — compute the top-left offset
        val scaledImageW = imageWidth * fitScale
        val scaledImageH = imageHeight * fitScale
        val offsetX = (canvasWidthPx - scaledImageW) / 2f
        val offsetY = (canvasHeightPx - scaledImageH) / 2f

        // Blinking animation for the dot
        val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
        val dotAlpha by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_alpha"
        )
        val dotRadius by infiniteTransition.animateFloat(
            initialValue = 14f,
            targetValue = 20f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_radius"
        )

        var zoomScale by remember { mutableStateOf(1f) }
        var zoomOffset by remember { mutableStateOf(Offset.Zero) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                        zoomOffset = if (zoomScale == 1f) Offset.Zero else zoomOffset + pan
                    }
                }
        ) {
            withTransform({
                translate(zoomOffset.x, zoomOffset.y)
                scale(zoomScale, zoomScale, pivot = center)
            }) {
                // --- Draw the map image ---
                drawImage(
                    image = bitmap,
                    dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(scaledImageW.toInt(), scaledImageH.toInt())
                )

                // --- Draw the blinking red dot ---
                dotPositionInImagePx?.let { imagePixel ->
                    // Convert image pixel coordinates → screen coordinates
                    val screenX = offsetX + imagePixel.x * fitScale
                    val screenY = offsetY + imagePixel.y * fitScale

                    // Only draw if the dot is within the visible image area
                    if (screenX in offsetX..(offsetX + scaledImageW) &&
                        screenY in offsetY..(offsetY + scaledImageH)
                    ) {
                        drawGpsDot(
                            center = Offset(screenX, screenY),
                            radius = dotRadius,
                            alpha = dotAlpha
                        )
                    }
                }
            }
        }

        // Show "No GPS" indicator if calibration loaded but no position yet
        if (dotPositionInImagePx == null) {
            Text(
                text = "⟳ Acquiring GPS…",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
}

/**
 * Draws a pulsing red dot with a semi-transparent halo.
 * The halo helps visibility on both light and dark map areas.
 */
private fun DrawScope.drawGpsDot(center: Offset, radius: Float, alpha: Float) {
    // Outer halo (white semi-transparent for contrast)
    drawCircle(
        color = Color.White.copy(alpha = alpha * 0.4f),
        radius = radius * 2.2f,
        center = center
    )
    // Inner red dot
    drawCircle(
        color = Color.Red.copy(alpha = alpha),
        radius = radius,
        center = center
    )
    // White center highlight
    drawCircle(
        color = Color.White.copy(alpha = alpha * 0.6f),
        radius = radius * 0.35f,
        center = center
    )
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Loading mission…",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ErrorOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⚠ $message",
            color = Color(0xFFFF6B6B),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp)
        )
    }
}
