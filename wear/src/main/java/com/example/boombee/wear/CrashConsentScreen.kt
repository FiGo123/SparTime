package com.example.boombee.wear

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
import com.example.boombee.wear.ui.theme.darkChipColors

/** First launch only: opt in to crash reports (changeable later in Settings). */
@Composable
fun CrashConsentScreen(onAnswer: (Boolean) -> Unit) {
    Scaffold {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ListHeader { Text("Help improve BoomBee?") } }
            item {
                Text(
                    "Send anonymous crash reports. No personal info. Change it any time in Settings.",
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item { Chip(label = { Text("Send reports") }, onClick = { onAnswer(true) }) }
            item { Chip(label = { Text("No thanks") }, onClick = { onAnswer(false) }, colors = darkChipColors()) }
        }
    }
}
