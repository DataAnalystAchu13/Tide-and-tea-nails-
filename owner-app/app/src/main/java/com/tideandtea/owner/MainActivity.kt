package com.tideandtea.owner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tideandtea.owner.ui.screens.BookingDetailScreen
import com.tideandtea.owner.ui.screens.LoginScreen
import com.tideandtea.owner.ui.screens.RequestsScreen
import com.tideandtea.owner.ui.screens.SplashScreen
import com.tideandtea.owner.ui.theme.TideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { TideTheme { OwnerApp() } }
    }
}

@Composable
fun OwnerApp(vm: OwnerViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    when (vm.authState) {
        AuthState.Checking -> SplashScreen()
        AuthState.SignedOut -> LoginScreen(vm.busy, vm.loginError, vm::signIn)
        AuthState.SignedIn -> {
            val bookings by vm.bookings.collectAsStateWithLifecycle()
            val nav = rememberNavController()
            NavHost(nav, startDestination = "list") {
                composable("list") {
                    RequestsScreen(
                        bookings = bookings,
                        snackbar = snackbar,
                        ownerEmail = vm.ownerEmail,
                        onOpen = { nav.navigate("booking/${it.id}") },
                        onPublishPrices = { vm.publishPriceList() },
                        onSignOut = vm::signOut,
                    )
                }
                composable("booking/{id}") { entry ->
                    val id = entry.arguments?.getString("id")
                    BookingDetailScreen(
                        booking = bookings?.firstOrNull { it.id == id },
                        vm = vm,
                        snackbar = snackbar,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
