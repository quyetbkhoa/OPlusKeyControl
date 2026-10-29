package dev.opluskeycontrol.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.opluskeycontrol.R
import dev.opluskeycontrol.domain.Tier
import dev.opluskeycontrol.root.RootAuthorizationState

/** Selected mode is separate from actual permission and input connection. */
@Composable
fun BackendStatus(tier: Tier, accessibility: Boolean, rootConnected: Boolean,
                  root: RootAuthorizationState, shizukuConnected: Boolean, shizukuGranted: Boolean) {
    val status = when (tier) {
        Tier.BASIC -> if (accessibility) R.string.accessibility_connected else R.string.accessibility_disconnected
        Tier.SHIZUKU -> if (shizukuGranted) R.string.shizuku_granted else if (shizukuConnected) R.string.shizuku_running else R.string.shizuku_missing
        Tier.ROOT -> when {
            rootConnected -> R.string.root_connected
            root == RootAuthorizationState.CHECKING -> R.string.root_checking
            root == RootAuthorizationState.AUTHORIZED -> R.string.root_authorized_waiting
            root == RootAuthorizationState.UNAVAILABLE -> R.string.root_authorization_failed
            else -> R.string.root_disconnected
        }
    }
    val active = when(tier) { Tier.ROOT -> rootConnected; Tier.BASIC -> accessibility; Tier.SHIZUKU -> shizukuConnected && shizukuGranted }
    val checking = tier == Tier.ROOT && root == RootAuthorizationState.CHECKING
    Text(stringResource(status), style = MaterialTheme.typography.bodyMedium,
        color = if(active) OpColors.Success else if(checking) OpColors.Amber else OpColors.Red)
    if (tier != Tier.ROOT) Text(stringResource(R.string.observation_only), style = MaterialTheme.typography.bodySmall)
}
