package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TestActivity : AppCompatActivity() {

    private lateinit var testStatusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        testStatusTextView = findViewById(R.id.testStatusTextView)

        findViewById<Button>(R.id.pureToneTestButton).setOnClickListener {
            // Navigate to PureToneTestActivity directly
            startActivity(Intent(this, PureToneTestActivity::class.java))
        }

        findViewById<Button>(R.id.speechTestButton).setOnClickListener {
            startActivity(Intent(this, SpeechTestActivity::class.java))
        }
    }
}
