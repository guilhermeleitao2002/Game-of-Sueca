package pt.up.fe.asma.sueca

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pt.up.fe.asma.sueca.data.ScanResultBus
import pt.up.fe.asma.sueca.data.SettingsViewModel
import pt.up.fe.asma.sueca.ui.screens.AdvisorScreen
import pt.up.fe.asma.sueca.ui.screens.AgentsScreen
import pt.up.fe.asma.sueca.ui.screens.DeckTrainerScreen
import pt.up.fe.asma.sueca.ui.screens.HomeScreen
import pt.up.fe.asma.sueca.ui.screens.PlayScreen
import pt.up.fe.asma.sueca.ui.screens.ScanScreen
import pt.up.fe.asma.sueca.ui.screens.SettingsScreen
import pt.up.fe.asma.sueca.ui.screens.SimulatorScreen

object Routes {
    const val HOME = "home"
    const val PLAY = "play"
    const val ADVISOR = "advisor"
    const val SCAN = "scan"
    const val TRAIN_DECK = "train-deck"
    const val SIMULATOR = "simulator"
    const val AGENTS = "agents"
    const val SETTINGS = "settings"
}

@Composable
fun SuecaApp(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val settings by settingsViewModel.settings.collectAsState()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                settings = settings,
                onNavigate = navController::navigate,
            )
        }

        composable(Routes.PLAY) {
            PlayScreen(
                settings = settings,
                onBack = navController::popBackStack,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.ADVISOR) {
            AdvisorScreen(
                settings = settings,
                onBack = navController::popBackStack,
                onScanHand = { navController.navigate(Routes.SCAN) },
            )
        }

        composable(Routes.SCAN) {
            ScanScreen(
                settings = settings,
                onBack = navController::popBackStack,
                onUse = { cards ->
                    ScanResultBus.offer(cards)
                    navController.popBackStack()
                },
                onTrainDeck = { navController.navigate(Routes.TRAIN_DECK) },
            )
        }

        composable(Routes.TRAIN_DECK) {
            DeckTrainerScreen(
                settings = settings,
                onBack = navController::popBackStack,
                onProfileSelected = settingsViewModel::setDeckProfile,
            )
        }

        composable(Routes.SIMULATOR) {
            SimulatorScreen(settings = settings, onBack = navController::popBackStack)
        }

        composable(Routes.AGENTS) {
            AgentsScreen(onBack = navController::popBackStack)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                viewModel = settingsViewModel,
                onBack = navController::popBackStack,
                onTrainDeck = { navController.navigate(Routes.TRAIN_DECK) },
            )
        }
    }
}
