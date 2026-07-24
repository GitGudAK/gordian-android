package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldAccent
import kotlin.math.sin

@Composable
fun CalmingUpliftingAnimation(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CalmingTransition")

    // Breathing pulse scale (4-second cycle)
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreatheScale"
    )

    // Breathing glow intensity
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    // Ripple 1 Progress (expanding outward)
    val ripple1Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple1"
    )

    // Ripple 2 Progress (staggered delay, using offset in a separate tween cycle)
    val ripple2Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, delayMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple2"
    )

    // Particle time factor for floating upwards
    val particleFactor by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Particles"
    )

    // We can define a set of static particles, each with standard offset coordinates
    // and float them up using sin/cos for sway.
    val particles = remember {
        List(25) {
            ParticleData(
                xSeed = (0..100).random() / 100f,
                ySeed = (0..100).random() / 100f,
                speed = (10..35).random() / 10f,
                size = (2..5).random().toFloat()
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.minDimension / 4.5f

            // 1. Draw glowing radial background aura
            val glowRadius = baseRadius * 1.8f * breatheScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GoldPrimary.copy(alpha = glowAlpha),
                        GoldPrimary.copy(alpha = glowAlpha * 0.4f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // 2. Draw expanding ripple rings of released tension
            if (ripple1Progress > 0f) {
                val r1 = baseRadius + (size.minDimension / 2 - baseRadius) * ripple1Progress
                val alpha1 = (1f - ripple1Progress) * 0.35f
                drawCircle(
                    color = GoldPrimary.copy(alpha = alpha1),
                    radius = r1,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            if (ripple2Progress > 0f) {
                val r2 = baseRadius + (size.minDimension / 2 - baseRadius) * ripple2Progress
                val alpha2 = (1f - ripple2Progress) * 0.35f
                drawCircle(
                    color = GoldPrimary.copy(alpha = alpha2),
                    radius = r2,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 3. Draw floating uplifting embers
            particles.forEach { p ->
                val elapsedY = (particleFactor * p.speed) % 100f
                val startY = size.height + 20f
                val currentY = startY - (elapsedY / 100f) * (size.height + 40f)

                // Add gentle horizontal sway
                val sway = sin(particleFactor * 0.15f + p.xSeed * 10f) * 20.dp.toPx()
                val currentX = p.xSeed * size.width + sway

                // Fade out at boundaries
                val topProximity = currentY / size.height
                val particleAlpha = when {
                    topProximity < 0.2f -> topProximity / 0.2f
                    topProximity > 0.8f -> (1f - topProximity) / 0.2f
                    else -> 1f
                } * 0.6f

                if (particleAlpha > 0f) {
                    drawCircle(
                        color = GoldAccent.copy(alpha = particleAlpha),
                        radius = p.size.dp.toPx(),
                        center = Offset(currentX, currentY)
                    )
                }
            }

            // 4. Central peaceful core "The Untied Knot"
            val coreRadius = baseRadius * breatheScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GoldAccent,
                        GoldPrimary,
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )
        }

        // Inner breathing state text
        val breathText = if (breatheScale > 1.0f) "Breathe In Clarity" else "Release All Doubt"
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = breathText.uppercase(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "KNOT UNTIED",
                color = GoldAccent.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class ParticleData(
    val xSeed: Float,
    val ySeed: Float,
    val speed: Float,
    val size: Float
)
