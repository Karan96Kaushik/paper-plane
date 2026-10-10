package com.barontech.paperplane.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barontech.paperplane.repository.NotificationRepository
import com.barontech.paperplane.sync.SupabaseFilterField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupabaseExclusionRules(
    repository: NotificationRepository,
    enabled: Boolean,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val rules by repository.observeSupabaseExclusionRules()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var field by remember { mutableStateOf(SupabaseFilterField.TITLE) }
    var pattern by remember { mutableStateOf("") }
    var fieldMenuExpanded by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Don't upload", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "A notification is kept on this device when any enabled rule matches. * matches any text and ? matches one character. Matching ignores case. Example: Title *otp* skips one-time codes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExposedDropdownMenuBox(
            expanded = fieldMenuExpanded,
            onExpandedChange = { if (enabled) fieldMenuExpanded = it }
        ) {
            OutlinedTextField(
                value = field.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Field") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fieldMenuExpanded) },
                enabled = enabled,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = fieldMenuExpanded,
                onDismissRequest = { fieldMenuExpanded = false }
            ) {
                SupabaseFilterField.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            field = option
                            fieldMenuExpanded = false
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = pattern,
            onValueChange = {
                pattern = it.take(MAX_PATTERN_LENGTH)
                formError = null
            },
            label = { Text("Wildcard") },
            placeholder = { Text("*keyword*") },
            supportingText = {
                Text(formError ?: "Matches the whole field. Use *keyword* to match text that contains keyword.")
            },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = {
                val trimmed = pattern.trim()
                if (trimmed.isEmpty()) {
                    formError = "Enter a wildcard pattern."
                    return@OutlinedButton
                }
                scope.launch {
                    repository.addSupabaseExclusionRule(field, trimmed)
                    pattern = ""
                    formError = null
                    onChanged()
                }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add exclusion rule")
        }
        rules.forEach { rule ->
            val label = SupabaseFilterField.fromStorage(rule.matchField)?.label ?: rule.matchField
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = rule.pattern,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            repository.setSupabaseExclusionRuleEnabled(rule.id, checked)
                            onChanged()
                        }
                    },
                    enabled = enabled
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteSupabaseExclusionRule(rule.id)
                            onChanged()
                        }
                    },
                    enabled = enabled
                ) {
                    Text("Remove")
                }
            }
        }
    }
}

private const val MAX_PATTERN_LENGTH = 200
