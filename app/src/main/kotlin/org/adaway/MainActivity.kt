package org.adaway

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow
import org.adaway.system.isTelevision
import org.adaway.ui.AdAwayApp
import org.adaway.ui.Destination

class MainActivity : ComponentActivity() {
    /** The destination the activity was opened with, until its screen is shown. */
    private val destination = MutableStateFlow<Destination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
