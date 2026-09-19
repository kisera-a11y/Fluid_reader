package com.fluidreader.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fluidreader.app.ui.about.AboutScreen
import com.fluidreader.app.ui.calibration.CalibrationScreen
import com.fluidreader.app.ui.camera.CameraScreen
import com.fluidreader.app.ui.debug.DebugTestScreen
import com.fluidreader.app.ui.drinklog.DrinkLogScreen
import com.fluidreader.app.ui.settings.SettingsScreen

object Routes {
    const val CAMERA = "camera"
    const val SETTINGS = "settings"
    const val CALIBRATION = "calibration"
    const val DEBUG = "debug"
    const val ABOUT = "about"
    const val DRINK_LOG = "drink_log"
}

@Composable
fun FluidReaderNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.CAMERA) {
        composable(Routes.CAMERA) {
            CameraScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenDrinkLog = { navController.navigate(Routes.DRINK_LOG) },
            )
        }
        composable(Routes.DRINK_LOG) {
            DrinkLogScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenCalibration = { navController.navigate(Routes.CALIBRATION) },
                onOpenDebug = { navController.navigate(Routes.DEBUG) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.CALIBRATION) {
            CalibrationScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DEBUG) {
            DebugTestScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
