package com.aura.player.ui.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/** True when the user (or the OS animator scale) asks for reduced motion. */
val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalHaptics = staticCompositionLocalOf { true }

/** Exposes the current press luminance state (`0f..1f`) to [Modifier.glass] so pressed glass brightens optically. */
val LocalPressLuminance = compositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/**
 * Centralized AURA Spring Motion System.
 *
 * Tuned around physical mass, damping ratio, and stiffness so UI elements stretch, pull,
 * overshoot naturally, and settle elastically like real liquid-glass objects.
 *
 * When reduced motion is active (via Settings or Android system animator scale = 0),
 * every spring specification cleanly collapses to [snap].
 */
enum class Spr(val damping: Float, val stiffness: Float) {
    /** Calm settle — ambient transitions, background layout shifts. */
    Soft(0.84f, 290f),

    /** Quick & responsive with light overshoot — toggles, chips, quick state changes. */
    Snappy(0.72f, 500f),

    /** Playful elastic overshoot — release bounce, indicators, interactive droplets. */
    Bouncy(0.50f, 390f),

    /** Critically damped (no overshoot) — clean dismissals, color/alpha fades. */
    Critical(1.0f, 640f),

    /** Navigation — smooth and controlled spring with subtle physical overshoot for tabs & screens. */
    Navigation(0.70f, 360f),

    /** Button press-in — fast, high-stiffness compression when finger touches glass. */
    ButtonPress(0.90f, 760f),

    /** Button release — elastic rebound with natural overshoot and settling when finger lifts. */
    ButtonRelease(0.46f, 360f),

    /** Dropdowns — spring expansion from anchor with natural elastic settling. */
    Dropdown(0.62f, 410f),

    /** Bottom sheets — physical drag release and spring settling. */
    BottomSheet(0.74f, 350f),

    /** Mini Player <-> Now Playing — fluid full-screen expansion and contraction. */
    MiniPlayer(0.74f, 330f),

    /** Artwork — smooth scale, play/pause breathing, and swipe-to-skip settling. */
    Artwork(0.58f, 310f),

    /** Popup menus & dialogs — spring-based appearance and dismissal. */
    Popup(0.64f, 440f),

    /** Favorite heart — expressive but restrained elastic feedback. */
    Favorite(0.42f, 430f),
}

@Composable
fun <T> spr(kind: Spr = Spr.Snappy): FiniteAnimationSpec<T> {
    val reduce = LocalReduceMotion.current
    return remember(reduce, kind) {
        if (reduce) snap() else spring(dampingRatio = kind.damping, stiffness = kind.stiffness)
    }
}

fun <T> sprRaw(kind: Spr, reduce: Boolean): FiniteAnimationSpec<T> =
    if (reduce) snap() else spring(dampingRatio = kind.damping, stiffness = kind.stiffness)

/**
 * Physical rubber-band resistance function (iOS-style asymptotic stretch):
 * maps an unconstrained drag delta [delta] over [dimension] with [resistance] into a bounded elastic offset.
 */
fun rubberBand(delta: Float, dimension: Float, resistance: Float = 0.55f): Float {
    if (dimension <= 0f || delta == 0f) return 0f
    val d = abs(delta)
    val c = dimension * resistance
    val mapped = (1f - (1f / ((d * resistance / dimension) + 1f))) * dimension
    return mapped.coerceAtMost(c) * sign(delta)
}

/**
 * Computes anisotropic stretch factors (`scaleX`, `scaleY`) when a glass droplet/pill
 * is in motion by normalized velocity/displacement [stretchAmount] (`0f..1.5f`).
 * Preserves approximate area (`scaleX * scaleY ≈ 1`) while stretching along the primary axis.
 */
fun stretchAxes(stretchAmount: Float, horizontal: Boolean = true): Pair<Float, Float> {
    val d = stretchAmount.coerceIn(0f, 1.5f)
    val primary = 1f + 0.24f * d
    val secondary = (1f / sqrt(primary)).coerceIn(0.84f, 1f)
    return if (horizontal) primary to secondary else secondary to primary
}

/**
 * Asymmetric spring-scaled press feedback + anisotropic liquid squash/stretch + optical luminance boost.
 * Uses [Spr.ButtonPress] on touch-down for immediate tactile response, and [Spr.ButtonRelease] on lift
 * so the control rebounds past 1.0f with natural elastic overshoot and settles smoothly.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    scaleTo: Float = 0.93f,
    stretchFactor: Float = 0.35f,
    enabled: Boolean = true,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val spec = spr<Float>(if (pressed) Spr.ButtonPress else Spr.ButtonRelease)
    val s by animateFloatAsState(if (pressed) scaleTo else 1f, spec, label = "pressScale")
    val lum by animateFloatAsState(if (pressed) 1f else 0f, spr(if (pressed) Spr.ButtonPress else Spr.Critical), label = "pressLum")
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHaptics.current

    this
        .graphicsLayer {
            // Anisotropic squash-and-stretch: when compressed (< 1f) or overshooting (> 1f),
            // X widens slightly faster than Y compresses, giving a physical liquid-cushion feel.
            val delta = s - 1f
            scaleX = (1f + delta * (1f - 0.22f * stretchFactor)).coerceIn(0.70f, 1.25f)
            scaleY = (1f + delta * (1f + 0.22f * stretchFactor)).coerceIn(0.70f, 1.25f)
        }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled && (onClick != null || onLongClick != null),
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick?.let { lc ->
                {
                    if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    lc()
                }
            },
        )
}
