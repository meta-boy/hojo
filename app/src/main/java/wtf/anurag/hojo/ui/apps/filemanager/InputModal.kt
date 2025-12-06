package wtf.anurag.hojo.ui.apps.filemanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import wtf.anurag.hojo.ui.theme.HojoTheme

@Composable
fun InputModal(
        visible: Boolean,
        title: String,
        value: String,
        placeholder: String,
        submitLabel: String,
        onClose: () -> Unit,
        onChangeText: (String) -> Unit,
        onSubmit: () -> Unit
) {
        if (visible) {
                val colors = HojoTheme.colors
                val focusRequester = remember { FocusRequester() }

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
                                        Text(
                                                text = title,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.headlineSmall,
                                                modifier = Modifier.padding(bottom = 24.dp),
                                                textAlign = TextAlign.Center
                                        )

                                        OutlinedTextField(
                                                value = value,
                                                onValueChange = onChangeText,
                                                placeholder = { Text(placeholder) },
                                                singleLine = true,
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .focusRequester(focusRequester),
                                                shape = MaterialTheme.shapes.medium
                                        )

                                        LaunchedEffect(Unit) { focusRequester.requestFocus() }

                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(top = 24.dp),
                                                horizontalArrangement = Arrangement.End
                                        ) {
                                                TextButton(onClick = onClose) { Text("Cancel") }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Button(onClick = onSubmit) { Text(submitLabel) }
                                        }
                                }
                        }
                }
        }
}
