package app.recetario.pairing

import app.recetario.backup.PAIRING_SIDE_KINDS

/**
 * Clave de una valoración: `"<kindA>:<idA>|<kindB>:<idB>"`, con los dos lados ordenados por texto (no importa el
 * orden en que lleguen). Cada `kind` es uno de [PAIRING_SIDE_KINDS]; catálogo con catálogo no se puede valorar.
 */
fun pairKey(a: Pair<String, String>, b: Pair<String, String>): String {
    require(a.first in PAIRING_SIDE_KINDS && b.first in PAIRING_SIDE_KINDS) { "Tipo de lado desconocido: ${a.first}, ${b.first}" }
    require(!(a.first == CATALOG && b.first == CATALOG)) { "Una pareja de dos elementos del catálogo no se valora" }
    return listOf("${a.first}:${a.second}", "${b.first}:${b.second}").sorted().joinToString("|")
}

fun pairKey(a: PairingItem, b: PairingItem): String = pairKey(a.kind to a.id, b.kind to b.id)

/** La clave, o null si los dos son del catálogo (esa pareja no se puede valorar). */
fun ratableKey(a: PairingItem, b: PairingItem): String? =
    if (a.kind == CATALOG && b.kind == CATALOG) null else pairKey(a, b)

internal const val RECIPE = "RECIPE"
internal const val CATALOG = "CATALOG"
