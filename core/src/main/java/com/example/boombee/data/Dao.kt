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
        private const val KEY_SHOW_CALLS_ON_SCREEN = "coach_show_calls_on_screen"
        private const val KEY_AGE = "user_age"
        private const val KEY_HEART_RATE_ENABLED = "heart_rate_enabled"
        private const val KEY_HR_COACHING_ENABLED = "heart_rate_coaching_enabled"
        private const val KEY_SAFETY_ACCEPTED = "safety_disclaimer_accepted"
        private const val KEY_HEART_RATE_SETUP_ASKED = "heart_rate_setup_asked"
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

    /** Phone round screen shows each coach call in big text (on by default). */
    fun getShowCallsOnScreen(): Boolean = preferencesProvider.getBoolean(KEY_SHOW_CALLS_ON_SCREEN) ?: true

    fun saveShowCallsOnScreen(enabled: Boolean) = preferencesProvider.putBoolean(KEY_SHOW_CALLS_ON_SCREEN, enabled)

    /** Age for max heart rate (220 − age); null until the user sets it. */
    fun getAge(): Int? = preferencesProvider.getInt(KEY_AGE).takeIf { it > 0 }

    fun saveAge(age: Int) = preferencesProvider.putInt(KEY_AGE, age)

    /** Heart rate recording during sessions (watch). */
    fun getHeartRateEnabled(): Boolean = preferencesProvider.getBoolean(KEY_HEART_RATE_ENABLED) ?: true

    fun saveHeartRateEnabled(enabled: Boolean) = preferencesProvider.putBoolean(KEY_HEART_RATE_ENABLED, enabled)

    /** Coach adapts to heart-rate zones (Coach mode on the watch). */
    fun getHeartRateCoachingEnabled(): Boolean = preferencesProvider.getBoolean(KEY_HR_COACHING_ENABLED) ?: true

    fun saveHeartRateCoachingEnabled(enabled: Boolean) = preferencesProvider.putBoolean(KEY_HR_COACHING_ENABLED, enabled)

    fun getSafetyAccepted(): Boolean = preferencesProvider.getBoolean(KEY_SAFETY_ACCEPTED) ?: false

    fun saveSafetyAccepted() = preferencesProvider.putBoolean(KEY_SAFETY_ACCEPTED, true)

    /** The one-time "set your age for heart-rate zones" step was shown. */
    fun getHeartRateSetupAsked(): Boolean = preferencesProvider.getBoolean(KEY_HEART_RATE_SETUP_ASKED) ?: false

    fun saveHeartRateSetupAsked() = preferencesProvider.putBoolean(KEY_HEART_RATE_SETUP_ASKED, true)
}
