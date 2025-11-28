package wtf.anurag.hojo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import wtf.anurag.hojo.ui.MainScreen
import wtf.anurag.hojo.ui.theme.HojoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HojoTheme { MainScreen() } }
    }
}
