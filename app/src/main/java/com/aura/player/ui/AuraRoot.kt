package com.aura.player.ui

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.player.AuraApp
import com.aura.player.art.AmbientColors
import com.aura.player.data.AccentPalettes
import com.aura.player.ui.components.OverlayController
import com.aura.player.ui.components.OverlayHost
import com.aura.player.ui.components.rememberArtColors
import com.aura.player.ui.glass.AmbientBackground
import com.aura.player.ui.glass.Backdrop
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.LocalBackdrop
import com.aura.player.ui.glass.LocalGlassQuality
import com.aura.player.ui.glass.LocalHaptics
import com.aura.player.ui.glass.LocalReduceMotion
import com.aura.player.ui.glass.Spr
import com.aura.player.ui.glass.glass
import com.aura.player.ui.glass.pressable
import com.aura.player.ui.glass.spr
import com.aura.player.ui.glass.sprRaw
import com.aura.player.ui.glass.stretchAxes
import com.aura.player.ui.screens.AlbumScreen
import com.aura.player.ui.screens.ArtistScreen
import com.aura.player.ui.screens.HomeScreen
import com.aura.player.ui.screens.LibraryScreen
import com.aura.player.ui.screens.MiniPlayer
import com.aura.player.ui.screens.NowPlayingLayer
import com.aura.player.ui.screens.PlaylistScreen
import com.aura.player.ui.screens.PlaylistsScreen
import com.aura.player.ui.screens.SearchScreen
import com.aura.player.ui.screens.SettingsScreen
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraTheme
import com.aura.player.ui.theme.AuraType
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AuraRoot(app: AuraApp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = app.settings

    val quality by st.quality.collectAsStateWithLifecycle()
    val accentIdx by st.accent.collectAsStateWithLifecycle()
    val themeMode by st.themeMode.collectAsStateWithLifecycle()
    val ambienceOn by st.ambience.collectAsStateWithLifecycle()
    val userReduce by st.reduceMotion.collectAsStateWithLifecycle()
    val haptics by st.haptics.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        1 -> true
        2 -> false
        else -> systemDark
    }

    val systemReduce = remember {
        try {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Throwable) {
            false
        }
    }
    val reduce = userReduce || systemReduce

    val overlay = remember { OverlayController() }
    val nav = remember { Navigator() }
    val sheet = remember { PlayerSheet(scope) }
    SideEffect {
        sheet.bindScope(scope)
        sheet.reduceMotion = reduce
    }
    val env = remember { AuraEnv(app, overlay, nav, sheet) }

    val perms = remember {
        buildList {
            add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { app.repo.rescan() }
    }
    env.requestAudio = { permLauncher.launch(perms) }
    LaunchedEffect(Unit) {
        if (app.repo.hasAudioPermission()) app.repo.rescan() else permLauncher.launch(perms)
    }

    val ps by app.player.state.collectAsStateWithLifecycle()
    val favorites by remember { app.repo.dao.favoriteIds().map { it.toHashSet() as Set<Long> } }.collectAsStateWithLifecycle(emptySet())
    val pal = AccentPalettes.getOrElse(accentIdx) { AccentPalettes.first() }
    val accent = remember(pal) { AmbientColors(pal.a, pal.b, pal.c) }
    val artColors = rememberArtColors(ps.current?.artUri, accent)
    val sceneColors = if (ambienceOn) artColors else accent

    // Ambient backdrop layer: allows content cards & buttons to sample the smooth ambient scene!
    val ambientLayer = rememberGraphicsLayer()
    val ambientBackdrop = remember(ambientLayer) { Backdrop(ambientLayer) }

    // Full scene backdrop layer: sampled by floating chrome (MiniPlayer, FloatingNav, Overlays).
    val backdropLayer = rememberGraphicsLayer()
    val backdrop = remember(backdropLayer) { Backdrop(backdropLayer) }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hasTrack = ps.current != null
    val miniExtra by animateDpAsState(if (hasTrack) 76.dp else 0.dp, sprRaw(Spr.Soft, reduce), label = "miniPad")
    val bottomPad = navInset + 12.dp + 64.dp + miniExtra + 24.dp

    AuraTheme(darkTheme = isDark) {
        CompositionLocalProvider(
            LocalAura provides env,
            LocalReduceMotion provides reduce,
            LocalHaptics provides haptics,
            LocalGlassQuality provides quality,
            LocalAccent provides accent,
            LocalFavorites provides favorites,
            LocalCurrentId provides ps.current?.mediaId,
            LocalBottomPad provides bottomPad,
        ) {
            Box(Modifier.fillMaxSize().background(AuraColors.Bg)) {
                // ---- Scene: recorded once per frame into the backdrop layer, then shown ----
                Box(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            val pos = it.positionInRoot()
                            backdrop.rootOffset = pos
                            ambientBackdrop.rootOffset = pos
                        }
                        .drawWithContent {
                            backdropLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(backdropLayer)
                        },
                ) {
                    Box(
                        Modifier.fillMaxSize().drawWithContent {
                            ambientLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(ambientLayer)
                        },
                    ) {
                        AmbientBackground(sceneColors, Modifier.fillMaxSize())
                    }
                    // Content screens sample the smooth ambient backdrop so cards & pills look like true glass!
                    CompositionLocalProvider(LocalBackdrop provides ambientBackdrop) {
                        ScreenHost(env)
                        NowPlayingLayer()
                    }
                }

                // ---- Floating layer: true backdrop-blurred & AGSL-refracted Liquid Glass ----
                CompositionLocalProvider(LocalBackdrop provides backdrop) {
                    val p = sheet.progress
                    val bgColor = AuraColors.Bg
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .drawBehind {
                                // Progressive bottom vignette so scrolling cards fade cleanly below the floating pill bar.
                                drawRect(
                                    Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        0.55f to bgColor.copy(alpha = 0.55f),
                                        1.0f to bgColor.copy(alpha = 0.90f),
                                    ),
                                )
                            }
                            .padding(horizontal = 18.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 10.dp)
                            .graphicsLayer {
                                val prog = p.value.coerceIn(0f, 1.15f)
                                translationY = prog * 150.dp.toPx()
                                scaleX = (1f - prog * 0.06f).coerceIn(0.88f, 1.04f)
                                scaleY = (1f + prog * 0.08f).coerceIn(0.92f, 1.12f)
                                alpha = (1f - prog * 1.55f).coerceIn(0f, 1f)
                            },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AnimatedVisibility(
                                visible = hasTrack,
                                enter = fadeIn(sprRaw(Spr.MiniPlayer, reduce)) +
                                    expandVertically(sprRaw(Spr.MiniPlayer, reduce)) +
                                    scaleIn(sprRaw(Spr.MiniPlayer, reduce), initialScale = 0.88f),
                                exit = fadeOut(sprRaw(Spr.Critical, reduce)) +
                                    shrinkVertically(sprRaw(Spr.Critical, reduce)) +
                                    scaleOut(sprRaw(Spr.Critical, reduce), targetScale = 0.90f),
                            ) { MiniPlayer() }
                            FloatingNav(nav)
                        }
                    }
                    OverlayHost(overlay)
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ScreenHost(env: AuraEnv) {
    val nav = env.nav
    val holder = rememberSaveableStateHolder()
    val reduce = LocalReduceMotion.current
    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.back() }

    AnimatedContent(
        targetState = nav.current,
        transitionSpec = {
            val d = nav.dir
            (fadeIn(sprRaw(Spr.Navigation, reduce)) +
                slideInHorizontally(sprRaw(Spr.Navigation, reduce)) { d * it / 6 } +
                scaleIn(sprRaw(Spr.Navigation, reduce), initialScale = 0.94f)) togetherWith
                (fadeOut(sprRaw(Spr.Critical, reduce)) +
                    slideOutHorizontally(sprRaw(Spr.Snappy, reduce)) { -d * it / 9 } +
                    scaleOut(sprRaw(Spr.Critical, reduce), targetScale = 0.96f)) using
                SizeTransform(clip = false)
        },
        label = "screens",
    ) { route ->
        holder.SaveableStateProvider(route.toString()) {
            when (route) {
                is Route.TabRoute -> when (route.tab) {
                    Tab.Home -> HomeScreen()
                    Tab.Library -> LibraryScreen()
                    Tab.Search -> SearchScreen()
                    Tab.Playlists -> PlaylistsScreen()
                    Tab.Settings -> SettingsScreen()
                }
                is Route.Album -> AlbumScreen(route.key)
                is Route.Artist -> ArtistScreen(route.name)
                is Route.Playlist -> PlaylistScreen(route.id)
            }
        }
    }
}

/**
 * Floating Liquid Glass tab bar modeled after Telegram Android & iOS 26:
 * Clean, neutral refracted capsule with a sleek horizontal active pill lens (no swollen blue ring!).
 */
@Composable
private fun FloatingNav(nav: Navigator) {
    val tabs = Tab.entries
    val selected = tabs.indexOf(nav.tab).coerceAtLeast(0)
    val pos by animateFloatAsState(selected.toFloat(), spr(Spr.Navigation), label = "navPos")

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .glass(RoundedCornerShape(32.dp), GlassLevel.Strong, borderAlpha = 1.0f)
            .padding(4.dp),
    ) {
        val tabW = maxWidth / tabs.size
        // Sleek, neutral Liquid Glass active tab lens (stretches horizontally while moving).
        Box(
            Modifier
                .offset { IntOffset((tabW.toPx() * pos).roundToInt(), 0) }
                .width(tabW)
                .fillMaxHeight()
                .graphicsLayer {
                    val dist = abs(pos - selected).coerceIn(0f, 1.5f)
                    val (sx, sy) = stretchAxes(dist * 1.1f, horizontal = true)
                    scaleX = sx
                    scaleY = sy
                }
                .glass(RoundedCornerShape(28.dp), GlassLevel.Lens, borderAlpha = 1.1f),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, tab ->
                val on = i == selected
                val lift by animateFloatAsState(if (on) 1f else 0f, spr(Spr.Bouncy), label = "tabLift")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable({ nav.select(tab) }, scaleTo = 0.88f)
                        .graphicsLayer {
                            val clampedLift = lift.coerceIn(-0.2f, 1.3f)
                            translationY = -1.5.dp.toPx() * clampedLift
                            val s = 1f + 0.05f * clampedLift
                            scaleX = s
                            scaleY = s
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val reduce = LocalReduceMotion.current
                    AnimatedContent(
                        targetState = on,
                        transitionSpec = {
                            (fadeIn(sprRaw(Spr.Snappy, reduce)) +
                                scaleIn(sprRaw(Spr.Bouncy, reduce), initialScale = 0.6f)) togetherWith
                                fadeOut(sprRaw(Spr.Critical, reduce))
                        },
                        label = "navIcon",
                    ) { sel ->
                        val activeTint = if (AuraColors.isDark) Color.White else AuraColors.TextPrimary
                        Icon(
                            if (sel) tab.selectedIcon else tab.icon,
                            contentDescription = tab.label,
                            tint = if (sel) activeTint else AuraColors.TextSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    val activeText = if (AuraColors.isDark) Color.White else AuraColors.TextPrimary
                    Text(
                        tab.label,
                        style = AuraType.Label,
                        color = if (on) activeText else AuraColors.TextTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
