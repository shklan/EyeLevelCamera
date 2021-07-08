package jp.ac.titech.itpro.sdl.eyelevelcamera

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Camera
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.PersistableBundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*
import java.util.jar.Manifest

class MainActivity : AppCompatActivity() {
    private val TAG: String = "EYE_LEVEL_CAMERA"
    private val ACTIVITY_STATUS: String = "activity_status"
    private val REQUEST_CAMERA = 200
    private val REQUEST_STORAGE = 201
    private var windowRoration: Int? = null
    private lateinit var metrics: DisplayMetrics

    private var mCaptureSession: CameraCaptureSession? = null
    private lateinit var mPreviewRequestBuilder: CaptureRequest.Builder
    private lateinit var mPreviewRequest: CaptureRequest
    private lateinit var preview: CameraPreview
    private lateinit var mCameraId: String
    private var backThread : HandlerThread? = null
    private var backHandler : Handler? = null
    private var frontThread : HandlerThread? = null

    private var frontHandler: Handler? = null
    private var mCamera: CameraDevice? = null
    private val cameraManager by lazy {
        this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private val cameraFacing: Int = CameraCharacteristics.LENS_FACING_BACK
    private var activityStopped: Boolean = false

    private val previewListener : TextureView.SurfaceTextureListener =
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
//                TODO("Not yet implemented")
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
//                TODO("Not yet implemented")
            }

        }
    private val mCameraCallback : CameraDevice.StateCallback =
        object : CameraDevice.StateCallback () {
            override fun onOpened(camera: CameraDevice) {
                mCamera = camera
                createCameraCaptureSession()
            }
            override fun onDisconnected(camera: CameraDevice) {
                closeCamera()
            }
            override fun onError(camera: CameraDevice, error: Int) {
                closeCamera()
            }
        }
    private val mStateCallback : CameraCaptureSession.StateCallback =
        object : CameraCaptureSession.StateCallback () {
            override fun onConfigured(session: CameraCaptureSession) {
                mCaptureSession = session
                mCaptureSession?.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, backHandler)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {

            }
        }
    private val mCaptureCallback : CameraCaptureSession.CaptureCallback =
        object : CameraCaptureSession.CaptureCallback () {

        }
    private val mOnImageAvailableListener : ImageReader.OnImageAvailableListener? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: ")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preview = findViewById(R.id.preview)
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowRoration = windowManager.defaultDisplay.rotation
        metrics = this.resources.displayMetrics
        requestPermission()
        preview.surfaceTextureListener = previewListener

        if (savedInstanceState != null) {
            activityStopped = savedInstanceState.getBoolean(ACTIVITY_STATUS)
        }

        // 撮影ボタン
        val button = findViewById<Button>(R.id.photo_button)
        button.setOnClickListener(fun(view: View) {
//            mCameraId = cameraManager.cameraIdList[0]
//            this.openCamera()
        })
    }

    override fun onResume() {
        Log.d(TAG, "onResume: ")
        super.onResume()
        Log.d(TAG, "activitiyStopped: "+activityStopped)
        if (preview.isAvailable && activityStopped) {
            openCamera()
            activityStopped = false
        }
    }

    // バックグラウンドになったらカメラを切る
    override fun onPause() {
        Log.d(TAG, "onPause: ")
        super.onPause()
        closeCamera()
        activityStopped = true
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: ")
        super.onDestroy()
        closeCamera()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                finish()
            }
        }
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            } else {
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ACTIVITY_STATUS, activityStopped)
    }

    private fun requestPermission() {
        val camera_permission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
        val save_permission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (camera_permission == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, Array<String>(1, {android.Manifest.permission.CAMERA}), REQUEST_CAMERA)
        }
        if (save_permission == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, Array<String>(1, {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}), REQUEST_STORAGE)
        }
    }

    private fun openCamera() {
        mCameraId = selectCameraId()
        configureOptimalPreview(mCameraId)
        cameraManager.openCamera(mCameraId, mCameraCallback, backHandler)
    }

    private fun createCameraCaptureSession() {
        val texture = preview.surfaceTexture
        // うまいことやる必要あり
        texture?.setDefaultBufferSize(preview.width, preview.height)
        val surface = Surface(texture)

        mPreviewRequestBuilder = mCamera!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        mPreviewRequestBuilder.addTarget(surface)

        mCamera!!.createCaptureSession(Arrays.asList(surface), mStateCallback, null)
    }

    private fun closeCamera() {
        if (mCamera != null) {
            mCamera?.close()
            mCamera = null
        }
        if (mCaptureSession != null) {
            mCaptureSession?.close()
            mCaptureSession = null
        }
    }

    private fun selectCameraId(): String {
        var selectedId = "0"
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == cameraFacing) {
                selectedId = id
                break
            }
        }
        return selectedId
    }

    private fun configureOptimalPreview(id: String) {
//        select optimal size of preview
//        set aspect ratio
        val characteristics = cameraManager.getCameraCharacteristics(id)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (map != null) {
            val availableSizes = map.getOutputSizes(SurfaceTexture::class.java)
            val optimalSize = selectOptimalSize(availableSizes)
            preview.setAspectRatio(optimalSize.getWidth(), optimalSize.getHeight())
        }
    }

    private fun selectOptimalSize(sizes: Array<Size>): Size {
        val targetWidth = metrics.widthPixels.toDouble()
        val targetHeight = metrics.heightPixels.toDouble()
        val targetRatio = targetHeight / targetWidth
        Log.d(TAG, "height: " +targetHeight+ ", width: " +targetWidth+ ", ratio: " +targetRatio)

        val candidates = hashMapOf<Double, Size>()
        for (size in sizes) {
            val ratio = size.height.toDouble() / size.width.toDouble()
            val diff = Math.abs(targetRatio - ratio)
            candidates.put(diff, size)
        }
        if (candidates.isEmpty()) {
            Log.d(TAG, "no candidates found")
            return sizes[0]
        } else {
            Log.d(TAG, "select candidates")
            val diffs = candidates.keys
            val optimalSize = candidates.get(diffs.minOrNull())!!
            Log.d(TAG, "height: " +optimalSize.height+ ", width: " +optimalSize.width)
            return optimalSize
        }
    }

    private fun transformPreview() {
        TODO("transformPreview")
    }

    private fun startBackThread() {
        backThread = HandlerThread("backThread")
        backThread!!.start()
        backHandler = Handler(backThread!!.looper)
    }

    private fun stopBackThread() {
        try {
            if (backThread != null) {
                backThread!!.quitSafely()
                backThread!!.join()
                backThread = null
                backHandler = null
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

//    fun shot(view: View) {
//
//    }
//
//

}