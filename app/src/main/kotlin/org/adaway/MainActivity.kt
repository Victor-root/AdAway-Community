package org.adaway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.adaway.system.isTelevision
import org.adaway.ui.AdAwayApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val isTelevision = isTelevision()
        setContent { AdAwayApp(isTelevision = isTelevision) }
    }
}
