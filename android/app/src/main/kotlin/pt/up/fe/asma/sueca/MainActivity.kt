package pt.up.fe.asma.sueca

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.up.fe.asma.sueca.data.SettingsViewModel
import pt.up.fe.asma.sueca.ui.theme.SuecaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            SuecaTheme {
                // Activity scoped, so every screen sees the same preferences.
                val settings: SettingsViewModel = viewModel()
                SuecaApp(settings)
            }
        }
    }
}
