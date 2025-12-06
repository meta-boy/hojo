package wtf.anurag.hojo.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class HojoColors(
    val windowBg: Color,
    val headerBg: Color,
    val sidebarBg: Color,
    val contentBg: Color,
    val text: Color,
    val subText: Color,
    val border: Color,
    val selection: Color,
    val selectionBorder: Color,
    val primary: Color,
    val success: Color,
    val error: Color,
    val danger: Color
)

val LightHojoColors = HojoColors(
    windowBg = Gray100,
    headerBg = Gray200,
    sidebarBg = Gray50,
    contentBg = White,
    text = Gray700,
    subText = Gray400,
    border = Gray300,
    selection = SelectionLight,
    selectionBorder = SelectionBorderLight,
    primary = Emerald500,
    success = Emerald500,
    error = Red500,
    danger = Red500
)

val DarkHojoColors = HojoColors(
    windowBg = Zinc950,
    headerBg = Zinc900,
    sidebarBg = Zinc950,
    contentBg = Zinc950,
    text = Zinc100,
    subText = Zinc400,
    border = Zinc800,
    selection = Zinc900,
    selectionBorder = Zinc800,
    primary = Emerald500,
    success = Emerald500,
    error = Red500,
    danger = Red400
)

val LocalHojoColors = staticCompositionLocalOf { LightHojoColors }

private val DarkColorScheme = darkColorScheme(
    primary = Emerald500,
    secondary = Zinc400,
    tertiary = Emerald500,
    background = Zinc950,
    surface = Zinc950,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Zinc100,
    onSurface = Zinc100,
    error = Red500
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald500,
    secondary = Gray400,
    tertiary = Emerald500,
    background = Gray100,
    surface = White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Gray700,
    onSurface = Gray700,
    error = Red500
)

@Composable
fun HojoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val hojoColors = if (darkTheme) DarkHojoColors else LightHojoColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = hojoColors.headerBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalHojoColors provides hojoColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}

object HojoTheme {
    val colors: HojoColors
        @Composable
        get() = LocalHojoColors.current
}