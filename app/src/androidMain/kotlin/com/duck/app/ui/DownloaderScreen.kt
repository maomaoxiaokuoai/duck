@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.duck.app.ui

import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue


import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.duck.app.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.draw.scale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duck.app.util.DownloadState
import com.duck.app.ui.theme.AppTheme
import coil.compose.AsyncImage

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material.icons.filled.Pause
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.border
import androidx.compose.ui.zIndex


/** Release notes can be long; the dialog shows a readable prefix. */
private const val MAX_RELEASE_NOTES_CHARS = 1200
/** Containers the preview renders as an image gallery rather than a video. */
/** Extensions the app treats as still images. */
internal val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")

/** Compact count: 12345 renders as 1.2万, the way the source app shows it. */
internal fun formatCount(value: Long): String = when {
    value >= 100_000_000 ->
        String.format(java.util.Locale.CHINA, "%.1f", value / 100_000_000.0) + "亿"
    value >= 10_000 ->
        String.format(java.util.Locale.CHINA, "%.1f", value / 10_000.0) + "万"
    else -> value.toString()
}

/** Human label for the kind of media behind a link. */
internal fun kindLabel(kind: String): String = when (kind) {
    "gallery" -> "图文"
    "audio" -> "音频"
    // A live photo is a still and a clip in one file, so it is neither of the above.
    "live" -> "动图"
    else -> "视频"
}

/** Publish date and time, to the minute. */
internal fun formatPostDateTime(epochSeconds: Long): String {
    if (epochSeconds !in 1_000_000_000L..9_999_999_999L) return ""
    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        .format(java.util.Date(epochSeconds * 1000))
    val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(epochSeconds * 1000))
    return date + " " + clock
}

/** Wall clock of a resolve, for the preview card. */
internal fun formatClock(epochMillis: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
        .format(java.util.Date(epochMillis))

/** Clip length as m:ss, from the milliseconds the platform reports. */
/**
 * Transport for an audio-only entry.
 *
 * The video player would show a black rectangle here, so this card replaces the surface
 * with the cover art and a play control that matches the rest of the sheet.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun AudioPlayerCard(
    player: androidx.media3.exoplayer.ExoPlayer,
    coverUrl: String?,
    title: String,
    author: String?,
) {
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // Poll the player rather than wiring a listener: the card only needs a rough
    // position for its slider, and this keeps the listener lifecycle trivial.
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: 0L
            isPlaying = player.isPlaying
            kotlinx.coroutines.delay(500)
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            if (!author.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            Slider(
                value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { fraction ->
                    if (duration > 0) player.seekTo((fraction * duration).toLong())
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatDuration(position),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatDuration(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            FloatingActionButton(
                onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                    isPlaying = player.isPlaying
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                )
            }
        }
    }
}
internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        minutes.toString() + ":" + seconds.toString().padStart(2, '0')
    } else {
        "0:" + seconds.toString().padStart(2, '0')
    }
}

/** Publish date as yyyy-MM-dd from the platform epoch seconds. */
internal fun formatPostDate(epochSeconds: Long): String {
    if (epochSeconds !in 1_000_000_000L..9_999_999_999L) return ""
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
    return fmt.format(java.util.Date(epochSeconds * 1000))
}

/**
 * The bare link inside a stored entry.
 *
 * Earlier versions stored whatever the user pasted, which for Douyin is a whole
 * share-sentence. Copying that back out is noise, so the first http(s) token wins.
 */
internal fun extractBareUrl(raw: String): String {
    val m = Regex("https?://[^\\s\u3000]+").find(raw)
    return (m?.value ?: raw).trim()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun DownloaderScreen(viewModel: DownloaderViewModel = viewModel()) {
    // Lives here rather than in the tab: the bolt is drawn by this screen, and a
    // grid selection the bolt cannot see is a selection that does nothing.
    val pickedImages = remember { mutableStateListOf<String>() }
    val context = androidx.compose.ui.platform.LocalContext.current
    // The context is a ComponentActivity in every real window, but a preview or a
    // wrapped context would make this cast fail, and the !! then crashed on launch.
    // Falling back to Compact just means the narrow layout, which is always safe.
    // calculateWindowSizeClass is itself composable, so it cannot live inside remember.
    // Calling it unconditionally and using the result is safe; only the cast can fail.
    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    val isExpanded = if (activity != null) {
        val windowSize = calculateWindowSizeClass(activity)
        windowSize.widthSizeClass != WindowWidthSizeClass.Compact
    } else {
        false
    }
    
    var selectedTab by remember { mutableIntStateOf(0) }

    
    var url by remember { mutableStateOf("") }
    // Always grab the best available rendition; the picker is gone.
    val quality = "max"
    // auto lets the parser decide between video and gallery from the response itself.
    val downloadMode = "auto"
    var showClearLogsDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val preview by viewModel.previewMetadata.collectAsState()
    val history by viewModel.downloadHistory.collectAsState(initial = emptyList())
    
    var showSupportedSitesDialog by remember { mutableStateOf(false) }
    var showTipsDialog by remember { mutableStateOf(false) }
    val externalUrl by viewModel.externalUrl.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    
    val instantInteractionSource = remember { MutableInteractionSource() }
    val isInstantPressed by instantInteractionSource.collectIsPressedAsState()
    val instantScale by animateFloatAsState(
        targetValue = if (isInstantPressed) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "instant_scale"
    )
    
    var isSettingsSearchActive by remember { mutableStateOf(false) }
    var settingsSearchQuery by remember { mutableStateOf("") }
    val searchInteractionSource = remember { MutableInteractionSource() }
    val isSearchPressed by searchInteractionSource.collectIsPressedAsState()
    val searchScale by animateFloatAsState(
        targetValue = if (isSearchPressed) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "search_scale"
    )
    val clearLogsInteractionSource = remember { MutableInteractionSource() }
    val isClearLogsPressed by clearLogsInteractionSource.collectIsPressedAsState()
    val clearLogsCorner by animateDpAsState(
        targetValue = if (isClearLogsPressed) 8.dp else 16.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "clear_corner"
    )
    val clearLogsScale by animateFloatAsState(
        targetValue = if (isClearLogsPressed) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "clear_scale"
    )

    LaunchedEffect(externalUrl) {
        externalUrl?.let { 
            url = it
            viewModel.consumeSharedUrl()
        }
    }

    // Typing a URL parses it after a pause. Tracking the last link that was resolved
    // keeps this from re-parsing the same one the paste button just handled, while
    // still resolving the next link that is typed.
    var lastParsedUrl by remember { mutableStateOf("") }
    LaunchedEffect(url) {
        if (url.isNotBlank() && url.startsWith("http") && url != lastParsedUrl) {
            delay(1000)
            lastParsedUrl = url
            viewModel.fetchPreview(url, quality, downloadMode)
        }
    }



    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.this_will_permanently_delete_a)) },
            confirmButton = {
                TextButton(
                    onClick = { 
                        viewModel.clearAllLogs()
                        showClearLogsDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }


    Row(modifier = Modifier.fillMaxSize()) {
        if (isExpanded) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
                Spacer(Modifier.height(12.dp))
                NavigationRailItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { 
                        val scale by animateFloatAsState(
                            targetValue = if (selectedTab == 0) 1.2f else 1.0f,
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                            label = "home_scale"
                        )
                        Icon(
                            if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home, 
                            null,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        ) 
                    },
                    label = { Text(stringResource(R.string.home)) }
                )
                NavigationRailItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { 
                        val rotation by animateFloatAsState(
                            targetValue = if (selectedTab == 1) 360f else 0f,
                            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                            label = "rotation"
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (selectedTab == 1) 1.2f else 1.0f,
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                            label = "scale"
                        )
                        Icon(
                            Icons.Default.History, 
                            null,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = rotation
                                scaleX = scale
                                scaleY = scale
                            }
                        ) 
                    },
                    label = { Text(stringResource(R.string.logs)) }
                )
                NavigationRailItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { 
                        val rotation by animateFloatAsState(
                            targetValue = if (selectedTab == 2) 360f else 0f,
                            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                            label = "rotation"
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (selectedTab == 2) 1.2f else 1.0f,
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                            label = "scale"
                        )
                        Icon(
                            Icons.Default.Settings, 
                            null,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = rotation
                                scaleX = scale
                                scaleY = scale
                            }
                        ) 
                    },
                    label = { Text(stringResource(R.string.settings)) }
                )
            }
        }

        val hazeState = remember { HazeState() }
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                if (!isExpanded) {
                    // A Surface with tonal elevation: Scaffold stacks content above a
                    // plain bottomBar, and a tall card could sit over the bolt so the
                    // tap hit the card. An elevated surface brings the bar forward.
                    Surface(
                        tonalElevation = 3.dp,
                        shadowElevation = 0.dp,
                        color = androidx.compose.ui.graphics.Color.Transparent,
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // The scrolling content draws above the bottom bar in a
                            // Scaffold, and a tall card could cover the bolt so taps
                            // landed on the card instead. Lifting the bar fixes the
                            // hit order rather than padding around it.
                            .zIndex(2f)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .shadow(8.dp, CircleShape)
                                .clip(CircleShape)
                                .hazeChild(
                                    state = hazeState,
                                    shape = CircleShape,
                                    style = HazeStyle(
                                        tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        blurRadius = 24.dp,
                                    ),
                                )
                                .animateContentSize(animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bg0 by animateColorAsState(if (selectedTab == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "")
                            val color0 by animateColorAsState(if (selectedTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "")
                            val pad0 by animateDpAsState(if (selectedTab == 0) 24.dp else 10.dp, tween(300, easing = FastOutSlowInEasing), label = "")
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text(stringResource(R.string.homepage)) } },
                                state = rememberTooltipState()
                            ) {
                                Box(
                                    modifier = Modifier.clip(CircleShape).let { m ->
                                        if (false) {
                                            m.hazeChild(state = hazeState, shape = CircleShape, style = HazeStyle(tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), blurRadius = 16.dp)).background(Color.Transparent)
                                        } else {
                                            m.background(bg0)
                                        }
                                    }.clickable {
                                        selectedTab = 0
                                    }.padding(horizontal = pad0, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val scale by animateFloatAsState(if (selectedTab == 0) 1.2f else 1.0f, tween(600, easing = FastOutSlowInEasing), label = "home_scale")
                                    Icon(if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home, "Home", modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, tint = color0)
                                }
                            }

                            val bg1 by animateColorAsState(if (selectedTab == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "")
                            val color1 by animateColorAsState(if (selectedTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "")
                            val pad1 by animateDpAsState(if (selectedTab == 1) 24.dp else 10.dp, tween(300, easing = FastOutSlowInEasing), label = "")
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text(stringResource(R.string.history)) } },
                                state = rememberTooltipState()
                            ) {
                                Box(
                                    modifier = Modifier.clip(CircleShape).let { m ->
                                        if (false) {
                                            m.hazeChild(state = hazeState, shape = CircleShape, style = HazeStyle(tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), blurRadius = 16.dp)).background(Color.Transparent)
                                        } else {
                                            m.background(bg1)
                                        }
                                    }.clickable {
                                        selectedTab = 1
                                    }.padding(horizontal = pad1, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val rotation by animateFloatAsState(if (selectedTab == 1) 360f else 0f, tween(800, easing = FastOutSlowInEasing), label = "rotation")
                                    val scale by animateFloatAsState(if (selectedTab == 1) 1.2f else 1.0f, tween(600, easing = FastOutSlowInEasing), label = "scale")
                                    Icon(Icons.Default.History, null, modifier = Modifier.graphicsLayer { rotationZ = rotation; scaleX = scale; scaleY = scale }, tint = color1)
                                }
                            }

                            val bg2 by animateColorAsState(if (selectedTab == 2) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "")
                            val color2 by animateColorAsState(if (selectedTab == 2) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "")
                            val pad2 by animateDpAsState(if (selectedTab == 2) 24.dp else 10.dp, tween(300, easing = FastOutSlowInEasing), label = "")
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text(stringResource(R.string.settings)) } },
                                state = rememberTooltipState()
                            ) {
                                Box(
                                    modifier = Modifier.clip(CircleShape).let { m ->
                                        if (false) {
                                            m.hazeChild(state = hazeState, shape = CircleShape, style = HazeStyle(tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), blurRadius = 16.dp)).background(Color.Transparent)
                                        } else {
                                            m.background(bg2)
                                        }
                                    }.clickable {
                                        selectedTab = 2
                                    }.padding(horizontal = pad2, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val rotation by animateFloatAsState(if (selectedTab == 2) 360f else 0f, tween(800, easing = FastOutSlowInEasing), label = "rotation")
                                    val scale by animateFloatAsState(if (selectedTab == 2) 1.2f else 1.0f, tween(600, easing = FastOutSlowInEasing), label = "scale")
                                    Icon(Icons.Default.Settings, null, modifier = Modifier.graphicsLayer { rotationZ = rotation; scaleX = scale; scaleY = scale }, tint = color2)
                                }
                            }
                        }

                        val isInstantVisible = selectedTab == 0 && uiState !is DownloadState.Downloading
                        val instantFabSize by animateDpAsState(if (isInstantVisible) 52.dp else 0.dp, androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
                        val instantSpacerSize by animateDpAsState(if (isInstantVisible) 10.dp else 0.dp, androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
                        
                        if (instantFabSize > 0.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(instantSpacerSize))
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                    tooltip = { PlainTooltip { Text(stringResource(R.string.instant_download)) } },
                                    state = rememberTooltipState()
                                ) {
                                    FloatingActionButton(
                                        onClick = {
                                            // The clipboard is the whole input here, so an
                                            // empty one has to say so instead of doing
                                            // nothing and looking broken.
                                            // The field wins when it has something in it.
                                            // Reading only the clipboard meant a link the
                                            // user had just typed was ignored in favour of
                                            // whatever happened to be copied earlier.
                                            // Field first, then the resolved post, then
                                            // the clipboard. The field is cleared once a
                                            // preview lands, so without the middle
                                            // fallback the bolt had nothing to act on
                                            // and silently did nothing.
                                            val typed = url.trim()
                                            val text = typed.takeIf { it.isNotEmpty() }
                                                ?: preview?.url?.takeIf { it.isNotBlank() }
                                                ?: clipboardManager.getText()?.text?.trim()
                                            when {
                                                text.isNullOrEmpty() -> {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "剪贴板是空的，请先复制抖音链接",
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                                else -> {
                                                    // Resolve first, then check. The clipboard
                                                    // holds a whole share-sentence, and asking
                                                    // URL() to parse that fails, which is why
                                                    // this route used to reject valid links.
                                                    val link = viewModel.extractUrl(text)
                                                    if (!viewModel.isDouyinUrl(link)) {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "剪贴板内容不是抖音或小红书链接",
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        ).show()
                                                        return@FloatingActionButton
                                                    }
                                                    // The bolt is the one-tap path. What it
                                                    // saves depends on the grid: picks win,
                                                    // and an untouched grid means everything.
                                                    url = link
                                                    if (pickedImages.isNotEmpty()) {
                                                        val picks = pickedImages.toList()
                                                        pickedImages.clear()
                                                        // One call, not a loop: downloadOneImage
                                                        // cancels the previous job, so looping
                                                        // over it saved only the last pick.
                                                        viewModel.downloadPickedImages(picks, preview?.title)
                                                    } else {
                                                        viewModel.downloadMedia(link, quality, downloadMode)
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(instantFabSize)
                                            .graphicsLayer {
                                                scaleX = instantScale
                                                scaleY = instantScale
                                            },
                                        interactionSource = instantInteractionSource,
                                        shape = CircleShape,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = "Instant Download")
                                    }
                                }
                            }
                        }
                        
                        val isBinVisible = selectedTab == 1
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val binFabSize by animateDpAsState(if (isBinVisible) 52.dp else 0.dp, androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
                        val binSpacerSize by animateDpAsState(if (isBinVisible) 10.dp else 0.dp, androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))

                        if (binFabSize > 0.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(binSpacerSize))
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                    tooltip = { PlainTooltip { Text(stringResource(R.string.clear_history_1)) } },
                                    state = rememberTooltipState()
                                ) {
                                    FloatingActionButton(
                                        onClick = { if (history.isEmpty()) android.widget.Toast.makeText(context, "No history", android.widget.Toast.LENGTH_SHORT).show() else showClearLogsDialog = true },
                                        modifier = Modifier
                                            .size(binFabSize)
                                            .graphicsLayer {
                                                scaleX = clearLogsScale
                                                scaleY = clearLogsScale
                                            },
                                        interactionSource = clearLogsInteractionSource,
                                        shape = CircleShape,
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All Logs")
                                    }
                                }
                            }
                        }
                        val isSearchVisible = selectedTab == 2
                        val searchFabSize by animateDpAsState(if (isSearchVisible) 52.dp else 0.dp, androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
                        val searchSpacerSize by animateDpAsState(if (isSearchVisible) 10.dp else 0.dp, androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))

                        if (searchFabSize > 0.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(searchSpacerSize))
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                    tooltip = { PlainTooltip { Text("Search Settings") } },
                                    state = rememberTooltipState()
                                ) {
                                    FloatingActionButton(
                                        onClick = { isSettingsSearchActive = !isSettingsSearchActive; if(!isSettingsSearchActive) settingsSearchQuery = "" },
                                        modifier = Modifier
                                            .size(searchFabSize)
                                            .graphicsLayer {
                                                scaleX = searchScale
                                                scaleY = searchScale
                                            },
                                        interactionSource = searchInteractionSource,
                                        shape = CircleShape,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ) {
                                        Icon(if (isSettingsSearchActive) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search Settings")
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        ) { padding ->
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val screenMargin = if (isExpanded) (configuration.screenWidthDp * 0.20f).dp else 0.dp
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = screenMargin).haze(state = hazeState)) {
                AnimatedContent(
                    targetState = selectedTab,
                    label = "tab_transition"
                ) { tab ->
                    when (tab) {
                        0 -> MainDownloaderTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            preview = preview,
                            url = url,
                            onUrlChange = { url = it },
                            quality = quality,
                            onQualityChange = { },
                            downloadMode = downloadMode,
                            onModeChange = { },
                            onStartDownload = { viewModel.downloadMedia(it, quality, downloadMode) },
                            onDownloadFinished = { selectedTab = 1 },
                            pickedImages = pickedImages,
                            contentPadding = padding,
                        )
                        1 -> LogsTab(
                            history = history, 
                            onDelete = viewModel::deleteLog,
                            contentPadding = padding
                        )
                        2 -> SettingsTab(viewModel, contentPadding = padding, isSearchActive = isSettingsSearchActive, searchQuery = settingsSearchQuery, onQueryChange = { settingsSearchQuery = it })
                    }
                }
            }
        }


        if (showSupportedSitesDialog) {
            AlertDialog(
                onDismissRequest = { showSupportedSitesDialog = false },
                title = { Text(stringResource(R.string.supported_sites), style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "支持抖音与小红书链接解析（视频 / 图文 / 音频）。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(listOf(
                                "抖音（视频、图集）",
                                "抖音短链 (v.douyin.com)",
                                "抖音网页链接 (www.douyin.com)",
                                "分享文案（自动提取链接）"
                            )) { site ->
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = site,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSupportedSitesDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTipsDialog = true }) {
                        Text(stringResource(R.string.help_me))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (showTipsDialog) {
            AlertDialog(
                onDismissRequest = { showTipsDialog = false },
                title = { Text(stringResource(R.string.downloading_tips)) },
                text = {
                    Text(
                        "• 复制抖音分享链接后粘贴到输入框即可自动解析。\n" +
                        "• 视频会保存到下载目录，图集会保存到 Pictures/duck。\n" +
                        "• 解析失败通常是因为作品被删除或需要登录，请换一个链接重试。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showTipsDialog = false }) {
                        Text(stringResource(R.string.got_it))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }

    viewModel.updateAvailable.value?.let { updateInfo ->
        val progress = viewModel.updateDownloadProgress.value
        val isDownloading = progress >= 0f

        AlertDialog(
            onDismissRequest = { if (!isDownloading) viewModel.updateAvailable.value = null },
            title = {
                Text(
                    if (isDownloading) "正在下载更新"
                    else "发现新版本 " + updateInfo.version,
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (isDownloading) {
                        Text("下载完成后会自动打开安装程序。")
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = (progress * 100).toInt().toString() + "%",
                            modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        if (updateInfo.notes.isNotBlank()) {
                            Text(
                                text = "更新内容",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = updateInfo.notes.take(MAX_RELEASE_NOTES_CHARS),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        } else {
                            Text("GitHub 上有新版本可用。")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isDownloading) {
                            viewModel.downloadUpdate(context, updateInfo.apkUrl)
                        }
                    },
                    enabled = !isDownloading,
                ) {
                    Text(if (isDownloading) "下载中…" else "立即更新")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.updateAvailable.value = null },
                    enabled = !isDownloading,
                ) {
                    Text(stringResource(R.string.later))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Opens the original Douyin post in the browser.
 *
 * The link the user pasted may be a short v.douyin.com redirect, which Douyin's own app
 * does not always resolve from an external intent, so the canonical page is used.
 */
private fun openDouyin(url: String, context: android.content.Context) {
    val target = url.ifBlank { "https://www.douyin.com/" }
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target))
    runCatching { context.startActivity(intent) }
}

/**
 * Hands the post link to whichever app the user picks.
 *
 * Sharing the link rather than the downloaded file is deliberate: the receiver usually
 * wants the post itself, and the files are already in the gallery for local sharing.
 */
private fun shareLink(url: String, context: android.content.Context) {
    if (url.isBlank()) return
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, url)
    }
    val chooser = android.content.Intent.createChooser(send, "分享作品链接")
    runCatching { context.startActivity(chooser) }
}

@Composable
fun MainDownloaderTab(
    viewModel: DownloaderViewModel, 
    uiState: DownloadState, 
    preview: com.duck.app.api.ExtractionResult?,
    url: String,
    onUrlChange: (String) -> Unit,
    quality: String,
    onQualityChange: (String) -> Unit,
    downloadMode: String,
    onModeChange: (String) -> Unit,
    onStartDownload: (String) -> Unit,
    /** Called once a download lands, so the shell can move to the history list. */
    onDownloadFinished: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** Grid picks owned by the shell so the bolt can act on them. */
    pickedImages: MutableList<String>,
) {
    var qualityExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var showFullscreenLogs by remember { mutableStateOf(false) }
    var showPreviewSheet by remember { mutableStateOf(false) }
    var showTerminalCard by remember { mutableStateOf(viewModel.getSetting("show_terminal_card", "true").toBoolean()) }
    var duplicateWarningUrl by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    BackHandler(enabled = showFullscreenLogs) {
        showFullscreenLogs = false
    }
    
    val clipboardManager = LocalClipboardManager.current
    val consoleLogs by viewModel.consoleLogs.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is DownloadState.Success) {
            // A dialog announcing what the user just asked for is noise, and it also
            // covered the preview they may want to keep looking at. Move to the history
            // list instead, which is where the result now lives.
            onDownloadFinished()
            viewModel.resetDownloadState()
        }
    }

    LaunchedEffect(showTerminalCard) {
        viewModel.saveSetting("show_terminal_card", showTerminalCard.toString())
    }

    if (duplicateWarningUrl != null) {
        AlertDialog(
            onDismissRequest = { duplicateWarningUrl = null },
            title = { Text(stringResource(R.string.duplicate_download)) },
            text = { Text(stringResource(R.string.you_have_already_downloaded_th)) },
            confirmButton = {
                TextButton(onClick = {
                    val proceedUrl = duplicateWarningUrl!!
                    duplicateWarningUrl = null
                    viewModel.downloadMedia(proceedUrl, quality, downloadMode)
                }) {
                    Text(stringResource(R.string.download_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { duplicateWarningUrl = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    var showCancelConfirmationDialog by remember { mutableStateOf(false) }
    var showDownloadingDetailsSheet by remember { mutableStateOf(false) }

    if (showCancelConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmationDialog = false },
            title = { Text(stringResource(R.string.cancel_download)) },
            text = { Text(stringResource(R.string.are_you_sure_you_want_to_cance)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirmationDialog = false
                    viewModel.cancelDownload()
                }) {
                    Text(stringResource(R.string.yes_cancel))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmationDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }

    if (showDownloadingDetailsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDownloadingDetailsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.downloading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                
                if (preview != null) {
                    AsyncImage(
                        model = preview?.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text(preview?.title ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }

                if (uiState is DownloadState.Downloading) {
                    val state = uiState as DownloadState.Downloading
                    val prog = state.progress
                    val downloadedMb = state.downloadedBytes / 1_048_576f
                    val totalMb = state.totalBytes / 1_048_576f
                    val speedKbps = state.speedBps / 1024f
                    
                    WavyProgressIndicator(
                        progress = prog,
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        waveAmplitude = 3.dp,
                        waveFrequency = 0.05f
                    )
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (state.totalBytes > 0) String.format("%.1f MB / %.1f MB", downloadedMb, totalMb) else String.format("%.1f MB downloaded", downloadedMb), style = MaterialTheme.typography.bodySmall)
                        Text(String.format("%.1f KB/s", speedKbps), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.cancelDownload()
                        showDownloadingDetailsSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Close, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cancel_download_1))
                }
            }
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        // ScrollState knows the real content extent once layout settles. Measuring
        // the column ourselves counted its own padding as content, so the check was
        // always true and a short page could still be dragged around.
        val canScroll = scrollState.maxValue > 0

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState, enabled = canScroll)
                .padding(
                    top = 108.dp + contentPadding.calculateTopPadding(),
                    // The Scaffold bottom bar IS the floating navigation bar, so
                    // calculateBottomPadding already equals its full height. Adding
                    // more on top of it left ~200dp of empty space below a gallery.
                    bottom = contentPadding.calculateBottomPadding() + 96.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Text("duck", 
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val onKeyboardGo = {
            if (url.isNotBlank()) {
                viewModel.fetchPreview(url, quality, downloadMode)
            }
        }

        val performAction: () -> Unit = {
            val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
            // An empty clipboard used to overwrite the field with "" and take the
            // resolved preview with it, leaving the screen blank with no way to
            // download what had just been parsed.
            if (clipText.isNotEmpty()) {
                // Put the resolved link in the field rather than the whole sentence
                // the share button copied.
                val link = viewModel.extractUrl(clipText)
                // Only fill the field. The LaunchedEffect watching it does the
                // resolving, so this cannot start a second resolution that races
                // the first.
                onUrlChange(link)
            } else if (url.isNotBlank()) {
                // Nothing copied: parse whatever is already in the field instead of
                // wiping it.
                viewModel.fetchPreview(url, quality, downloadMode)
            } else {
                android.widget.Toast.makeText(
                    context,
                    "剪贴板是空的，请先复制链接",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(100),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { typed ->
                        // A pasted share is a sentence, and showing that sentence in a
                        // field that claims to hold a link is confusing. When the text
                        // looks like it contains a URL but is not one, keep only the link.
                        val cleaned = if (typed.startsWith("http")) {
                            typed
                        } else if (typed.contains("http")) {
                            viewModel.extractUrl(typed)
                        } else {
                            typed
                        }
                        onUrlChange(cleaned)
                        if (cleaned.isEmpty()) viewModel.clearPreview()
                    },
                    placeholder = { Text(stringResource(R.string.search_or_paste_link)) },
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(YoutubeOutline, null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = { 
                                onUrlChange("")
                                viewModel.clearPreview()
                            }) {
                                Icon(Icons.Default.Clear, "Clear URL", modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { onKeyboardGo() }),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )

                IconButton(
                    onClick = performAction,
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Action")
                }
            }
        }

        // Metadata Preview Box
        //
        // Only a resolved post gets a card. An unrecognised link used to render an
        // empty "Unknown Title" placeholder, which looked like a result while saying
        // nothing, so nothing is drawn until there is something to show.
        val resolved = preview?.takeIf { it.status == "success" }
        AnimatedVisibility(visible = resolved != null) {
            resolved?.let { meta ->
                val isImageOrGallery = meta.is_gallery == true ||
                    meta.ext in IMAGE_EXTENSIONS

                // A gallery card has its own tappable grid, and a card-level click
                // wins over the tiles inside it. Only non-gallery cards open the
                // preview sheet from the card body.
                val cardModifier = if (isImageOrGallery) {
                    // A gallery has its own tappable grid; a card-level click would
                    // read as a button while doing nothing.
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth().clickable { showPreviewSheet = true }
                }
                Card(
                    modifier = cardModifier,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column {
                        // A gallery is a set of images, not one cover. Showing only the
                        // first hid what the post actually contains, so every page gets a
                        // tile and each tile downloads on its own.
                        if (isImageOrGallery && !meta.urls.isNullOrEmpty()) {
                            val galleryUrls = meta.urls.filter { it.isNotBlank() }
                            val selected = pickedImages
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        if (selected.isEmpty()) {
                                            "共 " + galleryUrls.size + " 张"
                                        } else {
                                            "已选 " + selected.size + " / " + galleryUrls.size + " 张"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected.isEmpty()) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    )
                                    Text(
                                        if (selected.isEmpty()) "点图片开始选择" else "点图片取消选择",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }

                                // Three per row, fixed height, so a 10-image post reads
                                // as a contact sheet rather than a long scroll.
                                galleryUrls.chunked(3).forEach { rowUrls ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        rowUrls.forEach { imageUrl ->
                                            val isPicked = selected.contains(imageUrl)
                                            // The border is what separates the two
                                            // states on a dark photo; the overlay alone
                                            // was hard to see.
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(110.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .then(
                                                        if (isPicked) {
                                                            Modifier.border(
                                                                2.dp,
                                                                MaterialTheme.colorScheme.primary,
                                                                RoundedCornerShape(12.dp),
                                                            )
                                                        } else {
                                                            Modifier.border(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.outlineVariant,
                                                                RoundedCornerShape(12.dp),
                                                            )
                                                        }
                                                    )
                                                    .clickable {
                                                        if (isPicked) selected.remove(imageUrl)
                                                        else selected.add(imageUrl)
                                                    },
                                            ) {
                                                AsyncImage(
                                                    model = imageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                )

                                                // A picked tile dims and shows a check so the
                                                // chosen set is readable at a glance.
                                                if (isPicked) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.45f)),
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                                                    )
                                                }
                                            }
                                        }
                                        // Keep the last row aligned with the grid above.
                                        repeat(3 - rowUrls.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }

                                // Saves only the picked tiles. With nothing picked the bolt
                                // in the bar still takes the whole set.
                            }
                        }
                        // Videos get a single cover. Galleries render as a grid
                        // below instead, so their cover would be a duplicate.
                        if (!isImageOrGallery) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            val galleryUrls = meta.urls
                                ?.filter { it.isNotBlank() }
                                ?: listOfNotNull(meta.thumbnail)

                            if (!meta.thumbnail.isNullOrBlank()) {
                                AsyncImage(
                                    model = meta.thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else if (galleryUrls.isNotEmpty()) {
                                AsyncImage(
                                    model = galleryUrls.first(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    imageVector = if (meta.ext == "mp3") Icons.Default.MusicNote else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(100),
                                ) {
                                    Text(
                                        meta.platform ?: "抖音",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }

                                val badges = buildList {
                                    meta.duration?.takeIf { it > 0 }
                                        ?.let { add(formatDuration(it)) }
                                    if (isImageOrGallery && galleryUrls.size > 1) {
                                        add(galleryUrls.size.toString() + " 张")
                                    }
                                }
                                if (badges.isNotEmpty()) {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(100),
                                    ) {
                                        Text(
                                            badges.joinToString(" · "),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                        }

                        Column(modifier = Modifier.padding(16.dp)) {
                            // Full title: this card summarises one post, and cutting it
                            // short hid the part that identifies the clip.
                            // The full caption, however long: cutting it hid the
                            // part that identifies the post.
                            Text(
                                meta.title ?: "无标题",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )

                            if (!meta.author.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    meta.author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }

                            // Facts about the post and about this resolution: what kind of
                            // media it is, how big the file is, when it was published and
                            // when we fetched it. Kept as one wrapped line so the card stays
                            // compact however many of them are present.
                            // What the post is, not when we fetched it: the resolve time
                            // belongs on the detail sheet, where it is a fact about this
                            // entry rather than about the clip.
                            val facts = listOfNotNull(
                                meta.media_kind?.let { kindLabel(it) },
                                meta.media_size?.takeIf { it > 0 }?.let { formatBytes(it) },
                                meta.create_time?.let { formatPostDateTime(it) },
                            )
                            if (facts.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    facts.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            val stats = listOfNotNull(
                                meta.like_count?.takeIf { it > 0 }?.let { "赞 " + formatCount(it) },
                                meta.comment_count?.takeIf { it > 0 }?.let { "评 " + formatCount(it) },
                                meta.share_count?.takeIf { it > 0 }?.let { "转 " + formatCount(it) },
                                meta.collect_count?.takeIf { it > 0 }?.let { "藏 " + formatCount(it) },
                            )
                            if (stats.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stats.joinToString("   "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (showPreviewSheet && preview != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            // Closing through hide() plays the sheet's own slide-down; clearing the flag
            // alone removed it in a single frame.
            val closeScope = rememberCoroutineScope()
            val meta = preview
            val isImageOrGallery = meta.is_gallery == true || meta.ext in IMAGE_EXTENSIONS

            ModalBottomSheet(
                onDismissRequest = { showPreviewSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isImageOrGallery) {
                        val imageUrls = meta.urls ?: listOf(meta.thumbnail ?: meta.url ?: "")
                        val validImageUrls = imageUrls.filter { !it.isNullOrBlank() }
                        
                        if (validImageUrls.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            ) {
                                val pagerState = rememberPagerState(pageCount = { validImageUrls.size })
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize()
                                ) { page ->
                                    AsyncImage(
                                        model = validImageUrls[page],
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                
                                if (validImageUrls.size > 1) {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .align(Alignment.TopEnd)
                                    ) {
                                        Text(
                                            text = "${pagerState.currentPage + 1} / ${validImageUrls.size}",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val streamUrlToPlay = meta.url ?: ""
                        if (streamUrlToPlay.isNotBlank()) {
                            val exoPlayer = remember { androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }
                            DisposableEffect(streamUrlToPlay) {
                                val videoUri = android.net.Uri.parse(streamUrlToPlay)
                                val videoSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                                    .createMediaSource(androidx.media3.common.MediaItem.fromUri(videoUri))
                                
                                val audioUrl = meta.audio_url
                                if (!audioUrl.isNullOrBlank()) {
                                    val audioSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                                        .createMediaSource(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(audioUrl)))
                                    val mergedSource = androidx.media3.exoplayer.source.MergingMediaSource(videoSource, audioSource)
                                    exoPlayer.setMediaSource(mergedSource)
                                } else {
                                    exoPlayer.setMediaSource(videoSource)
                                }
                                
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                                onDispose { exoPlayer.release() }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black)
                            ) {
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        val view = android.view.LayoutInflater.from(ctx).inflate(com.duck.app.R.layout.player_view_layout, null) as androidx.media3.ui.PlayerView
                                        view.apply {
                                            player = exoPlayer
                                            useController = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    
                    Text(meta.title ?: "Unknown Title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(meta.author ?: "Unknown Author", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Two downloads, one per rendition. Both are plain filled
                        // buttons so the pair reads as equal choices, and each starts
                        // downloading immediately rather than describing the stream.
                        // Both are the tonal style, so the pair reads as two equal
                        // renderings of the same post rather than a primary action
                        // beside a secondary one.
                        Button(
                            onClick = {
                                closeScope.launch {
                                    sheetState.hide()
                                    showPreviewSheet = false
                                }
                                viewModel.downloadMedia(url = url, quality = quality, mode = "video")
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(100),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Video", maxLines = 1)
                        }

                        Button(
                            onClick = {
                                closeScope.launch {
                                    sheetState.hide()
                                    showPreviewSheet = false
                                }
                                viewModel.downloadMedia(url = url, quality = quality, mode = "audio")
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(100),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Music", maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
 
        // Full-screen progress layer.
        //
        // It covers the home content rather than sitting inside it: while a download runs
        // there is nothing else to do on this screen, and the overlay keeps the list from
        // scrolling behind the progress ring.
        if (uiState is DownloadState.Downloading) {
            val state = uiState as DownloadState.Downloading
            DownloadProgressScreen(
                state = state,
                title = preview?.title ?: "下载中",
                author = preview?.author.orEmpty(),
                onCancel = { viewModel.cancelDownload() },
            )
        }
    }
}

/**
 * Full-screen download view.
 *
 * Shows a ring progress with the percentage in the middle, the item being fetched, and
 * the transfer rate. Every string is Chinese, matching the rest of the app.
 */
@Composable
private fun DownloadProgressScreen(
    state: DownloadState.Downloading,
    title: String,
    author: String,
    onCancel: () -> Unit,
) {
    val percent = (state.progress.coerceIn(0f, 1f) * 100).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(132.dp),
                    strokeWidth = 8.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = percent.toString() + "%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "已完成",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )

            if (author.isNotBlank()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    StatColumn("已下载", formatBytes(state.downloadedBytes))
                    StatColumn("总大小", formatBytes(state.totalBytes))
                    StatColumn("速度", formatBytes(state.speedBps) + "/s")
                }
            }

            Text(
                text = "保持应用在后台运行，下载不会中断。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            TextButton(onClick = onCancel) {
                Text("取消下载", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Renders a byte count the way a download manager would. */
internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "--"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> (Math.round(mb * 10) / 10.0).toString() + " MB"
        else -> Math.round(kb).toString() + " KB"
    }
}

@Composable
fun DownloadProgressBox(progress: Float, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.downloading), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                }
            }
            WavyProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(16.dp)
            )
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun WavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    waveAmplitude: androidx.compose.ui.unit.Dp = 3.dp,
    waveFrequency: Float = 0.08f,
    strokeWidth: androidx.compose.ui.unit.Dp = 6.dp,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    trackColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * kotlin.math.PI).toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val amplitude = waveAmplitude.toPx()
        val freq = waveFrequency
        
        val filledWidth = w * progress

        val lastY = if (filledWidth > 0f) centerY + amplitude * kotlin.math.sin(filledWidth * freq - phase) else centerY

        // Draw track connected to the end of the wave and tapered to center
        if (filledWidth < w) {
            val trackPath = androidx.compose.ui.graphics.Path()
            trackPath.moveTo(filledWidth, lastY)
            val distanceToCenter = kotlin.math.min(40f, w - filledWidth)
            if (distanceToCenter > 0f) {
                trackPath.cubicTo(
                    filledWidth + distanceToCenter / 2, lastY,
                    filledWidth + distanceToCenter / 2, centerY,
                    filledWidth + distanceToCenter, centerY
                )
            }
            trackPath.lineTo(w, centerY)

            drawPath(
                path = trackPath,
                color = trackColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }

        // Draw wavy filled part
        if (filledWidth > 0f) {
            val path = androidx.compose.ui.graphics.Path()
            var first = true
            for (x in 0..filledWidth.toInt() step 2) {
                val y = centerY + amplitude * kotlin.math.sin(x * freq - phase)
                if (first) {
                    path.moveTo(x.toFloat(), y)
                    first = false
                } else {
                    path.lineTo(x.toFloat(), y)
                }
            }
            drawPath(
                path = path,
                color = color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth.toPx(), 
                    cap = androidx.compose.ui.graphics.StrokeCap.Round, 
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
fun SegmentCard(
    onClick: () -> Unit,
    defaultTopStart: androidx.compose.ui.unit.Dp = 4.dp,
    defaultTopEnd: androidx.compose.ui.unit.Dp = 4.dp,
    defaultBottomStart: androidx.compose.ui.unit.Dp = 4.dp,
    defaultBottomEnd: androidx.compose.ui.unit.Dp = 4.dp,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val topStart by animateDpAsState(if (isPressed) 100.dp else defaultTopStart, animationSpec = tween(150), label = "topStart")
    val topEnd by animateDpAsState(if (isPressed) 100.dp else defaultTopEnd, animationSpec = tween(150), label = "topEnd")
    val bottomStart by animateDpAsState(if (isPressed) 100.dp else defaultBottomStart, animationSpec = tween(150), label = "bottomStart")
    val bottomEnd by animateDpAsState(if (isPressed) 100.dp else defaultBottomEnd, animationSpec = tween(150), label = "bottomEnd")
    
    val cardContainerColor by animateColorAsState(
        targetValue = if (isPressed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(150),
        label = "card_color"
    )

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interactionSource,
        shape = RoundedCornerShape(topStart = topStart, topEnd = topEnd, bottomStart = bottomStart, bottomEnd = bottomEnd),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor)
    ) {
        content()
    }
}

@Composable
fun SettingsListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(iconColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(viewModel: DownloaderViewModel, contentPadding: PaddingValues = PaddingValues(0.dp),
    isSearchActive: Boolean = false, searchQuery: String = "", onQueryChange: (String) -> Unit = {}) {
    var currentScreen by remember { mutableStateOf("Main") }

    BackHandler(enabled = currentScreen != "Main") {
        currentScreen = "Main"
    }
    
    LaunchedEffect(isSearchActive) {
        if (isSearchActive && currentScreen != "Main") {
            currentScreen = "Main"
        }
    }

    // currentScreen format: "ScreenName" or "ScreenName?target=CardTitle"
    val screenName = currentScreen.substringBefore("?")
    val target = if (currentScreen.contains("?target=")) currentScreen.substringAfter("?target=") else null

    AnimatedContent(
        targetState = screenName,
        transitionSpec = {
            if (targetState != "Main") {
                (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)) { it } + fadeIn()).togetherWith(
                    slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)) { -it } + fadeOut()
                )
            } else {
                (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)) { -it } + fadeIn()).togetherWith(
                    slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)) { it } + fadeOut()
                )
            }.using(SizeTransform(clip = false))
        },
        label = "settings_nav"
    ) { screen ->
        when (screen) {
            "Main" -> SettingsMainList(
                viewModel = viewModel, 
                onNavigate = { currentScreen = it }, 
                contentPadding = contentPadding,
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onQueryChange = onQueryChange
            )
            "Customisation" -> CustomisationScreen(viewModel, onBack = { currentScreen = "Main" }, contentPadding, target)
            "Downloads" -> DownloadsSettingsScreen(viewModel, onBack = { currentScreen = "Main" }, contentPadding, target)
            "Developer" -> DeveloperScreen(viewModel = viewModel, onBack = { currentScreen = "Main" }, contentPadding, target)
        }
    }
}

@Composable
fun SettingsMainList(
    viewModel: DownloaderViewModel, 
    onNavigate: (String) -> Unit, 
    contentPadding: PaddingValues,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onQueryChange: (String) -> Unit = {}
) {
    val allSettings = remember {
        listOf(
            Triple("Navigation Bar Blur", "Enable haze effect on floating navigation bar", "Customisation?target=Navigation Bar Blur"),
            Triple("True Glass Navigation", "Make navigation bar fully transparent glass", "Customisation?target=True Glass Navigation"),
            Triple("Opaque Navigation Bar", "Make floating navigation solid color", "Customisation?target=Opaque Navigation Bar"),
            Triple("Theme Mode", "System, Light, Dark", "Customisation?target=Theme Mode"),
            Triple("Color Scheme", "App Colors", "Customisation?target=Color Scheme"),
            Triple("Download Repository", "Files will be saved in your selected folder", "Downloads?target=Download Repository"),
            Triple("Extractors", "Update internal components", "Downloads?target=Extractors"),
            Triple("Check for Updates", "Check latest GitHub release", "Developer?target=Check for Updates")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()).padding(top = 16.dp + contentPadding.calculateTopPadding(), bottom = 24.dp + contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = isSearchActive,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search settings...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(100),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                singleLine = true
            )
        }

        if (isSearchActive && searchQuery.isNotEmpty()) {
            val results = allSettings.filter { it.first.contains(searchQuery, ignoreCase = true) || it.second.contains(searchQuery, ignoreCase = true) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                results.forEach { item ->
                    Card(
                        onClick = {
                            onQueryChange("")
                            onNavigate(item.third)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(item.first) },
                            supportingContent = { Text(item.second) },
                            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        } else {
        
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SegmentCard(
                onClick = { onNavigate("Customisation") },
                defaultTopStart = 24.dp,
                defaultTopEnd = 24.dp,
                defaultBottomStart = 4.dp,
                defaultBottomEnd = 4.dp
            ) {
                SettingsListItem(
                    icon = Icons.Default.Palette,
                    iconColor = Color(0xFF88637B), // Mauve
                    title = "个性化",
                    subtitle = "主题、外观、终端",
                )
            }

            SegmentCard(
                onClick = { onNavigate("Downloads") },
                defaultTopStart = 4.dp,
                defaultTopEnd = 4.dp,
                defaultBottomStart = 4.dp,
                defaultBottomEnd = 4.dp
            ) {
                SettingsListItem(
                    icon = Icons.Default.FolderOpen,
                    iconColor = Color(0xFF00758F), // Teal
                    title = "下载",
                    subtitle = "存储位置、下载规则",
                )
            }

            SegmentCard(
                onClick = { onNavigate("Developer") },
                defaultTopStart = 4.dp,
                defaultTopEnd = 4.dp,
                defaultBottomStart = 24.dp,
                defaultBottomEnd = 24.dp
            ) {
                SettingsListItem(
                    icon = Icons.Default.Code,
                    iconColor = Color(0xFF4C6B8B), // Slate blue
                    title = "开发者",
                    subtitle = "应用信息、平台、许可",
                )
            }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomisationScreen(viewModel: DownloaderViewModel, onBack: () -> Unit, contentPadding: PaddingValues, target: String? = null) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val positions = remember { mutableMapOf<String, Float>() }

    LaunchedEffect(target) {
        if (target != null) {
            kotlinx.coroutines.delay(300)
            val y = positions[target]
            if (y != null) {
                scrollState.animateScrollTo(y.toInt())
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState).padding(top = 16.dp + contentPadding.calculateTopPadding(), bottom = 24.dp + contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) { Icon(Icons.Default.ArrowBack, "Back") }
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.customisation), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        


        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleMedium, modifier = Modifier.onGloballyPositioned { positions["Theme Mode"] = it.parentLayoutCoordinates?.parentLayoutCoordinates?.positionInParent()?.y ?: 0f })
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val options = listOf("跟随系统", "浅色", "深色")
                    options.forEachIndexed { index, label ->
                        val isSelected = viewModel.themeMode.intValue == index
                        val shape = RoundedCornerShape(
                            topStart = if (index == 0) 100.dp else 8.dp,
                            bottomStart = if (index == 0) 100.dp else 8.dp,
                            topEnd = if (index == options.lastIndex) 100.dp else 8.dp,
                            bottomEnd = if (index == options.lastIndex) 100.dp else 8.dp
                        )
                        val colors = if (isSelected) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { viewModel.updateThemeMode(index) },
                            shape = shape,
                            colors = colors,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.color_scheme), style = MaterialTheme.typography.titleMedium, modifier = Modifier.onGloballyPositioned { positions["Color Scheme"] = it.parentLayoutCoordinates?.parentLayoutCoordinates?.positionInParent()?.y ?: 0f })
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val themes = AppTheme.values().toList()
                    themes.chunked(3).forEach { rowThemes ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            rowThemes.forEachIndexed { index, theme ->
                                val isSelected = viewModel.selectedTheme.value == theme
                                val shape = RoundedCornerShape(
                                    topStart = if (index == 0) 100.dp else 8.dp,
                                    bottomStart = if (index == 0) 100.dp else 8.dp,
                                    topEnd = if (index == rowThemes.lastIndex) 100.dp else 8.dp,
                                    bottomEnd = if (index == rowThemes.lastIndex) 100.dp else 8.dp
                                )
                                val colors = if (isSelected) {
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                } else {
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.updateSelectedTheme(theme) },
                                    shape = shape,
                                    colors = colors,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text(theme.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                            // Fill remaining space if chunk is smaller than 3
                            if (rowThemes.size < 3) {
                                repeat(3 - rowThemes.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsSettingsScreen(viewModel: DownloaderViewModel, onBack: () -> Unit, contentPadding: PaddingValues, target: String? = null) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()

            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()).padding(top = 16.dp + contentPadding.calculateTopPadding(), bottom = 24.dp + contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) { Icon(Icons.Default.ArrowBack, "Back") }
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.downloads), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        Text(
            "下载位置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "图片和视频分开保存，两个目录都会被系统相册索引。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Two rows rather than one: images and video genuinely land in different
        // folders, and showing a single path made it look like both went there.
        StorageRow(
            icon = Icons.Default.Photo,
            title = "图片",
            path = "Pictures/duck",
        )

        StorageRow(
            icon = Icons.Default.Videocam,
            title = "视频",
            path = "Movies/duck",
        )

        Spacer(Modifier.height(8.dp))
    }
}

/** One storage destination: an icon, a label and the folder it writes to. */
@Composable
private fun StorageRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    path: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
@Composable
fun DeveloperScreen(viewModel: DownloaderViewModel, onBack: () -> Unit, contentPadding: PaddingValues, target: String? = null) {
    val context = LocalContext.current
    // The avatar ships inside the APK. Fetching it from githubusercontent meant a
    // network round trip on a screen the user opens to read a version number, and it
    // also failed offline. A bundled image renders instantly, always.
    val profileImageRequest = remember(context) {
        coil.request.ImageRequest.Builder(context)
            .data("file:///android_asset/profile/avatar.png")
            .crossfade(true)
            .build()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()).padding(top = 16.dp + contentPadding.calculateTopPadding(), bottom = 24.dp + contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) { Icon(Icons.Default.ArrowBack, "Back") }
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.developer), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = profileImageRequest,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column {
                        Text("maomao", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.building_crisp_fast_and_secure), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/maomaoxiaokuoai"))) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(androidx.compose.ui.res.painterResource(id = com.duck.app.R.drawable.ic_github), null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.github), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/MAOMAOXIAOKUOAI")),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        // The two buttons share the row, and 8 characters plus an icon do
                        // not fit on one line at the default padding, so tighten both.
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_telegram), null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Telegram", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.app_info_updates), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("duck v${com.duck.app.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_check_updates), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.notify_on_launch_when_new_rele), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.autoCheckUpdates.value,
                        onCheckedChange = { viewModel.toggleAutoCheckUpdates(it) }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.check_for_updates), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        viewModel.updateCheckMessage.value?.let { msg ->
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        } ?: Text(stringResource(R.string.check_latest_github_release), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { viewModel.checkForUpdates(manual = true) },
                        enabled = !viewModel.isCheckingUpdates.value,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (viewModel.isCheckingUpdates.value) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.check))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsTab(
    history: List<com.duck.app.model.DownloadLog>, 
    onDelete: (com.duck.app.model.DownloadLog) -> Unit,
    onItemClick: (com.duck.app.model.DownloadLog) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var itemToDelete by remember { mutableStateOf<com.duck.app.model.DownloadLog?>(null) }
    var selectedLog by remember { mutableStateOf<com.duck.app.model.DownloadLog?>(null) }
    val context = LocalContext.current
    
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.clear_history_1)) },
            text = { Text(stringResource(R.string.are_you_sure_you_want_to_remov)) },
            confirmButton = {
                TextButton(onClick = {
                    itemToDelete?.let { onDelete(it) }
                    itemToDelete = null
                }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp + contentPadding.calculateTopPadding())
    ) {
        Text(stringResource(R.string.recent_downloads), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_history_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            val listState = rememberLazyListState()

            // A new download inserts at the top. Without this the list stayed wherever the
            // user had scrolled, so the entry they just created was off-screen.
            LaunchedEffect(history.firstOrNull()?.id) {
                if (history.isNotEmpty()) {
                    listState.animateScrollToItem(0)
                }
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp + contentPadding.calculateBottomPadding())
            ) {
                items(history, key = { it.id }) { log ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                                itemToDelete = log
                            }
                            false // always snap back, delete handled by dialog
                        }
                    )
                    
                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        },
                        content = {
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedLog = log }, 
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), 
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val thumbPath = log.thumbnailPath ?: log.path
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!thumbPath.isNullOrEmpty()) {
                                            coil.compose.AsyncImage(
                                                model = thumbPath,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (log.status == "Success") Icons.Default.PlayArrow else Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            log.title, 
                                            style = MaterialTheme.typography.titleMedium, 
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1, 
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        // Type and publish date, so the list says what each
                                        // entry is without opening it.
                                        // Local save time, not the post's publish date: an
                                        // entry in this list is a download, and when it was
                                        // downloaded is what the list is about.
                                        val itemFacts = listOfNotNull(
                                            log.platform ?: "抖音",
                                            log.fileSize?.takeIf { it > 0 }?.let { formatBytes(it) },
                                            log.duration?.let { formatDuration(it) },
                                            formatPostDateTime(log.timestamp / 1000),
                                        )
                                        Text(
                                            itemFacts.joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        // What was actually saved, rather than the word
                                        // Success. Failed entries still show their reason.
                                        // kindLabel keeps this in one place; the copy
                                        // here had missed "live" and labelled a
                                        // motion photo as a video.
                                        val kindText = if (log.status != "Success") {
                                            log.status
                                        } else {
                                            kindLabel(log.mediaKind.orEmpty())
                                        }
                                        Text(
                                            kindText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (log.status == "Success") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (selectedLog != null && selectedLog?.path != null) {
        val log = selectedLog!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val ext = log.path?.substringAfterLast('.', "") ?: ""
        val isGallery = ext.equals("jpg", true) || ext.equals("png", true) || ext.equals("jpeg", true) || ext.equals("webp", true) || ext.equals("gif", true) || (log.title.contains("Gallery", true) && !ext.equals("mp4", true))

        ModalBottomSheet(
            onDismissRequest = { selectedLog = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
        ) {
            // The content scrolls; the button row below does not, so closing the
            // sheet never depends on how far the user has scrolled.
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isGallery) {
                    // Only the files this entry actually wrote. Reading the post again
                    // would show pages the user never downloaded.
                    val saved = log.imagePaths
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()

                    if (saved.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "已下载 " + saved.size + " 张",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        saved.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowItems.forEach { savedPath ->
                                    coil.compose.AsyncImage(
                                        model = savedPath,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(110.dp)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else if (!log.path.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = log.path,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(300.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                } else {
                    val streamUrlToPlay = log.path ?: ""
                    if (streamUrlToPlay.isNotBlank()) {
                        val exoPlayer = remember { androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }
                        DisposableEffect(streamUrlToPlay) {
                            val mediaUri = android.net.Uri.parse(streamUrlToPlay)
                            val mediaItem = androidx.media3.common.MediaItem.fromUri(mediaUri)
                            exoPlayer.setMediaItem(mediaItem)
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                            onDispose { exoPlayer.release() }
                        }

                        // An mp3 has no picture, so the video surface renders as a black
                        // rectangle with controls on it. Audio gets its own card instead:
                        // cover art, title, and a transport row.
                        val isAudioOnly = log.mediaKind == "audio" ||
                            log.path?.endsWith(".mp3", ignoreCase = true) == true ||
                            log.path?.endsWith(".m4a", ignoreCase = true) == true

                        if (isAudioOnly) {
                            AudioPlayerCard(
                                player = exoPlayer,
                                coverUrl = log.thumbnailPath,
                                title = log.title,
                                author = log.author,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                    .background(androidx.compose.ui.graphics.Color.Black)
                            ) {
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        val view = android.view.LayoutInflater.from(ctx).inflate(com.duck.app.R.layout.player_view_layout, null) as androidx.media3.ui.PlayerView
                                        view.apply {
                                            player = exoPlayer
                                            useController = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                
                Text(
                    log.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                // Author, publish date and source, then the engagement counts. The sheet
                // is where the full metadata lives; the list stays one line per item.
                if (log.author != null) {
                    Text(
                        log.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                val metaLine = listOfNotNull(
                    log.platform ?: "抖音",
                    log.duration?.let { formatDuration(it) },
                    log.createTime?.let { formatPostDate(it) },
                ).joinToString(" · ")
                Text(
                    metaLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // When this entry was produced. It describes the record rather than the
                // clip, so it sits under the facts about the post.
                Text(
                    "下载于 " + formatClock(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                val stats = listOfNotNull(
                    log.likeCount?.takeIf { it > 0 }?.let { "点赞 " + formatCount(it) },
                    log.commentCount?.takeIf { it > 0 }?.let { "评论 " + formatCount(it) },
                    log.shareCount?.takeIf { it > 0 }?.let { "分享 " + formatCount(it) },
                    log.collectCount?.takeIf { it > 0 }?.let { "收藏 " + formatCount(it) },
                )
                if (stats.isNotEmpty()) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            stats.forEach { entry ->
                                Text(
                                    entry,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

                // Both actions sit in one row: closing is the way out of this sheet,
                // and copying next to it is the other thing people come here to do.
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                val closeScope = rememberCoroutineScope()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            clipboardManager.setText(
                                androidx.compose.ui.text.buildAnnotatedString {
                                    append(extractBareUrl(log.url))
                                },
                            )
                            android.widget.Toast.makeText(context, "已复制链接", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(100),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("复制链接")
                    }

                    Button(
                        onClick = {
                            closeScope.launch {
                                sheetState.hide()
                                selectedLog = null
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(100),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
        }
            }
    }
}

@Composable
fun StatusInfo(state: DownloadState, onOpenFolder: () -> Unit) {
    val color = when (state) {
        is DownloadState.Success -> MaterialTheme.colorScheme.primary
        is DownloadState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (state is DownloadState.Success) Modifier.clickable { onOpenFolder() } else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (state is DownloadState.Error) Icons.Default.Error else Icons.Default.Movie,
                null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Column {
                val title = when (state) {
                    is DownloadState.Downloading -> "Downloading... ${(state.progress * 100).toInt()}%"
                    is DownloadState.Success -> "Done!"
                    is DownloadState.Error -> "Error"
                    else -> ""
                }
                Text(title, style = MaterialTheme.typography.labelLarge, color = color)
                if (state is DownloadState.Error) {
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
                } else if (state is DownloadState.Success) {
                    Text(stringResource(R.string.check_your_selected_folder), style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
        }
    }
}


/** Chinese labels for the download mode chips. */
private fun modeLabel(mode: String): String = when (mode) {
    "auto" -> "自动"
    "video" -> "视频"
    "audio" -> "音频"
    "image" -> "图片"
    else -> mode
}

