package wtf.anurag.hojo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ConnectivityBottomBar(isConnected: Boolean, modifier: Modifier = Modifier) {
    Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        // Add safe area padding for navigation bar
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

        Row(
                modifier =
                        Modifier.padding(navBarPadding)
                                .height(56.dp) // Standard height, adjustable
                                .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
        ) {
            // Status Dot
            Box(
                    modifier =
                            Modifier.size(8.dp)
                                    .background(
                                            color = if (isConnected) Color.Green else Color.Red,
                                            shape = CircleShape
                                    )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Status Text
            Text(
                    text = if (isConnected) "Connected" else "Not connected",
                    style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
