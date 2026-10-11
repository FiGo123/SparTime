package com.example.boombee.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.example.boombee.safety.SafetyText
import com.example.boombee.wear.ui.theme.darkChipColors

/**
 * Health & safety disclaimer. On first launch it has to be accepted
 * ([onBack] null); from Settings it's read-only with a Back chip.
 */
@Composable
fun SafetyScreen(onAccept: (() -> Unit)?, onBack: (() -> Unit)?) {
    if (onBack != null) BackHandler(onBack = onBack)
    Scaffold {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ListHeader { Text(SafetyText.TITLE) } }
            item { SafetyParagraph(SafetyText.GENERAL) }
            item { SafetyParagraph(SafetyText.HEART_RATE) }
            item { SafetyParagraph("Full terms: boombee.web.app/terms.html") }
            if (onAccept != null) item { Chip(label = { Text("I understand") }, onClick = onAccept) }
            if (onBack != null) item { Chip(label = { Text("Back") }, onClick = onBack, colors = darkChipColors()) }
        }
    }
}

@Composable
private fun SafetyParagraph(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.caption2,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}
