package com.signapp

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate and set the common base layout
        setContentView(R.layout.activity_base)

        // Set up the Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

    override fun setContentView(layoutResID: Int) {
        val baseLayout = layoutInflater.inflate(R.layout.activity_base, null)
        val contentLayout = layoutInflater.inflate(layoutResID, null)
        baseLayout.findViewById<FrameLayout>(R.id.content_frame).addView(contentLayout)
        super.setContentView(baseLayout)
    }
}
