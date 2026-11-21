package app.recetario.today

import app.recetario.pairing.PairRole
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import app.recetario.weather.WeatherKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TodayTextsTest {
    private val soup = recipe("sopa", serveTemp = ServeTemp.HOT, minutes = 30)
    private val longSoup = recipe("pozole", serveTemp = ServeTemp.HOT, minutes = 240)
    private val salad = recipe("ensalada", serveTemp = ServeTemp.COLD, minutes = 15)
    private val plain = recipe("guisado", minutes = 90)
    private val quick = recipe("quesadillas", minutes = 20)
    private val chocolate = recipe("chocolate", PairRole.DRINK, serveTemp = ServeTemp.HOT)

    @Test
    fun reason_texts() {
        val cases: List<Triple<TodayContext, TodayRecipe?, String>> = listOf(
            // Con clima: la temperatura, lo que hace y lo que pide el plato elegido.
            Triple(ctx(thermal = Thermal.COLD, apparentC = 8.6, weatherKind = WeatherKind.RAIN, rainingNow = true), soup,
                "Hace 9 °C y llueve: algo caliente y reconfortante"),
            Triple(ctx(thermal = Thermal.HOT, apparentC = 31.2, weatherKind = WeatherKind.CLEAR), salad,
                "Hace 31 °C y hay sol: algo fresco"),
            Triple(ctx(thermal = Thermal.HOT, apparentC = 31.0), salad, "Hace 31 °C: algo fresco"),
            Triple(ctx(thermal = Thermal.HOT, apparentC = 26.0, weatherKind = WeatherKind.RAIN, rainingNow = true), salad,
                "Hace 26 °C y llueve: algo fresco"),
            Triple(ctx(thermal = Thermal.MILD, apparentC = 18.0, weatherKind = WeatherKind.CLOUDY, rainingNow = true), soup,
                "Hace 18 °C y llueve: algo calientito"),
            Triple(ctx(thermal = Thermal.COLD, apparentC = -2.0, weatherKind = WeatherKind.SNOW), soup,
                "Hace -2 °C y nieva: algo caliente y reconfortante"),
            Triple(ctx(thermal = Thermal.MILD, apparentC = 20.0, weatherKind = WeatherKind.STORM), plain,
                "Hace 20 °C y hay tormenta: una idea para la comida"),
            Triple(ctx(thermal = Thermal.MILD, apparentC = 20.0, weatherKind = WeatherKind.FOG), plain,
                "Hace 20 °C y hay niebla: una idea para la comida"),
            Triple(ctx(thermal = Thermal.MILD, apparentC = 20.0, weatherKind = WeatherKind.CLOUDY), plain,
                "Hace 20 °C y está nublado: una idea para la comida"),
            // Nunca contradice al plato: con frío y sin nada caliente, no dice «caliente».
            Triple(ctx(thermal = Thermal.COLD, apparentC = 9.0, moment = Moment.DINNER), salad,
                "Hace 9 °C: algo rápido para la cena"),
            Triple(ctx(thermal = Thermal.HOT, apparentC = 31.0, now = SATURDAY), longSoup, "Hace 31 °C: una idea para la comida"),
            // De noche no hay sol.
            Triple(ctx(thermal = Thermal.MILD, apparentC = 20.0, weatherKind = WeatherKind.CLEAR, moment = Moment.DINNER, now = SATURDAY), plain,
                "Hace 20 °C: una idea para la cena"),
            Triple(ctx(thermal = Thermal.COLD, apparentC = 12.0, weatherKind = WeatherKind.CLEAR, moment = Moment.NIGHT), chocolate,
                "Hace 12 °C: algo caliente y reconfortante"),
            // Sin clima: la estación, el día y el momento.
            Triple(ctx(season = Season.RAINY), soup, "Es temporada de lluvias: algo calientito"),
            Triple(ctx(season = Season.WINTER), soup, "Es invierno: algo calientito"),
            Triple(ctx(season = Season.SUMMER), salad, "Es verano: algo fresco"),
            Triple(ctx(season = Season.RAINY), salad, "Comida de entre semana: algo rápido"),
            Triple(ctx(season = Season.DRY, moment = Moment.DINNER), quick, "Cena de entre semana: algo rápido"),
            Triple(ctx(season = Season.DRY, now = SATURDAY), plain, "Es fin de semana: hay tiempo para cocinar"),
            Triple(ctx(season = Season.DRY, now = SATURDAY), quick, "Una idea para la comida"),
            Triple(ctx(season = Season.DRY, moment = Moment.BREAKFAST), quick, "Algo para empezar el día"),
            Triple(ctx(season = Season.DRY, moment = Moment.SNACK), quick, "Algo para la merienda"),
            Triple(ctx(season = Season.DRY, moment = Moment.NIGHT), quick, "Algo para la noche"),
            Triple(ctx(season = Season.DRY), null, "Una idea para la comida"),
            Triple(ctx(thermal = Thermal.MILD, apparentC = 15.0, moment = Moment.BREAKFAST), quick, "Hace 15 °C: algo para empezar el día"),
        )
        for ((c, lead, expected) in cases) {
            assertThat(todayReason(c, lead)).isEqualTo(expected)
        }
    }

    @Test
    fun planToday_usesTheReasonOfItsLead() {
        val c = ctx(thermal = Thermal.COLD, apparentC = 9.0, weatherKind = WeatherKind.RAIN, rainingNow = true)
        val menu = planToday(c, inputs(soup, salad), emptySet(), kotlin.random.Random(1))
        assertThat(menu.reason).isEqualTo("Hace 9 °C y llueve: algo caliente y reconfortante")
    }
}
