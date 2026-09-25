package com.example.boombee.data

class Dao(private val preferencesProvider: PreferencesProvider) {
    var isInProgress: Boolean? = preferencesProvider.getBoolean(KEY_PROGRESS)
    var defaultTraining: String? = preferencesProvider.getString(KEY_DEFAULT)
    var soundStatus: Boolean? = preferencesProvider.getBoolean(KEY_STATUS)
    var dialogAnswer: Boolean? = preferencesProvider.getBoolean(KEY_DIALOG)
    var timeIfInterupt: Int? = preferencesProvider.getInt(KEY_TIME_IF_INTERUPT)

    companion object {
        private const val KEY_PROGRESS = "progress"
        private const val KEY_DEFAULT = "default"
        private const val KEY_STATUS = "sound_status"
        private const val KEY_DIALOG = "key_dialog"
        private const val KEY_TIME_IF_INTERUPT = "time_if_interupt"
        private const val KEY_FREESTYLE_ENABLED = "coach_freestyle_enabled"
        private const val KEY_TACTICAL_COMMANDS_ENABLED = "coach_tactical_commands_enabled"
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
        preferencesProvider.putBoolean(KEY_STATUS, status)
    }

    fun saveIfInterupt(status: Int) {
        this.timeIfInterupt = status
        preferencesProvider.putInt(KEY_TIME_IF_INTERUPT, status)
    }

    fun getIfInterupt(): Int? {
        return preferencesProvider.getInt(KEY_TIME_IF_INTERUPT)
    }

    fun saveDialogAnswer(status: Boolean) {
        this.dialogAnswer = status
        preferencesProvider.putBoolean(KEY_DIALOG, status)
    }

    fun fetchDialogAnswer(): Boolean? {
        return preferencesProvider.getBoolean(KEY_DIALOG)
    }

    fun getSoundStatus(): Boolean {
        return preferencesProvider.getBoolean(KEY_STATUS) ?: true
    }

    fun saveFreestyleEnabled(enabled: Boolean) {
        preferencesProvider.putBoolean(KEY_FREESTYLE_ENABLED, enabled)
    }

    fun getFreestyleEnabled(): Boolean {
        return preferencesProvider.getBoolean(KEY_FREESTYLE_ENABLED) ?: true
    }

    fun saveTacticalCommandsEnabled(enabled: Boolean) {
        preferencesProvider.putBoolean(KEY_TACTICAL_COMMANDS_ENABLED, enabled)
    }

    fun getTacticalCommandsEnabled(): Boolean {
        return preferencesProvider.getBoolean(KEY_TACTICAL_COMMANDS_ENABLED) ?: true
    }
}
