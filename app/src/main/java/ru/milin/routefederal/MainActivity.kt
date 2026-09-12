package ru.milin.routefederal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import ru.milin.routefederal.ui.RouteApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.BLACK)
        )
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF006A62), secondary = Color(0xFF506A73),
                primaryContainer = Color(0xFFBDEDE0), secondaryContainer = Color(0xFFDCEDE6),
                surfaceContainerHighest = Color(0xFFE7EEEA),
                background = Color(0xFFF5F7F8), surface = Color(0xFFF5F7F8)
            )) { RouteApp() }
        }
    }
}
