@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.gonzalo.gamerate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private fun current(list: List<Play>, sessId: Long): String? =
    list.filter { it.status == 1 && it.cover.isNotEmpty() }
        .maxByOrNull { p -> if (p.id == sessId) Long.MAX_VALUE else p.activity }
        ?.cover

private fun now() = System.currentTimeMillis()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App(
    store: Store, openId: Long, openPartner: Boolean, openGo: String, openRate: String = "",
    shareUrl: String = "", shareTitle: String = "", onShareHandled: () -> Unit = {}, onOpenHandled: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var games by remember { mutableStateOf(store.load(0)) }
    var plays by remember { mutableStateOf(store.loadPlays(0)) }
    var books by remember { mutableStateOf(store.load(1)) }
    var bplays by remember { mutableStateOf(store.loadPlays(1)) }
    // Categorías propias y lo que hay en cada una.
    var kinds by remember { mutableStateOf(store.run { ensureMediaKinds(); kinds }.also { Mode.kinds = it }) }
    var extra by remember { mutableStateOf(kinds.associate { it.id to store.load(it.id) }) }
    var extraPlays by remember { mutableStateOf(kinds.associate { it.id to store.loadPlays(it.id) }) }
    var kindEdit by remember { mutableStateOf<Kind?>(null) }
    var kindNew by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf(store.key) }
    var sessId by remember { mutableStateOf(store.sessionId) }
    var section by remember { mutableStateOf(-1) }
    var tab by remember { mutableStateOf(0) }
    // Atrio 6: destino del dock (0 Inicio, 1 Juegos, 2 Libros, 3 Cine, 4 Diario) y apartado de la biblioteca
    var dest by remember { mutableStateOf(0) }
    /** En la interfaz Revista: la biblioteca se ha abierto a propósito. */
    var shellLib by remember { mutableStateOf(false) }
    var segment by remember { mutableStateOf(0) }
    var cineTv by remember { mutableStateOf(false) }
    var diaryKind by remember { mutableStateOf(0) }
    var wheelOpen by remember { mutableStateOf(false) }
    var partner by remember { mutableStateOf(Couple.cached(store)) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var syncTick by remember { mutableStateOf(0) }
    var reacts by remember { mutableStateOf(store.reacts) }
    var recsDone by remember { mutableStateOf(store.recsDone) }
    var undoImport by remember { mutableStateOf(store.hasUndoImport) }
    val year = LocalDate.now().year
    var goalG by remember { mutableStateOf(store.goal(0, year)) }
    var goalB by remember { mutableStateOf(store.goal(1, year)) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    remember { Mode.look = store.look; true }
    LaunchedEffect(Unit) {
        if (store.paired && store.notifyPartner) PartnerWatch.schedule(ctx)
    }

    // Viendo la app de tu pareja (solo lectura).
    val pv = Mode.viewP && partner != null
    val myKinds = listOf(GAMES, BOOKS) + kinds

    fun gamesOf(k: Kind): List<Game> = when (k.id) { 0 -> games; 1 -> books; else -> extra[k.id] ?: emptyList() }
    fun playsOf(k: Kind): List<Play> = when (k.id) { 0 -> plays; 1 -> bplays; else -> extraPlays[k.id] ?: emptyList() }

    val kind: Kind = if (pv) partner!!.kindById(section) else myKinds.find { it.id == section } ?: GAMES
    val myGames = if (pv) partner!!.games(kind) else gamesOf(kind)
    val myPlays = if (pv) partner!!.plays(kind) else playsOf(kind)

    fun setGames(k: Kind, l: List<Game>) {
        if (k.custom && kinds.none { it.id == k.id }) return // categoría ya borrada
        when (k.id) { 0 -> games = l; 1 -> books = l; else -> extra = extra + (k.id to l) }
        store.save(l, k.id)
    }

    fun setPlays(k: Kind, l: List<Play>) {
        if (k.custom && kinds.none { it.id == k.id }) return // categoría ya borrada
        when (k.id) { 0 -> plays = l; 1 -> bplays = l; else -> extraPlays = extraPlays + (k.id to l) }
        store.savePlays(l, k.id)
    }

    fun setKinds(l: List<Kind>) {
        kinds = l
        Mode.kinds = l
        store.kinds = l
    }

    fun say(m: String) { scope.launch { snack.showSnackbar(m, withDismissAction = true) } }

    fun undoable(m: String, undo: () -> Unit) {
        scope.launch {
            val r = snack.showSnackbar(m, actionLabel = "Deshacer", withDismissAction = true, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) undo()
        }
    }

    fun reloadAll() {
        games = store.load(0)
        plays = store.loadPlays(0)
        books = store.load(1)
        bplays = store.loadPlays(1)
        kinds = store.kinds
        Mode.kinds = kinds
        extra = kinds.associate { it.id to store.load(it.id) }
        extraPlays = kinds.associate { it.id to store.loadPlays(it.id) }
    }

    // Al salir de la app se programa una subida a la nube (y a Drive) que termina aunque la cierres.
    var cloudWarned by remember { mutableStateOf(false) }
    val life = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(life) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP && (store.paired || store.driveUri.isNotBlank())) CloudSave.enqueue(ctx)
        }
        life.lifecycle.addObserver(obs)
        onDispose { life.lifecycle.removeObserver(obs) }
    }

    // ---- La suite: conexión en tiempo real con Atrio mientras la app está delante ----
    DisposableEffect(life) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_START) Suite.start(ctx, store)
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP) Suite.stop()
        }
        life.lifecycle.addObserver(obs)
        onDispose { life.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(store.paired) { if (store.paired) { Suite.start(ctx, store); SuiteWatch.schedule(ctx) } }
    LaunchedEffect(Suite.notice) { Suite.notice?.let { say(it); Suite.notice = null } }

    // ---- Tu misma cuenta en otro dispositivo: lo que llegue de allí se vuelve a leer aquí ----
    LaunchedEffect(SyncBus.version) { if (SyncBus.version > 0) reloadAll() }
    // Mientras la app está abierta, cada 30 s se mira (con una consulta mínima) si tu otro dispositivo ha subido algo.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            if (store.paired && life.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                try { Couple.syncOwn(store) } catch (e: Exception) { /* sin conexión: la próxima vez */ }
            }
        }
    }
    // Al volver a la app, también.
    DisposableEffect(life) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_START && store.paired) {
                scope.launch { try { Couple.syncOwn(store) } catch (e: Exception) { } }
            }
        }
        life.lifecycle.addObserver(obs)
        onDispose { life.lifecycle.removeObserver(obs) }
    }

    // ---- Modo pareja: subir lo tuyo y bajar lo suyo ----
    LaunchedEffect(games, plays, books, bplays, extra, extraPlays, kinds, syncTick) {
        if (store.paired) {
            delay(1500)
            try {
                Couple.push(store)
                cloudWarned = false
                val p = Couple.pull(store)
                if (p != null) {
                    partner = p
                    Couple.markSeen(store, p)
                }
            } catch (e: Exception) {
                // Se reintenta en el próximo cambio y al salir de la app; se avisa una vez para que no pase desapercibido.
                if (!cloudWarned && store.cloudErr.isNotBlank()) {
                    cloudWarned = true
                    say("No se ha podido guardar en la nube: ${store.cloudErr}")
                }
            }
        }
    }
    // En categorías propias solo se compara si tu pareja tiene la misma con los mismos criterios.
    val side: PartnerSide? = if (pv) null else partner?.let { p ->
        if (kind.custom && p.match(kind)?.labels?.drop(1) != kind.labels.drop(1)) null else p.side(kind)
    }

    // Cada vez que tocas algo (como mucho una vez cada 4 s) baja lo último de tu pareja.
    var lastPull by remember { mutableStateOf(0L) }
    var pulling by remember { mutableStateOf(false) }
    fun refreshPartner() {
        if (!store.paired || pulling) return
        val t = now()
        if (t - lastPull < 4000L) return
        lastPull = t
        pulling = true
        scope.launch {
            try {
                val p = Couple.pull(store)
                if (p != null) {
                    if (p.updated != partner?.updated) partner = p
                    Couple.markSeen(store, p)
                }
            } catch (e: Exception) {
                // sin conexión: se reintenta en el próximo toque
            }
            pulling = false
        }
    }
    LaunchedEffect(screen, tab, section) { refreshPartner() }

    // El interruptor del calendario y las estadísticas de tu pareja solo vale en esas dos pestañas.
    LaunchedEffect(tab, section, screen) {
        if (section == -1 || screen !is Screen.Home || (tab != 2 && tab != 3)) Mode.calP = false
    }
    LaunchedEffect(partner) { if (partner == null) { Mode.calP = false; Mode.viewP = false } }
    // Una ficha de tu pareja abierta desde lo tuyo también va con su tema.
    LaunchedEffect(screen, pv) { Mode.itemP = !pv && screen is Screen.PartnerItem }

    /**
     * Clave de tu reacción a una ficha de tu pareja. En categorías propias va con el id que esa
     * categoría tiene en SU móvil (puede no ser el mismo que en el tuyo), para que la vea.
     */
    fun reactKey(k: Kind, id: Long) = itemKey(if (k.custom) partner?.match(k)?.id ?: k.id else k.id, id)

    fun react(k: Kind, id: Long, e: String?) {
        val m = reacts.toMutableMap()
        val kk = reactKey(k, id)
        if (e == null) m.remove(kk) else m[kk] = e
        reacts = m
        store.reacts = m
        syncTick++
    }

    fun recommend(k: Kind, id: Long, name: String, cover: String, creator: String, note: String) {
        val r = Rec(now(), k.id, id, name, if (Photos.isLocal(cover)) "" else cover, creator, note, now())
        store.recs = (store.recs.filter { it.itemId != id || it.kind != k.id } + r).takeLast(40)
        syncTick++
        say("Recomendado a ${partner?.name ?: "tu pareja"}")
    }

    fun recDone(r: Rec) {
        recsDone = recsDone + r.id
        store.recsDone = recsDone
    }

    fun recAdd(r: Rec) {
        val k = kindOf(r.kind)
        if (gamesOf(k).none { it.id == r.itemId } && playsOf(k).none { it.id == r.itemId }) {
            setPlays(k, playsOf(k) + Play(r.itemId, r.name, r.cover, "", 0, emptyList(), creator = r.creator, updated = now()))
            say("${r.name} añadido a tu lista de ${k.title.lowercase()}")
        } else {
            say("Ya tenías ${r.name}")
        }
        recDone(r)
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(store.exportAll().toByteArray(Charsets.UTF_8)) }
                say("Datos exportados")
            } catch (e: Exception) { say("Error al exportar: ${e.message}") }
        }
    }

    // ---- Google Drive: copia automática en un archivo ----
    var driveName by remember { mutableStateOf(DriveBackup.name(ctx, store) ?: if (store.driveUri.isNotBlank()) "tu archivo" else null) }
    var driveTick by remember { mutableStateOf(0) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    fun driveInfo(): String {
        val e = store.driveErr
        val t = store.driveLast
        return when {
            e.isNotBlank() -> "Último intento falló: $e"
            t > 0L -> "Última copia comprobada: " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(t)) +
                ". La app comprueba el archivo; la subida a Google la hace la app de Drive (lo ves en drive.google.com)."
            else -> "Aún no se ha guardado ninguna copia."
        }
    }
    val driveCreate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            DriveBackup.link(ctx, store, uri)
            driveName = DriveBackup.name(ctx, store) ?: "tu archivo"
            scope.launch {
                val ok = DriveBackup.save(ctx, store)
                driveTick++
                say(if (ok) "Vinculado: cada cambio se guardará en Drive" else "No se pudo escribir en ese archivo")
            }
        }
    }
    val driveOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    LaunchedEffect(games, plays, books, bplays, extra, extraPlays, kinds, syncTick) {
        if (store.driveUri.isNotBlank()) {
            delay(2500)
            DriveBackup.save(ctx, store)
            driveTick++
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val txt = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                store.importAll(txt)
                reloadAll()
                key = store.key
                undoImport = store.hasUndoImport
                say("Datos importados")
            } catch (e: Exception) { say("No se pudo importar: ${e.message}") }
        }
    }

    /** Termina la sesión y suma el tiempo (máximo 12 h, por si se te olvidó pararla). */
    fun stopSession() {
        val id = store.sessionId
        if (id != 0L && store.sessionStart > 0L) {
            val mins = ((now() - store.sessionStart) / 60_000L).toInt().coerceIn(0, 720)
            if (mins > 0) {
                // La sesión queda como parte jugada, lista para valorarla
                // Con «Valorar por partes» apagado se guarda igual (horas), pero sin pedir nota
                val quiet = plays.find { it.id == id }?.noParts == true
                val sg = Seg("t${store.sessionStart}", store.sessionStart, now(), mins, "timer", q = quiet)
                setPlays(GAMES, plays.map { if (it.id == id) it.copy(minutes = it.minutes + mins, segs = it.segs + sg, updated = now()) else it })
                val pl = plays.find { it.id == id }
                scope.launch {
                    if (quiet) { snack.showSnackbar("Sesión terminada: +${hoursText(mins)}", withDismissAction = true); return@launch }
                    val r = snack.showSnackbar("Sesión terminada: +${hoursText(mins)}", actionLabel = "Valorar", withDismissAction = true, duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed && pl != null) { section = 0; screen = Screen.Note(pl, null, sg) }
                }
            }
        }
        store.sessionId = 0L
        sessId = 0L
        Reminders.cancel(ctx)
    }

    fun startSession(p: Play, hours: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (sessId != 0L && sessId != p.id) stopSession()
        store.sessionId = p.id
        store.sessionStart = now()
        store.sessionHours = hours
        sessId = p.id
        Reminders.schedule(ctx, hours)
    }

    // ---- Traer de otras apps: Goodreads y Steam ----
    var importMsg by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    val goodreads = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing) scope.launch {
            importing = true
            try {
                val txt = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                }
                val list = Goodreads.parse(txt)
                var added = 0
                var skipped = 0
                // Por tandas: lo importado se va guardando, y cada tanda se compara con lo que tengas en ese momento.
                list.chunked(15).forEachIndexed { ci, chunk ->
                    val r = Goodreads.import(chunk, books, bplays) { i, _, t -> importMsg = "Buscando ${ci * 15 + i} de ${list.size}: $t" }
                    val have = (books.map { it.id } + bplays.map { it.id }).toSet()
                    val ng = r.games.filter { it.id !in have }
                    val np = r.plays.filter { it.id !in have && ng.none { g -> g.id == it.id } }
                    if (ng.isNotEmpty()) setGames(BOOKS, books + ng)
                    if (np.isNotEmpty()) setPlays(BOOKS, bplays + np)
                    added += ng.size + np.size
                    skipped += r.skipped + (r.added - ng.size - np.size)
                }
                importMsg = "Listo: $added añadidos" + if (skipped > 0) " ($skipped ya los tenías)." else "."
                say("Goodreads: $added libros añadidos")
            } catch (e: Exception) {
                importMsg = "No se pudo importar: ${e.message}"
            } finally {
                importing = false
            }
        }
    }

    /** Bookmory: un .zip con un .txt por libro. Lo nuevo se añade y lo que ya tenías se completa. */
    val bookmory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing) scope.launch {
            importing = true
            try {
                importMsg = "Leyendo el archivo de Bookmory…"
                val list = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { Bookmory.read(it) } ?: emptyList()
                }
                if (list.isEmpty()) throw IllegalArgumentException("el archivo está vacío")
                // Copia de cómo estaba todo, para el botón "Deshacer la última importación".
                withContext(Dispatchers.IO) { store.keepUndo() }
                undoImport = true
                var added = 0
                var updated = 0
                var same = 0
                val taken = HashSet<Long>() // obras ya usadas en esta importación: dos libros no pueden ser la misma
                list.chunked(8).forEachIndexed { ci, chunk ->
                    var g = books
                    var p = bplays
                    chunk.forEachIndexed { i, b ->
                        importMsg = "Bookmory: ${ci * 8 + i + 1} de ${list.size} · ${b.title}"
                        // Solo se busca en internet lo que no tienes ya.
                        var hit = if (Bookmory.existing(b, 0L, g, p) == null) BookFind.find(b.title, b.author, b.isbn) else null
                        if (hit != null && hit.id > 0 && hit.id in taken) hit = null
                        val r = Bookmory.apply(b, hit, g, p, now())
                        hit?.id?.takeIf { it > 0 }?.let { taken.add(it) }
                        g = r.games
                        p = r.plays
                        when (r.outcome) {
                            Bookmory.Outcome.ADDED -> added++
                            Bookmory.Outcome.UPDATED -> updated++
                            Bookmory.Outcome.SAME -> same++
                        }
                    }
                    if (g !== books) setGames(BOOKS, g)
                    if (p !== bplays) setPlays(BOOKS, p)
                }
                importMsg = "Bookmory: $added libros nuevos" +
                    (if (updated > 0) ", $updated completados" else "") +
                    (if (same > 0) " y $same que ya estaban igual" else "") + "."
                say("Bookmory: $added nuevos" + if (updated > 0) ", $updated completados" else "")
            } catch (e: Exception) {
                importMsg = "Bookmory: no se pudo importar (${e.message})"
            } finally {
                importing = false
            }
        }
    }

    /** Copia las horas de Steam en tus juegos (valorados, en curso, en lista y dropeados), solo si Steam tiene más. */
    fun steamHours(k: String, u: String, quiet: Boolean = false) {
        store.steamKey = k
        store.steamUser = u
        scope.launch {
            if (!quiet) importMsg = "Leyendo tu biblioteca de Steam…"
            try {
                val owned = Steam.owned(u, k).associateBy { Steam.key(it.name) }
                var nv = 0
                var np = 0
                val ng = games.map { g ->
                    val o = owned[Steam.key(g.name)]
                    if (o != null && o.minutes > g.minutes) { nv++; g.copy(minutes = o.minutes, updated = now()) } else g
                }
                val npl = plays.map { p ->
                    val o = owned[Steam.key(p.name)]
                    if (o != null && o.minutes > p.minutes) { np++; p.copy(minutes = o.minutes, updated = now()) } else p
                }
                if (nv > 0) setGames(GAMES, ng)
                if (np > 0) setPlays(GAMES, npl)
                store.steamLast = now()
                importMsg = "Steam: ${owned.size} juegos en tu biblioteca. Horas actualizadas en $nv valorados y $np de tu lista."
                if (quiet && nv + np > 0) say("Horas de Steam actualizadas en ${nv + np} juegos")
            } catch (e: Exception) { if (!quiet) importMsg = "Steam: ${e.message}" }
        }
    }
    // Se actualizan solas al abrir la app (como mucho cada 6 horas) si tienes Steam configurado.
    LaunchedEffect(Unit) {
        if (store.steamKey.isNotBlank() && store.steamUser.isNotBlank() && now() - store.steamLast > 6 * 3_600_000L) {
            steamHours(store.steamKey, store.steamUser, quiet = true)
        }
    }

    // ---- Estudios: se buscan solos en internet si no los has puesto ----
    val studioTried = remember { HashSet<Long>() }
    var studioMsg by remember { mutableStateOf("") }

    fun autoStudio(k: Kind, id: Long, name: String) {
        // Películas y series: director o creadores, desde su web (solo si se añadieron desde el buscador).
        if ((k.isMovie || k.isTv) && isWebId(id)) {
            val tag = id * 31 + k.id
            if (tag in studioTried) return
            studioTried.add(tag)
            scope.launch {
                val who = try {
                    if (k.isMovie) Tmdb.director(id, store.tmdbKey) else TvMaze.creators(id)
                } catch (e: Exception) { null } ?: return@launch
                if (gamesOf(k).any { it.id == id && it.creator.isBlank() })
                    setGames(k, gamesOf(k).map { if (it.id == id && it.creator.isBlank()) it.copy(creator = who) else it })
                if (playsOf(k).any { it.id == id && it.creator.isBlank() })
                    setPlays(k, playsOf(k).map { if (it.id == id && it.creator.isBlank()) it.copy(creator = who) else it })
            }
            return
        }
        if (!k.isGame || id in studioTried) return
        studioTried.add(id)
        scope.launch {
            val st = Studio.detect(name, store.rawgKey) ?: return@launch
            if (games.any { it.id == id && it.creator.isBlank() }) setGames(GAMES, games.map { if (it.id == id && it.creator.isBlank()) it.copy(creator = st) else it })
            if (plays.any { it.id == id && it.creator.isBlank() }) setPlays(GAMES, plays.map { if (it.id == id && it.creator.isBlank()) it.copy(creator = st) else it })
        }
    }

    fun fillStudios() {
        val todo = (games.filter { it.creator.isBlank() }.map { it.id to it.name } + plays.filter { it.creator.isBlank() }.map { it.id to it.name })
            .distinctBy { it.first }
        if (todo.isEmpty()) { studioMsg = "Todos tus juegos tienen estudio."; return }
        scope.launch {
            var ok = 0
            todo.forEachIndexed { i, (id, name) ->
                studioMsg = "Buscando ${i + 1} de ${todo.size}: $name"
                val st = Studio.detect(name, store.rawgKey)
                if (st != null) {
                    ok++
                    setGames(GAMES, games.map { if (it.id == id && it.creator.isBlank()) it.copy(creator = st) else it })
                    setPlays(GAMES, plays.map { if (it.id == id && it.creator.isBlank()) it.copy(creator = st) else it })
                }
            }
            studioMsg = "Listo: $ok de ${todo.size} encontrados."
        }
    }

    fun savePlay(p: Play) {
        val k = kind
        val np = p.copy(updated = now())
        setPlays(k, playsOf(k).filter { it.id != p.id } + np)
        if (!k.isBook && p.status != 1 && sessId == p.id) stopSession()
        if (!k.isBook && p.creator.isBlank()) autoStudio(k, p.id, p.name)
    }

    fun saveGame(g: Game) {
        val k = kind
        setGames(k, gamesOf(k).filter { it.id != g.id } + g.copy(updated = now()))
        if (!k.isBook && g.creator.isBlank()) autoStudio(k, g.id, g.name)
    }

    /** Añade a tu lista una carta de "Siguiente". */
    fun addCard(c: Card) {
        val k = kindOf(c.kind)
        scope.launch {
            if (playsOf(k).any { normName(it.name) == normName(c.title) } || gamesOf(k).any { normName(it.name) == normName(c.title) }) {
                say("Ya tenías ${c.title}")
                return@launch
            }
            var id = if (k.isBook) c.itemId else -c.itemId
            var cover = c.image
            var name = c.title
            if (!k.isBook && key.isNotBlank()) {
                try {
                    val hits = Sgdb.search(c.title, key)
                    val h = hits.firstOrNull { normName(it.name) == normName(c.title) } ?: hits.firstOrNull()
                    if (h != null) {
                        id = h.id
                        name = h.name
                        cover = try { Sgdb.covers(h.id, key).firstOrNull()?.second } catch (e: Exception) { null } ?: c.image
                    }
                } catch (e: Exception) { /* se queda con la imagen de RAWG */ }
            }
            if (playsOf(k).any { it.id == id } || gamesOf(k).any { it.id == id }) { say("Ya tenías ${c.title}"); return@launch }
            setPlays(k, playsOf(k) + Play(id, name, cover, "", 0, emptyList(), creator = c.creator, updated = now()))
            if (!k.isBook) autoStudio(k, id, name)
            say("$name añadido a tu lista")
        }
    }

    fun deleteGame(g: Game) {
        val k = kind
        val old = gamesOf(k).find { it.id == g.id } ?: g
        setGames(k, gamesOf(k).filter { it.id != g.id })
        undoable("${g.name} eliminado") { setGames(k, gamesOf(k).filter { it.id != old.id } + old) }
    }

    fun deletePlay(p: Play) {
        val k = kind
        if (k.isGame && sessId == p.id) stopSession()
        val old = playsOf(k).find { it.id == p.id } ?: p
        setPlays(k, playsOf(k).filter { it.id != p.id })
        undoable("${p.name} eliminado") { setPlays(k, playsOf(k).filter { it.id != old.id } + old) }
    }

    fun export(g: Game, mode: ExportMode) {
        val k = kind
        scope.launch {
            say("Generando…")
            try {
                val bm = Export.render(ctx, g, k)
                val name = g.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "ficha" }
                when (mode) {
                    ExportMode.Share -> {
                        val uri = withContext(Dispatchers.IO) { Export.saveJpgUri(ctx, bm, name) }
                        if (uri != null) Export.share(ctx, uri, g.name) else say("No se pudo preparar la imagen")
                    }
                    ExportMode.Pdf -> {
                        val ok = withContext(Dispatchers.IO) { Export.savePdf(ctx, bm, name) }
                        say(if (ok) "PDF guardado en Descargas/GameRate" else "No se pudo guardar")
                    }
                    ExportMode.Jpg -> {
                        val ok = withContext(Dispatchers.IO) { Export.saveJpg(ctx, bm, name) }
                        say(if (ok) "JPG guardado en Imágenes/GameRate" else "No se pudo guardar")
                    }
                }
            } catch (e: Exception) { say("Error: ${e.message}") }
        }
    }

    fun shareYear(k: Kind, list: List<Game>, y: Int) {
        scope.launch {
            say("Preparando tu resumen…")
            try {
                val bm = Export.renderYear(ctx, k, list, y, store.myName)
                val uri = withContext(Dispatchers.IO) { Export.saveJpgUri(ctx, bm, "ronron-$y-${normName(k.title)}") }
                if (uri != null) Export.share(ctx, uri, "Mi $y en Atrio") else say("No se pudo preparar la imagen")
            } catch (e: Exception) { say("Error: ${e.message}") }
        }
    }

    // ---- La suite: apuntes rápidos, partes jugadas y capturas ----
    var shooting by remember { mutableStateOf(false) }
    var memoFor by remember { mutableStateOf<Pair<Play, Shot?>?>(null) }
    var segFor by remember { mutableStateOf<Play?>(null) }
    var guideSheet by remember { mutableStateOf<PcGame?>(null) }
    // Las guías se abren dentro de la app, encima de lo que estés viendo
    SideEffect { Guides.opener = { g -> guideSheet = null; WebActivity.open(ctx, g) } }
    var skipped by remember { mutableStateOf(store.segsSkipped) }

    /** La ficha en curso de un juego que se está jugando en el PC (si no la tienes, se crea en «Jugando»). */
    fun playFor(g: LiveGame): Play? {
        if (g.sg <= 0L) { say("Atrio no sabe qué juego es en SteamGridDB: corrígelo en el PC (Opciones → Corregir juego)"); return null }
        plays.find { it.id == g.sg }?.let { return it }
        games.find { it.id == g.sg }?.let { v ->
            val np = Play(v.id, v.name, v.cover, v.hero, 1, emptyList(), start = today(), creator = v.creator, platform = "PC", updated = now(), logo = v.logo)
            setPlays(GAMES, plays + np)
            say("${v.name} vuelve a «Jugando» (lo rejuegas)")
            return np
        }
        val np = Play(g.sg, g.name, g.cover, g.hero, 1, emptyList(), start = today(), platform = "PC", updated = now(), logo = g.logo)
        setPlays(GAMES, plays + np)
        say("${g.name} añadido a «Jugando»")
        return np
    }

    fun addMemo(p: Play, text: String, shotId: String) {
        val k = kindOf(plays.find { it.id == p.id }?.let { 0 } ?: section.coerceAtLeast(0))
        val cur = playsOf(k).find { it.id == p.id } ?: p
        val m = Memo(now(), text, shotId, u = now())
        setPlays(k, playsOf(k).filter { it.id != p.id } + cur.copy(memos = cur.memos + m, updated = now()))
        say(if (shotId.isNotEmpty()) "Apunte guardado con su captura" else "Apunte guardado")
    }

    fun deleteMemo(p: Play, m: Memo) {
        val k = kind
        val cur = playsOf(k).find { it.id == p.id } ?: return
        setPlays(k, playsOf(k).filter { it.id != p.id } + cur.copy(memos = cur.memos.map { if (it.t == m.t) it.copy(del = true, u = now()) else it }, updated = now()))
    }

    fun shoot(onDone: (Shot?) -> Unit = {}) {
        if (shooting) return
        shooting = true
        scope.launch {
            val (id, msg) = Suite.shoot()
            if (id == null) { say(msg); shooting = false; onDone(null); return@launch }
            val s = Suite.awaitLocal(id)
            shooting = false
            onDone(s)
            if (s == null) say("La captura se está bajando; aparecerá en Capturas")
        }
    }

    fun saveMemo(p: Play, text: String, withShot: Boolean, preset: Shot?) {
        when {
            preset != null -> addMemo(p, text, preset.id)
            withShot -> shoot { s -> addMemo(p, text, s?.id ?: "") }
            else -> addMemo(p, text, "")
        }
    }

    fun addSeg(p: Play, sg: Seg) {
        val k = kind
        val cur = playsOf(k).find { it.id == p.id } ?: p
        val np = cur.copy(segs = cur.segs + sg, minutes = cur.minutes + if (k.isGame) sg.min else 0, updated = now())
        setPlays(k, playsOf(k).filter { it.id != p.id } + np)
        screen = Screen.Note(np, null, sg)
    }

    /** Partes jugadas sin valorar de todo lo que tienes en curso (lo más reciente primero). */
    val pendingSegs: List<Pair<Play, Seg>> = (listOf(GAMES, BOOKS) + kinds).flatMap { k -> playsOf(k).filter { it.status != 2 }.flatMap { p -> p.unrated().map { p to it } } }
        .filter { it.second.id !in skipped && now() - it.second.e < 30L * 86_400_000L }
        .sortedByDescending { it.second.e }

    LaunchedEffect(openRate) {
        if (openRate.isNotEmpty()) {
            val pid = openRate.substringBefore(":").toLongOrNull()
            val sid = openRate.substringAfter(":")
            plays.find { it.id == pid }?.let { p -> p.segs.find { it.id == sid }?.let { sg -> section = 0; screen = Screen.Note(p, null, sg) } }
            onOpenHandled()
        }
    }
    // Una captura recién llegada se puede abrir desde el aviso
    LaunchedEffect(Suite.arrived) {
        val a = Suite.arrived ?: return@LaunchedEffect
        Suite.arrived = null
        if (screen is Screen.ShotView || memoFor != null) return@LaunchedEffect
        val r = snack.showSnackbar("Captura de ${a.game.ifBlank { a.pc }} · ${a.w}×${a.h}", actionLabel = "Ver", withDismissAction = true)
        if (r == SnackbarResult.ActionPerformed) screen = Screen.ShotView(a, screen)
    }

    fun toEdit(s: Screen.Art, g: Game) {
        val p = s.play
        if (p != null) {
            val np = p.copy(cover = g.cover, hero = g.hero)
            savePlay(np)
            // Imagen de «En portada»: se vuelve a Inicio
            screen = if (s.home) Screen.Home else Screen.PlayDetail(np, editing = true)
        } else {
            screen = Screen.Edit(g, s.isNew, editing = true)
        }
    }

    // ---- Fotos de la galería como portada o fondo ----
    var galleryCb by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val cb = galleryCb
        galleryCb = null
        if (uri != null && cb != null) scope.launch {
            try { cb(Photos.import(ctx, uri)) } catch (e: Exception) { say("No se pudo usar esa foto: ${e.message}") }
        }
    }
    fun pickPhoto(cb: (String) -> Unit) {
        galleryCb = cb
        gallery.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // ---- Cambiar entre tu app y la de tu pareja ----
    fun togglePartner() {
        if (partner == null) {
            say(if (store.paired) "Aún no hay datos de tu pareja: espera a que se sincronice" else "Entra en tu cuenta y pon el usuario de tu pareja en Ajustes → Cuenta y pareja")
            return
        }
        store.knowsDoubleTap = true
        withReveal {
            Mode.calP = false
            Mode.viewP = !Mode.viewP
            section = -1
            tab = 0
            screen = Screen.Home
        }
    }

    // ---- Atrio 6: botón principal de cada tarjeta de «Sigue donde lo dejaste» ----
    fun pcGameOf(o: Ongoing): PcGame? = if (o.kind.isGame && Suite.target() != null) Guides.pcGameFor(o.play.id, o.play.name) else null
    fun primaryLabel(o: Ongoing): Pair<String, androidx.compose.ui.graphics.vector.ImageVector> = when {
        pcGameOf(o) != null -> "Streaming" to Icons.Rounded.Cast
        o.kind.isBook && BookFiles.get(ctx, o.play.id) != null -> "Leer" to Icons.Rounded.AutoStories
        else -> "Apuntar" to Icons.Rounded.EditNote
    }
    fun primaryAction(o: Ongoing, nav: (Screen) -> Unit) {
        val g = pcGameOf(o)
        when {
            g != null -> scope.launch {
                val err = Stream.play(ctx, g.key)
                if (err == "NOVA") say("Instala Nova para jugar en streaming (Perfil → Tu PC)") else if (err != null) say(err)
            }
            o.kind.isBook && BookFiles.get(ctx, o.play.id) != null -> ReaderActivity.open(ctx, o.play.id, o.play.name)
            else -> nav(Screen.Note(o.play, null))
        }
    }

    /** Añade a tu lista algo descubierto con las preguntas. */
    fun quizAdd(it: QuizItem) {
        val k = when {
            it.domain == 1 -> BOOKS
            it.tv -> kinds.firstOrNull { k -> k.isTv }
            else -> kinds.firstOrNull { k -> k.isMovie }
        } ?: run { say("No tienes la categoría para esto"); return }
        scope.launch {
            if (playsOf(k).any { p -> normName(p.name) == normName(it.title) } || gamesOf(k).any { g -> normName(g.name) == normName(it.title) }) {
                say("Ya tenías ${it.title}"); return@launch
            }
            var id = it.id
            var cover = it.cover
            // Las series de Atrio van con TVmaze (episodios, creadores): se busca la misma serie allí
            if (it.tv) {
                try {
                    val hits = TvMaze.search(it.original.ifBlank { it.title })
                    val h = hits.firstOrNull { h -> normName(h.name) == normName(it.original) && (it.year.isBlank() || h.sub.startsWith(it.year)) }
                        ?: hits.firstOrNull { h -> normName(h.name) == normName(it.title) } ?: hits.firstOrNull()
                    if (h != null) { id = h.id; if (cover.isBlank()) cover = h.cover }
                } catch (e: Exception) { }
            }
            if (playsOf(k).any { p -> p.id == id } || gamesOf(k).any { g -> g.id == id }) { say("Ya tenías ${it.title}"); return@launch }
            setPlays(k, playsOf(k) + Play(id, it.title, cover, "", 0, emptyList(), creator = it.creator, updated = now()))
            say("${it.title} añadido a tu lista")
        }
    }

    fun startFromWheel(p: Play) {
        savePlay(p.copy(status = 1, start = if (p.start == 0L) today() else p.start, end = 0L))
        say("¡A por ${p.name}!")
    }

    LaunchedEffect(openId, openPartner, openGo) {
        // Atajos y avisos abren siempre lo tuyo (nunca se edita nada desde la vista de tu pareja).
        if ((openGo.isNotEmpty() || openId != 0L) && Mode.viewP) { Mode.viewP = false; Mode.calP = false }
        if (openGo.isNotEmpty()) {
            // Accesos directos del icono: "add_game", "next_book", "stats_game"…
            val sec = if (openGo.endsWith("book")) 1 else 0
            section = sec
            when {
                openGo.startsWith("add") -> { tab = 0; screen = Screen.Search(true) }
                openGo.startsWith("next") -> { tab = 4; screen = Screen.Home }
                openGo.startsWith("stats") -> { tab = 3; screen = Screen.Home }
                else -> screen = Screen.Home
            }
            onOpenHandled()
        } else if (openId != 0L) {
            plays.find { it.id == openId }?.let { screen = Screen.Note(it, null); tab = 0; section = 0 }
            onOpenHandled()
        } else if (openPartner) {
            section = -1
            screen = Screen.Home
            onOpenHandled()
        }
    }

    // ---- Atrio 6: destinos del dock ----
    fun cineKindId(): Int {
        val ks = if (pv) partner!!.allKinds else myKinds
        return (ks.firstOrNull { if (cineTv) it.isTv else it.isMovie } ?: ks.firstOrNull { it.isMovie || it.isTv })?.id ?: -1
    }
    fun go(d: Int) {
        dest = d
        segment = 0
        section = when (d) { 1 -> 0; 2 -> 1; 3 -> cineKindId(); 4 -> diaryKind; else -> -1 }
        screen = Screen.Home
    }

    BackHandler(enabled = dest != 0 || section != -1 || screen !is Screen.Home || pv) {
        val cur = screen
        when {
            cur is Screen.ShotView -> screen = cur.back
            screen is Screen.Settings -> screen = Screen.Profile
            screen !is Screen.Home -> screen = Screen.Home
            dest != 0 -> go(0)
            section != -1 -> section = -1
            else -> togglePartner()
        }
    }

    val look = Mode.look
    val target = Color(
        when {
            section == 0 -> look.games
            section == 1 -> look.books
            section > 1 && kind.color != 0 -> kind.color
            else -> look.menu
        }
    )
    val bgColor by animateColorAsState(target, tween(500), label = "fondo")
    val bgMod = Modifier.pattern(look.pattern, bgColor, look.intensity)
    val shown: Any = if (screen is Screen.Home) "top:$dest:$section:$pv" else screen
    val topLevel = screen is Screen.Home
    val dockOn = topLevel && Skin.shell == 0
    // Al volver de una ficha abierta desde Feed, Revista o Escritorio, se vuelve a esa pantalla (no a la biblioteca)
    LaunchedEffect(screen is Screen.Home) {
        if (screen is Screen.Home && Skin.shell != 0 && !shellLib && dest == 0) section = -1
    }

    val touchWatcher = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                if (ev.type == PointerEventType.Press) ev.changes.firstOrNull()?.let { Mode.touch = it.position }
                if (ev.type == PointerEventType.Release) refreshPartner()
            }
        }
    }
    fun openPlay(k: Kind, p: Play) {
        if (k.id != section) section = k.id
        screen = if (pv) Screen.PartnerItem(k.id, p.id) else Screen.PlayDetail(p)
    }
    CompositionLocalProvider(LocalContentColor provides Charcoal) {
    Box(Modifier.fillMaxSize().then(bgMod).then(touchWatcher)) {
        androidx.compose.animation.SharedTransitionLayout {
        val sharedScope = this
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                val a = initialState; val b = targetState
                if (a is String && b is String) {
                    // Entre destinos del dock: fundido rápido con un leve desplazamiento
                    (fadeIn(tween(Motion.ms(200))) + scaleIn(tween(Motion.ms(240)), initialScale = 0.985f)) togetherWith fadeOut(tween(Motion.ms(120)))
                } else if (a is String) {
                    // Abrir una ficha: entra desde abajo mientras la portada vuela
                    (fadeIn(tween(Motion.ms(260))) + slideInVertically(tween(Motion.ms(320))) { it / 14 }) togetherWith fadeOut(tween(Motion.ms(200)))
                } else {
                    fadeIn(tween(Motion.ms(220))) togetherWith fadeOut(tween(Motion.ms(160)))
                }
            },
            label = "pantallas"
        ) { s ->
            CompositionLocalProvider(LocalShared provides sharedScope, LocalAnim provides this) {
            if (s is String) {
                val p = partner
                val pendingRecs = if (pv) emptyList() else p?.recs?.filter { it.id !in recsDone }?.sortedByDescending { it.time } ?: emptyList()
                val acts = if (p == null) emptyList() else p.allKinds.flatMap { k ->
                    p.games(k).map { g -> Activity(k.id, g.id, g.name, g.cover, "Valorado · ${g.general}", g.activity) } +
                        p.plays(k).map { pl -> Activity(k.id, pl.id, pl.name, pl.cover, k.statuses[pl.status.coerceIn(0, 2)], pl.activity) }
                }.sortedByDescending { it.time }.distinctBy { it.kind to it.id }.take(12)
                val sectionKinds = if (pv) p!!.allKinds else myKinds
                fun gs(k: Kind) = if (pv) p!!.games(k) else gamesOf(k)
                fun ps(k: Kind) = if (pv) p!!.plays(k) else playsOf(k)

                @Composable
                fun Library(k: Kind, onBack: (() -> Unit)?) {
                    val cine = k.isMovie || k.isTv
                    LibraryScreen(
                        k, myGames, myPlays, if (pv) 0L else sessId, side, pv, segment, { segment = it },
                        onOpenPlay = { openPlay(k, it) },
                        onOpenGame = { screen = if (pv) Screen.PartnerItem(k.id, it.id) else Screen.Edit(it, false) },
                        onAdd = if (pv) null else ({ forPlay -> screen = Screen.Search(forPlay) }),
                        onStats = { screen = Screen.Stats },
                        onDiscover = when {
                            pv -> null
                            k.isGame -> ({ screen = Screen.Deck })
                            k.isBook -> ({ screen = Screen.Quiz(1) })
                            cine -> ({ screen = Screen.Quiz(2) })
                            else -> null
                        },
                        onWheel = if (pv) null else ({ wheelOpen = true }),
                        titleSwitch = if (cine) ({
                            TitleTabs(listOf("Películas", "Series"), if (k.isTv) 1 else 0) { i -> cineTv = i == 1; section = cineKindId(); segment = 0 }
                        }) else null,
                        badgeFor = if (cine) ({ id, name -> RtBadgeFor(k, id, name) }) else null,
                        onBack = onBack
                    )
                }

                when {
                    dest == 4 -> {
                        val dk = if (pv) p!!.kindById(section) else myKinds.find { it.id == section } ?: GAMES
                        CalendarScreen(dk, myGames, myPlays, side, { go(0) }, heading = "Diario", top = {
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                                items(sectionKinds.size) { i ->
                                    val k = sectionKinds[i]
                                    SoftChip(k.title, k.id == dk.id) { diaryKind = k.id; section = k.id }
                                }
                            }
                        }) { c ->
                            if (pv) {
                                screen = Screen.PartnerItem(dk.id, c.id)
                            } else if (Mode.calP && side != null) {
                                screen = Screen.PartnerItem(partner?.match(dk)?.id ?: dk.id, c.id)
                            } else if (c.isPlay) {
                                myPlays.find { it.id == c.id }?.let { screen = Screen.PlayDetail(it) }
                            } else {
                                myGames.find { it.id == c.id }?.let { screen = Screen.Edit(it, false) }
                            }
                        }
                    }
                    dest in 1..3 && section >= 0 -> Library(kind, null)
                    dest == 3 -> Box(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) { Text("Aún no hay categoría de películas o series.", color = Muted) }
                    section >= 0 && (Skin.shell == 0 || shellLib) -> Library(kind) { section = -1; shellLib = false }
                    else -> {
                        val ongoing = sectionKinds.flatMap { k -> ps(k).filter { it.status == 1 }.map { Ongoing(k, it) } }
                            .sortedWith(compareByDescending<Ongoing> { !pv && it.kind.isGame && it.play.id == sessId }.thenByDescending { it.play.activity })
                            .let { l -> if (pv) l.filter { !Photos.isLocal(it.play.cover) } else l }
                        val collections = sectionKinds.filter { it.custom && !it.isMovie && !it.isTv }.map { k ->
                            val g = gs(k); val pl = ps(k)
                            CollectionInfo(k, (pl.sortedByDescending { it.activity }.map { it.cover } + g.sortedByDescending { it.activity }.map { it.cover }).filter { it.isNotBlank() }.take(3),
                                "${pl.count { it.status == 1 }} ${k.statuses[1].lowercase()} · ${g.size} ${k.doneWord.lowercase()}")
                        }
                        val doneYear = { k: Kind -> gs(k).count { it.end > 0L && LocalDate.ofEpochDay(it.end).year == year } }
                        val liveSlot: @Composable () -> Unit = {
                                if (!pv && store.paired) {
                                    LiveCard(
                                        plays, onShot = { shoot { s -> if (s != null) screen = Screen.ShotView(s, Screen.Home) } }, shooting = shooting,
                                        onMemo = { g -> playFor(g)?.let { memoFor = it to null } },
                                        onOpenPlay = { p -> section = 0; screen = Screen.PlayDetail(p) },
                                        onRemote = { screen = Screen.Suite },
                                        onGuides = { guideSheet = it }
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    if (pendingSegs.isNotEmpty()) {
                                        PendingParts(
                                            pendingSegs,
                                            onRate = { p, sg -> section = if (plays.any { it.id == p.id }) 0 else kinds.firstOrNull { k -> playsOf(k).any { it.id == p.id } }?.id ?: 1; screen = Screen.Note(p, null, sg) },
                                            onDismiss = { _, sg -> skipped = skipped + sg.id; store.segsSkipped = skipped }
                                        )
                                        Spacer(Modifier.height(16.dp))
                                    }
                                    if (Suite.localShots.isNotEmpty() || Suite.downloading.isNotEmpty()) {
                                        ShotStrip(Suite.localShots.take(10), "Últimas capturas") { s -> screen = Screen.ShotView(s, Screen.Home) }
                                        Spacer(Modifier.height(16.dp))
                                    }
                                } else if (!pv) {
                                    SuiteIntro(
                                        "Tu PC en el móvil",
                                        "Entra en tu cuenta (Perfil → Cuenta y pareja) y en Atrio del PC con la misma: verás en directo a qué juegas, pedirás capturas a calidad original y valorarás cada partida."
                                    ) { screen = Screen.Profile }
                                }
                            }
                        if (Skin.shell != 0) {
                            ShellHome(ShellModel(
                                name = if (pv) p!!.name else store.myName.ifBlank { "Atrio" },
                                readOnly = pv,
                                kinds = sectionKinds,
                                entries = { k -> (gs(k).map { ShellEntry(k, null, it) } + ps(k).map { ShellEntry(k, it, null) }).distinctBy { it.id } },
                                ongoing = ongoing,
                                paired = !pv && store.paired,
                                partnerName = p?.name,
                                open = { e ->
                                    shellLib = false
                                    if (e.play != null) openPlay(e.kind, e.play)
                                    else { section = e.kind.id; screen = if (pv) Screen.PartnerItem(e.kind.id, e.id) else Screen.Edit(e.game!!, false) }
                                },
                                primary = { o -> primaryAction(o) { sc -> section = o.kind.id; screen = sc } },
                                primaryLabel = { o -> primaryLabel(o) },
                                library = { k -> if (k.isMovie || k.isTv) cineTv = k.isTv; section = k.id; segment = 0; shellLib = true },
                                diary = { go(4) },
                                discover = if (pv) null else ({ d ->
                                    when (d) {
                                        0 -> { section = 0; screen = Screen.Deck }
                                        1 -> { section = 1; screen = Screen.Quiz(1) }
                                        else -> { section = cineKindId(); screen = Screen.Quiz(2) }
                                    }
                                }),
                                profile = if (pv) null else ({ screen = Screen.Profile }),
                                add = if (pv) null else ({ k -> section = k.id; screen = Screen.Search(true) }),
                                partner = if (p != null) ({ togglePartner() }) else null,
                                stats = { k -> section = k.id; shellLib = true; screen = Screen.Stats },
                                live = liveSlot,
                                pickImage = if (pv) null else ({ o ->
                                    val pl = o.play
                                    section = o.kind.id
                                    screen = Screen.Art(Game(pl.id, pl.name, pl.cover, hero = pl.hero, creator = pl.creator), o.kind.hasHero, false, false, pl, home = true)
                                })
                            ))
                        } else InicioScreen(
                            name = if (pv) p!!.name else store.myName.ifBlank { "Atrio" },
                            readOnly = pv, partnerName = p?.name,
                            hint = if (!pv && p != null && !store.knowsDoubleTap) "Toca a tu pareja (el gato) para ver lo suyo" else null,
                            ongoing = ongoing, sessId = if (pv) 0L else sessId,
                            collections = collections, recs = pendingRecs, acts = acts,
                            goals = listOf(
                                Triple(GAMES.title, doneYear(GAMES), if (pv) p!!.goalGames else goalG),
                                Triple(BOOKS.title, doneYear(BOOKS), if (pv) p!!.goalBooks else goalB)
                            ),
                            onTitleDouble = { togglePartner() },
                            onPartner = if (p != null) ({ togglePartner() }) else null,
                            onProfile = if (pv) null else ({ screen = Screen.Profile }),
                            onOpen = { o -> openPlay(o.kind, o.play) },
                            onPrimary = { o -> primaryAction(o) { sc -> section = o.kind.id; screen = sc } },
                            primaryLabel = { o -> primaryLabel(o) },
                            onCollection = { k -> section = k.id; segment = 0 },
                            onNewCollection = if (pv) null else ({ kindNew = true }),
                            onEditCollection = if (pv) null else ({ k -> kindEdit = k }),
                            onDiscover = if (pv) null else ({ d ->
                                when (d) {
                                    0 -> { dest = 1; section = 0; screen = Screen.Deck }
                                    1 -> { dest = 2; section = 1; screen = Screen.Quiz(1) }
                                    else -> { dest = 3; section = cineKindId(); screen = Screen.Quiz(2) }
                                }
                            }),
                            onRecAdd = { recAdd(it) }, onRecDismiss = { recDone(it) },
                            onAct = { a -> withReveal { screen = Screen.PartnerItem(a.kind, a.id) } },
                            top = liveSlot
                        )
                    }
                }
            } else {
            when (s) {
                Screen.Stats -> StatsScreen(
                    kind, myGames, myPlays, side,
                    when {
                        kind.custom -> 0
                        pv -> if (kind.isBook) (partner?.goalBooks ?: 0) else (partner?.goalGames ?: 0)
                        else -> if (kind.isBook) goalB else goalG
                    },
                    if (kind.isBook) (partner?.goalBooks ?: 0) else (partner?.goalGames ?: 0),
                    onShareYear = if (pv) null else ({ y -> shareYear(kind, myGames, y) })
                ) { screen = Screen.Home }

                Screen.Deck -> DeckScreen(kind, myGames, myPlays, partner, { screen = Screen.Home }, { addCard(it) }) { syncTick++ }

                is Screen.Quiz -> QuizScreen(
                    s.domain, store, mine = (if (s.domain == 1) listOf(BOOKS) else myKinds.filter { it.isMovie || it.isTv })
                        .flatMap { k -> gamesOf(k).map { it.name } + playsOf(k).map { it.name } }.toSet(),
                    onAdd = { item -> quizAdd(item) },
                    onBack = { screen = Screen.Home }
                )

                Screen.Profile -> ProfileScreen(
                    store, partner,
                    onStyle = { Skin.open = true },
                    onSettings = { screen = Screen.Settings },
                    onSuite = { screen = Screen.Suite },
                    goalGames = goalG, goalBooks = goalB,
                    onGoal = { k, v ->
                        val nv = v.coerceIn(0, 999)
                        store.setGoal(k, year, nv)
                        if (k == 1) goalB = nv else goalG = nv
                        syncTick++
                    },
                    couple = {
                        CoupleSettings(store, { partner = it }) {
                            reloadAll()
                            undoImport = store.hasUndoImport
                        }
                    },
                    onBack = { screen = Screen.Home }
                )

                Screen.Settings -> SettingsScreen(
                    key = key, look = Mode.look,
                    onLook = { l -> Mode.look = l; store.look = l },
                    themeMode = Mode.themeMode,
                    onTheme = { t -> store.themeMode = t; withReveal { Mode.themeMode = t } },
                    goalGames = goalG, goalBooks = goalB,
                    onGoal = { k, v ->
                        val nv = v.coerceIn(0, 999)
                        store.setGoal(k, year, nv)
                        if (k == 1) goalB = nv else goalG = nv
                        syncTick++
                    },
                    onSaveKey = { store.key = it; key = store.key; say("Clave guardada") },
                    rawgKey = store.rawgKey, igdbId = store.igdbId, igdbSecret = store.igdbSecret,
                    onSaveServices = { r, id, sec -> store.rawgKey = r; store.igdbId = id; store.igdbSecret = sec; say("Claves guardadas") },
                    tmdbKey = store.tmdbKey,
                    onSaveTmdb = { k ->
                        store.tmdbKey = k
                        if (k.isBlank()) say("Clave de TMDB quitada")
                        else scope.launch {
                            // Se comprueba al momento con una búsqueda de prueba.
                            val ok = try { Tmdb.search("Matrix", k); true } catch (e: Exception) { say("TMDB: ${e.message}"); false }
                            if (ok) say("Clave de TMDB guardada y comprobada")
                        }
                    },
                    studioMsg = studioMsg, onFillStudios = { fillStudios() },
                    driveName = driveName, driveInfo = remember(driveTick, driveName) { driveInfo() },
                    onDriveLink = { driveCreate.launch("ronron-copia.json") },
                    onDriveRestore = { driveOpen.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                    onDriveNow = { scope.launch { val ok = DriveBackup.save(ctx, store); driveTick++; say(if (ok) "Copia guardada en Drive" else "No se pudo guardar en Drive") } },
                    onDriveUnlink = { DriveBackup.unlink(ctx, store); driveName = null; say("Drive desvinculado") },
                    onExport = { exporter.launch("valorin-datos.json") },
                    onImport = { importer.launch(arrayOf("*/*")) },
                    onUndoImport = if (undoImport) ({
                        store.undoImport()
                        reloadAll()
                        undoImport = false
                        say("Importación deshecha")
                    }) else null,
                    couple = {
                        CoupleSettings(store, { partner = it }) {
                            reloadAll()
                            undoImport = store.hasUndoImport
                        }
                    },
                    importMsg = importMsg,
                    onGoodreads = { if (importing) say("Ya hay una importación en marcha") else goodreads.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*")) },
                    onBookmory = { if (importing) say("Ya hay una importación en marcha") else bookmory.launch(arrayOf("application/zip", "application/x-zip-compressed", "text/plain", "application/octet-stream", "*/*")) },
                    steamKey = store.steamKey, steamUser = store.steamUser,
                    onSteam = { k, u -> steamHours(k, u) }
                ) { screen = Screen.Profile }

                is Screen.Search -> if (kind.custom && (!kind.online || s.manual)) CustomAddScreen(
                    kind, s.forPlay, onPhoto = { cb -> pickPhoto(cb) },
                    onCreate = { name, creator, cover ->
                        val id = customItemId(name)
                        val had = myGames.find { it.id == id }
                        if (myGames.any { it.id == id } || myPlays.any { it.id == id }) {
                            say("Ya tenías $name")
                            screen = myPlays.find { it.id == id }?.let { Screen.PlayDetail(it) } ?: had?.let { Screen.Edit(it, false) } ?: Screen.Home
                        } else if (s.forPlay) {
                            val np = Play(id, name, cover, "", 0, emptyList(), creator = creator, updated = now())
                            setPlays(kind, playsOf(kind) + np)
                            screen = Screen.PlayDetail(np, editing = true)
                        } else {
                            screen = Screen.Edit(Game(id, name, cover, creator = creator), true)
                        }
                    },
                    onBack = { screen = if (s.manual) Screen.Search(s.forPlay) else Screen.Home }
                ) else SearchScreen(
                    needsKey = kind.isGame || kind.isMovie, key = if (kind.isMovie) store.tmdbKey else key,
                    title = kind.title,
                    hint = when { kind.isGame -> "Título del juego"; kind.isBook -> "Título o autor"; kind.isMovie -> "Título de la película"; else -> "Título de la serie" },
                    keyMsg = if (kind.isMovie) "Para buscar películas, añade tu clave gratuita de TMDB en Ajustes → Servicios." else "Antes de buscar, añade tu API key en Ajustes.",
                    onManual = if (kind.custom) ({ screen = Screen.Search(s.forPlay, true) }) else null,
                    manualText = if (kind.isMovie || kind.isTv) "¿No aparece? Añádela a mano con una foto" else "¿No aparece? Añádelo a mano con una foto",
                    owned = { id ->
                        when {
                            myGames.any { it.id == id } -> "Valorado"
                            myPlays.any { it.id == id } -> kind.statuses[myPlays.first { it.id == id }.status.coerceIn(0, 2)]
                            else -> null
                        }
                    },
                    search = { q ->
                        when {
                            kind.isBook -> OpenLib.search(q)
                            kind.isMovie -> Tmdb.search(q, store.tmdbKey)
                            kind.isTv -> TvMaze.search(q)
                            else -> Sgdb.search(q, key)
                        }
                    },
                    onPick = { h ->
                        val valued = myGames.find { it.id == h.id }
                        if (!s.forPlay && valued != null) {
                            say("Ya lo tenías valorado")
                            screen = Screen.Edit(valued, false)
                        } else {
                            val ex = if (s.forPlay) myPlays.find { it.id == h.id } else null
                            val cr = h.creator
                            val play = if (s.forPlay) (ex ?: Play(h.id, h.name, h.cover, "", 0, emptyList(), creator = cr, pages = h.pages)) else null
                            val g = Game(
                                h.id, h.name, ex?.cover ?: h.cover, hero = ex?.hero ?: "", creator = ex?.creator ?: cr,
                                pages = ex?.pages ?: h.pages
                            )
                            screen = Screen.Art(g, false, true, true, play)
                        }
                    },
                    onBack = { screen = Screen.Home }
                )

                is Screen.Art -> ArtScreen(
                    game = s.game, hero = s.hero,
                    load = {
                        if (kind.isMovie && isWebId(s.game.id)) Tmdb.images(s.game.id, store.tmdbKey, s.hero)
                        else if (kind.isTv && isWebId(s.game.id)) TvMaze.images(s.game.id, s.hero)
                        else if (kind.custom) emptyList()
                        // Libros importados que Open Library no reconoció: portadas de lo que sale al buscar su título.
                        else if (kind.isBook) OpenLib.covers(s.game.id).ifEmpty { OpenLib.coversByTitle(s.game.name) }
                        else if (s.hero) Sgdb.heroes(s.game.id, key)
                        else Sgdb.covers(s.game.id, key)
                    },
                    onPick = { url ->
                        if (!s.hero) {
                            val g = s.game.copy(cover = url)
                            if (s.chain && kind.hasHero) {
                                screen = Screen.Art(g, true, s.isNew, true, s.play)
                            } else {
                                toEdit(s, g)
                            }
                        } else {
                            toEdit(s, s.game.copy(hero = url))
                        }
                    },
                    onSkip = { toEdit(s, s.game) },
                    onBack = {
                        val p = s.play
                        screen = if (s.chain || s.home) Screen.Home else if (p != null) Screen.PlayDetail(p, editing = true) else Screen.Edit(s.game, s.isNew, editing = true)
                    },
                    search = if (kind.isBook && !s.hero) ({ q -> OpenLib.coversFor(q) }) else null,
                    translate = if (kind.isBook && !s.hero) ({ q -> TitleEn.translate(q)?.let { it.title to it.source } }) else null,
                    onGallery = {
                        pickPhoto { url ->
                            if (!s.hero) {
                                val g = s.game.copy(cover = url)
                                if (s.chain && kind.hasHero) screen = Screen.Art(g, true, s.isNew, true, s.play) else toEdit(s, g)
                            } else toEdit(s, s.game.copy(hero = url))
                        }
                    }
                )

                is Screen.Edit -> { ChameleonEffect(s.game.cover); EditScreen(
                    s.game, s.isNew, kind, side,
                    myReact = reacts[reactKey(kind, s.game.id)],
                    onReact = { e -> react(kind, s.game.id, e) },
                    onSave = { saveGame(it); screen = Screen.Home },
                    onDelete = { deleteGame(s.game); screen = Screen.Home },
                    onArt = { g, hero -> screen = Screen.Art(g, hero, s.isNew, false, null) },
                    onExport = { g, m -> export(g, m) },
                    onRecommend = if (side != null && !kind.custom) ({ g, note -> recommend(kind, g.id, g.name, g.cover, g.creator, note) }) else null,
                    editing = s.editing,
                    findTtb = if (kind.isGame) ({ n -> TimeToBeat.find(n, store) }) else null,
                    onQuiet = { g -> if (gamesOf(kind).any { it.id == g.id }) setGames(kind, gamesOf(kind).map { if (it.id == g.id) g else it }) }
                ) { screen = Screen.Home } }

                is Screen.PlayDetail -> {
                    val live = myPlays.find { it.id == s.play.id } ?: s.play
                    ChameleonEffect(live.cover)
                    PlayDetailScreen(
                        kind = kind, partner = side, p0 = live, startEditing = s.editing, sessId = sessId, sessHours = store.sessionHours,
                        sessStart = store.sessionStart,
                        myReact = reacts[reactKey(kind, s.play.id)],
                        onReact = { e -> react(kind, s.play.id, e) },
                        onChange = { savePlay(it) },
                        onNote = { p, e -> screen = Screen.Note(p, e) },
                        onStart = { p, h -> startSession(p, h) },
                        onStop = { stopSession() },
                        onArt = { p, hero ->
                            screen = Screen.Art(Game(p.id, p.name, p.cover, hero = p.hero, creator = p.creator), hero, false, false, p)
                        },
                        onFinish = { p ->
                            val b = p.baremo()
                            val notes = p.entries.filter { it.note.isNotBlank() }.joinToString("\n\n") { it.note }
                            val g = Game(
                                p.id, p.name, p.cover,
                                b?.general ?: 50, b?.gfx ?: 50, b?.story ?: 50, b?.music ?: 50, b?.gameplay ?: 50,
                                notes, p.hero, p.start, if (p.end > 0L) p.end else today(), p.creator,
                                platform = p.platform, pages = p.pages, minutes = p.minutes, updated = now(),
                                ttbMain = p.ttbMain, ttbExtra = p.ttbExtra, ttbFull = p.ttbFull, ttbSrc = p.ttbSrc,
                                logo = p.logo
                            )
                            if (!kind.isBook && sessId == p.id) stopSession()
                            val withMins = if (!kind.isBook) (plays.find { it.id == p.id }?.minutes ?: p.minutes) else p.minutes
                            // Si ya lo tenías valorado (lo has rejugado o releído), se conserva lo anterior y se suma lo nuevo.
                            val prev = gamesOf(kind).find { it.id == p.id }
                            val fin = if (prev == null) g.copy(minutes = withMins) else g.copy(
                                review = listOf(prev.review, notes).filter { it.isNotBlank() }.joinToString("\n\n"),
                                minutes = prev.minutes + withMins, fav = prev.fav,
                                hero = g.hero.ifBlank { prev.hero }, creator = g.creator.ifBlank { prev.creator },
                                platform = g.platform.ifBlank { prev.platform }, pages = if (g.pages > 0) g.pages else prev.pages,
                                start = if (g.start > 0L) g.start else prev.start,
                                ttbMain = if (g.ttbMain > 0) g.ttbMain else prev.ttbMain,
                                ttbExtra = if (g.ttbExtra > 0) g.ttbExtra else prev.ttbExtra,
                                ttbFull = if (g.ttbFull > 0) g.ttbFull else prev.ttbFull,
                                ttbSrc = g.ttbSrc.ifBlank { prev.ttbSrc },
                                logo = g.logo.ifBlank { prev.logo }
                            )
                            setGames(kind, gamesOf(kind).filter { it.id != p.id } + fin)
                            setPlays(kind, playsOf(kind).filter { it.id != p.id })
                            if (prev != null) say("Ya lo tenías valorado: he juntado tu opinión y tus horas")
                            tab = 1
                            screen = Screen.Edit(fin, false, editing = true)
                        },
                        onRecommend = if (side != null && !kind.custom) ({ p, note -> recommend(kind, p.id, p.name, p.cover, p.creator, note) }) else null,
                        onDelete = { p -> deletePlay(p); screen = Screen.Home },
                        findTtb = { n -> TimeToBeat.find(n, store) },
                        findEpisodes = if (kind.isTv) ({ id, name -> TvMaze.episodes(id, name) }) else null,
                        onRateSeg = { p, sg -> screen = Screen.Note(p, null, sg) },
                        onAddSeg = { p -> segFor = p },
                        onMemo = { p -> memoFor = p to null },
                        onDeleteMemo = { p, m -> deleteMemo(p, m) },
                        onOpenShot = { sh -> screen = Screen.ShotView(sh, Screen.PlayDetail(live)) },
                        onPartDone = { p, name ->
                            if (!p.noParts) scope.launch {
                                val r = snack.showSnackbar("«$name» completado", actionLabel = "Valorar", withDismissAction = true)
                                if (r == SnackbarResult.ActionPerformed) screen = Screen.Note(myPlays.find { it.id == p.id } ?: p, null, null, name)
                            }
                        },
                        onBack = { screen = Screen.Home }
                    )
                }

                is Screen.Note -> NoteScreen(
                    play = s.play, entry = s.entry, kind = kind, seg = s.seg, part = s.part,
                    onSave = { e ->
                        // Sobre la versión más reciente (puede haber llegado algo de Atrio mientras escribías)
                        val base = myPlays.find { it.id == s.play.id } ?: s.play
                        val old = s.entry
                        val es = if (old == null) base.entries + e
                        else base.entries.map { x -> if (x.time == old.time) e else x }
                        val np = base.copy(entries = es)
                        savePlay(np)
                        screen = Screen.PlayDetail(np)
                    },
                    onDelete = {
                        val base = myPlays.find { it.id == s.play.id } ?: s.play
                        val old = s.entry
                        val np = base.copy(entries = base.entries.filter { x -> x.time != old?.time })
                        savePlay(np)
                        screen = Screen.PlayDetail(np)
                    },
                    onBack = { screen = Screen.PlayDetail(myPlays.find { it.id == s.play.id } ?: s.play) }
                )

                Screen.Suite -> SuiteScreen(
                    plays, store, onBack = { screen = Screen.Home },
                    onOpenShot = { sh -> screen = Screen.ShotView(sh, Screen.Suite) },
                    onMemo = { g -> playFor(g)?.let { memoFor = it to null } },
                    onOpenPlay = { p -> section = 0; screen = Screen.PlayDetail(p) },
                    say = { say(it) }
                )

                is Screen.ShotView -> ShotViewer(
                    s.shot, onBack = { screen = s.back },
                    onDelete = { Suite.deleteLocal(s.shot); screen = s.back },
                    onMemo = (plays.find { it.id == s.shot.sg })?.let { p -> { memoFor = p to s.shot } }
                )

                is Screen.PartnerItem -> {
                    val p = partner
                    if (p == null) {
                        LaunchedEffect(Unit) { screen = Screen.Home }
                    } else {
                        val k = p.kindById(s.kind)
                        val ps = p.side(k)
                        val g = ps.games.find { it.id == s.id }
                        val pl = ps.plays.find { it.id == s.id }
                        PartnerPage(
                            ps, s.id, k, g?.name ?: pl?.name ?: "", g?.cover ?: pl?.cover ?: "", g?.hero ?: pl?.hero ?: "",
                            onBack = { screen = Screen.Home }, onFlip = null,
                            myReact = reacts[reactKey(k, s.id)],
                            onReact = { e -> react(k, s.id, e) }
                        )
                    }
                }
                else -> Unit
            }
            }
            }
        }
        }
        // Dock: solo en las pantallas principales
        androidx.compose.animation.AnimatedVisibility(
            dockOn, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(Motion.bouncy()) { it } + fadeIn(), exit = slideOutVertically(tween(Motion.ms(160))) { it } + fadeOut(tween(Motion.ms(120)))
        ) {
            Dock(
                listOf(
                    DockItem(Icons.Rounded.Home, "Inicio"), DockItem(Icons.Rounded.SportsEsports, "Juegos"),
                    DockItem(Icons.Rounded.AutoStories, "Libros"), DockItem(Icons.Rounded.Movie, "Cine"),
                    DockItem(Icons.Rounded.CalendarMonth, "Diario")
                ),
                if (dest == 0 && section >= 0) 0 else dest
            ) { go(it) }
        }
        if (wheelOpen) {
            val pool = myPlays.filter { it.status == 0 }
            val ps0 = side
            val shared = if (ps0 == null) emptyList() else pool.filter { mine -> ps0.plays.any { it.id == mine.id && it.status == 0 } }
            WheelDialog(pool, shared, partner?.name, kind, { p -> wheelOpen = false; startFromWheel(p) }, { p -> wheelOpen = false; screen = Screen.PlayDetail(p) }) { wheelOpen = false }
        }

        val pr = pendingRestore
        if (pr != null) {
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text("¿Recuperar desde Drive?", fontFamily = Display) },
                text = { Text("Se sustituirán los datos de este dispositivo por los del archivo, y ese archivo quedará vinculado para las próximas copias. Podrás deshacerlo en Copia de seguridad.") },
                confirmButton = {
                    TextButton({
                        pendingRestore = null
                        scope.launch {
                            try {
                                store.importAll(DriveBackup.read(ctx, pr))
                                reloadAll()
                                key = store.key
                                undoImport = store.hasUndoImport
                                DriveBackup.link(ctx, store, pr)
                                driveName = DriveBackup.name(ctx, store) ?: "tu archivo"
                                say("Datos recuperados de Drive")
                            } catch (e: Exception) { say("No se pudo recuperar: ${e.message}") }
                        }
                    }) { Text("Recuperar") }
                },
                dismissButton = { TextButton({ pendingRestore = null }) { Text("Cancelar") } },
                shape = shp(24.dp)
            )
        }

        val gs = guideSheet
        if (gs != null) {
            PcGameSheet(gs, Suite.target() != null, onPlay = {
                scope.launch { val (_, m) = Suite.send("launch", org.json.JSONObject().put("key", gs.key)); say(m) }
            }, onDismiss = { guideSheet = null }, say = { say(it) })
        }
        if (shareUrl.isNotEmpty()) {
            if (!store.paired) { LaunchedEffect(shareUrl) { say("Para guardar webs en tus juegos del PC, entra antes en tu cuenta"); onShareHandled() } }
            else ShareToGameDialog(shareUrl, shareTitle, onDismiss = { onShareHandled() }, say = { say(it) })
        }
        val mf = memoFor
        if (mf != null) {
            MemoDialog(
                "Apunte · ${mf.first.name}", canShot = Suite.target() != null, presetShot = mf.second,
                onSave = { t, w -> memoFor = null; saveMemo(mf.first, t, w, mf.second) },
                onDismiss = { memoFor = null }
            )
        }
        val sf = segFor
        if (sf != null) {
            AddSegDialog(kind, onSave = { sg -> segFor = null; addSeg(sf, sg) }, onDismiss = { segFor = null })
        }

        if (kindNew || kindEdit != null) {
            val ke = kindEdit
            KindDialog(
                ke, if (ke == null) 0 else gamesOf(ke).size + playsOf(ke).size,
                onSave = { t, cr, slots, pr, col, src ->
                    if (ke == null) {
                        val nk = customKind(store.nextKindId(), t, cr, pr, col, src)
                        setKinds(kinds + nk)
                        extra = extra + (nk.id to emptyList())
                        extraPlays = extraPlays + (nk.id to emptyList())
                        say("Categoría «${nk.title}» creada")
                    } else {
                        val nk = customKind(ke.id, t, cr, pr, col, src)
                        // Las notas siguen a su criterio aunque se borren o se añadan otros.
                        if (slots != (0 until ke.crit).toList()) {
                            setGames(nk, gamesOf(ke).map { it.withScores(remapScores(it.scores(), slots)) })
                            setPlays(nk, playsOf(ke).map { p -> p.copy(entries = p.entries.map { e -> e.withScores(remapScores(e.scores(), slots)) }) })
                        }
                        setKinds(kinds.map { if (it.id == ke.id) nk else it })
                    }
                    syncTick++
                    kindNew = false; kindEdit = null
                },
                onDelete = if (ke == null) null else ({
                    store.deleteKind(ke.id)
                    kinds = store.kinds
                    Mode.kinds = kinds
                    extra = extra - ke.id
                    extraPlays = extraPlays - ke.id
                    if (section == ke.id) section = -1
                    syncTick++
                    kindNew = false; kindEdit = null
                    say("Categoría borrada")
                }),
                onDismiss = { kindNew = false; kindEdit = null }
            )
        }

        // Revista: una franja de papel bajo la hora y la batería, para que lo que se desplaza no pase por debajo
        if (Skin.revista) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).then(Modifier.drawBehind { drawRect(Paper) }))
        // Con el dock a la vista, los avisos salen por encima de él (no lo tapan)
        val snackPad by animateDpAsState(if (dockOn) 84.dp else 12.dp, tween(Motion.ms(220)), label = "snackpad")
        SnackbarHost(
            snack,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = snackPad)
        ) { data ->
            Snackbar(
                data, shape = shp(18.dp),
                containerColor = Charcoal, contentColor = Paper, actionColor = GoldOnInk, dismissActionContentColor = Crudo
            )
        }
    }
    }
}
