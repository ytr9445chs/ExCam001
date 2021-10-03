package com.example.excam001.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.excam001.R
import com.example.excam001.fragment.CaptureFragment
import com.example.excam001.fragment.PreviewFragment


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        savedInstanceState ?: run {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, PreviewFragment.newInstance())
                .commit()
        }
    }

    fun showCaptureFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, CaptureFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }
}