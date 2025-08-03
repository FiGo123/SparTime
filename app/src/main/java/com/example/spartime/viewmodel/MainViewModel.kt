package com.example.spartime.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.spartime.data.Dao
import com.example.spartime.data.PreferencesProvider

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
}
