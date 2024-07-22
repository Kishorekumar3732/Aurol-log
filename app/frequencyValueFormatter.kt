package com.example.myapplication
import com.github.mikephil.charting.formatter.ValueFormatter
class frequencyValueFormatter : ValueFormatter() {
    private val frequencies = listOf("125Hz", "250Hz", "500Hz", "1000Hz", "2000Hz", "4000Hz", "8000Hz")

    override fun getFormattedValue(value: Float): String {
        return if (value.toInt() in frequencies.indices) {
            frequencies[value.toInt()]
        } else {
            value.toString()
        }
    }
}

