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

/** Animated flat bunny mascot inspired by the supplied loading reference. */
class LoadingMascotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF050607.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val darkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF050607.toInt()
        style = Paint.Style.FILL
    }
    private val eyeOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF050607.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val irisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4CA9A8.toInt()
        style = Paint.Style.FILL
    }
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val lashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF050607.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22000000
        style = Paint.Style.FILL
    }
    private val closedLidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF050607.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val earOuter = Path().apply {
        moveTo(-17f, 0f)
        cubicTo(-28f, -32f, -30f, -79f, -20f, -105f)
        cubicTo(-13f, -124f, 8f, -124f, 18f, -105f)
        cubicTo(31f, -79f, 26f, -31f, 17f, 0f)
        cubicTo(7f, 9f, -8f, 9f, -17f, 0f)
        close()
    }
    private val earInner = Path().apply {
        moveTo(-10f, -14f)
        cubicTo(-19f, -45f, -19f, -82f, -11f, -103f)
        cubicTo(-6f, -114f, 5f, -113f, 11f, -101f)
        cubicTo(20f, -79f, 16f, -43f, 10f, -14f)
        cubicTo(4f, -8f, -4f, -8f, -10f, -14f)
        close()
    }
    private val head = Path().apply {
        moveTo(100f, 77f)
        cubicTo(145f, 77f, 179f, 107f, 179f, 148f)
        cubicTo(179f, 186f, 147f, 211f, 100f, 214f)
        cubicTo(53f, 211f, 21f, 186f, 21f, 148f)
        cubicTo(21f, 107f, 55f, 77f, 100f, 77f)
        close()
    }
    private val eye = Path().apply {
        moveTo(-27f, 0f)
        cubicTo(-19f, -17f, 4f, -21f, 22f, -7f)
        cubicTo(31f, 0f, 29f, 7f, 21f, 14f)
        cubicTo(5f, 26f, -18f, 21f, -27f, 7f)
        cubicTo(-29f, 4f, -29f, 2f, -27f, 0f)
        close()
    }
    private val closedEye = Path().apply {
        moveTo(-26f, 1f)
        cubicTo(-13f, 8f, 12f, 8f, 26f, 0f)
    }
    private val nose = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-4f, -4f, -11f, -3f, -12f, 0f)
        cubicTo(-11f, 4f, -4f, 9f, 0f, 10f)
        cubicTo(4f, 9f, 11f, 4f, 12f, 0f)
        cubicTo(11f, -3f, 4f, -4f, 0f, 0f)
        close()
    }
    private var progress = 0f
    private var animator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isShown) startAnimation() else stopAnimation()
    }

    private fun startAnimation() {
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

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / 200f, height / 220f)
        if (scale <= 0f) return
        canvas.save()
        canvas.translate((width - 200f * scale) / 2f, (height - 220f * scale) / 2f)
        canvas.scale(scale, scale)
        canvas.drawOval(RectF(31f, 205f, 169f, 219f), shadowPaint)

        val turn = progress * (Math.PI * 2.0)
        val headShift = (sin(turn * 3.2) * 2.2).toFloat()
        val headTilt = (sin(turn * 2.3) * 2.2).toFloat()
        canvas.save()
        canvas.translate(headShift, 0f)
        canvas.rotate(headTilt, 100f, 145f)

        // Ears are independently hinged and flick in opposite directions.
        val leftEar = -9f + (sin(turn * 1.7 + 0.4) * 10.0 + sin(turn * 5.2) * 2.0).toFloat()
        val rightEar = 9f + (sin(turn * 1.9 + 2.0) * 11.0 + sin(turn * 5.7 + 1.0) * 2.2).toFloat()
        drawEar(canvas, 66f, 125f, leftEar)
        drawEar(canvas, 134f, 125f, rightEar)

        canvas.drawPath(head, white)
        canvas.drawPath(head, black)

        val gaze = (sin(turn * 3.1) * 9.0 + sin(turn * 7.0) * 2.5).toFloat()
        val gazeY = (sin(turn * 3.1 + 1.2) * 2.4).toFloat()
        val blinkPhase = (progress * 2.0f) % 1f
        val blink = if (blinkPhase < 0.14f) {
            sin((blinkPhase / 0.14f) * Math.PI).toFloat()
        } else 0f
        drawEye(canvas, 62f, 147f, gaze, gazeY, blink, mirror = false)
        drawEye(canvas, 138f, 147f, -gaze, gazeY, blink, mirror = true)

        canvas.save()
        canvas.translate(100f, 176f)
        canvas.drawPath(nose, darkFill)
        canvas.restore()
        canvas.restore()
        canvas.restore()
    }

    private fun drawEar(canvas: Canvas, x: Float, y: Float, angle: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
        canvas.drawPath(earOuter, white)
        canvas.drawPath(earOuter, black)
        canvas.drawPath(earInner, darkFill)
        canvas.restore()
    }

    private fun drawEye(
        canvas: Canvas,
        x: Float,
        y: Float,
        gaze: Float,
        gazeY: Float,
        blink: Float,
        mirror: Boolean
    ) {
        canvas.save()
        canvas.translate(x, y)
        if (mirror) canvas.scale(-1f, 1f)
        val open = 1f - blink
        if (open > 0.2f) {
            canvas.save()
            canvas.scale(1f, open)
            canvas.drawPath(eye, white)
            canvas.drawPath(eye, eyeOutline)

            canvas.save()
            canvas.clipPath(eye)
            canvas.translate(gaze, gazeY)
            canvas.drawOval(RectF(-13f, -15f, 14f, 15f), irisPaint)
            canvas.drawOval(RectF(-3.5f, -13f, 4f, 13f), darkFill)
            canvas.drawOval(RectF(-7f, -9f, -3f, -4f), highlight)
            canvas.restore()
            canvas.restore()

            // Sharp outer lashes echo the pointed anime eye in the reference.
            canvas.drawLine(-22f, -8f, -30f, -17f, lashPaint)
            canvas.drawLine(-25f, -2f, -34f, -5f, lashPaint)
            canvas.restore()
        } else {
            canvas.drawPath(closedEye, closedLidPaint)
        }
        canvas.restore()
    }
}
