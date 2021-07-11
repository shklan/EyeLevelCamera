package jp.ac.titech.itpro.sdl.eyelevelcamera

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.wifi.aware.Characteristics
import android.os.*
import android.text.style.LineHeightSpan
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
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
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
    private var windowRotation: Int? = null
    private var cameraRotation: Int? = null
    private lateinit var metrics: DisplayMetrics

    private var mImageReader: ImageReader? = null
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

    private val mCaptureCallback : CameraCaptureSession.CaptureCallback =
        object : CameraCaptureSession.CaptureCallback () {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: ")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState != null) {
            activityStopped = savedInstanceState.getBoolean(ACTIVITY_STATUS)
        }
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowRotation = windowManager.defaultDisplay.rotation
        requestPermission()

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
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                Log.d(TAG, "onSurfaceTextureSizeChanged")
                eyeLevelView.followSize(preview)
                transformPreview()
            }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
//                TODO("Not yet implemented")
            }
        }
        metrics = this.resources.displayMetrics

        // 撮影ボタン
        val button = findViewById<Button>(R.id.photo_button)
        button.setOnClickListener(fun(view: View) {
            if (mCaptureSession == null) return
            val captureBuilder = mCamera?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)!!
            val captureSurface = mImageReader!!.surface
            captureBuilder.addTarget(captureSurface)
            captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            mCaptureSession?.stopRepeating()
            mCaptureSession?.abortCaptures()
            mCaptureSession?.capture(captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        super.onCaptureCompleted(session, request, result)
                        mCaptureSession?.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, backHandler)
                    }
                }, backHandler)
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
//      X-Z平面が基準面の変換
        if (90 * windowRotation!! % 180 == 0) {
            SensorManager.remapCoordinateSystem(rawRotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, rotationMatrix)
        } else {
            SensorManager.remapCoordinateSystem(rawRotationMatrix, SensorManager.AXIS_MINUS_Z, SensorManager.AXIS_X, rotationMatrix)
        }
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
        cameraManager.openCamera(mCameraId,
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
            }, backHandler)
    }

    private fun createCameraCaptureSession() {
        val texture = preview.surfaceTexture
        // うまいことやる必要あり
        Log.d(TAG, "preview width " +preview.width+ ", height " +preview.height)
        texture?.setDefaultBufferSize(preview.width, preview.height)
        val previewSurface = Surface(texture)
        val captureSurface = mImageReader?.surface

        mPreviewRequestBuilder = mCamera!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        mPreviewRequestBuilder.addTarget(previewSurface)

        mCamera!!.createCaptureSession(Arrays.asList(previewSurface, captureSurface),
            object : CameraCaptureSession.StateCallback () {
                override fun onConfigured(session: CameraCaptureSession) {
                    mCaptureSession = session
                    mCaptureSession?.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, backHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {

                }
            }, null)
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
        if (mImageReader != null) {
            mImageReader?.close()
            mImageReader = null
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

        //imageReader
        mImageReader = ImageReader.newInstance(activeWidth!!, activeHeight!!, ImageFormat.JPEG, 4)
        mImageReader?.setOnImageAvailableListener(
            object : ImageReader.OnImageAvailableListener {
                override fun onImageAvailable(reader: ImageReader?) {
                    val image = reader?.acquireNextImage()
                    val buffer = image!!.planes[0].buffer
                    val rawData = ByteArray(buffer.capacity())
                    buffer.get(rawData)
                    val option = BitmapFactory.Options()
                    option.inMutable = true
                    val rawPicture = BitmapFactory.decodeByteArray(rawData, 0, rawData.size, option)

                    val matrix = Matrix()
                    val rotation = (cameraRotation!! - windowRotation!! * 90).toFloat()
                    Log.d(TAG, "rawPicture rotation " +rotation)
                    matrix.postRotate(rotation)
                    val transformedPicture = Bitmap.createBitmap(rawPicture, 0, 0, rawPicture.width, rawPicture.height, matrix, false)

                    // eyeLevelの描画
                    val outputCanvas = Canvas(transformedPicture)
                    val paint = Paint()
                    eyeLevelView.drawTo(outputCanvas)

                    // 保存
                    try {
                        val state = Environment.getExternalStorageState()
                        if (Environment.MEDIA_MOUNTED.equals(state)) {
                            Log.d(TAG, "export picture")
                            val exportDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES.toString())
                            val output = FileOutputStream(File(exportDir, "ELC.png"))
                            Log.d(TAG, "to "+exportDir)
                            transformedPicture.compress(Bitmap.CompressFormat.JPEG, 95, output)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }, backHandler)

        // preview
        var optimalSize: Size
        Log.d(TAG, "window rotation " +90*windowRotation!!)
        optimalSize = findOptimalSize(characteristics, activeWidth, activeHeight)

        if ((cameraRotation!! - 90*windowRotation!!) % 180 == 0) {
            Log.d(TAG, "rotated")
//            出力画面のリサイズ
            preview.setAspectRatio(optimalSize.width, optimalSize.height)
            eyeLevelView.setAngle(focalLength!![0], physicalHeight!! * activeHeight / pixelHeight!!)
        } else {
            Log.d(TAG, "normal")
//            出力画面のリサイズ
            preview.setAspectRatio(optimalSize.height, optimalSize.width)
            eyeLevelView.setAngle(focalLength!![0], physicalWidth!! * activeWidth / pixelWidth!!)
        }
    }

    private fun findOptimalSize(characteristics: CameraCharacteristics, width: Int, height: Int): Size {
        val aspect = width.toFloat() / height
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val candidate = hashMapOf<Int, Size>()
        val subCanditdate = hashMapOf<Int, Size>()
        val availableSizes = map.getOutputSizes(SurfaceTexture::class.java)

        for (size in availableSizes) {
            val candidateAspect = size.width.toFloat() / size.height
            if (Math.abs(aspect - candidateAspect) < 0.1) {
                if (size.width < preview.width && size.height < preview.height) {
                    candidate.put(size.width, size)
                } else {
                    subCanditdate.put(size.width, size)
                }
            }
        }

        if (!candidate.isEmpty()){
            Log.d(TAG, "select from candidate")
            val keys = candidate.keys
            return candidate.get(keys.maxOrNull()!!)!!
        } else if (!subCanditdate.isEmpty()) {
            Log.d(TAG, "select from subCandidate")
            val keys = subCanditdate.keys
            return subCanditdate.get(keys.minOrNull()!!)!!
        } else {
            Log.d(TAG, "select from default (0)")
            return availableSizes[0]
        }
    }

    private fun transformPreview() {
        Log.d(TAG, "transformPreview")
        val characteristics = cameraManager.getCameraCharacteristics(mCameraId)
        val activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
        val activeWidth = activeArraySize.width()
        val activeHeight = activeArraySize.height()

        val matrix = Matrix()
        Log.d(TAG, "" + (cameraRotation!! - 90*windowRotation!!))
        if ((cameraRotation!! - 90*windowRotation!!) % 180 == 0) {
            Log.d(TAG, "rotated")
            Log.d(TAG, "preview " +preview.width+ " : "+ preview.height)
            Log.d(TAG, "newRect activeWidth = " +activeWidth+ ", : activeHeight = " +activeHeight)

            val oldRect = RectF(0f, 0f, preview.width.toFloat(), preview.height.toFloat())
            val newRect = RectF(0f, 0f, activeHeight.toFloat(), activeWidth.toFloat())
            newRect.offset(oldRect.centerX() - newRect.centerX(), oldRect.centerY() - newRect.centerY())

//            今の view を（回転前の）view に変形
            matrix.setRectToRect(oldRect, newRect, Matrix.ScaleToFit.FILL)
//            X: スマホの縦方向，Y: スマホの横方向
            val scaleX = oldRect.height() / newRect.width()
            val scaleY = oldRect.width() / newRect.height()
            val scale = Math.min(scaleX, scaleY)
            Log.d(TAG, "scaleX = " +scaleX+ ", scaleY = " +scaleY)
            matrix.postScale(scale, scale, oldRect.centerX(), oldRect.centerY())
//            view の回転（縦横比がここで正しくなる）
            matrix.postRotate(-90*windowRotation!!.toFloat(), oldRect.centerX(), oldRect.centerY())
//            Log.d(TAG, "matrix " +matrix.toShortString())
        } else {
            Log.d(TAG, "normal")
            matrix.postRotate(-90*windowRotation!!.toFloat(), activeHeight/2f, activeWidth/2f)
        }
        preview.setTransform(matrix)
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
}