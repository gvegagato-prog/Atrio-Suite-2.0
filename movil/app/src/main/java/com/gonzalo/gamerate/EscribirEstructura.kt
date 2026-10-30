package com.gonzalo.gamerate

/**
 * La estructura de una obra en Escribir (la misma que escribir-estructura.js del PC): partes con sus capítulos, el orden del
 * libro, el paso de la «parte» antigua (un texto en cada capítulo) a partes de verdad, lo vinculado a una parte y dónde va
 * cada cosa en la línea temporal. Se comprueba con los casos de herramientas/casos/estructura.json (EscribirEstructuraTest).
 *
 * Parte «a» { titulo, orden, color, resumen }; el capítulo lleva «aid» (su parte, o nada si va suelto). Las partes y los
 * capítulos sueltos comparten el orden del manuscrito; los capítulos de una parte se ordenan entre ellos. Rama de la línea
 * «r» { titulo, color, desde, hasta? }; hito «h» con «rama» (vacío: la línea principal).
 */
object EscribirEstructura {
    private val porOrden = compareBy<Doc>({ it.orden }, { it.u }, { it.id })

    // ---- Un id que sale igual en todos los dispositivos (FNV-1a de 32 bits dos veces, sobre las unidades UTF-16)
    private fun fnv(s: String, semilla: Long): Long {
        var h = semilla.toInt()
        for (ch in s) { h = h xor ch.code; h *= 16777619 }
        return h.toLong() and 0xFFFFFFFFL
    }
    private fun hex(n: Long) = n.toString(16).padStart(8, '0')
    private fun normaNombre(t: String) = t.trim().replace(Regex("\\s+"), " ").lowercase()

    /** El id de la parte «nombre» de la obra pid al pasar las partes antiguas (texto) a partes de verdad. */
    fun idParte(pid: String, nombre: String): String {
        val k = "$pid|${normaNombre(nombre)}"
        return "a_" + hex(fnv(k, 2166136261L)) + hex(fnv(k, 3141592653L)).take(4)
    }

    class ParteNueva(val id: String, val titulo: String, val orden: Double)
    class Migracion(val partes: List<ParteNueva>, val asigna: List<Pair<String, String>>)

    /**
     * Lo que hay que hacer para pasar la «parte» de texto de los capítulos a partes de verdad: las partes que hay que crear
     * (si no existen ya) y a qué parte va cada capítulo. Cada parte toma el orden de su primer capítulo, así el manuscrito
     * queda igual. Los capítulos que ya tienen parte (aid) no se tocan.
     */
    fun planMigracion(pid: String, caps: List<Doc>, partesExistentes: List<Doc>): Migracion {
        val hay = partesExistentes.map { it.id }.toSet()
        val nuevas = LinkedHashMap<String, ParteNueva>()
        val asigna = ArrayList<Pair<String, String>>()
        for (c in caps.sortedWith(porOrden)) {
            val t = c.s("parte").trim()
            if (t.isEmpty() || c.s("aid").isNotEmpty()) continue
            val id = idParte(pid, t)
            if (id !in hay && id !in nuevas) nuevas[id] = ParteNueva(id, t.replace(Regex("\\s+"), " "), c.orden)
            asigna += c.id to id
        }
        return Migracion(nuevas.values.toList(), asigna)
    }

    /** Lo de arriba del manuscrito: una parte (con sus capítulos) o un capítulo suelto. */
    sealed class Item { class Parte(val parte: Doc, val caps: List<Doc>) : Item(); class Cap(val cap: Doc) : Item() }
    class Estructura(val items: List<Item>, val libro: List<Doc>)

    /** El manuscrito: partes y capítulos sueltos en su orden y, dentro de cada parte, sus capítulos (ver el PC). */
    fun estructura(caps: List<Doc>, partes: List<Doc>): Estructura {
        val ps = partes.sortedWith(porOrden)
        val ids = ps.map { it.id }.toSet()
        val de = ps.associate { it.id to ArrayList<Doc>() }
        val sueltos = ArrayList<Doc>()
        for (c in caps) { val a = c.s("aid"); if (a.isNotEmpty() && a in ids) de.getValue(a).add(c) else sueltos.add(c) }
        class Arriba(val item: Item, val orden: Double, val u: Long, val id: String)
        val arriba = (ps.map { Arriba(Item.Parte(it, de.getValue(it.id).sortedWith(porOrden)), it.orden, it.u, it.id) } +
            sueltos.map { Arriba(Item.Cap(it), it.orden, it.u, it.id) }).sortedWith(compareBy({ it.orden }, { it.u }, { it.id }))
        val items = arriba.map { it.item }
        return Estructura(items, items.flatMap { if (it is Item.Parte) it.caps else listOf((it as Item.Cap).cap) })
    }

    /** Las notas y fichas vinculadas a un documento (una parte o un capítulo): las que llevan su id en «vin». */
    fun vinculados(id: String, docs: List<Doc>): List<Doc> = docs.filter { id in it.lista("vin") }

    class Fila(val tipo: String, val id: String, val col: Int, val activas: List<Int>)
    class Tramo(val ini: Double, val fin: Double, val une: Boolean, val col: Int)
    class Linea(val filas: List<Fila>, val columnas: Int, val tramos: Map<String, Tramo>)

    /**
     * La línea temporal vertical: una fila por cosa (cada suceso, y el principio y el final de cada rama), de arriba abajo por
     * su orden, y en qué columna va cada línea (0 = la principal; las ramas, a su derecha, sin pisarse). Igual que en el PC.
     */
    fun linea(hitos: List<Doc>, ramas: List<Doc>): Linea {
        val ramaIds = ramas.map { it.id }.toSet()
        class F(val tipo: String, val id: String, val orden: Double, val k: Int, val rama: String = "")
        class T(val id: String, val ini: Double, val fin: Double, val une: Boolean)
        val filas = ArrayList<F>(); val tramos = ArrayList<T>()
        for (r in ramas) {
            val hs = hitos.filter { it.s("rama") == r.id }.map { it.orden }
            val ini = (hs + r.d("desde")).min()
            val hasta = r.o.opt("hasta")
            val une = hasta != null && hasta != org.json.JSONObject.NULL && hasta.toString().isNotEmpty() && hasta.toString().toDoubleOrNull() != null
            val fin = if (une) (hs + r.d("hasta")).max() else hs.maxOrNull() ?: ini
            tramos += T(r.id, ini, fin, une)
            filas += F("inicio", r.id, ini, 1)
            if (une) filas += F("fin", r.id, fin, 2)
        }
        for (x in hitos) filas += F("hito", x.id, x.orden, 0, x.s("rama").takeIf { it in ramaIds }.orEmpty())
        // (con el mismo orden: el suceso, luego la rama que nace de él y luego la que vuelve; pero los sucesos de una rama van
        // siempre después de que nazca)
        fun k(f: F, otro: F) = if (f.tipo == "hito" && otro.tipo == "inicio" && f.rama == otro.id) 2 else f.k
        filas.sortWith { a, b ->
            val o = a.orden.compareTo(b.orden)
            if (o != 0) o else { val kk = k(a, b) - k(b, a); if (kk != 0) kk else a.id.compareTo(b.id) }
        }
        val col = HashMap<String, Int>()
        val libreDesde = ArrayList<Double>()
        for (t in tramos.sortedWith(compareBy({ it.ini }, { it.id }))) {
            var c = 0
            while (c < libreDesde.size && !(libreDesde[c] < t.ini)) c++
            if (c == libreDesde.size) libreDesde += t.fin else libreDesde[c] = t.fin
            col[t.id] = c + 1
        }
        val filasDe = tramos.associate { t ->
            val a = filas.indexOfFirst { it.tipo == "inicio" && it.id == t.id }
            var b = a
            filas.forEachIndexed { i, f -> if ((f.tipo == "fin" && f.id == t.id) || (f.tipo == "hito" && f.rama == t.id)) b = maxOf(b, i) }
            t.id to (a to b)
        }
        val out = filas.mapIndexed { i, f ->
            val c = if (f.tipo == "hito") (if (f.rama.isNotEmpty()) col.getValue(f.rama) else 0) else col.getValue(f.id)
            val activas = (listOf(0) + tramos.filter { t -> val (a, b) = filasDe.getValue(t.id); i in a..b }.map { col.getValue(it.id) }).distinct().sorted()
            Fila(f.tipo, f.id, c, activas)
        }
        return Linea(out, 1 + libreDesde.size, tramos.associate { it.id to Tramo(it.ini, it.fin, it.une, col.getValue(it.id)) })
    }

    /** El orden para poner algo entre a y b (cualquiera de los dos puede faltar). */
    fun entre(a: Double?, b: Double?): Double = when {
        a == null && b == null -> 1000.0
        a == null -> b!! - 1000
        b == null -> a + 1000
        else -> (a + b) / 2
    }
}
