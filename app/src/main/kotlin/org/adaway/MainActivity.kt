package org.adaway

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow
import org.adaway.system.isTelevision
import org.adaway.system.withAppLanguage
import org.adaway.ui.AdAwayApp
import org.adaway.ui.Destination

class MainActivity : ComponentActivity() {
    /** The destination the activity was opened with, until its screen is shown. */
    private val destination = MutableStateFlow<Destination?>(null)

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(newBase.withAppLanguage())

    override fun onCreate(savedInstanceState: Bundle?) {
        // Each screen draws what is under the navigation bar, without the veil Android adds by default.
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) destination.value = Destination.from(intent)
        val isTelevision = isTelevision()
        setContent { AdAwayApp(isTelevision = isTelevision, destination = destination) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        destination.value = Destination.from(intent)
    }
}
