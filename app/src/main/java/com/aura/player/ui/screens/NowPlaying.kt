package com.aura.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.aura.player.albumKeyOf
import com.aura.player.data.TrackEntity
import com.aura.player.formatDuration
import com.aura.player.playback.PlayerController
import com.aura.player.playback.QueueItem
import com.aura.player.ui.LocalAccent
import com.aura.player.ui.LocalAura
import com.aura.player.ui.LocalFavorites
import com.aura.player.ui.Route
import com.aura.player.ui.components.Artwork
import com.aura.player.ui.components.GlassIconButton
import com.aura.player.ui.components.HeartButton
import com.aura.player.ui.components.PlayPauseGlyph
import com.aura.player.ui.components.rememberAnchor
import com.aura.player.ui.components.rememberArtColors
import com.aura.player.ui.glass.AmbientBackground
import com.aura.player.ui.glass.Backdrop
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.LocalBackdrop
import com.aura.player.ui.glass.LocalReduceMotion
import com.aura.player.ui.glass.Spr
import com.aura.player.ui.glass.glass
import com.aura.player.ui.glass.glow
import com.aura.player.ui.glass.pressable
import com.aura.player.ui.glass.rubberBand
import com.aura.player.ui.glass.spr
import com.aura.player.ui.glass.sprRaw
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Playback position is polled here (4 Hz, only while playing and visible) into a primitive state.
 * Only draw lambdas and two tiny Text composables read it, so nothing else recomposes.
 */
@Composable
fun rememberProgress(player: PlayerController, active: Boolean, key: Any?): State<Long> {
    val s = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, key) {
        s.longValue = player.position()
        while (active && isActive) {
            delay(250)
            s.longValue = player.position()
        }
    }
    return s
}

@Composable
fun NowPlayingLayer() {
    val env = LocalAura.current
    val sheet = env.sheet
    val ps by env.player.state.collectAsStateWithLifecycle()
    val visible by remember { derivedStateOf { sheet.progress.value > 0.001f } }
    val topShape = remember { RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val safeMaxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        SideEffect { sheet.heightPx = safeMaxH }
        if (visible && ps.current != null) {
            BackHandler(enabled = sheet.open) { sheet.collapse() }
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val scrimA = (sheet.progress.value.coerceIn(0f, 1f) * 0.52f).coerceIn(0f, 1f)
                        drawRect(Color.Black.copy(alpha = scrimA))
                    }
                    .graphicsLayer {
                        val rawP = sheet.progress.value
                        val slideP = rawP.coerceIn(0f, 1f)
                        val over = (rawP - 1f).coerceIn(0f, 0.25f)
                        translationY = (1f - slideP) * size.height
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        val dragScale = 0.94f + 0.06f * slideP
                        scaleX = (dragScale * (1f - over * 0.10f)).coerceIn(0.85f, 1.15f)
                        scaleY = (dragScale * (1f + over * 0.32f)).coerceIn(0.85f, 1.20f)
                        shape = topShape
                        clip = true
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { sheet.dragBy(it) },
                        onDragStopped = { sheet.settle(it) },
                    ),
            ) { NowPlayingContent() }
        }
    }
}

private fun QueueItem.toFallbackTrackEntity(): TrackEntity = TrackEntity(
    id = mediaId.toLongOrNull() ?: -(mediaId.hashCode().toLong() and 0x7fffffffL) - 1L,
    uri = artUri.orEmpty(),
    title = title.ifBlank { "Untitled" },
    artist = artist.ifBlank { "Unknown Artist" },
    album = album.ifBlank { "Unknown Album" },
    albumKey = albumKey.ifBlank { albumKeyOf(0L, album.ifBlank { "Unknown Album" }, artist.ifBlank { "Unknown Artist" }) },
    albumId = 0L,
    duration = 0L,
    dateAdded = 0L,
    trackNo = 0,
    source = 0,
)

@Composable
private fun NowPlayingContent() {
    val env = LocalAura.current
    val ps by env.player.state.collectAsStateWithLifecycle()
    val queue by env.player.queue.collectAsStateWithLifecycle()
    val track = ps.current ?: return
    val accent = LocalAccent.current
    val ambienceOn by env.settings.ambience.collectAsStateWithLifecycle()
    val artColors = rememberArtColors(track.artUri, accent)
    val colors = if (ambienceOn) artColors else accent
    val favId = track.mediaId.toLongOrNull()
    val isFav = favId != null && favId in LocalFavorites.current
    val progress = rememberProgress(env.player, ps.isPlaying, track.mediaId)
    val drag = remember { mutableStateOf<Float?>(null) }
    val menuAnchor = rememberAnchor()
    val reduce = LocalReduceMotion.current

    val npBackdropLayer = rememberGraphicsLayer()
    val npBackdrop = remember(npBackdropLayer) { Backdrop(npBackdropLayer) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { npBackdrop.rootOffset = it.positionInRoot() },
    ) {
        val compactHeight = maxHeight < 640.dp

        // 1. Record the Now Playing ambient scene into npBackdropLayer
        Box(
            Modifier.fillMaxSize().drawWithContent {
                npBackdropLayer.record { this@drawWithContent.drawContent() }
                drawLayer(npBackdropLayer)
            },
        ) {
            AmbientBackground(colors, Modifier.fillMaxSize(), intensity = 1.22f)
        }

        // 2. Render Now Playing controls with clean neutral Liquid Glass over npBackdrop
        CompositionLocalProvider(LocalBackdrop provides npBackdrop) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = if (compactHeight) 4.dp else 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Clean iOS-style grabber handle (no double border tube)
                Box(
                    Modifier
                        .padding(top = 6.dp, bottom = if (compactHeight) 4.dp else 8.dp)
                        .size(width = 36.dp, height = 4.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f)),
                )

                // Top header bar
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(
                        Icons.Rounded.KeyboardArrowDown,
                        onClick = { env.sheet.collapse() },
                        size = 42.dp,
                        level = GlassLevel.Regular,
                    )
                    Text(
                        "NOW PLAYING",
                        style = AuraType.Label,
                        color = AuraColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    GlassIconButton(
                        Icons.Rounded.MoreHoriz,
                        onClick = {
                            env.scope.launch {
                                val entity = favId?.let { env.repo.dao.trackById(it) } ?: track.toFallbackTrackEntity()
                                env.trackMenu(menuAnchor.rect, entity, isFav)
                            }
                        },
                        modifier = menuAnchor.modifier,
                        size = 42.dp,
                        level = GlassLevel.Regular,
                    )
                }

                // Square artwork pager
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = if (compactHeight) 6.dp else 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalBackdrop provides null) {
                        ArtworkPager(
                            mediaId = track.mediaId,
                            artUri = track.artUri,
                            title = track.title,
                            playing = ps.isPlaying,
                            glowColor = colors.a,
                        )
                    }
                }

                // Track title, artist & favorite heart
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .pressable(
                                onClick = {
                                    env.scope.launch {
                                        val artistName = favId?.let { env.repo.dao.trackById(it)?.artist } ?: track.artist
                                        if (artistName.isNotBlank()) env.openRoute(Route.Artist(artistName))
                                    }
                                },
                                scaleTo = 0.97f,
                            ),
                    ) {
                        AnimatedContent(
                            targetState = track.title to track.artist,
                            transitionSpec = {
                                (fadeIn(sprRaw(Spr.Navigation, reduce)) +
                                    slideInVertically(sprRaw(Spr.Navigation, reduce)) { it / 3 } +
                                    scaleIn(sprRaw(Spr.Navigation, reduce), initialScale = 0.94f)) togetherWith
                                    (fadeOut(sprRaw(Spr.Critical, reduce)) +
                                        slideOutVertically(sprRaw(Spr.Critical, reduce)) { -it / 4 })
                            },
                            label = "titleSwap",
                        ) { (title, artist) ->
                            Column {
                                Text(
                                    title,
                                    style = AuraType.Headline,
                                    color = AuraColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    artist,
                                    style = AuraType.Body.copy(fontSize = 17.sp),
                                    color = AuraColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    HeartButton(
                        isFav = isFav,
                        onClick = { favId?.let { env.toggleFavorite(it) } },
                        size = 24.dp,
                        boxSize = 46.dp,
                    )
                }

                Spacer(Modifier.height(if (compactHeight) 8.dp else 14.dp))
                SeekBar(env.player, progress, ps.duration, drag)
                TimeRow(progress, ps.duration, drag)
                Spacer(Modifier.height(if (compactHeight) 6.dp else 12.dp))

                // Clean, neutral Liquid Glass Transport Controls (modeled after iOS 26 Control Center / Apple Music)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToggleIcon(Icons.Rounded.Shuffle, ps.shuffle, colors.c) { env.player.toggleShuffle() }

                    Box(
                        Modifier
                            .size(54.dp)
                            .glass(CircleShape, GlassLevel.Regular, borderAlpha = 0.95f)
                            .pressable({ env.player.previous() }, scaleTo = 0.84f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = AuraColors.TextPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }

                    val playScale by animateFloatAsState(
                        if (ps.isPlaying) 1f else 0.94f,
                        spr(Spr.Bouncy),
                        label = "playPulse",
                    )
                    Box(
                        Modifier
                            .size(76.dp)
                            .graphicsLayer {
                                val s = playScale.coerceIn(0.85f, 1.15f)
                                scaleX = s
                                scaleY = s
                            }
                            .glow(colors.a, 88.dp, 0.45f)
                            .glass(CircleShape, GlassLevel.Lens, borderAlpha = 1.15f)
                            .pressable({ env.player.togglePlay() }, scaleTo = 0.86f),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayPauseGlyph(ps.isPlaying, Color.White, Modifier.size(28.dp))
                    }

                    Box(
                        Modifier
                            .size(54.dp)
                            .glass(CircleShape, GlassLevel.Regular, borderAlpha = 0.95f)
                            .pressable({ env.player.next() }, scaleTo = 0.84f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = AuraColors.TextPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }

                    ToggleIcon(
                        if (ps.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        ps.repeat != Player.REPEAT_MODE_OFF,
                        colors.c,
                    ) { env.player.cycleRepeat() }
                }

                Spacer(Modifier.height(if (compactHeight) 8.dp else 16.dp))

                // Up Next Liquid Glass Pill
                Row(
                    Modifier
                        .height(44.dp)
                        .glass(CircleShape, GlassLevel.Regular, borderAlpha = 0.95f)
                        .pressable({ env.showQueue() }, scaleTo = 0.92f)
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.QueueMusic, null, tint = AuraColors.TextPrimary, modifier = Modifier.size(20.dp))
                    Text("Up next", style = AuraType.Body, color = AuraColors.TextPrimary)
                    if (queue.isNotEmpty()) {
                        Text(
                            "· ${queue.size}",
                            style = AuraType.Caption,
                            color = AuraColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, on: Boolean, onColor: Color, onClick: () -> Unit) {
    val tint by animateColorAsState(if (on) onColor else AuraColors.TextTertiary, spr(Spr.Critical), label = "tg")
    val dot by animateFloatAsState(if (on) 1f else 0f, spr(Spr.Bouncy), label = "dot")
    Box(
        Modifier
            .size(46.dp)
            .then(
                if (on) Modifier.glass(CircleShape, GlassLevel.Subtle, borderAlpha = 0.9f)
                else Modifier,
            )
            .pressable(onClick, scaleTo = 0.82f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        val clampedDot = dot.coerceIn(0f, 1.4f)
        if (clampedDot > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(4.dp)
                    .graphicsLayer {
                        scaleX = clampedDot
                        scaleY = clampedDot
                    }
                    .background(tint, CircleShape),
            )
        }
    }
}

@Composable
private fun ArtworkPager(
    mediaId: String,
    artUri: String?,
    title: String,
    playing: Boolean,
    glowColor: Color,
) {
    val env = LocalAura.current
    val reduce = LocalReduceMotion.current
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(if (playing) 1f else 0.88f, spr(Spr.Artwork), label = "artScale")
    val shape = RoundedCornerShape(34.dp)

    BoxWithConstraints(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight).coerceAtLeast(64.dp)

        AnimatedContent(
            targetState = Triple(mediaId, artUri, title),
            transitionSpec = {
                (fadeIn(sprRaw(Spr.Artwork, reduce)) +
                    scaleIn(sprRaw(Spr.Artwork, reduce), initialScale = 0.82f)) togetherWith
                    (fadeOut(sprRaw(Spr.Critical, reduce)) +
                        scaleOut(sprRaw(Spr.Critical, reduce), targetScale = 0.90f))
            },
            contentKey = { it.first },
            label = "artSwap",
        ) { (_, uri, t) ->
            Artwork(
                uri = uri,
                seed = t,
                modifier = Modifier
                    .size(side)
                    .graphicsLayer {
                        val safeW = size.width.coerceAtLeast(1f)
                        val normDrag = (abs(offset.value) / safeW).coerceIn(0f, 1f)
                        val baseS = scale.coerceIn(0.75f, 1.15f)
                        scaleX = baseS * (1f + 0.06f * normDrag)
                        scaleY = baseS * (1f - 0.04f * normDrag)
                        translationX = offset.value
                        rotationZ = (offset.value / 36f).coerceIn(-12f, 12f)
                    }
                    .glow(glowColor, 220.dp, 0.45f)
                    .glass(shape, GlassLevel.Subtle, borderAlpha = 0.8f)
                    .pointerInput(mediaId) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val o = offset.value
                                scope.launch {
                                    when {
                                        o < -w * 0.25f -> env.player.next()
                                        o > w * 0.25f -> env.player.previous()
                                    }
                                    offset.animateTo(0f, sprRaw(Spr.Artwork, reduce))
                                }
                            },
                            onDragCancel = {
                                scope.launch { offset.animateTo(0f, sprRaw(Spr.Artwork, reduce)) }
                            },
                            onHorizontalDrag = { _, dx ->
                                scope.launch { offset.snapTo(offset.value + dx * 0.82f) }
                            },
                        )
                    },
                shape = shape,
                sizePx = 720,
            )
        }
    }
}

@Composable
private fun SeekBar(
    player: PlayerController,
    pos: State<Long>,
    duration: Long,
    drag: MutableState<Float?>,
) {
    val isDragging = drag.value != null
    val thick by animateFloatAsState(if (isDragging) 11f else 5.5f, spr(Spr.Snappy), label = "thick")
    var overScrub by remember { mutableFloatStateOf(0f) }
    val stretch by animateFloatAsState(if (isDragging) overScrub else 0f, spr(Spr.Bouncy), label = "scrubStretch")
    val safeDur = duration.coerceAtLeast(0L)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .graphicsLayer {
                val s = abs(stretch).coerceIn(0f, 0.10f)
                scaleX = 1f + s
                scaleY = (1f - s * 0.5f).coerceAtLeast(0.90f)
                translationX = stretch * 24f
            }
            .pointerInput(safeDur) {
                detectTapGestures { o ->
                    val w = size.width.toFloat()
                    if (w > 1f && safeDur > 0L) {
                        val f = (o.x / w).coerceIn(0f, 1f)
                        player.seekTo((f * safeDur).toLong())
                    }
                }
            }
            .pointerInput(safeDur) {
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        drag.value = (start.x / w).coerceIn(0f, 1f)
                        overScrub = 0f
                    },
                    onHorizontalDrag = { c, _ ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val raw = c.position.x / w
                        drag.value = raw.coerceIn(0f, 1f)
                        overScrub = when {
                            raw < 0f -> rubberBand(raw, 0.10f, 0.45f)
                            raw > 1f -> rubberBand(raw - 1f, 0.10f, 0.45f)
                            else -> 0f
                        }
                    },
                    onDragEnd = {
                        drag.value?.let { f ->
                            if (safeDur > 0L) player.seekTo((f.coerceIn(0f, 1f) * safeDur).toLong())
                        }
                        drag.value = null
                        overScrub = 0f
                    },
                    onDragCancel = {
                        drag.value = null
                        overScrub = 0f
                    },
                )
            },
    ) {
        val w = size.width
        if (w <= 1f) return@Canvas
        val frac = (drag.value ?: if (safeDur > 0L) (pos.value.toFloat() / safeDur.toFloat()) else 0f).coerceIn(0f, 1f)
        val h = thick.coerceIn(4f, 18f).dp.toPx()
        val y = (size.height - h) / 2f
        val r = CornerRadius(h / 2f)

        // iOS 26 / Apple Music style capsule progress bar: translucent white track + luminous white fill
        drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(0f, y), Size(w, h), r)

        val activeW = (w * frac).coerceAtLeast(0f)
        if (activeW > 0.5f) {
            drawRoundRect(
                Color.White.copy(alpha = if (isDragging) 0.96f else 0.82f),
                Offset(0f, y),
                Size(activeW.coerceAtLeast(h), h),
                r,
            )
        }
    }
}

@Composable
private fun TimeRow(pos: State<Long>, duration: Long, drag: State<Float?>) {
    val safeDur = duration.coerceAtLeast(0L)
    val currentMs = drag.value?.let { (it.coerceIn(0f, 1f) * safeDur).toLong() } ?: pos.value.coerceAtLeast(0L)
    Row(Modifier.fillMaxWidth()) {
        Text(
            formatDuration(currentMs),
            style = AuraType.Caption,
            color = AuraColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatDuration(safeDur),
            style = AuraType.Caption,
            color = AuraColors.TextSecondary,
        )
    }
}

/** Floating Liquid Glass Mini Player. Tap or drag up to expand with a spring; progress hairline is draw-only. */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val env = LocalAura.current
    val ps by env.player.state.collectAsStateWithLifecycle()
    val track = ps.current ?: return
    val progress = rememberProgress(env.player, ps.isPlaying, track.mediaId)
    val accent = LocalAccent.current
    val reduce = LocalReduceMotion.current
    val shape = RoundedCornerShape(33.dp)

    val artScale by animateFloatAsState(if (ps.isPlaying) 1f else 0.92f, spr(Spr.Artwork), label = "miniArtScale")

    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .glass(shape, GlassLevel.Strong, borderAlpha = 1.0f)
            .drawWithProgress(progress, ps.duration, accent.c)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { env.sheet.dragBy(it) },
                onDragStopped = { env.sheet.settle(it) },
            )
            .pressable({ env.sheet.expand() }, scaleTo = 0.965f)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(
            uri = track.artUri,
            seed = track.title,
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    val s = artScale.coerceIn(0.85f, 1.1f)
                    scaleX = s
                    scaleY = s
                },
            shape = CircleShape,
            sizePx = 140,
        )
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = track.title to track.artist,
                transitionSpec = {
                    (fadeIn(sprRaw(Spr.Navigation, reduce)) +
                        slideInVertically(sprRaw(Spr.Navigation, reduce)) { it / 3 }) togetherWith
                        fadeOut(sprRaw(Spr.Critical, reduce))
                },
                label = "miniTitle",
            ) { (t, a) ->
                Column {
                    Text(t, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a, style = AuraType.Caption, color = AuraColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .glass(CircleShape, GlassLevel.Lens, borderAlpha = 1.0f)
                .pressable({ env.player.togglePlay() }, scaleTo = 0.84f),
            contentAlignment = Alignment.Center,
        ) {
            PlayPauseGlyph(ps.isPlaying, AuraColors.TextPrimary, Modifier.size(16.dp))
        }
        Box(
            Modifier
                .size(40.dp)
                .pressable({ env.player.next() }, scaleTo = 0.82f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = AuraColors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun Modifier.drawWithProgress(pos: State<Long>, duration: Long, color: Color): Modifier =
    this.drawWithContent {
        drawContent()
        if (duration > 0L && size.width > 64.dp.toPx()) {
            val f = (pos.value.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            val inset = 28.dp.toPx()
            val w = (size.width - inset * 2f).coerceAtLeast(0f)
            val y = size.height - 4.dp.toPx()
            drawRoundRect(
                Color.White.copy(alpha = 0.16f),
                Offset(inset, y),
                Size(w, 2.dp.toPx()),
                CornerRadius(1.dp.toPx()),
            )
            if (w * f > 0.5f) {
                drawRoundRect(
                    color,
                    Offset(inset, y),
                    Size(w * f, 2.dp.toPx()),
                    CornerRadius(1.dp.toPx()),
                )
            }
        }
    }
