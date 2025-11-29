package wtf.anurag.hojo.ui.apps.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(colors.headerBg)
                                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                                    .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        text = title,
                        color = colors.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp),
                        textAlign = TextAlign.Center
                )

                BasicTextField(
                        value = value,
                        onValueChange = onChangeText,
                        textStyle = TextStyle(color = colors.text, fontSize = 16.sp),
                        cursorBrush = SolidColor(colors.primary),
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.windowBg)
                                        .padding(16.dp)
                                        .focusRequester(focusRequester),
                        decorationBox = { innerTextField ->
                            if (value.isEmpty()) {
                                Text(text = placeholder, color = colors.subText, fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                )

                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                            modifier =
                                    Modifier.weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(onClick = onClose)
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
                                            .background(colors.primary)
                                            .clickable(onClick = onSubmit)
                                            .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        Text(
                                text = submitLabel,
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
