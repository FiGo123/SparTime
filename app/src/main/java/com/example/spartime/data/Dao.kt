package com.example.spartime.data

class Dao (private val preferencesProvider: PreferencesProvider){
    var isInProgress: Boolean? = preferencesProvider.getBoolean(KEY_PROGRESS)
    var default: String? = preferencesProvider.getString(KEY_DEFAULT)

    companion object {
        private const val KEY_PROGRESS = "progress"
        private const val KEY_DEFAULT = "default"
    }

    fun saveIsInProgress(isInProgress: Boolean) {
        this.isInProgress = isInProgress
        preferencesProvider.putBoolean(KEY_PROGRESS, isInProgress)
    }

    fun saveDefault(default: String) {
        this.default = default
        preferencesProvider.putString(KEY_DEFAULT, default)
    }

    fun getDefault(): String? {
        return preferencesProvider.getString(KEY_DEFAULT)
    }


}