package com.gonzalo.gamerate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/*
 * Escribir con tu pareja: marcar capítulos y partes como terminados, compartirlos con tu pareja para que los lea en su
 * Atrio (y los anote si quiere), y ver lo que te anota (ver docs/escribir.md, «Compartir con tu pareja»).
 *
 * - En el capítulo y la parte: «hecho» (1 = terminado) y «comp» (1 = compartido). Compartir una parte comparte todos
 *   sus capítulos, también los que pongas después.
 * - Tu pareja lee tus escritos de /users/<tú>/escritos (las reglas ya le dejan leer tu cuenta) y solo enseña lo
 *   compartido; en su móvil solo se guarda eso.
 * - Lo que anota se guarda en SU cuenta: /users/<ella>/notasLectura/<tú>/<id> = {id, cid, eid, p, cita, texto, u}
 *   (p: el número del párrafo en la escena; cita: el principio del párrafo, para encontrarlo aunque cambie de sitio).
 *   Tú lo lees de ahí (las reglas también te dejan leer su cuenta).
 */

/** Una anotación de tu pareja a un párrafo de lo que le compartes. */
class NotaLectura(val id: String, val cid: String, val eid: String, val p: Int, val cita: String, val texto: String, val u: Long) {
    fun json(): JSONObject = JSONObject().put("id", id).put("cid", cid).put("eid", eid).put("p", p).put("cita", cita).put("texto", texto).put("u", u)

    companion object {
        fun de(o: JSONObject): NotaLectura? = runCatching {
            NotaLectura(o.getString("id"), o.optString("cid"), o.optString("eid"), o.optInt("p", -1), o.optString("cita"), o.optString("texto"), o.optLong("u"))
        }.getOrNull()?.takeIf { it.texto.isNotBlank() }

        fun lista(json: String?): List<NotaLectura> {
            if (json.isNullOrBlank()) return emptyList()
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
            return o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { de(it) } }.sortedBy { it.u }.toList()
        }
    }
}

object Compartir {
    /** Los párrafos de una escena tal como se leen (sin líneas vacías ni saltos «***»): lo que se puede anotar. */
    fun parrafos(texto: String): List<String> = texto.split('\n').map { it.trim() }.filter { it.isNotEmpty() && it != "***" }

    /** El principio de un párrafo (sin formato), para citarlo en una anotación. */
    fun cita(parrafo: String): String = parrafo.replace(Regex("^(#{1,3} |> )"), "").replace("**", "").replace("*", "").take(160)

    /** ¿Está compartido el capítulo (él o su parte)? */
    fun compartido(e: Escritos, c: Doc): Boolean = c.b("comp") || (e.get(c.s("aid"))?.b("comp") == true)

    /** ¿Está terminado el capítulo (él o su parte)? */
    fun hecho(e: Escritos, c: Doc): Boolean = c.b("hecho") || (e.get(c.s("aid"))?.b("hecho") == true)

    /**
     * De todos los documentos de alguien, solo lo que comparte: sus obras (con algo compartido), las partes de esas
     * obras, los capítulos compartidos y sus escenas. Lo demás no sale de su cuenta.
     */
    fun soloLoCompartido(docs: List<JSONObject>): List<JSONObject> {
        val vivos = docs.filter { it.optInt("d") != 1 && it.has("id") && it.has("t") }
        val porId = vivos.associateBy { it.optString("id") }
        fun b(o: JSONObject?, k: String) = o != null && (o.optInt(k) == 1 || o.optBoolean(k))
        val caps = vivos.filter { it.optString("t") == Escritos.T_CAP && (b(it, "comp") || b(porId[it.optString("aid")], "comp")) }
        val cids = caps.map { it.optString("id") }.toSet()
        val pids = caps.map { it.optString("pid") }.toSet()
        val escenas = vivos.filter { it.optString("t") == Escritos.T_ESCENA && it.optString("cid") in cids }
        val obras = vivos.filter { it.optString("t") == Escritos.T_OBRA && it.optString("id") in pids }
        val partes = vivos.filter { it.optString("t") == Escritos.T_PARTE && it.optString("pid") in pids }
        // (de cada cosa, solo lo que hace falta para leerla: nada de notas privadas, metas, guía…)
        fun recorta(o: JSONObject, vararg k: String) = JSONObject().apply { (listOf("id", "t", "pid", "u", "orden") + k).forEach { if (o.has(it)) put(it, o.get(it)) } }
        return obras.map { recorta(it, "titulo", "autor", "genero", "sinopsis", "color") } +
            partes.map { recorta(it, "titulo", "resumen", "color", "comp", "hecho") } +
            caps.map { recorta(it, "titulo", "resumen", "color", "aid", "comp", "hecho") } +
            escenas.map { recorta(it, "titulo", "cid", "texto", "palabras") }
    }
}

/** Lo de tu pareja: lo que te comparte (para leerlo y anotarlo) y lo que te anota a ti. */
object Pareja {
    @Volatile private var inst: Escritos? = null

    /** Lo que te comparte tu pareja (guardado en el móvil para leerlo sin conexión). Solo para leer. */
    fun escritos(ctx: Context): Escritos {
        val a = ctx.applicationContext
        return inst ?: synchronized(this) { inst ?: Escritos(File(a.filesDir, "escritos-pareja")).also { inst = it } }
    }

    /** Solo para las pruebas. */
    fun reiniciaParaPruebas() { synchronized(this) { inst = null } }

    private fun partner(st: Store) = st.partnerUid.takeIf { st.paired && it.isNotBlank() }

    /** ¿Hay pareja con la que compartir? */
    fun hay(ctx: Context): Boolean = partner(Store(ctx)) != null

    /** El nombre de tu pareja (o «tu pareja»). */
    fun nombre(ctx: Context): String = Couple.cached(Store(ctx))?.name?.takeIf { it.isNotBlank() } ?: "tu pareja"

    /** Baja lo que te comparte ahora. Devuelve false si no ha podido (sin conexión, o aún sin permiso). */
    suspend fun bajaLoCompartido(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val st = Store(ctx)
        val p = partner(st) ?: return@withContext false
        runCatching {
            val t = Account.db(st, "GET", "/users/$p/escritos", null)
            val o = t?.let { JSONObject(it) } ?: JSONObject()
            val docs = o.keys().asSequence().mapNotNull { o.optJSONObject(it) }.toList()
            escritos(ctx).ponDeOtro(Compartir.soloLoCompartido(docs))
            true
        }.getOrDefault(false)
    }

    /** Lo que tú has anotado en lo que te comparte tu pareja. */
    suspend fun misNotas(ctx: Context): List<NotaLectura> = withContext(Dispatchers.IO) {
        val st = Store(ctx)
        val p = partner(st) ?: return@withContext emptyList()
        runCatching { NotaLectura.lista(Account.db(st, "GET", "/users/${st.accUid}/notasLectura/$p", null)) }.getOrDefault(emptyList())
    }

    /** Lo que tu pareja ha anotado en lo que tú le compartes. */
    suspend fun notasDeMiPareja(ctx: Context): List<NotaLectura> = withContext(Dispatchers.IO) {
        val st = Store(ctx)
        val p = partner(st) ?: return@withContext emptyList()
        runCatching { NotaLectura.lista(Account.db(st, "GET", "/users/$p/notasLectura/${st.accUid}", null)) }.getOrDefault(emptyList())
    }

    /** Guarda (o cambia) una anotación tuya. */
    suspend fun anota(ctx: Context, n: NotaLectura): Boolean = withContext(Dispatchers.IO) {
        val st = Store(ctx)
        val p = partner(st) ?: return@withContext false
        runCatching { Account.db(st, "PUT", "/users/${st.accUid}/notasLectura/$p/${n.id}", n.json().toString(), ok404 = false); true }.getOrDefault(false)
    }

    /** Borra una anotación tuya. */
    suspend fun borraNota(ctx: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val st = Store(ctx)
        val p = partner(st) ?: return@withContext false
        runCatching { Account.db(st, "DELETE", "/users/${st.accUid}/notasLectura/$p/$id", null, ok404 = false); true }.getOrDefault(false)
    }

    fun nuevoId(): String = "nl_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
}
