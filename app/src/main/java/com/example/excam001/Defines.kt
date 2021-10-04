package com.example.excam001

import android.Manifest
import android.util.SparseIntArray
import android.view.Surface

class Defines {
    companion object {
        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        val ORIENTATIONS = object : SparseIntArray() {
            init {
                append(Surface.ROTATION_0, 0)
                append(Surface.ROTATION_90, 90)
                append(Surface.ROTATION_180, 180)
                append(Surface.ROTATION_270, 270)
            }
        }

        /**
         * Request code for camera permissions.
         */
        const val REQUEST_CAMERA_PERMISSIONS = 1

        /**
         * Permissions required to take a picture.
         */
        val CAMERA_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        /**
         * Max preview width that is guaranteed by Camera2 API
         */
        const val MAX_PREVIEW_WIDTH = 1920

        /**
         * Max preview height that is guaranteed by Camera2 API
         */
        const val MAX_PREVIEW_HEIGHT = 1080

        const val YUV_QUEUE_SIZE = 8
    }
}