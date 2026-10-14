package com.gonzalo.gamerate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/*
 * Enchufe inteligente de Tuya (Smart Life / Tuya Smart) para encender el PC a distancia, también fuera de casa.
 * El PC tiene que tener en la BIOS «Restore on AC power loss» (o «AC Back», «Power On») en «Power On»: al volver
 * la corriente, arranca solo. Encender = cortar la corriente unos segundos y volver a darla.
 *
 * Va por la API en la nube de Tuya (developer.tuya.com): un proyecto de «Cloud» con tu cuenta de la app enlazada,
 * su Access ID y Access Secret, y el Device ID del enchufe. Todo se guarda solo en este móvil.
 * Firma de cada petición: HMAC-SHA256(client_id [+ access_token] + t + nonce + stringToSign, secret) en mayúsculas,
 * con stringToSign = método \n SHA256(cuerpo) \n \n ruta.
 */
object Tuya {
    /** Centros de datos de Tuya: el de tu proyecto (no el de tu país). */
    val REGIONS = listOf(
        "eu" to ("Europa" to "openapi.tuyaeu.com"),
        "us" to ("América" to "openapi.tuyaus.com"),
        "cn" to ("China" to "openapi.tuyacn.com"),
        "in" to ("India" to "openapi.tuyain.com"),
    )

    data class Cfg(val region: String, val host: String, val id: String, val secret: String, val device: String, val waitSec: Int) {
        val ready get() = id.isNotBlank() && secret.isNotBlank() && device.isNotBlank() && host.isNotBlank()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("tuya", Context.MODE_PRIVATE)

    fun load(ctx: Context): Cfg {
        val p = prefs(ctx)
        val region = p.getString("region", "eu") ?: "eu"
        val host = p.getString("host", "")?.ifBlank { null } ?: REGIONS.firstOrNull { it.first == region }?.second?.second ?: "openapi.tuyaeu.com"
        return Cfg(region, host, p.getString("id", "") ?: "", p.getString("secret", "") ?: "", p.getString("device", "") ?: "", p.getInt("wait", 10))
    }

    fun save(ctx: Context, c: Cfg) {
        prefs(ctx).edit().putString("region", c.region).putString("host", c.host.trim().removePrefix("https://").trimEnd('/'))
            .putString("id", c.id.trim()).putString("secret", c.secret.trim()).putString("device", c.device.trim())
            .putInt("wait", c.waitSec.coerceIn(3, 60)).apply()
        token = null
    }

    fun configured(ctx: Context) = load(ctx).ready

    private var token: Pair<String, Long>? = null // token y cuándo caduca

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun hmac(s: String, key: String): String {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return m.doFinal(s.toByteArray()).joinToString("") { "%02X".format(it) }
    }

    /** Una petición firmada. [path] incluye la consulta, con sus parámetros ya en orden alfabético. */
    private fun call(c: Cfg, method: String, path: String, body: String?, withToken: Boolean): JSONObject {
        val t = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val tok = if (withToken) token!!.first else ""
        val stringToSign = "$method\n${sha256(body ?: "")}\n\n$path"
        val sign = hmac(c.id + tok + t + nonce + stringToSign, c.secret)
        val conn = URL("https://${c.host}$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000; conn.readTimeout = 15_000
        conn.setRequestProperty("client_id", c.id)
        conn.setRequestProperty("sign", sign)
        conn.setRequestProperty("t", t)
        conn.setRequestProperty("nonce", nonce)
        conn.setRequestProperty("sign_method", "HMAC-SHA256")
        if (withToken) conn.setRequestProperty("access_token", tok)
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val txt = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
        return try { JSONObject(txt) } catch (e: Exception) { JSONObject().put("success", false).put("msg", "Respuesta rara de Tuya (${conn.responseCode})") }
    }

    private fun fail(j: JSONObject): Nothing {
        val code = j.optInt("code")
        // Códigos que Tuya documenta (tuya-home-assistant/docs/error_code.md) o comprobados contra la API; el resto, con su mensaje
        val msg = when (code) {
            1004 -> "Tuya no acepta la firma: revisa el Access ID y el Access Secret."
            1010 -> "La sesión con Tuya ha caducado; vuelve a probar."
            2009 -> "Tuya no reconoce el Access ID (revisa también que el centro de datos sea el de tu proyecto)."
            1106 -> "Tu proyecto de Tuya no tiene permiso sobre ese enchufe: enlaza tu cuenta de la app en el proyecto (Devices → Link App Account)."
            else -> j.optString("msg").ifBlank { "Tuya ha dicho que no" } + " (código $code)"
        }
        throw IllegalStateException(msg)
    }

    private fun ensureToken(c: Cfg) {
        val tk = token
        if (tk != null && System.currentTimeMillis() < tk.second) return
        val j = call(c, "GET", "/v1.0/token?grant_type=1", null, false)
        if (!j.optBoolean("success")) fail(j)
        val r = j.getJSONObject("result")
        token = r.getString("access_token") to (System.currentTimeMillis() + (r.optLong("expire_time", 3600) - 60) * 1000)
    }

    /** Petición con token; si el token ha caducado, se pide otro y se repite una vez. */
    private fun authed(c: Cfg, method: String, path: String, body: String?): JSONObject {
        ensureToken(c)
        var j = call(c, method, path, body, true)
        if (!j.optBoolean("success") && j.optInt("code") == 1010) {
            token = null; ensureToken(c); j = call(c, method, path, body, true)
        }
        return j
    }

    /** Las dos rutas de la API de dispositivos (la nueva y la de siempre): se usa la que conteste. */
    private fun device(c: Cfg, method: String, tail: String, body: String?): JSONObject {
        val dev = java.net.URLEncoder.encode(c.device, "UTF-8")
        val a = authed(c, method, "/v1.0/iot-03/devices/$dev/$tail", body)
        if (a.optBoolean("success")) return a
        val b = authed(c, method, "/v1.0/devices/$dev/$tail", body)
        if (b.optBoolean("success")) return b
        fail(a)
    }

    /** Código del interruptor del enchufe (switch_1, switch…) y si está encendido. */
    private fun switchState(c: Cfg): Pair<String, Boolean> {
        val r = device(c, "GET", "status", null).optJSONArray("result") ?: JSONArray()
        var code: String? = null; var on = false
        for (i in 0 until r.length()) {
            val o = r.optJSONObject(i) ?: continue
            val k = o.optString("code")
            if ((k == "switch_1" || k == "switch" || k.startsWith("switch")) && o.opt("value") is Boolean) {
                if (code == null || k == "switch_1") { code = k; on = o.optBoolean("value") }
            }
        }
        return (code ?: "switch_1") to on
    }

    /** ¿Está dando corriente? null si no se puede saber (el mensaje va en el error). */
    suspend fun isOn(ctx: Context): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching { switchState(load(ctx)).second }
    }

    suspend fun set(ctx: Context, on: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val c = load(ctx)
            val code = switchState(c).first
            val body = JSONObject().put("commands", JSONArray().put(JSONObject().put("code", code).put("value", on))).toString()
            device(c, "POST", "commands", body)
            Unit
        }
    }

    /**
     * Encender el PC: corta la corriente, espera y la vuelve a dar (con la BIOS en «Power On», el PC arranca).
     * Si el enchufe ya estaba apagado, solo lo enciende. [progress] cuenta lo que va haciendo.
     */
    suspend fun powerCycle(ctx: Context, progress: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val c = load(ctx)
            val (code, on) = switchState(c)
            fun cmd(v: Boolean) = device(c, "POST", "commands",
                JSONObject().put("commands", JSONArray().put(JSONObject().put("code", code).put("value", v))).toString())
            if (on) {
                withContext(Dispatchers.Main) { progress("Cortando la corriente…") }
                cmd(false)
                // Tiempo para que la fuente se descargue y la placa note que se ha ido la corriente
                for (s in c.waitSec downTo 1) { withContext(Dispatchers.Main) { progress("Sin corriente · $s s") }; delay(1000) }
            }
            withContext(Dispatchers.Main) { progress("Dando corriente…") }
            cmd(true)
            Unit
        }
    }
}
