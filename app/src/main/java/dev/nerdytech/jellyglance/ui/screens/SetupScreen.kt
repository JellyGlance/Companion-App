package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.nerdytech.jellyglance.R
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.theme.LocalBrand
import kotlinx.coroutines.launch

/** First run: find the JellyGlance server, then sign in with an account. */
@Composable
fun SetupScreen(vm: AppViewModel) {
    val current = vm.settings.value
    // Coming back after a session ended: go straight to sign-in for the same server.
    var signIn by rememberSaveable { mutableStateOf(current.baseUrl.isNotBlank() && current.sessionExpired) }
    var url by rememberSaveable { mutableStateOf(current.baseUrl.ifBlank { "https://" }) }
    var insecure by rememberSaveable { mutableStateOf(current.insecure) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun next() {
        val clean = url.trim().trimEnd('/').let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        url = clean
        busy = true
        error = null
        scope.launch {
            vm.updateSettings { it.copy(baseUrl = clean, insecure = insecure) }
            runCatching { vm.auth.config(clean) }
                .onSuccess { signIn = true }
                .onFailure { e ->
                    error = (e as? ApiException)?.let {
                        when (it.kind) {
                            ApiException.Kind.MISSING, ApiException.Kind.NOT_JSON -> "That address doesn't look like a JellyGlance server."
                            else -> it.kind.message + if (it.detail.isNotBlank()) " (${it.detail})" else ""
                        }
                    } ?: e.message.orEmpty()
                }
            busy = false
        }
    }

    if (signIn) {
        SignInScreen(
            vm, url, onDone = {}, onBack = { signIn = false }, allowApiKey = false,
            notice = if (current.sessionExpired) "Your session ended. Sign in again to carry on." else null,
        )
        return
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(LocalBrand.current.primary.copy(alpha = 0.28f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)),
        ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Image(painterResource(R.drawable.jelly_logo), contentDescription = null, modifier = Modifier.size(112.dp))
            Spacer(Modifier.height(16.dp))
            Text("JellyGlance", style = MaterialTheme.typography.displaySmall)
            Text(
                "Your Jellyfin server at a glance: streams, stats, downloads, requests and health.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = url, onValueChange = { url = it }, label = { Text("JellyGlance address") },
                placeholder = { Text("https://glance.example.com") },
                leadingIcon = { Icon(Icons.Rounded.Link, null) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (url.length > 8) next() }),
                supportingText = { Text("The address you open JellyGlance on in a browser.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow self-signed certificates", style = MaterialTheme.typography.bodyLarge)
                    Text("Only for servers you trust on your own network", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = insecure, onCheckedChange = { insecure = it })
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = ::next, enabled = !busy && url.length > 8, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Continue")
            }
            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "You'll sign in with your JellyGlance account next. JellyGlance is a separate project and this companion app is unofficial.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
