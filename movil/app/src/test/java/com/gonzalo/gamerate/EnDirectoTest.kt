package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket

/**
 * La escucha en directo de tu cuenta (Firebase REST en streaming): con un servidor que hace como Firebase
 * (primero un 307 a otro sitio, luego «put», «keep-alive», «patch» y al final «auth_revoked»).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EnDirectoTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun escuchaYAvisa() {
        val sock = ServerSocket(0)
        val port = sock.localPort
        val pedidos = mutableListOf<String>()
        Thread {
            while (true) {
                val c = runCatching { sock.accept() }.getOrNull() ?: break
                val req = c.getInputStream().bufferedReader().let { r -> generateSequence { r.readLine() }.takeWhile { it.isNotEmpty() }.toList() }
                synchronized(pedidos) { pedidos += req.first() + " | " + req.firstOrNull { it.startsWith("Accept:", true) } }
                val out = c.getOutputStream()
                if (req.first().startsWith("GET /users/u1/stamps.json")) {
                    out.write("HTTP/1.1 307 Temporary Redirect\r\nLocation: http://127.0.0.1:$port/otro/users/u1/stamps.json?auth=t\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                } else {
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n" +
                        "event: put\ndata: {\"path\":\"/\",\"data\":{\"movil\":1}}\n\n" +
                        "event: keep-alive\ndata: null\n\n" +
                        "event: patch\ndata: {\"path\":\"/\",\"data\":{\"tablet\":2}}\n\n" +
                        "event: auth_revoked\ndata: \"credential is no longer valid\"\n\n").toByteArray())
                }
                out.flush(); c.shutdownOutput(); Thread.sleep(50); c.close()
            }
        }.apply { isDaemon = true }.start()
        val s = Store(ctx)
        s.dbUrl = "http://127.0.0.1:$port"
        s.accUid = "u1"
        s.accToken = "t"; s.accExp = System.currentTimeMillis() + 3_600_000
        var avisos = 0
        val why = Account.listen(s, false) { avisos++ }
        sock.close()
        assertEquals("auth_revoked", why)
        assertEquals("un aviso por cada «put» o «patch» (no por el keep-alive)", 2, avisos)
        assertTrue("pide el stream con su cabecera y sigue el 307: $pedidos", pedidos.size >= 2 && pedidos.all { it.contains("text/event-stream") } && pedidos.last().contains("/otro/"))
    }
}
