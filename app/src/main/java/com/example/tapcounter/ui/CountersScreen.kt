package com.example.tapcounter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.counter.CounterProfile
import com.example.tapcounter.counter.CounterProfiles

/** Several counters: each has its own count, history, name and its own secret code on the calculator. */
@Composable
fun CountersScreen(app: TapCounterApp, onBack: () -> Unit) {
    val store = app.counterStore
    val counters by store.counters.collectAsStateWithLifecycle()
    val activeId by store.activeId.collectAsStateWithLifecycle()

    var newName by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<CounterProfile?>(null) }
    var recoding by remember { mutableStateOf<CounterProfile?>(null) }
    var deleting by remember { mutableStateOf<CounterProfile?>(null) }

    ScreenPage("Counters", onBack) {
        Text(
            "Each counter keeps its own count, history, name and picture. On the calculator, typing a counter's code and pressing = opens THAT counter. " +
                "Face unlock and the phone biometric open the counter that was open last.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        counters.forEach { c ->
            CounterCard(
                profile = c,
                active = c.id == activeId,
                onOpen = { store.switchTo(c.id) },
                onRename = { renaming = c },
                onCode = { recoding = c },
                onDelete = { deleting = c },
            )
        }

        SectionCard("Add a counter", "Pick a name and its own secret code (4 to 8 digits, different from the others).") {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Name, e.g. Hanuman Chalisa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newCode,
                onValueChange = { newCode = it.filter(Char::isDigit).take(CounterProfiles.MAX_CODE); addError = null },
                label = { Text("Secret code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            addError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = {
                val error = store.addCounter(newName, newCode)
                addError = error
                if (error == null) {
                    newName = ""
                    newCode = ""
                }
            }) { Text("Add counter") }
        }
    }

    renaming?.let { c ->
        TextPromptDialog("Rename counter", c.name, "Name", numeric = false, onConfirm = { text ->
            store.renameCounter(c.id, text)
            null
        }, onDismiss = { renaming = null })
    }
    recoding?.let { c ->
        TextPromptDialog("New secret code", "", "4 to 8 digits", numeric = true, onConfirm = { text ->
            store.setCounterCode(c.id, text)
        }, onDismiss = { recoding = null })
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${c.name}\"?") },
            text = { Text("Its count, history, reset log and picture are deleted for good. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { store.deleteCounter(c.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CounterCard(
    profile: CounterProfile,
    active: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onCode: () -> Unit,
    onDelete: () -> Unit,
) {
    var showCode by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (active) Pill("OPEN", Color(0xFF2E7D32))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Code: " + if (showCode) profile.code else "••••", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showCode = !showCode }) { Text(if (showCode) "Hide" else "Show") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!active) Button(onClick = onOpen) { Text("Open") }
                OutlinedButton(onClick = onRename) { Text("Rename") }
                OutlinedButton(onClick = onCode) { Text("Code") }
            }
            if (profile.id != CounterProfiles.MAIN_ID) {
                TextButton(onClick = onDelete) { Text("Delete this counter", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** One text field in a dialog. [onConfirm] returns an error message to show, or null when done. */
@Composable
private fun TextPromptDialog(
    title: String,
    initial: String,
    label: String,
    numeric: Boolean,
    onConfirm: (String) -> String?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = if (numeric) it.filter(Char::isDigit).take(CounterProfiles.MAX_CODE) else it; error = null },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Text),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = onConfirm(text)
                if (e == null) onDismiss() else error = e
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
