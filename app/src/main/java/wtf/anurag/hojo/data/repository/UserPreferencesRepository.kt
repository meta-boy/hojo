package wtf.anurag.hojo.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class UserPreferencesRepository @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences =
            context.getSharedPreferences("hojo_user_prefs", Context.MODE_PRIVATE)

    private val _isGridLayout = MutableStateFlow(prefs.getBoolean("is_grid_layout", true))
    val isGridLayout: StateFlow<Boolean> = _isGridLayout.asStateFlow()

    fun setGridLayout(isGrid: Boolean) {
        prefs.edit().putBoolean("is_grid_layout", isGrid).apply()
        _isGridLayout.value = isGrid
    }
}
