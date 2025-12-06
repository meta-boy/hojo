package wtf.anurag.hojo.ui.apps.quicklink

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import wtf.anurag.hojo.ui.theme.HojoTheme

@Composable
fun QuickLinkModal(
        visible: Boolean,
        url: String,
        converting: Boolean,
        errorMessage: String?,
        onClose: () -> Unit,
        onChangeUrl: (String) -> Unit,
        onSubmit: () -> Unit,
        onDismissError: () -> Unit
) {
        if (visible) {
                val colors = HojoTheme.colors

                Dialog(onDismissRequest = onClose) {
                        Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                                Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                        Box(
                                                modifier =
                                                        Modifier.padding(bottom = 16.dp)
                                                                .size(56.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Link,
                                                        contentDescription = null,
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }

                                        Text(
                                                text = "New Quick Link",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.headlineSmall,
                                                modifier = Modifier.padding(bottom = 8.dp),
                                                textAlign = TextAlign.Center
                                        )

                                        Text(
                                                text =
                                                        "Paste a URL below to convert and send it to your device instantly.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(bottom = 24.dp)
                                        )

                                        OutlinedTextField(
                                                value = url,
                                                onValueChange = onChangeUrl,
                                                placeholder = { Text("https://...") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                keyboardOptions =
                                                        KeyboardOptions(
                                                                keyboardType = KeyboardType.Uri
                                                        ),
                                                shape = MaterialTheme.shapes.medium
                                        )

                                        // Error message display
                                        if (errorMessage != null) {
                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(top = 12.dp)
                                                                        .clip(
                                                                                MaterialTheme.shapes
                                                                                        .small
                                                                        )
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .errorContainer
                                                                        )
                                                                        .clickable(
                                                                                onClick =
                                                                                        onDismissError
                                                                        )
                                                                        .padding(12.dp)
                                                ) {
                                                        Text(
                                                                text = errorMessage,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onErrorContainer,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall
                                                        )
                                                }
                                        }

                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(top = 24.dp),
                                                horizontalArrangement = Arrangement.End
                                        ) {
                                                TextButton(
                                                        onClick = onClose,
                                                        enabled = !converting
                                                ) { Text("Cancel") }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Button(
                                                        onClick = onSubmit,
                                                        enabled = !converting && url.isNotEmpty()
                                                ) {
                                                        if (converting) {
                                                                CircularProgressIndicator(
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onPrimary,
                                                                        modifier =
                                                                                Modifier.size(
                                                                                        20.dp
                                                                                ),
                                                                        strokeWidth = 2.dp
                                                                )
                                                        } else {
                                                                Text("Send Link")
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
}
