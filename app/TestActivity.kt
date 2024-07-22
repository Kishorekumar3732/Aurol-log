package com.example.myapplication

import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TestActivity : AppCompatActivity() {

    private lateinit var testStatusTextView: TextView
    private lateinit var handler: Handler
    private lateinit var noiseRecorder: MediaRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        testStatusTextView = findViewById(R.id.testStatusTextView)
        handler = Handler(Looper.getMainLooper())

        findViewById<Button>(R.id.pureToneTestButton).setOnClickListener {
            startActivity(Intent(this, PureToneTestActivity::class.java))
        }

        findViewById<Button>(R.id.speechTestButton).setOnClickListener {
            startActivity(Intent(this, SpeechTestActivity::class.java))
        }

        checkHeadphones()
    }

    private fun checkHeadphones() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn) {
            testStatusTextView.text = "Headphones connected. Checking environment noise..."
            checkEnvironmentNoise()
        } else {
            testStatusTextView.text = "Please connect headphones."
            handler.postDelayed({ checkHeadphones() }, 3000)
        }
    }

    private fun checkEnvironmentNoise() {
        noiseRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile("/dev/null")
            prepare()
            start()
        }

        handler.postDelayed({
            val maxAmplitude = noiseRecorder.maxAmplitude
            noiseRecorder.stop()
            noiseRecorder.release()

            if (maxAmplitude > 2000) { // Adjust threshold as needed
                testStatusTextView.text = "Environment is too noisy. Please move to a quieter area."
                handler.postDelayed({ checkEnvironmentNoise() }, 3000)
            } else {
                testStatusTextView.text = "Environment noise is acceptable. You can start the test."
            }
        }, 3000)
    }
}
