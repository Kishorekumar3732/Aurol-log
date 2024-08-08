package com.example.myapplication

import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.utils.ColorTemplate

class SpeechTestActivity : AppCompatActivity() {

    private lateinit var letterInput: EditText
    private lateinit var submitButton: Button
    private lateinit var resultTextView: TextView
    private lateinit var barChart: BarChart
    private lateinit var handler: Handler

    private val letters = ('a'..'z').map { it.toString() }
    private var correctAnswers = ""
    private var correctCount = 0
    private var wrongCount = 0
    private var mediaPlayer: MediaPlayer? = null
    private var noiseMediaPlayer: MediaPlayer? = null
    private val userResponses = mutableListOf<Pair<Float, Float>>()  // Pair<SNR, Intelligibility>
    private var currentSNR = -22f  // Starting SNR value

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speech_test)

        letterInput = findViewById(R.id.letterInput)
        submitButton = findViewById(R.id.submitButton)
        resultTextView = findViewById(R.id.resultTextView)
        barChart = findViewById(R.id.barChart)
        handler = Handler(Looper.getMainLooper())

        setupBarChart()
        startBackgroundNoise()
        startRandomLetterPlayback()

        submitButton.setOnClickListener {
            checkAnswer()
        }
    }

    private fun setupBarChart() {
        // Customize X-axis
        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.labelRotationAngle = 90f
        xAxis.textColor = Color.BLACK
        xAxis.axisLineColor = Color.BLACK

        // Customize Y-axis (left)
        val leftAxis = barChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.textColor = Color.BLACK
        leftAxis.axisLineColor = Color.BLACK

        // Customize Y-axis (right)
        val rightAxis = barChart.axisRight
        rightAxis.setDrawGridLines(false)
        rightAxis.isEnabled = false

        // Customize the bar chart appearance
        barChart.description.isEnabled = false
        barChart.setDrawValueAboveBar(true)
        barChart.setDrawGridBackground(false)
        barChart.setFitBars(true)
    }

    private fun startBackgroundNoise() {
        noiseMediaPlayer = MediaPlayer.create(this, R.raw.background_noise)
        noiseMediaPlayer?.isLooping = true
        noiseMediaPlayer?.start()
    }

    private fun startRandomLetterPlayback() {
        handler.post(object : Runnable {
            override fun run() {
                playRandomLetter()
                handler.postDelayed(this, 3000) // Play a letter every 3 seconds
            }
        })
    }

    private fun playRandomLetter() {
        correctAnswers = letters.random()
        val resId = resources.getIdentifier(correctAnswers, "raw", packageName)

        if (resId != 0) {
            mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer?.start()
        } else {
            Log.e("SpeechTestActivity", "Sound file for $correctAnswers not found.")
        }
    }

    private fun checkAnswer() {
        val userAnswer = letterInput.text.toString().toLowerCase()

        if (userAnswer == correctAnswers) {
            correctCount++
            onResponse(true)
        } else {
            wrongCount++
            onResponse(false)
        }

        updateResults()
        letterInput.text.clear()
    }

    private fun onResponse(correct: Boolean) {
        val intelligibility = if (correct) 100f else 0f
        userResponses.add(Pair(currentSNR, intelligibility))

        // Update SNR value based on your test logic
        currentSNR += 2f  // Example increment; adjust based on your test setup
    }

    private fun updateResults() {
        resultTextView.text = "Correct: $correctCount\nWrong: $wrongCount"
        updateGraph()
    }

    private fun updateGraph() {
        val entries = userResponses.mapIndexed { index, response ->
            BarEntry(index.toFloat(), response.second)
        }

        val barDataSet = BarDataSet(entries, "SNR[dB] vs Intelligibility[%]")
        barDataSet.colors = ColorTemplate.COLORFUL_COLORS.toList()

        val barData = BarData(barDataSet)
        barChart.data = barData
        barChart.invalidate() // Refresh the chart
    }

    override fun onBackPressed() {
        super.onBackPressed()
        stopMediaPlayers()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMediaPlayers()
    }

    private fun stopMediaPlayers() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        noiseMediaPlayer?.stop()
        noiseMediaPlayer?.release()
        noiseMediaPlayer = null
    }
}
