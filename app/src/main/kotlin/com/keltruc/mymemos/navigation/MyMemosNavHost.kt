package com.keltruc.mymemos.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import com.keltruc.mymemos.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.keltruc.mymemos.ui.detail.MemoDetailScreen
import com.keltruc.mymemos.ui.editor.EditorScreen
import com.keltruc.mymemos.ui.account.NotificationsScreen
import com.keltruc.mymemos.ui.account.StatsScreen
import com.keltruc.mymemos.ui.account.TokensScreen
import com.keltruc.mymemos.ui.account.WebhooksScreen
import com.keltruc.mymemos.ui.admin.AdminInstanceScreen
import com.keltruc.mymemos.ui.admin.AdminUsersScreen
import com.keltruc.mymemos.ui.settings.SettingsNav
import com.keltruc.mymemos.ui.settings.SettingsScreen
import com.keltruc.mymemos.ui.review.ReviewScreen
import com.keltruc.mymemos.ui.tasks.TasksScreen
import com.keltruc.mymemos.ui.tags.TagsScreen
import com.keltruc.mymemos.ui.tags.LocalTagStyles
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.keltruc.mymemos.ui.templates.TemplatesScreen
import com.keltruc.mymemos.ui.data.DataScreen
import com.keltruc.mymemos.ui.shortcuts.ShortcutsScreen
import com.keltruc.mymemos.ui.signin.SignInScreen
import com.keltruc.mymemos.ui.timeline.TimelineScreen
import kotlinx.serialization.Serializable

@Serializable object SignInRoute
@Serializable object TimelineRoute
@Serializable data class MemoDetailRoute(val localId: String)
@Serializable data class EditorRoute(val localId: String? = null, val initialText: String? = null, val initialImages: List<String> = emptyList())
@Serializable object SettingsRoute
@Serializable object ShortcutsRoute
@Serializable object TokensRoute
@Serializable object WebhooksRoute
@Serializable object NotificationsRoute
@Serializable object StatsRoute
@Serializable object AdminUsersRoute
@Serializable object AdminInstanceRoute
@Serializable object ReviewRoute
@Serializable object TasksRoute
@Serializable data class TagsRoute(val tag: String? = null)
@Serializable object TemplatesRoute
@Serializable object DataRoute

@Composable
fun MyMemosNavHost(twoPane: Boolean = false) {
    val navController = rememberNavController()
    var paneMemo by rememberSaveable { mutableStateOf<String?>(null) }
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    val pending by sessionViewModel.pendingDestination.collectAsStateWithLifecycle()

    // Intents (share sheet, widgets, tile) land here once the user is signed in.
    LaunchedEffect(pending, session) {
        val dest = pending ?: return@LaunchedEffect
        if (session !is SessionState.SignedIn) return@LaunchedEffect
        when (dest) {
            is Destination.NewMemo -> navController.navigate(EditorRoute(initialText = dest.text, initialImages = dest.imageUris.map { it.toString() }))
            is Destination.OpenMemo -> navController.navigate(MemoDetailRoute(dest.localId))
        }
        sessionViewModel.consumeDestination()
    }

    when (session) {
        SessionState.Loading -> return
        SessionState.SignedOut -> {
            NavHost(navController, startDestination = SignInRoute) {
                composable<SignInRoute> { SignInScreen() }
            }
        }
        is SessionState.SignedIn -> {
            val tagStyles by sessionViewModel.tagStyles.collectAsStateWithLifecycle()
            val backStack by navController.currentBackStackEntryAsState()
            val destination = backStack?.destination
            val topLevel = destination?.let { d -> d.hasRoute(TimelineRoute::class) || d.hasRoute(TasksRoute::class) || d.hasRoute(ReviewRoute::class) } == true
            fun go(route: Any) = navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            CompositionLocalProvider(LocalTagStyles provides tagStyles) {
            Scaffold(
                bottomBar = {
                    if (topLevel) {
                        NavigationBar {
                            NavigationBarItem(selected = destination?.hasRoute(TimelineRoute::class) == true, onClick = { go(TimelineRoute) }, icon = { Icon(Icons.Default.Notes, null) }, label = { Text(stringResource(R.string.nav_memos)) })
                            NavigationBarItem(selected = destination?.hasRoute(TasksRoute::class) == true, onClick = { go(TasksRoute) }, icon = { Icon(Icons.Default.CheckCircle, null) }, label = { Text(stringResource(R.string.nav_tasks)) })
                            NavigationBarItem(selected = destination?.hasRoute(ReviewRoute::class) == true, onClick = { go(ReviewRoute) }, icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text(stringResource(R.string.nav_review)) })
                        }
                    }
                },
            ) { padding ->
            NavHost(
                navController,
                startDestination = TimelineRoute,
                modifier = Modifier.padding(bottom = if (topLevel) padding.calculateBottomPadding() else 0.dp),
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 8 } },
            ) {
                composable<TimelineRoute> {
                    if (twoPane) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(0.42f)) {
                                TimelineScreen(
                                    onOpenMemo = { paneMemo = it },
                                    onNewMemo = { navController.navigate(EditorRoute()) },
                                    onEditMemo = { navController.navigate(EditorRoute(it)) },
                                    onSettings = { navController.navigate(SettingsRoute) },
                                    onManageShortcuts = { navController.navigate(ShortcutsRoute) },
                                    onNotifications = { navController.navigate(NotificationsRoute) },
                                    onReview = { go(ReviewRoute) },
                                    onTagSettings = { navController.navigate(TagsRoute(it)) },
                                )
                            }
                            VerticalDivider()
                            Box(Modifier.weight(0.58f).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                val id = paneMemo
                                if (id == null) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.pane_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    MemoDetailScreen(
                                        localId = id,
                                        onBack = { paneMemo = null },
                                        onEdit = { navController.navigate(EditorRoute(id)) },
                                        onOpenMemo = { paneMemo = it },
                                        showBack = false,
                                    )
                                }
                            }
                        }
                        return@composable
                    }
                    TimelineScreen(
                        onOpenMemo = { navController.navigate(MemoDetailRoute(it)) },
                        onNewMemo = { navController.navigate(EditorRoute()) },
                        onEditMemo = { navController.navigate(EditorRoute(it)) },
                        onSettings = { navController.navigate(SettingsRoute) },
                        onManageShortcuts = { navController.navigate(ShortcutsRoute) },
                        onNotifications = { navController.navigate(NotificationsRoute) },
                        onReview = { go(ReviewRoute) },
                        onTagSettings = { navController.navigate(TagsRoute(it)) },
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
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        nav = SettingsNav(
                            onTokens = { navController.navigate(TokensRoute) },
                            onWebhooks = { navController.navigate(WebhooksRoute) },
                            onNotifications = { navController.navigate(NotificationsRoute) },
                            onStats = { navController.navigate(StatsRoute) },
                            onAdminUsers = { navController.navigate(AdminUsersRoute) },
                            onAdminInstance = { navController.navigate(AdminInstanceRoute) },
                            onTemplates = { navController.navigate(TemplatesRoute) },
                            onData = { navController.navigate(DataRoute) },
                            onTags = { navController.navigate(TagsRoute()) },
                        ),
                    )
                }
                composable<TokensRoute> { TokensScreen(onBack = { navController.popBackStack() }) }
                composable<WebhooksRoute> { WebhooksScreen(onBack = { navController.popBackStack() }) }
                composable<StatsRoute> { StatsScreen(onBack = { navController.popBackStack() }) }
                composable<NotificationsRoute> {
                    NotificationsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenMemo = { remoteName -> sessionViewModel.resolveMemo(remoteName) { navController.navigate(MemoDetailRoute(it)) } },
                    )
                }
                composable<AdminUsersRoute> {
                    AdminUsersScreen(serverUrl = (session as SessionState.SignedIn).account.serverUrl, onBack = { navController.popBackStack() })
                }
                composable<ReviewRoute> {
                    ReviewScreen(
                        onBack = { navController.popBackStack() },
                        onOpenMemo = { navController.navigate(MemoDetailRoute(it)) },
                        onEditMemo = { navController.navigate(EditorRoute(it)) },
                    )
                }
                composable<TemplatesRoute> { TemplatesScreen(onBack = { navController.popBackStack() }) }
                composable<DataRoute> { DataScreen(onBack = { navController.popBackStack() }) }
                composable<AdminInstanceRoute> { AdminInstanceScreen(onBack = { navController.popBackStack() }) }
                composable<TasksRoute> { TasksScreen(onOpenMemo = { navController.navigate(MemoDetailRoute(it)) }) }
                composable<TagsRoute> { entry -> TagsScreen(onBack = { navController.popBackStack() }, initialTag = entry.toRoute<TagsRoute>().tag) }
            }
            }
            }
        }
    }
}
