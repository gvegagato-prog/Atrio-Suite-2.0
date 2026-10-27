package com.gonzalo.gamerate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cuentas de Ronrón con usuario y contraseña (Firebase Authentication, por su API REST).
 *
 * En la base de datos:
 *   /config/webKey                    clave web del proyecto (pública), para que los demás dispositivos no tengan que pegarla
 *   /usernames/<usuario>              -> uid (para encontrar a tu pareja por su nombre de usuario)
 *   /users/<uid>/name, user, partner  tu nombre visible, tu usuario y el uid de quien puede ver lo tuyo
 *   /users/<uid>/dev/<dispositivo>    { name, user, updated, data } — cada dispositivo escribe solo en el suyo
 *   /users/<uid>/stamps/<dispositivo> hora de su última subida (para mirar novedades con una consulta mínima)
 *
 * Atrio (el lanzador de Linux) es un dispositivo más de tu cuenta y usa exactamente esto mismo.
 */
object Account {
    const val DEFAULT_DB = "https://app-ratear-default-rtdb.europe-west1.firebasedatabase.app"

    /** Firebase pide un correo: el usuario se convierte en uno que no existe ni recibe nada (example.com está reservado). */
    private const val MAIL = "ronron.example.com"

    /** Lo que lleve más de 150 días sin subir no se mezcla (los borrados se olvidan a los 180). */
    private const val STALE = 150L * 86_400_000L


    /**
     * Reglas de la base de datos para las cuentas. /couples se deja abierto solo mientras alguien siga con una
     * versión antigua (o para recuperar las copias de entonces); cuando los dos tengáis cuenta se puede quitar.
     */
    val RULES = """
{
  "rules": {
    "config": {
      ".read": true,
      "webKey": { ".write": "auth != null && !data.exists()" }
    },
    "usernames": {
      "${'$'}name": {
        ".read": "auth != null",
        ".write": "auth != null && !data.exists() && newData.val() === auth.uid"
      }
    },
    "users": {
      "${'$'}uid": {
        ".read": "auth != null && (auth.uid === ${'$'}uid || data.child('partner').val() === auth.uid)",
        ".write": "auth != null && auth.uid === ${'$'}uid"
      }
    },
    "couples": { ".read": true, ".write": true }
  }
}
""".trim()

    fun normUser(s: String) = s.trim().lowercase()
    fun validUser(u: String) = Regex("^[a-z0-9][a-z0-9._-]{2,23}$").matches(u)
    const val BAD_USER = "El usuario tiene que tener de 3 a 24 letras o números (sin espacios ni tildes; vale . _ -)."
    private fun email(u: String) = "$u@$MAIL"

    class Fail(msg: String) : Exception(msg)

    // ---------------------------------------------------------------- HTTP

    private class Res(val code: Int, val text: String)

    private fun http(method: String, url: String, body: String?, form: Boolean = false): Res {
        val c = URL(url).openConnection() as HttpURLConnection
        try { c.requestMethod = method } catch (e: java.net.ProtocolException) {
            // (el Java de las pruebas en el ordenador no conoce PATCH: Firebase acepta un POST que diga cuál es)
            c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", method)
        }
        c.connectTimeout = 15000
        c.readTimeout = 20000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.readText() ?: ""
        return Res(code, text)
    }

    /** Mensaje entendible a partir del error que da Firebase Authentication. */
    private fun authMsg(text: String): String {
        val raw = try { JSONObject(text).optJSONObject("error")?.optString("message") ?: text } catch (e: Exception) { text }
        if (raw.contains("API key not valid", true) || raw.contains("API_KEY_INVALID")) return "La clave web de Firebase no es válida."
        return when (raw.substringBefore(" ").substringBefore(":").trim()) {
            "EMAIL_EXISTS" -> "Ese nombre de usuario ya está cogido."
            "EMAIL_NOT_FOUND", "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS", "INVALID_EMAIL" -> "Usuario o contraseña incorrectos."
            "WEAK_PASSWORD" -> "La contraseña tiene que tener al menos 6 caracteres."
            "OPERATION_NOT_ALLOWED", "PASSWORD_LOGIN_DISABLED" ->
                "En Firebase no está activado el acceso con correo y contraseña (consola de Firebase → Authentication → Método de acceso)."
            "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Demasiados intentos seguidos. Espera un rato."
            "USER_DISABLED" -> "Esta cuenta está desactivada."
            "TOKEN_EXPIRED", "INVALID_REFRESH_TOKEN", "USER_NOT_FOUND", "INVALID_ID_TOKEN", "CREDENTIAL_TOO_OLD_LOGIN_AGAIN" ->
                "La sesión ha caducado. Vuelve a entrar con tu contraseña."
            "CONFIGURATION_NOT_FOUND" -> "Firebase Authentication no está activado en el proyecto."
            else -> "Firebase: $raw"
        }
    }

    private fun idUrl(store: Store, op: String) =
        store.authBase.ifBlank { "https://identitytoolkit.googleapis.com" } + "/v1/accounts:$op?key=" + store.webKey

    private fun tokenUrl(store: Store) =
        store.authBase.ifBlank { "https://securetoken.googleapis.com" } + "/v1/token?key=" + store.webKey

    // ---------------------------------------------------------------- Sesión

    /** La clave web: la guardada, o la que dejó publicada en la base de datos quien creó la primera cuenta. */
    suspend fun findWebKey(store: Store): String = withContext(Dispatchers.IO) {
        if (store.webKey.isNotBlank()) return@withContext store.webKey
        val r = try { http("GET", store.dbUrl + "/config/webKey.json", null) } catch (e: Exception) { null }
        val k = if (r != null && r.code == 200) (JSONTokener(r.text.trim()).nextValue() as? String ?: "") else ""
        if (k.isNotBlank()) store.webKey = k
        k
    }

    private fun keep(store: Store, user: String, o: JSONObject) {
        store.accUser = user
        store.accUid = o.getString("localId")
        store.accToken = o.getString("idToken")
        store.accRefresh = o.getString("refreshToken")
        store.accExp = System.currentTimeMillis() + (o.optString("expiresIn", "3600").toLongOrNull() ?: 3600L) * 1000L
    }

    /** Crea la cuenta. Devuelve el uid. */
    suspend fun signUp(store: Store, user: String, pass: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email(user)).put("password", pass).put("returnSecureToken", true)
        val r = http("POST", idUrl(store, "signUp"), body.toString())
        if (r.code !in 200..299) throw Fail(authMsg(r.text))
        keep(store, user, JSONObject(r.text))
        // Tu nombre de usuario queda reservado para que tu pareja te encuentre.
        val c = db(store, "PUT", "/usernames/$user", JSONObject.quote(store.accUid), ok404 = false)
        if (c == null) throw Fail("No se ha podido reservar el usuario.")
        // La clave web se deja en la base de datos (solo la primera vez) para que tus otros dispositivos la encuentren solos.
        try { db(store, "PUT", "/config/webKey", JSONObject.quote(store.webKey)) } catch (e: Exception) { /* ya estaba */ }
        Sincro.reinicia(store.contexto)
        store.accUid
    }

    /** Entra en una cuenta que ya existe. Devuelve el uid. */
    suspend fun signIn(store: Store, user: String, pass: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email(user)).put("password", pass).put("returnSecureToken", true)
        val r = http("POST", idUrl(store, "signInWithPassword"), body.toString())
        if (r.code !in 200..299) throw Fail(authMsg(r.text))
        val prev = store.accUid
        keep(store, user, JSONObject(r.text))
        if (prev != store.accUid) store.devSeen = JSONObject()
        // Por si al crearla no se pudo reservar el nombre (p. ej. reglas sin poner todavía).
        try {
            if (db(store, "GET", "/usernames/$user", null) == null) db(store, "PUT", "/usernames/$user", JSONObject.quote(store.accUid))
        } catch (e: Exception) { }
        // Se recupera con quién compartes (si ya lo habías puesto en otro dispositivo).
        try {
            val pu = db(store, "GET", "/users/${store.accUid}/partner", null)?.let { JSONTokener(it).nextValue() as? String } ?: ""
            if (pu.isNotBlank() && pu != store.partnerUid) {
                store.partnerUid = pu
                store.partnerUser = ""
                store.partnerRaw = ""
                store.partnerSeen = ""
            }
        } catch (e: Exception) { }
        Sincro.reinicia(store.contexto)
        store.accUid
    }

    fun signOut(store: Store) {
        store.accUid = ""
        store.accToken = ""
        store.accRefresh = ""
        store.accExp = 0L
        store.devSeen = JSONObject()
        store.partnerUid = ""
        store.partnerUser = ""
        store.partnerRaw = ""
        store.partnerId = ""
        store.partnerSeen = ""
        Sincro.reinicia(store.contexto)
    }

    /** Pase de acceso vigente (se renueva solo; dura una hora). */
    fun token(store: Store, force: Boolean = false): String {
        if (!force && store.accToken.isNotBlank() && System.currentTimeMillis() < store.accExp - 60_000L) return store.accToken
        if (store.accRefresh.isBlank()) throw Fail("No has iniciado sesión.")
        val body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(store.accRefresh, "UTF-8")
        val r = http("POST", tokenUrl(store), body, form = true)
        if (r.code !in 200..299) throw Fail(authMsg(r.text))
        val o = JSONObject(r.text)
        store.accToken = o.getString("id_token")
        store.accRefresh = o.optString("refresh_token", store.accRefresh)
        store.accExp = System.currentTimeMillis() + (o.optString("expires_in", "3600").toLongOrNull() ?: 3600L) * 1000L
        return store.accToken
    }

    suspend fun changePassword(store: Store, pass: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("idToken", token(store)).put("password", pass).put("returnSecureToken", true)
        val r = http("POST", idUrl(store, "update"), body.toString())
        if (r.code !in 200..299) throw Fail(authMsg(r.text))
        val o = JSONObject(r.text)
        if (o.has("idToken")) keep(store, store.accUser, o)
        Unit
    }

    // ---------------------------------------------------------------- Base de datos

    /**
     * Petición a la base de datos con tu sesión. Devuelve el texto, o null si no hay nada (o no tienes permiso
     * y ok404 es true). Si el pase ha caducado, se renueva y se repite una vez.
     */
    fun db(store: Store, method: String, path: String, body: String?, query: String = "", ok404: Boolean = true): String? {
        fun go(force: Boolean): Res {
            val t = token(store, force)
            val sep = if (query.isBlank()) "" else "&$query"
            return http(method, store.dbUrl + path + ".json?auth=" + t + sep, body)
        }
        var r = go(false)
        if (r.code == 401) r = go(true)
        if (r.code == 401 || r.code == 403) {
            if (ok404) return null
            throw Fail("Firebase no deja hacer eso. Revisa las reglas de la base de datos (Ajustes → Cuenta).")
        }
        if (r.code == 404) throw Fail("No encuentro la base de datos. Revisa la dirección.")
        if (r.code !in 200..299) throw Fail("Firebase respondió ${r.code}.")
        if (r.text.trimStart().startsWith("<")) throw Fail("La dirección no es la de la base de datos (devuelve una página web).")
        val t = r.text.trim()
        return if (t.isEmpty() || t == "null") null else t
    }

    private fun me(store: Store) = "/users/" + store.accUid

    /** Horas de subida de cada dispositivo de una cuenta (null si no tienes permiso para verla). */
    fun stamps(store: Store, uid: String): Map<String, Long>? {
        val t = db(store, "GET", "/users/$uid/stamps", null) ?: return if (uid == store.accUid) emptyMap() else null
        val o = JSONObject(t)
        return o.keys().asSequence().associateWith { o.optLong(it, 0L) }
    }

    /**
     * Sube tus datos a tu hueco y comprueba la hora que ha puesto el servidor.
     * Antes se mezcla lo que hayan subido tus otros dispositivos, para que tu hueco tenga siempre lo más completo.
     */
    fun upload(store: Store, data: String): Long {
        val dev = store.accDevice
        val node = JSONObject()
            .put("name", store.myName.ifBlank { store.accUser })
            .put("user", store.accUser)
            .put("device", "android")
            .put("updated", JSONObject().put(".sv", "timestamp"))
            .put("data", data)
        db(store, "PUT", me(store) + "/dev/$dev", node.toString(), "print=silent", ok404 = false)
        val ts = db(store, "GET", me(store) + "/dev/$dev/updated", null)?.toLongOrNull()
            ?: throw Fail("Firebase no ha guardado la copia.")
        val patch = JSONObject().put("stamps/$dev", ts).put("name", store.myName.ifBlank { store.accUser }).put("user", store.accUser)
        db(store, "PATCH", me(store), patch.toString(), "print=silent", ok404 = false)
        return ts
    }

    /** Mezcla aquí lo nuevo de tus otros dispositivos. Devuelve true si han cambiado tus datos. */
    fun mergeOwn(store: Store): Boolean {
        val st = stamps(store, store.accUid) ?: return false
        val seen = store.devSeen
        val mine = store.accDevice
        val now = System.currentTimeMillis()
        var changed = false
        for ((dev, ts) in st) {
            if (dev == mine || ts <= seen.optLong(dev, 0L) || now - ts > STALE) continue
            val raw = db(store, "GET", me(store) + "/dev/$dev/data", null) ?: continue
            val data = JSONTokener(raw).nextValue() as? String ?: continue
            if (store.mergeRemote(data)) changed = true
            seen.put(dev, ts)
        }
        store.devSeen = seen
        return changed
    }

    /** ¿Hay ya algo guardado en tu cuenta? (hora y fichas de lo más reciente, o null). */
    fun latest(store: Store, uid: String = store.accUid): JSONObject? {
        val st = stamps(store, uid) ?: return null
        val dev = st.maxByOrNull { it.value }?.key ?: return null
        val t = db(store, "GET", "/users/$uid/dev/$dev", null) ?: return null
        return JSONObject(t).put("_dev", dev)
    }

    /** Sustituye lo de este dispositivo por lo de tu cuenta (al entrar en un dispositivo que ya tenía otras cosas). */
    fun adopt(store: Store): Int {
        val o = latest(store) ?: return 0
        val data = o.optString("data", "")
        if (data.isBlank()) return 0
        store.importSync(data)
        val seen = store.devSeen
        seen.put(o.optString("_dev"), o.optLong("updated", 0L))
        store.devSeen = seen
        if (store.myName.isBlank() && o.optString("name").isNotBlank()) store.myName = o.optString("name")
        mergeOwn(store)
        return store.itemCount()
    }

    /** Busca a tu pareja por su usuario y le das permiso para ver lo tuyo. Devuelve su uid. */
    suspend fun setPartner(store: Store, user: String): String = withContext(Dispatchers.IO) {
        val u = normUser(user)
        if (u == store.accUser) throw Fail("Ese eres tú.")
        val raw = db(store, "GET", "/usernames/$u", null) ?: throw Fail("No hay nadie con el usuario «$u».")
        val uid = JSONTokener(raw).nextValue() as? String ?: throw Fail("No hay nadie con el usuario «$u».")
        db(store, "PUT", me(store) + "/partner", JSONObject.quote(uid), ok404 = false)
        if (uid != store.partnerUid) {
            store.partnerRaw = ""
            store.partnerSeen = ""
        }
        store.partnerUid = uid
        store.partnerUser = u
        Sincro.reinicia(store.contexto)
        uid
    }

    suspend fun clearPartner(store: Store) = withContext(Dispatchers.IO) {
        try { db(store, "DELETE", me(store) + "/partner", null) } catch (e: Exception) { }
        store.partnerUid = ""
        store.partnerUser = ""
        store.partnerRaw = ""
        store.partnerId = ""
        store.partnerSeen = ""
        Sincro.reinicia(store.contexto)
    }
}
