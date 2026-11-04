package com.tideandtea.owner

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tideandtea.owner.data.BookingStatus
import com.tideandtea.owner.shop.ShopViewModel
import com.tideandtea.owner.shop.shopGraph
import com.tideandtea.owner.ui.screens.BookingDetailScreen
import com.tideandtea.owner.ui.screens.InsightsScreen
import com.tideandtea.owner.ui.screens.LoginScreen
import com.tideandtea.owner.ui.screens.PaymentsScreen
import com.tideandtea.owner.ui.screens.RequestsScreen
import com.tideandtea.owner.ui.screens.SplashScreen
import com.tideandtea.owner.ui.theme.Pink
import com.tideandtea.owner.ui.theme.Plum
import com.tideandtea.owner.ui.theme.TideTheme

class MainActivity : ComponentActivity() {
    private val vm: OwnerViewModel by viewModels()
    private val shop: ShopViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        handle(intent)
        setContent { TideTheme { OwnerApp(vm, shop) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Opens the right screen when the app is launched from a notification. */
    private fun handle(intent: Intent?) {
        val id = intent?.getStringExtra(TideMessagingService.EXTRA_BOOKING) ?: return
        val type = intent.getStringExtra(TideMessagingService.EXTRA_TYPE) ?: "booking"
        vm.pendingOpen = "$type:$id"
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("list", "Requests", Icons.Outlined.Inbox),
    Tab("orders", "Orders", Icons.Outlined.ShoppingBag),
    Tab("website", "Website", Icons.Outlined.Language),
    Tab("insights", "Insights", Icons.Outlined.Insights),
)

@Composable
fun OwnerApp(vm: OwnerViewModel, shop: ShopViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    LaunchedEffect(shop.message) {
        shop.message?.let { snackbar.showSnackbar(it); shop.message = null }
    }
    // The shop data loads only for a signed-in owner (row level security hides it otherwise).
    LaunchedEffect(vm.authState) {
        if (vm.authState == AuthState.SignedIn) shop.start() else shop.stop()
    }
    when (vm.authState) {
        AuthState.Checking -> SplashScreen()
        AuthState.SignedOut -> LoginScreen(vm.busy, vm.loginError, vm::signIn)
        AuthState.SignedIn -> SignedInApp(vm, shop, snackbar)
    }
}

@Composable
private fun SignedInApp(vm: OwnerViewModel, shop: ShopViewModel, snackbar: SnackbarHostState) {
    val bookings by vm.bookings.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    val orders by shop.orders.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    // Ask once for permission to show notifications (Android 13+).
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(vm.pendingOpen) {
        val target = vm.pendingOpen ?: return@LaunchedEffect
        val (type, id) = target.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        nav.navigate(when (type) { "order" -> "order/$id"; else -> "booking/$id" })
        vm.pendingOpen = null
    }

    val newCount = bookings?.count { it.status == BookingStatus.REQUESTED || it.status == BookingStatus.PAYMENT_SUBMITTED || it.refundDue } ?: 0
    val ordersToDo = orders?.count { it.needsAction } ?: 0

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (route in TABS.map { it.route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEach { t ->
                        val badge = when (t.route) { "list" -> newCount; "orders" -> ordersToDo; "website" -> if (shop.unpublished) 1 else 0; else -> 0 }
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                BadgedBox(badge = { if (badge > 0) if (t.route == "website") Badge(containerColor = Pink) else Badge(containerColor = Pink, contentColor = Plum) { Text("$badge") } }) {
                                    Icon(t.icon, null)
                                }
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "list", modifier = Modifier.padding(pad)) {
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
            composable("insights") { InsightsScreen(bookings, payments) { nav.navigate("payments") } }
            composable("payments") {
                PaymentsScreen(payments, onBack = { nav.popBackStack() }) { p ->
                    when {
                        p.orderId.isNotBlank() -> nav.navigate("order/${p.orderId}")
                        p.bookingId.isNotBlank() -> nav.navigate("booking/${p.bookingId}")
                    }
                }
            }
            shopGraph(nav, shop, snackbar)
            composable("booking/{id}") { e ->
                val id = e.arguments?.getString("id")
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
