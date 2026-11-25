package com.rarodoce.sf2player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rarodoce.sf2player.ui.AppTheme
import com.rarodoce.sf2player.ui.MainScreen
import com.rarodoce.sf2player.ui.SplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // tela sempre acesa e em tela cheia: mais espaço para a mesa
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            AppTheme {
                // a abertura aparece só ao abrir o app, não ao girar a tela
                var splash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                Box {
                    MainScreen(vm)
                    if (splash) SplashScreen(onDone = { splash = false })
                }
            }
        }
    }
}
