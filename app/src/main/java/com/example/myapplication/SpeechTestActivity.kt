package com.example.myapplication

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SpeechTestActivity : AppCompatActivity() {

    private lateinit var letterInput: EditText
    private lateinit var submitButton: Button
    private lateinit var resultTextView: TextView
    private lateinit var handler: Handler

    private val letters = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z")
    private val correctAnswers = mutableListOf<String>()
    private var currentRound = 0
    private var correctCount = 0
    private var wrongCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speech_test)

        letterInput = findViewById(R.id.letterInput)
        submitButton = findViewById(R.id.submitButton)
        resultTextView = findViewById(R.id.resultTextView)
        handler = Handler(Looper.getMainLooper())

        startTest()

        submitButton.setOnClickListener {
            checkAnswer()
        }
    }

    private fun startTest() {
        if (currentRound < 6) {
            playLettersWithNoise()
        } else {
            showResults()
        }
    }

    private fun playLettersWithNoise() {
        // Select 3 random letters
        correctAnswers.clear()
        repeat(3) {
            correctAnswers.add(letters.random())
        }

        // Play the letters with noise in the background
        val mediaPlayer = MediaPlayer.create(this, R.raw.background_noise) // Add your noise file to res/raw
        mediaPlayer.setOnCompletionListener {
            correctAnswers.forEach { letter ->
                playLetterSound(letter)
            }
        }
        mediaPlayer.start()
    }

    private fun playLetterSound(letter: String) {
        val resId = resources.getIdentifier("letter_$letter", "raw", packageName)
        if (resId != 0) {
            val mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer.start()
        } else {
            Log.e("SpeechTestActivity", "Sound file for letter $letter not found.")
        }
    }

    private fun checkAnswer() {
        val userAnswer = letterInput.text.toString().toUpperCase()
        if (userAnswer == correctAnswers.joinToString("")) {
            correctCount++
        } else {
            wrongCount++
        }
        currentRound++
        letterInput.text.clear()
        startTest()
    }

    private fun showResults() {
        resultTextView.text = "Test completed\nCorrect: $correctCount\nWrong: $wrongCount"
    }
}
