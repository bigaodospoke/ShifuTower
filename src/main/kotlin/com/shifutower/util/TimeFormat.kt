package com.shifutower.util

object TimeFormat {
    /** 754_300 ms -> "12:34.3" ; acima de 1h -> "1:02:34.3" */
    fun duration(ms: Long): String {
        if (ms < 0) return "--:--"
        val totalSeconds = ms / 1000
        val tenths = (ms % 1000) / 100
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d.%d".format(hours, minutes, seconds, tenths)
        } else {
            "%02d:%02d.%d".format(minutes, seconds, tenths)
        }
    }

    /** Contagem regressiva sem décimos: "05:12:33" */
    fun countdown(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0) + 999) / 1000
        return "%02d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
    }
}
