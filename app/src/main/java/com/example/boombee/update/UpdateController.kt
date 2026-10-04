package com.example.boombee.update

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.boombee.data.PreferencesProvider
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

private const val TAG = "boombeelogs"

enum class UpdateMode { FLEXIBLE, IMMEDIATE }

/**
 * Play In-App Updates (RELEASE_AUTO_UPDATE.md). Normal updates download in
 * the background ([UpdateMode.FLEXIBLE]) and [updateReady] turns true when
 * they can be installed — the home screen shows a "Restart" Snackbar, so a
 * running round is never interrupted. Critical (priority 4–5) or week-old
 * updates use the blocking [UpdateMode.IMMEDIATE] flow.
 *
 * Only Play-installed builds ever see an update; on debug builds every
 * call quietly finds nothing.
 */
class UpdateController(private val activity: ComponentActivity) : DefaultLifecycleObserver {

    // Lazy: this is constructed with the activity, before it has a context.
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(activity) }
    private val prefs by lazy { PreferencesProvider(activity) }

    // Registered in the constructor, i.e. before the activity is STARTED.
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Not now / back: remember it so this version isn't offered again
        // as a flexible update (it still becomes immediate once stale).
        if (result.resultCode != ComponentActivity.RESULT_OK) {
            pendingVersionCode?.let { prefs.putInt(KEY_DECLINED_VERSION, it) }
        }
    }
    private var pendingVersionCode: Int? = null

    private val _updateReady = MutableLiveData(false)
    val updateReady: LiveData<Boolean> = _updateReady

    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) _updateReady.value = true
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        appUpdateManager.registerListener(installListener)
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener(::maybeStartUpdate)
            .addOnFailureListener { Log.d(TAG, "Update check failed: ${it.message}") }
    }

    override fun onResume(owner: LifecycleOwner) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> _updateReady.value = true
                // An accepted immediate update was interrupted (app left mid-update): resume it.
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                    startFlow(info, AppUpdateType.IMMEDIATE)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        appUpdateManager.unregisterListener(installListener)
    }

    /** Installs a downloaded flexible update; the app restarts. */
    fun completeUpdate() {
        _updateReady.value = false
        appUpdateManager.completeUpdate()
    }

    private fun maybeStartUpdate(info: AppUpdateInfo) {
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return
        val mode = chooseUpdateMode(
            priority = info.updatePriority(),
            stalenessDays = info.clientVersionStalenessDays(),
            flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
            declinedBefore = prefs.getInt(KEY_DECLINED_VERSION) == info.availableVersionCode(),
        ) ?: return
        startFlow(info, if (mode == UpdateMode.IMMEDIATE) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE)
    }

    private fun startFlow(info: AppUpdateInfo, type: Int) {
        pendingVersionCode = info.availableVersionCode()
        runCatching {
            appUpdateManager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())
        }.onFailure { Log.d(TAG, "Update flow failed: ${it.message}") }
    }

    companion object {
        private const val KEY_DECLINED_VERSION = "update_declined_version"
        private const val CRITICAL_PRIORITY = 4
        private const val STALE_DAYS = 7

        /** Which flow to offer for an available update, or null to stay quiet. */
        fun chooseUpdateMode(
            priority: Int,
            stalenessDays: Int?,
            flexibleAllowed: Boolean,
            immediateAllowed: Boolean,
            declinedBefore: Boolean,
        ): UpdateMode? {
            val urgent = priority >= CRITICAL_PRIORITY || (stalenessDays ?: 0) >= STALE_DAYS
            return when {
                urgent && immediateAllowed -> UpdateMode.IMMEDIATE
                declinedBefore -> null
                flexibleAllowed -> UpdateMode.FLEXIBLE
                else -> null
            }
        }
    }
}
