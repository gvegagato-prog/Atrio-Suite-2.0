package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Un documento de «Escribir» (obra, capítulo, escena, ficha, nota…): un JSON con `id`, `t` (tipo), `pid` (la obra), `u`
 * (hora de la última edición) y lo suyo. El formato está en docs/escribir.md y es el mismo en el PC.
 */
class Doc(val o: JSONObject) {
    val id: String get() = o.getString("id")
    val t: String get() = o.getString("t")
    val pid: String get() = o.optString("pid")
    val u: Long get() = o.optLong("u")
    val borrado: Boolean get() = o.optInt("d") == 1
    fun s(k: String, def: String = ""): String = o.optString(k, def)
    fun n(k: String, def: Long = 0L): Long = o.optLong(k, def)
    fun i(k: String, def: Int = 0): Int = o.optInt(k, def)
    fun d(k: String, def: Double = 0.0): Double = o.optDouble(k, def)
    fun b(k: String): Boolean = o.optInt(k) == 1 || o.optBoolean(k)
    fun lista(k: String): List<String> = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
    val orden: Double get() = o.optDouble("orden", 0.0)
    override fun toString() = o.toString()
}

/** Una instantánea del texto de una escena (el historial de versiones; no viaja con la cuenta). */
class Version(val u: Long, val palabras: Int, val texto: String)

/** Un resultado de buscar en la obra. */
class Hallazgo(val doc: Doc, val donde: String, val extracto: String, val desde: Int)

/**
 * Todo lo que escribes: obras, capítulos, escenas, fichas, notas… Se guarda en archivos del móvil (un documento por
 * archivo, así una novela larga no es un solo bloque), se puede escribir sin conexión y, con tu cuenta, cada cambio sube
 * en cuanto hay red y llega al momento a tus otros dispositivos (ver docs/escribir.md).
 */
class Escritos(private val dir: File, private val dev: () -> String = { "local" }) {
    companion object {
        const val T_OBRA = "p"; const val T_CAP = "c"; const val T_ESCENA = "e"; const val T_FICHA = "f"
        const val T_NOTA = "n"; const val T_BORRADOR = "b"; const val T_HITO = "h"; const val T_LOG = "l"
        private const val LIMPIEZA = 180L * 86_400_000L
        /** Lo borrado se queda en la papelera 30 días; luego solo queda la marca de borrado (para tus otros dispositivos). */
        const val PAPELERA = 30L * 86_400_000L
        const val VERSIONES = 40
        /** Cuántas versiones de la nube se guardan de cada documento (la base para juntar lo cambiado a la vez en dos sitios). */
        private const val BASES = 3
        private val LAPIDA = setOf("id", "t", "pid", "u", "pu", "d")

        /** Una marca de borrado sin nada dentro (lo que queda al vaciar la papelera, o lo que dejaban las versiones antiguas). */
        fun lapida(o: JSONObject): Boolean = o.optInt("d") == 1 && o.keys().asSequence().all { it in LAPIDA }
        const val ESPERA = 700L

        @Volatile private var inst: Escritos? = null
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var programada: Job? = null
        private var fallos = 0
        @Volatile private var ordenada = 0L
        private val escuchando = java.util.concurrent.atomic.AtomicBoolean(false)

        /** Los escritos de esta app. */
        fun get(ctx: Context): Escritos {
            val a = ctx.applicationContext
            return inst ?: synchronized(this) {
                inst ?: Escritos(File(a.filesDir, "escritos")) { Store(a).accDevice.ifBlank { "movil" } }.also { it.alCambiar = { pronto(a) }; inst = it }
            }
        }

        /** Solo para las pruebas: empezar de cero. */
        fun reiniciaParaPruebas() { synchronized(this) { inst?.parar(); inst = null; programada?.cancel(); programada = null; fallos = 0; ordenada = 0L } }

        fun nuevoId(t: String): String = t + "_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)

        /** Días desde 1970 (en la hora de aquí). */
        fun hoy(): Long = (System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())) / 86_400_000L

        private val PALABRA = Regex("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*")

        /** Cuántas palabras tiene un texto (los signos del formato no cuentan). */
        fun palabras(texto: String): Int = PALABRA.findAll(texto).count()

        /** Un orden que cae entre dos (los dos pueden ser null: el principio o el final). */
        fun ordenEntre(antes: Double?, despues: Double?): Double = when {
            antes == null && despues == null -> 1000.0
            antes == null -> despues!! - 1000.0
            despues == null -> antes + 1000.0
            else -> (antes + despues) / 2
        }

        /**
         * Programa subir lo pendiente (y bajar lo nuevo) dentro de [ms]. Sin conexión se reintenta solo (5 s, 15 s, 45 s…
         * hasta 5 min) y, por si se cierra la app, queda una tarea para cuando haya red.
         */
        fun pronto(ctx: Context, ms: Long = ESPERA) {
            val c = ctx.applicationContext
            if (!Store(c).paired) return
            synchronized(this) {
                programada?.cancel()
                programada = scope.launch {
                    delay(ms)
                    withContext(NonCancellable) { sincroniza(c) }
                }
            }
        }

        /** Sube y baja ahora. Devuelve false si no ha podido (se reintenta solo). */
        suspend fun sincroniza(ctx: Context): Boolean {
            val c = ctx.applicationContext
            val st = Store(c)
            if (!st.paired) return true
            val e = get(c)
            return try {
                e.sincroniza(st)
                fallos = 0
                if (System.currentTimeMillis() - ordenada > 6 * 3_600_000L) { ordenada = System.currentTimeMillis(); e.purga(); runCatching { e.limpia(st) } }
                if (e.pendientes().isNotEmpty()) pronto(c)
                true
            } catch (ex: Exception) {
                fallos++
                e.error = ex.message ?: "sin conexión"
                if (fallos <= 8) pronto(c, minOf(300_000L, 5_000L * Math.pow(3.0, (fallos - 1).toDouble()).toLong()))
                CloudSave.enqueue(c)
                false
            }
        }

        /** Baja lo nuevo de tus otros dispositivos (varias peticiones seguidas se juntan en una). */
        fun bajaPronto(ctx: Context) {
            val c = ctx.applicationContext
            if (!Store(c).paired || !escuchando.compareAndSet(false, true)) return
            scope.launch {
                delay(150)
                escuchando.set(false)
                runCatching { sincroniza(c) }
            }
        }
    }

    private val docs = ConcurrentHashMap<String, Doc>()
    private val acked = ConcurrentHashMap<String, Long>()
    private val lock = Any()
    private val mutex = Mutex()
    private val disco = Executors.newSingleThreadExecutor { r -> Thread(r, "escritos-disco").apply { isDaemon = true } }
    private val cambios = mutableIntStateOf(0)

    /** Sube con cada cambio (las pantallas lo leen para repintarse). */
    var version: Int
        get() = cambios.intValue
        private set(v) { cambios.intValue = v }

    /** Se llama con cada cambio hecho aquí (la app lo usa para subirlo en cuanto toca). */
    @Volatile var alCambiar: (() -> Unit)? = null

    /** Último error al sincronizar ("" si va bien). */
    @Volatile var error: String = ""
    /** Última vez que se sincronizó bien. */
    @Volatile var ultima: Long = 0L

    init { carga() }

    private fun archivo(id: String) = File(File(dir, "d"), "$id.json")
    private fun indice() = File(dir, "acked.json")

    private fun carga() {
        File(dir, "d").mkdirs()
        File(dir, "d").listFiles()?.forEach { f ->
            if (!f.name.endsWith(".json")) return@forEach
            runCatching { Doc(JSONObject(f.readText())) }.getOrNull()?.let { docs[it.id] = it }
        }
        runCatching {
            val o = JSONObject(indice().takeIf { it.exists() }?.readText() ?: "{}")
            o.keys().forEach { acked[it] = o.optLong(it) }
        }
    }

    private fun graba(f: File, txt: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(txt)
        if (!tmp.renameTo(f)) { f.writeText(txt); tmp.delete() }
    }

    private fun guarda(d: Doc) { val txt = d.o.toString(); disco.execute { runCatching { graba(archivo(d.id), txt) } } }
    private fun guardaIndice() {
        val txt = JSONObject().apply { acked.forEach { (k, v) -> put(k, v) } }.toString()
        disco.execute { runCatching { graba(indice(), txt) } }
    }

    /** Espera a que se haya escrito todo en disco (para las pruebas y al cerrar). */
    fun vacia() { disco.submit {}.get() }

    fun parar() { vacia() }

    private fun avisa() { version = version + 1 }

    // ------------------------------------------------------------------ Lectura

    fun get(id: String): Doc? = docs[id]?.takeIf { !it.borrado }
    fun todos(): List<Doc> = docs.values.filter { !it.borrado }
    fun deTipo(t: String, pid: String? = null): List<Doc> =
        docs.values.filter { it.t == t && !it.borrado && (pid == null || it.pid == pid) }.sortedWith(compareBy({ it.orden }, { it.u }))

    fun obras(): List<Doc> = deTipo(T_OBRA)
    fun capitulos(pid: String): List<Doc> = deTipo(T_CAP, pid)
    fun escenas(cid: String): List<Doc> = docs.values.filter { it.t == T_ESCENA && !it.borrado && it.s("cid") == cid }.sortedWith(compareBy({ it.orden }, { it.u }))
    fun escenasDe(pid: String): List<Doc> = capitulos(pid).flatMap { escenas(it.id) }
    fun fichas(pid: String): List<Doc> = deTipo(T_FICHA, pid)
    fun notas(pid: String): List<Doc> = deTipo(T_NOTA, pid)
    fun hitos(pid: String): List<Doc> = deTipo(T_HITO, pid)
    /** La última escena que se tocó (aquí o en otro dispositivo), de una obra que no esté archivada: para «seguir escribiendo». */
    fun ultimaEscena(): Doc? = docs.values.filter { it.t == T_ESCENA && !it.borrado && get(it.pid)?.b("arch") == false && get(it.s("cid")) != null }.maxByOrNull { it.u }
    fun borradores(eid: String): List<Doc> = docs.values.filter { it.t == T_BORRADOR && !it.borrado && it.s("eid") == eid }.sortedBy { it.u }

    // ------------------------------------------------------------------ Cambios

    /**
     * Pone o cambia un documento. La hora de edición siempre sube (aunque dos cambios caigan en el mismo milisegundo) y
     * apunta en «pu» de qué versión de la nube parte, para poder juntarlo luego con lo que se cambie a la vez en otro sitio.
     */
    fun pon(o: JSONObject): Doc {
        val d = synchronized(lock) { ponSin(o) }
        avisa()
        alCambiar?.invoke()
        return d
    }

    /** [pon] sin avisar (con el candado puesto): para cambiar varios de una vez. */
    private fun ponSin(o: JSONObject): Doc {
        val id = o.getString("id")
        val antes = docs[id]
        // Se deja de estar igual que en la nube: esa versión es la base si luego hay que juntar
        if (antes != null && antes.t != T_LOG && acked[id] == antes.u) apuntaBase(antes.o)
        o.put("u", maxOf(System.currentTimeMillis(), (antes?.u ?: 0L) + 1))
        if (o.optString("t") == T_LOG) o.remove("pu") else o.put("pu", acked[id] ?: 0L)
        val d = Doc(o)
        docs[id] = d
        guarda(d)
        return d
    }

    /** Crea un documento nuevo del tipo [t] en la obra [pid] (con orden al final si no se dice). */
    fun nuevo(t: String, pid: String, campos: JSONObject.() -> Unit = {}): Doc {
        val id = nuevoId(t)
        val o = JSONObject().put("id", id).put("t", t).put("pid", if (t == T_OBRA) id else pid)
        o.campos()
        if (!o.has("orden")) {
            val ult = docs.values.filter { it.t == t && it.pid == pid && !it.borrado && (t != T_ESCENA || it.s("cid") == o.optString("cid")) }.maxOfOrNull { it.orden }
            o.put("orden", (ult ?: 0.0) + 1000.0)
        }
        if (t == T_OBRA && !o.has("creado")) o.put("creado", System.currentTimeMillis())
        if (t == T_ESCENA && !o.has("palabras")) o.put("palabras", palabras(o.optString("texto")))
        return pon(o)
    }

    /** Cambia campos de un documento. */
    fun edita(id: String, cambia: JSONObject.() -> Unit): Doc? {
        val d = get(id) ?: return null
        val o = JSONObject(d.o.toString())
        o.cambia()
        if (d.t == T_ESCENA && o.optString("texto") != d.s("texto")) o.put("palabras", palabras(o.optString("texto")))
        return pon(o)
    }

    /** El texto de una escena (cuenta las palabras escritas hoy y guarda). */
    fun escribe(eid: String, texto: String): Doc? {
        val d = get(eid) ?: return null
        if (d.s("texto") == texto) return d
        val antes = d.i("palabras")
        val ahora = palabras(texto)
        val r = edita(eid) { put("texto", texto) }
        if (ahora > antes) registra(d.pid, ahora - antes)
        return r
    }

    /**
     * Manda un documento a la papelera (con lo que va dentro: una obra con todo lo suyo, un capítulo con sus escenas, una
     * escena con sus borradores). Se borra también en tus otros dispositivos, pero se puede recuperar durante 30 días.
     * Las palabras escritas cada día no se borran (las escribiste).
     */
    fun borra(id: String) {
        val d = get(id) ?: return
        val vivos = todos()
        fun conBorradores(e: Doc) = listOf(e.id) + vivos.filter { it.t == T_BORRADOR && it.s("eid") == e.id }.map { it.id }
        val ids = when (d.t) {
            T_OBRA -> listOf(id) + vivos.filter { it.pid == id && it.id != id && it.t != T_LOG }.map { it.id }
            T_CAP -> listOf(id) + vivos.filter { it.t == T_ESCENA && it.s("cid") == id }.flatMap { conBorradores(it) }
            T_ESCENA -> conBorradores(d)
            else -> listOf(id)
        }
        val ahora = System.currentTimeMillis()
        synchronized(lock) {
            ids.forEach { i ->
                val x = docs[i] ?: return@forEach
                val o = JSONObject(x.o.toString()).put("d", 1).put("dt", ahora)
                if (i == id) o.remove("dc") else o.put("dc", id)
                ponSin(o)
            }
        }
        avisa()
        alCambiar?.invoke()
    }

    private fun enPapelera(d: Doc?) = d != null && d.borrado && !lapida(d.o)

    /** Lo que hay en la papelera (lo que se borró, sin lo que se fue con ello), lo último primero. */
    fun papelera(): List<Doc> = docs.values.filter { d ->
        enPapelera(d) && d.t != T_LOG && (d.s("dc").isEmpty() || !enPapelera(docs[d.s("dc")]))
    }.sortedWith(compareByDescending<Doc> { it.n("dt", it.u) }.thenByDescending { it.u })

    /** Lo que se fue a la papelera con [id] (las escenas de un capítulo, lo de una obra…). */
    fun conElBorrado(id: String): List<Doc> = docs.values.filter { enPapelera(it) && it.s("dc") == id }

    /** Un documento de la papelera (para enseñarlo: su obra, su capítulo…), o uno vivo. */
    fun cualquiera(id: String): Doc? = docs[id]?.takeIf { !it.borrado || enPapelera(it) }

    /**
     * Saca algo de la papelera, con lo que se fue con ello. Si estaba dentro de algo que también se borró (una escena de
     * un capítulo borrado, un capítulo de una obra borrada), eso vuelve también, para que aparezca donde estaba.
     */
    fun restaura(id: String) {
        val d = docs[id]
        if (!enPapelera(d)) return
        val ids = LinkedHashSet<String>()
        fun con(x: Doc) { if (ids.add(x.id)) conElBorrado(x.id).forEach { con(it) } }
        fun dentroDe(x: Doc): List<String> = when (x.t) {
            T_OBRA -> emptyList()
            T_ESCENA -> listOf(x.s("cid"), x.pid)
            T_BORRADOR -> listOf(x.s("eid"), x.pid)
            else -> listOf(x.pid)
        }
        fun padres(x: Doc) { dentroDe(x).forEach { pid -> docs[pid]?.takeIf { enPapelera(it) && it.id !in ids }?.let { p -> con(p); padres(p) } } }
        con(d!!)
        padres(d)
        synchronized(lock) {
            ids.forEach { i ->
                val x = docs[i] ?: return@forEach
                val o = JSONObject(x.o.toString()); o.remove("d"); o.remove("dt"); o.remove("dc")
                ponSin(o)
            }
        }
        avisa()
        alCambiar?.invoke()
    }

    /** Borra para siempre lo que está en la papelera: [id] (con lo suyo), o todo si es null, o lo de hace más de [antesDe]. */
    fun purga(id: String? = null, antesDe: Long = System.currentTimeMillis() - PAPELERA) {
        val ids = LinkedHashSet<String>()
        fun con(x: Doc) { if (ids.add(x.id)) conElBorrado(x.id).forEach { con(it) } }
        when {
            id != null -> docs[id]?.takeIf { enPapelera(it) }?.let { con(it) }
            else -> docs.values.filter { enPapelera(it) && it.n("dt", it.u) < antesDe }.forEach { con(it) }
        }
        if (ids.isEmpty()) return
        synchronized(lock) {
            ids.forEach { i ->
                val x = docs[i] ?: return@forEach
                ponSin(JSONObject().put("id", x.id).put("t", x.t).put("pid", x.pid).put("d", 1))
                runCatching { archivoVersiones(i).delete() }
                runCatching { archivoBase(i).delete() }
            }
        }
        avisa()
        alCambiar?.invoke()
    }

    /** Vacía la papelera. */
    fun vaciaPapelera() = purga(antesDe = Long.MAX_VALUE)

    /** Pone [id] entre [antes] y [despues] (sus ids; null = el principio o el final). Para arrastrar. */
    fun mueve(id: String, antes: String?, despues: String?) {
        val a = antes?.let { docs[it]?.orden }
        val b = despues?.let { docs[it]?.orden }
        edita(id) { put("orden", ordenEntre(a, b)) }
    }

    /** Mueve una escena a otro capítulo, entre dos escenas. */
    fun mueveEscena(id: String, cid: String, antes: String?, despues: String?) {
        val a = antes?.let { docs[it]?.orden }
        val b = despues?.let { docs[it]?.orden }
        edita(id) { put("cid", cid); put("orden", ordenEntre(a, b)) }
    }

    /** Duplica una escena (con su texto) justo debajo. */
    fun duplicaEscena(id: String): Doc? {
        val d = get(id) ?: return null
        val sig = escenas(d.s("cid")).firstOrNull { it.orden > d.orden }
        val c = nuevo(T_ESCENA, d.pid) {
            put("cid", d.s("cid")); put("titulo", d.s("titulo") + " (copia)"); put("texto", d.s("texto")); put("resumen", d.s("resumen"))
            put("orden", ordenEntre(d.orden, sig?.orden))
        }
        return c
    }

    // ------------------------------------------------------------------ Palabras del día

    private fun idLog(pid: String, dia: Long) = "l_${pid}_$dia"

    /** Apunta [n] palabras escritas hoy en la obra [pid] (en este dispositivo). */
    fun registra(pid: String, n: Int) {
        if (n <= 0) return
        val id = idLog(pid, hoy())
        val d = docs[id]
        val dv = d?.o?.optJSONObject("dev")?.let { JSONObject(it.toString()) } ?: JSONObject()
        dv.put(dev(), dv.optInt(dev()) + n)
        pon(JSONObject().put("id", id).put("t", T_LOG).put("pid", pid).put("dia", hoy()).put("dev", dv))
    }

    private fun total(d: Doc): Int = d.o.optJSONObject("dev")?.let { dv -> dv.keys().asSequence().sumOf { dv.optInt(it) } } ?: 0

    /** Palabras escritas ese día (en la obra [pid], o en todas si es null), sumando todos tus dispositivos. */
    fun escritas(dia: Long, pid: String? = null): Int = docs.values.filter { it.t == T_LOG && !it.borrado && it.n("dia") == dia && (pid == null || it.pid == pid) }.sumOf { total(it) }

    /** Palabras de cada día de los últimos [dias] (el último es hoy). */
    fun ultimosDias(dias: Int, pid: String? = null): List<Pair<Long, Int>> = (hoy() - dias + 1..hoy()).map { it to escritas(it, pid) }

    /** Días seguidos escribiendo (si hoy aún no, cuenta desde ayer). */
    fun racha(pid: String? = null): Int {
        var d = hoy()
        if (escritas(d, pid) == 0) d--
        var n = 0
        while (escritas(d, pid) > 0) { n++; d-- }
        return n
    }

    /** Días distintos en los que has escrito algo en la obra. */
    fun diasEscritos(pid: String): Int = docs.values.filter { it.t == T_LOG && !it.borrado && it.pid == pid && total(it) > 0 }.map { it.n("dia") }.toSet().size
    fun palabrasObra(pid: String): Int = escenasDe(pid).sumOf { it.i("palabras") }
    fun palabrasCapitulo(cid: String): Int = escenas(cid).sumOf { it.i("palabras") }

    // ------------------------------------------------------------------ Versiones (solo en este dispositivo)

    private fun archivoVersiones(eid: String) = File(File(dir, "v"), "$eid.json")

    fun versiones(eid: String): List<Version> = runCatching {
        val a = JSONArray(archivoVersiones(eid).takeIf { it.exists() }?.readText() ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Version(o.optLong("u"), o.optInt("p"), o.optString("t")) } }
    }.getOrDefault(emptyList())

    /** Guarda una instantánea si el texto cambió desde la última y ha pasado [hueco] ms (o [fuerza]). */
    fun instantanea(eid: String, texto: String, hueco: Long = 300_000L, fuerza: Boolean = false): Boolean {
        if (texto.isBlank()) return false
        val v = versiones(eid)
        val ult = v.lastOrNull()
        if (ult != null && ult.texto == texto) return false
        if (!fuerza && ult != null && System.currentTimeMillis() - ult.u < hueco) return false
        val a = JSONArray()
        (v + Version(System.currentTimeMillis(), palabras(texto), texto)).takeLast(VERSIONES).forEach { a.put(JSONObject().put("u", it.u).put("p", it.palabras).put("t", it.texto)) }
        archivoVersiones(eid).let { f -> f.parentFile?.mkdirs(); runCatching { graba(f, a.toString()) } }
        return true
    }

    // ------------------------------------------------------------------ Buscar

    /** Busca [q] en el manuscrito, las fichas y las notas de la obra (sin mirar mayúsculas ni acentos). */
    fun busca(pid: String, q: String, limite: Int = 200): List<Hallazgo> {
        val n = norma(q)
        if (n.isBlank()) return emptyList()
        val out = ArrayList<Hallazgo>()
        fun mira(d: Doc, donde: String, txt: String) {
            val t = norma(txt)
            var i = t.indexOf(n)
            var veces = 0
            while (i >= 0 && out.size < limite && veces < 8) {
                val a = (i - 40).coerceAtLeast(0); val b = (i + n.length + 60).coerceAtMost(txt.length)
                out += Hallazgo(d, donde, (if (a > 0) "…" else "") + txt.substring(a, b).replace('\n', ' ') + (if (b < txt.length) "…" else ""), i)
                veces++
                i = t.indexOf(n, i + n.length)
            }
        }
        escenasDe(pid).forEach { mira(it, "Escena · " + it.s("titulo"), it.s("texto")); mira(it, "Escena · " + it.s("titulo"), it.s("resumen")) }
        fichas(pid).forEach { mira(it, "Ficha · " + it.s("nombre"), it.s("nombre") + " " + it.s("alias") + " " + it.s("resumen") + " " + it.s("texto")) }
        notas(pid).forEach { mira(it, "Nota · " + it.s("titulo"), it.s("titulo") + " " + it.s("texto")) }
        return out
    }

    /** Cambia [q] por [por] en todas las escenas de la obra (o solo en las dadas). Devuelve cuántas veces. */
    fun reemplaza(pid: String, q: String, por: String, soloEscenas: List<String>? = null, mayusculas: Boolean = false): Int {
        if (q.isEmpty()) return 0
        var n = 0
        val rx = if (mayusculas) Regex(Regex.escape(q)) else Regex(Regex.escape(q), RegexOption.IGNORE_CASE)
        escenasDe(pid).filter { soloEscenas == null || it.id in soloEscenas }.forEach { e ->
            val t = e.s("texto")
            val c = rx.findAll(t).count()
            if (c > 0) {
                n += c
                instantanea(e.id, t, fuerza = true)
                escribe(e.id, rx.replace(t, Regex.escapeReplacement(por)))
            }
        }
        return n
    }

    private fun norma(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    /** Las fichas de la obra que nombran un texto (por su nombre o alias): para enseñar «quién sale aquí». */
    fun fichasEn(pid: String, texto: String): List<Doc> {
        val t = " " + norma(texto).replace(Regex("[^\\p{L}\\p{N}]+"), " ") + " "
        return fichas(pid).filter { f ->
            (listOf(f.s("nombre")) + f.s("alias").split(',')).map { norma(it).trim().replace(Regex("[^\\p{L}\\p{N}]+"), " ") }
                .filter { it.length >= 2 }.any { t.contains(" $it ") }
        }
    }

    // ------------------------------------------------------------------ Sincronización

    /** Documentos con cambios que aún no están en la nube (su hora no es la última que se vio allí). */
    fun pendientes(): List<Doc> = docs.values.filter { (acked[it.id] ?: -1L) != it.u }

    /** Sube lo pendiente y baja lo nuevo. Una a la vez. Lanza un error si no hay conexión. */
    suspend fun sincroniza(st: Store) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!st.paired) return@withContext
            bajaYa(st)
            subeYa(st)
            error = ""
            ultima = System.currentTimeMillis()
            avisa()
        }
    }

    private fun base(st: Store) = "/users/${st.accUid}"

    private fun subeYa(st: Store) {
        var quedan = pendientes().sortedBy { it.u }
        while (quedan.isNotEmpty()) {
            val lote = ArrayList<Doc>(); var peso = 0
            for (d in quedan) { val len = d.o.toString().length; if (lote.isNotEmpty() && (peso + len > 600_000 || lote.size >= 40)) break; lote.add(d); peso += len }
            val body = JSONObject()
            lote.forEach { body.put("escritos/${it.id}", it.o); body.put("escritosIdx/${it.id}", it.u); intentos.getOrPut(it.id) { ConcurrentHashMap.newKeySet() }.add(it.u) }
            Account.db(st, "PATCH", base(st), body.toString(), "print=silent", ok404 = false)
            synchronized(lock) {
                lote.forEach { s ->
                    acked[s.id] = s.u
                    intentos.remove(s.id)
                    // Si mientras subía se siguió escribiendo, lo de ahora parte de lo que acaba de subir
                    val ahora = docs[s.id]
                    if (ahora != null && ahora.u != s.u && ahora.t != T_LOG) {
                        apuntaBase(s.o)
                        docs[s.id] = Doc(JSONObject(ahora.o.toString()).put("pu", s.u)).also { guarda(it) }
                    }
                }
            }
            guardaIndice()
            quedan = pendientes().sortedBy { it.u }.filter { q -> lote.none { it.id == q.id && it.u == q.u } }
        }
    }

    private fun bajaYa(st: Store) {
        val txt = Account.db(st, "GET", base(st) + "/escritosIdx", null) ?: "{}"
        val idx = JSONObject(txt)
        val faltan = ArrayList<String>()
        idx.keys().forEach { id ->
            val ru = idx.optLong(id)
            // (con la misma hora que lo de aquí también se baja: dos dispositivos pueden guardar en el mismo milisegundo)
            if (acked[id] == ru) return@forEach
            faltan += id
        }
        if (faltan.isEmpty()) { guardaIndice(); return }
        val remotos: JSONObject = if (faltan.size > 15) JSONObject(Account.db(st, "GET", base(st) + "/escritos", null) ?: "{}")
            else JSONObject().also { r -> faltan.forEach { id -> Account.db(st, "GET", base(st) + "/escritos/$id", null)?.let { r.put(id, JSONObject(it)) } } }
        var cambio = false
        faltan.forEach { id -> remotos.optJSONObject(id)?.let { if (aplica(id, it)) cambio = true } }
        guardaIndice()
        if (cambio) avisa()
    }

    /**
     * Mezcla un documento que ha llegado de la nube. Devuelve true si cambia lo que se ve aquí.
     *
     * Si aquí no había cambios y lo de fuera parte de lo de aquí, se queda lo de fuera. Si se cambió en los dos sitios a la
     * vez (o lo de fuera se subió sin haber visto lo de aquí), se juntan a partir de la versión de la que salen los dos (la
     * base): lo que cambió en uno solo se queda, y si los dos cambiaron el mismo campo o el mismo párrafo, manda el más
     * nuevo; en una escena, el texto que no se queda se guarda como borrador (no se pierde nada). Ver docs/escribir.md.
     */
    internal fun aplica(id: String, r: JSONObject): Boolean {
        if (!r.has("id")) r.put("id", id)
        val ru = r.optLong("u")
        synchronized(lock) {
            val l = docs[id]
            val ack = acked[id] ?: 0L
            if (l == null) { adopta(r); return true }
            if (l.t == T_LOG || r.optString("t") == T_LOG) return mezclaLog(l, r)
            val lu = l.u
            // Misma hora y mismo contenido: es la misma versión. Misma hora y otro contenido: se guardó a la vez en otro sitio
            val choca = lu == ru && EscribirMezcla.contenido(l.o) != EscribirMezcla.contenido(r)
            if (lu == ru && !choca) { acked[id] = ru; return false }
            // Lo subió este dispositivo y no llegó a confirmarse (se cortó la red a mitad): ya está en lo de aquí
            if (!choca && intentos[id]?.contains(ru) == true) {
                intentos.remove(id)
                acked[id] = ru
                apuntaBase(r)
                docs[id] = Doc(JSONObject(l.o.toString()).put("pu", ru)).also { guarda(it) }
                return false
            }
            val pendiente = lu != ack || choca
            val rpu = r.optLong("pu", -1L)
            if (!pendiente) {
                // Lo de fuera parte de lo de aquí (o viene de un dispositivo con una versión antigua, sin «pu»)
                if (rpu == lu || (rpu == -1L && ru > lu)) { adopta(r); return true }
                if (rpu == -1L) { acked[id] = ru; return false }   // lo de aquí es más nuevo: queda pendiente y se sube
            }
            val anc = base(id, rpu) ?: if (pendiente) base(id, ack) else null
            // Sin base y sin cambios aquí: lo de fuera viene de lo de aquí por versiones que no se llegaron a ver
            if (!pendiente && anc == null) { adopta(r); return true }
            if (lapida(l.o) || lapida(r)) {
                // Una marca de borrado sin nada dentro no se junta: manda la más nueva
                acked[id] = ru
                if (ru > lu) { adopta(r); return true }
                return false
            }
            return junta(l, r, anc)
        }
    }

    private fun adopta(r: JSONObject) {
        val d = Doc(r)
        docs[d.id] = d
        acked[d.id] = d.u
        guarda(d)
    }

    /** Junta lo de aquí [l] con lo de fuera [r] a partir de [anc] (la versión de la que salen los dos, si se tiene). */
    private fun junta(l: Doc, r: JSONObject, anc: JSONObject?): Boolean {
        val id = l.id
        val lu = l.u; val ru = r.optLong("u")
        val nuestro = lu > ru || (lu == ru && EscribirMezcla.contenido(l.o) > EscribirMezcla.contenido(r))
        val m = EscribirMezcla.docs(anc, l.o, r, nuestro)
        if (l.t == T_ESCENA && "texto" in m.chocan) aparta(l, if (nuestro) Doc(r) else l, m.o.optString("texto"))
        apuntaBase(r)
        acked[id] = ru
        val antes = EscribirMezcla.contenido(l.o)
        if (EscribirMezcla.canon(m.o) == EscribirMezcla.contenido(r)) { adopta(r); return antes != EscribirMezcla.contenido(r) }
        m.o.put("u", maxOf(lu, ru) + 1).put("pu", ru)
        val d = Doc(m.o)
        docs[id] = d
        guarda(d)
        return antes != EscribirMezcla.contenido(m.o)
    }

    /** Guarda el texto de [pierde] como borrador de la escena (si no es el que se ha quedado). Mismo id en todos los dispositivos. */
    private fun aparta(l: Doc, pierde: Doc, queda: String) {
        val txt = pierde.s("texto")
        if (txt.isBlank() || txt == queda) return
        val bid = "bc_${l.id}_${pierde.u}"
        if (docs[bid] != null) return
        val o = JSONObject().put("id", bid).put("t", T_BORRADOR).put("pid", l.pid).put("eid", l.id).put("conflicto", 1)
            .put("nombre", "Versión en conflicto (" + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("es")).format(java.util.Date(pierde.u)) + ")")
            .put("texto", txt).put("u", pierde.u)
        docs[bid] = Doc(o)
        guarda(docs[bid]!!)
    }

    // ------------------------------------------------------------------ Bases (versiones de la nube, para juntar)

    private fun archivoBase(id: String) = File(File(dir, "b"), "$id.json")
    private val baseHecha = ConcurrentHashMap<String, Long>()
    private val intentos = ConcurrentHashMap<String, MutableSet<Long>>()

    /** Apunta [o] como una versión que está (o estuvo) en la nube: la base si luego hay que juntar cambios hechos a la vez. */
    private fun apuntaBase(o: JSONObject) {
        val id = o.optString("id"); val u = o.optLong("u")
        if (id.isEmpty() || o.optString("t") == T_LOG || lapida(o) || baseHecha[id] == u) return
        baseHecha[id] = u
        val txt = o.toString()
        disco.execute {
            runCatching {
                val f = archivoBase(id)
                val a = runCatching { JSONArray(f.readText()) }.getOrDefault(JSONArray())
                val l = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optLong("u") != u } + JSONObject(txt)
                f.parentFile?.mkdirs()
                graba(f, JSONArray().apply { l.takeLast(BASES).forEach { put(it) } }.toString())
            }
        }
    }

    /** La versión [u] de [id] que se vio en la nube (si se apuntó). */
    private fun base(id: String, u: Long): JSONObject? {
        if (u <= 0L) return null
        return disco.submit<JSONObject?> {
            runCatching {
                val a = JSONArray(archivoBase(id).readText())
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.lastOrNull { it.optLong("u") == u }
            }.getOrNull()
        }.get()
    }

    private fun mezclaLog(l: Doc, r: JSONObject): Boolean {
        val id = l.id
        val ru = r.optLong("u")
        val a = l.o.optJSONObject("dev") ?: JSONObject()
        val b = r.optJSONObject("dev") ?: JSONObject()
        val m = JSONObject()
        (a.keys().asSequence() + b.keys().asSequence()).toSet().forEach { k -> m.put(k, maxOf(a.optInt(k), b.optInt(k))) }
        val igualQueRemoto = m.keys().asSequence().all { m.optInt(it) == b.optInt(it) } && b.length() == m.length()
        val o = JSONObject(r.toString()).put("dev", m)
        if (igualQueRemoto) { o.put("u", ru) } else o.put("u", maxOf(l.u, ru) + 1)
        val d = Doc(o)
        val cambia = d.toString() != l.toString()
        docs[id] = d; acked[id] = ru; guarda(d)
        return cambia
    }

    /** Quita de aquí y de la nube las marcas de borrado de hace más de 180 días. */
    suspend fun limpia(st: Store) {
        val hace = System.currentTimeMillis() - LIMPIEZA
        val viejos = docs.values.filter { it.borrado && it.u < hace && acked[it.id] == it.u }
        if (viejos.isEmpty()) return
        if (st.paired) {
            val body = JSONObject()
            viejos.forEach { body.put("escritos/${it.id}", JSONObject.NULL); body.put("escritosIdx/${it.id}", JSONObject.NULL) }
            runCatching { Account.db(st, "PATCH", base(st), body.toString(), "print=silent", ok404 = false) }.onFailure { return }
        }
        synchronized(lock) { viejos.forEach { docs.remove(it.id); acked.remove(it.id); runCatching { archivo(it.id).delete() }; runCatching { archivoBase(it.id).delete() } } }
        guardaIndice()
    }

    /** Para los datos de una copia (exportar) y para las pruebas. */
    fun volcado(): JSONArray = JSONArray().also { a -> docs.values.sortedBy { it.id }.forEach { a.put(it.o) } }

    /** Al cerrar sesión en otra cuenta, lo de esta se queda en el dispositivo; solo se olvida lo que se sabía de la nube. */
    fun olvidaNube() { acked.clear(); guardaIndice() }

    /** Si ahora estás en otra cuenta que la última vez, todo lo escrito aquí se sube a la nueva (y se olvida lo que se sabía de la anterior). */
    fun cuenta(uid: String) {
        if (uid.isBlank()) return
        val f = File(dir, "cuenta.txt")
        val antes = runCatching { f.readText() }.getOrDefault("")
        if (antes == uid) return
        if (antes.isNotEmpty()) olvidaNube()
        runCatching { dir.mkdirs(); f.writeText(uid) }
    }
}
