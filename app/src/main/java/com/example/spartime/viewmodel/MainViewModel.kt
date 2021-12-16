package com.example.spartime.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel(){

    val numOfRounds = MutableLiveData<Int>()
    val roundLengthInMin = MutableLiveData<Int>()
    val pauseLengthInSecs = MutableLiveData<Int>()

    fun setNumOfRounds(numberOfRounds:Int){
        numOfRounds.value = numberOfRounds
    }
    fun setRoundLengthInMin(seconds:Int){
        roundLengthInMin.value = seconds
    }
    fun setPauseLengthInSecs(seconds:Int){
        pauseLengthInSecs.value = seconds
    }

}