package com.example.myapplication

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class PureToneTestActivity : AppCompatActivity() {

    private val frequencies = arrayOf(250, 500, 1000, 2000, 4000, 8000)
    private val minDb = 0
    private val maxDb = 120
    private var currentFrequencyIndex = 0
    private var currentDb = 50
    private var heardDb: Int? = null
    private var isLeftEarTest = true
    private var isRightEarTest = false
    private var isPlaying = false

    private val leftEarResults = mutableMapOf<Int, Int>()
    private val rightEarResults = mutableMapOf<Int, Int>()

    private lateinit var resultTextView: TextView
    private lateinit var chart: LineChart
    private lateinit var scrollView: ScrollView

    private var audioTrack: AudioTrack? = null
    private val handler = Handler()
    private var playToneRunnable: Runnable? = null

    private lateinit var audioManager: AudioManager
    private lateinit var headphoneStateReceiver: BroadcastReceiver
    private var areHeadphonesConnected = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pure_tone_test)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val startTestButton: Button = findViewById(R.id.startTestButton)
        val heardButton: Button = findViewById(R.id.heardButton)
        val cannotHearButton: Button = findViewById(R.id.cannotHearButton)
        resultTextView = findViewById(R.id.resultTextView)
        chart = findViewById(R.id.chart)
        scrollView = findViewById(R.id.scrollView)

        setupChart()

        startTestButton.setOnClickListener {
            Log.d("PureToneTestActivity", "Start Test Button Clicked")
            startTest()
        }

        heardButton.setOnClickListener {
            Log.d("PureToneTestActivity", "Heard Button Clicked")
            if (isPlaying) {
                handleHeardButton(true)
            }
        }

        cannotHearButton.setOnClickListener {
            Log.d("PureToneTestActivity", "Cannot Hear Button Clicked")
            if (isPlaying) {
                handleHeardButton(false)
            }
        }

        // Register receiver to listen for headphone connection state
        headphoneStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = intent?.getIntExtra("state", -1) ?: return
                Log.d("PureToneTestActivity", "Headphone State Changed: $state")
                when (state) {
                    0 -> {
                        // Headphones disconnected
                        areHeadphonesConnected = false
                        Toast.makeText(this@PureToneTestActivity, "Headphones disconnected", Toast.LENGTH_SHORT).show()
                        if (isPlaying) stopTone()
                    }
                    1 -> {
                        // Headphones connected
                        areHeadphonesConnected = true
                        Toast.makeText(this@PureToneTestActivity, "Headphones connected", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        registerReceiver(headphoneStateReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        // Initial check for headphone status
        areHeadphonesConnected = areHeadphonesConnected()
        Log.d("PureToneTestActivity", "Initial Headphone Status: $areHeadphonesConnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(headphoneStateReceiver)
        stopTone()
    }

    private fun areHeadphonesConnected(): Boolean {
        val wiredHeadsetConnected = audioManager.isWiredHeadsetOn
        val bluetoothHeadsetConnected = audioManager.isBluetoothA2dpOn
        Log.d("PureToneTestActivity", "Wired Headset Connected: $wiredHeadsetConnected")
        Log.d("PureToneTestActivity", "Bluetooth Headset Connected: $bluetoothHeadsetConnected")
        return wiredHeadsetConnected || bluetoothHeadsetConnected
    }

    private fun startTest() {
        isPlaying = true
        currentFrequencyIndex = 0 // Reset frequency index
        currentDb = 50 // Reset dB level
        heardDb = null
        isLeftEarTest = true
        isRightEarTest = false
        leftEarResults.clear()
        rightEarResults.clear()
        resultTextView.text = "Starting Left Ear Test...\n" // Clear result text view
        chart.data = null // Clear chart data
        chart.invalidate() // Refresh chart
        playNextTone()
    }

    private fun playNextTone() {
        if (currentFrequencyIndex >= frequencies.size) {
            finishTest()
            return
        }

        val frequency = frequencies[currentFrequencyIndex]
        playTone(frequency)
        resultTextView.text = "Listening at $frequency Hz"
    }

    private fun playTone(frequency: Int) {
        stopTone()
        val sampleRate = 44100
        val duration = 1 // Duration in seconds
        val numSamples = duration * sampleRate
        val generatedSnd = ShortArray(numSamples)

        for (i in generatedSnd.indices) {
            val time = i / sampleRate.toDouble()
            val angle = 2.0 * PI * frequency * time
            generatedSnd[i] =
                (sin(angle) * (2.0.pow(currentDb / 20.0) * Short.MAX_VALUE / 2)).toInt().toShort()
        }

        try {
            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
                numSamples * 2, // Buffer size in bytes
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            ).apply {
                write(generatedSnd, 0, generatedSnd.size) // Write data to the audio track
                play() // Play the audio track
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopTone() {
        audioTrack?.apply {
            stop()
            release()
        }
        audioTrack = null
    }

    private fun handleHeardButton(heard: Boolean) {
        if (heard) {
            heardDb = currentDb
        } else {
            heardDb = null
        }

        if (isLeftEarTest) {
            leftEarResults[frequencies[currentFrequencyIndex]] = heardDb ?: 0
            if (currentFrequencyIndex == frequencies.lastIndex) {
                isLeftEarTest = false
                isRightEarTest = true
                resultTextView.text = "Starting Right Ear Test...\n"
                currentFrequencyIndex = 0
                playNextTone()
            } else {
                currentFrequencyIndex++
                playNextTone()
            }
        } else if (isRightEarTest) {
            rightEarResults[frequencies[currentFrequencyIndex]] = heardDb ?: 0
            if (currentFrequencyIndex == frequencies.lastIndex) {
                finishTest()
            } else {
                currentFrequencyIndex++
                playNextTone()
            }
        }
    }

    private fun finishTest() {
        isPlaying = false
        resultTextView.text = "Test Completed"
        updateChart()
    }

    private fun setupChart() {
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.labelRotationAngle = 45f
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.axisMaximum = 120f
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
    }

    private fun updateChart() {
        val leftEntries = leftEarResults.entries.map {
            Entry(it.key.toFloat(), it.value.toFloat())
        }
        val rightEntries = rightEarResults.entries.map {
            Entry(it.key.toFloat(), it.value.toFloat())
        }

        val leftDataSet = LineDataSet(leftEntries, "Left Ear").apply {
            color = resources.getColor(R.color.colorLeftEar)
            valueTextColor = resources.getColor(R.color.colorLeftEar)
        }
        val rightDataSet = LineDataSet(rightEntries, "Right Ear").apply {
            color = resources.getColor(R.color.colorRightEar)
            valueTextColor = resources.getColor(R.color.colorRightEar)
        }

        val lineData = LineData(leftDataSet, rightDataSet)
        chart.data = lineData
        chart.invalidate()
    }
}
