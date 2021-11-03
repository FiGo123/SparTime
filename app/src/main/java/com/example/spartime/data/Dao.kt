package com.example.spartime.data

class Dao (private val preferencesProvider: PreferencesProvider){
    var inProgress: Boolean? = preferencesProvider.getBoolean(KEY_PROGRESS)

    companion object {
        private const val KEY_PROGRESS = "progress"
    }

}