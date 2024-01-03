package com.example.spartime.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel(){

    val numOfRounds = MutableLiveData<Int>()
    val roundLengthInMin = MutableLiveData<Int>()
    val pauseLengthInMin = MutableLiveData<Int>()
    val currentRound = MutableLiveData<Int>()

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


}