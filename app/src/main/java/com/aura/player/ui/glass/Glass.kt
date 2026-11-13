package com.aura.player.ui.glass

import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aura.player.art.AmbientColors
import com.aura.player.ui.theme.AuraColors
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * AURA Liquid Glass Engine — Calibrated to WWDC25 / iOS 26 Control Center & Telegram Android Blur.
 *
 * Key optical rules:
 *  1. NEVER paint foggy white crescents, thick inner rings, or colored cyan/blue smudges on glass.
 *     Real Liquid Glass has a completely clean, uniform, neutral interior whose color comes purely
 *     from the blurred & refracted background behind it.
 *  2. Inflate the per-surface [GraphicsLayer] by [padPx] on all sides so the Gaussian blur kernel
 *     and AGSL lens refraction sample real surrounding backdrop pixels without edge-clamping smears.
 *  3. On Android 13+ (API 33+), run a circular-arc meniscus SDF refraction shader (`sqrt(2t - t²)`)
 *     along a narrow 5–9 dp rim bezel with subtle Snell's-law lensing and a crisp 1 dp Fresnel border.
 */
@Stable
class Backdrop(val layer: GraphicsLayer) {
    var rootOffset: Offset by mutableStateOf(Offset.Zero)
}

val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/** 0 = frosted (no blur), 1 = balanced, 2 = high. */
val LocalGlassQuality = compositionLocalOf { 2 }

enum class GlassLevel { Subtle, Regular, Strong, Lens }

private val saturationFilter: ColorFilter by lazy {
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.38f) })
}

private const val LIQUID_GLASS_AGSL = """
    uniform shader content;
    uniform float2 boxSize;
    uniform float pad;
    uniform float cornerRadius;
    uniform float bezelWidth;
    uniform float refractionPx;
    uniform float dispersion;

    half4 main(float2 fragCoord) {
        // Local coordinates inside the glass element (0..boxSize)
        float2 localPos = fragCoord - float2(pad, pad);
        float2 halfSize = max(boxSize * 0.5, float2(1.0, 1.0));
        float r = clamp(cornerRadius, 0.0, min(halfSize.x, halfSize.y));

        // Signed Distance Field (SDF) of rounded rectangle
        float2 p = localPos - halfSize;
        float2 q = abs(p) - (halfSize - float2(r, r));
        float2 qMax = max(q, float2(0.0, 0.0));
        float outDist = length(qMax);
        float inDist = min(max(q.x, q.y), 0.0);
        float sdf = outDist + inDist - r;

        // Analytical outward unit normal
        float2 signP = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float2 normal2D;
        if (q.x > 0.0 && q.y > 0.0) {
            normal2D = signP * (qMax / max(outDist, 0.0001));
        } else if (q.x > q.y) {
            normal2D = float2(signP.x, 0.0);
        } else {
            normal2D = float2(0.0, signP.y);
        }

        // Circular-arc meniscus profile: h(t) = sqrt(2t - t^2) over narrow rim bezel
        float bw = max(bezelWidth, 1.0);
        float t = clamp(-sdf / bw, 0.0, 1.0);
        // Slope is strongest right at the silhouette edge (t = 0) and 0 inside the flat window (t = 1)
        float cosTheta = (1.0 - t) * (1.0 - t);

        // Refract background rays along the curved rim normal
        float2 offset = -normal2D * (cosTheta * refractionPx);

        float2 totalSize = boxSize + float2(pad * 2.0, pad * 2.0);
        float2 minUv = float2(1.0, 1.0);
        float2 maxUv = max(totalSize - float2(1.0, 1.0), minUv);

        float2 uvR = clamp(fragCoord + offset * (1.0 + dispersion), minUv, maxUv);
        float2 uvG = clamp(fragCoord + offset, minUv, maxUv);
        float2 uvB = clamp(fragCoord + offset * (1.0 - dispersion), minUv, maxUv);

        half4 colR = content.eval(uvR);
        half4 colG = content.eval(uvG);
        half4 colB = content.eval(uvB);
        return half4(colR.r, colG.g, colB.b, colG.a);
    }
"""

@RequiresApi(33)
private object AgslLiquidGlass {
    fun buildEffect(
        blurRadiusPx: Float,
        widthPx: Float,
        heightPx: Float,
        padPx: Float,
        cornerRadiusPx: Float,
        bezelPx: Float,
        refractPx: Float,
        dispersion: Float,
    ): androidx.compose.ui.graphics.RenderEffect? = try {
        val shader = RuntimeShader(LIQUID_GLASS_AGSL)
        shader.setFloatUniform("boxSize", widthPx.coerceAtLeast(2f), heightPx.coerceAtLeast(2f))
        shader.setFloatUniform("pad", padPx)
        shader.setFloatUniform("cornerRadius", cornerRadiusPx.coerceAtLeast(0f))
        shader.setFloatUniform("bezelWidth", bezelPx.coerceAtLeast(1f))
        shader.setFloatUniform("refractionPx", refractPx)
        shader.setFloatUniform("dispersion", dispersion)

        val blur = AndroidRenderEffect.createBlurEffect(
            blurRadiusPx.coerceAtLeast(1f),
            blurRadiusPx.coerceAtLeast(1f),
            Shader.TileMode.CLAMP,
        )
        val satMatrix = AndroidColorMatrix().apply { setSaturation(1.38f) }
        val sat = AndroidRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(satMatrix), blur)
        val lensed = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
        AndroidRenderEffect.createChainEffect(lensed, sat).asComposeRenderEffect()
    } catch (_: Throwable) {
        null
    }
}

fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    level: GlassLevel = GlassLevel.Regular,
    tint: Color = Color.White,
    borderAlpha: Float = 1f,
    elevation: Dp = Dp.Unspecified,
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val quality = LocalGlassQuality.current
    val density = LocalDensity.current
    val isDark = AuraColors.isDark
    val canBlur = backdrop != null && quality > 0 && Build.VERSION.SDK_INT >= 31
    val blurLayer = if (canBlur) rememberGraphicsLayer() else null
    var origin by remember { mutableStateOf(Offset.Zero) }

    val blurRadiusPx = with(density) {
        val dpVal = when {
            level == GlassLevel.Lens -> if (quality >= 2) 22.dp else 16.dp
            quality >= 2 -> 32.dp
            else -> 20.dp
        }
        dpVal.toPx()
    }

    if (blurLayer != null && Build.VERSION.SDK_INT < 33) {
        SideEffect {
            blurLayer.clip = true
            blurLayer.renderEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Clamp)
            blurLayer.colorFilter = saturationFilter
        }
    }

    val overBackdrop = backdrop != null

    // Clean, neutral, uniform glass fills (matches iOS 26 Control Center & Telegram Android).
    val (topWhiteA, botWhiteA) = when (level) {
        GlassLevel.Subtle -> if (isDark) (0.06f to 0.03f) else (0.35f to 0.22f)
        GlassLevel.Regular -> if (isDark) (0.09f to 0.05f) else (0.48f to 0.32f)
        GlassLevel.Strong -> if (isDark) (0.12f to 0.06f) else (0.58f to 0.40f)
        GlassLevel.Lens -> if (isDark) (0.15f to 0.08f) else (0.65f to 0.45f)
    }

    this
        .onGloballyPositioned { if (canBlur) origin = it.positionInRoot() }
        .drawWithCache {
            val w = size.width
            val h = size.height
            val minDim = min(w, h).coerceAtLeast(1f)

            val outline = shape.createOutline(size, layoutDirection, this)
            var cornerR = minDim * 0.5f
            val path = Path().apply {
                when (outline) {
                    is Outline.Rounded -> {
                        addRoundRect(outline.roundRect)
                        cornerR = outline.roundRect.topLeftCornerRadius.x
                    }
                    is Outline.Rectangle -> {
                        addRect(outline.rect)
                        cornerR = 0f
                    }
                    is Outline.Generic -> addPath(outline.path)
                }
            }

            // Inflate the offscreen blur layer so neither the Gaussian kernel nor lens refraction clamps at the edge.
            val padPx = if (canBlur) 28.dp.toPx() else 0f
            val bezelPx = when (level) {
                GlassLevel.Subtle -> 5.dp.toPx()
                GlassLevel.Regular -> 7.dp.toPx()
                GlassLevel.Strong -> 9.dp.toPx()
                GlassLevel.Lens -> 11.dp.toPx()
            }.coerceAtMost(minDim * 0.32f)
            val refractPx = when (level) {
                GlassLevel.Subtle -> 4.dp.toPx()
                GlassLevel.Regular -> 7.dp.toPx()
                GlassLevel.Strong -> 10.dp.toPx()
                GlassLevel.Lens -> 14.dp.toPx()
            }
            val disp = if (level == GlassLevel.Lens) 0.14f else 0.08f

            if (blurLayer != null && Build.VERSION.SDK_INT >= 33 && w > 2f && h > 2f) {
                val agslEffect = AgslLiquidGlass.buildEffect(
                    blurRadiusPx = blurRadiusPx,
                    widthPx = w,
                    heightPx = h,
                    padPx = padPx,
                    cornerRadiusPx = cornerR,
                    bezelPx = bezelPx,
                    refractPx = refractPx,
                    dispersion = disp,
                )
                blurLayer.clip = true
                if (agslEffect != null) {
                    blurLayer.renderEffect = agslEffect
                    blurLayer.colorFilter = null
                } else {
                    blurLayer.renderEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                    blurLayer.colorFilter = saturationFilter
                }
            }

            // Neutral dark/light optical base for legibility over busy backgrounds (Telegram style).
            val base: Color = when {
                canBlur -> if (isDark) {
                    when (level) {
                        GlassLevel.Lens -> Color(0xFF0A0E1F).copy(alpha = 0.12f)
                        GlassLevel.Strong -> Color(0xFF0A0E1F).copy(alpha = 0.38f)
                        GlassLevel.Regular -> Color(0xFF0A0E1F).copy(alpha = 0.24f)
                        GlassLevel.Subtle -> Color(0xFF0A0E1F).copy(alpha = 0.14f)
                    }
                } else {
                    Color.White.copy(alpha = 0.24f)
                }
                overBackdrop -> if (isDark) Color(0xFF101426).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.90f)
                else -> if (isDark) {
                    Color(0xFF0D1226).copy(alpha = if (level == GlassLevel.Subtle) 0.22f else 0.34f)
                } else {
                    Color.White.copy(alpha = 0.45f)
                }
            }

            // Clean, subtle neutral sheen (no harsh crescents, no colored blobs).
            val surfaceBrush = Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = topWhiteA.coerceIn(0f, 1f)),
                1.0f to Color.White.copy(alpha = botWhiteA.coerceIn(0f, 1f)),
            )

            // Optional very subtle tint wash only when an explicit non-white tint is requested (e.g. active chip).
            val hasCustomTint = tint != Color.White
            val tintWash = if (hasCustomTint) {
                tint.copy(alpha = if (level == GlassLevel.Lens) 0.14f else 0.08f)
            } else Color.Transparent

            // Single razor-sharp 0.9dp directional specular hairline border (top-left & bottom-right catch light).
            val strokeWidth = if (level == GlassLevel.Lens) 1.05.dp.toPx() else 0.9.dp.toPx()
            val outerStroke = Stroke(width = strokeWidth)
            val rimScale = borderAlpha.coerceIn(0f, 1.3f)
            val border = Brush.linearGradient(
                0.0f to Color.White.copy(alpha = (0.55f * rimScale).coerceIn(0f, 1f)),
                0.35f to Color.White.copy(alpha = (0.07f * rimScale).coerceIn(0f, 1f)),
                0.65f to Color.White.copy(alpha = (0.07f * rimScale).coerceIn(0f, 1f)),
                1.0f to Color.White.copy(alpha = (0.30f * rimScale).coerceIn(0f, 1f)),
                start = Offset.Zero,
                end = Offset(w.coerceAtLeast(1f), h.coerceAtLeast(1f)),
            )

            val padI = padPx.roundToInt()
            val recordSize = IntSize(
                (w.roundToInt() + padI * 2).coerceAtLeast(2),
                (h.roundToInt() + padI * 2).coerceAtLeast(2),
            )

            onDrawWithContent {
                if (w <= 0.5f || h <= 0.5f) {
                    drawContent()
                    return@onDrawWithContent
                }

                // 1. Inflated backdrop blur + AGSL circular-arc lens refraction
                if (blurLayer != null && backdrop != null) {
                    val relX = origin.x - backdrop.rootOffset.x
                    val relY = origin.y - backdrop.rootOffset.y
                    blurLayer.record(size = recordSize) {
                        translate(-relX + padPx, -relY + padPx) {
                            drawLayer(backdrop.layer)
                        }
                    }
                    clipPath(path) {
                        translate(-padPx, -padPx) {
                            drawLayer(blurLayer)
                        }
                    }
                }

                // 2. Uniform, clean neutral glass interior
                if (base != Color.Transparent) drawPath(path, base)
                if (tintWash != Color.Transparent) drawPath(path, tintWash)
                drawPath(path, surfaceBrush)

                // 3. Crisp 0.9dp directional specular rim
                drawPath(path, border, style = outerStroke)

                drawContent()
            }
        }
}

/** Soft coloured glow behind an element (artwork, play button). Cheap radial gradient, safe for 0-size. */
fun Modifier.glow(color: Color, radius: Dp = 160.dp, alpha: Float = 0.45f): Modifier = drawWithCache {
    val r = radius.toPx().coerceAtLeast(1f)
    val safeAlpha = (alpha * if (AuraColors.isDark) 1f else 0.55f).coerceIn(0f, 1f)
    val brush = Brush.radialGradient(
        listOf(color.copy(alpha = safeAlpha), Color.Transparent),
        center = Offset(size.width / 2f, size.height / 2f),
        radius = r,
    )
    onDrawBehind {
        if (size.width > 0.5f && size.height > 0.5f) {
            drawCircle(brush, r, Offset(size.width / 2f, size.height / 2f))
        }
    }
}

/**
 * The AURA ambient scene: near-black base lit by three drifting colour fields.
 * Colours are animated with tween and only READ inside the draw lambda, so a track change
 * never recomposes anything – it only redraws one canvas.
 */
@Composable
fun AmbientBackground(colors: AmbientColors, modifier: Modifier = Modifier.fillMaxSize(), intensity: Float = 1f) {
    val a by animateColorAsState(colors.a, tween(900), label = "ambA")
    val b by animateColorAsState(colors.b, tween(900), label = "ambB")
    val c by animateColorAsState(colors.c, tween(900), label = "ambC")
    val isDark = AuraColors.isDark
    val bg = AuraColors.Bg
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0.5f || h <= 0.5f) return@Canvas
        val safeW = w.coerceAtLeast(1f)
        val scale = if (isDark) intensity else (intensity * 0.48f)
        drawRect(bg)
        drawRect(
            Brush.radialGradient(
                listOf(a.copy(alpha = (0.55f * scale).coerceIn(0f, 1f)), Color.Transparent),
                center = Offset(w * 0.10f, h * 0.08f),
                radius = safeW * 1.05f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(b.copy(alpha = (0.42f * scale).coerceIn(0f, 1f)), Color.Transparent),
                center = Offset(w * 0.98f, h * 0.38f),
                radius = safeW * 0.95f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(c.copy(alpha = (0.32f * scale).coerceIn(0f, 1f)), Color.Transparent),
                center = Offset(w * 0.15f, h * 0.98f),
                radius = safeW * 1.0f,
            ),
        )
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                1f to bg.copy(alpha = if (isDark) 0.55f else 0.35f),
            ),
        )
    }
}
