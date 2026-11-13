package app.recetario.recipes.editor

/**
 * Foto que devolvió la cámara o la galería y aún no se ha entregado al editor. [source] es la ruta del
 * archivo temporal de la cámara ([tempFile]) o la URI de la galería; [target] es «cover» o «step:<id>».
 */
data class PendingPhoto(val source: String, val tempFile: Boolean, val target: String)

/**
 * Fotos a la espera. Si Android cerró la app con la cámara abierta, el resultado llega al recrearse, antes
 * de que el editor esté listo o mientras se decide el borrador: se guarda aquí hasta poder entregarlo.
 */
data class PendingPhotos(val items: List<PendingPhoto> = emptyList()) {
    operator fun plus(photo: PendingPhoto) = PendingPhotos(items + photo)

    /** Con el editor listo y sin borrador por decidir se entregan todas (y no queda ninguna); si no, ninguna. */
    fun release(editorReady: Boolean, draftPending: Boolean): Pair<List<PendingPhoto>, PendingPhotos> =
        if (editorReady && !draftPending) items to PendingPhotos() else emptyList<PendingPhoto>() to this

    /** Archivos temporales de la cámara que aún hacen falta (la limpieza no debe borrarlos). */
    val tempFiles: Set<String>
        get() = items.filter { it.tempFile }.mapTo(LinkedHashSet()) { it.source }

    /** Para guardarlas en el estado de la pantalla (`rememberSaveable`). */
    fun encode(): ArrayList<String> = items.mapTo(ArrayList()) { "${if (it.tempFile) TEMP else URI}\n${it.target}\n${it.source}" }

    companion object {
        private const val TEMP = "T"
        private const val URI = "U"

        fun decode(encoded: List<String>): PendingPhotos = PendingPhotos(
            encoded.mapNotNull { entry ->
                val parts = entry.split('\n', limit = 3)
                if (parts.size != 3 || parts[0] !in setOf(TEMP, URI) || parts[1].isEmpty() || parts[2].isEmpty()) null
                else PendingPhoto(source = parts[2], tempFile = parts[0] == TEMP, target = parts[1])
            },
        )
    }
}
