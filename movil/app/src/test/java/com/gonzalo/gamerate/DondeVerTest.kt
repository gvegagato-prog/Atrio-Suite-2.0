package com.gonzalo.gamerate

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dónde verla: lo de España en la respuesta de TMDB (datos de JustWatch). */
class DondeVerTest {
    @Test fun espana() {
        val o = JSONObject("""{"id":550,"results":{
          "US":{"link":"https://www.themoviedb.org/movie/550/watch?locale=US","flatrate":[{"provider_id":8,"provider_name":"Netflix","logo_path":"/n.jpg","display_priority":1}]},
          "ES":{"link":"https://www.themoviedb.org/movie/550/watch?locale=ES",
            "flatrate":[{"provider_id":119,"provider_name":"Amazon Prime Video","logo_path":"/p.jpg","display_priority":2},{"provider_id":337,"provider_name":"Disney Plus","logo_path":"/d.jpg","display_priority":1}],
            "rent":[{"provider_id":2,"provider_name":"Apple TV","logo_path":"/a.jpg","display_priority":3}],
            "buy":[{"provider_id":2,"provider_name":"Apple TV","logo_path":"/a.jpg","display_priority":3},{"provider_id":3,"provider_name":"Google Play Movies","logo_path":"/g.jpg","display_priority":4}],
            "ads":[{"provider_id":9,"provider_name":"Pluto TV","logo_path":"/pl.jpg","display_priority":9}]}}}""")
        val d = Tmdb.proveedoresDe(o)
        assertEquals(listOf("Disney Plus", "Amazon Prime Video"), d.suscripcion.map { it.nombre })
        assertEquals("https://image.tmdb.org/t/p/w92/d.jpg", d.suscripcion[0].logo)
        assertEquals(listOf("Apple TV"), d.alquiler.map { it.nombre })
        assertEquals(listOf("Apple TV", "Google Play Movies"), d.compra.map { it.nombre })
        assertEquals(listOf("Pluto TV"), d.gratis.map { it.nombre })
        assertTrue(d.link.endsWith("locale=ES"))
        // Sin España: vacío (no las de otro país)
        val nada = Tmdb.proveedoresDe(JSONObject("""{"results":{"US":{"flatrate":[{"provider_name":"Netflix"}]}}}"""))
        assertTrue(nada.vacio)
    }

    @Test fun proximoEpisodio() {
        val p = TvMaze.proximoDe(JSONObject("""{"id":1,"name":"X","_embedded":{"nextepisode":{"season":2,"number":3,"name":"Long, Long Time","airdate":"2026-11-05"}}}"""))!!
        assertEquals(java.time.LocalDate.of(2026, 11, 5), p.dia)
        assertEquals("2×03", p.num)
        assertEquals("Long, Long Time", p.titulo)
        assertEquals(null, TvMaze.proximoDe(JSONObject("""{"id":1,"name":"Acabada"}""")))
        assertEquals(null, TvMaze.proximoDe(JSONObject("""{"_embedded":{"nextepisode":{"season":1,"number":1,"airdate":""}}}""")))
    }
}
