package com.keltruc.mymemos.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.keltruc.mymemos.ui.detail.MemoDetailScreen
import com.keltruc.mymemos.ui.editor.EditorScreen
import com.keltruc.mymemos.ui.settings.SettingsScreen
import com.keltruc.mymemos.ui.shortcuts.ShortcutsScreen
import com.keltruc.mymemos.ui.signin.SignInScreen
import com.keltruc.mymemos.ui.timeline.TimelineScreen
import kotlinx.serialization.Serializable

@Serializable object SignInRoute
@Serializable object TimelineRoute
@Serializable data class MemoDetailRoute(val localId: String)
@Serializable data class EditorRoute(val localId: String? = null)
@Serializable object SettingsRoute
@Serializable object ShortcutsRoute

@Composable
fun MyMemosNavHost() {
    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val session by sessionViewModel.state.collectAsStateWithLifecycle()

    when (session) {
        SessionState.Loading -> return
        SessionState.SignedOut -> {
            NavHost(navController, startDestination = SignInRoute) {
                composable<SignInRoute> { SignInScreen() }
            }
        }
        is SessionState.SignedIn -> {
            NavHost(navController, startDestination = TimelineRoute) {
                composable<TimelineRoute> {
                    TimelineScreen(
                        onOpenMemo = { navController.navigate(MemoDetailRoute(it)) },
                        onNewMemo = { navController.navigate(EditorRoute()) },
                        onEditMemo = { navController.navigate(EditorRoute(it)) },
                        onSettings = { navController.navigate(SettingsRoute) },
                        onManageShortcuts = { navController.navigate(ShortcutsRoute) },
                    )
                }
                composable<MemoDetailRoute> { entry ->
                    val route = entry.toRoute<MemoDetailRoute>()
                    MemoDetailScreen(
                        localId = route.localId,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(EditorRoute(route.localId)) },
                        onOpenMemo = { navController.navigate(MemoDetailRoute(it)) },
                    )
                }
                composable<EditorRoute> {
                    EditorScreen(onDone = { navController.popBackStack() })
                }
                composable<ShortcutsRoute> {
                    ShortcutsScreen(onBack = { navController.popBackStack() })
                }
                composable<SettingsRoute> {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
