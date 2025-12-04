package wtf.anurag.hojo.ui.apps.converter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Small intermediate preview shown before opening the full `XtcPreviewScreen`.
 * Shows file name/size and actions to open the full preview, upload or save.
 */
@Composable
fun XtcPreviewIntermediate(
    file: File,
    onBack: () -> Unit,
    onUpload: () -> Unit,
    onSaveToDownloads: () -> Unit,
    isSaved: Boolean
) {
    val showFull = remember { mutableStateOf(false) }

    if (showFull.value) {
        // Show the existing full preview screen; when it requests back, return to this intermediate view.
        XtcPreviewScreen(
            file = file,
            onBack = {
                // close full preview and return to intermediate
                showFull.value = false
            },
            onUpload = onUpload,
            onSaveToDownloads = onSaveToDownloads,
            isSaved = isSaved
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Preview file")
        Text(text = "Name: ${file.name}")
        Text(text = "Size: ${file.length()} bytes")

        // Primary actions grouped together
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { showFull.value = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Open preview")
            }

            Button(
                onClick = onUpload,
                modifier = Modifier.weight(1f)
            ) {
                Text("Upload")
            }
        }

        // Secondary actions grouped together
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onSaveToDownloads,
                enabled = !isSaved,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isSaved) "Saved" else "Save to Downloads")
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
        }
    }
}
