package com.example.boombee

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.boombee.analytics.Analytics
import com.example.boombee.databinding.ActivityMainBinding
import com.example.boombee.update.UpdateController

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Play In-App Updates; the home screen observes [UpdateController.updateReady]. */
    val updateController = UpdateController(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ forces edge-to-edge; opt in on older versions too so
        // every API level lays out the same way.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        Analytics.applyConsent(this)
        val navController = (supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment).navController
        binding.bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            destination.label?.let { Analytics.logScreen(this, it.toString()) }
            // Tabs only on the three top-level screens; a session, the
            // tutorial, settings and session detail use the full screen.
            setTabsVisible(destination.id in TAB_DESTINATIONS)
        }
    }

    /** Switches to a tab as if the user tapped it (e.g. the Training tab's week card → History). */
    fun selectTab(destinationId: Int) {
        binding.bottomNav.selectedItemId = destinationId
    }

    private var lastInsets: WindowInsetsCompat? = null

    private fun setTabsVisible(visible: Boolean) {
        val target = if (visible) View.VISIBLE else View.GONE
        if (binding.bottomNav.visibility == target) return
        binding.bottomNav.visibility = target
        lastInsets?.let(::applyInsets)
    }

    // The nav host is padded clear of the status bar and cutouts; the bottom
    // inset goes to the tab bar when it's shown, otherwise to the nav host
    // (which also lifts content above the keyboard).
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            lastInsets = windowInsets
            applyInsets(windowInsets)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun applyInsets(windowInsets: WindowInsetsCompat) {
        val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
        val tabsShown = binding.bottomNav.visibility == View.VISIBLE
        binding.fragmentContainerView.updatePadding(
            left = bars.left,
            top = bars.top,
            right = bars.right,
            bottom = if (tabsShown) 0 else maxOf(bars.bottom, ime.bottom),
        )
        binding.bottomNav.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
    }

    private companion object {
        val TAB_DESTINATIONS = setOf(R.id.first, R.id.learn, R.id.historyTraining)
    }
}
