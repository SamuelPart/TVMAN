package com.tvman.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.sin

/** Flat foreshortened cartoon hand with four separately tapping fingers. */
class LoadingHandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF080B12.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF218CFF.toInt()
        style = Paint.Style.FILL
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        style = Paint.Style.FILL
    }
    private val crease = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0758B8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }

    // A single rounded palm silhouette; its lower edge is hidden by the fingers.
    private val palmFill = Path().apply {
        moveTo(30f, 88f)
        cubicTo(24f, 61f, 31f, 34f, 47f, 21f)
        cubicTo(64f, 8f, 83f, 15f, 99f, 27f)
        cubicTo(118f, 12f, 143f, 16f, 160f, 34f)
        cubicTo(178f, 52f, 176f, 78f, 168f, 101f)
        lineTo(161f, 118f)
        cubicTo(154f, 132f, 139f, 138f, 119f, 138f)
        lineTo(79f, 138f)
        cubicTo(58f, 138f, 41f, 130f, 34f, 113f)
        close()
    }
    private val palmOutline = Path().apply {
        moveTo(30f, 88f)
        cubicTo(24f, 61f, 31f, 34f, 47f, 21f)
        cubicTo(64f, 8f, 83f, 15f, 99f, 27f)
        cubicTo(118f, 12f, 143f, 16f, 160f, 34f)
        cubicTo(178f, 52f, 176f, 78f, 168f, 101f)
        lineTo(161f, 118f)
        cubicTo(154f, 132f, 139f, 138f, 119f, 138f)
    }
    private val fingerFill = Path().apply {
        moveTo(-12f, 0f)
        cubicTo(-16f, 7f, -15f, 20f, -14f, 34f)
        lineTo(-12f, 78f)
        cubicTo(-11f, 98f, -7f, 109f, 0f, 110f)
        cubicTo(9f, 109f, 13f, 98f, 14f, 78f)
        lineTo(16f, 34f)
        cubicTo(17f, 20f, 16f, 7f, 12f, 0f)
        cubicTo(6f, -4f, -6f, -4f, -12f, 0f)
        close()
    }
    // Open at the knuckle so the fingers emerge naturally from behind the palm.
    private val fingerOutline = Path().apply {
        moveTo(-12f, 0f)
        cubicTo(-16f, 7f, -15f, 20f, -14f, 34f)
        lineTo(-12f, 78f)
        cubicTo(-11f, 98f, -7f, 109f, 0f, 110f)
        cubicTo(9f, 109f, 13f, 98f, 14f, 78f)
        lineTo(16f, 34f)
        cubicTo(17f, 20f, 16f, 7f, 12f, 0f)
    }

    private var progress = 0f
    private var animator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTapping()
    }

    override fun onDetachedFromWindow() {
        stopTapping()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isShown) startTapping() else stopTapping()
    }

    private fun startTapping() {
        if (animator != null || !isShown) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopTapping() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / 200f, height / 235f)
        if (scale <= 0f) return
        canvas.save()
        canvas.translate((width - 200f * scale) / 2f, (height - 235f * scale) / 2f)
        canvas.scale(scale, scale)
        canvas.drawOval(RectF(28f, 216f, 177f, 232f), shadow)

        // Draw the palm first, then four separated fingers pointing toward the viewer.
        canvas.drawPath(palmFill, fill)
        canvas.drawPath(palmOutline, outline)
        val fingerCenters = floatArrayOf(42f, 81f, 120f, 159f)
        fingerCenters.forEachIndexed { index, centerX ->
            val offset = index / 4f
            val cyclePart = ((progress - offset + 1f) % 1f) / 0.2f
            val lift = if (cyclePart < 1f) sin(cyclePart * Math.PI).toFloat() else 0f
            val liftY = -17f * lift
            val rotation = -9f * lift

            canvas.save()
            canvas.translate(centerX, 103f + liftY)
            canvas.rotate(rotation)
            canvas.drawPath(fingerFill, fill)
            canvas.save()
            canvas.clipRect(-24f, 28f, 24f, 112f)
            canvas.drawPath(fingerOutline, outline)
            canvas.restore()
            canvas.drawLine(-7f, 67f, 7f, 67f, crease)
            canvas.restore()
        }
        canvas.restore()
    }
}
