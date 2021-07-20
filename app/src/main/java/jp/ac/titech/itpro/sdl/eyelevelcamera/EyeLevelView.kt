package jp.ac.titech.itpro.sdl.eyelevelcamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.camera2.CameraCharacteristics
import android.util.AttributeSet
import android.util.Log
import android.view.View
import java.sql.Timestamp
import kotlin.math.atan

class EyeLevelView : View {
    private val eyeLevelPaint: Paint = Paint().apply {
        color = Color.BLUE
        alpha = 255
        strokeWidth = 5f
    }
    private var previewWidth: Int = 0
    private var previewHeight: Int = 0
    private var timestamp: Long? = null
    private var eyeLevel: Float = 0f
    private var angle: Float = 0f
    private val NS = 1.0f / 1000000000.0f
    private val ALPHA = 0.9f

    constructor(context: Context) : super(context) {
    }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    }
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
    }

    fun followSize(view: View) {
        previewWidth = view.width
        previewHeight = view.height
        Log.d(this::class.simpleName, "center : " +previewHeight/2)
        invalidate()
    }

    fun setAngle(focal: Float, height: Float) {
        Log.d(this::class.simpleName, "focal : " +focal)
        Log.d(this::class.simpleName, "height : " +height)
        angle = atan( height/(2*focal) )
        Log.d(this::class.simpleName, "angle : "+angle)
    }

    fun updateEyeLevel(rotation: FloatArray, ts: Long) {
//        方位角: rotation[0]
//        勾配: rotation[1]
//        回転: rotation[2]
        if (timestamp == null) {
            timestamp = ts
            eyeLevel = rotation[1]
        }
        eyeLevel = ALPHA * eyeLevel + (1-ALPHA) * rotation[1]
        timestamp = ts
//        Log.d("drawEyeLevel", "eyelevel = " +eyeLevel)
        invalidate()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val centerHeight = previewHeight.toFloat() / 2
        val leftX = 0f
        val lineHeight = centerHeight - centerHeight * Math.sin(eyeLevel.toDouble())/Math.sin(angle.toDouble())
        val rightX = width.toFloat()
        canvas?.drawLine(leftX, lineHeight.toFloat(), rightX, lineHeight.toFloat(), eyeLevelPaint)
    }

    fun drawTo(canvas: Canvas?) {
        val centerHeight = canvas!!.height.toFloat() / 2
        val leftX = 0f
        val lineHeight = centerHeight - centerHeight * Math.sin(eyeLevel.toDouble())/Math.sin(angle.toDouble())
        val rightX = canvas.width.toFloat()
        canvas?.drawLine(leftX, lineHeight.toFloat(), rightX, lineHeight.toFloat(), eyeLevelPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
    }
}