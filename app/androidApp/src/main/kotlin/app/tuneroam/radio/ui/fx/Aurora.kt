package app.tuneroam.radio.ui.fx

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import app.tuneroam.radio.ui.theme.animationClock
import app.tuneroam.radio.ui.theme.rememberReducedMotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * Northern lights in the station's colors, with a little film grain. [energy] 0..1 brightens
 * the curtains while the station plays. Android 13+ runs a shader; older phones get drifting
 * glows in the same colors. With [moving] false the curtains hold where they are.
 */
@Composable
fun AuroraBackground(
    primary: () -> Color,
    secondary: () -> Color,
    energy: () -> Float,
    modifier: Modifier = Modifier,
    moving: Boolean = true,
) {
    val reduced = rememberReducedMotion()
    val time = animationClock(running = !reduced && moving)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ShaderAurora(primary, secondary, energy, time, modifier)
    } else {
        GlowAurora(primary, secondary, energy, time, modifier)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderAurora(
    primary: () -> Color,
    secondary: () -> Color,
    energy: () -> Float,
    time: State<Float>,
    modifier: Modifier,
) {
    val shader = remember { RuntimeShader(AURORA_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier) {
        val a = primary()
        val b = secondary()
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("time", time.value)
        shader.setFloatUniform("energy", energy())
        shader.setFloatUniform("c1", a.red, a.green, a.blue)
        shader.setFloatUniform("c2", b.red, b.green, b.blue)
        drawRect(brush)
    }
}

@Composable
private fun GlowAurora(
    primary: () -> Color,
    secondary: () -> Color,
    energy: () -> Float,
    time: State<Float>,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val t = time.value * 0.15f
        val e = 0.35f + 0.65f * energy()
        drawRect(Color(0xFF071018))
        listOf(
            Triple(primary(), Offset(0.3f + 0.15f * sin(t), 0.25f + 0.08f * cos(t * 1.3f)), 0.75f),
            Triple(secondary(), Offset(0.75f + 0.12f * cos(t * 0.8f), 0.35f + 0.1f * sin(t * 1.1f)), 0.65f),
            Triple(primary(), Offset(0.5f + 0.2f * sin(t * 0.6f + 2f), 0.6f), 0.55f),
        ).forEach { (color, at, reach) ->
            val center = Offset(at.x * size.width, at.y * size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.32f * e), Color.Transparent),
                    center = center,
                    radius = size.width * reach,
                ),
                radius = size.width * reach,
                center = center,
            )
        }
    }
}

private const val AURORA_SHADER = """
uniform float2 size;
uniform float time;
uniform float energy;
uniform float3 c1;
uniform float3 c2;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

half4 main(float2 fc) {
    float2 uv = fc / size.y;
    float t = time * 0.07;
    float2 q = float2(fbm(uv * 1.2 + float2(0.0, t)), fbm(uv * 1.2 + float2(5.2, 1.3) - t));
    // Stretched tall so the light hangs in curtains.
    float n = fbm(float2(uv.x * 2.4, uv.y * 0.55) + 2.0 * q + float2(t * 0.8, -t * 0.4));
    float curtain = smoothstep(0.3, 0.75, n);
    // Fine vertical rays inside the curtains, drifting sideways.
    float rays = 0.65 + 0.35 * sin(uv.x * 38.0 + q.y * 12.0 + t * 3.0);
    float y = fc.y / size.y;
    float band = exp(-pow((y - 0.28) * 2.2, 2.0));
    float3 col = mix(c1, c2, smoothstep(0.3, 0.7, q.x));
    float glow = curtain * rays * (0.15 + 0.85 * band) * (0.4 + 0.6 * energy);
    float3 base = mix(float3(0.035, 0.075, 0.11), float3(0.02, 0.045, 0.07), y);
    float3 outc = base + col * glow * 0.75 + col * 0.08 * band * energy;
    float grain = hash(fc + float2(fract(time * 7.0) * 97.0, fract(time * 3.0) * 53.0)) - 0.5;
    outc += grain * 0.035;
    return half4(half3(outc), 1.0);
}
"""
