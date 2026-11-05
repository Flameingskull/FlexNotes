package com.fitlens.companion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fitlens.companion.data.Snapshot

/**
 * Draws real screens behind a real navigation host, wired to [nav] as `AppRoot` wires it, so a test that taps a
 * control can read where it went (`nav.top`) and see that screen drawn. [start] is the first screen. [snap] may change
 * (a test's `show`) without restarting the host.
 */
@Composable
internal fun NavHarness(nav: Nav, snap: Snapshot, start: Screen) {
    val controller = rememberNavController()
    val first = remember { Route(Nav.encode(start)) }
    val current by rememberUpdatedState(snap)
    LaunchedEffect(controller) {
        nav.attach(controller)
        controller.currentBackStackEntryFlow.collect { nav.sync(controller) }
    }
    NavHost(navController = controller, startDestination = first) {
        composable<Route> { entry ->
            val text by remember(entry) {
                entry.savedStateHandle.getStateFlow(Nav.KEY, entry.arguments?.getString(Nav.KEY) ?: "")
            }.collectAsState()
            ScreenContent(current, nav, remember(text) { Nav.decode(text) })
        }
    }
}
