package com.aura.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.player.data.TrackEntity
import com.aura.player.ui.LocalAccent
import com.aura.player.ui.LocalAura
import com.aura.player.ui.components.Artwork
import com.aura.player.ui.components.EmptyState
import com.aura.player.ui.components.MenuItemSpec
import com.aura.player.ui.components.PlainIconButton
import com.aura.player.ui.components.highlightClick
import com.aura.player.ui.components.rememberAnchor
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.glass
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlinx.coroutines.launch

@Composable
fun AddToPlaylistSheet(tracks: List<TrackEntity>, dismiss: () -> Unit) {
    val env = LocalAura.current
    val accent = LocalAccent.current
    val playlists by remember { env.repo.dao.playlists() }.collectAsStateWithLifecycle(emptyList())
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Text("Add to playlist", style = AuraType.Title, color = AuraColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("new") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .glass(RoundedCornerShape(22.dp), GlassLevel.Regular, borderAlpha = 1.05f)
                        .highlightClick(
                            RoundedCornerShape(22.dp),
                            onClick = {
                                dismiss()
                                promptNewPlaylist(env) { id ->
                                    env.scope.launch {
                                        env.repo.addToPlaylist(id, tracks.map { it.id })
                                        env.overlay.showToast("Added to playlist")
                                    }
                                }
                            },
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(42.dp).glass(CircleShape, GlassLevel.Lens, tint = accent.c, borderAlpha = 1.15f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = AuraColors.Cyan)
                    }
                    Text("New playlist", style = AuraType.Body, color = AuraColors.TextPrimary)
                }
            }
            itemsIndexed(playlists, key = { _, p -> p.id }) { _, p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .glass(RoundedCornerShape(20.dp), GlassLevel.Subtle, borderAlpha = 0.8f)
                        .highlightClick(
                            RoundedCornerShape(20.dp),
                            onClick = {
                                dismiss()
                                env.scope.launch {
                                    val n = env.repo.addToPlaylist(p.id, tracks.map { it.id })
                                    env.overlay.showToast(if (n > 0) "Added to ${p.name}" else "Already in ${p.name}")
                                }
                            },
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.trackCount} songs", style = AuraType.Caption, color = AuraColors.TextSecondary)
                    }
                }
            }
        }
    }
}

/** Up Next queue sheet: tap to jump, per-item Liquid Glass menu to reorder or remove. */
@Composable
fun QueueSheet(dismiss: () -> Unit) {
    val env = LocalAura.current
    val accent = LocalAccent.current
    val queue by env.player.queue.collectAsStateWithLifecycle()
    val ps by env.player.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(queue.size, ps.index) {
        if (queue.isNotEmpty() && ps.index in 1..queue.lastIndex) {
            try {
                listState.scrollToItem((ps.index - 1).coerceIn(0, queue.lastIndex))
            } catch (_: Throwable) {}
        }
    }

    Column(Modifier.padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Up next", style = AuraType.Title, color = AuraColors.TextPrimary)
                Text("${queue.size} songs in queue", style = AuraType.Caption, color = AuraColors.TextSecondary)
            }
            if (queue.isNotEmpty()) {
                Box(
                    Modifier
                        .glass(CircleShape, GlassLevel.Lens, tint = accent.c)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text("${(ps.index + 1).coerceIn(1, queue.size)} / ${queue.size}", style = AuraType.Label, color = AuraColors.TextPrimary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (queue.isEmpty()) {
            EmptyState(Icons.Rounded.QueueMusic, "Queue is empty", "Play any song or album to start your queue.")
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                itemsIndexed(queue, key = { i, q -> "${q.mediaId}#$i" }) { i, q ->
                    val current = i == ps.index
                    val anchor = rememberAnchor()
                    val rowShape = RoundedCornerShape(20.dp)
                    val rowGlass = if (current) {
                        Modifier.glass(rowShape, GlassLevel.Lens, tint = accent.c, borderAlpha = 1.15f)
                    } else Modifier

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .animateItem()
                            .then(rowGlass)
                            .highlightClick(rowShape, onClick = { env.player.jumpTo(i) })
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(
                            q.artUri,
                            q.title,
                            Modifier.size(48.dp).glass(RoundedCornerShape(14.dp), GlassLevel.Subtle),
                            RoundedCornerShape(14.dp),
                            sizePx = 120,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                q.title,
                                style = AuraType.Body,
                                color = if (current) AuraColors.Cyan else AuraColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(q.artist, style = AuraType.Caption, color = AuraColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (current) Icon(Icons.Rounded.GraphicEq, null, tint = AuraColors.Cyan, modifier = Modifier.size(20.dp))
                        PlainIconButton(
                            Icons.Rounded.MoreVert,
                            onClick = {
                                val menuItems = buildList {
                                    add(MenuItemSpec("Play now", Icons.Rounded.PlayArrow) { env.player.jumpTo(i) })
                                    if (i > 0) add(MenuItemSpec("Move up", Icons.Rounded.ArrowUpward) { env.player.moveQueueItem(i, i - 1) })
                                    if (i < queue.lastIndex) add(MenuItemSpec("Move down", Icons.Rounded.ArrowDownward) { env.player.moveQueueItem(i, i + 1) })
                                    add(MenuItemSpec("Remove", Icons.Rounded.Delete, danger = true) { env.player.removeAt(i) })
                                }
                                env.overlay.menu(anchor.rect, menuItems)
                            },
                            modifier = anchor.modifier,
                            size = 36.dp,
                            iconSize = 20.dp,
                        )
                    }
                }
            }
        }
    }
}
