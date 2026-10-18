package app.lumen.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.lumen.tv.ui.LumenRoot
import app.lumen.tv.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LumenTheme {
                LumenRoot()
            }
        }
        window.setBackgroundDrawable(null)
    }
}
