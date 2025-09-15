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
 * Kreisförmiges Badge-Widget zur Anzeige der Anzahl verbundener Syncthing-Geräte.
 * Zeigt einen roten Kreis mit "0" wenn keine Geräte verbunden sind,
 * oder einen grünen Kreis mit der Gerätezahl wenn Geräte verbunden sind.
 */
class CircularBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var deviceCount = 0
    
    // Farben für verbunden (grün) und nicht verbunden (rot)
    private val connectedColor = ContextCompat.getColor(context, android.R.color.holo_green_light)
    private val disconnectedColor = ContextCompat.getColor(context, android.R.color.holo_red_light)
    private val textColor = ContextCompat.getColor(context, android.R.color.white)
    
    // Paint-Objekte für Kreis und Text
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
        // Quadratische View mit fester Größe (40dp für bessere Sichtbarkeit)
        val size = (40 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(size, size)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Textgröße basierend auf View-Größe anpassen
        textPaint.textSize = w * 0.5f
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) * 0.9f // 10% Padding
        
        // Kreis-Farbe basierend auf Verbindungsstatus setzen
        circlePaint.color = if (deviceCount > 0) connectedColor else disconnectedColor
        
        // Kreis zeichnen
        canvas.drawCircle(centerX, centerY, radius, circlePaint)
        
        // Text (Gerätezahl) in der Mitte zeichnen
        val text = deviceCount.toString()
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, centerX, textY, textPaint)
    }
    
    /**
     * Setzt die Anzahl der verbundenen Geräte und löst einen Redraw aus.
     * Die Farbe des Kreises wird automatisch angepasst.
     */
    fun setDeviceCount(count: Int) {
        if (deviceCount != count) {
            deviceCount = count
            invalidate() // View neu zeichnen
        }
    }
    
    /**
     * Gibt die aktuelle Anzahl der verbundenen Geräte zurück.
     */
    fun getDeviceCount(): Int = deviceCount
}