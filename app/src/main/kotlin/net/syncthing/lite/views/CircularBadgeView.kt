package net.syncthing.lite.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import net.syncthing.lite.R

/**
 * Circular badge widget for displaying the number of connected Syncthing devices.
 * Shows a red circle with "0" when no devices are connected,
 * or a green circle with the device count when devices are connected.
 */
class CircularBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var deviceCount = 0
    
    // Colors for connected (green) and disconnected (red)
    private val connectedColor = ContextCompat.getColor(context, android.R.color.holo_green_light)
    private val disconnectedColor = ContextCompat.getColor(context, android.R.color.holo_red_light)
    private val textColor = ContextCompat.getColor(context, android.R.color.white)
    
    // Paint objects for circle and text
    private val circlePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = textColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Square view with fixed size (40dp for better visibility)
        val size = (40 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(size, size)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Adjust text size based on view size
        textPaint.textSize = w * 0.5f
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) * 0.9f // 10% padding
        
        // Set circle color based on connection status
        circlePaint.color = if (deviceCount > 0) connectedColor else disconnectedColor
        
        // Draw circle
        canvas.drawCircle(centerX, centerY, radius, circlePaint)
        
        // Draw text (device count) in the center
        val text = deviceCount.toString()
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, centerX, textY, textPaint)
    }
    
    /**
     * Sets the number of connected devices and triggers a redraw.
     * The color of the circle is automatically adjusted.
     */
    fun setDeviceCount(count: Int) {
        if (deviceCount != count) {
            deviceCount = count
            invalidate() // Redraw view
        }
    }
    
    /**
     * Returns the current number of connected devices.
     */
    fun getDeviceCount(): Int = deviceCount
}