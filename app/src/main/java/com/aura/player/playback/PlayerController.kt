package com.aura.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aura.player.albumKeyOf
import com.aura.player.data.MusicRepository
import com.aura.player.data.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumKey: String = "",
    val artUri: String?,
)

data class PlayerState(
    val current: QueueItem? = null,
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val duration: Long = 0L,
)

/**
 * UI-facing wrapper around a MediaController connected to [PlaybackService].
 * Playback position is deliberately NOT part of [PlayerState]; it is polled by the
 * progress widgets only, so the rest of the UI never recomposes at 4 Hz.
 */
class PlayerController(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val repo: MusicRepository,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue

    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            refreshQueue()
            refreshCore()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshCore()
            mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                scope.launch(Dispatchers.IO) {
                    try { repo.recordPlay(id) } catch (_: Throwable) {}
                }
            }
        }
        override fun onEvents(player: Player, events: Player.Events) = refreshCore()
    }

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                refreshQueue()
                refreshCore()
                val copy = ArrayList(pending)
                pending.clear()
                copy.forEach { action ->
                    try { action(c) } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            try { block(c) } catch (_: Throwable) {}
        } else {
            pending += block
        }
    }

    private fun MediaItem.toQueueItem(): QueueItem {
        val titleStr = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Untitled"
        val artistStr = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val albumStr = mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Album"
        val keyStr = mediaMetadata.extras?.getString("albumKey")
            ?.takeIf { it.isNotBlank() }
            ?: albumKeyOf(0L, albumStr, artistStr)
        // Resilient artwork URI resolution across MediaSession serialization and stream metadata updates.
        val resolvedArtUri = mediaMetadata.extras?.getString("uri")
            ?: requestMetadata.mediaUri?.toString()
            ?: localConfiguration?.uri?.toString()
            ?: mediaMetadata.artworkUri?.toString()
        return QueueItem(
            mediaId = mediaId,
            title = titleStr,
            artist = artistStr,
            album = albumStr,
            albumKey = keyStr,
            artUri = resolvedArtUri,
        )
    }

    private fun refreshQueue() {
        val c = controller ?: return
        val count = c.mediaItemCount.coerceAtLeast(0)
        _queue.value = List(count) { c.getMediaItemAt(it).toQueueItem() }
    }

    private fun refreshCore() {
        val c = controller ?: return
        val count = c.mediaItemCount
        val item = if (count > 0) {
            c.currentMediaItem?.toQueueItem()
                ?: c.currentMediaItemIndex.takeIf { it in 0 until count }?.let { c.getMediaItemAt(it).toQueueItem() }
        } else null
        val safeIdx = if (count > 0 && c.currentMediaItemIndex in 0 until count) c.currentMediaItemIndex else -1
        val rawDur = c.duration
        val safeDur = if (rawDur != C.TIME_UNSET && rawDur > 0L) rawDur else 0L
        _state.update {
            it.copy(
                current = item,
                index = safeIdx,
                isPlaying = c.isPlaying,
                shuffle = c.shuffleModeEnabled,
                repeat = c.repeatMode,
                duration = safeDur,
            )
        }
    }

    private fun TrackEntity.toMediaItem(): MediaItem {
        val parsedUri = Uri.parse(uri)
        val art = if (albumId > 0) Uri.parse("content://media/external/audio/albumart/$albumId") else parsedUri
        val extras = Bundle().apply {
            putString("uri", uri)
            putString("albumKey", albumKey)
            putString("artist", artist)
            putString("album", album)
        }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(parsedUri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(parsedUri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(art)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    // ---- commands ----
    fun play(tracks: List<TrackEntity>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(tracks.map { it.toMediaItem() }, safeIndex, 0L)
            c.prepare()
            c.play()
            refreshQueue()
            refreshCore()
        }
    }

    fun playNext(t: TrackEntity) = withController { c ->
        val count = c.mediaItemCount
        if (count == 0) {
            play(listOf(t))
        } else {
            val insertIdx = if (c.currentMediaItemIndex in 0 until count) c.currentMediaItemIndex + 1 else count
            c.addMediaItem(insertIdx.coerceIn(0, count), t.toMediaItem())
        }
    }

    fun addToQueue(t: TrackEntity) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(t)) else c.addMediaItem(t.toMediaItem())
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE && c.mediaItemCount > 0) c.prepare()
            c.play()
        }
    }

    fun next() = withController { c ->
        if (c.mediaItemCount == 0) return@withController
        if (c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
        } else if (c.repeatMode != Player.REPEAT_MODE_OFF) {
            c.seekToDefaultPosition(0)
        }
    }

    fun previous() = withController { c ->
        if (c.mediaItemCount == 0) return@withController
        val pos = c.currentPosition.coerceAtLeast(0L)
        if (pos > 3000L || !c.hasPreviousMediaItem()) {
            c.seekTo(0L)
        } else {
            c.seekToPreviousMediaItem()
        }
    }

    fun seekTo(ms: Long) = withController { c ->
        if (c.mediaItemCount == 0) return@withController
        val d = c.duration
        val target = if (d != C.TIME_UNSET && d > 0L) ms.coerceIn(0L, d) else ms.coerceAtLeast(0L)
        c.seekTo(target)
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun jumpTo(i: Int) = withController { c ->
        if (i in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(i)
            c.play()
        }
    }

    fun removeAt(i: Int) = withController { c ->
        if (i in 0 until c.mediaItemCount) c.removeMediaItem(i)
    }

    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        val count = c.mediaItemCount
        if (from in 0 until count && to in 0 until count && from != to) {
            c.moveMediaItem(from, to)
        }
    }

    fun position(): Long {
        val p = controller?.currentPosition ?: 0L
        return if (p == C.TIME_UNSET || p < 0L) 0L else p
    }

    fun duration(): Long {
        val d = controller?.duration ?: 0L
        return if (d == C.TIME_UNSET || d <= 0L) 0L else d
    }
}
