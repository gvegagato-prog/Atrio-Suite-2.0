package com.gonzalo.gamerate

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/*
 * Perfil (Atrio 6): lo que antes estaba repartido entre el menú y Ajustes, ordenado en pocas tarjetas:
 * estilo, tu PC, objetivos, cuenta y pareja, y el resto de ajustes (claves, importar, copias) en una página aparte.
 */

@Composable
fun ProfileScreen(
    store: Store, partner: Partner?,
    onStyle: () -> Unit, onSettings: () -> Unit, onSuite: () -> Unit,
    onMassScores: () -> Unit,
    onDuelos: () -> Unit = {},
    /** Pedir permiso para los avisos (Android 13+). */
    onPedirAvisos: () -> Unit = {},
    onNegraAnadir: (String) -> Unit = {},
    onNegraQuitar: (Vetado) -> Unit = {},
    goalGames: Int, goalBooks: Int, onGoal: (Int, Int) -> Unit,
    couple: @Composable () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 18.dp).padding(bottom = 24.dp)
    ) {
        if (Skin.revista) TopBar("Atrio", onBack, "Perfil") else BigHeader("Perfil") { BackRound(onBack = onBack) }

        // Quién eres
        Card6(Modifier.rise(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val name = store.myName.ifBlank { "Tú" }
                // Revista: la inicial como capitular roja, sin círculo
                if (Skin.revista) Text(name.take(1).uppercase(), color = ColGeneral, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 64.sp)
                else Box(Modifier.size(58.dp).clip(CircleShape).background(ColGeneral), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), color = Paper, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    FitTitle(name, if (Skin.revista) 30.sp else 22.sp, if (Skin.revista) 32.sp else 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            !store.paired -> "Sin cuenta: tus datos solo están en este móvil"
                            partner != null -> "En pareja con ${partner.name}"
                            else -> "Con cuenta · sincronizado"
                        }, color = Muted, fontSize = 13.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Estilo
        Card6(Modifier.rise(1), onClick = onStyle) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!Skin.revista) { Icon(Icons.Rounded.Palette, null, tint = ColGeneral, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("Apariencia", fontWeight = FontWeight.Bold, fontSize = if (Skin.revista) 24.sp else 17.sp, fontFamily = if (Skin.revista) Display else null)
                    Text(skinSummary(), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!Skin.revista) Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    val t = Skin.tones
                    listOf(t.general, t.gfx, t.story, t.music).forEach { c -> Box(Modifier.size(18.dp).clip(CircleShape).background(Color(c))) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Las listas con portada (como las sagas) o solo con títulos
        if (Skin.revista) {
            Card6(Modifier.rise(1), onClick = { Skin.listCovers = !Skin.listCovers; Skin.save(ctx) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Portadas en las listas", fontWeight = FontWeight.Bold, fontSize = 24.sp, fontFamily = Display)
                        Text(if (Skin.listCovers) "Cada título con su portada, como las sagas" else "Solo los títulos", color = Muted, fontSize = 13.sp)
                    }
                    androidx.compose.material3.Switch(Skin.listCovers, { Skin.listCovers = it; Skin.save(ctx) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Tu PC
        val pc = Suite.target()
        ProfileRow(Icons.Rounded.DesktopWindows, "Tu PC", when {
            !store.paired -> "Entra en tu cuenta para conectarlo"
            pc != null -> "${pc.name} · conectado"
            else -> "Desconectado · streaming, encendido y capturas"
        }, Modifier.rise(2), onSuite)
        Spacer(Modifier.height(12.dp))

        // Objetivos
        Card6(Modifier.rise(3)) {
            Text("Objetivos de ${LocalDate.now().year}", fontWeight = FontWeight.Bold, fontSize = if (Skin.revista) 24.sp else 17.sp, fontFamily = if (Skin.revista) Display else null)
            Text("Cuántos quieres terminar este año. 0 = sin objetivo.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(GAMES.title, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Stepper(goalGames, "", { onGoal(0, goalGames - 1) }, { onGoal(0, goalGames + 1) })
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(BOOKS.title, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Stepper(goalBooks, "", { onGoal(1, goalBooks - 1) }, { onGoal(1, goalBooks + 1) })
            }
            // Lectura al día (con el lector de Atrio): se ve en la portada, en el Diario y en el propio lector
            var meta by remember { mutableStateOf(store.metaLectura) }
            var aviso by remember { mutableStateOf(store.avisoLectura) }
            fun ponMeta(v: Int) { meta = v.coerceIn(0, 600); store.metaLectura = meta; LecturaAviso.programa(ctx) }
            fun ponAviso(h: Int) { aviso = h; store.avisoLectura = h; LecturaAviso.programa(ctx) }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lectura al día", fontWeight = FontWeight.Medium)
                    Text(if (meta > 0) "minutos con el lector" else "sin objetivo", color = Muted, fontSize = 12.sp)
                }
                Stepper(meta, " min", { ponMeta(meta - 5) }, { ponMeta(meta + 5) })
            }
            if (meta > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Avisarme si no lo cumplo", fontWeight = FontWeight.Medium)
                        Text(if (aviso >= 0) "a las ${"%02d".format(aviso)}:00, con el libro por el que vas" else "sin aviso", color = Muted, fontSize = 12.sp)
                    }
                    androidx.compose.material3.Switch(aviso >= 0, { on -> if (on) { onPedirAvisos(); ponAviso(21) } else ponAviso(-1) })
                }
                if (aviso >= 0) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    Stepper(aviso, ":00", { ponAviso((aviso + 23) % 24) }, { ponAviso((aviso + 1) % 24) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Cuenta y pareja
        Card6(Modifier.rise(4)) {
            Text("Cuenta y pareja", fontWeight = FontWeight.Bold, fontSize = if (Skin.revista) 24.sp else 17.sp, fontFamily = if (Skin.revista) Display else null)
            Spacer(Modifier.height(8.dp))
            couple()
        }
        Spacer(Modifier.height(12.dp))

        // Actualizarse sola desde la carpeta «versiones» de tu kDrive
        run {
            val actx = androidx.compose.ui.platform.LocalContext.current
            if (KDrive.on(actx)) {
                var auto by remember { mutableStateOf(Actualizador.activo(actx)) }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Actualizarse sola", fontWeight = FontWeight.Medium)
                        Text("Al abrir, con wifi o datos, busca una versión nueva en «versiones» de tu kDrive", color = Muted, fontSize = 12.sp)
                    }
                    androidx.compose.material3.Switch(auto, { auto = it; Actualizador.ponActivo(actx, it) })
                }
                // Qué pasó la última vez, y comprobar ya (para saber por qué no se actualiza, si no lo hace)
                val alcance = androidx.compose.runtime.rememberCoroutineScope()
                var mirando by remember { mutableStateOf(false) }
                val vers = remember { runCatching { actx.packageManager.getPackageInfo(actx.packageName, 0).versionName }.getOrDefault("?") }
                Text("Versión instalada: $vers" + Actualizador.estadoGuardado(actx).let { if (it.isBlank()) "" else " · última comprobación: $it" }.let { if (Actualizador.estado.isNotBlank()) " · última comprobación: ${Actualizador.estado}" else it },
                    color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                androidx.compose.material3.TextButton({
                    if (!mirando) { mirando = true; alcance.launch { withContext(kotlinx.coroutines.Dispatchers.IO) { Actualizador.comprueba(actx, manual = true) }; mirando = false } }
                }, enabled = !mirando) { Text(if (mirando) "Comprobando…" else "Comprobar ahora") }
                // Reabrirse sola tras actualizar: Android pide este permiso para abrir una app «desde atrás»
                var sobre by remember { mutableStateOf(Actualizador.puedeReabrirSola(actx)) }
                val vida = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
                androidx.compose.runtime.DisposableEffect(vida) {
                    val ob = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) sobre = Actualizador.puedeReabrirSola(actx) }
                    vida.addObserver(ob); onDispose { vida.removeObserver(ob) }
                }
                Text(if (sobre) "Tras actualizar, Atrio se vuelve a abrir sola" else "Tras actualizar, Atrio se cierra y te avisa para abrirla. Para que se reabra sola hace falta el permiso «Mostrar sobre otras apps»",
                    color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                if (!sobre) androidx.compose.material3.TextButton({
                    runCatching { actx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${actx.packageName}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Dar permiso") }
                Spacer(Modifier.height(8.dp))
            }
        }
        // Lista negra de juegos: no salen en ningún sitio
        var negra by remember { mutableStateOf(false) }
        ProfileRow(Icons.Rounded.Edit, "Lista negra de juegos", if (Negra.lista.isEmpty()) "Juegos que nunca deben salir (p. ej. Wallpaper Engine)" else "${Negra.lista.size} oculto${if (Negra.lista.size == 1) "" else "s"} en todos tus dispositivos", Modifier.rise(5)) { negra = true }
        Spacer(Modifier.height(12.dp))
        if (negra) NegraDialogo(onAnadir = { n -> onNegraAnadir(n) }, onQuitar = { v -> onNegraQuitar(v) }, onCerrar = { negra = false })
        // Repasar notas (sobre todo tras importar): por duelos o una a una
        var repasar by remember { mutableStateOf(false) }
        ProfileRow(Icons.Rounded.Edit, "Repasar notas", "Duelos de libros o cambiar varias notas seguidas (p. ej. tras importar)", Modifier.rise(5)) { repasar = true }
        Spacer(Modifier.height(12.dp))
        if (repasar) androidx.compose.material3.AlertDialog(
            onDismissRequest = { repasar = false },
            title = { Text("Repasar notas", fontFamily = Display) },
            text = {
                Column {
                    Column(Modifier.fillMaxWidth().clickable { repasar = false; onDuelos() }.padding(vertical = 10.dp)) {
                        Text("Duelos de libros", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("«¿Cuál te gustó más?» de dos en dos; al final, notas sin empates", color = Muted, fontSize = 13.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    Column(Modifier.fillMaxWidth().clickable { repasar = false; onMassScores() }.padding(vertical = 10.dp)) {
                        Text("Cambiar notas en masa", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Eliges varios y les cambias la nota uno tras otro", color = Muted, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton({ repasar = false }) { Text("Cancelar") } }
        )

        ProfileRow(Icons.Rounded.Tune, "Ajustes", "Claves de servicios, importar de otras apps, Drive y copias", Modifier.rise(5), onSettings)
        Spacer(Modifier.height(20.dp))
        val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { null } ?: "" }
        Text("Atrio $ver", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(4.dp))
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Card6(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!Skin.revista) { Icon(icon, null, tint = Charcoal, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = if (Skin.revista) 24.sp else 17.sp, fontFamily = if (Skin.revista) Display else null)
                Text(sub, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (Skin.revista) Text("→", fontFamily = Display, fontSize = 24.sp) else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted)
        }
    }
}
