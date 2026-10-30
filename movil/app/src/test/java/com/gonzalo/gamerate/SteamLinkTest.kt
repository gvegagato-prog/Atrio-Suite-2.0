package com.gonzalo.gamerate

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Streaming con Steam Link: lo que publica el PC, detectar y abrir Steam Link, y qué se enseña si falta. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SteamLinkTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun shoot(n: String) {
        rule.waitForIdle()
        // (toda la pantalla: así sale también el diálogo, que va en su propia ventana)
        if (System.getProperty("roborazzi.test.record") == "true") com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/steamlink_$n.png")
    }

    private fun instalaSteamLink() {
        val comp = ComponentName(Stream.STEAM_LINK, "com.valvesoftware.steamlink.SteamShellActivity")
        val pm = shadowOf(ctx.packageManager)
        pm.addActivityIfNotPresent(comp)
        pm.addIntentFilterForActivity(comp, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    @Test fun loQuePublicaElPc() {
        val h = StreamHost.from(JSONObject("""{"on":true,"run":false,"name":"PC-salon","lan":["192.168.1.20"],"mac":["aa:bb:cc:dd:ee:ff"],"ok":true,"host":"steamlink"}"""))
        assertTrue(h.on); assertFalse(h.running)
        assertEquals("PC-salon", h.name)
        assertEquals(listOf("192.168.1.20"), h.lan)
        assertEquals(listOf("aa:bb:cc:dd:ee:ff"), h.macs)
    }

    @Test fun abreSteamLinkSiEstaInstalado() {
        assertFalse(Stream.installed(ctx))
        assertFalse(Stream.open(ctx))
        instalaSteamLink()
        assertTrue(Stream.installed(ctx))
        assertTrue(Stream.open(ctx))
        val abierta = shadowOf(ctx as Application).nextStartedActivity
        assertEquals(Stream.STEAM_LINK, abierta.component?.packageName)
    }

    @Test fun sinSteamLinkOfreceInstalarlo() {
        Mode.dark = false
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    Column(Modifier.padding(16.dp)) {
                        PlayButtons("custom:prueba", "Juego de Prueba", Modifier.padding(bottom = 24.dp))
                        StreamPanel()
                    }
                }
            }
        }
        rule.onAllNodesWithText("Steam Link no está instalado").assertCountEquals1()
        rule.onAllNodesWithText("En este móvil, con Steam Link", ignoreCase = true).assertCountEquals1()
        shoot("1_panel")
        rule.onAllNodesWithText("Streaming →")[0].performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Falta Steam Link").assertCountEquals1()
        shoot("2_falta")
        rule.onAllNodesWithText("Instalar Steam Link")[0].performClick()
        rule.waitForIdle()
        val tienda = shadowOf(ctx as Application).nextStartedActivity
        assertEquals("market://details?id=${Stream.STEAM_LINK}", tienda.dataString)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals1() {
    val n = fetchSemanticsNodes().size
    assertTrue("se esperaba 1 y hay $n", n == 1)
}
