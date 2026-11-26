package dev.nerdytech.jellyglance.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nerdytech.jellyglance.R
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Auth
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.theme.LocalBrand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sign in with a JellyGlance account the way the web dashboard does: Quick Connect, Jellyfin password or a local account. */
@Composable
fun SignInScreen(
    vm: AppViewModel, baseUrl: String, onDone: () -> Unit, onBack: () -> Unit,
    allowApiKey: Boolean = true, notice: String? = null,
) {
    val base = baseUrl.trim().trimEnd('/')
    val brand = LocalBrand.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<Auth.Config?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var qc by remember { mutableStateOf<Auth.QuickConnect?>(null) }
    var busy by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    fun message(e: Throwable) = (e as? ApiException)?.let { if (it.detail.isNotBlank()) it.detail else it.kind.message } ?: e.message.orEmpty()

    LaunchedEffect(base) {
        runCatching { vm.auth.config(base) }.onSuccess { config = it }.onFailure { error = message(it) }
    }

    // Poll Jellyfin until the code is approved, then trade the secret for a JellyGlance session.
    LaunchedEffect(qc) {
        val code = qc ?: return@LaunchedEffect
        repeat(100) {
            delay(3000)
            val approved = runCatching { vm.auth.quickConnectApproved(base, code.secret) }.getOrDefault(false)
            if (approved) {
                runCatching { vm.auth.quickConnectFinish(base, code.secret) }
                    .onSuccess { vm.saveSession(base, it); onDone() }
                    .onFailure { error = message(it); qc = null }
                return@LaunchedEffect
            }
        }
        qc = null
        error = "The Quick Connect code expired. Get a new one."
    }

    fun passwordSignIn() {
        busy = true
        error = null
        scope.launch {
            runCatching { if (config?.jellyfin == true) vm.auth.jellyfin(base, user.trim(), pass) else vm.auth.local(base, user.trim(), pass) }
                .onSuccess { pass = ""; vm.saveSession(base, it); onDone() }
                .onFailure { error = if ((it as? ApiException)?.kind == ApiException.Kind.AUTH) "Wrong username or password" else message(it) }
            busy = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(brand.primary.copy(alpha = 0.25f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max = 520.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth()) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }
            Image(painterResource(R.drawable.jelly_logo), null, Modifier.size(88.dp))
            Spacer(Modifier.height(12.dp))
            Text("Sign in to JellyGlance", style = MaterialTheme.typography.headlineSmall)
            Text(
                listOfNotNull(Uri.parse(base).host, config?.version?.takeIf { it.isNotBlank() }?.let { "v$it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            notice?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))

            val cfg = config
            when {
                cfg == null && error == null -> CircularProgressIndicator()
                cfg?.oidc == true -> Text(
                    "This server signs in with single sign-on (${cfg.label.ifBlank { "OIDC" }}), which the app can't do yet. Add an API key in the app's Settings instead.",
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                )
                cfg != null -> {
                    if (cfg.jellyfin) {
                        Card(
                            Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Quick Connect", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "No password needed: approve this device from Jellyfin.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(16.dp))
                                val code = qc
                                if (code == null) {
                                    Button(onClick = {
                                        error = null
                                        busy = true
                                        scope.launch {
                                            runCatching { vm.auth.quickConnectStart(base) }.onSuccess { qc = it }.onFailure { error = message(it) }
                                            busy = false
                                        }
                                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Get a code") }
                                } else {
                                    Text(
                                        code.code.chunked(3).joinToString(" "),
                                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = 4.sp,
                                        color = brand.primary,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "In Jellyfin, open your profile, then Quick Connect, and enter this code. Or open the link below on a device signed in to Jellyfin.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    if (code.url.isNotBlank()) {
                                        FilledTonalButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.url))) } }) {
                                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Approve in Jellyfin")
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Waiting for approval…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text("  or with your Jellyfin password  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(Modifier.weight(1f))
                        }
                    }
                    OutlinedTextField(
                        value = user, onValueChange = { user = it }, singleLine = true,
                        label = { Text(if (cfg.jellyfin) "Jellyfin username" else "Username") }, leadingIcon = { Icon(Icons.Rounded.Person, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it }, singleLine = true, label = { Text("Password") },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showPass = !showPass }) { Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show password") } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = ::passwordSignIn, enabled = !busy && user.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Sign in")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (cfg.jellyfin) "Your password goes to Jellyfin through JellyGlance and is never stored on this phone."
                        else "Your password is sent to JellyGlance and never stored on this phone.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
            }
            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack) { Text(if (allowApiKey) "Back" else "Change server address") }
            Spacer(Modifier.height(12.dp))
        }
    }
}
