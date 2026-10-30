@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.gamerate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

private val SortNames = listOf("Nota", "Recientes", "Nombre", "Fecha de fin")

private fun matches(q: String, vararg fields: String): Boolean {
    if (q.isBlank()) return true
    val t = q.trim().lowercase()
    return fields.any { it.lowercase().contains(t) }
}

/** Botón flotante de añadir. */
@Composable
private fun AddButton(modifier: Modifier, onAdd: () -> Unit) {
    FloatingActionButton(
        onAdd, modifier, containerColor = Charcoal, contentColor = Crudo, shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp)
    ) { Icon(Icons.Rounded.Add, "Añadir") }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, color = UiSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).pill(18, 16, 12))
}

@Composable
fun Home(games: List<Game>, kind: Kind, onMenu: () -> Unit, onAdd: (() -> Unit)?, partner: PartnerSide?, onOpen: (Game) -> Unit) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(0) }
    var favOnly by remember { mutableStateOf(false) }
    val shown = remember(games, query, sort, favOnly) {
        val f = games.filter { (!favOnly || it.fav) && matches(query, it.name, it.creator, it.platform) }
        when (sort) {
            1 -> f.sortedByDescending { it.activity }
            2 -> f.sortedBy { it.name.lowercase() }
            3 -> f.sortedByDescending { it.end }
            else -> f.sortedByDescending { it.general }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            TopBar(kind.title, onMenu, "Valorados") {
                IconPill(if (favOnly) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Solo favoritos", favOnly) { favOnly = !favOnly }
            }
            if (games.isNotEmpty()) {
                SearchPill(query, "Buscar por nombre, ${kind.creatorWord.lowercase()} o ${kind.platformWord.lowercase()}", Modifier.fillMaxWidth()) { query = it }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SortNames.forEachIndexed { i, s -> SoftChip(s, sort == i) { sort = i } }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (games.isEmpty()) {
                EmptyNote(if (onAdd == null) "No hay nada valorado todavía." else "Aún no has valorado nada. Pulsa + para añadir el primero.")
            } else if (shown.isEmpty()) {
                EmptyNote("No hay nada que coincida.")
            }
            LazyVerticalGrid(
                formCells(),
                horizontalArrangement = Arrangement.spacedBy(formGap().first), verticalArrangement = Arrangement.spacedBy(formGap().second),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                itemsIndexed(shown, key = { _, g -> g.id }) { i, g ->
                    FlipCard(
                        enabled = partner != null, onClick = { onOpen(g) },
                        modifier = Modifier.appear(200 + i * 50, i < 12),
                        front = {
                            FormTile(g.cover, g.name, g.creator, gauge = { d -> Ring(g.general, ColGeneral, d) }) {
                                    if (g.fav) {
                                        Box(
                                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(Crudo, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) { Icon(Icons.Rounded.Star, null, tint = Gold, modifier = Modifier.size(15.dp)) }
                                    }
                                    val r = partner?.reactsToMe?.get(g.id)
                                    if (r != null) {
                                        Box(
                                            Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp).background(Crudo, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) { Text(r, fontSize = 12.sp) }
                                    }
                            }
                        },
                        back = { partner?.let { PartnerFace(it, g.id, kind) } }
                    )
                }
            }
        }
        if (onAdd != null) AddButton(Modifier.align(Alignment.BottomEnd).padding(20.dp), onAdd)
    }
}

fun statusOrder(status: Int): Int = when (status) {
    1 -> 0
    0 -> 1
    else -> 2
}

@Composable
fun PlayingHome(
    plays: List<Play>, kind: Kind, onMenu: () -> Unit, onAdd: (() -> Unit)?, partner: PartnerSide?,
    onStartPlay: (Play) -> Unit, onOpen: (Play) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(-1) }
    var wheel by remember { mutableStateOf(false) }
    val shown = remember(plays, query, filter) {
        plays.filter { (filter == -1 || it.status == filter) && matches(query, it.name, it.creator, it.platform) }
            .sortedWith(compareBy<Play> { statusOrder(it.status) }.thenByDescending { it.activity })
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            TopBar(kind.title, onMenu, kind.statuses[1]) {
                if (onAdd != null) IconPill(Icons.Rounded.Casino, "¿Qué empiezo?") { wheel = true }
            }
            if (plays.isNotEmpty()) {
                SearchPill(query, "Buscar", Modifier.fillMaxWidth()) { query = it }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoftChip("Todos", filter == -1) { filter = -1 }
                    SoftChip(kind.statuses[1], filter == 1) { filter = 1 }
                    SoftChip(kind.statuses[0], filter == 0) { filter = 0 }
                    SoftChip(kind.statuses[2], filter == 2) { filter = 2 }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (plays.isEmpty()) {
                EmptyNote(
                    when {
                        onAdd == null -> "No hay nada aquí todavía."
                        kind.custom -> "Añade con + lo que quieras empezar."
                        kind.isBook -> "Añade con + los libros que quieras leer."
                        else -> "Añade con + los juegos que quieras pasarte."
                    }
                )
            } else if (shown.isEmpty()) {
                EmptyNote("No hay nada que coincida.")
            }
            LazyVerticalGrid(
                formCells(),
                horizontalArrangement = Arrangement.spacedBy(formGap().first), verticalArrangement = Arrangement.spacedBy(formGap().second),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                itemsIndexed(shown, key = { _, p -> p.id }) { i, p ->
                    val bc = when (p.status) { 1 -> ColGeneral; 2 -> Muted; else -> Line }
                    val st = kind.statuses[p.status.coerceIn(0, 2)]
                    val pct = p.percent()
                    val extra = when {
                        pct != null -> " · $pct %"
                        kind.isBook && p.chapter > 0 -> " · cap. ${p.chapter}"
                        !kind.isBook && p.minutes > 0 -> " · ${hoursText(p.minutes)}"
                        else -> ""
                    }
                    FlipCard(
                        enabled = partner != null, onClick = { onOpen(p) },
                        modifier = Modifier.appear(200 + i * 50, i < 12),
                        front = {
                            FormTile(p.cover, p.name, "$st · ${p.entries.size} ${if (p.entries.size == 1) "apunte" else "apuntes"}$extra",
                                alpha = if (p.status == 2) 0.55f else 1f, dot = bc) {
                                    val r = partner?.reactsToMe?.get(p.id)
                                    if (r != null) {
                                        Box(
                                            Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp).background(Crudo, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) { Text(r, fontSize = 12.sp) }
                                    }
                            }
                        },
                        back = { partner?.let { PartnerFace(it, p.id, kind) } }
                    )
                }
            }
        }
        if (onAdd != null) AddButton(Modifier.align(Alignment.BottomEnd).padding(20.dp), onAdd)
    }
    if (wheel) {
        val pool = plays.filter { it.status == 0 }.ifEmpty { plays.filter { it.status != 2 } }
        val ps = partner
        val shared = if (ps == null) emptyList() else pool.filter { mine -> ps.plays.any { it.id == mine.id && it.status == 0 } }
        WheelDialog(pool, shared, partner?.name, kind, { p -> wheel = false; onStartPlay(p) }, { p -> wheel = false; onOpen(p) }) { wheel = false }
    }
}

/** Ruleta: elige al azar qué empezar de tu lista. */
@Composable
fun WheelDialog(
    all: List<Play>, shared: List<Play>, partnerName: String?, kind: Kind,
    onStart: (Play) -> Unit, onOpen: (Play) -> Unit, onDismiss: () -> Unit
) {
    var together by remember { mutableStateOf(false) }
    val pool = if (together && shared.isNotEmpty()) shared else all
    var idx by remember { mutableStateOf(0) }
    var spin by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(spin, together) {
        idx = 0
        if (pool.isEmpty()) return@LaunchedEffect
        done = false
        var d = 45L
        val turns = 18 + (0 until pool.size.coerceAtLeast(1)).random()
        repeat(turns) {
            idx = (idx + 1) % pool.size
            delay(d)
            d += 14L
        }
        done = true
    }
    val sc by animateFloatAsState(if (done) 1.06f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "ruleta")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (done) "¡Te toca!" else "Girando…", fontFamily = Display) },
        text = {
            if (pool.isEmpty()) {
                Text("Añade cosas a tu lista (${kind.statuses[0]}) y la ruleta elegirá por ti.")
            } else {
                val p = pool[idx.coerceIn(0, pool.size - 1)]
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (partnerName != null && shared.isNotEmpty()) {
                        SoftChip("En común (${shared.size})", together, onPanel = true) { together = !together }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (Skin.revista) {
                        // Revista: sin portadas; el título enorme va cambiando como un letrero de estación
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Charcoal))
                        Box(Modifier.fillMaxWidth().heightIn(min = 150.dp).graphicsLayer { scaleX = sc; scaleY = sc }, contentAlignment = Alignment.Center) {
                            FitTitle(p.name, 40.sp, 42.sp, letterSpacing = (-1).sp, color = if (done) ColGeneral else Charcoal, maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 14.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Charcoal))
                        if (p.creator.isNotBlank()) Text(p.creator.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                            letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        AsyncImage(
                            p.cover, p.name,
                            Modifier.width(170.dp).aspectRatio(2f / 3f).graphicsLayer { scaleX = sc; scaleY = sc }
                                .shadow(4.dp, shp(12.dp)).clip(shp(12.dp)).background(Sand),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(p.name, fontFamily = Display, fontSize = 20.sp, textAlign = TextAlign.Center)
                        if (p.creator.isNotBlank()) Text(p.creator, color = Muted, fontSize = 13.sp)
                    }
                    if (done) {
                        Spacer(Modifier.height(8.dp))
                        Text("Elegido entre ${pool.size}.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (pool.isNotEmpty() && done) {
                TextButton({ onStart(pool[idx.coerceIn(0, pool.size - 1)]) }) { Text("¡A por él!") }
            }
        },
        dismissButton = {
            Row {
                if (pool.isNotEmpty() && done) {
                    TextButton({ onOpen(pool[idx.coerceIn(0, pool.size - 1)]) }) { Text("Ver ficha") }
                    TextButton({ spin++ }) { Text("Otra vez") }
                }
                TextButton(onDismiss) { Text("Cerrar") }
            }
        },
        shape = shp(24.dp)
    )
}

@Composable
private fun SettingsSection(title: String, delay: Int, content: @Composable ColumnScope.() -> Unit) {
    Panel(Modifier.padding(bottom = 14.dp).appear(delay), title, content)
}

@Composable
fun SettingsScreen(
    key: String,
    onSaveKey: (String) -> Unit, onExport: () -> Unit, onImport: () -> Unit, onUndoImport: (() -> Unit)?,
    rawgKey: String, igdbId: String, igdbSecret: String, onSaveServices: (String, String, String) -> Unit,
    tmdbKey: String = "", onSaveTmdb: (String) -> Unit = {},
    studioMsg: String, onFillStudios: () -> Unit,
    driveName: String?, driveInfo: String, onDriveLink: () -> Unit, onDriveRestore: () -> Unit,
    onDriveNow: () -> Unit, onDriveUnlink: () -> Unit,
    importMsg: String = "", onGoodreads: () -> Unit = {}, onBookmory: () -> Unit = {}, onMassScores: () -> Unit = {}, onDuelos: () -> Unit = {},
    steamKey: String = "", steamUser: String = "", onSteam: (String, String) -> Unit = { _, _ -> },
    /** Tu nube (kDrive): libros, fotos y capturas en todos tus dispositivos. */
    nube: (@Composable () -> Unit)? = null,
    /** Las pestañas de Ajustes (General · Servicios y copias), justo bajo el título. */
    pestanas: @Composable () -> Unit = {},
    onBack: () -> Unit
) {
    var k by remember { mutableStateOf(key) }
    var sk by remember { mutableStateOf(steamKey) }
    var su by remember { mutableStateOf(steamUser) }
    var iid by remember { mutableStateOf(igdbId) }
    var isec by remember { mutableStateOf(igdbSecret) }
    var rk by remember { mutableStateOf(rawgKey) }
    var tk by remember { mutableStateOf(tmdbKey) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding().wrapContentWidth().widthIn(max = 760.dp).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (Skin.revista) { TopBar("Atrio", onBack, "Ajustes"); pestanas() }
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Column {
                Overline("Perfil")
                Text("Ajustes", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 33.sp)
            }
        }
        Spacer(Modifier.height(18.dp))

        SettingsSection("Portadas de juegos (SteamGridDB)", 120) {
            OutlinedTextField(k, { k = it }, label = { Text("API key de SteamGridDB") }, singleLine = true,
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
            Plegado("Se genera en tu perfil de steamgriddb.com, en Preferencias > API. Se guarda solo en este dispositivo.")
            Button({ onSaveKey(k) }, shape = pillShape()) { Text("Guardar clave") }
        }

        SettingsSection("Servicios", 380) {
            Text("RAWG (estudios y recomendaciones de juegos)", fontWeight = FontWeight.SemiBold)
            Plegado(
                "Con una clave gratuita de rawg.io/apidocs, «Descubrir» te propone juegos según tus respuestas y se detectan mejor los estudios. " +
                    "Sin ella, los estudios se buscan en Wikidata y Descubrir solo funciona con libros, pelis y series."
            )
            OutlinedTextField(rk, { rk = it }, label = { Text("Clave de RAWG") }, singleLine = true,
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text("IGDB (cuánto dura cada juego)", fontWeight = FontWeight.SemiBold)
            Plegado(
                "Da el tiempo que tardan sus jugadores en pasarse cada juego: deprisa, normal y al 100 %. Es gratis y va con una cuenta de Twitch " +
                    "(con verificación en dos pasos): en dev.twitch.tv/console registra una aplicación con cualquier nombre, " +
                    "URL de redirección «http://localhost», categoría «Application Integration» y tipo «Confidencial». Copia aquí su Client ID y un secreto nuevo. " +
                    "Sin esto se usa la media de horas de RAWG, o la pones a mano."
            )
            OutlinedTextField(iid, { iid = it }, label = { Text("Client ID de Twitch") }, singleLine = true,
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(isec, { isec = it }, label = { Text("Secreto (Client Secret)") }, singleLine = true,
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button({ onSaveServices(rk, iid, isec) }, shape = pillShape()) { Text("Guardar claves") }
            Spacer(Modifier.height(16.dp))
            Text("TMDB (películas)", fontWeight = FontWeight.SemiBold)
            Plegado(
                "Para buscar películas con sus pósters en español, sus fondos y su director. Es gratis: crea una cuenta en themoviedb.org " +
                    "desde el ordenador, entra en los ajustes de tu cuenta → API y pide una clave para uso personal. " +
                    "Pega aquí la «API Key» o el «API Read Access Token» (vale cualquiera de los dos). Las series no necesitan clave."
            )
            OutlinedTextField(tk, { tk = it }, label = { Text("Clave de TMDB") }, singleLine = true,
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button({ onSaveTmdb(tk) }, shape = pillShape()) { Text("Guardar clave de TMDB") }
            Spacer(Modifier.height(12.dp))
            Text("Estudios que faltan", fontWeight = FontWeight.SemiBold)
            Text("Busca en internet el estudio de todos tus juegos que no lo tengan.", color = UiSoft, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onFillStudios, shape = pillShape()) { Text("Rellenar estudios") }
            if (studioMsg.isNotEmpty()) Text(studioMsg, color = UiSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }

        SettingsSection("Traer de otras apps", 390) {
            Text("Goodreads", fontWeight = FontWeight.SemiBold)
            Plegado(
                "En goodreads.com: Mis libros → Importar y exportar → Exportar biblioteca. Descarga el CSV y elígelo aquí. " +
                    "Los leídos pasan a valorados (cada estrella son 20 puntos), los que estás leyendo a Leyendo y el resto a En lista. " +
                    "Lo que ya tengas no se duplica."
            )
            OutlinedButton(onGoodreads, shape = pillShape()) { Text("Importar CSV de Goodreads") }
            if (importMsg.isNotEmpty() && "Steam" !in importMsg && "Bookmory" !in importMsg) ImportNote(importMsg)
            Spacer(Modifier.height(14.dp))
            Text("Bookmory", fontWeight = FontWeight.SemiBold)
            Plegado(
                "Exporta tus datos desde Bookmory (un .zip con un .txt por libro) y elige aquí ese .zip tal cual. " +
                    "Los terminados pasan a Leídos con tu nota (cada estrella son 20 puntos), tus fechas, favoritos, comentarios, relecturas y citas; " +
                    "los que estás leyendo o tienes en pausa, a Leyendo, y los de leer más tarde, a En lista. " +
                    "Lo que ya tengas no se duplica: solo se completa lo que le falte, sin tocar tus notas. Se puede deshacer en Copia de seguridad."
            )
            OutlinedButton(onBookmory, shape = pillShape()) { Text("Importar ZIP de Bookmory") }
            if (importMsg.isNotEmpty() && "Bookmory" in importMsg) ImportNote(importMsg)
            Spacer(Modifier.height(14.dp))
            Text("Horas de Steam", fontWeight = FontWeight.SemiBold)
            Plegado(
                "Copia las horas jugadas de tu biblioteca de Steam en todos tus juegos, también los valorados (solo si Steam tiene más). " +
                    "Una vez guardado, se actualiza solo al abrir la app. " +
                    "Necesitas una clave en steamcommunity.com/dev/apikey y que los detalles de juego de tu perfil sean públicos."
            )
            OutlinedTextField(sk, { sk = it }, label = { Text("Clave de Steam") }, singleLine = true,
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(su, { su = it }, label = { Text("Tu perfil (URL, nombre o SteamID64)") }, singleLine = true,
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ onSteam(sk, su) }, shape = pillShape(), enabled = sk.isNotBlank() && su.isNotBlank()) { Text("Traer horas de Steam") }
            if (importMsg.isNotEmpty() && "Steam" in importMsg) ImportNote(importMsg)
            Spacer(Modifier.height(14.dp))
            // (cambiar notas en masa y los duelos de libros están una sola vez: en la pestaña General, «Repasar notas»)
            Text("Después de importar, repasa las notas en la pestaña General → Repasar notas (en masa o con duelos de libros).", color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }

        if (nube != null) SettingsSection("Tu nube (kDrive)", 395) { nube() }

        SettingsSection("Google Drive", 400) {
            if (driveName == null) {
                Text(
                    "Vincula un archivo de tu Google Drive y cada cambio se guardará ahí solo. " +
                        "Al pulsar «Vincular», en el selector abre el menú de la izquierda, elige Drive y guarda el archivo.",
                    color = UiSoft, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onDriveLink, shape = pillShape()) { Text("Vincular") }
                    OutlinedButton(onDriveRestore, shape = pillShape()) { Text("Recuperar de Drive") }
                }
                Text(
                    "Si has reinstalado la app: «Recuperar de Drive», elige tu archivo y se cargan tus datos (y queda vinculado otra vez).",
                    color = UiSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                Text("Vinculado con $driveName", fontWeight = FontWeight.SemiBold)
                if (driveInfo.isNotEmpty()) Text(driveInfo, color = UiSoft, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onDriveNow, shape = pillShape()) { Text("Guardar ahora") }
                    OutlinedButton(onDriveUnlink, shape = pillShape()) { Text("Desvincular") }
                }
                TextButton(onDriveRestore) { Text("Recuperar desde un archivo de Drive", color = Muted) }
            }
        }

        SettingsSection("Copia de seguridad", 420) {
            Text(
                "Exporta todo lo guardado (videojuegos y libros, valorados y en progreso, notas, apuntes y fechas) a un archivo. " +
                    "El archivo incluye todas tus claves (SteamGridDB, RAWG, IGDB, Steam y TMDB), igual que la copia de Drive y la de la nube. Al importar, se sustituye todo lo que tienes ahora.",
                color = UiSoft, modifier = Modifier.padding(bottom = 8.dp)
            )
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ onExport() }, shape = pillShape()) { Text("Exportar datos") }
                OutlinedButton({ confirm = true }, shape = pillShape()) { Text("Importar datos") }
            }
            if (onUndoImport != null) {
                Spacer(Modifier.height(6.dp))
                TextButton(onUndoImport) { Text("Deshacer la última importación", color = ColGeneral) }
            }
        }

        SettingsSection("Acerca de", 500) {
            // La versión sale del propio APK (antes estaba escrita a mano y se quedó en 3.6)
            val actx = androidx.compose.ui.platform.LocalContext.current
            val ver = remember { try { actx.packageManager.getPackageInfo(actx.packageName, 0).versionName } catch (e: Exception) { null } ?: "" }
            Text("Atrio $ver", fontWeight = FontWeight.SemiBold)
            Text(
                "Portadas de juegos: SteamGridDB. Libros: Open Library y Google Books. Películas: TMDB (este producto usa la API de TMDB, pero TMDB no lo respalda ni lo certifica). Series: TVmaze (CC BY-SA). Recomendaciones: RAWG. Letras: Fraunces e Inter (SIL Open Font License).",
                color = UiSoft, fontSize = 13.sp
            )
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Importar datos?", fontFamily = Display) },
            text = { Text("Se sustituirán todos tus datos actuales por los del archivo que elijas. Podrás deshacerlo justo después.") },
            confirmButton = { TextButton({ confirm = false; onImport() }) { Text("Elegir archivo") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancelar") } },
            shape = shp(24.dp)
        )
    }
}

@Composable
fun ColorRow(label: String, value: Int, onPick: (Int) -> Unit) {
    Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Swatches.forEach { (_, c) ->
            val on = c == value
            Box(
                Modifier.pop { onPick(c) }.size(36.dp).background(Color(c), CircleShape)
                    .border(if (on) 3.dp else 0.dp, if (on) Charcoal else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (on) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SearchScreen(
    needsKey: Boolean, key: String, owned: (Long) -> String?,
    search: suspend (String) -> List<Hit>, onPick: (Hit) -> Unit, onBack: () -> Unit,
    title: String = if (needsKey) "Videojuegos" else "Libros",
    hint: String = if (needsKey) "Título del juego" else "Título o autor",
    keyMsg: String = "Antes de buscar, añade tu API key en Ajustes.",
    /** Si no aparece, añadirlo a mano (con lo que has escrito como nombre). */
    onManual: ((String) -> Unit)? = null,
    manualText: String = "¿No aparece? Añádelo a mano con una foto",
    /** Libros: un botón para escanear el código de barras de la contracubierta (el ISBN). */
    escanear: Boolean = false
) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    // Pulsar "Buscar" en el teclado fuerza la búsqueda sin esperar.
    var now by remember { mutableStateOf(0) }
    var lastNow by remember { mutableStateOf(0) }
    // Busca mientras escribes, tras una pausa breve. Al cambiar el texto se cancela la búsqueda anterior,
    // así una respuesta vieja nunca pisa a la nueva.
    LaunchedEffect(q, now) {
        val forced = now != lastNow
        lastNow = now
        val term = q.trim()
        if (term.isEmpty() || (needsKey && key.isBlank())) return@LaunchedEffect
        if (!forced) {
            if (term.length < 3) return@LaunchedEffect
            kotlinx.coroutines.delay(550)
        }
        try {
            msg = "Buscando…"
            val r = search(term)
            results = r
            msg = if (r.isEmpty()) "Sin resultados" else ""
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { msg = e.message ?: "Error de conexión" }
    }
    Column(Modifier.fillMaxSize().imePadding().wrapContentWidth().widthIn(max = 760.dp).statusBarsPadding().padding(16.dp)) {
        if (Skin.revista) TopBar(title, onBack, "Añadir")
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Column {
                Overline(title)
                Text("Añadir", fontFamily = Display, fontSize = 26.sp, lineHeight = 30.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (needsKey && key.isBlank()) {
            Text(keyMsg, color = UiSoft, modifier = Modifier.pill())
            Spacer(Modifier.height(8.dp))
        }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchPill(q, hint, Modifier.weight(1f), onSearch = { now++ }) { q = it }
            if (escanear) {
                Spacer(Modifier.width(8.dp))
                IconButton({
                    EscanerIsbn.abre(ctx, onIsbn = { isbn -> q = isbn; now++ }, onOtro = { msg = it })
                }) { Icon(Icons.Rounded.QrCodeScanner, "Escanear el código de barras", tint = UiSoft) }
            }
        }
        if (onManual != null && q.isBlank()) {
            TextButton({ onManual("") }) { Text(manualText, color = UiSoft, fontSize = 13.sp) }
        }
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(msg, color = UiSoft, modifier = Modifier.pill())
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            // Lo primero: añadirlo tal cual lo has escrito (si no es ninguno de los resultados)
            if (onManual != null && q.isNotBlank()) item {
                Row(
                    Modifier.fillMaxWidth().pop { onManual(q.trim()) }.padding(vertical = 14.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, null, tint = ColGeneral, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Añadir «${q.trim()}» tal cual", fontWeight = FontWeight.Medium, color = ColGeneral)
                        Text("A mano, con portada y fondo de tus archivos", color = UiSoft, fontSize = 12.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            }
            itemsIndexed(results) { i, h ->
                val have = owned(h.id)
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Row(
                    Modifier.fillMaxWidth()
                        .pop { onPick(h) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (h.cover.isNotEmpty()) {
                        MiniCover(h.cover, 40.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(h.name, fontWeight = FontWeight.Medium)
                        if (h.sub.isNotEmpty()) Text(h.sub, color = UiSoft, fontSize = 12.sp)
                    }
                    if (have != null) {
                        Text(have, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Muted,
                            modifier = Modifier.border(1.dp, Line, pillShape()).padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ArtScreen(
    game: Game, hero: Boolean, load: suspend () -> List<Pair<String, String>>, onPick: (String) -> Unit, onSkip: () -> Unit,
    onBack: () -> Unit,
    /** Libros: buscar portadas con otro título (p. ej. el original en inglés). */
    search: (suspend (String) -> List<Pair<String, String>>)? = null,
    /** Pasa el título al inglés: (título, de dónde sale). */
    translate: (suspend (String) -> Pair<String, String>?)? = null,
    /** Tiene fondo (juegos, películas, series): pestaña «Fondos» y, en «Archivo», elegir para qué es. */
    hasHero: Boolean = false,
    /** Cambiar a la pestaña de portadas (false) o de fondos (true). */
    onTab: ((Boolean) -> Unit)? = null,
    /** Una imagen del móvil: para el fondo o la portada; de tus archivos (true) o de la galería (false). */
    onLocal: ((hero: Boolean, files: Boolean) -> Unit)? = null,
    /** Empezar en «Archivo» (lo creado a mano no tiene imágenes en internet). */
    startLocal: Boolean = false,
    /** Una foto de otro dispositivo tuyo ya descargada aquí: para el fondo o la portada, con su dirección. */
    onArrived: ((hero: Boolean, url: String) -> Unit)? = null,
    /** Hay fondos en internet para elegir (pestaña «Fondos»); si no, el fondo solo sale de «Archivo». */
    fondosWeb: Boolean = true
) {
    var list by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var msg by remember { mutableStateOf("Cargando…") }
    // Pestañas: 0 portadas, 1 fondos, 2 archivo (la última)
    var tab by remember { mutableStateOf(if (startLocal && onLocal != null) 2 else if (hero) 1 else 0) }
    var localHero by remember { mutableStateOf(hero) }
    // Al pasar de la portada al fondo (al añadir algo nuevo), «Archivo» elige ya para el fondo
    LaunchedEffect(hero) { localHero = hero }
    var query by remember { mutableStateOf(game.name) }
    var searched by remember { mutableStateOf("") }
    var translating by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(game.id, hero) {
        list = emptyList()
        msg = "Cargando…"
        try {
            var l = load()
            if (!hero && game.cover.isNotBlank() && l.none { it.second == game.cover }) {
                l = listOf(game.cover to game.cover) + l
            }
            list = l
            msg = if (l.isEmpty()) {
                if (search != null) "No hay portadas con este título. Prueba con el título original."
                else if (onLocal != null) "No hay imágenes en internet: elige una en «Archivo»." else "No hay imágenes disponibles."
            } else ""
        } catch (e: Exception) { msg = e.message ?: "Error de conexión" }
    }
    fun runSearch() {
        val q = query.trim()
        if (search == null || q.isEmpty()) return
        focus.clearFocus()
        searched = q
        msg = "Buscando «$q»…"
        scope.launch {
            try {
                val l = search(q)
                // La portada que ya tiene se queda la primera, por si no quieres cambiarla.
                list = (if (game.cover.isNotBlank()) listOf(game.cover to game.cover) else emptyList()) +
                    l.filter { it.second != game.cover }
                msg = if (l.isEmpty()) "No hay portadas para «$q». Prueba con otro título." else ""
            } catch (e: Exception) { msg = e.message ?: "Error de conexión" }
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackButton(onBack)
            if (hero) Text("Omitir fondo", color = Charcoal, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                modifier = Modifier.pop(onSkip).border(1.dp, Line, pillShape()).padding(horizontal = 16.dp, vertical = 9.dp))
        }
        Spacer(Modifier.height(8.dp))
        Overline(game.name, Modifier.padding(start = 4.dp, top = 4.dp))
        Text(if (tab == 2) "Una imagen tuya" else if (hero) "Elige el fondo" else "Elige la portada",
            fontFamily = Display, fontSize = 26.sp, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(8.dp))
        // Pestañas: Portadas · Fondos · Archivo
        val tabs = buildList {
            add(0 to "Portadas")
            if (hasHero && fondosWeb) add(1 to "Fondos")
            if (onLocal != null) add(2 to "Archivo")
        }
        if (tabs.size > 1) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                tabs.forEach { (t, label) ->
                    val on = tab == t
                    Column(Modifier.clickable {
                        if (t == 2) { localHero = hero; tab = 2 }
                        else { tab = t; if ((t == 1) != hero) onTab?.invoke(t == 1) }
                    }.padding(vertical = 8.dp)) {
                        Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp,
                            color = if (on) Charcoal else Muted)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.width(if (on) 26.dp else 0.dp).height(2.dp).background(ColGeneral))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Spacer(Modifier.height(10.dp))
        }
        if (tab == 2 && onLocal != null) {
            LocalArt(game, hasHero, localHero, { localHero = it }, onArrived = onArrived?.let { cb -> { url: String -> cb(localHero, url) } }) { files -> onLocal(localHero, files) }
            return@Column
        }
        if (search != null && !hero) {
            // Solo cambia lo que se busca: el libro conserva su nombre.
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(),
                label = { Text("Buscar portada por título") }, singleLine = true, shape = shp(14.dp),
                placeholder = { Text("Prueba con el título original", color = UiSoft) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { runSearch() }),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ runSearch() }) { Icon(Icons.Rounded.Search, "Buscar") }
                        if (translate != null) {
                            // Al inglés: busca el título original y, con él, las portadas.
                            IconButton(
                                {
                                    val q = query.trim()
                                    if (q.isNotEmpty() && !translating) {
                                        translating = true
                                        focus.clearFocus()
                                        msg = "Pasando «$q» al inglés…"
                                        scope.launch {
                                            val r = try { translate(q) } catch (e: Exception) { null }
                                            translating = false
                                            if (r == null) {
                                                msg = "No he podido pasarlo al inglés. Escribe el título original a mano."
                                            } else {
                                                query = r.first
                                                from = "«$q» → «${r.first}» (${r.second})."
                                                runSearch()
                                            }
                                        }
                                    }
                                },
                                Modifier.padding(end = 4.dp)
                            ) {
                                if (translating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.Translate, "Traducir al inglés")
                            }
                        }
                    }
                }
            )
            if (from.isNotEmpty()) Text(from, color = UiSoft, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            if (searched.isNotEmpty()) Text(
                "Resultados de «$searched». El libro sigue llamándose «${game.name}».",
                color = UiSoft, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
            Spacer(Modifier.height(10.dp))
        }
        if (msg.isNotEmpty()) Text(msg, color = UiSoft, modifier = Modifier.pill())
        LazyVerticalGrid(GridCells.Adaptive(if (hero) 320.dp else 110.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
            itemsIndexed(list) { i, c ->
                AsyncImage(c.first, null,
                    Modifier.appear(150 + (i % 12) * 40, i < 12).padding(4.dp).fillMaxWidth().aspectRatio(if (hero) 3.1f else 2f / 3f)
                        .clip(shp(12.dp)).background(Sand).clickable { onPick(c.second) },
                    contentScale = ContentScale.Crop)
            }
        }
    }
}

/** La pestaña «Archivo»: una imagen del móvil (de tus archivos o de la galería) como portada o como fondo. */
@Composable
private fun LocalArt(game: Game, hasHero: Boolean, hero: Boolean, onTarget: (Boolean) -> Unit, onArrived: ((String) -> Unit)? = null, onPick: (files: Boolean) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var bajando by remember { mutableStateOf<String?>(null) }
    var nota by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
        if (hasHero) {
            Text("PARA", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftChip("La portada", !hero) { onTarget(false) }
                SoftChip("El fondo", hero) { onTarget(true) }
            }
            Spacer(Modifier.height(16.dp))
        }
        // Lo que tiene ahora
        val now = if (hero) game.hero else game.cover
        val shape = shp(12.dp)
        Box(
            Modifier.then(if (hero) Modifier.fillMaxWidth().aspectRatio(3.1f) else Modifier.width(150.dp).aspectRatio(2f / 3f))
                .clip(shape).background(Sand).border(1.dp, Line, shape),
            contentAlignment = Alignment.Center
        ) {
            if (now.isNotBlank()) AsyncImage(now, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(if (hero) "Sin fondo" else "Sin portada", color = Muted, fontSize = 13.sp)
        }
        Text(if (now.isNotBlank()) "Ahora" else "", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(14.dp))
        // El fondo puede ser la propia portada (si no, se pone otra imagen aparte)
        if (hero && onArrived != null && game.cover.isNotBlank() && game.hero != game.cover) {
            Button({ onArrived(game.cover) }, shape = pillShape(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("Usar la portada como fondo")
            }
            Spacer(Modifier.height(8.dp))
        }
        Button({ onPick(true) }, shape = pillShape(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("Elegir de mis archivos")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton({ onPick(false) }, shape = pillShape(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("Desde la galería")
        }
        // De internet: Google Imágenes dentro de la app
        val web = Internet.pick
        if (web != null && onArrived != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ web(game.name + if (hero) " fondo" else "") { url -> onArrived(url) } }, shape = pillShape(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("Buscar en internet")
            }
        }
        // Una imagen de tu kDrive (la que quieras, de cualquier carpeta)
        val nube = NubeHub.pickFromCloud
        if (nube != null && onArrived != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ nube(game.name) { url -> onArrived(url) } }, shape = pillShape(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("De tu nube")
            }
        }
        // La foto que tiene es de otro dispositivo tuyo (la elegiste en el móvil): se puede descargar aquí, a máxima calidad
        if (onArrived != null && Photos.isLocal(now) && !Photos.here(ctx, now)) {
            Spacer(Modifier.height(16.dp))
            Text("ESTÁ EN OTRO DISPOSITIVO TUYO", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(6.dp))
            val otros = Suite.phones()
            if (otros.isEmpty()) Text("Abre Atrio en el móvil donde elegiste esta foto y podrás descargarla aquí, en su calidad original.", color = Muted, fontSize = 13.sp)
            otros.forEach { d ->
                OutlinedButton({
                    if (bajando != null) return@OutlinedButton
                    bajando = d.dev; nota = "Pidiéndosela a ${d.name}…"
                    scope.launch {
                        val (ok, msg) = Suite.fetchPhoto(d.dev, now)
                        bajando = null; nota = msg
                        if (ok) onArrived(now)
                    }
                }, shape = pillShape(), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), enabled = bajando == null) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (bajando == d.dev) "Descargando de ${d.name}…" else "Descargar de ${d.name}")
                }
            }
            if (nota.isNotBlank()) Text(nota, color = UiSoft, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            (if (hero) "Se recorta a lo ancho, como los fondos. " else "Se recorta en vertical, como las portadas. ") +
                "La imagen se guarda en este dispositivo y no se sube a internet: tu pareja no la verá. Tus otros dispositivos (el PC, la tableta) pueden descargarla mientras tengas Atrio abierto aquí.",
            color = Muted, fontSize = 12.sp
        )
    }
}

/** Cómo va una importación, justo debajo del botón que la empezó. */
@Composable
private fun ImportNote(msg: String) =
    Text(msg, color = UiSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))

/** Una explicación larga, plegada: «Cómo funciona ›» y, al tocarlo, el texto. Así Ajustes se lee de un vistazo. */
@Composable
fun Plegado(texto: String, titulo: String = "Cómo funciona") {
    var abierto by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            (if (abierto) "$titulo ⌃" else "$titulo ›").uppercase(), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
            modifier = Modifier.clickable { abierto = !abierto }.padding(vertical = 8.dp)
        )
        androidx.compose.animation.AnimatedVisibility(abierto) {
            Text(texto, color = UiSoft, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

/** El escáner de códigos de Google Play services: abre la cámara él solo (sin pedir permiso) y devuelve el ISBN. */
object EscanerIsbn {
    fun abre(ctx: android.content.Context, onIsbn: (String) -> Unit, onOtro: (String) -> Unit) {
        val opciones = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13)
            .enableAutoZoom()
            .build()
        try {
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx, opciones).startScan()
                .addOnSuccessListener { b ->
                    val isbn = Isbn.limpia(b.rawValue ?: "")
                    if (isbn != null) onIsbn(isbn)
                    else onOtro("Ese código no es un ISBN: busca el que empieza por 978 o 979")
                }
                .addOnFailureListener { onOtro("No se pudo abrir el escáner: ${it.message ?: "sin Google Play services"}") }
        } catch (e: Exception) {
            onOtro("No se pudo abrir el escáner")
        }
    }
}
