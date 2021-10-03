package com.example.excam001

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class MyCameraManager(activity: Activity?) :
    ActivityCompat.OnRequestPermissionsResultCallback {
    companion object {
        private const val TAG = "MyCameraManager"
    }

    private var mActivity: Activity? = null
    private val mSynchronize = Any()
    private val mSemaphore = Semaphore(1)
    private var mThread: HandlerThread? = null
    private var mHandler: Handler? = null
    private var mCharacteristics: CameraCharacteristics? = null
    private var mJpegSizes: ArrayList<Size>? = null
    private var mYuvSizes: ArrayList<Size>? = null
    private var mYuvReader: ImageReader? = null
    private var mYuvMap: TreeMap<Long, Image>? = null
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null

    private fun scanCameraCharacteristics(cameraId: String?): Boolean {
        val id = cameraId ?: return false
        val manager = mActivity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
        manager ?: return false

        mJpegSizes = ArrayList()
        mYuvSizes = ArrayList()
        mJpegSizes ?: return false
        mYuvSizes ?: return false

        val characteristics = manager.getCameraCharacteristics(id)
        characteristics[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]?.let {
            // TODO : check if functions are available (RAW etc..)
        }

        val map = characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
        map?.let {
            mJpegSizes?.addAll(it.getOutputSizes(ImageFormat.JPEG))
            mYuvSizes?.addAll(it.getOutputSizes(ImageFormat.YUV_420_888))
        }
        mJpegSizes?.sortByDescending { it.width * it.height }
        mYuvSizes?.sortByDescending { it.width * it.height }

        for (size in mJpegSizes!!) {
            Log.d(
                TAG,
                String.format("cameraId = %s [JPEG size] %dx%d", cameraId, size.width, size.height)
            )
        }
        for (size in mYuvSizes!!) {
            Log.d(
                TAG,
                String.format("cameraId = %s [YUV size] %dx%d", cameraId, size.width, size.height)
            )
        }

        mCharacteristics = characteristics
        return true
    }

    init {
        mActivity = activity
    }

    fun getCameraIds(): Array<String>? {
        val manager = mActivity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
        manager ?: return null
        return manager.cameraIdList
    }

    fun getJpegSize(): Size? {
        val sizes = mJpegSizes ?: return null
        if (0 < sizes.size) {
            return sizes[0]
        }
        return null
    }

    fun sensorToDeviceRotation(deviceOrientation: Int): Int {
        val c = mCharacteristics ?: return 0
        var orientation = deviceOrientation
        val sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: return 0

        // Get device orientation in degrees
        orientation = Defines.ORIENTATIONS.get(orientation)

        // Reverse device orientation for front-facing cameras
        if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            orientation = -orientation
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (sensorOrientation - orientation + 360) % 360
    }

    fun chooseOptimalSize(
        textureViewWidth: Int,
        textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size
    ): Size {
        val characteristics = mCharacteristics ?: return Size(0, 0)
        val map =
            characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP] ?: return Size(
                0,
                0
            )
        val choices = map.getOutputSizes(SurfaceTexture::class.java)
        // Collect the supported resolutions that are at least as big as the preview Surface
        val bigEnough: MutableList<Size> = ArrayList()
        // Collect the supported resolutions that are smaller than the preview Surface
        val notBigEnough: MutableList<Size> = ArrayList()
        val w = aspectRatio.width
        val h = aspectRatio.height
        for (option in choices) {
            if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w) {
                if (option.width >= textureViewWidth &&
                    option.height >= textureViewHeight
                ) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        return when {
            bigEnough.size > 0 -> {
                var ret = bigEnough[0]
                for (size in bigEnough) {
                    if (size.width * size.height < ret.width * ret.height) {
                        ret = size
                    }
                }
                ret
            }
            notBigEnough.size > 0 -> {
                var ret = notBigEnough[0]
                for (size in notBigEnough) {
                    if (size.width * size.height > ret.width * ret.height) {
                        ret = size
                    }
                }
                ret
            }
            else -> {
                Log.e(TAG, "Couldn't find any suitable preview size")
                choices[0]
            }
        }
    }

    fun open(cameraId: String?): Boolean {
        val id = cameraId ?: return false
        val activity = mActivity ?: return false

        mThread = HandlerThread("MyCameraManagerThread")
        mThread?.let {
            it.start()
            synchronized(mSynchronize) {
                mHandler = Handler(it.looper)
            }
        }

        mCharacteristics = null
        if (!scanCameraCharacteristics(cameraId)) {
            return false
        }

        // setup CameraDevice
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    activity,
                    Defines.CAMERA_PERMISSIONS,
                    Defines.REQUEST_CAMERA_PERMISSIONS
                )
                return false
            }
            if (!mSemaphore.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw java.lang.RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(id, mDeviceStateCallback, mHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw java.lang.RuntimeException("Interrupted while trying to lock camera opening.", e)
        }

        // setup ImageReader for receiving images.
        mYuvMap = TreeMap<Long, Image>()
        mYuvSizes?.let {
            if (0 < it.size) {
                val size = it[0]
                mYuvReader =
                    ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 5)
            }
        }
        mYuvReader?.setOnImageAvailableListener(mOnImageAvailableListener, mHandler)

        return true
    }

    fun isFrontCameraOpened() : Boolean {
        val characteristics = mCharacteristics ?: throw java.lang.RuntimeException("Camera is not opened.")
        return CameraCharacteristics.LENS_FACING_FRONT == characteristics[CameraCharacteristics.LENS_FACING]
    }

    fun setPreview(texture: SurfaceTexture?, size: Size) {
        // setup SurfaceTexture for previewing.
        val sizes = mJpegSizes ?: return
        val cameraDevice = mCameraDevice ?: return
        val yuvReader = mYuvReader ?: return
        val yuvSurface = yuvReader.surface ?: return

        texture?.setDefaultBufferSize(sizes[0].width, sizes[0].height)
        val surface = Surface(texture)
        val builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder.addTarget(surface)
        mPreviewRequestBuilder = builder

        cameraDevice.createCaptureSession(
            listOf(surface, yuvSurface), mSessionStateCallback, mHandler
        )
    }

    fun close() {
        try {
            mSemaphore.acquire()
            mYuvReader?.close()
            mYuvReader = null
            mYuvMap = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mSemaphore.release()
        }

        mThread?.let {
            it.quitSafely()
            synchronized(mSynchronize) {
                it.join()
                mHandler = null
            }
        }
        mThread = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (Defines.REQUEST_CAMERA_PERMISSIONS == requestCode) {
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    val toast = Toast.makeText(
                        mActivity,
                        "Rejected Permission: " + permissions[i], Toast.LENGTH_SHORT
                    )
                    toast.show()
                }
            }
        }
    }

    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener {
        synchronized(mSynchronize) {
            val yuvReader = mYuvReader ?: return@synchronized
            val yuvMap = mYuvMap ?: return@synchronized
            val image = it.acquireLatestImage()
            while (Defines.YUV_QUEUE_SIZE <= yuvMap.size) {
                val key = yuvMap.firstKey()
                key.let {
                    Log.d(TAG, String.format("drop frame %d", it))
                    yuvMap.remove(it)
                }

            }
            mYuvReader?.let {
                val image = it.acquireLatestImage()
                mYuvMap?.plus(Pair(image.timestamp, image))
            }
        }
    }

    private val mDeviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            synchronized(mSynchronize) {
                mSemaphore.release()
                mCameraDevice = cameraDevice
            }
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            synchronized(mSynchronize) {
                mSemaphore.release()
                cameraDevice.close()
                mCameraDevice = null
            }
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            synchronized(mSynchronize) {
                mSemaphore.release()
                cameraDevice.close()
                mCameraDevice = null
            }
            mActivity?.finish() // XXX
        }
    }

    private val mSessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
            synchronized(mSynchronize) {
                mCameraDevice ?: return
                mPreviewRequestBuilder ?: return

                try {
                    val request = mPreviewRequestBuilder?.build() ?: return

                    // TODO : setup 3A setting and set into request

                    cameraCaptureSession.setRepeatingRequest(request, mCaptureCallback, mHandler)
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                    return
                } catch (e: java.lang.IllegalStateException) {
                    e.printStackTrace()
                    return
                }
                // When the session is ready, we start displaying the preview.
                mCaptureSession = cameraCaptureSession
            }
        }

        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
            val toast = Toast.makeText(
                mActivity,
                "Failed to configure camera.", Toast.LENGTH_SHORT
            )
            toast.show()
        }
    }

    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(
            session: CameraCaptureSession, request: CaptureRequest,
            partialResult: CaptureResult
        ) {
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession, request: CaptureRequest,
            result: TotalCaptureResult
        ) {
        }
    }
}