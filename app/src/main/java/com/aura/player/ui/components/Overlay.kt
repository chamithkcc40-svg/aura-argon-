package com.aura.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.LocalReduceMotion
import com.aura.player.ui.glass.Spr
import com.aura.player.ui.glass.glass
import com.aura.player.ui.glass.rubberBand
import com.aura.player.ui.glass.sprRaw
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * In-tree Liquid Glass Overlay System (modeled after Telegram's ActionBarPopupWindow & iOS 26 Menus).
 * Menus, dropdowns, dialogs and bottom sheets sample the live backdrop layer with deep blur + AGSL
 * edge refraction and animate with physical spring stretch, overshoot, and settling.
 */

data class MenuItemSpec(
    val label: String,
    val icon: ImageVector? = null,
    val danger: Boolean = false,
    val checked: Boolean = false,
    val onClick: () -> Unit,
)

enum class OverlayKind { Menu, Dialog, Sheet }

class OverlayEntry(
    val id: Long,
    val kind: OverlayKind,
    val anchor: Rect,
    val content: @Composable (dismiss: () -> Unit, progress: () -> Float) -> Unit,
)

@Stable
class OverlayController {
    val entries = mutableStateListOf<OverlayEntry>()
    var toast by mutableStateOf<String?>(null)
        private set
    private var seq = 0L

    fun menu(anchor: Rect, items: List<MenuItemSpec>) {
        entries += OverlayEntry(seq++, OverlayKind.Menu, anchor) { dismiss, p -> MenuContent(items, dismiss, p) }
    }

    fun dialog(content: @Composable (dismiss: () -> Unit) -> Unit) {
        entries += OverlayEntry(seq++, OverlayKind.Dialog, Rect.Zero) { d, _ -> content(d) }
    }

    fun sheet(content: @Composable (dismiss: () -> Unit) -> Unit) {
        entries += OverlayEntry(seq++, OverlayKind.Sheet, Rect.Zero) { d, _ -> content(d) }
    }

    fun showToast(message: String) { toast = message }
    internal fun clearToast() { toast = null }
    internal fun remove(id: Long) { entries.removeAll { it.id == id } }
}

@Composable
fun OverlayHost(ctrl: OverlayController) {
    ctrl.entries.forEach { e ->
        key(e.id) { OverlayShell(e) { ctrl.remove(e.id) } }
    }
    ToastHost(ctrl)
}

@Composable
private fun ToastHost(ctrl: OverlayController) {
    val msg = ctrl.toast
    val reduce = LocalReduceMotion.current
    LaunchedEffect(msg) {
        if (msg != null) { delay(2200); ctrl.clearToast() }
    }
    var last by remember { mutableStateOf("") }
    if (msg != null) last = msg
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = msg != null,
            enter = fadeIn(sprRaw(Spr.Popup, reduce)) + scaleIn(sprRaw(Spr.Bouncy, reduce), initialScale = 0.76f),
            exit = fadeOut(sprRaw(Spr.Critical, reduce)) + scaleOut(sprRaw(Spr.Critical, reduce), targetScale = 0.88f),
        ) {
            Box(Modifier.glass(CircleShape, GlassLevel.Strong, borderAlpha = 1.0f).padding(horizontal = 22.dp, vertical = 12.dp)) {
                Text(last, style = AuraType.Body, color = AuraColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun OverlayShell(entry: OverlayEntry, onRemove: () -> Unit) {
    val ts = remember { MutableTransitionState(false).apply { targetState = true } }
    val dismiss = remember { { ts.targetState = false } }
    val t = updateTransition(ts, label = "overlay")
    val reduce = LocalReduceMotion.current
    val openKind = when (entry.kind) {
        OverlayKind.Menu -> Spr.Dropdown
        OverlayKind.Dialog -> Spr.Popup
        OverlayKind.Sheet -> Spr.BottomSheet
    }
    val pState = t.animateFloat(
        transitionSpec = {
            if (targetState) sprRaw(openKind, reduce)
            else sprRaw(Spr.Critical, reduce)
        },
        label = "p",
    ) { if (it) 1f else 0f }
    val progress = { pState.value }

    LaunchedEffect(ts.currentState, ts.targetState) {
        if (!ts.currentState && !ts.targetState) onRemove()
    }
    BackHandler(enabled = ts.targetState) { dismiss() }

    when (entry.kind) {
        OverlayKind.Menu -> MenuShell(entry, dismiss, progress)
        OverlayKind.Dialog -> DialogShell(entry, dismiss, progress)
        OverlayKind.Sheet -> SheetShell(entry, dismiss, progress)
    }
}

/**
 * Telegram ActionBarPopupWindow / iOS 26 Context Menu:
 * Compact, deeply blurred & refracted glass panel with spring expansion from the anchor corner.
 */
@Composable
private fun MenuShell(entry: OverlayEntry, dismiss: () -> Unit, p: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val scrim = (0.24f * p().coerceIn(0f, 1f)).coerceIn(0f, 1f)
                if (scrim > 0.005f) drawRect(Color.Black.copy(alpha = scrim))
            }
            .pointerInput(Unit) { detectTapGestures { dismiss() } },
    ) {
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    Modifier
                        .glass(RoundedCornerShape(22.dp), GlassLevel.Strong, borderAlpha = 1.0f)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) { entry.content(dismiss, p) }
            },
        ) { measurables, c ->
            val margin = 14.dp.roundToPx()
            val gap = 6.dp.roundToPx()
            val w = 216.dp.roundToPx().coerceAtMost((c.maxWidth - 2 * margin).coerceAtLeast(120))
            val pl = measurables[0].measure(Constraints(minWidth = w, maxWidth = w, maxHeight = (c.maxHeight - 2 * margin).coerceAtLeast(100)))
            val a = entry.anchor
            layout(c.maxWidth, c.maxHeight) {
                val x = (a.right.roundToInt() - pl.width).coerceIn(margin, (c.maxWidth - pl.width - margin).coerceAtLeast(margin))
                val below = a.bottom.roundToInt() + gap
                val fitsBelow = below + pl.height + margin <= c.maxHeight
                val y = if (fitsBelow) below else (a.top.roundToInt() - gap - pl.height).coerceAtLeast(margin)
                val ox = if (pl.width > 0) ((a.center.x - x) / pl.width).coerceIn(0f, 1f) else 0.5f
                val oy = if (fitsBelow) 0f else 1f
                pl.placeWithLayer(x, y) {
                    val v = p()
                    val sx = (0.68f + 0.32f * v).coerceIn(0.4f, 1.14f)
                    val sy = (0.48f + 0.52f * v).coerceIn(0.3f, 1.18f)
                    scaleX = sx
                    scaleY = sy
                    alpha = v.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(ox, oy)
                }
            }
        }
    }
}

@Composable
private fun MenuContent(items: List<MenuItemSpec>, dismiss: () -> Unit, p: () -> Float) {
    Column(Modifier.padding(6.dp)) {
        items.forEachIndexed { i, item ->
            val rowShape = RoundedCornerShape(14.dp)
            val selectedBg = if (item.checked) {
                Modifier
                    .clip(rowShape)
                    .background(Color.White.copy(alpha = if (AuraColors.isDark) 0.11f else 0.35f))
            } else Modifier

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .graphicsLayer {
                        val raw = (p() - i * 0.045f) / 0.62f
                        val a = raw.coerceIn(0f, 1f)
                        val over = (raw - 1f).coerceIn(0f, 0.25f)
                        alpha = a
                        translationY = (1f - a) * 10.dp.toPx() - over * 3.dp.toPx()
                    }
                    .then(selectedBg)
                    .highlightClick(rowShape, onClick = { dismiss(); item.onClick() })
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val tint = if (item.danger) AuraColors.Danger else AuraColors.TextPrimary
                if (item.icon != null) Icon(item.icon, null, tint = tint, modifier = Modifier.size(20.dp))
                Text(item.label, style = AuraType.Body, color = tint, modifier = Modifier.weight(1f), maxLines = 1)
                if (item.checked) {
                    Icon(Icons.Rounded.Check, null, tint = AuraColors.Cyan, modifier = Modifier.size(18.dp))
                }
            }
            if (i < items.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(0.5.dp)
                        .background(Color.White.copy(alpha = 0.07f)),
                )
            }
        }
    }
}

@Composable
private fun DialogShell(entry: OverlayEntry, dismiss: () -> Unit, p: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = (0.55f * p().coerceIn(0f, 1f)).coerceIn(0f, 1f))) }
            .pointerInput(Unit) { detectTapGestures { dismiss() } }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(28.dp)
                .widthIn(max = 340.dp)
                .graphicsLayer {
                    val v = p()
                    val delta = v - 1f
                    scaleX = (1f + delta * 0.18f).coerceIn(0.75f, 1.15f)
                    scaleY = (1f + delta * 0.24f).coerceIn(0.75f, 1.18f)
                    alpha = v.coerceIn(0f, 1f)
                }
                .glass(RoundedCornerShape(32.dp), GlassLevel.Strong, borderAlpha = 1.0f)
                .pointerInput(Unit) { detectTapGestures { } },
        ) { entry.content(dismiss, p) }
    }
}

@Composable
private fun SheetShell(entry: OverlayEntry, dismiss: () -> Unit, p: () -> Float) {
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val safeH = size.height.coerceAtLeast(1f)
                val fade = (1f - drag.value.coerceAtLeast(0f) / safeH).coerceIn(0f, 1f)
                drawRect(Color.Black.copy(alpha = (0.52f * p().coerceIn(0f, 1f) * fade).coerceIn(0f, 1f)))
            }
            .pointerInput(Unit) { detectTapGestures { dismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val maxH = maxHeight * 0.80f
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxH)
                .graphicsLayer {
                    val v = p()
                    val slideP = v.coerceIn(0f, 1f)
                    val openOvershoot = (v - 1f).coerceIn(0f, 0.25f)
                    val d = drag.value
                    val downOffset = d.coerceAtLeast(0f)
                    val upPull = (-d).coerceAtLeast(0f)
                    val safeH = size.height.coerceAtLeast(1f)
                    val stretchY = 1f + openOvershoot * 0.28f + (rubberBand(upPull, safeH, 0.16f) / safeH)
                    val stretchX = (1f - openOvershoot * 0.08f).coerceIn(0.92f, 1f)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationY = (1f - slideP) * size.height + downOffset
                    scaleX = stretchX
                    scaleY = stretchY.coerceIn(0.92f, 1.18f)
                }
                .glass(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp), GlassLevel.Strong, borderAlpha = 1.0f)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { d ->
                            scope.launch {
                                val next = drag.value + d
                                val damped = if (next < 0f) next * 0.38f else next
                                drag.snapTo(damped)
                            }
                        },
                        onDragStopped = { v ->
                            if (drag.value > 140.dp.value * 2.4f || v > 1300f) {
                                dismiss()
                            } else {
                                scope.launch { drag.animateTo(0f, sprRaw(Spr.BottomSheet, reduce)) }
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 38.dp, height = 4.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.32f)),
                )
            }
            Box(Modifier.weight(1f, fill = false).navigationBarsPadding()) { entry.content(dismiss, p) }
        }
    }
}

@Composable
fun GlassAlert(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean = false,
    dismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = AuraType.Title, color = AuraColors.TextPrimary)
        Text(message, style = AuraType.Body, color = AuraColors.TextSecondary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Cancel", onClick = dismiss, modifier = Modifier.weight(1f))
            PrimaryButton(
                confirmLabel,
                onClick = { dismiss(); onConfirm() },
                modifier = Modifier.weight(1f),
                danger = danger,
            )
        }
    }
}

@Composable
fun GlassPrompt(
    title: String,
    initial: String = "",
    placeholder: String,
    confirmLabel: String,
    dismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = AuraType.Title, color = AuraColors.TextPrimary)
        GlassTextField(
            text,
            { text = it },
            placeholder,
            onSearch = { if (text.isNotBlank()) { dismiss(); onSubmit(text) } },
            autoFocus = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Cancel", onClick = dismiss, modifier = Modifier.weight(1f))
            PrimaryButton(
                confirmLabel,
                onClick = { if (text.isNotBlank()) { dismiss(); onSubmit(text) } },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
