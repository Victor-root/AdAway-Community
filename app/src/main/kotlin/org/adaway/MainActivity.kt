package org.adaway

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow
import org.adaway.system.isTelevision
import org.adaway.ui.AdAwayApp
import org.adaway.ui.Shortcut

class MainActivity : ComponentActivity() {
    /** The shortcut the activity was opened with, until its screen is shown. */
    private val shortcut = MutableStateFlow<Shortcut?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) shortcut.value = Shortcut.from(intent)
        val isTelevision = isTelevision()
        setContent { AdAwayApp(isTelevision = isTelevision, shortcut = shortcut) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shortcut.value = Shortcut.from(intent)
    }
}
