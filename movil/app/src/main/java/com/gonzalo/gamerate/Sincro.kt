package com.gonzalo.gamerate

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * La sincronización de tu cuenta, al momento y en un solo sitio (antes estaba repartida por la app y lo que no era una
 * ficha esperaba hasta medio minuto):
 *
 *  - Todo lo que se guarda y viaja con la cuenta (fichas, diario, sagas, lista negra, lo leído, maquetas, ajustes…)
 *    avisa aquí al guardarse y se sube enseguida ([ESPERA] ms, por si llegan varios cambios seguidos).
 *  - Mientras se ve cualquier pantalla de la app (también el lector), se escucha en directo tu cuenta: lo que sube otro
 *    dispositivo tuyo (otro móvil, la tablet, Atrio en el PC) se baja y se mezcla al instante; las horas jugadas en tus
 *    PC y lo de tu pareja, igual.
 *  - Una sola subida o mezcla a la vez ([candado]): nunca dos mezclas cruzadas.
 *  - Sin conexión se reintenta solo, y al salir de la app queda una subida programada para cuando haya red.
 */
object Sincro {
    /** Lo que se espera tras un cambio antes de subirlo (para juntar los que llegan seguidos). */
    const val ESPERA = 600L

    /** Subir y mezclar, de uno en uno (lo usan Couple.push y Couple.syncOwn). */
    val candado = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var app: Context? = null
    private var programada: Job? = null
    @Volatile private var cuando = 0L
    @Volatile private var fallos = 0
    private val bajando = AtomicBoolean(false)
    private val parejaPedida = AtomicBoolean(false)
    private val escuchas = ArrayList<FbStream>()
    private var escuchaDe = ""

    /** La app se está viendo (cualquiera de sus pantallas). */
    @Volatile var visible = false
        private set

    /** Último error al subir ("" si fue bien); la app lo enseña una vez. */
    var error by mutableStateOf("")
        private set

    @Volatile private var observando = false

    fun init(ctx: Context) {
        val a = ctx.applicationContext
        if (app === a) return
        synchronized(this) {
            if (app === a) return
            if (app != null) para()          // (solo en las pruebas cambia la app sin cambiar el proceso)
            app = a
            if (observando) return
            observando = true
        }
        // La app a la vista o no: la de todo el proceso (si se pasa al lector, la escucha sigue)
        val pon = Runnable {
            runCatching {
                androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, ev ->
                    when (ev) {
                        androidx.lifecycle.Lifecycle.Event.ON_START -> alVolver()
                        androidx.lifecycle.Lifecycle.Event.ON_STOP -> alSalir()
                        else -> {}
                    }
                })
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) pon.run() else Handler(Looper.getMainLooper()).post(pon)
    }

    private fun store(): Store? = app?.let { Store(it) }

    /**
     * Algo de tu cuenta ha cambiado aquí: se sube dentro de [ms] (si ya hay una subida programada antes, esa vale).
     * Lo llama Store al guardar; no hace falta llamarlo a mano.
     */
    fun pronto(ctx: Context, ms: Long = ESPERA) {
        init(ctx)
        val c = app ?: return
        if (!Store(c).paired) return
        val t = System.currentTimeMillis() + ms
        synchronized(this) {
            if (programada?.isActive == true && cuando in 1..t) return
            programada?.cancel()
            cuando = t
            programada = scope.launch {
                delay(ms)
                synchronized(this@Sincro) { cuando = 0L }
                // Una subida empezada se termina siempre (si se cortara a medias, los demás no se enterarían)
                withContext(NonCancellable) { sube(c) }
            }
        }
    }

    /** Sube lo pendiente ahora mismo. Devuelve false si no ha podido (se reintenta solo). */
    suspend fun sube(ctx: Context): Boolean {
        init(ctx)
        val c = app ?: return false
        val st = Store(c)
        if (!st.paired || !st.pendiente) return true
        return try {
            Couple.push(st)
            fallos = 0
            if (error.isNotEmpty()) enPantalla { error = "" }
            // Una foto tuya nueva (portada, fondo…): a tu nube enseguida, para que tus otros dispositivos la vean ya
            if (runCatching { NubeSync.fotosPorSubir(c, st) }.getOrDefault(false)) NubeSync.enqueue(c)
            // Lo que haya cambiado mientras se subía
            if (st.pendiente) pronto(c)
            true
        } catch (e: Exception) {
            fallos++
            val msg = st.cloudErr.ifBlank { e.message ?: "sin conexión" }
            enPantalla { error = msg }
            // Se reintenta solo: 5 s, 15 s, 45 s… hasta 5 minutos (y, por si se cierra la app, una subida para cuando haya red)
            if (fallos <= 8) pronto(c, minOf(300_000L, 5_000L * Math.pow(3.0, (fallos - 1).toDouble()).toLong()))
            CloudSave.enqueue(c)
            false
        }
    }

    /** Baja y mezcla lo nuevo de tus otros dispositivos (varias peticiones seguidas se juntan en una). */
    fun bajaPronto() {
        val c = app ?: return
        if (!bajando.compareAndSet(false, true)) return
        scope.launch {
            delay(150)
            bajando.set(false)
            val st = Store(c)
            if (st.paired) runCatching { Couple.syncOwn(st) }
            if (st.pendiente) pronto(c)
        }
    }

    /** Lo de tu pareja: se baja y la app lo enseña ([PartnerHub]). */
    private fun parejaPronto() {
        val c = app ?: return
        if (!parejaPedida.compareAndSet(false, true)) return
        scope.launch {
            delay(300)
            parejaPedida.set(false)
            val st = Store(c)
            runCatching { Couple.pull(st) }.getOrNull() ?: return@launch
            enPantalla { PartnerHub.version++ }
        }
    }

    internal fun alVolver() {
        visible = true
        val c = app ?: return
        escucha(c)
        bajaPronto()
        if (Store(c).pendiente) pronto(c, 0)
        if (Store(c).paired) Escritos.pronto(c, 0)
    }

    internal fun alSalir() {
        visible = false
        para()
        val c = app ?: return
        if (Store(c).pendiente) pronto(c, 0)
        // Lo escrito que no haya subido: ahora, y si no hay red, cuando la haya
        if (Store(c).paired && Escritos.get(c).pendientes().isNotEmpty()) { Escritos.pronto(c, 0); CloudSave.enqueue(c) }
    }

    /** Tras entrar o salir de la cuenta, o cambiar de pareja: se vuelve a escuchar lo que toca. */
    fun reinicia(ctx: Context) {
        init(ctx)
        para()
        if (visible) app?.let { escucha(it); bajaPronto() }
        app?.let { c -> val st = Store(c); if (st.paired) { Escritos.get(c).cuenta(st.accUid); Escritos.pronto(c, 0) } }
    }

    private fun url(st: Store, path: String, force: Boolean): String? =
        if (!st.paired) null else st.dbUrl + path + ".json?auth=" + Account.token(st, force)

    @Synchronized private fun escucha(c: Context) {
        val st = Store(c)
        if (!st.paired) { para(); return }
        val uid = st.accUid
        val pareja = st.partnerUid
        val de = "$uid|$pareja"
        if (escuchas.isNotEmpty() && escuchaDe == de) return
        para()
        escuchaDe = de
        val yo = st.accDevice
        // Las horas de subida de tus dispositivos: en cuanto otro sube algo, se baja (lo mío propio no)
        escuchas += FbStream("cuenta", { f -> url(st, "/users/$uid/stamps", f) }, { _, path ->
            if (path.trim('/') != yo) bajaPronto()
        })
        // Lo que escribes (Escribir): cada documento que sube otro dispositivo cambia su hora en el índice
        escuchas += FbStream("escritos", { f -> url(st, "/users/$uid/escritosIdx", f) }, { _, _ -> Escritos.bajaPronto(c) })
        // Lo jugado en tus PC (el diario y el calendario)
        escuchas += FbStream("horas", { f -> url(st, "/users/$uid/horasAt", f) }, { _, _ ->
            scope.launch { runCatching { HorasPc.bajar(c, st, uid, ya = true) } }
        })
        // Tu pareja: lo que valora, lee o escribe aparece al momento (si te deja verlo)
        // (si aún no te deja verlo, Firebase dice «sin permiso»: no es cosa de tu sesión, así que no se renueva por eso)
        if (pareja.isNotBlank()) escuchas += FbStream("pareja", { _ -> url(st, "/users/$pareja/stamps", false) }, { _, _ -> parejaPronto() })
        escuchas.forEach { it.start(scope) }
    }

    @Synchronized private fun para() {
        escuchas.forEach { it.stop() }
        escuchas.clear()
        escuchaDe = ""
    }

    private fun enPantalla(f: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) f() else Handler(Looper.getMainLooper()).post(f)
    }
}

/** Llegó algo nuevo de tu pareja (la app vuelve a leer lo guardado). */
object PartnerHub {
    private val v = mutableIntStateOf(0)
    var version: Int
        get() = v.intValue
        set(x) { v.intValue = x }
}
