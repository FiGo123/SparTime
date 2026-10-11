package com.example.boombee.safety

/**
 * Health & safety wording shown on first launch (phone and watch) and
 * under Settings → Safety & disclaimer. Same text as hosting/terms.html —
 * change both together.
 */
object SafetyText {
    const val TITLE = "Health & safety"

    const val GENERAL =
        "BoomBee is a training aid, not a medical device, and it does not replace a qualified " +
            "boxing coach or trainer. Talk to a doctor before starting intense exercise, especially " +
            "if you have a heart condition or high blood pressure, are pregnant, or take medication. " +
            "Stop right away if you feel pain, dizziness or shortness of breath. Train in a safe " +
            "space with proper wraps and gloves. You train at your own risk."

    const val HEART_RATE =
        "Heart rate and zones are estimates (max heart rate = 220 − age) and can be inaccurate. " +
            "They are not medical advice. Your heart rate and age stay on your watch."

    const val TERMS_URL = "https://boombee.web.app/terms.html"
}
