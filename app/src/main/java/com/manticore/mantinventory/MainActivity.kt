package com.manticore.mantinventory

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.manticore.mantinventory.ui.AppViewModel
import com.manticore.mantinventory.ui.AppViewModelFactory
import com.manticore.mantinventory.ui.screens.MantinventoryApp
import com.manticore.mantinventory.ui.theme.MantinventoryTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels {
        AppViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleDeepLink(intent?.data)

        setContent {
            MantinventoryTheme {
                MantinventoryApp(viewModel = appViewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent.data)
    }

    private fun handleDeepLink(uri: Uri?) {
        appViewModel.consumeDeepLink(uri)
    }
}
