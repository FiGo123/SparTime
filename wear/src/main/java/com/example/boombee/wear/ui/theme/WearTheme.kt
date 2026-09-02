package com.example.boombee.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val BoomBeeColors = Colors(
    primary = androidx.compose.ui.graphics.Color(0xFFFFC107),
    primaryVariant = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
    secondary = androidx.compose.ui.graphics.Color(0xFFFF6F00),
    background = androidx.compose.ui.graphics.Color(0xFF0D0D0D),
    surface = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
)

@Composable
fun BoomBeeWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = BoomBeeColors,
        content = content
    )
}
