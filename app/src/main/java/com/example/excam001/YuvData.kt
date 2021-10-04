package com.example.excam001

import java.nio.ByteBuffer

class YuvData {
    var width: Int = 0
    var height: Int = 0
    var timestamp: Long = 0
    private var mBuffer: ByteArray? = null

    fun setYuv(data: ByteArray) {
        mBuffer = data
    }

    fun getYuv(): ByteArray? {
        return mBuffer
    }
}