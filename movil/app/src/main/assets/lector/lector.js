// Lector de Atrio: EPUB, PDF, MOBI/AZW3, FB2 y CBZ con foliate-js (y pdf.js para PDF).
// Todo se guarda en el móvil a través de window.Atrio (puente con la app): posición, ajustes, subrayados y marcadores.
import './foliate/view.js'
import { FootnoteHandler } from './foliate/footnotes.js'
import { compare as cfiCompare } from './foliate/epubcfi.js'

const A = window.Atrio
const $ = s => document.querySelector(s)
const st = JSON.parse(A.state())
const S = Object.assign({
    theme: 'papel', size: 100, font: 'original', spacing: 1.5, margin: 'm', justify: true,
    flow: 'paginated', brightness: -1, warm: 0, keepOn: true, zoom: 'fit-width',
    // Atrio 6.8: guiones, espacio entre párrafos, dos columnas en horizontal, animación, línea de estado,
    // tu velocidad de lectura (bytes por minuto; 0 = aún no se sabe) y la velocidad de la voz
    hyphens: true, paraSpace: 0, columns: false, animated: true, status: true, speed: 0, ttsRate: 1,
    // De 21:00 a 7:00, el tema Noche (y por el día, el tuyo)
    nightAuto: false,
    // PDF y cómics: «pages» página a página; «scroll», deslizando hacia abajo; «strip», tira continua sin huecos (manga)
    pdfFlow: 'pages',
    // PDF: el texto adaptado a la pantalla (novelas) o la página tal cual
    pdfTexto: true, pdfForzar: false,      // (pdfForzar: «Texto adaptable» elegido a mano, vale aunque el PDF tenga poco texto por página)
    // Atrio 6.62: los toques de los lados («normal»: izquierda atrás y derecha adelante; «adelante»: los dos lados
    // adelante, para leer con una mano), los botones de volumen para pasar página y la letra algo más gruesa (Literata)
    tapZones: 'normal', volPages: true, weight: 0,
}, st.settings || {})
// Lo que llevas leído de este libro: tiempo total y por día (ms)
const stats = Object.assign({ total: 0, days: {} }, st.stats || {})
// El tiempo leído va por dispositivo (stats.dev): aquí solo se suma en el de este dispositivo, y «total» y «days» son
// la suma de todos (lo que se enseña). Un tiempo antiguo, sin repartir, pasa a ser el de este dispositivo.
const DEV = st.device || 'local'
if (!stats.dev) stats.dev = stats.total > 0 ? { [DEV]: { total: stats.total, days: { ...stats.days } } } : {}
if (!stats.dev[DEV]) stats.dev[DEV] = { total: 0, days: {} }
function recontar() {
    let t = 0; const d = {}
    for (const b of Object.values(stats.dev)) {
        t += Number(b.total) || 0
        for (const [k, v] of Object.entries(b.days || {})) d[k] = (d[k] || 0) + (Number(v) || 0)
    }
    stats.total = t; stats.days = d
}
recontar()
// En un PDF, lo subrayado y los marcadores de la página original y los del texto adaptado no se mezclan (sus
// posiciones no valen en el otro formato): m = 'r' es del texto adaptado. Se guardan todos juntos.
let notesAll = st.annotations || [], marksAll = st.bookmarks || []
let notes = notesAll      // { value, text, note, created, index, label, m } (el color de antes ya no se usa)
let marks = marksAll      // { value, label, fraction, created, m }
const delModo = n => !esPDF || (n.m === 'r') === reflow
function separaModo() {
    notes = notesAll.filter(delModo); marks = marksAll.filter(delModo)
}
/** Las letras de la app con grosor variable (se pueden poner un poco más gruesas). */
const VARIABLES = ['literata', 'garamond']
const THEMES = [['papel', 'Papel'], ['sepia', 'Sepia'], ['gris', 'Gris'], ['noche', 'Noche'], ['negro', 'Negro']]
const FONTS = [['original', 'Original'], ['literata', 'Literata'], ['garamond', 'Garamond'], ['atrio', 'Fraunces'], ['serif', 'Serif'], ['sans', 'Sans'], ['facil', 'Fácil lectura'], ['mono', 'Mono']]
// El acento de la app (el que elegiste en Apariencia), en su versión clara y oscura
const ACCENT = st.accent || { light: '#C8372D', dark: '#E0533F' }
const DARK_THEMES = ['gris', 'noche', 'negro']
const isNight = () => { const h = new Date().getHours(); return h >= 21 || h < 7 }
/** El tema que toca ahora: el tuyo, o Noche si lo pediste y es de noche (salvo que ya uses uno oscuro). */
const theme = () => (S.nightAuto && isNight() && !DARK_THEMES.includes(S.theme)) ? 'noche' : S.theme
// Cada diez minutos se mira si ha cambiado (al anochecer, sin cerrar el libro)
let lastTheme = null
setInterval(() => { if (view && S.nightAuto && theme() !== lastTheme) applySettings() }, 600000)
let view, isPDF = false, esPDF = false, reflow = false, last = null, chromeOn = false, selection = null

// ---------------------------------------------------------------- Utilidades
const debounce = (f, ms) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => f(...a), ms) } }
const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c])
const pct = f => Math.round((f || 0) * 100) + '%'
const fecha = t => new Date(t).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })
/** Un aviso corto abajo. Con [deshacer], lleva su botón y dura más. */
function aviso(t, deshacer, etiqueta = 'Deshacer') {
    const a = $('#aviso')
    a.replaceChildren(document.createTextNode(t))
    a.classList.toggle('con-boton', !!deshacer)
    if (deshacer) {
        const b = document.createElement('button')
        b.textContent = etiqueta
        b.onclick = e => { e.stopPropagation(); a.classList.remove('on'); deshacer() }
        a.append(b)
    }
    a.classList.add('on'); clearTimeout(aviso.t); aviso.t = setTimeout(() => a.classList.remove('on'), deshacer ? 5000 : 1800)
}
const saveSettings = () => A.saveSettings(JSON.stringify(S))
// Atrio 6.63: el lector ya no tiene brillo propio: usa el del móvil. Deslizar por el borde izquierdo cambia el brillo
// del móvil de verdad (con el permiso «Modificar ajustes del sistema»); sin ese permiso, solo mientras tienes el libro abierto.
S.brightness = -1
let brilloSesion = -1      // (sin permiso: el brillo que pusiste deslizando, hasta que cierres el libro)
let brilloAvisado = false
/** El brillo de ahora en la escala de la barra: el que pusiste deslizando (sin permiso) o el del móvil. */
const brilloAhora = () => {
    if (brilloSesion >= 0) return brilloSesion
    try { const v = A.brilloActual?.(); if (typeof v === 'number' && v >= 0 && v <= 1) return Math.round(v * 100) / 100 } catch (e) { }
    return 0.5
}
const saveNotes = () => { notesAll = [...notesAll.filter(n => !delModo(n)), ...notes]; A.saveAnnotations(JSON.stringify(notesAll)) }
const saveMarks = () => { marksAll = [...marksAll.filter(n => !delModo(n)), ...marks]; A.saveBookmarks(JSON.stringify(marksAll)) }
const label = () => last?.tocItem?.label?.trim() || ''

// ---------------------------------------------------------------- Abrir el libro
let paso = 'inicio'
let avisoPDF = ''
function fallo(e) {
    console.error('Lector: fallo en «' + paso + '»', e && (e.stack || e.message || e))
    $('#carga').classList.remove('fuera')
    // Dónde ha fallado (archivo y línea): si me mandas una captura, se ve a la primera qué arreglar
    const at = String(e?.stack || '').split('\n').map(l => l.match(/([\w-]+\.m?js):(\d+)/)).find(Boolean)
    $('#carga-txt').textContent = ((e && e.message) || 'No se ha podido abrir el libro.') + ' (' + paso + (at ? ' · ' + at[1] + ':' + at[2] : '') + ')'
    $('.gira').classList.add('oculta')
}
// Si algo se queda colgado, al menos se ve en qué paso
setTimeout(() => { if (!$('#carga').classList.contains('fuera') && !$('.gira').classList.contains('oculta')) console.error('Lector: sigue cargando en «' + paso + '»') }, 15000)

async function open() {
    try { await abrir() } catch (e) { fallo(e) }
}

async function abrir() {
    $('#t-libro').textContent = st.title || ''
    document.body.dataset.theme = theme()
    try {
        paso = 'leer el archivo'
        const res = await fetch('/libro/' + encodeURIComponent(st.file))
        if (!res.ok) throw new Error('No se puede leer el archivo (' + res.status + '). ¿Lo has movido o borrado?')
        const file = new File([await res.blob()], st.file)
        // PDF: el texto adaptado a la pantalla (si tiene texto) o la página original
        esPDF = new TextDecoder('latin1').decode(await file.slice(0, 1024).arrayBuffer()).includes('%PDF-')
        reflow = false
        let libro = file
        if (esPDF && S.pdfTexto !== false) {
            try {
                paso = 'preparar el texto del PDF'
                const { makePDFReflow } = await import('./foliate/pdf-reflow.js')
                const cargaTxt = $('#carga-txt')
                libro = await makePDFReflow(file, { forzar: !!S.pdfForzar, onProgress: (n, t) => { cargaTxt.textContent = `Preparando el texto del PDF… ${Math.round(100 * n / t)} % (solo la primera vez)` } })
                reflow = true
            } catch (e) {
                libro = file
                if (e?.sinTexto) avisoPDF = 'Este PDF no tiene texto (parece escaneado): se ve como página.'
                else if (e?.pocoTexto) avisoPDF = 'Este PDF tiene muy poco texto por página (¿cómic o libro ilustrado?): se ve como página. Letra → Texto adaptable lo fuerza.'
                else { console.warn('Lector: no se pudo adaptar el texto del PDF', e); avisoPDF = 'No se ha podido adaptar el texto de este PDF: se ve como página.' }
            }
        }
        separaModo()
        paso = 'preparar el visor'
        view = document.createElement('foliate-view')
        // (solo cuenta en PDF y cómics: los EPUB tienen su propio «Pasar página»)
        view.dataset.fxlFlow = S.pdfFlow || 'pages'
        $('#libro').append(view)
        paso = 'descomprimir el libro'
        await view.open(libro)
    } catch (e) {
        fallo(e)
        return
    }
    paso = 'mostrar la primera página'
    isPDF = !!view.isFixedLayout
    document.body.classList.toggle('pdf', isPDF)
    view.addEventListener('load', onLoad)
    view.addEventListener('relocate', onRelocate)
    view.addEventListener('external-link', e => { e.preventDefault(); A.openUrl(e.detail.a.href) })
    // Notas al pie: se abren en una hoja abajo, sin perder la página
    view.addEventListener('link', e => {
        const p = footnotes.handle(view.book, e)
        if (!p) { saltoDesde(); return }   // (un enlace a otra parte del libro: se puede volver)
        p.catch(err => { console.warn(err); saltoDesde(); view.goTo(e.detail.href) })
    })
    if (!st.title) $('#t-libro').textContent = (typeof view.book.metadata?.title === 'string' ? view.book.metadata.title : '') || ''
    applySettings()
    // Desde la ficha del libro se puede abrir directamente en un subrayado
    // La posición de la página original y la del texto adaptado no valen la una en la otra: en ese caso, por el porcentaje
    const buena = c => !c || (reflow ? c.includes('!') : (!esPDF || !c.includes('!')))
    let donde = st.goto && buena(st.goto) ? st.goto : (st.location && buena(st.location) ? st.location : null)
    if (!donde && (st.goto || st.location) && st.fraction > 0 && st.fraction < 1) donde = { fraction: st.fraction }
    if (!buena(st.goto)) st.goto = null
    if (!buena(st.location)) st.location = null
    try {
        await view.init({ lastLocation: donde || null, showTextStart: !donde })
    } catch (e) {
        // La posición guardada no encaja con este archivo (llegó de otro dispositivo con otra edición, o el archivo
        // cambió): en lugar de no abrir, se abre por el mismo porcentaje (o por el principio)
        if (!st.goto && !st.location) throw e
        console.warn('Lector: la posición guardada no vale en este archivo', e)
        paso = 'mostrar la primera página (sin la posición guardada)'
        if (st.fraction > 0 && st.fraction < 1) await view.goToFraction(st.fraction)
        else await view.renderer.next()
    }
    // PDF como texto: la posición guardada (de otro dispositivo, o de una versión anterior de Atrio que reconstruía el
    // texto algo distinto) tiene que caer donde dice su porcentaje; si cae lejos, manda el porcentaje
    if (reflow && !st.goto && typeof donde === 'string' && st.fraction > 0 && st.fraction < 1) {
        await new Promise(r => setTimeout(r, 60))
        const f = last?.fraction
        if (typeof f === 'number' && Math.abs(f - st.fraction) > 0.03) {
            console.warn('Lector: la posición guardada no cuadra con su porcentaje', f, st.fraction)
            try { await view.goToFraction(st.fraction) } catch (e) { }
        }
    }
    paso = 'listo'
    // Leyendo hacia abajo, los botones de volumen no pasan página (son el volumen de siempre)
    volumen()
    $('#carga').classList.add('fuera')
    if (avisoPDF) { aviso(avisoPDF); avisoPDF = '' }
    else if (reflow) {
        // La primera vez que se abre un PDF como texto, se dice cómo volver a la página original
        try {
            if (!localStorage.getItem('atrioPdfTexto')) {
                localStorage.setItem('atrioPdfTexto', '1')
                setTimeout(() => aviso('PDF adaptado como texto · Letra → Formato del PDF para ver la página original'), 2400)
            }
        } catch (e) { }
    }
    showChrome(true)
    setTimeout(() => showChrome(false), 1600)
}

/** Los botones de volumen pasan página si lo quieres, salvo leyendo hacia abajo o en voz alta (ahí son el volumen). */
function volumen() { try { A.volumeKeys?.(S.volPages !== false && !haciaAbajo() && !vozOn) } catch (e) { } }
/** PDF o cómic leyendo hacia abajo (las páginas una debajo de otra). */
const haciaAbajo = () => isPDF && view?.renderer?.localName === 'foliate-fxl-scroll'
/** Cambiar entre página a página y hacia abajo: se vuelve a abrir el libro en la misma página. */
async function reabrir() {
    const loc = last?.cfi
    closePanel()
    $('#carga').classList.remove('fuera')
    try { view.close() } catch (e) { }
    try { view.book?.destroy?.() } catch (e) { }
    view.remove()
    docs.clear()
    const fr = last?.fraction
    last = null; lastTurn = null
    if (loc) st.location = loc
    if (fr > 0) st.fraction = fr
    st.goto = null
    await open()
}

// ---------------------------------------------------------------- Ajustes de lectura
function css() {
    const cs = getComputedStyle(document.body)
    const fg = cs.getPropertyValue('--fg').trim(), bg = cs.getPropertyValue('--bg').trim(), accent = cs.getPropertyValue('--accent').trim()
    const dark = DARK_THEMES.includes(theme())
    const fam = { original: '', literata: 'AtrioLiterata, Georgia, serif', garamond: 'AtrioGaramond, Georgia, serif', atrio: 'AtrioSerif, Georgia, serif', serif: 'Georgia, "Noto Serif", serif', sans: 'system-ui, Roboto, "Noto Sans", sans-serif', facil: 'AtrioAtkinson, Verdana, sans-serif', mono: '"Roboto Mono", "Noto Sans Mono", monospace' }[S.font]
    const font = location.origin + '/assets/lector/fuentes/'
    const hy = S.hyphens ? 'auto' : 'manual'
    const para = [
        '',
        'p { margin-top: 0 !important; margin-bottom: .55em !important; }',
        'p { margin-top: 0 !important; margin-bottom: 1.1em !important; text-indent: 0 !important; }',
    ][S.paraSpace] || ''
    return `
@namespace epub "http://www.idpf.org/2007/ops";
@font-face { font-family: AtrioSerif; src: url("${font}display_regular.ttf"); font-weight: 400; }
@font-face { font-family: AtrioSerif; src: url("${font}display_semibold.ttf"); font-weight: 600 900; }
@font-face { font-family: AtrioSerif; src: url("${font}display_italic.ttf"); font-style: italic; }
@font-face { font-family: AtrioLiterata; src: url("${font}literata.ttf"); font-weight: 200 900; }
@font-face { font-family: AtrioLiterata; src: url("${font}literata_italic.ttf"); font-weight: 200 900; font-style: italic; }
@font-face { font-family: AtrioGaramond; src: url("${font}garamond.ttf"); font-weight: 400 800; }
@font-face { font-family: AtrioGaramond; src: url("${font}garamond_italic.ttf"); font-weight: 400 800; font-style: italic; }
@font-face { font-family: AtrioAtkinson; src: url("${font}atkinson_regular.ttf"); font-weight: 400; }
@font-face { font-family: AtrioAtkinson; src: url("${font}atkinson_bold.ttf"); font-weight: 700; }
@font-face { font-family: AtrioAtkinson; src: url("${font}atkinson_italic.ttf"); font-weight: 400; font-style: italic; }
@font-face { font-family: AtrioAtkinson; src: url("${font}atkinson_bolditalic.ttf"); font-weight: 700; font-style: italic; }
html { color: ${fg} !important; background: transparent !important; font-size: ${S.size}% !important; color-scheme: ${dark ? 'dark' : 'light'}; }
body { background: transparent !important; color: inherit !important; ${fam ? `font-family: ${fam} !important;` : ''} }
${fam ? 'body *:not(code):not(pre) { font-family: inherit !important; }' : ''}
p, li, blockquote, dd { line-height: ${S.spacing} !important; text-align: ${S.justify ? 'justify' : 'start'}; -webkit-hyphens: ${hy}; hyphens: ${hy}; widows: 2; orphans: 2; }
${para}
${S.weight && VARIABLES.includes(S.font) ? 'body, p, li, blockquote, dd { font-weight: ' + (S.font === 'garamond' ? 500 : 480) + '; }' : ''}
h1, h2, h3, h4, p, li, span, div { color: inherit !important; }
a:link, a:visited { color: ${accent} !important; }
img, svg, video { max-width: 100%; ${dark ? 'filter: brightness(.86);' : ''} }
pre { white-space: pre-wrap !important; }
aside[epub|type~="footnote"], aside[epub|type~="endnote"], aside[epub|type~="rearnote"] { display: none; }
html, body, body * { -webkit-user-select: none !important; user-select: none !important; -webkit-touch-callout: none !important; }
html.atrio-reflow h2 { text-align: center !important; font-size: 1.3em; line-height: 1.25; margin: 2.4em 0 1.3em; -webkit-hyphens: manual; hyphens: manual; font-weight: 700; }
html.atrio-reflow p { text-indent: 1.4em; margin: 0; }
html.atrio-reflow h2 + p, html.atrio-reflow p.sep + p { text-indent: 0; }
html.atrio-reflow p.sep { text-align: center !important; text-indent: 0; margin: 1.4em 0; letter-spacing: .3em; }
html.atrio-reflow.bloque p { text-indent: 0; margin: 0 0 .75em; }
html.atrio-reflow p.centro { text-align: center !important; text-indent: 0; margin: .6em 0; }
html.atrio-reflow p.derecha { text-align: right !important; text-indent: 0; margin: .4em 0; }
html.atrio-reflow p.verso { text-indent: 0; text-align: left !important; margin: .5em 0; }
html.atrio-reflow p.sep.esp { margin: .9em 0; letter-spacing: 0; }
html.atrio-reflow p.nota { font-size: .8em; text-indent: 0; opacity: .85; margin: .6em 0; }
::highlight(atrio) { background-color: ${fg}; color: ${bg}; }`
}

function applySettings() {
    sinDensidad()
    lastTheme = theme()
    document.body.dataset.theme = lastTheme
    // La página del lector, con el mismo esquema de color que el libro: si no coinciden, Chromium pinta el libro
    // (va en un iframe) sobre un fondo opaco del modo oscuro (#121212), y con Negro se veía una caja gris
    document.documentElement.style.colorScheme = DARK_THEMES.includes(lastTheme) ? 'dark' : 'light'
    document.documentElement.style.setProperty('--accent', DARK_THEMES.includes(lastTheme) ? ACCENT.dark : ACCENT.light)
    document.body.classList.toggle('sin-estado', !S.status)
    A.setTheme(lastTheme)
    if (!view?.renderer) return
    if (isPDF) {
        view.renderer.setAttribute('zoom', S.zoom)
    } else {
        view.renderer.setStyles?.(css())
        view.renderer.setAttribute('flow', S.flow)
        view.renderer.setAttribute('gap', { s: '4%', m: '7%', l: '11%' }[S.margin])
        // Arriba y abajo: lo que ocupen la cámara y las barras del móvil, más el margen elegido
        const rs = getComputedStyle(document.documentElement)
        const safe = Math.max(parseFloat(rs.getPropertyValue('--safe-top')) || 0, parseFloat(rs.getPropertyValue('--safe-bottom')) || 0)
        view.renderer.setAttribute('margin', Math.round(safe + { s: 26, m: 42, l: 62 }[S.margin]) + 'px')
        view.renderer.setAttribute('max-inline-size', '760px')
        // Dos columnas solo si lo pides y la pantalla es ancha (móvil en horizontal o tableta)
        view.renderer.setAttribute('max-column-count', S.columns ? '2' : '1')
        if (S.animated) view.renderer.setAttribute('animated', '')
        else view.renderer.removeAttribute('animated')
    }
    A.setBrightness(brilloSesion)
    despierta()
    volumen()
    repaintAll()
    let w = $('#calido')
    if (!w) { w = document.createElement('div'); w.id = 'calido'; document.body.append(w) }
    Object.assign(w.style, { position: 'fixed', inset: '0', pointerEvents: 'none', zIndex: 15, background: `rgba(255,120,20,${S.warm * 0.28})`, mixBlendMode: 'multiply' })
}

/** Al soltar el dedo: el brillo va al del móvil (si Android deja), o se queda solo mientras lees. */
function brilloAlMovil(v) {
    if (!(v >= 0)) return
    let r = 'permiso'
    try { r = A.brilloMovil?.(v) || 'permiso' } catch (e) { }
    if (r === 'ok' || r === 'auto') {
        brilloSesion = -1
        if (r === 'auto') aviso('Brillo del móvil al ' + Math.round(v * 100) + ' % · el brillo automático queda apagado')
        return
    }
    brilloSesion = v
    if (!brilloAvisado) {
        brilloAvisado = true
        setTimeout(() => aviso('Para cambiar el brillo del móvil, Android pide permiso', () => { try { A.pedirPermisoBrillo?.() } catch (e) { } }, 'Permitir'), 900)
    }
}

// ---------------------------------------------------------------- La hora y la batería (arriba)
// La hora se repinta justo al cambiar de minuto (sin sondear) y se para con la pantalla apagada
let relojT = 0
function pintaHora() {
    const e = $('#r-hora'); if (!e) return
    const d = new Date()
    e.textContent = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
}
function tickReloj() {
    clearTimeout(relojT)
    if (document.hidden) return
    pintaHora()
    relojT = setTimeout(tickReloj, 60000 - (Date.now() % 60000) + 80)
}
tickReloj()
document.addEventListener('visibilitychange', tickReloj)
/** La app avisa de la batería (y si está cargando). */
window.atrioBateria = (pct, carga) => { const e = $('#r-bat'); if (e) e.textContent = (carga ? '⚡ ' : '') + pct + ' %' }

// ---------------------------------------------------------------- Posición
const saveProgress = debounce(() => {
    if (!last) return
    A.saveProgress(last.cfi || '', last.fraction || 0, label())
}, 700)

// ---------------------------------------------------------------- Tu ritmo: tiempo leído y lo que te queda
// foliate calcula «unidades de tiempo» de 1600 bytes de texto (un minuto a ritmo medio). Midiendo cuánto avanzas de
// verdad mientras lees, el tiempo que queda se ajusta a tu velocidad.
const UNIT = 1600
const LOC = 1500
let lastTurn = null           // { t, loc }
const today = () => { const d = new Date(); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') }
const saveStats = debounce(() => { try { A.saveStats(JSON.stringify(stats)) } catch (e) { } }, 4000)
const saveSpeed = debounce(() => saveSettings(), 8000)
// El tiempo leído: corre mientras lees y se para solo si pasan más de 5 minutos sin mover la página (o tocarla),
// o si sales del lector, se apaga la pantalla o vas a otra app (lo avisa la app con atrioPausa). Se cuenta hasta el
// último movimiento más esos 5 minutos como mucho, también la última página antes de cerrar.
const PARADO = 5 * 60000
const ahora = () => (window.__reloj ? window.__reloj() : Date.now())
let activo = ahora(), enPausa = false
function sumar(now) {
    if (enPausa) return
    const add = Math.max(0, Math.min(now - activo, PARADO))
    activo = now
    if (add < 1000) return
    const mio = stats.dev[DEV]
    mio.total += add
    const d = today()
    mio.days[d] = (mio.days[d] || 0) + add
    recontar()
    saveStats()
    metaHoy()
}
// Tu objetivo de lectura del día (minutos, en Perfil): al cumplirlo mientras lees, un aviso (una vez)
const META = Number(st.meta) || 0
let metaDia = null
function metaHoy() {
    if (!META) return
    const d = today(), hecho = (stats.days[d] || 0) >= META * 60000
    if (metaDia === null || metaDia.d !== d) { metaDia = { d, ok: hecho }; return }   // (lo cumplido antes de abrir el libro no avisa)
    if (hecho && !metaDia.ok) { metaDia.ok = true; aviso('Objetivo de hoy cumplido: ' + META + ' min') }
}
metaHoy()
/** Cualquier movimiento o toque: lo de antes cuenta (hasta 5 min) y el contador sigue. */
function mueve() { sumar(ahora()); despierta() }
// La pantalla encendida mientras lees; si pasan 10 minutos sin tocar nada (te has dormido leyendo), se deja apagar
// como siempre y vuelve a quedarse encendida en cuanto la tocas
const DESPIERTA = 10 * 60000
let despiertaT = 0, despiertaOn = null
function despierta() {
    const on = !!S.keepOn
    if (on !== despiertaOn) { despiertaOn = on; try { A.keepScreenOn(on) } catch (e) { } }
    clearTimeout(despiertaT)
    if (on) despiertaT = setTimeout(() => { despiertaOn = false; try { A.keepScreenOn(false) } catch (e) { } }, DESPIERTA)
}
window.atrioPausa = on => {
    if (on) { sumar(ahora()); enPausa = true; try { A.saveStats(JSON.stringify(stats)) } catch (e) { } }
    else if (enPausa) { enPausa = false; activo = ahora() }
}
document.addEventListener('visibilitychange', () => window.atrioPausa(document.hidden))
window.addEventListener('pagehide', () => window.atrioPausa(true))
document.addEventListener('pointerdown', mueve, true)
function track(detail) {
    const now = ahora()
    const loc = detail.location?.current
    mueve()
    if (lastTurn && typeof loc === 'number') {
        const dt = now - lastTurn.t
        // Velocidad: lo avanzado (en bytes) entre lo tardado, si es un paso normal de página
        const dloc = loc - lastTurn.loc
        if (!isPDF && dloc > 0 && dloc <= 6 && dt > 4000 && dt < PARADO) {
            const bpm = dloc * LOC / (dt / 60000)
            if (bpm > 200 && bpm < 20000) { S.speed = S.speed ? Math.round(S.speed * 0.85 + bpm * 0.15) : Math.round(bpm); saveSpeed() }
        }
    }
    if (typeof loc === 'number') lastTurn = { t: now, loc }
}
const durTxt = min => {
    if (!isFinite(min) || min < 0) return ''
    if (min < 1) return 'menos de 1 min'
    if (min < 60) return Math.round(min) + ' min'
    const h = Math.floor(min / 60), m = Math.round(min % 60)
    return h + ' h' + (m ? ' ' + m + ' min' : '')
}
const ms2txt = ms => durTxt(ms / 60000) || '0 min'
/** Minutos que te quedan (del capítulo y del libro), a tu ritmo si ya se conoce. */
function remaining(detail) {
    const tm = detail?.time
    if (!tm) return null
    const k = S.speed ? UNIT / S.speed : 1
    return { section: tm.section * k, total: tm.total * k }
}
// Páginas: las del capítulo, contadas tal y como está maquetado ahora (letra, tipo, márgenes, interlineado,
// columnas); las del libro, estimadas con lo que cabe en una pantalla (bytes por página) en los capítulos ya
// maquetados con estos ajustes. Al cambiar un ajuste se vuelve a medir desde cero.
let densidad = new Map()   // índice del capítulo → [bytes, páginas]
const sinDensidad = () => { densidad = new Map() }
function paginas(detail) {
    const r = view?.renderer, secs = view?.book?.sections
    if (!r || !secs || isPDF) return null
    const i = detail.section?.current ?? detail.index ?? 0
    let left, total
    if (r.scrolled) {
        const size = r.size, vs = r.viewSize
        if (!(size > 0 && vs > 0)) return null
        total = Math.max(1, Math.ceil(vs / size)); left = Math.max(0, Math.ceil((vs - r.end) / size))
    } else {
        if (!(r.pages > 2)) return null
        total = r.pages - 2; left = Math.max(0, total - r.page)
    }
    const bytes = secs[i]?.linear != 'no' ? (secs[i]?.size || 0) : 0
    if (bytes > 0 && total > 0) densidad.set(i, [bytes, total])
    let b = 0, n = 0
    for (const [x, y] of densidad.values()) { b += x; n += y }
    let libro = null
    if (b > 0 && n > 0) {
        const after = secs.slice(i + 1).reduce((a, s) => a + (s.linear != 'no' && s.size > 0 ? s.size : 0), 0)
        libro = left + Math.round(after / (b / n))
    }
    return { cap: left, libro }
}
const pags = n => n === 1 ? '1 pág.' : `${n} págs.`
function drawStatus(detail) {
    const el = $('#estado')
    if (!el || !detail) return
    let left = '', right = pct(detail.fraction)
    if (isPDF) {
        const n = view?.book?.sections?.length || 0
        const i = (detail.section?.current ?? detail.index ?? 0) + 1
        left = n ? `Página ${i} de ${n}` : ''
        if (n) right = `quedan ${pags(n - i)} · ${right}`
    } else {
        const r = remaining(detail)
        const p = paginas(detail)
        const t = r && r.section >= 1 ? durTxt(r.section) : ''
        if (p && p.cap > 0) left = `${pags(p.cap)}${t ? ' · ' + t : ''} para acabar el capítulo`
        else if (p) left = 'Última página del capítulo'
        else if (r) left = r.section >= 1 ? `${t} para acabar el capítulo` : 'Última página del capítulo'
        if (p?.libro != null) right = `≈ ${pags(p.libro)} · ${right}`
    }
    $('#e-izq').textContent = left
    $('#e-der').textContent = right
}

function onRelocate({ detail }) {
    last = detail
    track(detail)
    pasoVuelta()
    pasoBusqueda()
    drawStatus(detail)
    $('#b-marca-cinta')?.classList.toggle('oculta', !currentMark())
    $('#p-barra').value = detail.fraction ?? 0
    $('#p-pct').textContent = pct(detail.fraction)
    const pg = detail.pageItem?.label ? 'pág. ' + detail.pageItem.label
        : detail.location ? `${detail.location.current + 1} / ${detail.location.total}` : ''
    $('#p-pag').textContent = pg
    $('#t-cap').textContent = label()
    $('#b-marca').classList.toggle('on', !!currentMark())
    saveProgress()
}
$('#p-barra').addEventListener('pointerdown', () => { if (!vuelta || vuelta.pasos > 0) saltoDesde(); arrastra = true })
$('#p-barra').addEventListener('change', () => { arrastra = false })
$('#p-barra').addEventListener('input', e => view?.goToFraction(parseFloat(e.target.value)))

/** El capítulo (o la página del PDF) que se ve. */
const idxAhora = () => last?.section?.current ?? last?.index ?? null
/** El marcador de la página que se ve: el que cae dentro de lo que hay en pantalla (los de antes, por el porcentaje). */
function currentMark() {
    if (!last) return null
    const idx = idxAhora()
    const cerca = m => Math.abs((m.fraction || 0) - (last.fraction || 0)) < 0.0025
    return marks.find(m => {
        if (m.index == null || idx == null) return cerca(m)
        if (m.index !== idx) return false
        if (isPDF) return true
        const doc = liveDoc(idx), r = doc && rangeOf(doc, m.value), vis = last.range
        if (!r || !vis || vis.startContainer?.ownerDocument !== doc) return cerca(m)
        try { return vis.comparePoint(r.startContainer, r.startOffset) === 0 } catch (e) { return cerca(m) }
    })
}

// ---------------------------------------------------------------- Volver a donde estabas
// Al saltar (índice, búsqueda, notas, marcadores, «Ir a», la barra o un enlace) se ofrece volver a la página de antes,
// hasta que pasas unas cuantas páginas desde el sitio nuevo
let vuelta = null      // { cfi, fraction, pasos }
function saltoDesde() {
    if (!last?.cfi) return
    vuelta = { cfi: last.cfi, fraction: last.fraction || 0, pasos: -1 }
    pintaVuelta()
}
let arrastra = false
function pasoVuelta() {
    if (!vuelta || arrastra) return
    if (++vuelta.pasos > 5) { vuelta = null; pintaVuelta() }
}
function pintaVuelta() {
    let b = $('#volver')
    if (!b) {
        b = document.createElement('button'); b.id = 'volver'; b.className = 'volver oculta'
        b.onclick = () => { const v = vuelta; vuelta = null; pintaVuelta(); if (v) view.goTo(v.cfi).catch(() => view.goToFraction(v.fraction)) }
        document.body.append(b)
    }
    b.classList.toggle('oculta', !vuelta)
    if (vuelta) b.textContent = '← Volver a donde estabas (' + pct(vuelta.fraction) + ')'
}
/** Ir a un sitio del libro desde un panel: con la vuelta preparada. */
function irA(target) { saltoDesde(); closePanel(); return view.goTo(target) }

// ---------------------------------------------------------------- Toques
// La app avisa cuando sabe cuánto ocupan las barras del móvil
window.atrioInsets = () => applySettings()

function onLoad({ detail: { doc, index } }) {
    // Para las pruebas automáticas (el libro va en un shadow DOM cerrado)
    ;(window.__docs ||= []).push({ doc, index })
    window.__view = view
    docs.set(index, doc)
    // Nada de la selección del navegador (con sus asas): mantener pulsado lo lleva el lector (también en PDF)
    const stl = doc.createElement('style')
    stl.textContent = 'html,body,body *{-webkit-user-select:none!important;user-select:none!important;-webkit-touch-callout:none!important}'
    ;(doc.head || doc.documentElement).append(stl)
    const win = doc.defaultView
    // Tocar la página también cuenta como que sigues leyendo (el contador de tiempo no se para)
    doc.addEventListener("pointerdown", mueve, true)

    // Mantener pulsado (sin mover el dedo): la palabra de debajo. Mientras, la página NO se mueve.
    let t0 = null, holding = false, holdTimer = 0, swiped = false, held = false
    // Brillo: deslizar arriba o abajo por el borde izquierdo de la pantalla (como en los lectores de tinta)
    let edge = false, glow = null, glowY = 0, glowV = -1
    // Pellizcar con dos dedos (libros de texto): la letra más grande o más pequeña
    let pinch = null
    const dist2 = ts => Math.hypot(ts[0].clientX - ts[1].clientX, ts[0].clientY - ts[1].clientY)
    const topX = x => x + (doc.defaultView.frameElement?.getBoundingClientRect().left || 0)
    win.addEventListener('touchstart', e => {
        if (e.touches.length === 2 && !isPDF) {
            clearTimeout(holdTimer); holding = false; edge = false; glow = null
            pinch = { d0: dist2(e.touches) || 1, size0: S.size, size: S.size }
            e.stopPropagation(); return
        }
        const t = e.touches[0]
        t0 = t ? { x: t.clientX, y: t.clientY, time: e.timeStamp } : null
        swiped = false; held = false; holding = false
        // (leyendo hacia abajo, el borde también sirve para desplazar: ahí no se toca el brillo; con el menú abierto,
        // deslizar hacia abajo es para cerrarlo. La franja es estrecha: en la tableta, un 10 % eran 12 cm de pantalla)
        edge = !!t && e.touches.length === 1 && topX(t.clientX) < Math.min(32, window.innerWidth * 0.06) && !haciaAbajo() && !chromeOn
        glow = null
        clearTimeout(holdTimer)
        if (t && e.touches.length === 1) holdTimer = setTimeout(() => {
            holding = true; held = true
            onHold(doc, index, t0.x, t0.y)
        }, 450)
    }, { capture: true, passive: true })
    win.addEventListener('touchmove', e => {
        if (pinch) {
            if (e.touches.length === 2) {
                const k = dist2(e.touches) / pinch.d0
                const n = Math.min(240, Math.max(60, Math.round(pinch.size0 * Math.pow(k, 0.8) / 10) * 10))
                if (n !== pinch.size) { pinch.size = n; aviso('Letra ' + n + ' %') }
            }
            e.stopPropagation(); return
        }
        const t = e.touches[0]
        if (t0 && t && Math.hypot(t.clientX - t0.x, t.clientY - t0.y) > 12 && !holding) clearTimeout(holdTimer)
        if (edge && t0 && t && !holding) {
            const dx = t.clientX - t0.x, dy0 = t.clientY - t0.y
            // empieza en el brillo que hay de verdad (en automático, el del móvil), sin saltos: cuenta desde aquí
            if (glow === null && Math.abs(dy0) > 24 && Math.abs(dy0) > Math.abs(dx) * 2) { glow = brilloAhora(); glowY = t.clientY }
            if (glow !== null) {
                const dy = t.clientY - glowY
                glowV = Math.round(Math.min(1, Math.max(0, glow - dy / (window.innerHeight * 0.7))) * 100) / 100
                A.setBrightness(glowV)    // (mientras deslizas, al momento; al soltar, al móvil)
                aviso('Brillo ' + Math.round(glowV * 100) + ' %')
                e.stopPropagation()
            }
        }
        if (holding) e.stopPropagation()
    }, { capture: true, passive: true })
    win.addEventListener('touchend', e => {
        clearTimeout(holdTimer)
        if (pinch) {
            if (e.touches.length) return
            const p = pinch; pinch = null; swiped = true; holding = false
            if (p.size !== S.size) { S.size = p.size; applySettings(); saveSettings() }
            return
        }
        const t = e.changedTouches[0]
        // (el touchend sí le llega a foliate: así la página vuelve a su sitio si se llegó a mover un poco al empezar)
        if (glow !== null) { glow = null; edge = false; swiped = true; holding = false; brilloAlMovil(glowV); return }
        // Deslizar hacia arriba abre el menú (y hacia abajo lo cierra). Leyendo hacia abajo no: ahí deslizar mueve el libro
        if (!haciaAbajo() && t0 && t && !holding) {
            const dx = t.clientX - t0.x, dy = t.clientY - t0.y
            if (Math.abs(dy) > 40 && Math.abs(dy) > Math.abs(dx) * 1.5 && e.timeStamp - t0.time < 1200) {
                swiped = true
                if (dy < 0 && !chromeOn) showChrome(true)
                else if (dy > 0 && chromeOn) showChrome(false)
                holding = false
                return
            }
        }
        // PDF: se pasa de página deslizando (foliate no lo hace solo en los PDF)
        // (leyendo hacia abajo no se pasa de página deslizando de lado: solo se desplaza)
        if (isPDF && !haciaAbajo() && t0 && t && !holding) {
            const dx = t.clientX - t0.x, dy = t.clientY - t0.y
            // Un desliz corto y suave basta (como en las páginas que no son PDF)
            if (Math.abs(dx) > 16 && Math.abs(dx) > Math.abs(dy) * 1.2 && e.timeStamp - t0.time < 1500) {
                swiped = true
                if (dx < 0) view.goRight(); else view.goLeft()
                if (chromeOn) showChrome(false)
            }
        }
        if (holding) e.stopPropagation()
        holding = false
    }, { capture: true, passive: true })
    // El menú contextual del navegador (mantener pulsado), fuera
    doc.addEventListener('contextmenu', e => e.preventDefault())

    // Los subrayados de este capítulo (o página). En PDF la página se dibuja después: se repinta cuando aparece.
    paint(index)
    if (isPDF) {
        // Al hacer zoom pdf.js vuelve a crear el texto de la página: los tramos de antes ya no valen
        const again = debounce(() => { rangeCache.delete(doc); paint(index); pageColor(doc) }, 120)
        const mo = new MutationObserver(again)
        for (const el of doc.querySelectorAll('#canvas, .textLayer')) mo.observe(el, { childList: true })
        win.addEventListener('resize', again)
    }

    doc.addEventListener('click', e => {
        if (e.target.closest?.('a[href]')) return  // (los <a id> sin enlace, que envuelven párrafos en muchos EPUB, no cuentan)
        if (swiped) { swiped = false; return }
        if (held) { held = false; return }
        // Una imagen: a pantalla completa, para ampliarla
        const img = !isPDF && e.target.closest?.('img, image')
        if (img && $('#palabra').classList.contains('oculta') && !selection && !$('#panel').classList.contains('on')) {
            const src = img.currentSrc || img.src || img.getAttribute('href') || img.getAttributeNS('http://www.w3.org/1999/xlink', 'href')
            if (src && (img.naturalWidth || 200) >= 120) { showImage(typeof src === 'string' ? src : src.baseVal); return }
        }
        const hit = noteAt(doc, index, e.clientX, e.clientY)
        setTimeout(() => {
            if (!$('#palabra').classList.contains('oculta')) { hideWord(); return }
            if (selection) { hideSel(); return }
            if ($('#panel').classList.contains('on')) { closePanel(); return }
            if (pend) return
            // Tocar un subrayado: su nota, copiarlo, compartirlo o quitarlo
            if (hit) { showSel({ mode: 'edit', note: hit.note, y: yOf(doc, hit.range) }); return }
            // PDF: tocar nunca pasa de página (las páginas, deslizando)
            if (S.flow === 'paginated' && !isPDF) {
                const fr = doc.defaultView.frameElement?.getBoundingClientRect()
                const x = e.clientX + (fr ? fr.left : 0)
                const w = window.innerWidth
                const unaMano = S.tapZones === 'adelante'
                if (x < w * 0.27) { if (unaMano) view.next(); else view.goLeft(); if (chromeOn) showChrome(false); return }
                if (x > w * 0.73) { if (unaMano) view.next(); else view.goRight(); if (chromeOn) showChrome(false); return }
            }
            // El menú: tocando en el centro de verdad (la zona del medio, ni bordes ni arriba ni abajo); con el menú
            // abierto, tocar en cualquier sitio lo cierra
            if (chromeOn) { showChrome(false); return }
            const fr2 = doc.defaultView.frameElement?.getBoundingClientRect()
            const cx = e.clientX + (fr2 ? fr2.left : 0), cy = e.clientY + (fr2 ? fr2.top : 0)
            if (cx > window.innerWidth * 0.3 && cx < window.innerWidth * 0.7 && cy > window.innerHeight * 0.25 && cy < window.innerHeight * 0.75) showChrome(true)
        }, 40)
    })
}

// ---------------------------------------------------------------- Palabras (mantener pulsado)
const LETRA = /[\p{L}\p{N}\p{M}’'·-]/u
/** La palabra que hay en (x, y) del documento: recorre el texto del bloque y mira dónde cae el dedo. */
function wordAt(doc, x, y) {
    const el = doc.elementFromPoint(x, y)
    if (!el) return null
    const block = el.closest?.('p,li,dd,dt,blockquote,h1,h2,h3,h4,h5,h6,td,th,figcaption,span,div') || el
    const walker = doc.createTreeWalker(block, NodeFilter.SHOW_TEXT)
    let best = null, bestD = 1e9, n
    while ((n = walker.nextNode())) {
        const t = n.textContent
        const re = /[\p{L}\p{N}\p{M}’'·-]+/gu
        let m
        while ((m = re.exec(t))) {
            const r = doc.createRange(); r.setStart(n, m.index); r.setEnd(n, m.index + m[0].length)
            for (const b of r.getClientRects()) {
                if (x >= b.left - 2 && x <= b.right + 2 && y >= b.top - 4 && y <= b.bottom + 4) return trimWord(r)
                const d = Math.hypot(Math.max(b.left - x, 0, x - b.right), Math.max(b.top - y, 0, y - b.bottom))
                if (d < bestD) { bestD = d; best = r }
            }
        }
    }
    return best && bestD < 18 ? trimWord(best) : null
}
/** Sin guiones ni apóstrofos sueltos al principio o al final. */
function trimWord(r) {
    const t = r.toString()
    const a = t.length - t.replace(/^[’'·-]+/, '').length, b = t.length - t.replace(/[’'·-]+$/, '').length
    if (a) r.setStart(r.startContainer, r.startOffset + a)
    if (b && t.length - a - b > 0) r.setEnd(r.endContainer, r.endOffset - b)
    return r
}
/** La frase en la que está la palabra (para saber el idioma y traducirla con su contexto). */
function sentenceOf(r) {
    const block = r.startContainer.parentElement?.closest?.('p,li,dd,blockquote,h1,h2,h3,h4,td,div') || r.startContainer.parentElement
    const all = (block?.textContent || '').replace(/\s+/g, ' ')
    const w = r.toString()
    const i = all.indexOf(w)
    if (i < 0) return all.slice(0, 300)
    const s = Math.max(all.lastIndexOf('. ', i), all.lastIndexOf('? ', i), all.lastIndexOf('! ', i))
    let e = all.slice(i).search(/[.?!](\s|$)/)
    e = e < 0 ? all.length : i + e + 1
    return all.slice(s < 0 ? 0 : s + 2, e).trim().slice(0, 400)
}

let pend = null      // subrayado en marcha: { doc, index, start }
let marca = null     // la palabra marcada ahora mismo (no se guarda): { index, range }
let palabra = null   // { doc, index, range, word, sentence, lang }
function mark(index, range) {
    const old = marca
    marca = { index, range: range.cloneRange() }
    if (old && old.index !== index) paint(old.index)
    paint(index)
}
function unmark() {
    const old = marca
    marca = null
    if (old) paint(old.index)
}

// ---------------------------------------------------------------- Subrayados (como en un Kindle)
// El texto subrayado se invierte: el fondo, del color de la letra; la letra, del color del fondo.
// EPUB: CSS Custom Highlight API (::highlight). PDF: un rectángulo blanco en modo «diferencia» encima de la página,
// que invierte lo que haya debajo (y con los temas oscuros, que ya invierten la página, vuelve a quedar al revés).
const docs = new Map()          // índice del capítulo/página → su documento
const rangeCache = new WeakMap() // documento → Map(cfi → Range)
function rangeOf(doc, value) {
    let m = rangeCache.get(doc)
    if (!m) rangeCache.set(doc, m = new Map())
    if (m.has(value)) return m.get(value)
    let r = null
    try { r = view.resolveCFI(value)?.anchor?.(doc) || null } catch (e) { }
    if (r && typeof r.getClientRects !== 'function') r = null
    // En PDF el texto de la página llega después: si aún no está, se vuelve a intentar en el siguiente repintado
    if (r) m.set(value, r)
    return r
}
function rangesFor(index, doc) {
    const out = []
    for (const n of notes) if (n.index === index) { const r = rangeOf(doc, n.value); if (r) out.push({ note: n, range: r }) }
    return out
}
function liveDoc(index) {
    const d = docs.get(index)
    if (!d || !d.defaultView) { docs.delete(index); return null }
    return d
}
function paint(index) {
    const doc = liveDoc(index)
    if (!doc) return
    const ranges = rangesFor(index, doc).map(x => x.range)
    if (marca?.index === index) ranges.push(marca.range)
    const win = doc.defaultView
    if (!isPDF && win.CSS?.highlights && win.Highlight) {
        win.CSS.highlights.set('atrio', new win.Highlight(...ranges))
        return
    }
    // PDF (o un WebView muy viejo sin ::highlight): rectángulos encima del texto
    let box = doc.getElementById('atrio-sub')
    if (!box) {
        box = doc.createElement('div')
        box.id = 'atrio-sub'
        box.style.cssText = 'position:absolute;left:0;top:0;width:100px;height:0;overflow:visible;pointer-events:none;z-index:5'
        ;(doc.body || doc.documentElement).append(box)
    }
    box.replaceChildren()
    // La mezcla va en el contenedor: con z-index es un grupo aislado y, dentro, los rectángulos no verían la página
    box.style.mixBlendMode = isPDF ? 'difference' : 'normal'
    if (!ranges.length) return
    const base = box.getBoundingClientRect()
    const k = base.width / 100 || 1
    const fill = isPDF ? 'background:#fff'
        : `background:${getComputedStyle(document.body).getPropertyValue('--fg')};opacity:.32`
    for (const r of ranges) for (const b of r.getClientRects()) {
        if (b.width < 1 || b.height < 1) continue
        const d = doc.createElement('div')
        d.style.cssText = `position:absolute;${fill};left:${(b.left - base.left) / k}px;top:${(b.top - base.top) / k}px;width:${b.width / k}px;height:${b.height / k}px`
        box.append(d)
    }
}
function repaintAll() { for (const i of [...docs.keys()]) paint(i) }
/** El subrayado que hay debajo del dedo, si hay alguno. */
function noteAt(doc, index, x, y) {
    for (const h of rangesFor(index, doc))
        for (const b of h.range.getClientRects())
            if (x >= b.left - 3 && x <= b.right + 3 && y >= b.top - 3 && y <= b.bottom + 3) return h
    return null
}
// PDF: sin bandas arriba y abajo; lo que sobra alrededor de la página, del color del papel de la página
function pageColor(doc) {
    // Leyendo hacia abajo, entre página y página: el blanco del papel (con los temas oscuros, invertido)
    if (haciaAbajo()) { view.style.background = ''; return }
    const c = doc.querySelector('#canvas canvas')
    if (!c || !c.width || !c.height) return
    try {
        const ctx = c.getContext('2d')
        const w = c.width, h = c.height
        const pts = [[2, 2], [w - 3, 2], [2, h - 3], [w - 3, h - 3], [w >> 1, 2], [w >> 1, h - 3]]
        const px = pts.map(([x, y]) => ctx.getImageData(x, y, 1, 1).data)
        // El color que más se repite en los bordes (por si en una esquina hay un dibujo)
        const key = d => [d[0] >> 3, d[1] >> 3, d[2] >> 3].join()
        const count = {}
        for (const d of px) count[key(d)] = (count[key(d)] || 0) + 1
        const best = px.reduce((a, d) => count[key(d)] > count[key(a)] ? d : a)
        if (best[3] < 200) return
        view.style.background = `rgb(${best[0]},${best[1]},${best[2]})`
    } catch (e) { }
}
function yOf(doc, r) {
    const fr = doc.defaultView.frameElement?.getBoundingClientRect()
    return (fr ? fr.top : 0) + r.getBoundingClientRect().top
}

function onHold(doc, index, x, y) {
    const w = wordAt(doc, x, y)
    if (!w) return
    A.buzz()
    if (pend) {
        if (pend.doc !== doc) { aviso('El subrayado tiene que acabar en el mismo capítulo'); return }
        finishRange(doc, index, w)
        return
    }
    showWord(doc, index, w)
}

let lookupN = 0
const results = {}
window.atrioResult = (id, r) => { results[id]?.(r); delete results[id] }
function ask(fn, ...args) {
    const id = 'r' + (++lookupN)
    return new Promise(res => { results[id] = res; A[fn](...args, id) })
}

async function showWord(doc, index, range) {
    hideSel(); closePanel(); showChrome(false)
    const word = range.toString().trim()
    const sentence = sentenceOf(range)
    palabra = { doc, index, range: range.cloneRange(), word, sentence, lang: null }
    await mark(index, range)
    const box = $('#palabra')
    $('#pal-w').textContent = word
    $('#pal-lang').textContent = ''
    $('#pal-def').innerHTML = '<span class="gris">Buscando el significado…</span>'
    $('#pal-tr').classList.add('oculta')
    $('#pal-trad').classList.add('oculta')
    box.classList.toggle('arriba', yOf(doc, range) > window.innerHeight * 0.5)
    box.classList.remove('oculta')
    const mine = palabra
    const r = await ask('lookup', word, sentence)
    if (palabra !== mine) return
    palabra.lang = r.lang
    if (r.lang && r.lang !== 'es' && r.lang !== 'und') {
        $('#pal-lang').textContent = r.langName || r.lang
        $('#pal-trad').textContent = 'Traducir del ' + (r.langName || r.lang).toLowerCase() + ' al español'
        $('#pal-trad').classList.remove('oculta')
    }
    $('#pal-def').innerHTML = r.defs?.length
        ? (r.nota ? `<div class="gris" style="margin-bottom:4px">${esc(r.nota)}:</div>`
            : r.lemma ? `<div class="gris" style="margin-bottom:4px">De «${esc(r.lemma)}»:</div>` : '') + `<ol>${r.defs.map(d => `<li>${esc(d)}</li>`).join('')}</ol>`
        : `<span class="gris">${esc(r.msg || 'No encuentro su significado.')}</span>`
}
function hideWord() {
    $('#palabra').classList.add('oculta')
    palabra = null
    if (!pend) unmark()
}
$('#pal-trad').onclick = async () => {
    const p = palabra
    if (!p) return
    const tr = $('#pal-tr')
    tr.classList.remove('oculta')
    tr.innerHTML = '<span class="gris">Traduciendo… (la primera vez se descarga el traductor de este idioma, unos 30 MB)</span>'
    const r = await ask('translate', p.word, p.sentence, p.lang || 'und')
    if (palabra !== p) return
    tr.innerHTML = r.word
        ? `<b>${esc(r.word)}</b>${r.sentence ? `<div style="margin-top:6px">${esc(r.sentence)}</div>` : ''}`
        : `<span class="gris">${esc(r.msg || 'No se ha podido traducir.')}</span>`
}
$('#pal-rae').onclick = () => { if (palabra) A.openUrl('https://dle.rae.es/' + encodeURIComponent(palabra.word.toLowerCase())) }
$('#pal-wik').onclick = () => { if (palabra) A.openUrl('https://es.wiktionary.org/wiki/' + encodeURIComponent(palabra.word.toLowerCase())) }
$('#p-copiar').onclick = () => { if (palabra) { A.copy(palabra.word); aviso('Copiado') } hideWord() }
$('#p-compartir').onclick = () => { if (palabra) A.share(`«${palabra.sentence}»\n— ${st.title || ''}`); hideWord() }
// Nota sobre esta palabra: se subraya y se escribe la nota
$('#p-nota').onclick = () => {
    const p = palabra
    if (!p) return
    $('#palabra').classList.add('oculta'); palabra = null
    unmark()
    const n = addNote(p.doc, p.index, p.range)
    if (n) editNote(n)
}
// Subrayar: esta palabra es el principio; falta mantener pulsada la última
$('#p-subrayar').onclick = () => {
    const p = palabra
    if (!p) return
    $('#palabra').classList.add('oculta'); palabra = null
    pend = { doc: p.doc, index: p.index, start: p.range }
    $('#pend').classList.remove('oculta')
}
$('#pend-no').onclick = () => { pend = null; $('#pend').classList.add('oculta'); unmark() }
$('#pend-una').onclick = () => { const p = pend; if (p) finishRange(p.doc, p.index, p.start) }

function finishRange(doc, index, end) {
    const start = pend.start
    pend = null
    $('#pend').classList.add('oculta')
    unmark()
    // Da igual el orden: de la primera palabra a la última
    const before = end.compareBoundaryPoints(Range.START_TO_START, start) < 0
    const a = before ? end : start, b = before ? start : end
    const r = doc.createRange()
    r.setStart(a.startContainer, a.startOffset)
    r.setEnd(b.endContainer, b.endOffset)
    const n = addNote(doc, index, r)
    if (!n) return
    aviso('Subrayado')
    showSel({ mode: 'edit', note: n, y: yOf(doc, r) })
}
/** Guarda el subrayado del tramo (si ya estaba, devuelve el que había). */
/** El texto del tramo. En PDF cada trozo de línea es un elemento aparte y toString() los pega sin espacio. */
function textOf(r) {
    if (!isPDF) return r.toString().replace(/\s+/g, ' ').trim()
    const doc = r.startContainer.ownerDocument
    const w = doc.createTreeWalker(r.commonAncestorContainer, NodeFilter.SHOW_TEXT)
    const parts = []
    let n = r.commonAncestorContainer.nodeType === 3 ? r.commonAncestorContainer : w.nextNode()
    for (; n; n = w.nextNode()) {
        if (!r.intersectsNode(n)) continue
        let t = n.textContent
        if (n === r.endContainer) t = t.slice(0, r.endOffset)
        if (n === r.startContainer) t = t.slice(r.startOffset)
        parts.push(t)
    }
    return parts.join(' ').replace(/\s+/g, ' ').trim()
}
function addNote(doc, index, r) {
    const text = textOf(r)
    if (!text) return null
    const value = view.getCFI(index, r)
    const old = notes.find(x => x.value === value)
    if (old) return old
    const n = { value, color: 'subrayado', text, note: '', created: Date.now(), index, label: label(), frac: last?.fraction ?? null, ...(reflow ? { m: 'r' } : {}) }
    notes.push(n)
    rangeCache.get(doc)?.set(value, r.cloneRange())
    paint(index)
    saveNotes()
    return n
}

function showChrome(on) {
    // Solo si cambia: esconder o enseñar las barras del móvil cuando ya lo estaban recoloca la página
    if (on === chromeOn) return
    chromeOn = on
    document.body.classList.toggle('menu', on)
    $('#arriba').classList.toggle('oculta', !on)
    $('#abajo').classList.toggle('oculta', !on)
    // (las barras de Android se quedan ocultas: enseñarlas cambia los márgenes y el libro se recolocaba, parpadeando)
}

// ---------------------------------------------------------------- Barra de un subrayado
function showSel(sel) {
    selection = sel
    showChrome(false)
    const bar = $('#sel')
    bar.classList.remove('oculta')
    const lower = (sel.y ?? window.innerHeight / 2) > window.innerHeight * 0.55
    bar.style.top = lower ? 'calc(var(--safe-top) + 14px)' : ''
    bar.style.bottom = lower ? '' : 'calc(var(--safe-bottom) + 18px)'
    $('#s-cita').textContent = sel.note.text.length > 140 ? sel.note.text.slice(0, 140) + '…' : sel.note.text
    $('#s-txt').textContent = sel.note.note || ''
    $('#s-txt').classList.toggle('oculta', !sel.note.note)
    $('#s-nota').textContent = sel.note.note ? 'Editar nota' : 'Añadir nota'
}
function hideSel() {
    selection = null
    $('#sel').classList.add('oculta')
}
$('#s-nota').addEventListener('click', () => { const n = selection?.note; hideSel(); if (n) editNote(n) })
$('#s-copiar').addEventListener('click', () => { const t = selection?.note.text; if (t) { A.copy(t); aviso('Copiado') } hideSel() })
$('#s-compartir').addEventListener('click', () => {
    const n = selection?.note
    hideSel()
    if (n) openCompartir(n)
})

// ---------------------------------------------------------------- Compartir una cita como imagen
// Una lámina de revista (4:5, la de las redes): el antetítulo, la cita en grande con su comilla del acento, un filete
// y el libro con su autor. Con los colores del tema que usas (claro u oscuro).
async function laminaCita(n) {
    await Promise.all(['600 60px AtrioSerif', 'italic 400 40px AtrioSerif', '400 40px AtrioSerif'].map(f => document.fonts.load(f).catch(() => { })))
    const W = 1080, H = 1350, M = 96
    const cs = getComputedStyle(document.body)
    const bg = cs.getPropertyValue('--bg').trim() || '#FBF8F1', fg = cs.getPropertyValue('--fg').trim() || '#1F1D1A'
    const muted = cs.getPropertyValue('--muted').trim() || '#857E72', acc = cs.getPropertyValue('--accent').trim() || '#C8372D'
    const c = document.createElement('canvas'); c.width = W; c.height = H
    const g = c.getContext('2d')
    g.fillStyle = bg; g.fillRect(0, 0, W, H)
    // Antetítulo
    g.fillStyle = acc; g.fillRect(M, M + 6, 22, 22)
    g.fillStyle = fg; g.font = '800 26px system-ui, Roboto, sans-serif'
    if ('letterSpacing' in g) g.letterSpacing = '6px'
    g.textBaseline = 'top'; g.fillText('SUBRAYADO', M + 40, M + 4)
    if ('letterSpacing' in g) g.letterSpacing = '0px'
    g.fillRect(M, M + 56, W - 2 * M, 5)
    // La cita: lo más grande que quepa (como mucho 12 líneas)
    const texto = n.text.length > 700 ? n.text.slice(0, 690).replace(/\s+\S*$/, '') + '…' : n.text
    const lineas = (size) => {
        g.font = `400 ${size}px AtrioSerif, Georgia, serif`
        const out = []; let l = ''
        for (const w of texto.split(/\s+/)) {
            const t = l ? l + ' ' + w : w
            if (g.measureText(t).width > W - 2 * M && l) { out.push(l); l = w } else l = t
        }
        if (l) out.push(l)
        return out
    }
    let size = 76, ls = lineas(size)
    const alto = () => ls.length * size * 1.28
    while (size > 34 && (alto() > H - 2 * M - 420 || ls.length > 12)) { size -= 4; ls = lineas(size) }
    const y0 = M + 190 + Math.max(0, (H - 2 * M - 470 - alto()) / 2)
    g.fillStyle = acc; g.font = `600 ${Math.round(size * 2.4)}px AtrioSerif, Georgia, serif`; g.fillText('«', M - 8, y0 - size * 1.95)
    g.fillStyle = fg; g.font = `400 ${size}px AtrioSerif, Georgia, serif`
    ls.forEach((l, i) => g.fillText(l, M, y0 + i * size * 1.28))
    // La nota, si la tiene (en cursiva y del acento)
    let yn = y0 + alto() + 26
    if (n.note) {
        g.fillStyle = acc; g.font = `italic 400 ${Math.max(28, Math.round(size * 0.5))}px AtrioSerif, Georgia, serif`
        const nota = n.note.length > 140 ? n.note.slice(0, 136) + '…' : n.note
        g.fillText(nota, M, yn, W - 2 * M)
    }
    // Pie: filete, el libro y su autor
    const yp = H - M - 150
    g.fillStyle = fg; g.fillRect(M, yp, W - 2 * M, 3)
    g.font = '600 46px AtrioSerif, Georgia, serif'
    g.fillText(st.title || '', M, yp + 30, W - 2 * M)
    g.fillStyle = muted; g.font = '700 26px system-ui, Roboto, sans-serif'
    if ('letterSpacing' in g) g.letterSpacing = '4px'
    g.fillText([st.autor, n.label].filter(Boolean).join(' · ').toUpperCase().slice(0, 60), M, yp + 96, W - 2 * M)
    if ('letterSpacing' in g) g.letterSpacing = '0px'
    return c
}
async function openCompartir(n) {
    openPanel(`<h2>Compartir</h2>
      <div class="lamina"><span class="gris">Preparando la imagen…</span></div>
      <div class="fila" style="margin-top:14px;justify-content:flex-end">
        <button class="boton suave" id="c-texto">Como texto</button><button class="boton" id="c-imagen" disabled>Como imagen</button>
      </div>`, async el => {
        el.querySelector('#c-texto').onclick = () => { closePanel(); A.share(`«${n.text}»${n.note ? `\n${n.note}` : ''}\n— ${st.title || ''}${st.autor ? ', ' + st.autor : ''}`) }
        try {
            const c = await laminaCita(n)
            const url = c.toDataURL('image/png')
            const img = new Image(); img.src = url; img.alt = 'La cita como imagen'
            el.querySelector('.lamina').replaceChildren(img)
            const b = el.querySelector('#c-imagen'); b.disabled = false
            b.onclick = () => { closePanel(); try { A.shareImage(url.slice(url.indexOf(',') + 1)) } catch (e) { aviso('No se ha podido compartir la imagen') } }
        } catch (e) { console.warn(e); el.querySelector('.lamina').textContent = 'No se ha podido hacer la imagen.' }
    })
}
$('#s-buscar').addEventListener('click', () => { const t = selection?.note.text; hideSel(); if (t) openSearch(t) })
$('#s-borrar').addEventListener('click', () => {
    const n = selection?.note
    hideSel()
    if (!n) return
    removeNoteUndo(n)
})
function removeNote(n) {
    notes = notes.filter(x => x !== n)
    paint(n.index)
    saveNotes()
}
/** Quitar con «Deshacer»: vuelve tal cual (con su nota). */
function removeNoteUndo(n, after) {
    removeNote(n)
    aviso('Subrayado quitado', () => { if (!notes.includes(n)) { notes.push(n); paint(n.index); saveNotes(); after?.() } })
}
/** Los subrayados en el orden del libro (dentro de un capítulo, por su sitio en el texto). */
const enOrden = list => [...list].sort((a, b) => {
    if (a.index !== b.index) return (a.index ?? 0) - (b.index ?? 0)
    try { const c = cfiCompare(a.value, b.value); if (c) return c } catch (e) { }
    return (a.created || 0) - (b.created || 0)
})

// ---------------------------------------------------------------- Paneles
function openPanel(html, after) {
    hideSel()
    $('#panel-cuerpo').innerHTML = html
    $('#panel').classList.add('on')
    $('#velo').classList.add('on')
    after?.($('#panel-cuerpo'))
}
function closePanel() { $('#panel').classList.remove('on'); $('#velo').classList.remove('on') }
$('#velo').addEventListener('click', () => { closePanel(); closePie() })

function editNote(n, after) {
    openPanel(`<h2>Tu nota</h2>
        <p class="cita">${esc(n.text)}</p>
        <textarea id="nota-txt" placeholder="Escribe lo que te sugiere…">${esc(n.note)}</textarea>
        <div class="fila" style="margin-top:12px;justify-content:flex-end">
          <button class="boton suave" id="nota-no">Cancelar</button><button class="boton" id="nota-si">Guardar</button>
        </div>`, el => {
        const t = el.querySelector('#nota-txt'); setTimeout(() => t.focus(), 300)
        el.querySelector('#nota-no').onclick = () => { closePanel(); after?.() }
        el.querySelector('#nota-si').onclick = () => { n.note = t.value.trim(); saveNotes(); closePanel(); aviso(n.note ? 'Nota guardada' : 'Nota borrada'); after?.() }
    })
}

// Ajustes de letra: lo de siempre a la vista; lo demás, en «Más opciones»
let masOpciones = false
function openSettings() {
    // Al tocar una opción el panel se repinta: que no vuelva arriba del todo
    const keep = $('#panel').classList.contains('on') ? $('#panel-cuerpo').scrollTop : 0
    const chips = (list, cur, key) => list.map(([v, t]) => `<button class="chip ${cur === v ? 'on' : ''}" data-k="${key}" data-v="${v}">${t}</button>`).join('')
    openPanel(`<h2>Letra y página</h2>
      <h3>Tema</h3>
      <div class="fila">${THEMES.map(([v, t]) => `<button class="tema ${S.theme === v ? 'on' : ''}" data-theme-btn="${v}" title="${t}" style="background:var(--t-${v})">Aa</button>`).join('')}</div>
      <div class="fila" style="margin-top:10px">${chips([[false, 'Siempre este'], [true, 'Noche de 21:00 a 7:00']], S.nightAuto, 'nightAuto')}</div>
      <h3>Luz cálida (menos luz azul)</h3>
      <input class="deslizar" type="range" id="warm" min="0" max="1" step="0.05" value="${S.warm}">
      ${esPDF ? `
        <h3>Formato del PDF</h3>
        <div class="fila">${chips([[true, 'Texto adaptable (novelas)'], [false, 'Página original']], reflow, 'pdfTexto')}</div>
        ${!reflow && S.pdfTexto !== false ? '<p class="sub" style="margin-top:6px">Este PDF no tiene texto que adaptar, así que se ve como página.</p>' : ''}` : ''}
      ${isPDF ? `
        <h3>Pasar página</h3>
        <div class="fila">${chips([['pages', 'Página a página'], ['scroll', 'Deslizando hacia abajo'], ['strip', 'Tira continua (manga, webtoon)']], S.pdfFlow, 'pdfFlow')}</div>
        ${S.pdfFlow === 'pages' ? `
        <h3>Tamaño de la página</h3>
        <div class="fila">${chips([['fit-width', 'Ajustar al ancho'], ['fit-page', 'Página entera']], S.zoom, 'zoom')}</div>` : `
        <p class="sub" style="margin-top:8px">Las páginas, una debajo de otra y al ancho de la pantalla. Solo se avanza desplazando: ni deslizar de lado ni los botones de volumen pasan página.</p>`}
      ` : `
        <h3>Tamaño de letra</h3>
        <div class="paso"><button data-step="size" data-d="-10">−</button><b>${S.size}%</b><button data-step="size" data-d="10">+</button></div>
        <h3>Tipo de letra</h3>
        <div class="fila">${chips(FONTS, S.font, 'font')}</div>
        ${VARIABLES.includes(S.font) ? `<div class="fila" style="margin-top:8px">${chips([[0, 'Grosor normal'], [1, 'Un poco más gruesa']], S.weight || 0, 'weight')}</div>` : ''}
        <h3>Interlineado</h3>
        <div class="paso"><button data-step="spacing" data-d="-0.1">−</button><b>${S.spacing.toFixed(1)}</b><button data-step="spacing" data-d="0.1">+</button></div>
        <h3>Márgenes</h3>
        <div class="fila">${chips([['s', 'Estrechos'], ['m', 'Normales'], ['l', 'Anchos']], S.margin, 'margin')}</div>
        <h3>Texto</h3>
        <div class="fila">${chips([[true, 'Justificado'], [false, 'Alineado a la izquierda']], S.justify, 'justify')}</div>
      `}
      <button class="mas" id="mas-opciones">${masOpciones ? 'Menos opciones ▴' : 'Más opciones ▾'}</button>
      ${!masOpciones ? '' : isPDF ? '' : `
        <h3>Guiones</h3>
        <div class="fila">${chips([[true, 'Partir palabras con guion'], [false, 'Sin guiones']], S.hyphens, 'hyphens')}</div>
        <h3>Párrafos</h3>
        <div class="fila">${chips([[0, 'Como el libro'], [1, 'Con un poco de aire'], [2, 'Separados, sin sangría']], S.paraSpace, 'paraSpace')}</div>
        <h3>Pasar página</h3>
        <div class="fila">${chips([['paginated', 'Páginas'], ['scrolled', 'Desplazamiento continuo']], S.flow, 'flow')}</div>
        <div class="fila" style="margin-top:8px">${chips([[true, 'Con animación'], [false, 'Sin animación']], S.animated, 'animated')}</div>
        ${S.flow === 'paginated' ? `<h3>Tocar los lados</h3>
        <div class="fila">${chips([['normal', 'Izquierda atrás, derecha adelante'], ['adelante', 'Los dos adelante (una mano)']], S.tapZones || 'normal', 'tapZones')}</div>` : ''}
        <h3>Columnas</h3>
        <div class="fila">${chips([[false, 'Una'], [true, 'Dos en horizontal']], S.columns, 'columns')}</div>
      `}
      ${!masOpciones ? '' : `
      ${!haciaAbajo() ? `<h3>Botones de volumen</h3>
      <div class="fila">${chips([[true, 'Pasan página'], [false, 'Son el volumen']], S.volPages !== false, 'volPages')}</div>` : ''}
      <h3>Abajo de la página</h3>
      <div class="fila">${chips([[true, isPDF ? 'Página y %' : 'Lo que queda del capítulo y %'], [false, 'Nada']], S.status, 'status')}</div>
      <h3>Pantalla</h3>
      <div class="fila">${chips([[true, 'Encendida mientras lees'], [false, 'Normal']], S.keepOn, 'keepOn')}</div>
      ${S.keepOn ? '<p class="sub" style="margin-top:6px">Si pasan 10 minutos sin tocarla, se apaga como siempre.</p>' : ''}
      `}
      <p class="sub" style="margin-top:14px">Trucos: pellizca la página con dos dedos para cambiar el tamaño de la letra; desliza el dedo arriba o abajo por el borde izquierdo para el brillo del móvil.</p>`, el => {
        el.scrollTop = keep
        el.querySelector('#mas-opciones').onclick = () => { masOpciones = !masOpciones; openSettings() }
        el.querySelector('#warm').oninput = e => { S.warm = parseFloat(e.target.value); applySettings() }
        el.querySelector('#warm').onchange = saveSettings
        el.querySelectorAll('[data-theme-btn]').forEach(b => {
            const t = b.dataset.themeBtn
            b.style.background = { papel: '#FBF8F1', sepia: '#F1E6CF', gris: '#2B2D31', noche: '#121317', negro: '#000' }[t]
            b.style.color = { papel: '#1F1D1A', sepia: '#43341F', gris: '#E3E1DC', noche: '#D9D6CF', negro: '#BEBBB4' }[t]
            b.onclick = () => { S.theme = t; applySettings(); saveSettings(); openSettings() }
        })
        el.querySelectorAll('.chip[data-k]').forEach(b => b.onclick = () => {
            const k = b.dataset.k; let v = b.dataset.v
            if (v === 'true' || v === 'false') v = v === 'true'
            else if (/^\d+$/.test(v)) v = Number(v)
            S[k] = v; applySettings(); saveSettings()
            if (k === 'pdfTexto') {
                // «Texto adaptable» elegido a mano vale aunque el PDF tenga poco texto por página
                S.pdfForzar = v === true; saveSettings()
                if (v !== reflow) reabrir(); else openSettings(); return
            }
            if (k === 'pdfFlow' && isPDF && (v === 'pages') !== !haciaAbajo()) { reabrir(); return }
            if (k === 'pdfFlow' && isPDF) view.renderer?.setAttribute('gap', v === 'strip' ? '0' : '10')
            if (k === 'volPages') volumen()
            openSettings()
        })
        el.querySelectorAll('[data-step]').forEach(b => b.onclick = () => {
            const k = b.dataset.step, d = parseFloat(b.dataset.d)
            if (k === 'size') S.size = Math.min(240, Math.max(60, S.size + d))
            if (k === 'spacing') S.spacing = Math.round(Math.min(2.4, Math.max(1.0, S.spacing + d)) * 10) / 10
            applySettings(); saveSettings(); openSettings()
        })
    })
}

// Índice (con «Ir a» y lo que llevas leído)
/** Días seguidos leyendo (hasta hoy o hasta ayer). */
function streak() {
    let n = 0
    const d = new Date()
    if (!stats.days[today()]) d.setDate(d.getDate() - 1)
    for (;;) {
        const k = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
        if (!stats.days[k] || stats.days[k] < 60000) break
        n++; d.setDate(d.getDate() - 1)
    }
    return n
}
function readingHtml() {
    if (!stats.total) return ''
    const r = !isPDF && last ? remaining(last) : null
    const s = streak()
    return `<div class="lectura">
        <div><b>${esc(ms2txt(stats.total))}</b><span>${(last?.fraction ?? 0) >= 0.99 ? 'lo has leído en' : 'leído en total'}</span></div>
        <div><b>${esc(ms2txt(stats.days[today()] || 0))}</b><span>hoy${META ? ' · objetivo ' + META + ' min' : ''}</span></div>
        ${r ? `<div><b>${esc(durTxt(r.total) || '—')}</b><span>para acabarlo${S.speed ? ' a tu ritmo' : ''}</span></div>` : ''}
        ${s > 1 ? `<div><b>${s}</b><span>días seguidos</span></div>` : ''}
      </div>`
}
function openToc() {
    const toc = view?.book?.toc || []
    const cur = last?.tocItem?.href
    const items = []
    const walk = (list, depth) => { for (const t of list) { items.push([t, depth]); if (t.subitems?.length) walk(t.subitems, depth + 1) } }
    walk(toc, 0)
    const pages = isPDF ? (view?.book?.sections?.length || 0) : 0
    openPanel(`<h2>Índice</h2>
      ${readingHtml()}
      <div class="ir"><span>Ir a</span><input id="ir-n" type="number" inputmode="numeric" min="${isPDF ? 1 : 0}" max="${isPDF ? pages : 100}"
        placeholder="${isPDF ? 'página (1–' + pages + ')' : '%'}"><button class="boton" id="ir-si">Ir</button></div>
      ${items.length ? `<ul class="lista">${items.map(([t, d], i) =>
        `<li class="n${Math.min(d, 3)} ${t.href === cur ? 'actual' : ''}" data-i="${i}">${esc(t.label?.trim() || 'Sin título')}</li>`).join('')}</ul>`
        : '<p class="vacio">Este libro no trae índice.</p>'}`, el => {
        el.querySelectorAll('li[data-i]').forEach(li => li.onclick = () => irA(items[li.dataset.i][0].href))
        el.querySelector('.actual')?.scrollIntoView({ block: 'center' })
        const go = () => {
            const n = parseFloat(el.querySelector('#ir-n').value)
            if (!isFinite(n)) return
            saltoDesde(); closePanel()
            if (isPDF) view.goTo(Math.min(pages, Math.max(1, Math.round(n))) - 1)
            else view.goToFraction(Math.min(100, Math.max(0, n)) / 100)
        }
        el.querySelector('#ir-si').onclick = go
        el.querySelector('#ir-n').addEventListener('keydown', e => { if (e.key === 'Enter') go() })
    })
}

// Subrayados, notas y marcadores
let soloConNota = false
function openNotes(tab = 'notas') {
    const conNota = notes.filter(n => n.note).length
    const lista = enOrden(soloConNota ? notes.filter(n => n.note) : notes)
    const list = tab === 'notas'
        ? (lista.length ? `<ul class="lista">${lista.map(n => `
            <li data-v="${esc(n.value)}"><div class="cita">${esc(n.text)}</div>
              ${n.note ? `<div class="nota">✎ ${esc(n.note)}</div>` : ''}
              <div class="sub">${esc(n.label || '')}${n.frac != null ? ' · ' + pct(n.frac) : ''} · ${fecha(n.created)}</div>
              <div class="mini"><button data-nota="${esc(n.value)}">${n.note ? 'Editar nota' : 'Añadir nota'}</button><button data-quitar="${esc(n.value)}" class="peligro">Quitar</button></div></li>`).join('')}</ul>`
            : `<p class="vacio">${notes.length ? 'Ningún subrayado tiene nota todavía.' : 'Mantén pulsada una palabra y toca Subrayar o Nota.'}</p>`)
        : (marks.length ? `<ul class="lista">${[...marks].sort((a, b) => a.fraction - b.fraction).map(m => `
            <li data-v="${esc(m.value)}"><b>${esc(m.label || 'Marcador')}</b><div class="sub">${pct(m.fraction)} · ${fecha(m.created)}</div>
              <div class="mini"><button data-quitar-marca="${esc(m.value)}" class="peligro">Quitar</button></div></li>`).join('')}</ul>`
            : '<p class="vacio">Toca el marcador de arriba para guardar la página.</p>')
    openPanel(`<h2>Tus notas</h2>
      <div class="pestanas"><button class="chip ${tab === 'notas' ? 'on' : ''}" data-tab="notas">Subrayados (${notes.length})</button>
      <button class="chip ${tab === 'marcas' ? 'on' : ''}" data-tab="marcas">Marcadores (${marks.length})</button></div>
      ${tab === 'notas' && conNota && conNota < notes.length ? `<div class="fila" style="margin-bottom:4px">${[[false, 'Todos'], [true, `Solo con nota (${conNota})`]].map(([v, t]) => `<button class="chip ${soloConNota === v ? 'on' : ''}" data-solo="${v}">${t}</button>`).join('')}</div>` : ''}
      ${list}
      ${tab === 'notas' && notes.length ? '<div class="fila" style="margin-top:14px"><button class="boton suave" id="exportar">Compartir todas</button></div>' : ''}`, el => {
        el.querySelectorAll('[data-tab]').forEach(b => b.onclick = () => openNotes(b.dataset.tab))
        el.querySelectorAll('[data-solo]').forEach(b => b.onclick = () => { soloConNota = b.dataset.solo === 'true'; openNotes('notas') })
        el.querySelectorAll('li[data-v]').forEach(li => li.onclick = () => irA(li.dataset.v))
        const byValue = v => notes.find(n => n.value === v)
        el.querySelectorAll('[data-nota]').forEach(b => b.onclick = e => { e.stopPropagation(); const n = byValue(b.dataset.nota); if (n) editNote(n, () => openNotes('notas')) })
        el.querySelectorAll('[data-quitar]').forEach(b => b.onclick = e => { e.stopPropagation(); const n = byValue(b.dataset.quitar); if (n) { removeNoteUndo(n, () => openNotes('notas')); openNotes('notas') } })
        el.querySelectorAll('[data-quitar-marca]').forEach(b => b.onclick = e => { e.stopPropagation(); const m = marks.find(x => x.value === b.dataset.quitarMarca); if (m) { quitaMarca(m); openNotes('marcas') } })
        el.querySelector('#exportar')?.addEventListener('click', () => {
            const txt = `${st.title || ''}\n\n` + enOrden(notes)
                .map(n => `«${n.text}»${n.note ? `\n   ✎ ${n.note}` : ''}${n.label ? `\n   — ${n.label}` : ''}`).join('\n\n')
            A.share(txt)
        })
    })
}

// Buscar
let ultimaBusqueda = ''
function openSearch(q = ultimaBusqueda) {
    openPanel(`<h2>Buscar en el libro</h2>
      <div class="buscador"><input id="q" placeholder="Palabra o frase" value="${esc(q)}" enterkeyhint="search"><button class="boton" id="q-ir">Buscar</button></div>
      <p class="sub" id="q-estado" style="color:var(--muted);font-size:13px"></p>
      <ul class="lista" id="q-res"></ul>`, el => {
        const input = el.querySelector('#q')
        const go = () => runSearch(input.value.trim(), el)
        el.querySelector('#q-ir').onclick = go
        input.addEventListener('keydown', e => { if (e.key === 'Enter') go() })
        if (q) go(); else setTimeout(() => input.focus(), 300)
    })
}
let searchRun = 0
const fold = t => t.normalize('NFD').replace(/\p{Mn}+/gu, '').toLowerCase()
// PDF: foliate-js no busca en PDF; se busca en el texto de cada página con pdf.js
async function searchPDF(q, out, estado, run) {
    const pdf = view.book.pdf
    const nq = fold(q)
    let n = 0
    for (let i = 1; i <= pdf.numPages && n < 300; i++) {
        if (run !== searchRun) return
        const tc = await (await pdf.getPage(i)).getTextContent()
        const text = tc.items.map(x => x.str).join(' ')
        const ft = fold(text)
        let at = ft.indexOf(nq)
        while (at >= 0 && n < 300) {
            n++
            const li = document.createElement('li')
            li.innerHTML = `<div class="sub">Página ${i}</div>…${esc(text.slice(Math.max(0, at - 40), at))}<mark>${esc(text.slice(at, at + q.length))}</mark>${esc(text.slice(at + q.length, at + q.length + 60))}…`
            const idx = i - 1
            li.onclick = () => irA(idx)
            out.append(li)
            at = ft.indexOf(nq, at + nq.length)
        }
        if (i % 5 === 0) estado.textContent = `Buscando… ${pct(i / pdf.numPages)}`
    }
    estado.textContent = n ? `${n}${n >= 300 ? '+' : ''} resultados` : 'Nada encontrado.'
}

/** Lo encontrado en el libro: una raya fina del acento bajo cada vez que sale (no un recuadro rojo). */
function subraya(rects, { color = '#C8372D' } = {}) {
    const NS = 'http://www.w3.org/2000/svg'
    const g = document.createElementNS(NS, 'g')
    g.setAttribute('fill', color)
    for (const { left, bottom, width } of rects) {
        const r = document.createElementNS(NS, 'rect')
        r.setAttribute('x', left); r.setAttribute('y', bottom - 1); r.setAttribute('width', width); r.setAttribute('height', 2.5)
        g.append(r)
    }
    return g
}
// Las rayas de la búsqueda se quitan solas al seguir leyendo (tres páginas después de llegar a un resultado)
let buscaPasos = null
function pasoBusqueda() {
    if (buscaPasos === null) return
    if (++buscaPasos > 3) { buscaPasos = null; try { view.clearSearch?.() } catch (e) { } }
}
/** Lo encontrado, marcado unos segundos al llegar (como un subrayado). */
async function destella(cfi) {
    await new Promise(r => setTimeout(r, 250))
    try {
        const { index, anchor } = view.resolveCFI(cfi)
        const doc = liveDoc(index), r = doc && anchor(doc)
        if (!r || typeof r.getClientRects !== 'function') return
        mark(index, r)
        setTimeout(() => { if (!palabra && !pend && !vozOn && marca?.index === index) unmark() }, 2600)
    } catch (e) { }
}
async function runSearch(q, el) {
    if (!q || !view) return
    ultimaBusqueda = q
    const run = ++searchRun
    if (isPDF && view.book.pdf) {
        const out = el.querySelector('#q-res'), estado = el.querySelector('#q-estado')
        out.innerHTML = ''; estado.textContent = 'Buscando…'
        try { await searchPDF(q, out, estado, run) } catch (e) { estado.textContent = 'No se ha podido buscar en este PDF.' }
        return
    }
    view.clearSearch?.()
    const out = el.querySelector('#q-res'), estado = el.querySelector('#q-estado')
    out.innerHTML = ''
    let n = 0
    estado.textContent = 'Buscando…'
    try {
        for await (const r of view.search({ query: q, draw: subraya, drawOptions: { color: getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#C8372D' } })) {
            if (run !== searchRun) return
            if (r === 'done') break
            if (r.progress != null) { estado.textContent = `Buscando… ${pct(r.progress)}`; continue }
            const items = r.subitems || (r.cfi ? [r] : [])
            for (const it of items) {
                if (n >= 300) break
                n++
                const li = document.createElement('li')
                const ex = it.excerpt || {}
                li.innerHTML = `${r.label ? `<div class="sub">${esc(r.label)}</div>` : ''}…${esc(ex.pre)}<mark>${esc(ex.match)}</mark>${esc(ex.post)}…`
                li.onclick = () => { buscaPasos = -1; irA(it.cfi).then(() => destella(it.cfi)) }
                out.append(li)
            }
        }
        estado.textContent = n ? `${n}${n >= 300 ? '+' : ''} resultados` : 'Nada encontrado.'
    } catch (e) { estado.textContent = 'No se ha podido buscar en este libro.' }
}

// ---------------------------------------------------------------- Notas al pie
const footnotes = new FootnoteHandler()
footnotes.addEventListener('before-render', e => {
    const v = e.detail.view
    // Un enlace dentro de la nota (por ejemplo «volver al texto»): se va allí y se cierra la hoja
    v.addEventListener('link', ev => { ev.preventDefault(); closePie(); saltoDesde(); view.goTo(ev.detail.href) })
    v.addEventListener('external-link', ev => { ev.preventDefault(); A.openUrl(ev.detail.a.href) })
    $('#pie-cuerpo').replaceChildren(v)
    v.renderer.setAttribute('flow', 'scrolled')
    v.renderer.setAttribute('margin', '0px')
    v.renderer.setAttribute('gap', '4%')
    v.renderer.setStyles?.(css() + '\naside, aside[epub|type] { display: block !important; } html, body { background: transparent !important; }')
})
footnotes.addEventListener('render', e => {
    const { type } = e.detail
    $('#pie-tipo').textContent = { footnote: 'Nota al pie', endnote: 'Nota', note: 'Nota', biblioentry: 'Referencia', definition: 'Definición' }[type] || 'Nota'
    hideSel(); hideWord(); closePanel(); showChrome(false)
    $('#pie').classList.add('on')
    $('#velo').classList.add('on')
})
function closePie() {
    $('#pie').classList.remove('on')
    if (!$('#panel').classList.contains('on')) $('#velo').classList.remove('on')
    setTimeout(() => { if (!$('#pie').classList.contains('on')) $('#pie-cuerpo').replaceChildren() }, 350)
}
$('#pie-cerrar').onclick = closePie

// ---------------------------------------------------------------- Imágenes a pantalla completa
// Pellizcar para ampliar, arrastrar para moverla, doble toque para ampliar o volver; tocar fuera o atrás cierra.
const zoom = { s: 1, x: 0, y: 0 }
function drawImage() { $('#imagen img').style.transform = `translate(${zoom.x}px, ${zoom.y}px) scale(${zoom.s})` }
function showImage(src) {
    hideWord(); hideSel(); showChrome(false)
    Object.assign(zoom, { s: 1, x: 0, y: 0 })
    const img = $('#imagen img')
    img.src = src
    drawImage()
    $('#imagen').classList.remove('oculta')
}
function hideImage() { $('#imagen').classList.add('oculta'); $('#imagen img').removeAttribute('src') }
{
    const box = $('#imagen')
    let p0 = null, lastTap = 0
    const dist = t => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY)
    box.addEventListener('touchstart', e => {
        const t = e.touches
        p0 = t.length === 2 ? { d: dist(t), s: zoom.s } : { x: t[0].clientX - zoom.x, y: t[0].clientY - zoom.y, moved: false }
    }, { passive: true })
    box.addEventListener('touchmove', e => {
        const t = e.touches
        if (!p0) return
        if (t.length === 2 && p0.d) zoom.s = Math.min(6, Math.max(1, p0.s * dist(t) / p0.d))
        else if (t.length === 1 && p0.x !== undefined && zoom.s > 1) { zoom.x = t[0].clientX - p0.x; zoom.y = t[0].clientY - p0.y; p0.moved = true }
        if (zoom.s === 1) { zoom.x = 0; zoom.y = 0 }
        drawImage()
        e.preventDefault()
    }, { passive: false })
    box.addEventListener('touchend', e => {
        if (e.touches.length) return
        const tap = p0 && p0.x !== undefined && !p0.moved
        p0 = null
        if (!tap) return
        const now = Date.now()
        if (now - lastTap < 300) { Object.assign(zoom, zoom.s > 1 ? { s: 1, x: 0, y: 0 } : { s: 2.5 }); drawImage(); lastTap = 0 }
        else { lastTap = now; setTimeout(() => { if (lastTap === now && zoom.s === 1) hideImage() }, 320) }
    })
    $('#imagen-cerrar').onclick = hideImage
}

// ---------------------------------------------------------------- Escuchar (leer en voz alta)
// foliate prepara el texto frase a frase (SSML con marcas); cada frase se lee con la voz de Android
// (A.speak) y, cuando acaba (window.atrioSpoken), se marca y se lee la siguiente. Al acabar el capítulo, sigue.
let voz = null          // { segs: [{ mark, text }], i }
let vozOn = false, vozPaused = false, vozN = 0
const RATES = [0.8, 1, 1.25, 1.5, 1.75, 2]
// Temporizador: parar a los 15, 30 o 60 minutos, o al acabar el capítulo (como en un audiolibro, para dormirse)
const TEMPOS = [0, 15, 30, 60, 'cap']
let vozTempo = 0, vozFin = 0
const bookLang = () => {
    const l = view?.book?.metadata?.language
    return String((Array.isArray(l) ? l[0] : l) || 'es')
}
function parseSSML(ssml) {
    const d = new DOMParser().parseFromString(ssml, 'application/xml')
    const segs = [{ mark: null, text: '' }]
    const walk = n => {
        for (const c of n.childNodes) {
            if (c.nodeType === 3) segs[segs.length - 1].text += c.nodeValue
            else if (c.nodeType === 1) {
                if (c.localName === 'mark') segs.push({ mark: c.getAttribute('name'), text: '' })
                else if (c.localName === 'break') segs[segs.length - 1].text += ' '
                else walk(c)
            }
        }
    }
    walk(d.documentElement)
    return segs.map(x => ({ mark: x.mark, text: x.text.replace(/\s+/g, ' ').trim() })).filter(x => x.text || x.mark)
}
/** La frase que se está leyendo: marcada (como un subrayado) y, si no se ve, se pasa de página. */
function vozMark(range) {
    const doc = range.startContainer?.ownerDocument
    let index = null
    for (const [i, d] of docs) if (d === doc) index = i
    if (index === null) return
    mark(index, range)
    view.renderer.scrollToAnchor?.(range)
}
async function vozStart() {
    if (isPDF) { aviso('Leer en voz alta funciona con libros de texto (EPUB…), no con PDF'); return }
    hideSel(); hideWord(); closePanel(); showChrome(false)
    vozOn = true; vozPaused = false
    volumen()
    drawVoz()
    $('#voz').classList.remove('oculta')
    try {
        await view.initTTS('sentence', vozMark)
        const r = last?.range
        let ssml = null
        // Desde lo que se ve; si esa posición no está en el capítulo cargado, desde su principio
        try { ssml = r ? view.tts.from(r) : null } catch (e) { console.warn(e) }
        vozLoad(ssml || view.tts.start())
    } catch (e) { console.error(e); aviso('No se ha podido empezar a leer en voz alta'); vozStop() }
}
function vozLoad(ssml) {
    if (!vozOn) return
    if (!ssml) { vozNextSection(); return }
    voz = { segs: parseSSML(ssml), i: 0 }
    vozSay()
}
/** Lee la frase actual (o pasa a la siguiente si está vacía). */
function vozSay() {
    if (!vozOn || vozPaused || !voz) return
    if (vozFin && Date.now() >= vozFin) { aviso('Temporizador: se para la voz'); vozStop(); return }
    let seg = voz.segs[voz.i]
    while (seg && !seg.text) { if (seg.mark) view.tts.setMark(seg.mark); voz.i++; seg = voz.segs[voz.i] }
    if (!seg) { vozLoad(view.tts.next()); return }
    if (seg.mark) view.tts.setMark(seg.mark)
    vozN++
    A.speak(seg.text, 'v' + vozN, S.ttsRate, bookLang())
}
window.atrioSpoken = (id, error) => {
    if (id !== 'v' + vozN || !vozOn || vozPaused) return
    despierta()
    if (error) { aviso('La voz del móvil ha fallado: revisa «Texto a voz» en los ajustes de Android'); vozStop(); return }
    voz.i++
    vozSay()
}
async function vozNextSection() {
    try {
        const cur = view.renderer.getContents?.()?.[0]?.index
        const n = view.book.sections.length
        let next = (cur ?? -1) + 1
        while (next < n && view.book.sections[next].linear === 'no') next++
        if (next >= n) { aviso('Fin del libro'); vozStop(); return }
        if (vozTempo === 'cap') { aviso('Fin del capítulo: se para la voz'); vozStop(); return }
        await view.goTo(next)
        if (!vozOn) return
        await view.initTTS('sentence', vozMark)
        vozLoad(view.tts.start())
    } catch (e) { console.error(e); aviso('No se ha podido seguir leyendo en voz alta'); vozStop() }
}
function vozPause() {
    if (!vozOn) return
    vozPaused = !vozPaused
    if (vozPaused) A.stopSpeaking(); else vozSay()
    drawVoz()
}
function vozSkip() { if (!vozOn || !voz) return; A.stopSpeaking(); vozPaused = false; voz.i++; vozSay(); drawVoz() }
function vozStop() {
    vozOn = false; vozPaused = false; voz = null
    vozTempo = 0; vozFin = 0
    volumen()
    try { A.stopSpeaking() } catch (e) { }
    unmark()
    $('#voz').classList.add('oculta')
}
function drawVoz() {
    $('#v-pausa').textContent = vozPaused ? 'Seguir' : 'Pausa'
    $('#v-vel').textContent = String(S.ttsRate).replace('.', ',') + '×'
    const t = $('#v-tempo')
    if (t) {
        const quedan = vozFin ? Math.max(1, Math.ceil((vozFin - Date.now()) / 60000)) : 0
        t.textContent = vozTempo === 'cap' ? 'Hasta fin de capítulo' : vozTempo ? `Parar en ${quedan} min` : 'Temporizador'
        t.classList.toggle('on', !!vozTempo)
    }
}
setInterval(() => { if (vozOn && vozFin) drawVoz() }, 30000)
$('#v-pausa').onclick = vozPause
$('#v-sig').onclick = vozSkip
$('#v-parar').onclick = vozStop
$('#v-tempo').onclick = () => {
    vozTempo = TEMPOS[(TEMPOS.indexOf(vozTempo) + 1) % TEMPOS.length]
    vozFin = typeof vozTempo === 'number' && vozTempo > 0 ? Date.now() + vozTempo * 60000 : 0
    drawVoz()
    aviso(vozTempo === 'cap' ? 'Se para al acabar el capítulo' : vozTempo ? `Se para dentro de ${vozTempo} min` : 'Sin temporizador')
}
$('#v-vel').onclick = () => {
    S.ttsRate = RATES[(RATES.indexOf(S.ttsRate) + 1) % RATES.length] || 1
    saveSettings(); drawVoz()
}

// ---------------------------------------------------------------- Botones
$('#b-cerrar').onclick = () => { window.atrioPausa(true); A.close() }
$('#b-indice').onclick = openToc
$('#b-voz').onclick = () => vozOn ? vozStop() : vozStart()
$('#b-notas').onclick = () => openNotes('notas')
$('#b-ajustes').onclick = openSettings
$('#b-buscar').onclick = () => openSearch()
function pintaMarca() {
    $('#b-marca').classList.toggle('on', !!currentMark())
    $('#b-marca-cinta')?.classList.toggle('oculta', !currentMark())
}
function quitaMarca(m) {
    marks = marks.filter(x => x !== m); saveMarks(); pintaMarca()
    aviso('Marcador quitado', () => { if (!marks.includes(m)) { marks.push(m); saveMarks(); pintaMarca() } })
}
$('#b-marca').onclick = () => {
    const m = currentMark()
    if (m) { quitaMarca(m); return }
    if (last) { marks.push({ value: last.cfi, label: label() || pct(last.fraction), fraction: last.fraction || 0, index: idxAhora(), created: Date.now(), ...(reflow ? { m: 'r' } : {}) }); aviso('Página marcada') }
    pintaMarca()
    saveMarks()
}

// Atrás de Android: primero se cierra lo que haya abierto
window.atrioBack = () => {
    if (!$('#imagen').classList.contains('oculta')) { hideImage(); return true }
    if ($('#pie').classList.contains('on')) { closePie(); return true }
    if (!$('#palabra').classList.contains('oculta')) { hideWord(); return true }
    if (pend) { pend = null; $('#pend').classList.add('oculta'); unmark(); return true }
    if (selection) { hideSel(); return true }
    if ($('#panel').classList.contains('on')) { closePanel(); return true }
    if (vozOn) { vozStop(); return true }
    if (chromeOn) { showChrome(false); return true }
    return false
}
// El menú desde fuera (las pruebas): lo abre o lo cierra; con algo abierto encima, lo cierra primero
window.atrioMenu = () => { if (paso !== 'listo') return; if (!window.atrioBack()) showChrome(!chromeOn) }
// Teclas de volumen para pasar página (las manda la app)
window.atrioPage = d => { if (haciaAbajo()) return; if (d > 0) view?.goRight(); else view?.goLeft() }

open()
