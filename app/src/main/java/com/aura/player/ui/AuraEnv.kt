package com.aura.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import com.aura.player.AuraApp
import com.aura.player.art.AmbientColors
import com.aura.player.data.TrackEntity
import com.aura.player.ui.components.MenuItemSpec
import com.aura.player.ui.components.OverlayController
import com.aura.player.ui.glass.Spr
import com.aura.player.ui.glass.rubberBand
import com.aura.player.ui.screens.AddToPlaylistSheet
import com.aura.player.ui.screens.QueueSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Library("Library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    Search("Search", Icons.Outlined.Search, Icons.Rounded.Search),
    Playlists("Playlists", Icons.Outlined.QueueMusic, Icons.Rounded.QueueMusic),
    Settings("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

sealed interface Route {
    data class TabRoute(val tab: Tab) : Route
    data class Album(val key: String) : Route
    data class Artist(val name: String) : Route
    data class Playlist(val id: Long) : Route
}

@Stable
class Navigator {
    var tab by mutableStateOf(Tab.Home)
        private set
    val stack = mutableStateListOf<Route>()
    var dir by mutableIntStateOf(1)
        private set
    var libraryPage by mutableIntStateOf(0)

    val current: Route get() = stack.lastOrNull() ?: Route.TabRoute(tab)

    fun select(t: Tab) {
        if (t == tab && stack.isEmpty()) return
        dir = if (t.ordinal >= tab.ordinal) 1 else -1
        stack.clear()
        tab = t
    }

    fun open(r: Route) { dir = 1; stack.add(r) }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        dir = -1
        stack.removeAt(stack.lastIndex)
        return true
    }
}

/**
 * Mini-player <-> Now Playing expansion controller.
 *
 * CRITICAL BUG FIX:
 * Previously, [PlayerSheet] was passed `AuraApp.appScope` (`SupervisorJob() + Dispatchers.Main.immediate`),
 * which has NO [MonotonicFrameClock] in its `CoroutineContext`. Calling `progress.animateTo(...)`
 * threw `IllegalStateException: A MonotonicFrameClock is not available in this CoroutineContext` on the main thread
 * the instant the user opened Now Playing.
 *
 * [PlayerSheet] now binds to the composition's `rememberCoroutineScope()` (which provides `AndroidUiFrameClock`),
 * updates its bound scope across recompositions via [bindScope], and safely falls back to [Animatable.snapTo]
 * if ever invoked without a frame clock.
 */
@Stable
class PlayerSheet(initialScope: CoroutineScope) {
    private var uiScope: CoroutineScope = initialScope
    val progress = Animatable(0f)
    var heightPx by mutableFloatStateOf(1f)
    var open by mutableStateOf(false)
        private set
    var reduceMotion = false

    fun bindScope(scope: CoroutineScope) {
        uiScope = scope
    }

    private fun spec(kind: Spr) =
        if (reduceMotion) snap<Float>() else spring(dampingRatio = kind.damping, stiffness = kind.stiffness)

    private fun animateProgressTo(target: Float, kind: Spr, initialVelocity: Float = 0f) {
        uiScope.launch {
            try {
                if (reduceMotion || coroutineContext[MonotonicFrameClock] == null) {
                    progress.snapTo(target)
                } else {
                    progress.animateTo(
                        targetValue = target,
                        animationSpec = spec(kind),
                        initialVelocity = initialVelocity,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                try { progress.snapTo(target) } catch (_: Throwable) {}
            }
        }
    }

    fun expand() {
        open = true
        animateProgressTo(1f, Spr.MiniPlayer)
    }

    fun collapse() {
        open = false
        animateProgressTo(0f, Spr.BottomSheet)
    }

    fun dragBy(deltaY: Float) {
        val safeH = heightPx.coerceAtLeast(1f)
        val rawNext = progress.value - deltaY / safeH
        // Allow subtle upward rubber-band over-pull past 1.0f for physical elastic feel.
        val clamped = when {
            rawNext > 1f -> 1f + rubberBand(rawNext - 1f, 0.14f, 0.45f)
            rawNext < 0f -> 0f
            else -> rawNext
        }
        uiScope.launch {
            try { progress.snapTo(clamped) } catch (_: Throwable) {}
        }
    }

    fun settle(velocityY: Float) {
        val safeH = heightPx.coerceAtLeast(1f)
        val target = when {
            velocityY < -750f -> 1f
            velocityY > 750f -> 0f
            progress.value > 0.46f -> 1f
            else -> 0f
        }
        open = target == 1f
        val v = (-velocityY / safeH).coerceIn(-12f, 12f)
        animateProgressTo(
            target = target,
            kind = if (target == 1f) Spr.MiniPlayer else Spr.BottomSheet,
            initialVelocity = v,
        )
    }
}

data class PlaylistCtx(val id: Long, val index: Int, val size: Int)

@Stable
class AuraEnv(
    val app: AuraApp,
    val overlay: OverlayController,
    val nav: Navigator,
    val sheet: PlayerSheet,
) {
    val repo get() = app.repo
    val player get() = app.player
    val settings get() = app.settings
    val scope: CoroutineScope get() = app.appScope
    var requestAudio: () -> Unit = {}

    fun play(tracks: List<TrackEntity>, index: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val start = if (shuffle) tracks.indices.randomOrNull() ?: 0 else index.coerceIn(0, tracks.lastIndex)
        player.play(tracks, start, shuffle)
    }

    fun playFlow(flow: Flow<List<TrackEntity>>, shuffle: Boolean = false) {
        scope.launch {
            val list = try { flow.first() } catch (_: Throwable) { emptyList() }
            if (list.isNotEmpty()) play(list, 0, shuffle)
        }
    }

    fun shuffleAll() {
        scope.launch {
            val all = try { repo.songsList("", 0) } catch (_: Throwable) { emptyList() }
            if (all.isNotEmpty()) play(all, 0, true)
        }
    }

    fun openRoute(r: Route) {
        if (sheet.open) sheet.collapse()
        nav.open(r)
    }

    fun toggleFavorite(id: Long) { scope.launch { repo.toggleFavorite(id) } }

    fun trackMenu(anchor: Rect, t: TrackEntity, isFav: Boolean, playlist: PlaylistCtx? = null) {
        val items = mutableListOf(
            MenuItemSpec("Play next", Icons.Rounded.QueuePlayNext) { player.playNext(t); overlay.showToast("Playing next") },
            MenuItemSpec("Add to queue", Icons.Rounded.QueueMusic) { player.addToQueue(t); overlay.showToast("Added to queue") },
            MenuItemSpec(if (isFav) "Remove favorite" else "Favorite", if (isFav) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite) { toggleFavorite(t.id) },
            MenuItemSpec("Add to playlist", Icons.Rounded.PlaylistAdd) { addToPlaylist(listOf(t)) },
            MenuItemSpec("Go to album", Icons.Rounded.Album) { openRoute(Route.Album(t.albumKey)) },
            MenuItemSpec("Go to artist", Icons.Rounded.Person) { openRoute(Route.Artist(t.artist)) },
        )
        if (playlist != null) {
            if (playlist.index > 0) items += MenuItemSpec("Move up", Icons.Rounded.ArrowUpward) {
                scope.launch { repo.dao.moveEntry(playlist.id, playlist.index, playlist.index - 1) }
            }
            if (playlist.index < playlist.size - 1) items += MenuItemSpec("Move down", Icons.Rounded.ArrowDownward) {
                scope.launch { repo.dao.moveEntry(playlist.id, playlist.index, playlist.index + 1) }
            }
            items += MenuItemSpec("Remove from playlist", Icons.Rounded.Delete, danger = true) {
                scope.launch { repo.dao.removeEntry(playlist.id, t.id) }
            }
        }
        overlay.menu(anchor, items)
    }

    fun addToPlaylist(tracks: List<TrackEntity>) {
        overlay.sheet { dismiss -> AddToPlaylistSheet(tracks, dismiss) }
    }

    fun showQueue() {
        overlay.sheet { dismiss -> QueueSheet(dismiss) }
    }
}

val LocalAura = staticCompositionLocalOf<AuraEnv> { error("AuraEnv not provided") }

/** Stable accent palette from settings (controls). Artwork-derived colours are used for ambience only. */
val LocalAccent = staticCompositionLocalOf { AmbientColors(androidx.compose.ui.graphics.Color.Magenta, androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.Cyan) }

val LocalFavorites = compositionLocalOf<Set<Long>> { emptySet() }
val LocalCurrentId = compositionLocalOf<String?> { null }
val LocalBottomPad = compositionLocalOf { androidx.compose.ui.unit.Dp(0f) }
