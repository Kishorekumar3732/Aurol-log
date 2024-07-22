package com.example.myapplication

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private var audioTrack: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var isLeftEarTest = true
    private var isRightEarTest = false
    private val frequencies = listOf(125, 250, 500, 1000, 2000, 4000, 8000)
    private var currentFrequencyIndex = 0
    private var currentDb = -10 // Start at -10 dB
    private val maxDb = 120
    private lateinit var resultTextView: TextView
    private lateinit var chart: LineChart
    private lateinit var scrollView: ScrollView
    private val leftEarResults = mutableMapOf<Int, Int>()
    private val rightEarResults = mutableMapOf<Int, Int>()
    private var playToneRunnable: Runnable? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startTestButton: Button = findViewById(R.id.startTestButton)
        val heardButton: Button = findViewById(R.id.heardButton)
        resultTextView = findViewById(R.id.resultTextView)
        chart = findViewById(R.id.chart)
        scrollView = findViewById(R.id.scrollView)

        setupChart()

        startTestButton.setOnClickListener {
            if (!isPlaying) {
                isPlaying = true
                currentFrequencyIndex = 0 // Reset frequency index
                currentDb = -10 // Reset dB level
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
                val frequency = frequencies[currentFrequencyIndex]
                if (isLeftEarTest) {
                    leftEarResults[frequency] = currentDb
                    Log.d("MainActivity", "Left ear frequency $frequency, dB $currentDb")
                } else if (isRightEarTest) {
                    rightEarResults[frequency] = currentDb
                    Log.d("MainActivity", "Right ear frequency $frequency, dB $currentDb")
                }

                // Stop the sound
                stopTone()

                // Move to the next frequency or ear test
                if (currentFrequencyIndex < frequencies.size - 1) {
                    currentFrequencyIndex++
                    currentDb = -10 // Reset dB for the next frequency
                } else {
                    if (isRightEarTest) {
                        // If completed both ears, show results
                        resultTextView.append("Right ear test completed. Test completed.\n")
                        printResults()
                        plotResults()
                        isPlaying = false
                        return@setOnClickListener
                    } else {
                        // Print left ear results and move to next ear
                        resultTextView.append("Left ear test completed. Starting right ear test...\n")
                        printResults()
                        isLeftEarTest = false
                        isRightEarTest = true
                        currentFrequencyIndex = 0
                        currentDb = -10 // Reset dB for the next ear
                    }
                }

                // Start testing the next frequency or ear
                startEarTest()
            }
        }
    }

    private fun startEarTest() {
        val frequency = frequencies[currentFrequencyIndex]
        playTone(frequency, isLeftEar = isLeftEarTest)
    }

    private fun playTone(frequency: Int, isLeftEar: Boolean) {
        stopTone()

        val sampleRate = 44100
        val duration = 15 // seconds, change as needed
        val numSamples = duration * sampleRate
        val sample = DoubleArray(numSamples)
        val generatedSnd = ByteArray(2 * numSamples)

        for (i in sample.indices) {
            sample[i] = sin(2 * PI * i / (sampleRate / frequency))
        }

        // Apply volume ramp
        var idx = 0
        for (i in sample.indices) {
            val ramp = i.toFloat() / numSamples // Linear ramp from 0 to 1
            val volume = ramp * 32767 * 10.0.pow(currentDb / 20.0)
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

            // Increase volume gradually
            playToneRunnable = object : Runnable {
                override fun run() {
                    if (currentDb < maxDb) {
                        currentDb += 10 // Increase dB level
                        val volume = 10.0.pow(currentDb / 20.0).toFloat()
                        audioTrack?.setStereoVolume(
                            if (isLeftEar) volume else 0.0f,
                            if (isLeftEar) 0.0f else volume
                        )
                        handler.postDelayed(this, 1000) // Schedule next volume increase
                    }
                }
            }
            handler.post(playToneRunnable!!)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing AudioTrack", e)
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
            Log.e("MainActivity", "Error stopping AudioTrack", e)
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
        leftEarDataSet.color = resources.getColor(R.color.colorLeftEar, null)
        leftEarDataSet.setCircleColor(resources.getColor(R.color.colorLeftEar, null))
        leftEarDataSet.lineWidth = 2f
        leftEarDataSet.circleRadius = 4f

        val rightEarDataSet = LineDataSet(rightEarEntries, "Right Ear")
        rightEarDataSet.color = resources.getColor(R.color.colorRightEar, null)
        rightEarDataSet.setCircleColor(resources.getColor(R.color.colorRightEar, null))
        rightEarDataSet.lineWidth = 2f
        rightEarDataSet.circleRadius = 4f

        // Set data and refresh chart
        val lineData = LineData(leftEarDataSet, rightEarDataSet)
        chart.data = lineData
        chart.invalidate() // Refresh chart view
    }

    private fun printResults() {
        resultTextView.append("\nFrequency\tLeft Ear\tRight Ear\n")
        frequencies.forEach { frequency ->
            val leftDb = leftEarResults[frequency] ?: "-"
            val rightDb = rightEarResults[frequency] ?: "-"
            resultTextView.append("$frequency Hz\t$leftDb dB\t$rightDb dB\n")
        }
        scrollView.scrollTo(0, resultTextView.bottom)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTone()
    }

    inner class FrequencyValueFormatter(private val frequencies: List<Int>) : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return frequencies.getOrNull(value.toInt())?.toString() ?: value.toString()
        }
    }
}
