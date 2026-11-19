package com.somnalooper.app.focus

import android.os.SystemClock
import com.somnalooper.app.audio.SoundEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class FocusPhase { WORK, BREAK, LONG_BREAK }

data class FocusPlan(val workMin: Int, val breakMin: Int, val longBreakMin: Int, val cyclesBeforeLong: Int = 4) {
    companion object {
        val CLASSIC = FocusPlan(25, 5, 15)
        val DEEP = FocusPlan(50, 10, 20)
        fun of(index: Int) = if (index == 1) DEEP else CLASSIC
    }
}

data class FocusState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val phase: FocusPhase = FocusPhase.WORK,
    val plan: FocusPlan = FocusPlan.CLASSIC,
    val remainingMs: Long = FocusPlan.CLASSIC.workMin * 60_000L,
    val totalMs: Long = FocusPlan.CLASSIC.workMin * 60_000L,
    val completedWork: Int = 0,
) {
    val progress: Float get() = if (totalMs <= 0) 0f else 1f - remainingMs.toFloat() / totalMs
}

/**
 * Pomodoro companion: alternates work and break phases and automatically switches the
 * soundscape (focus sounds while working, relaxing nature while resting) with a soft chime.
 */
class FocusController(
    private val engine: SoundEngine,
    private val scope: CoroutineScope,
    private val workMix: () -> Map<String, Float>,
    private val breakMix: () -> Map<String, Float>,
    private val phaseLabel: (FocusPhase) -> String,
) {
    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()
    private var job: Job? = null
    private var endAt = 0L

    fun setPlan(plan: FocusPlan) {
        if (_state.value.active) return
        _state.value = FocusState(plan = plan, remainingMs = plan.workMin * 60_000L, totalMs = plan.workMin * 60_000L)
    }

    fun start() {
        val s = _state.value
        if (s.active && !s.paused) return
        if (!s.active) {
            enterPhase(FocusPhase.WORK, s.plan, completed = 0, chime = false)
        } else {
            endAt = SystemClock.elapsedRealtime() + s.remainingMs
            _state.update { it.copy(paused = false) }
            engine.play()
        }
        runLoop()
    }

    fun pause() {
        if (!_state.value.active) return
        job?.cancel()
        _state.update { it.copy(paused = true, remainingMs = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)) }
        engine.pause()
    }

    fun skip() {
        if (!_state.value.active) return
        advance()
        if (!_state.value.paused) runLoop()
    }

    fun stop() {
        job?.cancel()
        val plan = _state.value.plan
        _state.value = FocusState(plan = plan, remainingMs = plan.workMin * 60_000L, totalMs = plan.workMin * 60_000L)
        engine.pause()
    }

    private fun runLoop() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val remaining = endAt - SystemClock.elapsedRealtime()
                if (remaining <= 0) advance() else _state.update { it.copy(remainingMs = remaining) }
                delay(500)
            }
        }
    }

    private fun advance() {
        val s = _state.value
        if (s.phase == FocusPhase.WORK) {
            val done = s.completedWork + 1
            val long = done % s.plan.cyclesBeforeLong == 0
            enterPhase(if (long) FocusPhase.LONG_BREAK else FocusPhase.BREAK, s.plan, done, chime = true)
        } else {
            enterPhase(FocusPhase.WORK, s.plan, s.completedWork, chime = true)
        }
    }

    private fun enterPhase(phase: FocusPhase, plan: FocusPlan, completed: Int, chime: Boolean) {
        val minutes = when (phase) {
            FocusPhase.WORK -> plan.workMin
            FocusPhase.BREAK -> plan.breakMin
            FocusPhase.LONG_BREAK -> plan.longBreakMin
        }
        val total = minutes * 60_000L
        endAt = SystemClock.elapsedRealtime() + total
        _state.value = FocusState(true, _state.value.paused && _state.value.active, phase, plan, total, total, completed)
        if (chime) engine.playChime()
        engine.setMix(if (phase == FocusPhase.WORK) workMix() else breakMix(), phaseLabel(phase), rampMs = 3000)
        if (!_state.value.paused) engine.play()
    }
}
