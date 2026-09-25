package com.example.boombee.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.boombee.coach.CoachSession
import com.example.boombee.coach.Difficulty
import com.example.boombee.coach.TrainingMode
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.data.Dao
import com.example.boombee.data.PreferencesProvider

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = Dao(PreferencesProvider(getApplication()))
    
    private val _numOfRounds = MutableLiveData<Int>()
    val numOfRounds: LiveData<Int> = _numOfRounds
    
    private val _roundLengthInMin = MutableLiveData<Int>()
    val roundLengthInMin: LiveData<Int> = _roundLengthInMin
    
    private val _pauseLengthInMin = MutableLiveData<Int>()
    val pauseLengthInMin: LiveData<Int> = _pauseLengthInMin
    
    private val _currentRound = MutableLiveData<Int>()
    val currentRound: LiveData<Int> = _currentRound
    
    private val _leftTime = MutableLiveData<Int>()
    val leftTime: LiveData<Int> = _leftTime
    
    private val _trainingType = MutableLiveData<String>()
    val trainingType: LiveData<String> = _trainingType
    
    private val _finishedRounds = MutableLiveData<Int>()
    val finishedRounds: LiveData<Int> = _finishedRounds

    private val _selectedDifficulty = MutableLiveData<Int>(3)
    val selectedDifficulty: LiveData<Int> = _selectedDifficulty

    // Boxing Coach (v2). Unrelated to [selectedDifficulty] above, which is
    // the pre-existing (always-3, unused) 1-5 training-difficulty rating —
    // this is the coach's Beginner/Intermediate/Advanced pacing profile.
    private val _trainingMode = MutableLiveData(TrainingMode.TIMER_ONLY)
    val trainingMode: LiveData<TrainingMode> = _trainingMode

    private val _coachDifficulty = MutableLiveData<Difficulty?>(null)
    val coachDifficulty: LiveData<Difficulty?> = _coachDifficulty

    private val _voiceStyle = MutableLiveData(VoiceStyle.WORDS)
    val voiceStyle: LiveData<VoiceStyle> = _voiceStyle

    fun setVoiceStyle(style: VoiceStyle) {
        _voiceStyle.value = style
    }

    /** 0 (off), 30, or 60 — see RampStage.WARMUP. */
    private val _warmupSeconds = MutableLiveData(0)
    val warmupSeconds: LiveData<Int> = _warmupSeconds

    fun setWarmupSeconds(seconds: Int) {
        _warmupSeconds.value = seconds
    }

    /**
     * Persists across the Second/Rest fragment recreation that happens
     * every round within one training session (Nav Component replaces the
     * fragment instance each time), unlike a plain fragment field. Reset
     * via [resetCoachSession] whenever a fresh session starts from First.
     */
    var coachSession: CoachSession? = null
        private set

    fun setTrainingMode(mode: TrainingMode) {
        _trainingMode.value = mode
    }

    fun setCoachDifficulty(difficulty: Difficulty?) {
        _coachDifficulty.value = difficulty
    }

    fun resetCoachSession() {
        Log.d(
            "boombeelogs",
            "resetCoachSession: mode=${_trainingMode.value} difficulty=${_coachDifficulty.value} warmupSeconds=${_warmupSeconds.value} voiceStyle=${_voiceStyle.value}"
        )
        coachSession = if (_trainingMode.value == TrainingMode.BOXING_COACH) {
            CoachSession(getApplication(), _coachDifficulty.value ?: Difficulty.BEGINNER)
        } else {
            null
        }
    }

    fun setNumOfRounds(numberOfRounds: Int) {
        _numOfRounds.value = numberOfRounds
    }

    fun setRoundLengthInMin(minutes: Int) {
        _roundLengthInMin.value = minutes
    }

    fun setPauseLengthInSecs(minutes: Int) {
        _pauseLengthInMin.value = minutes
    }

    fun setCurrentRound(currentRoundFromFragment: Int) {
        _currentRound.value = currentRoundFromFragment
    }

    fun setDefaultTrainingType(type: String) {
        repository.saveDefault(type)
        _trainingType.value = type
    }

    fun setLeftTime(time: Int) {
        _leftTime.value = time
    }

    fun getDefaultTrainingType() {
        _trainingType.value = repository.getDefault()
    }

    fun setSoundSettings(status: Boolean) {
        repository.saveSoundStatus(status)
    }

    fun setLastTrainingRound(finishedRounds: Int) {
        repository.saveIfInterupt(finishedRounds)
        _finishedRounds.value = finishedRounds
    }

    fun getLastTrainingRound() {
        _finishedRounds.value = repository.getIfInterupt()
    }

    fun setDialogAnswer(status: Boolean) {
        repository.saveDialogAnswer(status)
    }

    fun getDialogAnswer(): Boolean? {
        return repository.fetchDialogAnswer()
    }

    fun getSoundStatus(): Boolean {
        return repository.getSoundStatus()
    }

    fun getTacticalCommandsEnabled(): Boolean = repository.getTacticalCommandsEnabled()

    fun setTacticalCommandsEnabled(enabled: Boolean) {
        repository.saveTacticalCommandsEnabled(enabled)
    }

    fun getFreestyleEnabled(): Boolean = repository.getFreestyleEnabled()

    fun setFreestyleEnabled(enabled: Boolean) {
        repository.saveFreestyleEnabled(enabled)
    }

    fun setSelectedDifficulty(rating: Int) {
        _selectedDifficulty.value = rating
    }

    fun getSelectedDifficulty(): Int = _selectedDifficulty.value ?: 3
}
