package com.example.spartime.data

class Dao (private val preferencesProvider: PreferencesProvider){
    var isInProgress: Boolean? = preferencesProvider.getBoolean(KEY_PROGRESS)

    companion object {
        private const val KEY_PROGRESS = "progress"
    }

    fun saveIsInProgress(isInProgress: Boolean) {
        this.isInProgress = isInProgress
        preferencesProvider.putBoolean(KEY_PROGRESS, isInProgress)
    }

}