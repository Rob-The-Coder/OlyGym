package olygym.app.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import olygym.app.ui.plan.PlanListScreen

/**
 * The one Navigator. Voyager is the navigation library (see the Phase 0 decisions in
 * docs/PORT-TO-KOTLIN.md); this is the single place its stack is created.
 *
 * disposeBehavior keeps a screen's own state while it is off the top of the stack, and keeps nested
 * navigators alive: the tab bar of Phase 1 will be a nested navigator, and a tab that pushes a
 * detail and comes back must not be rebuilt from scratch.
 */
@Composable
fun AppNavigator(initial: Screen = PlanListScreen) {
    Navigator(
        screen = initial,
        disposeBehavior = NavigatorDisposeBehavior(disposeSteps = false, disposeNestedNavigators = false),
    )
}
