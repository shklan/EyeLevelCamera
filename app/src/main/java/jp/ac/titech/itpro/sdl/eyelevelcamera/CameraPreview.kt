package jp.ac.titech.itpro.sdl.eyelevelcamera

import android.content.Context
import android.hardware.camera2.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Rational
import android.view.SurfaceHolder
import android.view.TextureView
import android.view.View.MeasureSpec
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraPreview : TextureView {
    private var widthRatio : Int? = null
    private var heightRatio : Int? = null
    constructor(context: Context) :super(context) {
    }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
    }
    constructor(context: Context, attrs: AttributeSet, defstyle: Int): super(context, attrs, defstyle) {
    }

    fun setAspectRatio(width: Int, height: Int) {
        widthRatio = width
        heightRatio = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (widthRatio == null || heightRatio == null) {
            setMeasuredDimension(width, height)
        } else {
            val fixedHeight = width * heightRatio!! / widthRatio!!
            val fixedWidth = height * widthRatio!! / heightRatio!!
            if (width < fixedWidth) {
                setMeasuredDimension(width, fixedHeight)
            }
            else {
                setMeasuredDimension(fixedWidth, height)
            }
        }
    }
}