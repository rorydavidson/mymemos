package com.keltruc.mymemos.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
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

/** How the top-level screens share the window with an open memo. */
enum class PaneLayout {
    Single,
    // An unfolded phone: split down the middle, which is where the hinge is.
    EvenSplit,
    WideDetail,
}

@Composable
fun MyMemosNavHost(paneLayout: PaneLayout = PaneLayout.Single) {
    val twoPane = paneLayout != PaneLayout.Single
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
            val topLevel = destination.isTopLevel()
            fun go(route: Any) = navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            val openMemo: (String) -> Unit = { if (twoPane) paneMemo = it else navController.navigate(MemoDetailRoute(it)) }

            // Folding or unfolding keeps the open memo in front: it moves between the right
            // pane and its own screen rather than being dropped.
            LaunchedEffect(twoPane) {
                val current = navController.currentBackStackEntry
                val pane = paneMemo
                if (!twoPane && pane != null) {
                    paneMemo = null
                    navController.navigate(MemoDetailRoute(pane))
                } else if (twoPane && current?.destination?.hasRoute(MemoDetailRoute::class) == true &&
                    navController.previousBackStackEntry?.destination.isTopLevel()
                ) {
                    paneMemo = current.toRoute<MemoDetailRoute>().localId
                    navController.popBackStack()
                }
            }

            val navBar: @Composable () -> Unit = {
                NavigationBar {
                    NavigationBarItem(selected = destination?.hasRoute(TimelineRoute::class) == true, onClick = { go(TimelineRoute) }, icon = { Icon(Icons.Default.Notes, null) }, label = { Text(stringResource(R.string.nav_memos)) })
                    NavigationBarItem(selected = destination?.hasRoute(TasksRoute::class) == true, onClick = { go(TasksRoute) }, icon = { Icon(Icons.Default.CheckCircle, null) }, label = { Text(stringResource(R.string.nav_tasks)) })
                    NavigationBarItem(selected = destination?.hasRoute(ReviewRoute::class) == true, onClick = { go(ReviewRoute) }, icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text(stringResource(R.string.nav_review)) })
                }
            }
            // In two panes the bar sits under the left pane only, so the memo gets the full height.
            val barUnderWindow = topLevel && !twoPane
            val topLevelPanes: @Composable (@Composable () -> Unit) -> Unit = { list ->
                if (twoPane) {
                    ListDetailPanes(
                        listWeight = if (paneLayout == PaneLayout.EvenSplit) 0.5f else 0.42f,
                        navBar = navBar,
                        list = list,
                    ) {
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
                } else {
                    list()
                }
            }
            CompositionLocalProvider(LocalTagStyles provides tagStyles) {
            Scaffold(
                bottomBar = { if (barUnderWindow) navBar() },
            ) { padding ->
            NavHost(
                navController,
                startDestination = TimelineRoute,
                modifier = Modifier.padding(bottom = if (barUnderWindow) padding.calculateBottomPadding() else 0.dp),
                // Between tabs in two panes the bar and the open memo stay put, so nothing slides.
                enterTransition = { if (twoPane && betweenTopLevel()) fadeIn(tween(220)) else fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { if (twoPane && betweenTopLevel()) fadeOut(tween(160)) else fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 8 } },
            ) {
                composable<TimelineRoute> {
                    topLevelPanes {
                        TimelineScreen(
                            onOpenMemo = openMemo,
                            onNewMemo = { navController.navigate(EditorRoute()) },
                            onEditMemo = { navController.navigate(EditorRoute(it)) },
                            onSettings = { navController.navigate(SettingsRoute) },
                            onManageShortcuts = { navController.navigate(ShortcutsRoute) },
                            onNotifications = { navController.navigate(NotificationsRoute) },
                            onReview = { go(ReviewRoute) },
                            onTagSettings = { navController.navigate(TagsRoute(it)) },
                        )
                    }
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
                    topLevelPanes {
                        ReviewScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMemo = openMemo,
                            onEditMemo = { navController.navigate(EditorRoute(it)) },
                        )
                    }
                }
                composable<TemplatesRoute> { TemplatesScreen(onBack = { navController.popBackStack() }) }
                composable<DataRoute> { DataScreen(onBack = { navController.popBackStack() }) }
                composable<AdminInstanceRoute> { AdminInstanceScreen(onBack = { navController.popBackStack() }) }
                composable<TasksRoute> { topLevelPanes { TasksScreen(onOpenMemo = openMemo) } }
                composable<TagsRoute> { entry -> TagsScreen(onBack = { navController.popBackStack() }, initialTag = entry.toRoute<TagsRoute>().tag) }
            }
            }
            }
        }
    }
}

private fun NavDestination?.isTopLevel(): Boolean =
    this != null && (hasRoute(TimelineRoute::class) || hasRoute(TasksRoute::class) || hasRoute(ReviewRoute::class))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTopLevel(): Boolean =
    initialState.destination.isTopLevel() && targetState.destination.isTopLevel()

/** A top-level screen with the navigation bar beneath it on the left, the open memo on the right. */
@Composable
private fun ListDetailPanes(
    listWeight: Float,
    navBar: @Composable () -> Unit,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Scaffold(modifier = Modifier.weight(listWeight), bottomBar = navBar) { padding ->
            Box(Modifier.padding(bottom = padding.calculateBottomPadding())) { list() }
        }
        VerticalDivider()
        Box(Modifier.weight(1f - listWeight).fillMaxSize().background(MaterialTheme.colorScheme.background)) { detail() }
    }
}
