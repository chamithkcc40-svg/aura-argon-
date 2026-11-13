package com.aura.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.player.data.AccentPalettes
import com.aura.player.data.GlassQualityNames
import com.aura.player.data.ThemeModeNames
import com.aura.player.ui.LocalAura
import com.aura.player.ui.LocalBottomPad
import com.aura.player.ui.components.GlassAlert
import com.aura.player.ui.components.GlassDropdownPill
import com.aura.player.ui.components.GlassToggle
import com.aura.player.ui.components.MenuItemSpec
import com.aura.player.ui.components.PlainIconButton
import com.aura.player.ui.components.PrimaryButton
import com.aura.player.ui.components.ScreenTitle
import com.aura.player.ui.components.SoftButton
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.glass
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val env = LocalAura.current
    val st = env.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val quality by st.quality.collectAsStateWithLifecycle()
    val accent by st.accent.collectAsStateWithLifecycle()
    val themeMode by st.themeMode.collectAsStateWithLifecycle()
    val ambience by st.ambience.collectAsStateWithLifecycle()
    val reduce by st.reduceMotion.collectAsStateWithLifecycle()
    val haptics by st.haptics.collectAsStateWithLifecycle()
    val scanning by env.repo.scanning.collectAsStateWithLifecycle()
    val count by remember { env.repo.dao.trackCount() }.collectAsStateWithLifecycle(0)
    var folders by remember { mutableStateOf(env.repo.grantedFolders()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) {}
            folders = env.repo.grantedFolders()
            scope.launch { env.repo.rescan(); env.overlay.showToast("Folder added") }
        }
    }

    // Keep ScreenTitle pinned above the scrolling LazyColumn so it never overlaps the status bar clock!
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Settings")
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 6.dp, bottom = LocalBottomPad.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("appearance") {
                Group("Appearance") {
                    Row2("Theme", Icons.Rounded.DarkMode, subtitle = "Adaptive Liquid Glass") {
                        GlassDropdownPill(ThemeModeNames.getOrElse(themeMode) { ThemeModeNames[1] }) { rect ->
                            env.overlay.menu(
                                rect,
                                ThemeModeNames.mapIndexed { i, name ->
                                    MenuItemSpec(name, checked = i == themeMode) { st.setThemeMode(i) }
                                },
                            )
                        }
                    }
                    Row2("Accent", Icons.Rounded.Palette) {
                        GlassDropdownPill(AccentPalettes.getOrElse(accent) { AccentPalettes.first() }.name) { rect ->
                            env.overlay.menu(
                                rect,
                                AccentPalettes.mapIndexed { i, p ->
                                    MenuItemSpec(p.name, checked = i == accent) { st.setAccent(i) }
                                },
                            )
                        }
                    }
                    Row2("Glass quality", Icons.Rounded.BlurOn) {
                        GlassDropdownPill(GlassQualityNames.getOrElse(quality) { GlassQualityNames[2] }) { rect ->
                            env.overlay.menu(
                                rect,
                                GlassQualityNames.mapIndexed { i, n ->
                                    MenuItemSpec(n, checked = i == quality) { st.setQuality(i) }
                                },
                            )
                        }
                    }
                    Row2("Artwork ambience", Icons.Rounded.AutoAwesome) { GlassToggle(ambience, st::setAmbience) }
                    Row2("Reduce motion", Icons.Rounded.SlowMotionVideo) { GlassToggle(reduce, st::setReduceMotion) }
                    Row2("Haptics", Icons.Rounded.Vibration) { GlassToggle(haptics, st::setHaptics) }
                }
            }

            item("library") {
                Group("Library") {
                    Row2("$count songs", Icons.Rounded.Refresh, subtitle = if (scanning) "Scanning…" else "Device library") {
                        SoftButton(if (scanning) "…" else "Rescan", { scope.launch { env.repo.rescan(); env.overlay.showToast("Library updated") } })
                    }
                    folders.forEach { uri ->
                        Row2(
                            Uri.decode(uri.lastPathSegment ?: uri.toString()).substringAfterLast(':').ifEmpty { "Folder" },
                            Icons.Rounded.FolderOpen,
                        ) {
                            PlainIconButton(Icons.Rounded.Close, {
                                try {
                                    ctx.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                } catch (_: Throwable) {}
                                folders = env.repo.grantedFolders()
                                scope.launch { env.repo.rescan() }
                            })
                        }
                    }
                    Box(Modifier.padding(16.dp)) {
                        PrimaryButton("Add music folder", { picker.launch(null) }, Modifier.fillMaxWidth(), Icons.Rounded.FolderOpen)
                    }
                    if (!env.repo.hasAudioPermission()) {
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                            SoftButton("Allow device music access", { env.requestAudio() }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            item("data") {
                Group("Data") {
                    Row2("Clear play history", Icons.Rounded.Delete) {
                        SoftButton("Clear", {
                            env.overlay.dialog { dismiss ->
                                GlassAlert("Clear play history?", "Recently played will be emptied.", "Clear", true, dismiss) {
                                    scope.launch {
                                        env.repo.dao.clearHistory()
                                        env.repo.dao.clearSearches()
                                        env.overlay.showToast("History cleared")
                                    }
                                }
                            }
                        })
                    }
                    Row2("About AURA", Icons.Rounded.Info, subtitle = "Version 1.0.0") {
                        SoftButton("Open", {
                            env.overlay.dialog { dismiss ->
                                GlassAlert(
                                    "AURA 1.0",
                                    "A Liquid Glass music player with real backdrop blur, AGSL circular-arc lens refraction, and spring motion physics.",
                                    "OK",
                                    false,
                                    dismiss,
                                ) {}
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            title.uppercase(),
            style = AuraType.Label,
            color = AuraColors.TextTertiary,
            modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), GlassLevel.Regular, borderAlpha = 0.95f)
                .padding(vertical = 6.dp),
            content = content,
        )
    }
}

@Composable
private fun Row2(title: String, icon: ImageVector, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(38.dp).glass(CircleShape, GlassLevel.Subtle, borderAlpha = 0.8f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = AuraColors.Cyan, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = AuraType.Caption, color = AuraColors.TextSecondary)
        }
        trailing()
    }
}
