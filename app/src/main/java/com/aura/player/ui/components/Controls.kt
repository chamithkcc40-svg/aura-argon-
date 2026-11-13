package com.aura.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.ui.LocalAccent
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.LocalReduceMotion
import com.aura.player.ui.glass.Spr
import com.aura.player.ui.glass.glass
import com.aura.player.ui.glass.pressable
import com.aura.player.ui.glass.spr
import com.aura.player.ui.glass.sprRaw
import com.aura.player.ui.glass.stretchAxes
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlin.math.abs
import kotlin.math.roundToInt

/** Light-press highlight used by list rows and menu items with elastic squash-and-stretch. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.highlightClick(shape: Shape, onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (pressed) 0.12f else 0f, spr(Spr.Critical), label = "hl")
    val s by animateFloatAsState(
        if (pressed) 0.975f else 1f,
        spr(if (pressed) Spr.ButtonPress else Spr.ButtonRelease),
        label = "hs",
    )
    this
        .graphicsLayer {
            val delta = s - 1f
            scaleX = (1f + delta * 0.85f).coerceIn(0.90f, 1.08f)
            scaleY = (1f + delta * 1.15f).coerceIn(0.90f, 1.08f)
        }
        .clip(shape)
        .drawBehind {
            val safeA = a.coerceIn(0f, 1f)
            if (safeA > 0.005f) drawRect(Color.White.copy(alpha = safeA))
        }
        .combinedClickable(src, null, onClick = onClick, onLongClick = onLongClick)
}

/** Captures an element's bounds so overlays can anchor to it. */
class AnchorState {
    var rect: Rect = Rect.Zero
    val modifier: Modifier = Modifier.onGloballyPositioned { rect = it.boundsInRoot() }
}

@Composable
fun rememberAnchor() = remember { AnchorState() }

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = AuraColors.TextPrimary,
    level: GlassLevel = GlassLevel.Regular,
) {
    Box(
        modifier
            .size(size)
            .glass(CircleShape, level, borderAlpha = 1.0f)
            .pressable(onClick, scaleTo = 0.86f),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize)) }
}

@Composable
fun PlainIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    tint: Color = AuraColors.TextSecondary,
) {
    Box(modifier.size(size).pressable(onClick, scaleTo = 0.84f), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    val accent = LocalAccent.current
    val brush = if (danger) Brush.linearGradient(listOf(Color(0xFFFF5C7A), Color(0xFFD9366A)))
    else Brush.linearGradient(listOf(accent.a, accent.b))
    Row(
        modifier
            .height(48.dp)
            .pressable(onClick, scaleTo = 0.92f)
            .clip(CircleShape)
            .background(brush)
            .glass(CircleShape, GlassLevel.Subtle, borderAlpha = 0.9f)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(text, style = AuraType.Body.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
    }
}

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .height(44.dp)
            .glass(CircleShape, GlassLevel.Regular, borderAlpha = 0.95f)
            .pressable(onClick, scaleTo = 0.92f)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = AuraColors.TextPrimary, modifier = Modifier.size(19.dp))
        Text(text, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1)
    }
}

@Composable
fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fillA by animateFloatAsState(if (selected) 1f else 0f, spr(Spr.Navigation), label = "chip")
    val txt by animateColorAsState(if (selected) Color.White else AuraColors.TextSecondary, spr(Spr.Critical), label = "chipT")
    Box(
        modifier
            .height(38.dp)
            .pressable(onClick, scaleTo = 0.90f)
            .clip(CircleShape)
            .drawBehind {
                val safeA = (0.16f * fillA).coerceIn(0f, 1f)
                if (safeA > 0.005f) {
                    drawRect(Color.White.copy(alpha = safeA))
                }
            }
            .glass(
                CircleShape,
                level = if (selected) GlassLevel.Lens else GlassLevel.Subtle,
                borderAlpha = if (selected) 1.15f else 0.75f,
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = AuraType.Body, color = txt, maxLines = 1) }
}

@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
        itemsIndexed(options) { i, label -> GlassChip(label, i == selected, { onSelect(i) }) }
    }
}

/** Segmented control with a spring-driven sliding Liquid Glass lens indicator that stretches in motion. */
@Composable
fun GlassSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val pos by animateFloatAsState(selected.toFloat(), spr(Spr.Navigation), label = "seg")
    BoxWithConstraints(modifier.height(44.dp).glass(CircleShape, GlassLevel.Subtle).padding(4.dp)) {
        val segW = maxWidth / options.size.coerceAtLeast(1)
        Box(
            Modifier
                .offset { IntOffset((segW.toPx() * pos).roundToInt(), 0) }
                .width(segW)
                .fillMaxHeight()
                .graphicsLayer {
                    val dist = abs(pos - selected).coerceIn(0f, 1.5f)
                    val (sx, sy) = stretchAxes(dist, horizontal = true)
                    scaleX = sx
                    scaleY = sy
                }
                .glass(CircleShape, GlassLevel.Lens, borderAlpha = 1.1f),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().pressable({ onSelect(i) }, scaleTo = 0.94f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = AuraType.Body,
                        color = if (i == selected) AuraColors.TextPrimary else AuraColors.TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * iOS 26 / WWDC25 Liquid Glass Toggle Switch (see Reference Image 2 & PrismalSwitch):
 * - Track crossfades from subtle glass to vibrant accent color.
 * - At rest, the thumb is a crisp capsule; when pressed or sliding, it stretches into a translucent
 *   Liquid Glass lens with spring physics!
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val target = if (checked) 1f else 0f
    val prog by animateFloatAsState(target, spr(Spr.Bouncy), label = "tog")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val inFlight = abs(prog - target).coerceIn(0f, 1f)
    val lensMode by animateFloatAsState(
        if (pressed || inFlight > 0.08f) 1f else 0f,
        spr(if (pressed) Spr.ButtonPress else Spr.ButtonRelease),
        label = "lensMode",
    )
    val thumbW = (28f + 9f * lensMode.coerceIn(0f, 1.2f)).coerceIn(26f, 40f)

    Box(
        modifier
            .size(width = 58.dp, height = 30.dp)
            .clip(CircleShape)
            .drawBehind {
                val safeA = (0.90f * prog).coerceIn(0f, 1f)
                if (safeA > 0.005f) {
                    drawRect(Brush.horizontalGradient(listOf(accent.a.copy(alpha = safeA), accent.c.copy(alpha = safeA))))
                }
            }
            .glass(CircleShape, GlassLevel.Subtle, borderAlpha = 0.85f)
            .combinedClickable(src, null, onClick = { onChange(!checked) }),
    ) {
        val solidAlpha = (1f - 0.68f * lensMode.coerceIn(0f, 1f)).coerceIn(0.22f, 1f)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val travel = (58.dp.toPx() - thumbW.dp.toPx() - 6.dp.toPx()).coerceAtLeast(0f)
                    val clampedP = prog.coerceIn(-0.06f, 1.06f)
                    IntOffset((3.dp.toPx() + travel * clampedP).roundToInt(), 0)
                }
                .size(width = thumbW.dp, height = 24.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = solidAlpha))
                .glass(CircleShape, GlassLevel.Lens, borderAlpha = 1.15f),
        )
    }
}

/** Settings-style row that opens a clean Liquid Glass dropdown anchored to its value pill. */
@Composable
fun GlassDropdownPill(value: String, onOpen: (Rect) -> Unit) {
    val anchor = rememberAnchor()
    var pulse by remember { mutableStateOf(false) }
    val chevronRot by animateFloatAsState(if (pulse) 180f else 0f, spr(Spr.Dropdown), label = "chevRot")

    Row(
        anchor.modifier
            .height(38.dp)
            .glass(CircleShape, GlassLevel.Regular, borderAlpha = 0.95f)
            .pressable(
                onClick = {
                    pulse = !pulse
                    onOpen(anchor.rect)
                },
                scaleTo = 0.91f,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(value, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1)
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = AuraColors.TextSecondary,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = chevronRot },
        )
    }
}

@Composable
fun GlassTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    onSearch: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    onFocus: (Boolean) -> Unit = {},
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, spr(Spr.Snappy), label = "focus")
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val reduce = LocalReduceMotion.current
    LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }
    Row(
        modifier
            .height(50.dp)
            .graphicsLayer {
                val clampedF = f.coerceIn(-0.1f, 1.3f)
                scaleX = 1f + 0.015f * clampedF
                scaleY = 1f + 0.010f * clampedF
            }
            .glass(CircleShape, GlassLevel.Regular, borderAlpha = 0.95f)
            .drawWithContent {
                drawContent()
                val safeF = (0.65f * f).coerceIn(0f, 1f)
                if (safeF > 0.01f) {
                    drawRoundRect(
                        SolidColor(Color.White.copy(alpha = safeF)),
                        cornerRadius = CornerRadius(size.height / 2f),
                        style = Stroke(1.2.dp.toPx()),
                    )
                }
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leading != null) Icon(leading, null, tint = AuraColors.TextSecondary, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = AuraType.Body.copy(fontSize = 16.sp), color = AuraColors.TextTertiary)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = AuraType.Body.copy(fontSize = 16.sp, color = AuraColors.TextPrimary),
                cursorBrush = SolidColor(accent.c),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch?.invoke() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) },
            )
        }
        AnimatedVisibility(
            visible = value.isNotEmpty(),
            enter = fadeIn(sprRaw(Spr.Snappy, reduce)) + scaleIn(sprRaw(Spr.Bouncy, reduce), initialScale = 0.5f),
            exit = fadeOut(sprRaw(Spr.Critical, reduce)) + scaleOut(sprRaw(Spr.Critical, reduce), targetScale = 0.5f),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Clear",
                tint = AuraColors.TextSecondary,
                modifier = Modifier.size(20.dp).pressable({ onChange("") }, scaleTo = 0.8f),
            )
        }
    }
}

/** Expressive but restrained favorite heart button with anisotropic spring pop & settle. */
@Composable
fun HeartButton(
    isFav: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    boxSize: Dp = 44.dp,
) {
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(isFav) {
        if (first) { first = false; return@LaunchedEffect }
        if (!reduce) {
            if (isFav) {
                scale.snapTo(0.52f)
                scale.animateTo(1f, sprRaw(Spr.Favorite, false))
            } else {
                scale.snapTo(0.78f)
                scale.animateTo(1f, sprRaw(Spr.Snappy, false))
            }
        }
    }
    val tint by animateColorAsState(if (isFav) AuraColors.Pink else AuraColors.TextSecondary, spr(Spr.Critical), label = "heart")
    Box(
        modifier
            .size(boxSize)
            .glass(CircleShape, GlassLevel.Subtle, borderAlpha = 0.85f)
            .pressable(onClick, scaleTo = 0.78f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = "Favorite",
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val v = scale.value.coerceIn(0.4f, 1.5f)
                    val delta = v - 1f
                    scaleX = 1f + delta * 1.12f
                    scaleY = 1f + delta * 0.92f
                },
        )
    }
}

/** Play/pause glyph that morphs between two quads (triangle halves <-> bars) on a spring. */
@Composable
fun PlayPauseGlyph(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t by animateFloatAsState(if (playing) 1f else 0f, spr(Spr.Snappy), label = "pp")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val safeT = t.coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float) = a + (b - a) * safeT
        val l = Path().apply {
            moveTo(0f, 0f)
            lineTo(lerp(.5f, .34f) * w, lerp(.25f, 0f) * h)
            lineTo(lerp(.5f, .34f) * w, lerp(.75f, 1f) * h)
            lineTo(0f, h)
            close()
        }
        val r = Path().apply {
            moveTo(lerp(.5f, .66f) * w, lerp(.25f, 0f) * h)
            lineTo(w, lerp(.5f, 0f) * h)
            lineTo(w, lerp(.5f, 1f) * h)
            lineTo(lerp(.5f, .66f) * w, lerp(.75f, 1f) * h)
            close()
        }
        drawPath(l, color)
        drawPath(r, color)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = AuraType.Section, color = AuraColors.TextPrimary, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = AuraType.Caption,
                color = AuraColors.Cyan,
                modifier = Modifier.pressable(onAction, scaleTo = 0.90f).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
