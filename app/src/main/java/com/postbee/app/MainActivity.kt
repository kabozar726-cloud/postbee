package com.postbee.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.postbee.app.ui.AccountsScreen
import com.postbee.app.ui.DarkBg
import com.postbee.app.ui.Gold
import com.postbee.app.ui.HistoryScreen
import com.postbee.app.ui.HomeScreen
import com.postbee.app.ui.UploadScreen

enum class Screen {
    HOME, UPLOAD, ACCOUNTS, HISTORY
}

class MainActivity : ComponentActivity() {

    private val viewModel: PostBeeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OAuth callback — TikTok / Meta login ke baad wapas aane par
        intent?.data?.let { viewModel.handleOAuthCallback(it) }
        setContent {
            PostBeeApp(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { viewModel.handleOAuthCallback(it) }
    }
}

@Composable
fun PostBeeApp(viewModel: PostBeeViewModel) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    // Media permissions — video/thumbnail chune ke liye
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(perms)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            background = DarkBg,
            surface = DarkBg,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF1A1A1A)) {
                    NavigationBarItem(
                        selected = screen == Screen.HOME,
                        onClick = { screen = Screen.HOME },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold)
                    )
                    NavigationBarItem(
                        selected = screen == Screen.UPLOAD,
                        onClick = { screen = Screen.UPLOAD },
                        icon = { Icon(Icons.Filled.CloudUpload, contentDescription = "Upload") },
                        label = { Text("Upload") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold)
                    )
                    NavigationBarItem(
                        selected = screen == Screen.ACCOUNTS,
                        onClick = { screen = Screen.ACCOUNTS },
                        icon = { Icon(Icons.Filled.AccountCircle, contentDescription = "Accounts") },
                        label = { Text("Accounts") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold)
                    )
                    NavigationBarItem(
                        selected = screen == Screen.HISTORY,
                        onClick = { screen = Screen.HISTORY },
                        icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                        label = { Text("History") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold)
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(viewModel) { screen = Screen.UPLOAD }
                    Screen.UPLOAD -> UploadScreen(viewModel)
                    Screen.ACCOUNTS -> AccountsScreen(viewModel)
                    Screen.HISTORY -> HistoryScreen(viewModel)
                }
            }
        }
    }
}
