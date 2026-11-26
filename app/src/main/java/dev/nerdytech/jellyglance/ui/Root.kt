package dev.nerdytech.jellyglance.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.ui.screens.ShareScreen
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.ui.screens.RequestRulesScreen
import dev.nerdytech.jellyglance.ui.screens.ActivityScreen
import dev.nerdytech.jellyglance.ui.screens.AdminScreen
import dev.nerdytech.jellyglance.ui.screens.AdminSectionScreen
import dev.nerdytech.jellyglance.ui.screens.BrowseScreen
import dev.nerdytech.jellyglance.ui.screens.IssuesScreen
import dev.nerdytech.jellyglance.ui.screens.DiscoverListScreen
import dev.nerdytech.jellyglance.ui.screens.ItemScreen
import dev.nerdytech.jellyglance.ui.screens.LibrariesScreen
import dev.nerdytech.jellyglance.ui.screens.LibraryScreen
import dev.nerdytech.jellyglance.ui.screens.UserScreen
import dev.nerdytech.jellyglance.ui.screens.UsersScreen
import dev.nerdytech.jellyglance.ui.screens.MediaScreen
import dev.nerdytech.jellyglance.ui.screens.MyGlanceScreen
import dev.nerdytech.jellyglance.ui.screens.RequestsScreen
import dev.nerdytech.jellyglance.ui.screens.DashboardScreen
import dev.nerdytech.jellyglance.ui.screens.DetailScreen
import dev.nerdytech.jellyglance.ui.screens.SettingsScreen
import dev.nerdytech.jellyglance.ui.screens.SignInScreen
import dev.nerdytech.jellyglance.ui.screens.RecentlyAddedScreen
import dev.nerdytech.jellyglance.ui.screens.StatisticsScreen
import dev.nerdytech.jellyglance.ui.screens.TimelineScreen
import dev.nerdytech.jellyglance.ui.screens.TranscodesScreen
import dev.nerdytech.jellyglance.ui.screens.SetupScreen
import dev.nerdytech.jellyglance.ui.screens.StreamsScreen
import dev.nerdytech.jellyglance.ui.screens.UpcomingScreen
import dev.nerdytech.jellyglance.ui.theme.JellyGlanceTheme
import dev.nerdytech.jellyglance.ui.theme.ThemePresets

data class TabRequest(val tab: String, val at: Long = System.nanoTime(), val share: String? = null)

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("dashboard", "Home", Icons.Rounded.Dashboard),
    Tab("streams", "Streams", Icons.Rounded.PlayCircle),
    Tab("requests", "Requests", Icons.Rounded.Inbox),
    Tab("activity", "Activity", Icons.Rounded.BarChart),
    Tab("settings", "Settings", Icons.Rounded.Settings),
)

/** Signed-in accounts get My Glance in place of the Settings tab (Settings opens from its gear). */
private val accountTabs = tabs.dropLast(1) + Tab("me", "Me", Icons.Rounded.AccountCircle)
/** Owners and admins also get the server admin hub, just before Me. */
private val adminTabs = tabs.dropLast(1) + Tab("admin", "Admin", Icons.Rounded.AdminPanelSettings) + Tab("me", "Me", Icons.Rounded.AccountCircle)

@Composable
fun JellyGlanceRoot(startTab: TabRequest? = null, vm: AppViewModel = viewModel(), locked: Boolean = false, onUnlock: () -> Unit = {}) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    JellyGlanceTheme(settings.theme, settings.dynamicColor, ThemePresets.resolve(settings.themeName, settings.serverTheme)) {
        // Surface supplies onBackground as the default content colour for screens without a Scaffold.
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            androidx.compose.runtime.LaunchedEffect(settings.signedIn) { vm.discoverJellyfinUrl() }
            androidx.compose.runtime.CompositionLocalProvider(dev.nerdytech.jellyglance.ui.components.LocalJellyfinUrl provides settings.jellyfinUrl) {
                when {
                    locked -> dev.nerdytech.jellyglance.ui.screens.LockScreen(onUnlock)
                    settings.configured -> MainShell(vm, startTab)
                    else -> SetupScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun MainShell(vm: AppViewModel, startTab: TabRequest?) {
    val routes by vm.routes.collectAsStateWithLifecycle()
    run {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val current = entry?.destination?.route
        val streamCount = vm.nowPlaying(routes)?.playing ?: 0

        fun goTab(route: String) = nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        // Launcher shortcuts and notifications open a specific tab.
        val settingsNow by vm.settings.collectAsStateWithLifecycle()
        val isAdmin = settingsNow.signedIn && settingsNow.sessionRole in setOf("Owner", "Admin")
        val bar = when { isAdmin -> adminTabs; settingsNow.signedIn -> accountTabs; else -> tabs }
        LaunchedEffect(startTab) {
            if (startTab?.share != null) { vm.sharedText = startTab.share; nav.navigate("share"); return@LaunchedEffect }
            startTab?.tab?.takeIf { t -> (tabs + adminTabs).any { it.route == t } }?.let { goTab(it) }
        }

        fun openWidget(id: String) = nav.navigate(if (id == "digest" || id == "health") "issues" else "widget/$id")
        fun openItem(id: String, title: String) = nav.navigate("item/" + Uri.encode(id) + "?title=" + Uri.encode(title))
        fun openRoute(route: String) = nav.navigate("route?path=" + Uri.encode(route))

        // Tablets and unfolded foldables: tabs move to a side rail.
        val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
        Scaffold(
            bottomBar = {
                if (!wide && bar.any { it.route == current }) {
                    NavigationBar {
                        bar.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.route,
                                onClick = { goTab(tab.route) },
                                icon = {
                                    if (tab.route == "streams" && streamCount > 0) {
                                        BadgedBox(badge = { Badge { Text(Fields.fmtNum(streamCount.toDouble())) } }) { Icon(tab.icon, null) }
                                    } else Icon(tab.icon, null)
                                },
                                label = {
                                    Text(
                                        tab.label, maxLines = 1, softWrap = false,
                                        style = if (bar.size > 5) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                    )
                                },
                                alwaysShowLabel = true,
                            )
                        }
                    }
                }
            },
        ) { pad ->
          androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
            if (wide) androidx.compose.material3.NavigationRail(
                header = { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(dev.nerdytech.jellyglance.R.drawable.jelly_logo), null, Modifier.padding(top = 12.dp).size(40.dp)) },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                bar.forEach { tab ->
                    androidx.compose.material3.NavigationRailItem(
                        selected = current == tab.route, onClick = { goTab(tab.route) },
                        icon = {
                            if (tab.route == "streams" && streamCount > 0) {
                                BadgedBox(badge = { Badge { Text(Fields.fmtNum(streamCount.toDouble())) } }) { Icon(tab.icon, null) }
                            } else Icon(tab.icon, null)
                        },
                        label = { Text(tab.label, maxLines = 1) },
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
            NavHost(
                nav, startDestination = "dashboard", modifier = Modifier.weight(1f).padding(bottom = pad.calculateBottomPadding()),
                enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
            ) {
                composable("dashboard") {
                    DashboardScreen(vm, ::openWidget, onStreams = { goTab("streams") }, openItem = ::openItem, onRequests = { goTab("requests") }, onBrowse = { nav.navigate("browse") }, onUpcoming = { nav.navigate("upcoming") }, onIssues = { nav.navigate("issues") }, onSignIn = { nav.navigate("signin") }, onLibraries = { nav.navigate("libraries") }, onRecent = { nav.navigate("recent") })
                }
                composable("streams") { StreamsScreen(vm, ::openItem) }
                composable("requests") {
                    RequestsScreen(
                        vm, ::openItem,
                        openMedia = { m -> vm.selectedMedia = m; nav.navigate("media") },
                        openList = { src, key, chip, title ->
                            nav.navigate("discover-list?src=${Uri.encode(src)}&key=${Uri.encode(key)}&chip=${Uri.encode(chip ?: "")}&title=${Uri.encode(title)}")
                        },
                        openRules = { nav.navigate("request-rules") },
                        openIssues = { nav.navigate("admin/issues") },
                    )
                }
                composable("share") {
                    ShareScreen(vm, vm.sharedText.orEmpty(), openMedia = { m -> vm.selectedMedia = m; nav.navigate("media") }, onBack = { nav.popBackStack() })
                }
                composable("request-rules") { RequestRulesScreen(vm, onBack = { nav.popBackStack() }) }
                composable("libraries") { LibrariesScreen(vm, openLibrary = { id, name, count -> nav.navigate("library/${Uri.encode(id)}?name=${Uri.encode(name)}&count=$count") }, onBack = { nav.popBackStack() }) }
                composable(
                    "library/{id}?name={name}&count={count}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType; defaultValue = "" },
                        navArgument("count") { type = NavType.IntType; defaultValue = 0 },
                    ),
                ) {
                    val a = it.arguments
                    LibraryScreen(vm, a?.getString("id").orEmpty(), a?.getString("name").orEmpty(), a?.getInt("count") ?: 0, ::openItem, onBack = { nav.popBackStack() })
                }
                composable("users") { UsersScreen(vm, openUser = { u -> vm.selectedUser = u; nav.navigate("user") }, onBack = { nav.popBackStack() }) }
                composable("user") { vm.selectedUser?.let { u -> UserScreen(vm, u, onBack = { nav.popBackStack() }) } }
                composable("admin") {
                    AdminScreen(
                        vm,
                        open = { k -> nav.navigate("admin/$k") },
                        onBack = if (isAdmin) null else ({ nav.popBackStack() }),
                        openRoute = { r -> nav.navigate(r) },
                    )
                }
                composable("admin/{key}", arguments = listOf(navArgument("key") { type = NavType.StringType })) {
                    AdminSectionScreen(vm, it.arguments?.getString("key").orEmpty(), onBack = { nav.popBackStack() })
                }
                composable("media") {
                    vm.selectedMedia?.let { m -> MediaScreen(vm, m, ::openItem, onBack = { nav.popBackStack() }) }
                }
                composable(
                    "discover-list?src={src}&key={key}&chip={chip}&title={title}",
                    arguments = listOf("src", "key", "chip", "title").map { n -> navArgument(n) { type = NavType.StringType; defaultValue = "" } },
                ) {
                    val a = it.arguments
                    DiscoverListScreen(
                        vm, a?.getString("src").orEmpty(), a?.getString("key").orEmpty(), a?.getString("chip")?.ifBlank { null }, a?.getString("title").orEmpty(),
                        openMedia = { m -> vm.selectedMedia = m; nav.navigate("media") }, onBack = { nav.popBackStack() },
                    )
                }
                composable("activity") { ActivityScreen(vm, onUsers = { nav.navigate("users") }, onLibraries = { nav.navigate("libraries") }, onStatistics = { nav.navigate("statistics") }, open = { r -> nav.navigate(r) }, openItem = ::openItem) }
                composable("statistics") { StatisticsScreen(vm, ::openItem, onBack = { nav.popBackStack() }) }
                composable("transcodes") { TranscodesScreen(vm, ::openItem, onBack = { nav.popBackStack() }) }
                composable("recent") { RecentlyAddedScreen(vm, ::openItem, onBack = { nav.popBackStack() }) }
                composable("timeline") { TimelineScreen(vm, ::openItem, onBack = { nav.popBackStack() }) }
                composable("signin") {
                    SignInScreen(vm, vm.settings.value.baseUrl, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                }
                composable("issues") { IssuesScreen(vm, onBack = { nav.popBackStack() }) }
                composable("upcoming") { UpcomingScreen(vm, onBack = { nav.popBackStack() }) }
                composable("browse") { BrowseScreen(vm, ::openWidget, ::openRoute, onBack = { nav.popBackStack() }) }
                composable("settings") {
                    SettingsScreen(
                        vm, onBrowse = { nav.navigate("browse") }, onSignIn = { nav.navigate("signin") }, onAdmin = { nav.navigate("admin") },
                        openPage = { p -> nav.navigate("settings/$p") },
                        onBack = if (settingsNow.signedIn) ({ nav.popBackStack() }) else null,
                    )
                }
                composable("settings/{page}", arguments = listOf(navArgument("page") { type = NavType.StringType })) {
                    SettingsScreen(
                        vm, onBrowse = { nav.navigate("browse") }, onSignIn = { nav.navigate("signin") }, onAdmin = { nav.navigate("admin") },
                        page = it.arguments?.getString("page"), onBack = { nav.popBackStack() },
                    )
                }
                composable("me") {
                    MyGlanceScreen(vm, ::openItem, onSettings = { nav.navigate("settings") }, onRequests = { goTab("requests") })
                }
                composable(
                    "item/{id}?title={title}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("title") { type = NavType.StringType; defaultValue = "" }),
                ) {
                    ItemScreen(vm, it.arguments?.getString("id").orEmpty(), it.arguments?.getString("title").orEmpty(), onBack = { nav.popBackStack() })
                }
                composable("widget/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    val id = it.arguments?.getString("id").orEmpty()
                    val def = Catalogue.get(id)
                    if (def != null) DetailScreen(vm, def, onBack = { nav.popBackStack() }, onStreams = { goTab("streams") })
                }
                composable("route?path={path}", arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })) {
                    val path = it.arguments?.getString("path").orEmpty()
                    DetailScreen(vm, Catalogue.custom(path), onBack = { nav.popBackStack() }, onStreams = { goTab("streams") })
                }
            }
          }
        }
    }
}
