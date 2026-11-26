package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.nerdytech.jellyglance.R
import dev.nerdytech.jellyglance.ui.components.Wordmark

/** Shown while the app lock is on and the user hasn't unlocked yet. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.jelly_logo), null, Modifier.size(96.dp))
        Spacer(Modifier.height(12.dp))
        Wordmark(30.dp)
        Spacer(Modifier.height(8.dp))
        Text("Locked", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onUnlock) {
            Icon(Icons.Rounded.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Unlock")
        }
    }
}
