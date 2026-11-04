package com.tideandtea.owner.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tideandtea.owner.ui.theme.BunnyWhite
import com.tideandtea.owner.ui.theme.Gold
import com.tideandtea.owner.ui.theme.Pink
import com.tideandtea.owner.ui.theme.Royal

/** The studio mascot: a little bunny whose ears twitch every few seconds. Drawn in a 120 x 130 box. */
@Composable
fun Bunny(size: Dp = 96.dp, modifier: Modifier = Modifier, hop: Boolean = false) {
    val t = rememberInfiniteTransition(label = "bunny")
    val ear by t.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3400
                0f at 2400
                -12f at 2650
                4f at 2900
                0f at 3150
            }
        ),
        label = "ear",
    )
    val jump by t.animateFloat(
        initialValue = 0f, targetValue = if (hop) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hop",
    )
    Canvas(modifier.size(size, size * 130f / 120f)) {
        val s = this.size.width / 120f
        val dy = -10f * s * jump
        fun o(x: Float, y: Float) = Offset(x * s, y * s + dy)
        val stroke = Stroke(width = 3f * s)

        fun earAt(cx: Float, angle: Float) {
            rotate(angle, pivot = o(cx, 62f)) {
                drawOval(BunnyWhite, o(cx - 13f, 2f), Size(26f * s, 64f * s))
                drawOval(Royal, o(cx - 13f, 2f), Size(26f * s, 64f * s), style = stroke)
                drawOval(Pink, o(cx - 6f, 14f), Size(12f * s, 44f * s))
            }
        }
        earAt(42f, ear)
        earAt(78f, -ear)

        drawOval(BunnyWhite, o(20f, 56f), Size(80f * s, 68f * s))
        drawOval(Royal, o(20f, 56f), Size(80f * s, 68f * s), style = stroke)
        // eyes
        drawCircle(Color(0xFF2A1442), 4.5f * s, o(45f, 86f))
        drawCircle(Color(0xFF2A1442), 4.5f * s, o(75f, 86f))
        drawCircle(Color.White, 1.5f * s, o(46.6f, 84.4f))
        drawCircle(Color.White, 1.5f * s, o(76.6f, 84.4f))
        // cheeks
        drawOval(Pink.copy(alpha = 0.85f), o(28f, 93.5f), Size(14f * s, 9f * s))
        drawOval(Pink.copy(alpha = 0.85f), o(78f, 93.5f), Size(14f * s, 9f * s))
        // nose and mouth
        val nose = Path().apply { moveTo(o(57f, 94.5f).x, o(57f, 94.5f).y); lineTo(o(63f, 94.5f).x, o(63f, 94.5f).y); lineTo(o(60f, 97.7f).x, o(60f, 97.7f).y); close() }
        drawPath(nose, Color(0xFFE58AAB))
        val mouth = Stroke(width = 2f * s, cap = StrokeCap.Round)
        drawArc(Royal, 0f, 180f, false, o(55f, 96f), Size(5f * s, 5f * s), style = mouth)
        drawArc(Royal, 0f, 180f, false, o(60f, 96f), Size(5f * s, 5f * s), style = mouth)
    }
}

/** Four-point sparkle from the logo. */
@Composable
fun Sparkle(size: Dp = 18.dp, color: Color = Gold, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sparkle")
    val k by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "k")
    Canvas(modifier.size(size)) { sparkle(center, this.size.minDimension / 2f * k, color) }
}

fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticBezierTo(c.x, c.y, c.x + r, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y + r)
        quadraticBezierTo(c.x, c.y, c.x - r, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
}
