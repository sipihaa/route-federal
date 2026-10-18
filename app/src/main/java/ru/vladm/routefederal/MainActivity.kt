package ru.vladm.routefederal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.vladm.routefederal.ui.navigation.AppNavigation
import ru.vladm.routefederal.ui.theme.RouteFederalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RouteFederalTheme {
                AppNavigation()
            }
        }
    }
}
