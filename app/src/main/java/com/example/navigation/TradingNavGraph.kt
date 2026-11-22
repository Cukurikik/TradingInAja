package com.example.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.MainUiEvent
import com.example.R
import com.tradinginaja.feature.learn.LearnRoute
import com.tradinginaja.feature.market.MarketDetailRoute
import com.tradinginaja.feature.market.MarketListRoute
import com.tradinginaja.feature.market.NewsRoute
import com.tradinginaja.feature.portfolio.PortfolioRoute
import com.tradinginaja.feature.profile.ProfileRoute
import com.tradinginaja.feature.trade.TradeRoute
import kotlinx.serialization.Serializable

@Serializable
data object MarketRouteKey

@Serializable
data class MarketDetailRouteKey(val symbol: String)

@Serializable
data class NewsRouteKey(val symbolFilter: String? = null)

@Serializable
data class TradeRouteKey(val symbol: String? = null)

@Serializable
data object PortfolioRouteKey

@Serializable
data object LearnRouteKey

@Serializable
data object ProfileRouteKey

enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    MARKET(
        route = MarketRouteKey,
        labelRes = R.string.nav_market,
        selectedIcon = Icons.AutoMirrored.Rounded.ShowChart,
        unselectedIcon = Icons.AutoMirrored.Outlined.ShowChart
    ),
    TRADE(
        route = TradeRouteKey(),
        labelRes = R.string.nav_trade,
        selectedIcon = Icons.Rounded.SwapHoriz,
        unselectedIcon = Icons.Outlined.SwapHoriz
    ),
    PORTFOLIO(
        route = PortfolioRouteKey,
        labelRes = R.string.nav_portfolio,
        selectedIcon = Icons.Rounded.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet
    ),
    LEARN(
        route = LearnRouteKey,
        labelRes = R.string.nav_learn,
        selectedIcon = Icons.Rounded.School,
        unselectedIcon = Icons.Outlined.School
    ),
    PROFILE(
        route = ProfileRouteKey,
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Rounded.Person,
        unselectedIcon = Icons.Outlined.Person
    )
}

private val COMPACT_WIDTH_BREAKPOINT = 600.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingAppShell(
    showTutorial: Boolean = false,
    tutorialStepIndex: Int = 0,
    onTutorialEvent: (MainUiEvent) -> Unit = {},
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val navigateToTopLevel: (TopLevelDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= COMPACT_WIDTH_BREAKPOINT

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.top_bar_virtual_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    TradingBottomNavigationBar(
                        currentDestination = currentDestination,
                        onNavigate = navigateToTopLevel
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    TradingNavigationRail(
                        currentDestination = currentDestination,
                        onNavigate = navigateToTopLevel
                    )
                }
                TradingNavHost(
                    navController = navController,
                    onReplayTutorial = { onTutorialEvent(MainUiEvent.ReplayTutorial) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (showTutorial) {
            InteractiveTutorialDialog(
                stepIndex = tutorialStepIndex,
                onNext = { onTutorialEvent(MainUiEvent.NextTutorialStep) },
                onPrevious = { onTutorialEvent(MainUiEvent.PreviousTutorialStep) },
                onSkip = { onTutorialEvent(MainUiEvent.SkipOrFinishTutorial) },
                onJumpToDestination = navigateToTopLevel
            )
        }
    }
}

@Composable
private fun TradingBottomNavigationBar(currentDestination: NavDestination?, onNavigate: (TopLevelDestination) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = isDestinationSelected(currentDestination, destination)
            val label = stringResource(destination.labelRes)

            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                modifier = Modifier.testTag("nav_tab_${destination.name.lowercase()}"),
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = label
                    )
                },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}

@Composable
private fun TradingNavigationRail(currentDestination: NavDestination?, onNavigate: (TopLevelDestination) -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = isDestinationSelected(currentDestination, destination)
            val label = stringResource(destination.labelRes)

            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                modifier = Modifier.testTag("nav_rail_${destination.name.lowercase()}"),
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = label
                    )
                },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun TradingNavHost(
    navController: NavHostController,
    onReplayTutorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = MarketRouteKey,
        modifier = modifier.fillMaxSize()
    ) {
        composable<MarketRouteKey> {
            MarketListRoute(
                onAssetClick = { symbol ->
                    navController.navigate(MarketDetailRouteKey(symbol = symbol))
                },
                onOpenNews = {
                    navController.navigate(NewsRouteKey())
                }
            )
        }
        composable<MarketDetailRouteKey> { backStackEntry ->
            val route = backStackEntry.toRoute<MarketDetailRouteKey>()
            MarketDetailRoute(
                symbol = route.symbol,
                onNavigateBack = { navController.popBackStack() },
                onTradeAsset = { symbol ->
                    navController.navigate(TradeRouteKey(symbol = symbol)) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                    }
                },
                onOpenRelatedNews = { symbol ->
                    navController.navigate(NewsRouteKey(symbolFilter = symbol))
                }
            )
        }
        composable<NewsRouteKey> { backStackEntry ->
            val route = backStackEntry.toRoute<NewsRouteKey>()
            NewsRoute(
                symbolFilter = route.symbolFilter,
                onNavigateBack = { navController.popBackStack() },
                onAssetClick = { symbol ->
                    navController.navigate(MarketDetailRouteKey(symbol = symbol))
                }
            )
        }
        composable<TradeRouteKey> { backStackEntry ->
            val route = backStackEntry.toRoute<TradeRouteKey>()
            TradeRoute(initialSymbol = route.symbol)
        }
        composable<PortfolioRouteKey> {
            PortfolioRoute(
                onTradeAssetClick = { symbol ->
                    navController.navigate(TradeRouteKey(symbol = symbol)) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable<LearnRouteKey> { LearnRoute(onReplayTutorial = onReplayTutorial) }
        composable<ProfileRouteKey> { ProfileRoute(onReplayTutorial = onReplayTutorial) }
    }
}

private fun isDestinationSelected(currentDestination: NavDestination?, destination: TopLevelDestination): Boolean {
    val hierarchy = currentDestination?.hierarchy ?: return false
    return hierarchy.any { navDest ->
        navDest.hasRoute(destination.route::class) ||
            (
                destination == TopLevelDestination.MARKET &&
                    (navDest.hasRoute(MarketDetailRouteKey::class) || navDest.hasRoute(NewsRouteKey::class))
                )
    }
}
