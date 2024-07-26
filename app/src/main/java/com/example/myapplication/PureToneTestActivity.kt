package com.example.myapplication

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.pow

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

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pure_tone_test)

        val startTestButton: Button = findViewById(R.id.startTestButton)
        val heardButton: Button = findViewById(R.id.heardButton)
        val cannotHearButton: Button = findViewById(R.id.cannotHearButton)
        resultTextView = findViewById(R.id.resultTextView)
        chart = findViewById(R.id.chart)
        scrollView = findViewById(R.id.scrollView)

        setupChart()

        startTestButton.setOnClickListener {
            if (!isPlaying) {
                isPlaying = true
                currentFrequencyIndex = 0 // Reset frequency index
                currentDb = 50 // Reset dB level
                isLeftEarTest = true
                isRightEarTest = false
                leftEarResults.clear()
                rightEarResults.clear()
                resultTextView.text = "Starting Left Ear Test...\n" // Clear result text view
                chart.data = null // Clear chart data
                chart.invalidate() // Refresh chart view
                scrollView.scrollTo(0, 0) // Scroll to top
                startEarTest()
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
    }

    private fun handleHeardButton(heard: Boolean) {
        val frequency = frequencies[currentFrequencyIndex]
        if (heard) {
            if (heardDb == null) {
                heardDb = currentDb
                currentDb -= 10 // Decrease dB level for finer tuning
            } else {
                val previousDb = heardDb!!
                if (currentDb < previousDb) {
                    // User can hear at lower dB, decrease further
                    heardDb = currentDb
                    currentDb -= 10
                } else {
                    // User can't hear at lower dB, increase slightly
                    currentDb = (previousDb + currentDb) / 2
                }
            }
        } else {
            currentDb += 10 // Increase dB level since user cannot hear
        }

        if (currentDb < minDb) {
            currentDb = minDb
        }

        if (heardDb != null && currentDb >= heardDb!!) {
            // Finalize the result for this frequency
            if (isLeftEarTest) {
                leftEarResults[frequency] = heardDb!!
                Log.d("PureToneTestActivity", "Left ear frequency $frequency, dB $heardDb")
            } else if (isRightEarTest) {
                rightEarResults[frequency] = heardDb!!
                Log.d("PureToneTestActivity", "Right ear frequency $frequency, dB $heardDb")
            }

            heardDb = null
            currentDb = 50 // Reset dB level for the next frequency
            moveToNextFrequencyOrEar()
        } else {
            startEarTest()
        }
    }

    private fun moveToNextFrequencyOrEar() {
        if (currentFrequencyIndex < frequencies.size - 1) {
            currentFrequencyIndex++
        } else {
            if (isRightEarTest) {
                // If completed both ears, show results
                resultTextView.append("Right ear test completed. Test completed.\n")
                printResults()
                plotResults()
                isPlaying = false
                return
            } else {
                // Print left ear results and move to next ear
                resultTextView.append("Left ear test completed. Starting right ear test...\n")
                printResults()
                isLeftEarTest = false
                isRightEarTest = true
                currentFrequencyIndex = 0
            }
        }
        startEarTest()
    }

    private fun startEarTest() {
        val frequency = frequencies[currentFrequencyIndex]
        playTone(frequency, isLeftEar = isLeftEarTest)
        resultTextView.append("Testing frequency: $frequency Hz at $currentDb dB\n")
    }

    private fun playTone(frequency: Int, isLeftEar: Boolean) {
        stopTone()

        val sampleRate = 44100
        val duration = 2 // seconds, change as needed
        val numSamples = duration * sampleRate
        val sample = DoubleArray(numSamples)
        val generatedSnd = ByteArray(2 * numSamples)

        for (i in sample.indices) {
            sample[i] = sin(2 * PI * i / (sampleRate / frequency))
        }

        var idx = 0
        for (i in sample.indices) {
            val volume = 32767 * 10.0.pow(currentDb / 20.0)
            val valInt = (sample[i] * volume).toInt()
            val valShort = valInt.toShort()
            generatedSnd[idx++] = (valShort.toInt() and 0x00ff).toByte()
            generatedSnd[idx++] = ((valShort.toInt() and 0xff00) ushr 8).toByte()
        }

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(generatedSnd.size)
                .build()

            audioTrack?.write(generatedSnd, 0, generatedSnd.size)
            audioTrack?.play()

            // Set volume for left or right ear test
            val leftVolume = if (isLeftEar) 1.0f else 0.0f
            val rightVolume = if (isLeftEar) 0.0f else 1.0f
            audioTrack?.setStereoVolume(leftVolume, rightVolume)
        } catch (e: Exception) {
            Log.e("PureToneTestActivity", "Error initializing AudioTrack", e)
        }
    }

    private fun stopTone() {
        playToneRunnable?.let {
            handler.removeCallbacks(it)
        }
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e("PureToneTestActivity", "Error stopping AudioTrack", e)
        }
        audioTrack = null
    }

    private fun setupChart() {
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            valueFormatter = FrequencyValueFormatter(frequencies)
        }

        chart.axisLeft.apply {
            axisMinimum = -10f
            axisMaximum = 120f
            granularity = 10f
        }

        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
    }

    private fun plotResults() {
        val leftEarEntries = mutableListOf<Entry>()
        val rightEarEntries = mutableListOf<Entry>()

        // Convert dB results to Entry for plotting
        leftEarResults.forEach { (frequency, db) ->
            leftEarEntries.add(Entry(frequency.toFloat(), db.toFloat()))
        }
        rightEarResults.forEach { (frequency, db) ->
            rightEarEntries.add(Entry(frequency.toFloat(), db.toFloat()))
        }

        // Create datasets for the chart
        val leftEarDataSet = LineDataSet(leftEarEntries, "Left Ear")
        val rightEarDataSet = LineDataSet(rightEarEntries, "Right Ear")

        // Customize datasets appearance
        leftEarDataSet.color = 0xff00ff00.toInt() // Green
        rightEarDataSet.color = 0xffff0000.toInt() // Red

        // Add datasets to chart data
        val data = LineData(leftEarDataSet, rightEarDataSet)
        chart.data = data
        chart.invalidate() // Refresh chart view
    }

    private fun printResults() {
        resultTextView.append(
            if (isLeftEarTest) "Left ear results:\n" else "Right ear results:\n"
        )
        val results = if (isLeftEarTest) leftEarResults else rightEarResults
        results.forEach { (frequency, db) ->
            resultTextView.append("Frequency: $frequency Hz, dB: $db\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTone()
    }
}
