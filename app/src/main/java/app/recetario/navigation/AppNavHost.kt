package app.recetario.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.recetario.AppContainer
import app.recetario.backup.BackupRoute
import app.recetario.cooking.CookingRoute
import app.recetario.converter.ConverterRoute
import app.recetario.core.ui.components.LocalNavAnimatedVisibilityScope
import app.recetario.core.ui.components.LocalSharePhotos
import app.recetario.core.ui.components.LocalSharedTransitionScope
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.motion.LocalSaveCelebration
import app.recetario.core.ui.motion.SaveCelebration
import app.recetario.core.ui.motion.SavedToast
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.diary.DiaryRoute
import app.recetario.home.HomeRoute
import app.recetario.pairing.CompareRoute
import app.recetario.pairing.FullMenuRoute
import app.recetario.pairing.PairingHistoryRoute
import app.recetario.pairing.PairingRoute
import app.recetario.recipes.detail.DetailRoute
import app.recetario.recipes.editor.EditorRoute
import app.recetario.recipes.list.RecipesRoute
import app.recetario.settings.AboutRoute
import app.recetario.settings.AppearanceRoute
import app.recetario.settings.CategoriesRoute
import app.recetario.settings.SettingsRoute
import app.recetario.settings.TagsRoute
import app.recetario.shopping.ShoppingRoute
import app.recetario.pantry.WhatCanIMakeRoute
import app.recetario.settings.PantryBasicsRoute
import app.recetario.settings.LocationWeatherRoute
import app.recetario.settings.MealTimesRoute

private const val FADE_MS = 250

/**
 * Barra inferior (solo en las pestañas) y NavHost dentro de un SharedTransitionLayout: la foto de una
 * tarjeta viaja al detalle con la clave `photo-$recipeId`. Cada tarea añade aquí sus destinos.
 */
@Composable
fun AppNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    // Guardar una receta: aviso «✓ Receta guardada» y rebote de su tarjeta al volver (Inicio o Recetas).
    saveCelebration: SaveCelebration = remember { SaveCelebration() },
) {
    val reduced = LocalReducedMotion.current
    val entry by navController.currentBackStackEntryAsState()
    val currentTab = Tab.entries.firstOrNull { tab ->
        entry?.destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
    }
    val detailTracker = remember { DetailTransitionTracker() }
    val celebration = saveCelebration
    // El rebote pendiente sigue mientras se esté en Inicio, Recetas, el detalle o el editor (al editar se vuelve al
    // detalle y luego a la lista); en cualquier otra pantalla se olvida.
    val destination = entry?.destination
    LaunchedEffect(destination) {
        if (destination != null && !keepsPendingBounce(destination)) celebration.expireBounce()
    }
    val sharePhotos = detailTracker.involvesDetail(entry?.id, entry?.destination?.hasRoute(RecipeDetail::class) == true)
    Scaffold(
        modifier = modifier,
        containerColor = LocalRecetarioColors.current.bg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = currentTab != null,
                enter = if (reduced) EnterTransition.None else slideInVertically(tween(FADE_MS)) { it },
                exit = if (reduced) ExitTransition.None else slideOutVertically(tween(FADE_MS)) { it },
            ) {
                BottomBar(
                    current = currentTab,
                    onSelect = { tab -> navController.navigateToTab(tab) },
                    modifier = Modifier.testTag("bottom-bar"),
                )
            }
        },
    ) { padding ->
        SharedTransitionLayout(Modifier.padding(padding).consumeWindowInsets(padding)) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalSharePhotos provides sharePhotos,
                LocalSaveCelebration provides celebration,
            ) {
                Box(Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = Home,
                        enterTransition = { if (reduced) EnterTransition.None else fadeIn(tween(FADE_MS)) },
                        exitTransition = { if (reduced) ExitTransition.None else fadeOut(tween(FADE_MS)) },
                        popEnterTransition = { if (reduced) EnterTransition.None else fadeIn(tween(FADE_MS)) },
                        popExitTransition = { if (reduced) ExitTransition.None else fadeOut(tween(FADE_MS)) },
                    ) {
                        composable<Home> { from ->
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                                HomeRoute(
                                    container = container,
                                    onCategory = { id -> navController.from(from) { openRecipesFiltered(id) } },
                                    onRecipe = { id -> navController.from(from) { navigate(RecipeDetail(id)) } },
                                    onAdd = { navController.from(from) { navigate(RecipeEditor()) } },
                                    onSettings = { navController.from(from) { navigate(Settings) } },
                                    onAppearance = { navController.from(from) { navigate(Appearance) } },
                                    onBackup = { navController.from(from) { navigate(Backup) } },
                                    onDiary = { navController.from(from) { navigate(Diary) } },
                                    onAddIn = { categoryId -> navController.from(from) { navigate(RecipeEditor(categoryId = categoryId)) } },
                                    onCooking = { id, servings -> navController.from(from) { navigate(Cooking(id, servings)) } },
                                )
                            }
                        }
                        composable<Recipes> { from ->
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                                RecipesRoute(
                                    container = container,
                                    onRecipe = { id -> navController.from(from) { navigate(RecipeDetail(id)) } },
                                    onAdd = { categoryId ->
                                        navController.from(from) { navigate(RecipeEditor(categoryId = categoryId)) }
                                    },
                                    onConverter = { navController.from(from) { navigate(Converter) } },
                                )
                            }
                        }
                        composable<Shopping> { from ->
                            ShoppingRoute(container, onWhatCanIMake = { navController.from(from) { navigate(WhatCanIMake) } })
                        }
                        composable<WhatCanIMake> { from ->
                            WhatCanIMakeRoute(
                                container = container,
                                onBack = { navController.from(from) { popBackStack() } },
                                onRecipe = { id -> navController.from(from) { navigate(RecipeDetail(id)) } },
                            )
                        }
                        composable<LocationWeather> { from ->
                            LocationWeatherRoute(container, onBack = { navController.from(from) { popBackStack() } })
                        }
                        composable<MealTimesSettings> { from ->
                            MealTimesRoute(container, onBack = { navController.from(from) { popBackStack() } })
                        }
                        composable<PantryBasics> { from ->
                            PantryBasicsRoute(container, onBack = { navController.from(from) { popBackStack() } })
                        }
                        composable<Pairing> { from ->
                            PairingRoute(
                                container = container,
                                onCompare = { subject, other ->
                                    navController.from(from) { navigate(PairingCompare(subject.kind, subject.id, other.kind, other.id)) }
                                },
                                onFullMenu = { navController.from(from) { navigate(FullMenu) } },
                                onHistory = { navController.from(from) { navigate(PairingHistory) } },
                            )
                        }
                        composable<PairingCompare> { from ->
                            val route = from.toRoute<PairingCompare>()
                            CompareRoute(
                                container = container,
                                first = route.aKind to route.aId,
                                second = route.bKind to route.bId,
                                onBack = { navController.from(from) { popBackStack() } },
                            )
                        }
                        composable<FullMenu> { from ->
                            FullMenuRoute(
                                container = container,
                                onBack = { navController.from(from) { popBackStack() } },
                                onRecipe = { id -> navController.from(from) { navigate(RecipeDetail(id)) } },
                            )
                        }
                        composable<PairingHistory> { from ->
                            PairingHistoryRoute(
                                container = container,
                                onBack = { navController.from(from) { popBackStack() } },
                                onCompare = { pair ->
                                    navController.from(from) { navigate(PairingCompare(pair.a.kind, pair.a.id, pair.b.kind, pair.b.id)) }
                                },
                            )
                        }
                        composable<RecipeDetail> { from ->
                            val route = from.toRoute<RecipeDetail>()
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                                DetailRoute(
                                    container = container,
                                    recipeId = route.id,
                                    onBack = { navController.from(from) { popBackStack() } },
                                    onEdit = { navController.from(from) { navigate(RecipeEditor(id = route.id)) } },
                                    onCooking = { servings -> navController.from(from) { navigate(Cooking(route.id, servings)) } },
                                    onMorePairings = { navController.from(from) { openPairingFor(route.id) } },
                                    onComparePairing = { other ->
                                        navController.from(from) { navigate(PairingCompare("RECIPE", route.id, other.kind, other.id)) }
                                    },
                                )
                            }
                        }
                        composable<Cooking> { from ->
                            CookingRoute(container, onClose = { navController.from(from) { popBackStack() } })
                        }
                        composable<Converter> { from ->
                            ConverterRoute(container, onBack = { navController.from(from) { popBackStack() } })
                        }
                        composable<Settings> { from ->
                            SettingsRoute(
                                container = container,
                                onBack = { navController.from(from) { popBackStack() } },
                                onAppearance = { navController.from(from) { navigate(Appearance) } },
                                onCategories = { navController.from(from) { navigate(Categories) } },
                                onTags = { navController.from(from) { navigate(Tags) } },
                                onBackup = { navController.from(from) { navigate(Backup) } },
                                onAbout = { navController.from(from) { navigate(About) } },
                                onPantryBasics = { navController.from(from) { navigate(PantryBasics) } },
                                onLocationWeather = { navController.from(from) { navigate(LocationWeather) } },
                                onMealTimes = { navController.from(from) { navigate(MealTimesSettings) } },
                            )
                        }
                        composable<Appearance> { from ->
                            AppearanceRoute(container, onBack = { navController.from(from) { popBackStack() } })
                        }
                        composable<Categories> { from ->
                            CategoriesRoute(container, onBack = { navController.from(from) { popBackStack() } })
                        }
                        composable<Tags> { from ->
                            TagsRoute(container, onBack = { navController.from(from) { popBackStack() } })
                        }
                        composable<Backup> { from ->
                            BackupRoute(container, onBack = { navController.from(from) { popBackStack() } })
                        }
                        composable<Diary> { from ->
                            DiaryRoute(
                                container = container,
                                onBack = { navController.from(from) { popBackStack() } },
                                onRecipe = { id -> navController.from(from) { navigate(RecipeDetail(id)) } },
                            )
                        }
                        composable<About> { from ->
                            AboutRoute(onBack = { navController.from(from) { popBackStack() } })
                        }
                        composable<RecipeEditor> { from ->
                            val route = from.toRoute<RecipeEditor>()
                            EditorRoute(
                                container = container,
                                isNew = route.id == null,
                                onBack = { navController.from(from) { popBackStack() } },
                                onSaved = { id ->
                                    navController.from(from) {
                                        celebration.recipeSaved(id)
                                        popBackStack()
                                    }
                                },
                            )
                        }
                    }
                    // Por encima de todo, sin tocarse: los toques pasan a la pantalla de debajo.
                    SavedToast(celebration, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
                }
            }
        }
    }
}

private fun keepsPendingBounce(destination: NavDestination): Boolean =
    destination.hasRoute(Home::class) || destination.hasRoute(Recipes::class) ||
        destination.hasRoute(RecipeDetail::class) || destination.hasRoute(RecipeEditor::class)

/** Recuerda si el destino anterior era el detalle, para saber si la última navegación entra o sale de él. */
private class DetailTransitionTracker {
    private var lastId: String? = null
    private var lastIsDetail = false
    private var previousIsDetail = false

    fun involvesDetail(entryId: String?, isDetail: Boolean): Boolean {
        if (entryId != lastId) {
            previousIsDetail = lastIsDetail
            lastIsDetail = isDetail
            lastId = entryId
        }
        return lastIsDetail || previousIsDetail
    }
}

/**
 * Navega solo si [entry] sigue siendo el destino actual. Los toques nunca se bloquean durante las
 * transiciones: un segundo toque en la pantalla que se va no apila otra copia del destino.
 */
private inline fun NavHostController.from(entry: NavBackStackEntry, block: NavHostController.() -> Unit) {
    if (currentBackStackEntry?.id == entry.id) block()
}

/** Pestañas: cada una conserva su estado y pulsarla otra vez no apila copias. */
private fun NavHostController.navigateToTab(tab: Tab) = navigate(tab.route) {
    popUpTo<Home> { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** «Ver más» del maridaje del detalle: la pestaña Maridaje con la receta ya elegida. */
private fun NavHostController.openPairingFor(recipeId: String) = navigate(Pairing(subject = "RECIPE:$recipeId")) {
    popUpTo<Home> { saveState = true }
    launchSingleTop = true
}

/** Un chip de categoría de Inicio abre Recetas filtrado (sin recuperar el filtro anterior). */
private fun NavHostController.openRecipesFiltered(categoryId: String) = navigate(Recipes(categoryId)) {
    popUpTo<Home> { saveState = true }
    launchSingleTop = true
}
