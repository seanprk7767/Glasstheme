package com.raam.glasslauncher

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

// AGSL lens: bends the backdrop near the edges, splits RGB slightly, adds a rim highlight.
private const val GLASS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float refraction;
float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}
half4 main(float2 fc) {
    float2 c = size * 0.5;
    float2 p = fc - c;
    float d = sdRoundRect(p, c, radius);
    float depth = clamp(-d / 28.0, 0.0, 1.0);
    float bend = pow(1.0 - depth, 2.5);
    float2 dir = normalize(p + 0.001);
    float2 off = -dir * bend * refraction;
    half r = content.eval(fc + off * 1.06).r;
    half g = content.eval(fc + off).g;
    half b = content.eval(fc + off * 0.94).b;
    half3 col = half3(r, g, b);
    col = mix(col, half3(1.0), 0.10);
    float rim = smoothstep(3.0, 0.0, abs(d + 1.5));
    float light = clamp(dot(dir, normalize(float2(-0.6, -0.8))) * 0.5 + 0.5, 0.0, 1.0);
    col += half3(rim * (0.15 + 0.55 * light));
    return half4(col, 1.0);
}
"""

/**
 * Real "backdrop" glass: re-draws [backdrop] behind the panel at the panel's screen position,
 * then blurs + refracts it. Works because the launcher owns its own wallpaper layer.
 */
@Composable
fun GlassPanel(
    shape: Shape,
    cornerRadiusDp: Float,
    modifier: Modifier = Modifier,
    backdrop: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var pos by remember { mutableStateOf(IntOffset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadiusDp.dp.toPx() }

    Box(
        modifier
            .onGloballyPositioned { pos = IntOffset(it.positionInRoot().x.toInt(), it.positionInRoot().y.toInt()) }
            .onSizeChanged { size = it }
            .clip(shape)
    ) {
        val api = Build.VERSION.SDK_INT
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    if (api >= 31 && size.width > 0) {
                        val blur = RenderEffect.createBlurEffect(14f, 14f, Shader.TileMode.CLAMP)
                        renderEffect = if (api >= 33) {
                            val s = RuntimeShader(GLASS_AGSL)
                            s.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
                            s.setFloatUniform("radius", radiusPx)
                            s.setFloatUniform("refraction", 26f)
                            RenderEffect.createChainEffect(
                                RenderEffect.createRuntimeShaderEffect(s, "content"), blur
                            ).asComposeRenderEffect()
                        } else blur.asComposeRenderEffect()
                    }
                }
        ) {
            // Draw the shared backdrop shifted so it lines up with the real wallpaper.
            Box(
                Modifier
                    .wrapContentSize(unbounded = true, align = androidx.compose.ui.Alignment.TopStart)
                    .offset { IntOffset(-pos.x, -pos.y) }
            ) { backdrop() }
        }
        // Fallback tint + soft edge highlight (also layered over the shader on 33+)
        Box(
            Modifier.matchParentSize()
                .background(Color.White.copy(alpha = if (api >= 31) 0.06f else 0.22f))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(0.7f), Color.White.copy(0.05f), Color.White.copy(0.35f))),
                    shape
                )
        )
        content()
    }
}
