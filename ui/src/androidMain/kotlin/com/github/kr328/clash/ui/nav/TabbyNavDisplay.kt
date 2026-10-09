package com.github.kr328.clash.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

@Composable
fun <T : Any> TabbyNavDisplay(
  backStack: List<T>,
  modifier: Modifier = Modifier,
  onBack: () -> Unit = {
    if (backStack is MutableList<T>) {
      backStack.removeLastOrNull()
    }
  },
  entryProvider: (key: T) -> NavEntry<T>,
) {

  NavDisplay(
    modifier = modifier,
    backStack = backStack,
    onBack = onBack,
    // Keep both decorators: savable state is required for SavedStateHandle support, and
    // ViewModelStore decorator scopes viewModel() to each NavEntry instead of the Activity.
    entryDecorators =
      listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
      ),
    transitionSpec = {
      slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) togetherWith
        slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
    },
    popTransitionSpec = {
      slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) togetherWith
        slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
    },
    predictivePopTransitionSpec = {
      slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) togetherWith
        slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
    },
    entryProvider = entryProvider,
  )
}

@Composable
inline fun rememberNavBackStackBuilder(
  builderAction: MutableList<NavKey>.() -> Unit,
): NavBackStack<NavKey> = rememberNavBackStack(*buildList(builderAction).toTypedArray())

fun <T> MutableList<T>.addIfNotLast(element: T): Boolean {
  if (lastOrNull() == element) return false
  return add(element)
}
