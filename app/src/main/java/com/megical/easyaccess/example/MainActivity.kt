package com.megical.easyaccess.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.megical.easyaccess.example.ui.main.ExampleFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, ExampleFragment.newInstance())
                .commitNow()
        }
    }
}