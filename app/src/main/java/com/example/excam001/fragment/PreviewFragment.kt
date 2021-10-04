package com.example.excam001.fragment

import android.graphics.*
import android.os.Bundle
import android.util.Size
import android.view.*
import android.widget.CompoundButton
import android.widget.Switch
import androidx.fragment.app.Fragment
import com.example.excam001.Defines
import com.example.excam001.MyCameraManager
import com.example.excam001.R
import com.example.excam001.view.AutoFitTextureView
import java.lang.Float.max

class PreviewFragment : Fragment(), View.OnClickListener, CompoundButton.OnCheckedChangeListener,
    TextureView.SurfaceTextureListener {
    private var mMyCameraManager: MyCameraManager? = null
    private var mOriginalImageView: AutoFitTextureView? = null

    private fun configureTransform(width: Int, height: Int) {
        val view = mOriginalImageView ?: return
        val surfaceTexture = view.surfaceTexture ?: return
        val cameraManager = mMyCameraManager ?: return
        val act = activity ?: return
        val size = cameraManager.getJpegSize() ?: return
        val deviceRotation = act.windowManager.defaultDisplay.rotation
        val displaySize = Point()
        act.windowManager.defaultDisplay.getSize(displaySize)
        val totalRotation = cameraManager.sensorToDeviceRotation(deviceRotation)

        val swappedDimensions = 90 == totalRotation || 270 == totalRotation
        var rotatedWidth = width
        var rotatedHeight = height
        var maxWidth = displaySize.x
        var maxHeight = displaySize.y
        if (swappedDimensions) {
            rotatedWidth = rotatedHeight.also { rotatedHeight = rotatedWidth }
            maxWidth = maxHeight.also { maxHeight = maxWidth }
        }
        maxWidth = kotlin.math.min(maxWidth, Defines.MAX_PREVIEW_WIDTH)
        maxHeight = kotlin.math.min(maxHeight, Defines.MAX_PREVIEW_HEIGHT)

        val previewSize: Size = cameraManager.chooseOptimalSize(
            rotatedWidth, rotatedHeight, maxWidth, maxHeight, size)
        if (swappedDimensions) {
            view.setAspectRatio(
                previewSize.height, previewSize.width
            )
        } else {
            view.setAspectRatio(
                previewSize.width, previewSize.height
            )
        }

        val rotation = if (cameraManager.isFrontCameraOpened()) {
                (360 + Defines.ORIENTATIONS.get(deviceRotation)) % 360
            } else {
                (360 - Defines.ORIENTATIONS.get(deviceRotation)) % 360
            }
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val center = PointF(viewRect.centerX(), viewRect.centerY())

        bufferRect.offset(center.x - bufferRect.centerX(), center.y - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
        if (Surface.ROTATION_90 == deviceRotation || Surface.ROTATION_270 == deviceRotation) {
            val scale = kotlin.math.min(
                height.toFloat() / previewSize.height,
                width.toFloat() / previewSize.width)
            matrix.postScale(scale, scale, center.x, center.y)
        } else {
            val scale = kotlin.math.min(
                width.toFloat() / previewSize.height,
                height.toFloat() / previewSize.width)
            matrix.postScale(scale, scale, center.x, center.y)
        }
        matrix.postRotate(rotation.toFloat(), center.x, center.y)
        mOriginalImageView?.setTransform(matrix)
        cameraManager.setPreview(surfaceTexture, size)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.capture).setOnClickListener(this)
        view.findViewById<Switch>(R.id.process_switch).setOnCheckedChangeListener(this)
        mOriginalImageView = view.findViewById<View>(R.id.original_image) as AutoFitTextureView
    }

    override fun onResume() {
        super.onResume()
        mMyCameraManager = MyCameraManager(activity)
        val cameraIds = mMyCameraManager?.getCameraIds()
        if (cameraIds?.contains("0")!!) {
            mMyCameraManager?.open("0")
        }
        mOriginalImageView?.let {
            if (it.isAvailable) {
                configureTransform(it.width, it.height)
            } else {
                it.surfaceTextureListener = this
            }
        }
    }

    override fun onDestroy() {
        mMyCameraManager?.close()
        mMyCameraManager = null
        super.onDestroy()
    }

    override fun onClick(p0: View?) {
    }

    override fun onCheckedChanged(p0: CompoundButton?, p1: Boolean) {
        when (p0?.id) {
            R.id.process_switch -> {
                if (p1) {
                    view?.findViewById<View>(R.id.original_image)?.visibility = View.INVISIBLE
                    view?.findViewById<View>(R.id.processed_image)?.visibility = View.VISIBLE
                } else {
                    view?.findViewById<View>(R.id.original_image)?.visibility = View.VISIBLE
                    view?.findViewById<View>(R.id.processed_image)?.visibility = View.INVISIBLE
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            PreviewFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        configureTransform(width, height)
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        configureTransform(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        mMyCameraManager?.setPreview(null, Size(0,0))
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
    }
}