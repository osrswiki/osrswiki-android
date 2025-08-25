package com.omiyawaki.osrswiki.test

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.search.SearchFragment

class ColorTestLauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Launch main activity and automatically navigate to search
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("OPEN_SEARCH", true)
        }
        startActivity(intent)
        
        // Close this launcher activity
        finish()
    }
}