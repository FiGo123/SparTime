package com.example.spartime.data

class Dao (private val preferencesProvider: PreferencesProvider){
    var isInProgress: Boolean? = preferencesProvider.getBoolean(KEY_PROGRESS)
    var defaultTraining: String? = preferencesProvider.getString(KEY_DEFAULT)
    var soundStatus: Boolean? = preferencesProvider.getBoolean(KEY_STATUS)

    companion object {
        private const val KEY_PROGRESS = "progress"
        private const val KEY_DEFAULT = "default"
        private const val KEY_STATUS = "sound_status"
    }

    fun saveIsInProgress(isInProgress: Boolean) {
        this.isInProgress = isInProgress
        preferencesProvider.putBoolean(KEY_PROGRESS, isInProgress)
    }

    fun saveDefault(default: String) {
        this.defaultTraining = default
        preferencesProvider.putString(KEY_DEFAULT, default)
    }

    fun getDefault(): String? {
        return preferencesProvider.getString(KEY_DEFAULT)
    }

    fun saveSoundStatus(status: Boolean) {
        this.soundStatus = status
        return preferencesProvider.putBoolean(KEY_STATUS, status)
    }




}