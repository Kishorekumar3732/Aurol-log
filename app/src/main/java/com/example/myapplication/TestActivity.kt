package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
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
            checkHeadphones()
        }

        findViewById<Button>(R.id.speechTestButton).setOnClickListener {
            startActivity(Intent(this, SpeechTestActivity::class.java))
        }
    }

    private fun checkHeadphones() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val isHeadphonesConnected = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }

        if (isHeadphonesConnected) {
            testStatusTextView.text = getString(R.string.headphones_connected)
            checkEnvironmentNoise()
        } else {
            testStatusTextView.text = getString(R.string.connect_headphones)
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
                testStatusTextView.text = getString(R.string.environment_too_noisy)
                handler.postDelayed({ checkEnvironmentNoise() }, 3000)
            } else {
                testStatusTextView.text = getString(R.string.environment_acceptable)
                startActivity(Intent(this, PureToneTestActivity::class.java))
            }
        }, 3000)
    }
}
