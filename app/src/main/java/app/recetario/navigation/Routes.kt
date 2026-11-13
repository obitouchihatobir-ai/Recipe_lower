package app.recetario.navigation

import kotlinx.serialization.Serializable

// Pestañas de la barra inferior.
@Serializable
data object Home

/** Lista de recetas; [categoryId] la abre ya filtrada (desde los chips de Inicio). */
@Serializable
data class Recipes(val categoryId: String? = null)

@Serializable
data object Shopping

@Serializable
data object Pairing

// Pantallas de receta.
@Serializable
data class RecipeDetail(val id: String)

/** [id] null crea una receta; [categoryId] la preselecciona. Son las claves que lee EditorViewModel. */
@Serializable
data class RecipeEditor(val id: String? = null, val categoryId: String? = null)

@Serializable
data class Cooking(val recipeId: String, val servings: Int)

// Destinos que registran tareas posteriores.
@Serializable
data object Converter

@Serializable
data object Settings

@Serializable
data object Appearance

@Serializable
data object Categories

@Serializable
data object Tags

@Serializable
data object Backup

@Serializable
data object About
