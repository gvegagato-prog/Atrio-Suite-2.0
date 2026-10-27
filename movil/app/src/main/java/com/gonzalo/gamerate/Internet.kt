package com.gonzalo.gamerate

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** Buscar una imagen en internet (Google Imágenes dentro de la app) y quedarse con ella como foto tuya. */
object Internet {
    /** Lo pone la app: abre el buscador con una búsqueda inicial y devuelve la foto ya guardada en este móvil. */
    var pick: ((query: String, (String) -> Unit) -> Unit)? = null

    /** Una web de imágenes donde buscar: cómo se llama y cómo es su búsqueda. */
    class Fuente(val id: String, val nombre: String, val url: (String) -> String)

    val fuentes = listOf(
        Fuente("google", "Google Imágenes") { q -> "https://www.google.com/search?udm=2&hl=es&q=" + enc(q) },
        Fuente("artstation", "ArtStation") { q -> "https://www.artstation.com/search?sort_by=relevance&query=" + enc(q) },
        Fuente("pinterest", "Pinterest") { q -> "https://www.pinterest.com/search/pins/?q=" + enc(q) },
        Fuente("newgrounds", "Newgrounds") { q -> "https://www.newgrounds.com/search/conduct/art?terms=" + enc(q) }
    )
    private fun enc(q: String) = URLEncoder.encode(q.trim(), "UTF-8")
    fun fuente(id: String?) = fuentes.firstOrNull { it.id == id } ?: fuentes[0]

    fun searchUrl(q: String, f: Fuente = fuentes[0]) = f.url(q)

    /**
     * Las direcciones a probar para una imagen, de mejor a peor calidad: las webs enseñan miniaturas y guardan la grande con
     * otro tamaño en la dirección (Pinterest: /236x/ → /originals/). La última es siempre la que se vio.
     */
    fun candidatas(url: String): List<String> {
        val out = ArrayList<String>()
        if (url.contains("pinimg.com/")) {
            val m = Regex("(pinimg\\.com)/(\\d+x)/").find(url)
            if (m != null) { out += url.replace("/${m.groupValues[2]}/", "/originals/"); if (m.groupValues[2] != "736x") out += url.replace("/${m.groupValues[2]}/", "/736x/") }
        }
        if (url.contains("artstation.com/")) {
            val r = Regex("/(micro_square|smaller_square|small_square|small|medium|large|4k|8k)/")
            if (r.containsMatchIn(url)) {
                // ArtStation guarda cada imagen en varios tamaños; el mayor (8k, 4k) no siempre existe: se baja del primero que haya
                if (url.contains("/images/images/")) { out += r.replace(url, "/8k/"); out += r.replace(url, "/4k/") }
                out += r.replace(url, "/large/")
            }
        }
        out += url
        return out.distinct()
    }

    /** De un enlace de Google Imágenes (/imgres?imgurl=…), la dirección de la imagen. */
    fun deImgres(href: String): String? {
        val m = Regex("[?&]imgurl=([^&]+)").find(href) ?: return null
        return runCatching { URLDecoder.decode(m.groupValues[1], "UTF-8") }.getOrNull()?.takeIf { it.startsWith("http") }
    }

    /** Una miniatura de Google (de la cuadrícula de resultados), no la imagen de verdad. */
    fun esMiniatura(u: String) = u.startsWith("data:") || u.contains("encrypted-tbn") || u.contains("gstatic.com/")

    /** Los bytes de una imagen (dirección http/https o data:) y su tipo. */
    fun descargar(url: String, ua: String, desde: String? = null): Pair<ByteArray, String?> {
        if (url.startsWith("data:")) {
            val head = url.substringBefore(',')
            val mime = head.removePrefix("data:").substringBefore(';').ifBlank { null }
            val body = url.substringAfter(',')
            val bytes = if (head.contains("base64")) android.util.Base64.decode(body, android.util.Base64.DEFAULT) else URLDecoder.decode(body, "ISO-8859-1").toByteArray(Charsets.ISO_8859_1)
            return bytes to mime
        }
        var last: Exception? = null
        // Algunas webs no dejan sacar sus imágenes sin venir de una página: se prueba también así
        val cookies = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        for (u in candidatas(url)) for (referer in listOf(null, desde, "https://www.google.com/").distinct()) {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", ua)
                c.setRequestProperty("Accept", "image/*,*/*;q=0.8")
                if (referer != null) c.setRequestProperty("Referer", referer)
                if (!cookies.isNullOrBlank()) c.setRequestProperty("Cookie", cookies)
                c.connectTimeout = 15000; c.readTimeout = 25000
                if (c.responseCode !in 200..299) error("la web respondió ${c.responseCode}")
                val len = c.contentLengthLong
                if (len > MAX) error("la imagen es demasiado grande")
                val bytes = c.inputStream.use { it.readBytes() }
                if (bytes.size > MAX) error("la imagen es demasiado grande")
                if (bytes.size < 200) error("no es una imagen")
                return bytes to c.contentType?.substringBefore(';')?.trim()
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("no se pudo bajar")
    }
    private const val MAX = 40L * 1024 * 1024

    /**
     * ArtStation: al abrir un proyecto (/artwork/<hash>) se pide su ficha (/projects/<hash>.json, desde la propia página, con sus
     * cookies) y, si la página no ha puesto ninguna imagen grande a la vista, se muestran aquí las imágenes del proyecto.
     */
    const val JS_ARTSTATION = """(function(){try{
var m=/\/artwork\/([A-Za-z0-9]+)/.exec(location.pathname);if(!m)return;var h=m[1];
if(window.__atrioAS&&window.__atrioAS.h===h)return;
var st=window.__atrioAS={h:h,urls:null};
var prev=document.getElementById('atrio-as');if(prev)prev.remove();
fetch('/projects/'+h+'.json',{credentials:'include',headers:{'Accept':'application/json'}}).then(function(r){return r.json();}).then(function(j){
 st.urls=(j.assets||[]).filter(function(a){return a.has_image&&a.image_url;}).map(function(a){return a.image_url;});
}).catch(function(e){st.err=String(e);});
var n=0;var t=setInterval(function(){n++;if(window.__atrioAS!==st||n>12){clearInterval(t);return;}
 if(!st.urls||!st.urls.length)return;
 var grande=[].slice.call(document.images).some(function(i){var r=i.getBoundingClientRect();return i.naturalWidth>=400&&r.width>=200&&r.height>=120&&r.bottom>0&&r.top<innerHeight;});
 if(grande){clearInterval(t);return;}
 if(n<4)return;
 clearInterval(t);
 var d=document.createElement('div');d.id='atrio-as';
 d.style.cssText='position:fixed;left:0;right:0;top:60px;bottom:0;overflow:auto;background:#111;z-index:2147483647;padding:8px;-webkit-overflow-scrolling:touch';
 var x=document.createElement('div');x.textContent='Imágenes del proyecto (vista de Atrio) — toca aquí para cerrar';x.style.cssText='color:#ccc;font:14px sans-serif;padding:6px 4px 10px';x.onclick=function(){d.remove();};d.appendChild(x);
 st.urls.forEach(function(u){var im=document.createElement('img');im.src=u;im.style.cssText='display:block;width:100%;height:auto;margin:0 0 8px';d.appendChild(im);});
 document.body.appendChild(d);
},1000);
}catch(e){}})()"""

    /**
     * ArtStation y Pinterest, a nuestra manera. Sus webs dentro de la app fallan (en ArtStation los proyectos salen en
     * blanco y la página se descoloca; Pinterest tapa los resultados pidiendo que inicies sesión), así que, una vez cargada
     * su página (con su comprobación anti-robots ya pasada), se le piden los resultados a su propia API desde la misma
     * página y se pinta encima una rejilla limpia; al tocar uno se abren sus imágenes grandes. Si la API no responde,
     * se queda su web tal cual. Uso: [rejillaUi].
     *  - ArtStation: /api/v2/csrf_protection/token.json y /api/v2/search/projects.json (como gallery-dl); /projects/<hash>.json.
     *  - Pinterest: /resource/BaseSearchResource/get/ (la que usa su web, sin sesión), por páginas con «bookmarks».
     */
    private const val JS_REJILLA = """(function(){if(window.atrioRej)return;
var F=null,raiz=null,estado,rejilla,mas,detalle,consulta='',cargando=false,bien=false,sigue=null,total=0;
function el(t,css,txt){var e=document.createElement(t);if(css)e.style.cssText=css;if(txt!=null)e.textContent=txt;return e;}
function monta(){if(raiz&&document.body.contains(raiz))return;
 raiz=el('div','position:fixed;left:0;top:0;right:0;bottom:0;z-index:2147483646;background:PAPEL;color:TINTA;overflow:auto;-webkit-overflow-scrolling:touch;font:15px/1.45 system-ui,sans-serif;padding:12px 12px 48px;box-sizing:border-box');
 raiz.id='atrio-rej';
 estado=el('div','padding:4px 2px 12px;color:GRIS;font-size:13px');
 rejilla=el('div','display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:10px');
 mas=el('button','display:none;margin:18px auto 0;padding:12px 24px;border:1.5px solid TINTA;background:transparent;color:TINTA;font:800 12px system-ui,sans-serif;letter-spacing:1.4px','MÁS RESULTADOS');
 mas.onclick=function(){carga(false);};
 detalle=el('div','display:none');
 raiz.appendChild(estado);raiz.appendChild(rejilla);raiz.appendChild(mas);raiz.appendChild(detalle);
 document.body.appendChild(raiz);document.documentElement.style.overflow='hidden';}
function quita(){if(raiz){raiz.remove();raiz=null;document.documentElement.style.overflow='';}}
function resumen(){estado.textContent=(total?total+' en ':'Resultados de ')+F.nombre+' · toca uno para verlo grande';}
function carga(primera){if(cargando)return;cargando=true;if(raiz)estado.textContent=primera?'Buscando…':'Cargando más…';
 F.busca(consulta,primera?null:sigue).then(function(r){
  monta();if(primera){rejilla.innerHTML='';cierra();raiz.scrollTop=0;}
  sigue=r.sigue;total=r.total||0;
  r.items.forEach(function(it){if(!it.mini)return;var c=el('div','cursor:pointer;min-width:0');
   var im=el('img','display:block;width:100%;aspect-ratio:1/1;object-fit:cover;background:rgba(0,0,0,.06)');im.src=it.mini;im.alt=it.titulo||'';im.loading='lazy';
   var t=el('div','font-size:12px;line-height:1.3;margin-top:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis',it.titulo||'');
   c.appendChild(im);c.appendChild(t);c.onclick=function(){abre(it);};rejilla.appendChild(c);});
  bien=true;window.atrioRej.ok=true;
  if(rejilla.children.length)resumen();else estado.textContent=F.nombre+' no tiene nada para «'+consulta+'».';
  mas.style.display=sigue&&r.items.length?'block':'none';})
 .catch(function(e){window.atrioRej.err=String(e);if(!bien)quita();else if(raiz)estado.textContent='No se ha podido cargar ('+e.message+')';})
 .then(function(){cargando=false;});}
function abre(it){rejilla.style.display='none';mas.style.display='none';detalle.style.display='block';detalle.innerHTML='';raiz.scrollTop=0;
 var v=el('div','padding:2px 2px 12px;font:800 12px system-ui,sans-serif;letter-spacing:1.4px;cursor:pointer','← RESULTADOS');v.onclick=cierra;detalle.appendChild(v);
 if(it.titulo)detalle.appendChild(el('div','font-weight:700;font-size:19px;line-height:1.25;margin:0 2px 12px',it.titulo));
 estado.textContent='Abriendo…';
 F.abre(it).then(function(us){
  estado.textContent=us.length?'Deja a la vista la que quieras y pulsa «Usar esta imagen» (o mantenla pulsada)':'Esto no tiene imágenes (es un vídeo o un modelo 3D).';
  us.forEach(function(u){var im=el('img','display:block;width:100%;height:auto;margin:0 0 12px;background:rgba(0,0,0,.06)');im.src=u;detalle.appendChild(im);});})
 .catch(function(e){estado.textContent='No se ha podido abrir ('+e.message+')';});}
function cierra(){if(!detalle)return;detalle.style.display='none';detalle.innerHTML='';rejilla.style.display='grid';mas.style.display=sigue&&rejilla.children.length?'block':'none';if(bien&&rejilla.children.length)resumen();}
function ok(r){if(!r.ok)throw new Error('error '+r.status);return r.json();}
var FUENTES={
 artstation:{nombre:'ArtStation',token:null,
  tok:function(){var f=this;if(f.token)return Promise.resolve(f.token);
   return fetch('/api/v2/csrf_protection/token.json',{method:'POST',credentials:'include',headers:{'Accept':'*/*','Content-Type':'application/json'},body:'{}'})
   .then(ok).then(function(j){f.token=j.public_csrf_token;if(!f.token)throw new Error('sin token');return f.token;});},
  busca:function(q,pag){var n=pag||1;return this.tok().then(function(t){return fetch('/api/v2/search/projects.json',{method:'POST',credentials:'include',
   headers:{'Accept':'application/json, text/plain, */*','Content-Type':'application/json','PUBLIC-CSRF-TOKEN':t},
   body:JSON.stringify({query:q,page:n,per_page:50,sorting:'relevance',pro_first:'1',filters:[],additional_fields:[]})});}).then(ok)
   .then(function(j){var d=j.data||[];var tot=j.total_count||d.length;
    return {total:tot,sigue:(n*50<tot&&d.length)?n+1:null,items:d.filter(function(p){return p.hash_id;}).map(function(p){var c=p.cover||{};var who=(p.user&&(p.user.full_name||p.user.username))||'';
     return {id:p.hash_id,mini:p.smaller_square_cover_url||c.small_square_url||c.thumb_url||c.medium_image_url||p.cover_url||'',titulo:(p.title||'')+(who?' · '+who:'')};})};});},
  abre:function(it){return fetch('/projects/'+it.id+'.json',{credentials:'include',headers:{'Accept':'application/json'}}).then(ok)
   .then(function(j){return (j.assets||[]).filter(function(a){return a.has_image&&a.image_url;}).map(function(a){return a.image_url;});});}},
 pinterest:{nombre:'Pinterest',
  busca:function(q,bm){var src='/search/pins/?q='+encodeURIComponent(q);
   var data=JSON.stringify({options:{query:q,scope:'pins',page_size:50,bookmarks:bm?[bm]:[]},context:{}});
   return fetch('/resource/BaseSearchResource/get/?source_url='+encodeURIComponent(src)+'&data='+encodeURIComponent(data),{credentials:'include',
    headers:{'Accept':'application/json, text/javascript, */*; q=0.01','X-Requested-With':'XMLHttpRequest','X-Pinterest-AppState':'active','X-Pinterest-Source-Url':src,'X-Pinterest-PWS-Handler':'www/search/[scope].js'}})
   .then(ok).then(function(j){var rr=j.resource_response||{};var d=rr.data||{};var l=Array.isArray(d)?d:(d.results||[]);var b=rr.bookmark;
    return {total:0,sigue:(b&&b!=='-end-'&&l.length)?b:null,items:l.filter(function(x){return x&&x.images;}).map(function(x){var im=x.images;var g=im['474x']||im['236x']||im['736x']||im.orig||{};
     return {id:x.id,mini:g.url||'',grande:((im.orig||im['736x']||g)||{}).url||g.url,titulo:x.grid_title||x.title||x.seo_alt_text||''};})};});},
  abre:function(it){return Promise.resolve(it.grande?[it.grande]:[]);}}
};
window.atrioRej={busca:function(f,q){var n=FUENTES[f];if(!n)return;var c=String(q||'');if(F===n&&consulta===c&&(bien||cargando)){if(bien&&raiz&&!document.body.contains(raiz)){document.body.appendChild(raiz);document.documentElement.style.overflow='hidden';}return;}if(F!==n){F=n;bien=false;}consulta=c;sigue=null;if(consulta)carga(true);},
 abierto:function(){return !!(detalle&&detalle.style.display==='block');},cierra:cierra,activo:function(){return !!(raiz&&document.body.contains(raiz));}};
})()"""

    /**
     * Cómo va la rejilla propia: «ok» (ya está), «err» (su API no responde: se queda su web), «reto» (la comprobación
     * anti-robots de Cloudflare: si pide que toques algo, hay que enseñarla) o «» (aún cargando).
     */
    const val JS_ESTADO_REJILLA = """(function(){try{var r=window.atrioRej;if(r&&r.activo())return 'ok';
var reto=!!(window._cf_chl_opt||document.querySelector('#challenge-form,.cf-turnstile,#cf-challenge-running,#challenge-stage'))||/just a moment|un momento/i.test(document.title||'');
if(reto)return 'reto';if(r&&r.err)return 'err';return '';}catch(e){return 'err';}})()"""

    /** La rejilla propia de [fuente] («artstation» o «pinterest») con los colores de la app, buscando [q]. */
    fun rejillaUi(fuente: String, q: String, papel: String, tinta: String, gris: String): String =
        JS_REJILLA.replace("PAPEL", papel).replace("TINTA", tinta).replace("GRIS", gris) +
            ";window.atrioRej&&atrioRej.busca(" + JSONObject.quote(fuente) + "," + JSONObject.quote(q) + ")"

    /** Si esa página es la búsqueda de una web con rejilla propia: cuál («artstation», «pinterest») y qué se busca. */
    fun rejillaDe(url: String?): Pair<String, String>? {
        val u = runCatching { android.net.Uri.parse(url ?: return null) }.getOrNull() ?: return null
        val h = u.host.orEmpty()
        return when {
            (h == "artstation.com" || h.endsWith(".artstation.com")) && u.path.orEmpty().startsWith("/search") -> "artstation" to u.getQueryParameter("query").orEmpty()
            h.contains("pinterest.") && u.path.orEmpty().startsWith("/search/pins") -> "pinterest" to u.getQueryParameter("q").orEmpty()
            else -> null
        }
    }

    /**
     * Qué imagen se está viendo en la página: la más grande de las visibles, mejor si es la de verdad (http) y no la
     * miniatura de Google. Devuelve {"u": dirección, "w": ancho, "low": true si es solo la miniatura}.
     */
    const val JS_ELEGIDA = """(function(){try{
var vis=function(e){var r=e.getBoundingClientRect();return r.width>=90&&r.height>=90&&r.bottom>0&&r.top<innerHeight&&r.right>0&&r.left<innerWidth;};
var tbn=function(s){return /encrypted-tbn|gstatic\.com\/|^data:/.test(s);};
var best=null,bestLow=null;
var capa=document.getElementById('atrio-rej');
var all=[].slice.call(capa?capa.querySelectorAll('img'):document.images).filter(vis);
all.forEach(function(i){var s=i.currentSrc||i.src||'';if(!s)return;var r=i.getBoundingClientRect();var a=r.width*r.height;
 var a2=i.closest&&i.closest('a');var h=a2?a2.href:'';var m=/[?&]imgurl=([^&]+)/.exec(h);
 var full=m?decodeURIComponent(m[1]):null;
 var o={u:full||s,a:a,w:i.naturalWidth||0,low:!full&&tbn(s)};
 if(o.low){if(!bestLow||a>bestLow.a)bestLow=o;}else{if(!best||a>best.a)best=o;}});
var as=window.__atrioAS;if(as&&as.urls&&as.urls.length){var el=document.getElementById('atrio-as');
 var ims=el?[].slice.call(el.querySelectorAll('img')).filter(vis):[];
 if(el&&ims.length){ims.sort(function(a,b){return b.getBoundingClientRect().height-a.getBoundingClientRect().height;});return JSON.stringify({u:ims[0].src,w:ims[0].naturalWidth||0,low:false});}
 if(!best)return JSON.stringify({u:as.urls[0],w:0,low:false});}
var r=best||bestLow;return JSON.stringify(r?{u:r.u,w:r.w,low:!!r.low}:{});}catch(e){return JSON.stringify({err:String(e)});}})()"""
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InternetPicker(initialQuery: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    val prefs = remember { ctx.getSharedPreferences("internet", Context.MODE_PRIVATE) }
    var fuente by remember { mutableStateOf(Internet.fuente(prefs.getString("fuente", null))) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // Los colores de la app, para la rejilla propia de ArtStation
    val papel = String.format("#%06X", 0xFFFFFF and Paper.toArgb())
    val tinta = String.format("#%06X", 0xFFFFFF and Charcoal.toArgb())
    val gris = String.format("#%06X", 0xFFFFFF and Muted.toArgb())
    // ArtStation y Pinterest: su web se tapa con un fondo liso hasta que está la rejilla propia (sin la web a medio cargar
    // que luego desaparece). Si su API no responde, o Cloudflare pide que toques algo, se destapa
    fun conRejilla(u: String?) = Internet.rejillaDe(u)?.second?.isNotBlank() == true
    var tapada by remember { mutableStateOf(conRejilla(Internet.searchUrl(initialQuery, fuente))) }
    var tapadaDesde by remember { mutableStateOf(System.currentTimeMillis()) }
    // (hasta que entra la página nueva, lo que se pregunte lo contesta la de antes, que puede tener ya su rejilla)
    var entro by remember { mutableStateOf(false) }
    var pedida by remember { mutableStateOf<String?>(null) }
    fun carga(w: WebView, u: String) {
        tapada = conRejilla(u); tapadaDesde = System.currentTimeMillis(); entro = false; pedida = Internet.rejillaDe(u)?.first
        w.loadUrl(u)
    }
    fun haEntrado(url: String?) { if (Internet.rejillaDe(url)?.first == pedida) entro = true }
    LaunchedEffect(tapada) {
        while (tapada) {
            kotlinx.coroutines.delay(250)
            val t = System.currentTimeMillis() - tapadaDesde
            if (t > 15_000) { tapada = false; break }
            val w = web ?: continue
            if (!entro) continue
            w.evaluateJavascript(Internet.JS_ESTADO_REJILLA) { r ->
                val st = runCatching { JSONTokener(r).nextValue() as? String }.getOrNull().orEmpty()
                if (st == "ok" || st == "err" || (st == "reto" && t > 6_000)) tapada = false
            }
        }
    }
    fun buscar() {
        msg = ""
        val w = web ?: return
        // Con la rejilla propia ya puesta (ArtStation, Pinterest), se busca ahí mismo: sin recargar ni volver a pasar por su comprobación
        if (Internet.rejillaDe(w.url)?.first == fuente.id)
            w.evaluateJavascript("window.atrioRej&&atrioRej.activo()?(atrioRej.busca(${JSONObject.quote(fuente.id)},${JSONObject.quote(query)}),true):false") { r -> if (r != "true") carga(w, Internet.searchUrl(query, fuente)) }
        else carga(w, Internet.searchUrl(query, fuente))
    }

    fun usar(raw: String) {
        if (busy) return
        busy = true; msg = "Bajando la imagen…"
        scope.launch {
            try {
                val ua = web?.settings?.userAgentString ?: "Mozilla/5.0"
                // Como la pide la página: desde ella (Referer) y con sus cookies (algunas webs no dan la imagen si no)
                val desde = web?.url?.takeIf { it.startsWith("http") }
                val (bytes, mime) = withContext(Dispatchers.IO) { Internet.descargar(raw, ua, desde) }
                val foto = Photos.importBytes(ctx, bytes, mime)
                busy = false
                onPick(foto)
            } catch (e: Exception) {
                busy = false
                msg = "No se ha podido usar esa imagen (${e.message ?: "error"}). Prueba con otra."
            }
        }
    }

    // Una miniatura (la de la cuadrícula de Google, pequeña): se avisa, y si vuelves a pulsar, se usa igual
    var miniatura by remember { mutableStateOf("") }
    fun usarOMiniatura(u: String, low: Boolean) {
        if (!low || miniatura == u) { miniatura = ""; usar(u); return }
        miniatura = u
        msg = "Eso es la miniatura (se vería borrosa): tócala, espera a que se vea grande y pulsa «Usar esta imagen». Si aun así la quieres, pulsa otra vez."
    }

    fun usarLaAbierta() {
        val w = web ?: return
        w.evaluateJavascript(Internet.JS_ELEGIDA) { r ->
            val txt = runCatching { JSONTokener(r).nextValue() as? String }.getOrNull()
            val o = runCatching { JSONObject(txt ?: "{}") }.getOrDefault(JSONObject())
            val u = o.optString("u")
            when {
                u.isBlank() -> msg = "No veo ninguna imagen abierta: toca una de los resultados y espera a que se vea grande."
                else -> usarOMiniatura(u, o.optBoolean("low"))
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)) {
        // Atrás: primero por las páginas que has abierto dentro; luego se cierra
        BackHandler {
            val w = web
            if (w == null) onDismiss()
            // En la rejilla propia, Atrás cierra primero lo que hayas abierto
            else w.evaluateJavascript("window.atrioRej&&atrioRej.abierto()?(atrioRej.cierra(),true):false") { r ->
                if (r != "true") { if (w.canGoBack()) w.goBack() else onDismiss() }
            }
        }
        Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
            // De dónde: tres webs de imágenes
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Internet.fuentes.forEach { f ->
                    SoftChip(f.nombre, f.id == fuente.id) { fuente = f; prefs.edit().putString("fuente", f.id).apply(); web?.let { carga(it, Internet.searchUrl(query, f)) }; msg = "" }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onDismiss)
                OutlinedTextField(
                    query, { query = it }, Modifier.weight(1f).padding(horizontal = 6.dp),
                    singleLine = true, shape = shp(14.dp), label = { Text("Buscar en ${fuente.nombre}") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { buscar() })
                )
                IconButton({ buscar() }) { Icon(Icons.Rounded.Search, "Buscar") }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        // Como el Chrome del móvil (con «wv», algunas webs dan una versión recortada)
                        settings.userAgentString = settings.userAgentString.replace("; wv)", ")")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            // En cuanto la página empieza a verse (no hace falta esperar a sus imágenes): la rejilla, antes
                            override fun onPageCommitVisible(v: WebView, url: String?) {
                                haEntrado(url)
                                Internet.rejillaDe(url)?.let { (f, q) -> v.evaluateJavascript(Internet.rejillaUi(f, q.ifBlank { query }, papel, tinta, gris), null) }
                            }
                            override fun onPageFinished(v: WebView, url: String?) {
                                // (si te ha llevado a otra página, sin rejilla, se enseña ya)
                                haEntrado(url)
                                if (!conRejilla(url)) tapada = false
                                if (url?.contains("artstation.com/artwork/") == true) v.evaluateJavascript(Internet.JS_ARTSTATION, null)
                                // La búsqueda de ArtStation o Pinterest: la rejilla propia encima (si su API responde; si no, su web)
                                Internet.rejillaDe(url)?.let { (f, q) -> v.evaluateJavascript(Internet.rejillaUi(f, q.ifBlank { query }, papel, tinta, gris), null) }
                            }
                            // ArtStation cambia de proyecto sin recargar la página: se avisa al cambiar la dirección
                            override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) { if (url?.contains("artstation.com/artwork/") == true) v.evaluateJavascript(Internet.JS_ARTSTATION, null) }
                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                val u = r.url.toString()
                                return !(u.startsWith("http://") || u.startsWith("https://"))
                            }
                        }
                        // Mantener pulsada una imagen: esa es la que quieres
                        setOnLongClickListener {
                            val h = hitTestResult
                            val extra = h.extra.orEmpty()
                            val u = when (h.type) {
                                WebView.HitTestResult.IMAGE_TYPE -> extra
                                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE, WebView.HitTestResult.SRC_ANCHOR_TYPE -> Internet.deImgres(extra) ?: extra.takeIf { h.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE }
                                else -> null
                            }
                            if (!u.isNullOrBlank() && (u.startsWith("http") || u.startsWith("data:"))) { usarOMiniatura(u, Internet.esMiniatura(u) && Internet.deImgres(extra) == null); true } else false
                        }
                        carga(this, Internet.searchUrl(initialQuery, fuente))
                        web = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.stopLoading(); it.destroy(); web = null }
            )
            if (tapada) Box(
                // (fondo liso que no deja tocar la web de debajo)
                Modifier.matchParentSize().background(Paper).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
                contentAlignment = Alignment.Center
            ) {
                Text("Buscando en ${fuente.nombre}…", color = Muted, fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 18.sp)
            }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(msg.ifBlank { "Toca una imagen para verla grande y pulsa «Usar esta imagen». También vale mantener pulsada una imagen." + if (fuente.id == "google") "" else " Se intenta bajar en su tamaño original." }, color = Muted, fontSize = 12.sp)
                Button({ usarLaAbierta() }, enabled = !busy && !tapada, shape = pillShape(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Bajando…" else "Usar esta imagen") }
            }
        }
    }
}
