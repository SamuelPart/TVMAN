package com.tvman.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Native Canvas Newton's cradle splash; no WebGL, browser, or external graphics dependency. */
class NewtonCradleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private var phase = 0f
    private var animator: ValueAnimator? = null
    private var renderFailed = false

    private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val railHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xBFFFFFFF.toInt()
        strokeWidth = dp(1.2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val cablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE2ECFA.toInt()
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
    }
    private val cableGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4468D9D5
        strokeWidth = dp(6f)
        strokeCap = Paint.Cap.ROUND
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB9C8DA.toInt()
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }
    private val postPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8295B0.toInt()
        style = Paint.Style.FILL
    }
    private val postHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xBFFFFFFF.toInt()
        strokeWidth = dp(1.5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF29364B.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val ballShine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1268D9D5
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAA68D9D5.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
    }
    private val ballShader = RadialGradient(
        0f,
        0f,
        1f,
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFE3EDF9.toInt(), 0xFF75849B.toInt(), 0xFF25334A.toInt()),
        floatArrayOf(0f, 0.18f, 0.63f, 1f),
        Shader.TileMode.CLAMP
    )
    private val shaderMatrix = Matrix()
    private val beamBounds = RectF()

    init {
        ballPaint.shader = ballShader
        railPaint.shader = LinearGradient(
            0f, 0f, 0f, dp(12f),
            intArrayOf(0xFFF7FBFF.toInt(), 0xFF7B8AA1.toInt(), 0xFF26344A.toInt()),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )
        contentDescription = "Cuna de Newton metálica en movimiento"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startMotion()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isShown) startMotion() else {
            animator?.cancel()
            animator = null
        }
    }

    private fun startMotion() {
        if (animator != null || renderFailed || !isShown) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2_500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (renderFailed) {
            drawFallback(canvas)
            return
        }
        val checkpoint = canvas.saveCount
        try {
            drawCradle(canvas)
        } catch (error: RuntimeException) {
            canvas.restoreToCount(checkpoint)
            renderFailed = true
            animator?.cancel()
            animator = null
            android.util.Log.e("NewtonCradleView", "Cradle rendering failed; drawing static fallback", error)
            drawFallback(canvas)
        }
    }

    private fun drawCradle(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = min(w / 14f, h / 9.6f).coerceAtLeast(dp(8f))
        val spacing = radius * 2.035f
        val centerX = w * 0.5f
        val pivotY = h * 0.30f
        val maxSwing = 0.45f
        val widthLimitedCable = ((w * 0.5f) - spacing * 2f - radius - dp(8f))
            .coerceAtLeast(radius * 0.8f) / sin(maxSwing)
        val cableLength = min(h * 0.365f, widthLimitedCable)
        val cradleWidth = spacing * 4f + radius * 2f
        val leftEdge = centerX - cradleWidth * 0.5f
        val rightEdge = centerX + cradleWidth * 0.5f
        val railTop = pivotY - dp(15f)
        val railBottom = pivotY - dp(5f)
        val groundY = pivotY + cableLength + radius * 1.28f

        val breath = 0.5f + 0.5f * sin(phase * 2.0 * PI).toFloat()
        val impactLeft = impactPulse(phase, 0.25f)
        val impactRight = impactPulse(phase, 0.75f)
        val impact = maxOf(impactLeft, impactRight)

        // Low-contrast neon atmosphere behind the polished metal.
        auraPaint.color = alphaColor(0xFF68D9D5.toInt(), (13 + 18 * breath).toInt())
        canvas.drawCircle(centerX, pivotY + cableLength * 0.82f, radius * 6.1f, auraPaint)
        auraPaint.color = alphaColor(0xFF8E50F5.toInt(), (8 + 10 * impact).toInt())
        canvas.drawCircle(centerX, pivotY + cableLength, radius * 4.4f, auraPaint)

        // Clearly visible side uprights, crossbar, diagonal braces, and feet.
        val postWidth = maxOf(dp(7f), radius * 0.20f)
        val leftPostX = leftEdge - radius * 0.72f
        val rightPostX = rightEdge + radius * 0.72f
        val postTop = railTop - radius * 0.72f
        val postBottom = groundY + radius * 0.55f
        canvas.drawRoundRect(leftPostX - postWidth / 2f, postTop, leftPostX + postWidth / 2f, postBottom,
            postWidth / 2f, postWidth / 2f, postPaint)
        canvas.drawRoundRect(rightPostX - postWidth / 2f, postTop, rightPostX + postWidth / 2f, postBottom,
            postWidth / 2f, postWidth / 2f, postPaint)
        canvas.drawLine(leftPostX - postWidth * 0.18f, postTop + dp(4f), leftPostX - postWidth * 0.18f, postBottom - radius * 0.18f, postHighlight)
        canvas.drawLine(rightPostX - postWidth * 0.18f, postTop + dp(4f), rightPostX - postWidth * 0.18f, postBottom - radius * 0.18f, postHighlight)
        framePaint.strokeWidth = maxOf(dp(3f), radius * 0.075f)
        canvas.drawLine(leftPostX, postBottom - radius * 0.9f, leftEdge + radius * 0.15f, groundY, framePaint)
        canvas.drawLine(rightPostX, postBottom - radius * 0.9f, rightEdge - radius * 0.15f, groundY, framePaint)
        framePaint.strokeWidth = maxOf(dp(7f), radius * 0.22f)
        canvas.drawLine(leftPostX - radius * 0.35f, postBottom, leftPostX + radius * 0.48f, postBottom, framePaint)
        canvas.drawLine(rightPostX - radius * 0.48f, postBottom, rightPostX + radius * 0.35f, postBottom, framePaint)
        beamBounds.set(leftEdge - radius * 1.1f, railTop, rightEdge + radius * 1.1f, railBottom)
        canvas.drawRoundRect(beamBounds, dp(5f), dp(5f), railPaint)
        canvas.drawLine(beamBounds.left + dp(5f), beamBounds.top + dp(1.5f), beamBounds.right - dp(5f), beamBounds.top + dp(1.5f), railHighlight)

        val angles = FloatArray(5)
        val p = phase.coerceIn(0f, 1f)
        when {
            p < 0.25f -> angles[0] = -maxSwing * cos((p / 0.25f * PI / 2.0)).toFloat()
            p < 0.50f -> angles[4] = maxSwing * sin(((p - 0.25f) / 0.25f * PI / 2.0)).toFloat()
            p < 0.75f -> angles[4] = maxSwing * cos(((p - 0.50f) / 0.25f * PI / 2.0)).toFloat()
            else -> angles[0] = -maxSwing * sin(((p - 0.75f) / 0.25f * PI / 2.0)).toFloat()
        }
        val coupling = sin(p * 4.0 * PI).toFloat() * (0.025f + impact * 0.055f)
        angles[1] = -coupling
        angles[2] = coupling * 0.45f
        angles[3] = coupling

        val ballXs = FloatArray(5)
        val ballYs = FloatArray(5)
        for (i in 0 until 5) {
            val pivotX = centerX + (i - 2) * spacing
            val angle = angles[i]
            ballXs[i] = pivotX + sin(angle) * cableLength
            ballYs[i] = pivotY + cos(angle) * cableLength
        }

        // Draw each suspension cable before its sphere for clean occlusion.
        for (i in 0 until 5) {
            val pivotX = centerX + (i - 2) * spacing
            val dx = ballXs[i] - pivotX
            val dy = ballYs[i] - pivotY
            val mag = (dx * dx + dy * dy).let { kotlin.math.sqrt(it) }.coerceAtLeast(1f)
            val endX = ballXs[i] - dx / mag * radius * 0.78f
            val endY = ballYs[i] - dy / mag * radius * 0.78f
            if (i == 0 || i == 4) {
                cableGlowPaint.alpha = (25 + 65 * impact).toInt().coerceIn(0, 90)
                canvas.drawLine(pivotX, pivotY, endX, endY, cableGlowPaint)
            }
            canvas.drawLine(pivotX, pivotY, endX, endY, cablePaint)
            canvas.drawCircle(pivotX, pivotY, dp(3.2f), railPaint)
        }

        // Silver spheres with moving impact glints and dark precision outlines.
        for (i in 0 until 5) {
            val cx = ballXs[i]
            val cy = ballYs[i]
            shaderMatrix.setScale(radius * 1.55f, radius * 1.55f)
            shaderMatrix.postTranslate(cx - radius * 0.43f, cy - radius * 0.43f)
            ballShader.setLocalMatrix(shaderMatrix)
            canvas.drawCircle(cx, cy, radius, ballPaint)
            canvas.drawCircle(cx, cy, radius, ballOutline)
            canvas.drawArc(cx - radius * 0.72f, cy - radius * 0.72f, cx + radius * 0.72f, cy + radius * 0.72f,
                205f, 88f, false, ballShine)
        }

        // Tight neon collision flash at each transfer, instead of labels or loading text.
        if (impact > 0.06f) {
            accentPaint.alpha = (45 + impact * 150).toInt().coerceIn(0, 190)
            accentPaint.strokeWidth = dp(1f + impact * 2.2f)
            canvas.drawCircle(centerX, pivotY + cableLength, radius * (1.2f + impact * 0.55f), accentPaint)
        }
    }

    private fun impactPulse(p: Float, center: Float): Float {
        var delta = kotlin.math.abs(p - center)
        if (delta > 0.5f) delta = 1f - delta
        return (1f - (delta / 0.12f).coerceIn(0f, 1f)).let { it * it }
    }

    private fun alphaColor(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun drawFallback(canvas: Canvas) {
        val cx = width * 0.5f
        val top = height * 0.28f
        val r = min(width / 12f, height / 10f).coerceAtLeast(dp(8f))
        val gap = 2.03f * r
        val length = height * 0.36f
        val p = phase.coerceIn(0f, 1f)
        val swing = if (p < 0.5f) -0.45f * (1f - p * 2f) else 0.45f * (p - 0.5f) * 2f
        framePaint.strokeWidth = dp(4f)
        canvas.drawLine(cx - gap * 2.5f, top, cx + gap * 2.5f, top, framePaint)
        for (i in 0 until 5) {
            val pivotX = cx + (i - 2) * gap
            val angle = when (i) { 0 -> swing; 4 -> -swing; else -> 0f }
            val bx = pivotX + sin(angle) * length
            val by = top + cos(angle) * length
            canvas.drawLine(pivotX, top, bx, by, cablePaint)
            canvas.drawCircle(bx, by, r, ballPaint)
        }
    }

    private fun dp(value: Float): Float = value * density
}
