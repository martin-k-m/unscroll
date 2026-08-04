package me.blinkdev.unscroll.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.blinkdev.unscroll.block.BlockRules

private val LIMIT_STEPS = listOf(0, 10, 20, 30, 45, 60, 90)

@Composable
fun HomeScreen(state: UiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val insets = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = insets,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(20.dp, 24.dp, 20.dp, 0.dp)) {
                Text(
                    "unscroll",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Short-video feeds close themselves. Everything stays on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            SetupCard(
                blockerEnabled = state.blockerEnabled,
                usageAccessGranted = state.usageAccessGranted,
                onEnableBlocker = { Permissions.openAccessibilitySettings(context) },
                onGrantUsage = { Permissions.openUsageAccessSettings(context) },
            )
        }

        item { SectionTitle("Feeds to block") }

        items(BlockRules.surfaces, key = { it.id }) { surface ->
            SettingRow(
                title = surface.label,
                subtitle = surface.packageName,
            ) {
                Switch(
                    checked = surface.id in state.settings.enabledSurfaces,
                    onCheckedChange = { viewModel.setSurfaceEnabled(surface.id, it) },
                )
            }
        }

        item { SectionTitle("Daily limits") }

        items(BlockRules.limitCandidates, key = { it.first }) { (pkg, label) ->
            val limit = state.settings.dailyLimits[pkg] ?: 0
            val used = state.usedToday[pkg] ?: 0
            LimitRow(
                label = label,
                usedMinutes = used,
                limitMinutes = limit,
                onCycle = {
                    val next = LIMIT_STEPS[(LIMIT_STEPS.indexOf(limit).coerceAtLeast(0) + 1) % LIMIT_STEPS.size]
                    viewModel.setDailyLimit(pkg, next)
                },
            )
        }

        item { SectionTitle("Escape hatch") }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (state.settings.isSnoozed(System.currentTimeMillis())) {
                            "Blocking is paused."
                        } else {
                            "Blocking is on."
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pausing is deliberately short. If you need longer, turn the service off in Accessibility settings, which takes enough taps to be a decision.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.snooze(5) }) { Text("Pause 5 min") }
                        TextButton(onClick = { viewModel.clearSnooze() }) { Text("Resume now") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SetupCard(
    blockerEnabled: Boolean,
    usageAccessGranted: Boolean,
    onEnableBlocker: () -> Unit,
    onGrantUsage: () -> Unit,
) {
    if (blockerEnabled && usageAccessGranted) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Two switches to flip", fontWeight = FontWeight.SemiBold)
            if (!blockerEnabled) {
                Text(
                    "The accessibility service is what lets unscroll see a Reels or Shorts screen and back out of it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onEnableBlocker) { Text("Enable unscroll blocker") }
            }
            if (!usageAccessGranted) {
                Text(
                    "Usage access is only needed for daily time limits. Skip it if you just want the feed blocking.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onGrantUsage) { Text("Grant usage access") }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun LimitRow(
    label: String,
    usedMinutes: Int,
    limitMinutes: Int,
    onCycle: () -> Unit,
) {
    SettingRow(
        title = label,
        subtitle = if (limitMinutes > 0) {
            "$usedMinutes of $limitMinutes min used today"
        } else {
            "$usedMinutes min today, no limit"
        },
    ) {
        OutlinedButton(onClick = onCycle) {
            Text(if (limitMinutes > 0) "$limitMinutes min" else "Off")
        }
    }
}
