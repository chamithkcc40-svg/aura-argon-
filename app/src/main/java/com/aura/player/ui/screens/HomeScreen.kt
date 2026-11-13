package com.aura.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.player.data.AlbumRow
import com.aura.player.greetingFor
import com.aura.player.ui.LocalAccent
import com.aura.player.ui.LocalAura
import com.aura.player.ui.LocalBottomPad
import com.aura.player.ui.Route
import com.aura.player.ui.Tab
import com.aura.player.ui.components.AlbumCard
import com.aura.player.ui.components.Artwork
import com.aura.player.ui.components.EmptyState
import com.aura.player.ui.components.GlassIconButton
import com.aura.player.ui.components.PlaylistCard
import com.aura.player.ui.components.PrimaryButton
import com.aura.player.ui.components.SectionHeader
import com.aura.player.ui.components.SoftButton
import com.aura.player.ui.components.TrackCard
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.glass
import com.aura.player.ui.glass.glow
import com.aura.player.ui.glass.pressable
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import java.util.Calendar

@Composable
fun HomeScreen() {
    val env = LocalAura.current
    val dao = env.repo.dao
    val accent = LocalAccent.current

    val count by remember { dao.trackCount() }.collectAsStateWithLifecycle(-1)
    val scanning by env.repo.scanning.collectAsStateWithLifecycle()
    val recent by remember { dao.recentlyPlayed(20) }.collectAsStateWithLifecycle(emptyList())
    val albums by remember { dao.albums() }.collectAsStateWithLifecycle(emptyList())
    val favorites by remember { dao.favorites() }.collectAsStateWithLifecycle(emptyList())
    val playlists by remember { dao.playlists() }.collectAsStateWithLifecycle(emptyList())

    val greeting = remember { greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    val newest = remember(albums) { albums.sortedByDescending { it.newest }.take(14) }
    val featured: AlbumRow? = remember(albums, recent) {
        albums.firstOrNull { it.albumKey == recent.firstOrNull()?.albumKey } ?: albums.maxByOrNull { it.newest }
    }
    val hasAudio = env.repo.hasAudioPermission()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomPad.current)) {
        item("head") {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "AURA",
                        style = AuraType.Wordmark.copy(brush = Brush.linearGradient(listOf(accent.c, accent.b, accent.a))),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(greeting, style = AuraType.Display, color = AuraColors.TextPrimary)
                }
                GlassIconButton(Icons.Rounded.Settings, { env.nav.select(Tab.Settings) }, level = GlassLevel.Strong)
            }
            Spacer(Modifier.height(20.dp))
        }

        if (count == 0) {
            item("empty") {
                EmptyState(
                    Icons.Rounded.LibraryMusic,
                    if (scanning) "Scanning your music…" else "Let's find your music",
                    if (hasAudio) "No songs found yet. Add a folder or rescan your device library."
                    else "Allow AURA to read audio files on your device to build your library.",
                ) {
                    if (!hasAudio) PrimaryButton("Allow access", { env.requestAudio() }, Modifier.fillMaxWidth())
                    SoftButton("Add a folder", { env.nav.select(Tab.Settings) }, Modifier.fillMaxWidth(), Icons.Rounded.FolderOpen)
                }
            }
        } else {
            featured?.let { f ->
                item("featured") { FeaturedCard(f) }
            }
            item("quick") {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickTile("Shuffle all", Icons.Rounded.Shuffle, accent.c, Modifier.weight(1f)) { env.shuffleAll() }
                    QuickTile("Favorites", Icons.Rounded.Favorite, AuraColors.Pink, Modifier.weight(1f)) {
                        env.nav.libraryPage = 4
                        env.nav.select(Tab.Library)
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item("recent") {
                    SectionHeader("Recently played")
                    Spacer(Modifier.height(12.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(recent, key = { it.id }) { t ->
                            TrackCard(t, Modifier.width(152.dp)) { env.play(recent, recent.indexOf(t)) }
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                }
            }
            if (newest.isNotEmpty()) {
                item("added") {
                    SectionHeader("Recently added", action = "See all", onAction = { env.nav.libraryPage = 0; env.nav.select(Tab.Library) })
                    Spacer(Modifier.height(12.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(newest, key = { it.albumKey }) { a -> AlbumCard(a, Modifier.width(152.dp)) }
                    }
                    Spacer(Modifier.height(26.dp))
                }
            }
            if (playlists.isNotEmpty()) {
                item("playlists") {
                    SectionHeader("Your playlists", action = "See all", onAction = { env.nav.select(Tab.Playlists) })
                    Spacer(Modifier.height(12.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(playlists, key = { it.id }) { p ->
                            PlaylistCard(
                                p,
                                Modifier.width(152.dp),
                                onClick = { env.openRoute(Route.Playlist(p.id)) },
                                onMenu = { rect: Rect -> env.overlay.menu(rect, playlistMenu(env, p)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                }
            }
            if (favorites.isNotEmpty()) {
                item("favs") {
                    SectionHeader("Favorites", action = "Play", onAction = { env.play(favorites) })
                    Spacer(Modifier.height(12.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(favorites.take(20), key = { it.id }) { t ->
                            TrackCard(t, Modifier.width(152.dp)) { env.play(favorites, favorites.indexOf(t)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedCard(a: AlbumRow) {
    val env = LocalAura.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(34.dp)
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(244.dp)
            .glow(accent.a, 180.dp, 0.35f)
            .glass(shape, GlassLevel.Regular, borderAlpha = 1.1f)
            .pressable({ env.openRoute(Route.Album(a.albumKey)) }, scaleTo = 0.965f),
    ) {
        Artwork(a.artUri, a.album, Modifier.fillMaxSize(), shape, sizePx = 720)
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Brush.verticalGradient(0.30f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.80f))),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .glass(CircleShape, GlassLevel.Lens, tint = accent.c, borderAlpha = 1.2f)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) { Text("FEATURED", style = AuraType.Label, color = Color.White) }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(a.album, style = AuraType.Headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.artist, style = AuraType.Body, color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier
                    .size(58.dp)
                    .glow(accent.c, 64.dp, 0.45f)
                    .glass(CircleShape, GlassLevel.Lens, tint = accent.c, borderAlpha = 1.3f)
                    .pressable({ env.playFlow(env.repo.dao.tracksByAlbum(a.albumKey)) }, scaleTo = 0.86f),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        }
    }
}

/**
 * Liquid Glass quick-action capsule modeled after the "Focus" capsule in Reference Images 4 & 5:
 * An outer translucent glass pill housing an inner circular [GlassLevel.Lens] icon well.
 */
@Composable
private fun QuickTile(
    label: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(66.dp)
            .glass(RoundedCornerShape(33.dp), GlassLevel.Regular, borderAlpha = 1.05f)
            .pressable(onClick, scaleTo = 0.94f)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .glass(CircleShape, GlassLevel.Lens, tint = iconColor, borderAlpha = 1.15f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
        }
        Text(label, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
