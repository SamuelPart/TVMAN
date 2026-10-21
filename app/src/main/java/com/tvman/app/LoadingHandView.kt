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

/** Flat blue cartoon hand with four separately tapping fingers. */
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
        color = 0xFF258BFF.toInt()
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
    private val fingerPath = Path().apply {
        moveTo(-13f, 2f)
        cubicTo(-17f, 6f, -17f, 15f, -16f, 28f)
        lineTo(-13f, 83f)
        cubicTo(-12f, 98f, -7f, 104f, 0f, 104f)
        cubicTo(8f, 104f, 13f, 98f, 14f, 83f)
        lineTo(17f, 28f)
        cubicTo(18f, 14f, 16f, 5f, 12f, 2f)
        cubicTo(6f, -3f, -7f, -3f, -13f, 2f)
        close()
    }
    private val palmPath = Path().apply {
        moveTo(27f, 88f)
        cubicTo(24f, 66f, 26f, 43f, 31f, 29f)
        cubicTo(36f, 14f, 50f, 13f, 59f, 24f)
        cubicTo(64f, 30f, 66f, 44f, 68f, 58f)
        cubicTo(69f, 32f, 77f, 18f, 91f, 18f)
        cubicTo(107f, 18f, 111f, 34f, 110f, 59f)
        cubicTo(113f, 35f, 122f, 24f, 135f, 26f)
        cubicTo(151f, 28f, 152f, 46f, 148f, 70f)
        cubicTo(155f, 54f, 169f, 52f, 176f, 64f)
        cubicTo(182f, 73f, 174f, 92f, 174f, 111f)
        cubicTo(174f, 140f, 151f, 157f, 123f, 158f)
        lineTo(73f, 158f)
        cubicTo(49f, 156f, 34f, 140f, 30f, 120f)
        close()
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

        canvas.drawOval(RectF(30f, 208f, 177f, 226f), shadow)

        // The palm sits behind the fingers so the four digits read as one hand.
        canvas.drawPath(palmPath, fill)
        canvas.drawPath(palmPath, outline)

        val fingerCenters = floatArrayOf(46f, 82f, 118f, 154f)
        fingerCenters.forEachIndexed { index, centerX ->
            val offset = index / 4f
            val phase = ((progress - offset + 1f) % 1f) / 0.2f
            val lift = if (phase < 1f) sin(phase * Math.PI).toFloat() else 0f
            val liftY = -17f * lift
            val rotation = -11f * lift

            canvas.save()
            canvas.translate(centerX, 113f + liftY)
            canvas.rotate(rotation)
            canvas.drawPath(fingerPath, fill)
            canvas.drawPath(fingerPath, outline)
            // A short crease adds cartoon detail without changing the solid blue fill.
            canvas.drawLine(-7f, 26f, 7f, 26f, crease)
            canvas.restore()
        }
        canvas.restore()
    }
}
