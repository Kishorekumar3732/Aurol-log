package com.example.myapplication

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
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
    private var isPlaying = false

    private val leftEarResults = mutableMapOf<Int, Int>()
    private val rightEarResults = mutableMapOf<Int, Int>()

    private lateinit var resultTextView: TextView
    private lateinit var frequencyTextView: TextView
    private lateinit var dbLevelTextView: TextView
    private lateinit var chart: LineChart

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

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val startTestButton: Button = findViewById(R.id.startTestButton)
        val heardButton: Button = findViewById(R.id.heardButton)
        val cannotHearButton: Button = findViewById(R.id.cannotHearButton)
        resultTextView = findViewById(R.id.resultTextView)
        frequencyTextView = findViewById(R.id.frequencyTextView)
        dbLevelTextView = findViewById(R.id.dbLevelTextView)
        chart = findViewById(R.id.chart)

        setupChart()

        startTestButton.setOnClickListener {
            if (!isPlaying) {
                if (!areHeadphonesConnected) {
                    Toast.makeText(this, "Please connect headphones to start the test", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startTest()
            }
        }

        heardButton.setOnClickListener {
            if (isPlaying) {
                handleHeardButton(true)
            }
        }

        cannotHearButton.setOnClickListener {
            if (isPlaying) {
                handleHeardButton(false)
            }
        }

        headphoneStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = intent?.getIntExtra("state", -1) ?: return
                when (state) {
                    0 -> {
                        areHeadphonesConnected = false
                        Toast.makeText(this@PureToneTestActivity, "Headphones disconnected", Toast.LENGTH_SHORT).show()
                        if (isPlaying) stopTone()
                    }
                    1 -> {
                        areHeadphonesConnected = true
                        Toast.makeText(this@PureToneTestActivity, "Headphones connected", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        registerReceiver(headphoneStateReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        areHeadphonesConnected = areHeadphonesConnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(headphoneStateReceiver)
        stopTone()
    }

    private fun areHeadphonesConnected(): Boolean {
        val wiredHeadsetConnected = audioManager.isWiredHeadsetOn
        val bluetoothHeadsetConnected = audioManager.isBluetoothA2dpOn
        return wiredHeadsetConnected || bluetoothHeadsetConnected
    }

    private fun startTest() {
        isPlaying = true
        currentFrequencyIndex = 0
        currentDb = 50
        heardDb = null
        isLeftEarTest = true
        leftEarResults.clear()
        rightEarResults.clear()
        resultTextView.text = "Starting Left Ear Test...\n"
        chart.data = null
        chart.invalidate()
        playNextTone()
    }

    private fun playNextTone() {
        if (currentFrequencyIndex >= frequencies.size) {
            if (isLeftEarTest) {
                isLeftEarTest = false
                currentFrequencyIndex = 0
                currentDb = -1 // Reset dB level for the next ear
                resultTextView.append("\nStarting Right Ear Test...\n")
            } else {
                finishTest()
                return
            }
        }

        if (currentDb == -1) {
            currentDb = 40 // Initial starting dB level for each frequency
        }

        val frequency = frequencies[currentFrequencyIndex]
        playTone(frequency)
        updateDisplay(frequency, currentDb)
        resultTextView.append("Listening at $frequency Hz and $currentDb dB\n")
        // Removed auto-switch logic to wait for user input

        playToneRunnable = Runnable {
            // No timeout; waiting for user interaction
        }
        handler.postDelayed(playToneRunnable!!, 3000)
    }


    private fun playTone(frequency: Int) {
        stopTone()
        val sampleRate = 44100
        val duration = 1
        val numSamples = duration * sampleRate
        val generatedSnd = ShortArray(numSamples * 2) // Stereo output (Left + Right)

        for (i in 0 until numSamples) {
            val time = i / sampleRate.toDouble()
            val angle = 2.0 * PI * frequency * time
            val sound = (sin(angle) * (2.0.pow(currentDb / 20.0) * Short.MAX_VALUE / 2)).toInt().toShort()

            if (isLeftEarTest) {
                // Left ear test: Sound only in the left channel
                generatedSnd[i * 2] = sound
                generatedSnd[i * 2 + 1] = 0
            } else {
                // Right ear test: Sound only in the right channel
                generatedSnd[i * 2] = 0
                generatedSnd[i * 2 + 1] = sound
            }
        }

        try {
            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO) // Stereo output
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
                numSamples * 2 * 2, // Stereo output (Left + Right)
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            ).apply {
                write(generatedSnd, 0, generatedSnd.size)
                play()
            }
        } catch (e: Exception) {
            Log.e("PureToneTestActivity", "Error playing tone: ${e.message}")
        }
    }

    private fun stopTone() {
        try {
            audioTrack?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("PureToneTestActivity", "Error stopping tone: ${e.message}")
        } finally {
            audioTrack = null
            playToneRunnable?.let { handler.removeCallbacks(it) }
        }
    }

    private fun handleHeardButton(heard: Boolean) {
        if (heard) {
            if (currentDb <= 30) {
                // Mark as heard and move to the next frequency
                heardDb = currentDb
                if (isLeftEarTest) {
                    leftEarResults[frequencies[currentFrequencyIndex]] = heardDb ?: 0
                } else {
                    rightEarResults[frequencies[currentFrequencyIndex]] = heardDb ?: 0
                }
                currentFrequencyIndex++
                currentDb = -1 // Reset dB for the next frequency
            } else {
                // Reduce the dB and try again
                currentDb -= 10
            }
        } else {
            // Increase the dB and try again
            currentDb += 5
        }

        playNextTone() // Only proceed to next tone when user clicks heard or not heard
    }



    private fun handleToneTimeout() {
        if (isLeftEarTest) {
            leftEarResults[frequencies[currentFrequencyIndex]] = heardDb ?: 0
        } else {
            rightEarResults[frequencies[currentFrequencyIndex]] = heardDb ?: 0
        }

        currentFrequencyIndex++
        currentDb = -1 // Reset dB for the next frequency
        playNextTone()
    }

    private fun finishTest() {
        isPlaying = false
        stopTone()
        resultTextView.append("\nTest Completed.\n")
        resultTextView.append("Left Ear Results:\n${leftEarResults.entries.joinToString { "${it.key}Hz: ${it.value}dB" }}\n")
        resultTextView.append("Right Ear Results:\n${rightEarResults.entries.joinToString { "${it.key}Hz: ${it.value}dB" }}\n")
        updateChart() // Update the chart with test results
    }

    private fun setupChart() {
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            setPinchZoom(false)
            setBackgroundColor(Color.WHITE)
            legend.isEnabled = true
        }

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setLabelCount(frequencies.size, true)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return frequencies.getOrNull(value.toInt())?.toString() ?: value.toString()
            }
        }

        val yAxis = chart.axisLeft
        yAxis.granularity = 10f
        yAxis.axisMinimum = 0f
        yAxis.axisMaximum = maxDb.toFloat()
        yAxis.setLabelCount((maxDb / 10) + 1, true)
        yAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.toInt()} dB"
            }
        }
        chart.axisRight.isEnabled = false

        setChartBackgroundColors()
    }

    private fun setChartBackgroundColors() {
        val colors = listOf(
            Pair(Color.parseColor("#C8E6C9"), 20f), // Normal hearing (green)
            Pair(Color.parseColor("#FFEB3B"), 40f), // Mild hearing loss (yellow)
            Pair(Color.parseColor("#FF9800"), 60f), // Moderate hearing loss (orange)
            Pair(Color.parseColor("#F44336"), 80f), // Severe hearing loss (red)
            Pair(Color.parseColor("#B71C1C"), maxDb.toFloat()) // Profound hearing loss (dark red)
        )

        // Create a List of Entries for the background colors
        val entries = mutableListOf<Entry>()
        for ((color, limit) in colors) {
            entries.add(Entry(0f, limit))
            entries.add(Entry(frequencies.size.toFloat(), limit))
        }

        // Create a LineDataSet for the background color bands
        val dataSet = LineDataSet(entries, "Hearing Loss Categories").apply {
            // No need to set color here as we use transparent lines
            setDrawValues(false)
            setDrawCircles(false)
            color = Color.TRANSPARENT // This hides the line itself
            lineWidth = 0f // No line width to avoid drawing lines
            mode = LineDataSet.Mode.STEPPED // This creates stepped bands instead of continuous lines
        }

        // Add the background color bands as an additional dataset
        val lineData = chart.data ?: LineData()
        lineData.addDataSet(dataSet)
        chart.data = lineData

        chart.invalidate() // Refresh the chart
    }


    private fun updateChart() {
        val leftEarEntries = leftEarResults.map { Entry(frequencies.indexOf(it.key).toFloat(), it.value.toFloat()) }
        val rightEarEntries = rightEarResults.map { Entry(frequencies.indexOf(it.key).toFloat(), it.value.toFloat()) }

        val leftEarDataSet = LineDataSet(leftEarEntries, "Left Ear").apply {
            color = Color.RED
            setCircleColor(Color.RED)
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            mode = LineDataSet.Mode.LINEAR
            setDrawValues(false)
        }

        val rightEarDataSet = LineDataSet(rightEarEntries, "Right Ear").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            mode = LineDataSet.Mode.LINEAR
            setDrawValues(false)
        }

        val data = LineData(leftEarDataSet, rightEarDataSet)
        chart.data = data
        chart.invalidate()
    }

    private fun updateDisplay(frequency: Int, db: Int) {
        frequencyTextView.text = "Frequency: $frequency Hz"
        dbLevelTextView.text = "Decibel Level: $db dB"
    }
}
