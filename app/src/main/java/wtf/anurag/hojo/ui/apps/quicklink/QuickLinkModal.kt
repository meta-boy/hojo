package wtf.anurag.hojo.ui.apps.quicklink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(colors.headerBg)
                                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                                    .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                        modifier =
                                Modifier.padding(bottom = 16.dp)
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x0DFFFFFF)),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                        text = "New Quick Link",
                        color = colors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                )

                Text(
                        text = "Paste a URL below to convert and send it to your device instantly.",
                        color = colors.subText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                )

                BasicTextField(
                        value = url,
                        onValueChange = onChangeUrl,
                        textStyle = TextStyle(color = colors.text, fontSize = 16.sp),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.windowBg)
                                        .padding(16.dp),
                        decorationBox = { innerTextField ->
                            if (url.isEmpty()) {
                                Text(text = "https://...", color = colors.subText, fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                )

                // Error message display
                if (errorMessage != null) {
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(top = 12.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x1FFF5252))
                                            .border(1.dp, Color(0x3FFF5252), RoundedCornerShape(8.dp))
                                            .clickable(onClick = onDismissError)
                                            .padding(12.dp)
                    ) {
                        Text(
                                text = errorMessage,
                                color = Color(0xFFFF8A80),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                        )
                    }
                }

                Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                            modifier =
                                    Modifier.weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(onClick = onClose, enabled = !converting)
                                            .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        Text(
                                text = "Cancel",
                                color = colors.subText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                            modifier =
                                    Modifier.weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                    colors.primary.copy(
                                                            alpha = if (converting) 0.7f else 1f
                                                    )
                                            )
                                            .clickable(
                                                    onClick = onSubmit,
                                                    enabled = !converting && url.isNotEmpty()
                                            )
                                            .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        if (converting) {
                            CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                    text = "Send Link",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
