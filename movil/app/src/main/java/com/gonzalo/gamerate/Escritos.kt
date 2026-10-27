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
        const val VERSIONES = 40
        const val ESPERA = 700L

        @Volatile private var inst: Escritos? = null
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var programada: Job? = null
        private var fallos = 0
        private val escuchando = java.util.concurrent.atomic.AtomicBoolean(false)

        /** Los escritos de esta app. */
        fun get(ctx: Context): Escritos {
            val a = ctx.applicationContext
            return inst ?: synchronized(this) {
                inst ?: Escritos(File(a.filesDir, "escritos")) { Store(a).accDevice.ifBlank { "movil" } }.also { it.alCambiar = { pronto(a) }; inst = it }
            }
        }

        /** Solo para las pruebas: empezar de cero. */
        fun reiniciaParaPruebas() { synchronized(this) { inst?.parar(); inst = null; programada?.cancel(); programada = null; fallos = 0 } }

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
    fun borradores(eid: String): List<Doc> = docs.values.filter { it.t == T_BORRADOR && !it.borrado && it.s("eid") == eid }.sortedBy { it.u }

    // ------------------------------------------------------------------ Cambios

    /** Pone o cambia un documento. La hora de edición siempre sube (aunque dos cambios caigan en el mismo milisegundo). */
    fun pon(o: JSONObject): Doc {
        val d: Doc
        synchronized(lock) {
            val antes = docs[o.getString("id")]
            o.put("u", maxOf(System.currentTimeMillis(), (antes?.u ?: 0L) + 1))
            d = Doc(o)
            docs[d.id] = d
            guarda(d)
        }
        avisa()
        alCambiar?.invoke()
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

    /** Borra un documento (queda la marca, para que se borre también en tus otros dispositivos). Una obra borra todo lo suyo. */
    fun borra(id: String) {
        val d = docs[id] ?: return
        val ids = when (d.t) {
            T_OBRA -> docs.values.filter { it.pid == id }.map { it.id }
            T_CAP -> listOf(id) + docs.values.filter { it.t == T_ESCENA && it.s("cid") == id }.flatMap { e -> listOf(e.id) + docs.values.filter { it.t == T_BORRADOR && it.s("eid") == e.id }.map { it.id } }
            T_ESCENA -> listOf(id) + docs.values.filter { it.t == T_BORRADOR && it.s("eid") == id }.map { it.id }
            else -> listOf(id)
        }
        synchronized(lock) {
            val ahora = System.currentTimeMillis()
            ids.forEach { i ->
                val x = docs[i] ?: return@forEach
                val o = JSONObject().put("id", x.id).put("t", x.t).put("pid", x.pid).put("d", 1).put("u", maxOf(ahora, x.u + 1))
                docs[i] = Doc(o); guarda(docs[i]!!)
                if (x.t == T_ESCENA) runCatching { File(File(dir, "v"), "$i.json").delete() }
            }
        }
        // Los hitos que apuntaban a una escena borrada se quedan, sin escena
        if (d.t == T_ESCENA) docs.values.filter { it.t == T_HITO && it.s("eid") == id }.forEach { h -> edita(h.id) { remove("eid") } }
        avisa()
    }

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
            lote.forEach { body.put("escritos/${it.id}", it.o); body.put("escritosIdx/${it.id}", it.u) }
            Account.db(st, "PATCH", base(st), body.toString(), "print=silent", ok404 = false)
            lote.forEach { acked[it.id] = it.u }
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
            val l = docs[id]
            // (el registro del día no se da por igual solo por la hora: dos dispositivos pueden apuntarlo en el mismo milisegundo)
            if (l != null && l.u == ru && l.t != T_LOG) { acked[id] = ru; return@forEach }
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

    /** Mezcla un documento que ha llegado de la nube. Devuelve true si cambia lo que se ve aquí. */
    internal fun aplica(id: String, r: JSONObject): Boolean {
        if (!r.has("id")) r.put("id", id)
        val ru = r.optLong("u")
        synchronized(lock) {
            val l = docs[id]
            val ack = acked[id] ?: 0L
            if (l == null) { val d = Doc(r); docs[id] = d; acked[id] = ru; guarda(d); return true }
            if (l.t == T_LOG || r.optString("t") == T_LOG) return mezclaLog(l, r)
            val lu = l.u
            if (lu == ru) { acked[id] = ru; return false }
            val pendiente = lu != ack
            if (!pendiente) {
                if (ru > lu) { val d = Doc(r); docs[id] = d; acked[id] = ru; guarda(d); return true }
                acked[id] = ru   // lo de aquí es más nuevo que lo de la nube: queda pendiente y se sube
                return false
            }
            // Se cambió en los dos sitios a la vez: gana el más nuevo y el texto del otro se guarda como borrador
            val gana = if (ru > lu) Doc(r) else l
            val pierde = if (ru > lu) l else Doc(r)
            if (l.t == T_ESCENA && !gana.borrado && !pierde.borrado && pierde.s("texto").isNotBlank() && pierde.s("texto") != gana.s("texto")) {
                val bid = "bc_${id}_${pierde.u}"
                if (docs[bid] == null) {
                    val o = JSONObject().put("id", bid).put("t", T_BORRADOR).put("pid", l.pid).put("eid", id).put("conflicto", 1)
                        .put("nombre", "Versión de otro dispositivo (" + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("es")).format(java.util.Date(pierde.u)) + ")")
                        .put("texto", pierde.s("texto")).put("u", pierde.u)
                    docs[bid] = Doc(o); guarda(docs[bid]!!)
                }
            }
            acked[id] = ru
            if (ru > lu) { docs[id] = gana; guarda(gana) }
            return ru > lu
        }
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
        synchronized(lock) { viejos.forEach { docs.remove(it.id); acked.remove(it.id); runCatching { archivo(it.id).delete() } } }
        guardaIndice()
    }

    /** Para los datos de una copia (exportar) y para las pruebas. */
    fun volcado(): JSONArray = JSONArray().also { a -> docs.values.sortedBy { it.id }.forEach { a.put(it.o) } }

    /** Al cerrar sesión en otra cuenta, lo de esta se queda en el dispositivo; solo se olvida lo que se sabía de la nube. */
    fun olvidaNube() { acked.clear(); guardaIndice() }
}
