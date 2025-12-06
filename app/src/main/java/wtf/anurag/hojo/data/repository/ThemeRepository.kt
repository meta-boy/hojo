package wtf.anurag.hojo.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Singleton
class ThemeRepository @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences =
            context.getSharedPreferences("hojo_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getStoredThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    private fun getStoredThemeMode(): ThemeMode {
        val modeName = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }
}
