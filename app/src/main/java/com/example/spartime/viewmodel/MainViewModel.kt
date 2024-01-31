package com.example.spartime.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.spartime.data.Dao
import com.example.spartime.data.PreferencesProvider

class MainViewModel(application: Application) : AndroidViewModel(application){

    private val repository = Dao(PreferencesProvider(getApplication()))
    val numOfRounds = MutableLiveData<Int>()
    val roundLengthInMin = MutableLiveData<Int>()
    val pauseLengthInMin = MutableLiveData<Int>()
    val currentRound = MutableLiveData<Int>()
    var trainingType = MutableLiveData<String>()
    var finishedRounds = MutableLiveData<Int>()

    fun setNumOfRounds(numberOfRounds:Int){
        numOfRounds.value = numberOfRounds
    }
    fun setRoundLengthInMin(seconds:Int){
        roundLengthInMin.value = seconds
    }
    fun setPauseLengthInSecs(seconds:Int){
        pauseLengthInMin.value = seconds
    }
    fun setCurrentRound(currentRoundFromFragment:Int){
        currentRound.value = currentRoundFromFragment
    }

    fun setDefaultTrainingType(type: String) {
        repository.saveDefault(type)

    }

    fun getDefaultTrainingType() {
        trainingType.value = repository.getDefault()
    }


    fun setSoundSettings(status: Boolean){
        repository.saveSoundStatus(status)
    }

    fun setLastTrainingRound(finishedRounds: Int){
        repository.saveIfInterupt(finishedRounds)
    }
    fun getLastTrainingRound(){
        finishedRounds.value = repository.getIfInterupt()
    }

    fun setDialogAnswer(status: Boolean){
        repository.saveDialogAnswer(status)
    }

    fun getDialogAnswer(): Boolean? {
        return repository.getDialogAnswer()
    }





}