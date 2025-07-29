package com.ipsat.ipsat_tv.components.resendTimer

import android.os.CountDownTimer

class ResendTimerUtil(
    private val durationMillis: Long,
    private val intervalMillis: Long,
    private val oonTick: (Long) -> Unit,
    private val oonFinish: () -> Unit
) {
    private var timer: CountDownTimer? = null
    private var timeLeft: Long = 0

    fun start() {
        timer?.cancel()
        timeLeft = durationMillis
        timer = object : CountDownTimer(timeLeft, intervalMillis) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = millisUntilFinished
                oonTick(millisUntilFinished)
            }

            override fun onFinish() {
                oonFinish()
            }
        }.start()
    }

    fun stop() {
        timer?.cancel()
        timer = null
    }
}
