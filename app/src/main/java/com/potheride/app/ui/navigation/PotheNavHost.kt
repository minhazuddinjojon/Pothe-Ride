package com.potheride.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.potheride.app.core.geo.LatLng
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.screens.ActivityScreen
import com.potheride.app.ui.screens.AdminScreen
import com.potheride.app.ui.screens.AuthScreen
import com.potheride.app.ui.screens.BookingConfirmScreen
import com.potheride.app.ui.screens.ChatScreen
import com.potheride.app.ui.screens.ChatsScreen
import com.potheride.app.ui.screens.DriverCreateTripScreen
import com.potheride.app.ui.screens.DriverEarningsScreen
import com.potheride.app.ui.screens.DriverLiveScreen
import com.potheride.app.ui.screens.DriverRegistrationScreen
import com.potheride.app.ui.screens.FaceVerificationScreen
import com.potheride.app.ui.screens.HomeScreen
import com.potheride.app.ui.screens.NotificationsScreen
import com.potheride.app.ui.screens.VerificationStatusScreen
import com.potheride.app.ui.screens.PassengerResultsScreen
import com.potheride.app.ui.screens.PassengerSearchScreen
import com.potheride.app.ui.screens.ProfileScreen
import com.potheride.app.ui.screens.RateScreen
import com.potheride.app.ui.screens.RideStatusScreen
import com.potheride.app.ui.screens.RoutePreviewScreen
import com.potheride.app.ui.theme.LocalStrings

/**
 * The whole navigation graph plus the persistent bottom bar.
 *
 * Search parameters (the two addresses and the seat count) are held here rather than
 * passed as route arguments: they are needed by three consecutive screens, and
 * encoding free-text addresses into a route string is a reliable source of crashes
 * the first time someone types a slash.
 */
@Composable
fun PotheNavHost(
    vm: PotheRideViewModel,
    onRequestLocationPermission: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val snackbarHost = remember { SnackbarHostState() }

    var pickupAddress by remember { mutableStateOf("") }
    var dropAddress by remember { mutableStateOf("") }
    var seats by remember { mutableStateOf(1) }
    var chatBookingId by remember { mutableStateOf("") }
    var rateBookingId by remember { mutableStateOf("") }
    var rateeId by remember { mutableStateOf("") }
    var prefillPickup by remember { mutableStateOf<Pair<String, LatLng>?>(null) }

    // One-shot messages from the ViewModel.
    LaunchedEffect(state.toast?.id) {
        state.toast?.let {
            snackbarHost.showSnackbar(it.message)
            vm.consumeToast()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = state.isLoggedIn && currentRoute in Routes.bottomBarRoutes

    fun switchTab(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomDestination.values().forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { switchTab(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label(strings)) },
                            colors = NavigationBarItemDefaults.colors()
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = Routes.AUTH
            ) {
                composable(Routes.AUTH) {
                    AuthScreen(vm) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    }
                }

                composable(Routes.HOME) {
                    HomeScreen(
                        vm = vm,
                        onSearch = { prefillPickup = null; switchTab(Routes.SEARCH) },
                        onPublish = { navController.navigate(Routes.DRIVER_CREATE) },
                        onDashboard = {
                            navController.navigate(
                                if (state.mode == com.potheride.app.ui.AppMode.DRIVER) Routes.EARNINGS
                                else Routes.ACTIVITY
                            )
                        },
                        onActiveRide = { navController.navigate(Routes.RIDE_STATUS) },
                        onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                        onPlaceSelected = { place ->
                            prefillPickup = place.address to place.point
                            switchTab(Routes.SEARCH)
                        }
                    )
                }

                composable(Routes.SEARCH) {
                    PassengerSearchScreen(
                        vm = vm,
                        initialPickup = prefillPickup,
                        onResults = { pickup, drop, seatCount ->
                            pickupAddress = pickup
                            dropAddress = drop
                            seats = seatCount
                            navController.navigate(Routes.RESULTS)
                        }
                    )
                }

                composable(Routes.RESULTS) {
                    PassengerResultsScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onSelect = { navController.navigate(Routes.ROUTE_PREVIEW) }
                    )
                }

                composable(Routes.ROUTE_PREVIEW) {
                    RoutePreviewScreen(
                        vm = vm,
                        pickupAddress = pickupAddress,
                        dropAddress = dropAddress,
                        onBack = { navController.popBackStack() },
                        onRequest = { navController.navigate(Routes.BOOKING_CONFIRM) }
                    )
                }

                composable(Routes.BOOKING_CONFIRM) {
                    BookingConfirmScreen(
                        vm = vm,
                        pickupAddress = pickupAddress,
                        dropAddress = dropAddress,
                        seats = seats,
                        onBack = { navController.popBackStack() },
                        onSent = {
                            navController.navigate(Routes.RIDE_STATUS) {
                                popUpTo(Routes.HOME)
                            }
                        }
                    )
                }

                composable(Routes.RIDE_STATUS) {
                    RideStatusScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onChat = { id ->
                            chatBookingId = id
                            navController.navigate(Routes.CHAT)
                        },
                        onRate = { bookingId, driverUserId ->
                            rateBookingId = bookingId
                            rateeId = driverUserId
                            navController.navigate(Routes.RATE)
                        },
                        onFindRide = { switchTab(Routes.SEARCH) }
                    )
                }

                composable(Routes.CHAT) {
                    ChatScreen(vm, chatBookingId) { navController.popBackStack() }
                }

                composable(Routes.RATE) {
                    RateScreen(
                        vm = vm,
                        bookingId = rateBookingId,
                        rateeId = rateeId,
                        onBack = { navController.popBackStack() },
                        onDone = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.DRIVER_CREATE) {
                    DriverCreateTripScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onPublished = {
                            navController.navigate(Routes.DRIVER_LIVE) {
                                popUpTo(Routes.HOME)
                            }
                        }
                    )
                }

                composable(Routes.DRIVER_LIVE) {
                    DriverLiveScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onRequestPermission = onRequestLocationPermission
                    )
                }

                composable(Routes.EARNINGS) {
                    DriverEarningsScreen(vm) { navController.popBackStack() }
                }

                composable(Routes.ACTIVITY) {
                    ActivityScreen(
                        vm = vm,
                        onOpenRide = { navController.navigate(Routes.RIDE_STATUS) },
                        onFindRide = { switchTab(Routes.SEARCH) }
                    )
                }

                composable(Routes.PROFILE) {
                    ProfileScreen(
                        vm = vm,
                        onAdmin = { navController.navigate(Routes.ADMIN) },
                        onSignedOut = {
                            navController.navigate(Routes.AUTH) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen(vm) { navController.popBackStack() }
                }

                composable(Routes.ADMIN) {
                    AdminScreen(vm) { navController.popBackStack() }
                }

                composable(Routes.CHATS) {
                    ChatsScreen(
                        vm = vm,
                        onOpenChat = { id ->
                            chatBookingId = id
                            navController.navigate(Routes.CHAT)
                        },
                        onFindRide = { switchTab(Routes.SEARCH) }
                    )
                }

                composable(Routes.DRIVER_REGISTRATION) {
                    DriverRegistrationScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onPickDocument = { _, onPicked ->
                            // Wired to a real file picker at the Activity layer once
                            // Storage upload exists — see docs/upgrade/deps-verification.md.
                            onPicked("local://placeholder")
                        },
                        onSubmitted = { navController.navigate(Routes.VERIFICATION_STATUS) }
                    )
                }

                composable(Routes.VERIFICATION_STATUS) {
                    VerificationStatusScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onReupload = { navController.navigate(Routes.DRIVER_REGISTRATION) }
                    )
                }

                composable(Routes.FACE_VERIFICATION) {
                    FaceVerificationScreen(
                        onBack = { navController.popBackStack() },
                        onVerified = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

/** The four tabs from wireframe 2's bottom bar. */
private enum class BottomDestination(
    val route: String,
    val icon: ImageVector
) {
    HOME(Routes.HOME, Icons.Default.Home),
    ACTIVITY(Routes.ACTIVITY, Icons.Default.DirectionsCar),
    CHAT(Routes.CHATS, Icons.Default.ChatBubbleOutline),
    PROFILE(Routes.PROFILE, Icons.Default.Person);

    fun label(strings: com.potheride.app.core.i18n.Strings): String = when (this) {
        HOME -> strings.navHome
        ACTIVITY -> strings.navActivity
        CHAT -> strings.navChat
        PROFILE -> strings.navProfile
    }
}
