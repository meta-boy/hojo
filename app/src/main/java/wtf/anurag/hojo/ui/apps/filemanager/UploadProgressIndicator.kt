package wtf.anurag.hojo.ui.apps.filemanager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import wtf.anurag.hojo.data.model.UploadProgress

@Composable
fun UploadProgressIndicator(uploadProgress: UploadProgress?, modifier: Modifier = Modifier) {

        AnimatedVisibility(
                visible = uploadProgress != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = modifier
        ) {
                uploadProgress?.let { progress ->
                        Column(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                                .clip(MaterialTheme.shapes.medium)
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .padding(16.dp)
                        ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                        Text(
                                                text = "Uploading...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.weight(1f))

                                        Text(
                                                text = "${progress.progressPercentage}%",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                        progress = { progress.progressPercentage / 100f },
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor =
                                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                )

                                if (progress.transferSpeedBytesPerSecond > 0) {
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                        ) {
                                                Text(
                                                        text = "Speed:",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )

                                                Spacer(modifier = Modifier.width(4.dp))

                                                Text(
                                                        text = progress.transferSpeedFormatted,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.primary
                                                )
                                        }
                                }
                        }
                }
        }
}
