package com.example.myapplication

import com.github.mikephil.charting.formatter.ValueFormatter

class FrequencyValueFormatter(private val frequencies: Array<Int>) : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        val index = value.toInt()
        return if (index in frequencies.indices) {
            frequencies[index].toString()
        } else {
            value.toString()
        }
    }
}
