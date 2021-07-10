package jp.ac.titech.itpro.sdl.eyelevelcamera

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.AXIS_X
import android.hardware.SensorManager.AXIS_Z
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
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

class MainActivity : AppCompatActivity(), SensorEventListener {
    private val TAG = this::class.simpleName
    private val ACTIVITY_STATUS: String = "activity_status"
    private val REQUEST_CAMERA = 200
    private val REQUEST_STORAGE = 201

    //sensor
    private lateinit var eyeLevelView: EyeLevelView
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var magnetometer: Sensor
    private val accelerometerValue = FloatArray(3)
    private val magnetometerValue = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    //camera
    private var windowRoration: Int? = null
    private var cameraRotation: Int? = null
    private lateinit var metrics: DisplayMetrics

    private var mCaptureSession: CameraCaptureSession? = null
    private lateinit var mPreviewRequestBuilder: CaptureRequest.Builder
    private lateinit var mPreviewRequest: CaptureRequest
    private lateinit var preview: CameraPreview
    private lateinit var mCameraId: String
    private var backThread : HandlerThread? = null
    private var backHandler : Handler? = null

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
                Log.d(TAG, "onSurfaceTextureSizeChanged")
                eyeLevelView.followSize(preview)
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
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
            }

        }
    private val mOnImageAvailableListener : ImageReader.OnImageAvailableListener? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: ")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState != null) {
            activityStopped = savedInstanceState.getBoolean(ACTIVITY_STATUS)
        }
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowRoration = windowManager.defaultDisplay.rotation

        //sensor
        eyeLevelView = findViewById(R.id.eyeLevelView)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        if (sensorManager == null) {
            finish()
            return
        }
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        //camera
        preview = findViewById(R.id.preview)
        preview.surfaceTextureListener = previewListener
        metrics = this.resources.displayMetrics
        requestPermission()


        // 撮影ボタン
        val button = findViewById<Button>(R.id.photo_button)
        button.setOnClickListener(fun(view: View) {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            mCaptureSession?.capture(mPreviewRequestBuilder.build(), mCaptureCallback, backHandler)
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
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        Log.d(TAG, "onPause: ")
        super.onPause()
        closeCamera()
        activityStopped = true
        sensorManager.unregisterListener(this)
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

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerValue, 0, accelerometerValue.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerValue, 0, magnetometerValue.size)
        }
        val rawRotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrix(rawRotationMatrix, null, accelerometerValue, magnetometerValue)
//      X-Z平面が基準面となるように変換
        SensorManager.remapCoordinateSystem(rawRotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, rotationMatrix)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val timestamp = event.timestamp
//            Log.d(TAG, "X: " +eventValueX+ ", Y: " +eventValueY+ ", Z:" +eventValueZ)
        eyeLevelView.updateEyeLevel(orientation, timestamp)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
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
        val characteristics = cameraManager.getCameraCharacteristics(mCameraId)
        configureView(characteristics)
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

    private fun configureView(characteristics: CameraCharacteristics) {
        cameraRotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
        Log.d(TAG, "orientation " +cameraRotation)
        val focalLength = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val pixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val pixelWidth = pixelArraySize?.width
        val pixelHeight = pixelArraySize?.height
        val activeWidth = activeArraySize?.width()
        val activeHeight = activeArraySize?.height()
        val physicalWidth = physicalSize?.width
        val physicalHeight = physicalSize?.height

        // preview
        val matrix = Matrix()
        matrix.postRotate(90*windowRoration!!.toFloat(), preview.width * 0.5f, preview.height * 0.5f)
//            preview.setTransform(matrix)
        if ((cameraRotation!! + 90*windowRoration!!) % 180 == 0) {
            preview.setAspectRatio(activeWidth, activeHeight)
            eyeLevelView.setAngle(focalLength!![0], physicalHeight!! * activeHeight!! / pixelHeight!!)
        } else {
            preview.setAspectRatio(activeHeight, activeWidth)
            eyeLevelView.setAngle(focalLength!![0], physicalWidth!! * activeWidth!! / pixelWidth!!)
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