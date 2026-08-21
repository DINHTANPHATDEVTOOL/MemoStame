package com.mipastudio.memostamp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mipastudio.memostamp.core.theme.MemoStampTheme
import com.mipastudio.memostamp.navigation.MemoStampNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemoStampTheme {
                MemoStampNavGraph()
            }
        }
    }
}