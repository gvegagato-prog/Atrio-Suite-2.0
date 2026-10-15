@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.gamerate

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Clave de una ficha para reacciones y avisos: "tipo:id". */
fun itemKey(kind: Int, id: Long) = "$kind:$id"

/** Emojis para reaccionar a lo de tu pareja. */
val ReactEmojis = listOf("❤️", "😂", "😮", "😢", "🔥", "👏", "🤢")

/** Datos de tu pareja tal como los recibió la última vez. */
class Partner(
    val name: String, val updated: Long,
    private val games: List<Game>, private val plays: List<Play>,
    private val books: List<Game>, private val bplays: List<Play>,
    val recs: List<Rec>, val reacts: Map<String, String>,
    val goalGames: Int, val goalBooks: Int,
    private val likesGames: List<Card> = emptyList(), private val likesBooks: List<Card> = emptyList(),
    /** Sus categorías propias y lo que tiene en ellas. */
    val kinds: List<Kind> = emptyList(),
    private val extraGames: Map<Int, List<Game>> = emptyMap(),
    private val extraPlays: Map<Int, List<Play>> = emptyMap(),
    /** Su último test de descubrir de cada tipo (0 juegos, 1 libros, 2 pelis y series). */
    val quizzes: Map<Int, QuizRun> = emptyMap()
) {
    /** Cartas que le han gustado en "Siguiente". */
    fun likes(kind: Kind) = if (kind.custom) emptyList() else if (kind.isBook) likesBooks else likesGames

    /** Todas sus secciones: juegos, libros y las suyas propias. */
    val allKinds: List<Kind> get() = listOf(GAMES, BOOKS) + kinds

    /** Su categoría equivalente a una tuya (las propias se emparejan por nombre). */
    fun match(kind: Kind): Kind? =
        if (!kind.custom) kind else kinds.find { it.id == kind.id && normName(it.title) == normName(kind.title) }
            ?: kinds.find { normName(it.title) == normName(kind.title) }

    /** Una de sus categorías por su id (el de su móvil). */
    fun kindById(id: Int): Kind = allKinds.find { it.id == id } ?: GAMES

    fun games(kind: Kind): List<Game> = when (kind.id) {
        0 -> games
        1 -> books
        else -> match(kind)?.let { extraGames[it.id] } ?: emptyList()
    }

    fun plays(kind: Kind): List<Play> = when (kind.id) {
        0 -> plays
        1 -> bplays
        else -> match(kind)?.let { extraPlays[it.id] } ?: emptyList()
    }
}

/** Lo de tu pareja para una sección (juegos o libros). */
class PartnerSide(
    val name: String, val games: List<Game>, val plays: List<Play>,
    /** Sus reacciones a tus fichas de esta sección: id -> emoji. */
    val reactsToMe: Map<Long, String>
)

fun Partner.side(kind: Kind) = PartnerSide(
    name, games(kind), plays(kind),
    reacts.filterKeys { it.startsWith("${kind.id}:") }
        .mapNotNull { (k, v) -> k.substringAfter(":").toLongOrNull()?.let { it to v } }.toMap()
)

/** Cómo lo ve tu pareja: notas (si las hay), opinión y estado. */
class PView(val scores: List<Int>?, val review: String, val status: String)

fun partnerView(id: Long, kind: Kind, side: PartnerSide): PView? {
    side.games.find { it.id == id }?.let { g ->
        return PView(g.scores(), g.review, "Valorado")
    }
    side.plays.find { it.id == id }?.let { p ->
        val b = p.baremo()
        val last = p.entries.filter { it.note.isNotBlank() }.maxByOrNull { it.time }?.note ?: ""
        return PView(b?.scores(), last, kind.statuses[p.status.coerceIn(0, 2)])
    }
    return null
}

/** Una copia guardada en la nube (para recuperar datos). */
class CloudNode(val key: String, val name: String, val updated: Long, val data: String, val count: Int, val mine: Boolean)

object Couple {
    /** La dirección buena acaba en firebaseio.com o firebasedatabase.app (no es la de la consola). */
    fun validUrl(u: String): Boolean {
        val h = u.trim().removePrefix("https://").removePrefix("http://").substringBefore("/").lowercase()
        return h.endsWith(".firebaseio.com") || h.endsWith(".firebasedatabase.app") || h.startsWith("10.0.2.2")
    }

    const val BAD_URL = "Esa no es la dirección de la base de datos. Tiene que acabar en firebaseio.com o firebasedatabase.app " +
        "y la ves arriba, en la pestaña Datos de Realtime Database (no la de la barra del navegador)."

    /**
     * Sube tus datos a tu cuenta y comprueba que Firebase los ha guardado de verdad (se vuelve a leer la hora que
     * ha puesto el servidor). Antes se mezcla lo que hayan cambiado tus otros dispositivos (otro móvil, Atrio…).
     * El resultado (o el error) queda apuntado para enseñarlo en Ajustes.
     */
    suspend fun push(store: Store) = withContext(Dispatchers.IO) {
        if (!store.paired) return@withContext
        try {
            syncOwn(store)
            val data = store.syncData()
            val ts = Account.upload(store, data)
            store.cloudLast = ts
            store.ownSeen = ts
            store.cloudCount = countItems(data)
            store.cloudErr = ""
        } catch (e: Exception) {
            store.cloudErr = e.message ?: "sin conexión"
            throw e
        }
        Unit
    }

    /**
     * Si otro dispositivo de tu cuenta ha subido algo después de lo último que tienes, lo baja y lo mezcla
     * (de cada ficha se queda la versión más reciente; lo borrado se borra). Devuelve true si han cambiado tus datos.
     */
    suspend fun syncOwn(store: Store): Boolean = withContext(Dispatchers.IO) {
        if (!store.paired) return@withContext false
        val changed = Account.mergeOwn(store)
        if (changed) SyncBus.changed()
        changed
    }

    /** Fichas que hay dentro de un texto de copia. */
    fun countItems(data: String): Int = try {
        val d = JSONObject(data)
        d.keys().asSequence().filter { it in setOf("games", "plays", "books", "bplays") || it.startsWith("items_") || it.startsWith("iplays_") }
            .sumOf { (d.optJSONArray(it) ?: JSONArray()).length() }
    } catch (e: Exception) { 0 }

    /** Lee de Firebase lo que hay guardado de este dispositivo: (hora del servidor, fichas), o null si no hay nada. */
    suspend fun check(store: Store): Pair<Long, Int>? = withContext(Dispatchers.IO) {
        val t = Account.db(store, "GET", "/users/${store.accUid}/dev/${store.accDevice}", null) ?: return@withContext null
        val o = JSONObject(t)
        o.optLong("updated", 0L) to countItems(o.optString("data", "{}"))
    }

    /** Baja lo último de tu pareja (null si aún no ha subido nada o todavía no ha puesto tu usuario en su app). */
    suspend fun pull(store: Store): Partner? = withContext(Dispatchers.IO) {
        if (!store.paired || store.partnerUid.isBlank()) return@withContext null
        val o = Account.latest(store, store.partnerUid) ?: return@withContext null
        val p = parse(o)
        o.remove("_dev")
        store.partnerRaw = o.toString()
        store.partnerId = store.partnerUid
        if (store.partnerUser.isBlank() && o.optString("user").isNotBlank()) store.partnerUser = o.optString("user")
        p
    }

    /** ¿Tu pareja te deja ver lo suyo? (ha puesto tu usuario en su app) */
    suspend fun partnerShares(store: Store): Boolean = withContext(Dispatchers.IO) {
        store.partnerUid.isNotBlank() && Account.stamps(store, store.partnerUid) != null
    }

    /** Petición sin sesión: solo para las copias antiguas con código de pareja. */
    private fun legacyGet(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        if (c.responseCode !in 200..299) return null
        val t = c.inputStream.bufferedReader().readText().trim()
        return if (t.isEmpty() || t == "null" || t.startsWith("<")) null else t
    }

    /** Copias guardadas: las de cada dispositivo de tu cuenta y, si quedan, las antiguas del código de pareja. */
    suspend fun nodes(store: Store): List<CloudNode> = withContext(Dispatchers.IO) {
        val out = ArrayList<CloudNode>()
        fun add(all: JSONObject, mine: String, tag: String) {
            all.keys().asSequence().forEach { k ->
                val o = all.optJSONObject(k) ?: return@forEach
                val data = o.optString("data", "")
                val dev = when (o.optString("device")) { "atrio" -> " · Atrio"; "android" -> " · Android"; else -> "" }
                out.add(CloudNode(k, o.optString("name", "?") + dev + tag, o.optLong("updated", 0L), data, countItems(data), k == mine))
            }
        }
        if (store.paired) {
            Account.db(store, "GET", "/users/${store.accUid}/dev", null)?.let { add(JSONObject(it), store.accDevice, "") }
        }
        if (store.coupleCode.isNotBlank()) {
            try { legacyGet(store.dbUrl + "/couples/" + store.coupleCode + ".json")?.let { add(JSONObject(it), store.deviceId, " · copia antigua") } }
            catch (e: Exception) { /* las reglas ya no dejan leerlas */ }
        }
        out.sortedByDescending { it.updated }
    }

    /** Las fotos de su galería solo existen en su móvil: aquí se quitan para que salga la portada vacía. */
    private fun noLocal(l: List<Game>) = l.map { if (Photos.isLocal(it.cover) || Photos.isLocal(it.hero)) it.copy(cover = it.cover.takeUnless { c -> Photos.isLocal(c) } ?: "", hero = it.hero.takeUnless { h -> Photos.isLocal(h) } ?: "") else it }
    private fun noLocalP(l: List<Play>) = l.map { if (Photos.isLocal(it.cover) || Photos.isLocal(it.hero)) it.copy(cover = it.cover.takeUnless { c -> Photos.isLocal(c) } ?: "", hero = it.hero.takeUnless { h -> Photos.isLocal(h) } ?: "") else it }

    private fun parse(o: JSONObject): Partner {
        val d = JSONObject(o.getString("data"))
        val re = d.optJSONObject("reacts") ?: JSONObject()
        val goals = d.optJSONObject("goals") ?: JSONObject()
        val lk = d.optJSONObject("likes") ?: JSONObject()
        val ks = kindsFromJson(d.optJSONArray("kinds") ?: JSONArray())
        return Partner(
            o.optString("name", "Tu pareja"), o.optLong("updated", 0L),
            noLocal(gamesFromJson(d.optJSONArray("games") ?: JSONArray())),
            noLocalP(playsFromJson(d.optJSONArray("plays") ?: JSONArray())),
            noLocal(gamesFromJson(d.optJSONArray("books") ?: JSONArray())),
            noLocalP(playsFromJson(d.optJSONArray("bplays") ?: JSONArray())),
            recsFromJson(d.optJSONArray("recs") ?: JSONArray()),
            re.keys().asSequence().associateWith { re.optString(it) },
            goals.optInt("g", 0), goals.optInt("b", 0),
            cardsFromJson(lk.optJSONArray("0") ?: JSONArray()), cardsFromJson(lk.optJSONArray("1") ?: JSONArray()),
            ks,
            ks.associate { it.id to noLocal(gamesFromJson(d.optJSONArray("items_${it.id}") ?: JSONArray())) },
            ks.associate { it.id to noLocalP(playsFromJson(d.optJSONArray("iplays_${it.id}") ?: JSONArray())) },
            quizRunsFromJson(d.optJSONObject("quiz"))
        )
    }

    /** Lo último que se bajó, para verlo sin conexión. */
    fun cached(store: Store): Partner? {
        val raw = store.partnerRaw
        if (raw.isBlank()) return null
        return try { parse(JSONObject(raw)) } catch (e: Exception) { null }
    }
    // ---- Avisos de novedades ----

    /** Firma de cada cosa de tu pareja, para saber qué ha cambiado. */
    private fun signature(p: Partner): JSONObject {
        val o = JSONObject()
        for (k in p.allKinds) {
            p.games(k).forEach { g -> o.put("g${k.id}:${g.id}", "${g.general}|${g.review.length}") }
            p.plays(k).forEach { pl -> o.put("p${k.id}:${pl.id}", "${pl.status}|${pl.entries.size}") }
        }
        p.recs.forEach { r -> o.put("r:${r.id}", "1") }
        p.reacts.forEach { (k, e) -> o.put("x:$k", e) }
        for (k in listOf(GAMES, BOOKS)) p.likes(k).forEach { c -> o.put("l:${k.id}:${c.key}", "1") }
        p.quizzes.forEach { (d, r) -> o.put("q:$d", r.time.toString()) }
        return o
    }

    /** Marca como visto todo lo que hay ahora de tu pareja. */
    fun markSeen(store: Store, p: Partner) {
        store.partnerSeen = signature(p).toString()
    }

    /** Frases con lo nuevo de tu pareja desde la última vez que lo viste. */
    fun news(store: Store, p: Partner): List<String> {
        val prevText = store.partnerSeen
        if (prevText.isBlank()) return emptyList()
        val prev = try { JSONObject(prevText) } catch (e: Exception) { return emptyList() }
        val now = signature(p)
        val out = ArrayList<String>()
        val n = p.name
        for (k in p.allKinds) {
            p.games(k).forEach { g ->
                val key = "g${k.id}:${g.id}"
                val old = prev.optString(key, "")
                if (old.isEmpty()) {
                    out.add("$n ha valorado ${g.name} con un ${g.general}")
                } else if (old != now.optString(key)) {
                    val oldNote = old.substringBefore("|").toIntOrNull() ?: -1
                    val oldLen = old.substringAfter("|").toIntOrNull() ?: 0
                    if (oldNote != g.general) out.add("$n ha cambiado su nota de ${g.name}: ${g.general}")
                    else if (oldLen == 0 && g.review.isNotEmpty()) out.add("$n ha escrito su opinión de ${g.name}")
                }
            }
            p.plays(k).forEach { pl ->
                val key = "p${k.id}:${pl.id}"
                val old = prev.optString(key, "")
                val oldStatus = old.substringBefore("|").toIntOrNull() ?: -1
                val oldEntries = old.substringAfter("|").toIntOrNull() ?: 0
                if (old.isEmpty() && pl.status == 0) out.add("$n ha añadido ${pl.name} a su lista")
                else if ((old.isEmpty() || oldStatus != 1) && pl.status == 1) out.add("$n ha empezado ${pl.name}")
                else if (pl.entries.size > oldEntries && old.isNotEmpty()) out.add("$n ha apuntado notas de ${pl.name}")
            }
        }
        p.recs.forEach { r -> if (!prev.has("r:${r.id}")) out.add("$n te recomienda ${r.name}") }
        for (k in listOf(GAMES, BOOKS)) {
            val mineLikes = store.likes(k.id).map { it.key }.toSet()
            p.likes(k).forEach { c ->
                if (!prev.has("l:${k.id}:${c.key}") && c.key in mineLikes) out.add("¡Match! A los dos os apetece ${c.title}")
            }
        }
        // Tests de descubrir: aviso y, si os sale lo mismo, qué
        val myQ = store.quizzes
        p.quizzes.forEach { (d, r) ->
            if (prev.optString("q:$d", "") == r.time.toString()) return@forEach
            val what = quizWhat(d)
            val mineRun = myQ[d]
            val both = mineRun?.let { quizMatches(r.items, it.items) } ?: emptyList()
            out.add(when {
                both.isNotEmpty() -> "¡Coincidís! En el test de $what a los dos os sale ${both.first().title}" + if (both.size > 1) " (y ${both.size - 1} más)" else ""
                mineRun != null -> "$n ha hecho el test de $what; esta vez no coincidís en nada"
                else -> "$n ha hecho el test de $what: haz el tuyo y mira si coincidís"
            })
        }
        val mine = (store.load(0) + store.load(1)).associateBy { it.id } // nombres para las reacciones
        p.reacts.forEach { (k, e) ->
            if (prev.optString("x:$k", "") != e) {
                val title = k.substringAfter(":").toLongOrNull()?.let { mine[it]?.name }
                out.add(if (title != null) "$n ha reaccionado $e a ${title}" else "$n ha reaccionado $e a una de tus fichas")
            }
        }
        return out
    }
}

/**
 * Tarjeta que se da la vuelta al mantenerla pulsada y enseña lo de tu pareja.
 * Si [enabled] es false, solo reacciona al toque normal.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FlipCard(
    enabled: Boolean, onClick: () -> Unit, modifier: Modifier,
    front: @Composable () -> Unit, back: @Composable BoxScope.() -> Unit,
    shape: androidx.compose.ui.graphics.Shape = shp(18.dp)
) {
    var flipped by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(
        if (enabled && flipped) 180f else 0f,
        tween(550, easing = FastOutSlowInEasing), label = "flip"
    )
    val click by rememberUpdatedState(onClick)
    val haptic = LocalHapticFeedback.current
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.96f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 600f), label = "pulsar")
    val gestures = if (enabled) {
        // Toque normal: abre al instante. Mantener pulsado: se da la vuelta (antes era doble toque, que retrasaba cada toque).
        Modifier.graphicsLayer { scaleX = sc; scaleY = sc }.combinedClickable(
            interactionSource = src, indication = null, onClick = { click() },
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                flipped = !flipped
            }
        )
    } else {
        Modifier.pop(onClick)
    }
    Box(
        modifier.then(gestures)
            .graphicsLayer { rotationY = rot; cameraDistance = 16f * density }
            .then(if (rot > 90f) Modifier.background(UiPanel, shape).border(1.dp, Line, shape) else Modifier)
    ) {
        Box(Modifier.graphicsLayer { alpha = if (rot <= 90f) 1f else 0f }) { front() }
        if (rot > 90f) {
            Box(Modifier.matchParentSize().graphicsLayer { rotationY = 180f }) { back() }
        }
    }
}

/** Cara de atrás: lo que ha puesto tu pareja. */
@Composable
fun PartnerFace(side: PartnerSide, id: Long, kind: Kind, compact: Boolean = false) {
    val v = partnerView(id, kind, side)
    val ring = if (compact) 40.dp else 54.dp
    val small = if (compact) 11.sp else 12.sp
    Column(
        Modifier.fillMaxSize().padding(if (compact) 6.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(side.name.uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = UiSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        if (v == null) {
            Text("Todavía no lo tiene", color = UiSoft, fontSize = 13.sp, textAlign = TextAlign.Center)
        } else if (v.scores == null) {
            Text(v.status, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text("Sin notas aún", color = UiSoft, fontSize = 12.sp)
        } else {
            val avg = v.scores.subList(1, 1 + kind.crit).average().toFloat()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Ring(v.scores[0], ColGeneral, ring)
                Ring(avg.roundToInt(), ColAvg, ring)
            }
            Spacer(Modifier.height(6.dp))
            Text(v.status, color = UiSoft, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            for (i in 1..kind.crit) {
                Text(kind.labels[i] + " " + v.scores[i], fontSize = small)
            }
            if (v.review.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    v.review, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = UiSoft,
                    maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Sección «Cuenta y pareja» de Ajustes. */
@Composable
fun CoupleSettings(store: Store, onPartner: (Partner?) -> Unit, onRestored: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var signed by remember { mutableStateOf(store.paired) }
    var user by remember { mutableStateOf(store.accUser) }
    var pass by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(store.myName) }
    var key by remember { mutableStateOf(store.webKey) }
    var needKey by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(store.dbUrl) }
    var showUrl by remember { mutableStateOf(false) }
    var partnerIn by remember { mutableStateOf("") }
    var partnerUser by remember { mutableStateOf(store.partnerUser) }
    var shares by remember { mutableStateOf<Boolean?>(null) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(Couple.cached(store)) }
    var notify by remember { mutableStateOf(store.notifyPartner) }
    var cloud by remember { mutableStateOf<List<CloudNode>?>(null) }
    var restore by remember { mutableStateOf<CloudNode?>(null) }
    var mergeAsk by remember { mutableStateOf<Int?>(null) }
    var newPass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) msg = "Sin permiso de notificaciones no puedo avisarte."
    }

    fun launchOp(f: suspend () -> String) {
        scope.launch {
            busy = true
            msg = "Un momento…"
            msg = try { f() } catch (e: Exception) { "Error: " + (e.message ?: "sin conexión") }
            busy = false
        }
    }

    suspend fun refreshPartner(): Partner? {
        val p = try { Couple.pull(store) } catch (e: Exception) { null }
        info = p
        onPartner(p)
        if (p != null) Couple.markSeen(store, p)
        partnerUser = store.partnerUser
        shares = try { Couple.partnerShares(store) } catch (e: Exception) { null }
        return p
    }

    suspend fun syncNow(): String {
        Couple.push(store)
        val p = refreshPartner()
        if (store.notifyPartner) PartnerWatch.schedule(ctx)
        val up = "Subido y comprobado en la nube (${store.cloudCount} fichas)."
        return when {
            store.partnerUid.isBlank() -> up
            p == null -> "$up Tu pareja aún no ha puesto tu usuario en su app."
            else -> "$up Sincronizado con ${p.name}."
        }
    }

    /** Antes de entrar o crear la cuenta: dirección y clave web del proyecto. */
    suspend fun prepare(): Boolean {
        if (!Couple.validUrl(url)) { msg = Couple.BAD_URL; return false }
        store.dbUrl = url
        if (key.isNotBlank()) store.webKey = key
        val k = Account.findWebKey(store)
        if (k.isBlank()) {
            needKey = true
            msg = "Falta la clave web de Firebase (solo hay que ponerla una vez: después tus otros dispositivos la encuentran solos)."
            return false
        }
        key = k
        return true
    }

    fun creds(): Pair<String, String>? {
        val u = Account.normUser(user)
        if (!Account.validUser(u)) { msg = Account.BAD_USER; return null }
        if (pass.length < 6) { msg = "La contraseña tiene que tener al menos 6 caracteres."; return null }
        return u to pass
    }

    Column(Modifier.fillMaxWidth()) {
        if (!signed) {
            Text(
                "Con una cuenta de Atrio tus datos se guardan en la nube con tu usuario: los ves y los editas desde cualquier " +
                    "dispositivo (este móvil, otro, o Atrio en el ordenador) y todo se junta solo. Tu pareja te encuentra por tu usuario.",
                color = UiSoft, modifier = Modifier.padding(bottom = 10.dp)
            )
            OutlinedTextField(
                user, { user = it }, label = { Text("Usuario") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, keyboardType = KeyboardType.Ascii),
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                name, { name = it }, label = { Text("Tu nombre (lo verá tu pareja)") }, singleLine = true,
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth()
            )
            if (needKey) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    key, { key = it.trim() }, label = { Text("Clave web de Firebase") }, singleLine = true,
                    shape = shp(14.dp), modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Consola de Firebase → ⚙ Configuración del proyecto → General → «Clave de API web». Empieza por AIza.",
                    color = UiSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shape = pillShape(), enabled = !busy && user.isNotBlank() && pass.isNotBlank(), onClick = {
                    val c = creds() ?: return@Button
                    launchOp {
                        if (!prepare()) return@launchOp msg
                        Account.signIn(store, c.first, c.second)
                        if (name.isNotBlank()) store.myName = name
                        pass = ""
                        val hasCloud = withContext(Dispatchers.IO) { Account.stamps(store, store.accUid).orEmpty().isNotEmpty() }
                        val here = store.itemCount()
                        if (hasCloud && here > 0) {
                            mergeAsk = here
                            "Has entrado. Elige qué hacer con lo que ya hay en este dispositivo."
                        } else {
                            signed = true
                            val r = syncNow()
                            name = store.myName
                            onRestored()
                            "Has entrado como ${store.accUser}. $r"
                        }
                    }
                }) { Text("Entrar") }
                OutlinedButton(shape = pillShape(), enabled = !busy && user.isNotBlank() && pass.isNotBlank(), onClick = {
                    val c = creds() ?: return@OutlinedButton
                    launchOp {
                        if (!prepare()) return@launchOp msg
                        Account.signUp(store, c.first, c.second)
                        store.myName = name.ifBlank { c.first }
                        name = store.myName
                        pass = ""
                        signed = true
                        // Lo que tienes en este móvil es lo primero que se guarda en tu cuenta.
                        val r = syncNow()
                        "Cuenta creada: ${store.accUser}. $r"
                    }
                }) { Text("Crear cuenta") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton({ showUrl = !showUrl }) { Text(if (showUrl) "Ocultar base de datos" else "Otra base de datos…", color = Muted) }
            if (showUrl) {
                OutlinedTextField(
                    url, { url = it }, label = { Text("Dirección de tu Firebase Realtime Database") }, singleLine = true,
                    shape = shp(14.dp), modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Text("Has entrado como ${store.accUser}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            val last = store.cloudLast
            val err = store.cloudErr
            Text(
                when {
                    err.isNotBlank() -> "La última subida falló: $err"
                    last > 0L -> "Guardado en tu cuenta el " +
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last)) +
                        " · ${store.cloudCount} fichas. Se sube solo con cada cambio, y lo que cambies en otro dispositivo (o en Atrio) llega aquí en unos segundos."
                    else -> "Todavía no se ha subido nada."
                },
                color = if (err.isNotBlank()) ColGeneral else UiSoft, fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                name, { name = it }, label = { Text("Tu nombre (lo verá tu pareja)") }, singleLine = true,
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shape = pillShape(), enabled = !busy, onClick = {
                    store.myName = name
                    launchOp { syncNow() }
                }) { Text("Sincronizar ahora") }
                OutlinedButton(shape = pillShape(), enabled = !busy, onClick = {
                    launchOp {
                        val r = Couple.check(store)
                        val here = store.itemCount()
                        if (r == null) "En la nube no hay nada de este dispositivo todavía. Pulsa «Sincronizar ahora»."
                        else "Comprobado en Firebase: ${r.second} fichas de este dispositivo, guardadas el " +
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.first)) +
                            ". Aquí tienes $here." + if (r.second != here) " No coinciden: pulsa «Sincronizar ahora»." else " Todo al día."
                    }
                }) { Text("Comprobar la nube") }
            }

            // ---- Pareja ----
            Spacer(Modifier.height(18.dp))
            Text("Tu pareja", fontWeight = FontWeight.SemiBold)
            if (store.partnerUid.isBlank()) {
                Text(
                    "Escribe su usuario de Atrio. Así ella puede ver lo tuyo; cuando ella ponga el tuyo (${store.accUser}), tú verás lo suyo.",
                    color = UiSoft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)
                )
                OutlinedTextField(
                    partnerIn, { partnerIn = it }, label = { Text("Usuario de tu pareja") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, keyboardType = KeyboardType.Ascii),
                    shape = shp(14.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(shape = pillShape(), enabled = !busy && partnerIn.isNotBlank(), onClick = {
                    launchOp {
                        Account.setPartner(store, partnerIn)
                        partnerIn = ""
                        val p = refreshPartner()
                        if (p != null) "Ya compartís: ves lo de ${p.name}."
                        else "Listo. Cuando ${store.partnerUser} ponga tu usuario (${store.accUser}) en su Atrio, veréis lo del otro."
                    }
                }) { Text("Compartir con mi pareja") }
            } else {
                val inf = info
                val who = partnerUser.ifBlank { inf?.name ?: "tu pareja" }
                Text(
                    when {
                        inf != null -> "Compartes con $who. Sus datos son de " +
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(inf.updated)) + "."
                        shares == false -> "Ella ya puede ver lo tuyo. Tú verás lo suyo cuando $who ponga tu usuario (${store.accUser}) en su Atrio."
                        else -> "Compartes con $who. Aún no ha subido nada."
                    },
                    color = UiSoft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)
                )
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = pillShape(), enabled = !busy, onClick = { launchOp { refreshPartner(); if (info != null) "Al día." else "Todavía no hay nada suyo que ver." } }) { Text("Mirar lo suyo") }
                    OutlinedButton(shape = pillShape(), enabled = !busy, onClick = {
                        launchOp {
                            Account.clearPartner(store)
                            info = null
                            onPartner(null)
                            PartnerWatch.cancel(ctx)
                            "Ya no compartes tus datos con $who."
                        }
                    }) { Text("Dejar de compartir") }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Avisarme de sus novedades", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Una notificación cuando valore, empiece, apunte, te recomiende algo o reaccione a lo tuyo. " +
                                "Android lo comprueba cada media hora más o menos, no al instante.",
                            color = UiSoft, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = notify,
                        onCheckedChange = { on ->
                            notify = on
                            store.notifyPartner = on
                            if (on) {
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                                info?.let { Couple.markSeen(store, it) }
                                PartnerWatch.schedule(ctx)
                            } else {
                                PartnerWatch.cancel(ctx)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Charcoal, checkedThumbColor = Crudo)
                    )
                }
            }

            // ---- Recuperar ----
            Spacer(Modifier.height(18.dp))
            Text("Recuperar datos de la nube", fontWeight = FontWeight.SemiBold)
            Text(
                "Lo último que subió cada uno de tus dispositivos (y las copias antiguas del código de pareja, si siguen ahí).",
                color = UiSoft, fontSize = 13.sp
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(shape = pillShape(), enabled = !busy, onClick = {
                launchOp {
                    cloud = Couple.nodes(store)
                    if (cloud.isNullOrEmpty()) "No hay copias." else ""
                }
            }) { Text("Ver copias en la nube") }

            // ---- Sesión ----
            Spacer(Modifier.height(18.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape = pillShape(), enabled = !busy, onClick = { showPass = !showPass }) { Text("Cambiar contraseña") }
                OutlinedButton(shape = pillShape(), enabled = !busy, onClick = {
                    Account.signOut(store)
                    signed = false
                    info = null
                    onPartner(null)
                    PartnerWatch.cancel(ctx)
                    msg = "Has cerrado la sesión. Tus datos siguen en este dispositivo."
                }) { Text("Cerrar sesión") }
            }
            if (showPass) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    newPass, { newPass = it }, label = { Text("Contraseña nueva") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
                    shape = shp(14.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Button(shape = pillShape(), enabled = !busy && newPass.length >= 6, onClick = {
                    launchOp {
                        Account.changePassword(store, newPass)
                        newPass = ""
                        showPass = false
                        "Contraseña cambiada. En tus otros dispositivos seguirás dentro."
                    }
                }) { Text("Guardar contraseña") }
            }
        }

        Spacer(Modifier.height(10.dp))
        TextButton({ showRules = !showRules }) { Text(if (showRules) "Ocultar reglas de Firebase" else "Reglas de Firebase para las cuentas", color = Muted) }
        if (showRules) {
            Text(
                "Pégalas en la consola de Firebase → Realtime Database → Reglas → Publicar. Cada uno solo puede escribir en lo suyo, " +
                    "y solo puede leer lo tuyo quien tú hayas puesto como pareja.",
                color = UiSoft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)
            )
            Text(
                Account.RULES, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(UiTint, shp(14.dp)).padding(12.dp)
            )
            OutlinedButton({
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("reglas", Account.RULES))
                msg = "Reglas copiadas."
            }, shape = pillShape()) { Text("Copiar reglas") }
        }

        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(msg, color = UiSoft)
        }
    }

    val ask = mergeAsk
    if (ask != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Ya hay datos en tu cuenta", fontFamily = Display) },
            text = {
                Text(
                    "En este dispositivo hay $ask fichas y en tu cuenta también hay cosas. Puedes juntarlo todo " +
                        "(de cada ficha se queda la versión más reciente) o quedarte solo con lo de la cuenta. " +
                        "Si eliges la cuenta y te equivocas, en Copia de seguridad puedes deshacerlo."
                )
            },
            confirmButton = {
                TextButton({
                    mergeAsk = null
                    signed = true
                    launchOp {
                        val r = syncNow()
                        name = store.myName
                        onRestored()
                        "Juntado. $r"
                    }
                }) { Text("Juntarlo todo") }
            },
            dismissButton = {
                TextButton({
                    mergeAsk = null
                    signed = true
                    launchOp {
                        val n = withContext(Dispatchers.IO) { Account.adopt(store) }
                        onRestored()
                        val r = syncNow()
                        name = store.myName
                        "Ahora tienes lo de tu cuenta ($n fichas). $r"
                    }
                }) { Text("Solo lo de la cuenta") }
            },
            shape = shp(24.dp)
        )
    }

    val list = cloud
    if (list != null && list.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { cloud = null },
            title = { Text("Copias en la nube", fontFamily = Display) },
            text = {
                Column {
                    list.forEach { n ->
                        val whenText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(n.updated))
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .pop { restore = n }
                                .background(Sand, shp(14.dp)).padding(12.dp)
                        ) {
                            Text(n.name + if (n.mine) " (este dispositivo)" else "", fontWeight = FontWeight.SemiBold)
                            Text("${n.count} fichas · $whenText", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ cloud = null }) { Text("Cerrar") } },
            shape = shp(24.dp)
        )
    }
    val r = restore
    if (r != null) {
        AlertDialog(
            onDismissRequest = { restore = null },
            title = { Text("¿Recuperar la copia de ${r.name}?", fontFamily = Display) },
            text = {
                Text(
                    "Se sustituirán todos tus datos de este dispositivo por los de esa copia (${r.count} fichas). " +
                        "Si te equivocas, en Copia de seguridad podrás deshacerlo."
                )
            },
            confirmButton = {
                TextButton({
                    restore = null
                    cloud = null
                    msg = try {
                        store.importSync(r.data)
                        onRestored()
                        "Datos recuperados."
                    } catch (e: Exception) { "No se pudo recuperar: ${e.message}" }
                }) { Text("Recuperar") }
            },
            dismissButton = { TextButton({ restore = null }) { Text("Cancelar") } },
            shape = shp(24.dp)
        )
    }
}

/** Aviso de que los datos guardados han cambiado desde fuera (otro dispositivo de tu cuenta): la app los relee. */
object SyncBus {
    var version by androidx.compose.runtime.mutableStateOf(0)
        private set

    fun changed() {
        android.os.Handler(android.os.Looper.getMainLooper()).post { version++ }
    }
}
