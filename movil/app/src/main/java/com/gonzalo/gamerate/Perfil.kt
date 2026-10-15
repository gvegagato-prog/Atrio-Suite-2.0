package com.gonzalo.gamerate

import androidx.compose.foundation.background
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
                    Text("Estilo", fontWeight = FontWeight.Bold, fontSize = if (Skin.revista) 24.sp else 17.sp, fontFamily = if (Skin.revista) Display else null)
                    Text(skinSummary(), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!Skin.revista) Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    val t = Skin.tones
                    listOf(t.general, t.gfx, t.story, t.music).forEach { c -> Box(Modifier.size(18.dp).clip(CircleShape).background(Color(c))) }
                }
            }
            Text("Colores, letras, formas, notas, movimiento, fondo e icono de la app.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))

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
        }
        Spacer(Modifier.height(12.dp))

        // Cuenta y pareja
        Card6(Modifier.rise(4)) {
            Text("Cuenta y pareja", fontWeight = FontWeight.Bold, fontSize = if (Skin.revista) 24.sp else 17.sp, fontFamily = if (Skin.revista) Display else null)
            Spacer(Modifier.height(8.dp))
            couple()
        }
        Spacer(Modifier.height(12.dp))

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
