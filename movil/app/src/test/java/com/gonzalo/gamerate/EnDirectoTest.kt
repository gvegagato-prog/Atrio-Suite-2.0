package com.gonzalo.gamerate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * La escucha en directo de tu cuenta (Firebase REST en streaming, la de [Sincro]): con un servidor que hace como Firebase
 * (primero un 307 a otro sitio, luego «put», «keep-alive», «patch» y al final «auth_revoked», tras el que se vuelve a conectar).
 */
class EnDirectoTest {
    @Test fun escuchaSigueEl307YSeReconecta() {
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
        val avisos = mutableListOf<String>()
        val dos = CountDownLatch(3)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val f = FbStream("prueba", { "http://127.0.0.1:$port/users/u1/stamps.json?auth=t" }, { t, path ->
            synchronized(avisos) { avisos += "$path=${t?.keys()?.asSequence()?.sorted()?.joinToString()}" }
            dos.countDown()
        })
        f.start(scope)
        // put + patch de la primera conexión y, tras «auth_revoked», el put de la segunda
        assertTrue("llegan los cambios y se reconecta: $avisos", dos.await(10, TimeUnit.SECONDS))
        f.stop(); scope.cancel(); sock.close()
        assertTrue("el patch se junta con lo que había: $avisos", avisos[1] == "/=movil, tablet")
        assertTrue("pide el stream con su cabecera y sigue el 307: $pedidos", pedidos.size >= 2 && pedidos.all { it.contains("text/event-stream") } && pedidos.any { it.contains("/otro/") })
    }
}
