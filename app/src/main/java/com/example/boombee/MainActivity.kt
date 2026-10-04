package com.example.boombee

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
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
        val navHost = supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        navHost.navController.addOnDestinationChangedListener { _, destination, _ ->
            destination.label?.let { Analytics.logScreen(this, it.toString()) }
        }
    }

    // All screens share the activity's background, so padding the nav host
    // keeps every fragment clear of the status/navigation bars, cutouts and
    // the keyboard without per-fragment handling.
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentContainerView) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom)
            )
            WindowInsetsCompat.CONSUMED
        }
    }
}
