// Lector de Atrio: EPUB, PDF, MOBI/AZW3, FB2 y CBZ con foliate-js (y pdf.js para PDF).
// Todo se guarda en el móvil a través de window.Atrio (puente con la app): posición, ajustes, subrayados y marcadores.
import './foliate/view.js'
import { FootnoteHandler } from './foliate/footnotes.js'

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
}, st.settings || {})
// Lo que llevas leído de este libro: tiempo total y por día (ms)
const stats = Object.assign({ total: 0, days: {} }, st.stats || {})
let notes = st.annotations || []      // { value, text, note, created, index, label } (el color de antes ya no se usa)
let marks = st.bookmarks || []        // { value, label, fraction, created }
const THEMES = [['papel', 'Papel'], ['sepia', 'Sepia'], ['gris', 'Gris'], ['noche', 'Noche'], ['negro', 'Negro']]
const FONTS = [['original', 'Original'], ['atrio', 'Fraunces'], ['serif', 'Serif'], ['sans', 'Sans'], ['facil', 'Fácil lectura'], ['mono', 'Mono']]
// El acento de la app (el que elegiste en Apariencia), en su versión clara y oscura
const ACCENT = st.accent || { light: '#C8372D', dark: '#E0533F' }
const DARK_THEMES = ['gris', 'noche', 'negro']
const isNight = () => { const h = new Date().getHours(); return h >= 21 || h < 7 }
/** El tema que toca ahora: el tuyo, o Noche si lo pediste y es de noche (salvo que ya uses uno oscuro). */
const theme = () => (S.nightAuto && isNight() && !DARK_THEMES.includes(S.theme)) ? 'noche' : S.theme
// Cada diez minutos se mira si ha cambiado (al anochecer, sin cerrar el libro)
let lastTheme = null
setInterval(() => { if (view && S.nightAuto && theme() !== lastTheme) applySettings() }, 600000)
let view, isPDF = false, last = null, chromeOn = false, selection = null

// ---------------------------------------------------------------- Utilidades
const debounce = (f, ms) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => f(...a), ms) } }
const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c])
const pct = f => Math.round((f || 0) * 100) + '%'
const fecha = t => new Date(t).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })
function aviso(t) { const a = $('#aviso'); a.textContent = t; a.classList.add('on'); clearTimeout(aviso.t); aviso.t = setTimeout(() => a.classList.remove('on'), 1800) }
const saveSettings = () => A.saveSettings(JSON.stringify(S))
const saveNotes = () => A.saveAnnotations(JSON.stringify(notes))
const saveMarks = () => A.saveBookmarks(JSON.stringify(marks))
const label = () => last?.tocItem?.label?.trim() || ''

// ---------------------------------------------------------------- Abrir el libro
let paso = 'inicio'
function fallo(e) {
    console.error('Lector: fallo en «' + paso + '»', e && (e.stack || e.message || e))
    $('#carga').classList.remove('fuera')
    $('#carga-txt').textContent = ((e && e.message) || 'No se ha podido abrir el libro.') + ' (' + paso + ')'
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
        paso = 'preparar el visor'
        view = document.createElement('foliate-view')
        // (solo cuenta en PDF y cómics: los EPUB tienen su propio «Pasar página»)
        view.dataset.fxlFlow = S.pdfFlow || 'pages'
        $('#libro').append(view)
        paso = 'descomprimir el libro'
        await view.open(file)
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
    view.addEventListener('link', e => footnotes.handle(view.book, e)?.catch(err => { console.warn(err); view.goTo(e.detail.href) }))
    if (!st.title) $('#t-libro').textContent = (typeof view.book.metadata?.title === 'string' ? view.book.metadata.title : '') || ''
    applySettings()
    // Desde la ficha del libro se puede abrir directamente en un subrayado
    await view.init({ lastLocation: st.goto || st.location || null, showTextStart: !st.location && !st.goto })
    paso = 'listo'
    $('#carga').classList.add('fuera')
    showChrome(true)
    setTimeout(() => showChrome(false), 1600)
}

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
    last = null; lastTurn = null
    if (loc) st.location = loc
    st.goto = null
    await open()
}

// ---------------------------------------------------------------- Ajustes de lectura
function css() {
    const cs = getComputedStyle(document.body)
    const fg = cs.getPropertyValue('--fg').trim(), bg = cs.getPropertyValue('--bg').trim(), accent = cs.getPropertyValue('--accent').trim()
    const dark = DARK_THEMES.includes(theme())
    const fam = { original: '', atrio: 'AtrioSerif, Georgia, serif', serif: 'Georgia, "Noto Serif", serif', sans: 'system-ui, Roboto, "Noto Sans", sans-serif', facil: 'Verdana, "Atkinson Hyperlegible", sans-serif', mono: '"Roboto Mono", "Noto Sans Mono", monospace' }[S.font]
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
html { color: ${fg} !important; background: transparent !important; font-size: ${S.size}% !important; color-scheme: ${dark ? 'dark' : 'light'}; }
body { background: transparent !important; color: inherit !important; ${fam ? `font-family: ${fam} !important;` : ''} }
${fam ? 'body *:not(code):not(pre) { font-family: inherit !important; }' : ''}
p, li, blockquote, dd { line-height: ${S.spacing} !important; text-align: ${S.justify ? 'justify' : 'start'}; -webkit-hyphens: ${hy}; hyphens: ${hy}; widows: 2; orphans: 2; }
${para}
h1, h2, h3, h4, p, li, span, div { color: inherit !important; }
a:link, a:visited { color: ${accent} !important; }
img, svg, video { max-width: 100%; ${dark ? 'filter: brightness(.86);' : ''} }
pre { white-space: pre-wrap !important; }
aside[epub|type~="footnote"], aside[epub|type~="endnote"], aside[epub|type~="rearnote"] { display: none; }
html, body, body * { -webkit-user-select: none !important; user-select: none !important; -webkit-touch-callout: none !important; }
::highlight(atrio) { background-color: ${fg}; color: ${bg}; }`
}

function applySettings() {
    lastTheme = theme()
    document.body.dataset.theme = lastTheme
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
    A.setBrightness(S.brightness)
    A.keepScreenOn(!!S.keepOn)
    repaintAll()
    let w = $('#calido')
    if (!w) { w = document.createElement('div'); w.id = 'calido'; document.body.append(w) }
    Object.assign(w.style, { position: 'fixed', inset: '0', pointerEvents: 'none', zIndex: 15, background: `rgba(255,120,20,${S.warm * 0.28})`, mixBlendMode: 'multiply' })
}

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
function track(detail) {
    const now = Date.now()
    const loc = detail.location?.current
    if (lastTurn && typeof loc === 'number') {
        const dt = now - lastTurn.t
        // Solo cuenta como lectura si no has dejado el móvil (más de 10 min en una página = te fuiste)
        if (dt > 1500 && dt < 10 * 60000 && !document.hidden) {
            const add = Math.min(dt, 4 * 60000)
            stats.total += add
            const d = today()
            stats.days[d] = (stats.days[d] || 0) + add
            // Velocidad: lo avanzado (en bytes) entre lo tardado, si es un paso normal de página
            const dloc = loc - lastTurn.loc
            if (!isPDF && dloc > 0 && dloc <= 6 && dt > 4000) {
                const bpm = dloc * LOC / (dt / 60000)
                if (bpm > 200 && bpm < 20000) { S.speed = S.speed ? Math.round(S.speed * 0.85 + bpm * 0.15) : Math.round(bpm); saveSpeed() }
            }
            saveStats()
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
function drawStatus(detail) {
    const el = $('#estado')
    if (!el || !detail) return
    let left = ''
    if (isPDF) {
        const n = view?.book?.sections?.length || 0
        const i = (detail.section?.current ?? detail.index ?? 0) + 1
        left = n ? `Página ${i} de ${n}` : ''
    } else {
        const r = remaining(detail)
        if (r) left = r.section >= 1 ? `${durTxt(r.section)} para acabar el capítulo` : 'Última página del capítulo'
    }
    $('#e-izq').textContent = left
    $('#e-der').textContent = pct(detail.fraction)
}

function onRelocate({ detail }) {
    last = detail
    track(detail)
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
$('#p-barra').addEventListener('input', e => view?.goToFraction(parseFloat(e.target.value)))

function currentMark() {
    if (!last) return null
    return marks.find(m => Math.abs((m.fraction || 0) - (last.fraction || 0)) < 0.0025)
}

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

    // Mantener pulsado (sin mover el dedo): la palabra de debajo. Mientras, la página NO se mueve.
    let t0 = null, holding = false, holdTimer = 0, swiped = false, held = false
    // Brillo: deslizar arriba o abajo por el borde izquierdo de la pantalla (como en los lectores de tinta)
    let edge = false, glow = null
    const topX = x => x + (doc.defaultView.frameElement?.getBoundingClientRect().left || 0)
    win.addEventListener('touchstart', e => {
        const t = e.touches[0]
        t0 = t ? { x: t.clientX, y: t.clientY, time: e.timeStamp } : null
        swiped = false; held = false; holding = false
        // (leyendo hacia abajo, el borde también sirve para desplazar: ahí no se toca el brillo)
        edge = !!t && e.touches.length === 1 && topX(t.clientX) < window.innerWidth * 0.1 && !haciaAbajo()
        glow = null
        clearTimeout(holdTimer)
        if (t && e.touches.length === 1) holdTimer = setTimeout(() => {
            holding = true; held = true
            onHold(doc, index, t0.x, t0.y)
        }, 450)
    }, { capture: true, passive: true })
    win.addEventListener('touchmove', e => {
        const t = e.touches[0]
        if (t0 && t && Math.hypot(t.clientX - t0.x, t.clientY - t0.y) > 12 && !holding) clearTimeout(holdTimer)
        if (edge && t0 && t && !holding) {
            const dx = t.clientX - t0.x, dy = t.clientY - t0.y
            if (glow === null && Math.abs(dy) > 16 && Math.abs(dy) > Math.abs(dx) * 1.6) glow = S.brightness < 0 ? 0.6 : S.brightness
            if (glow !== null) {
                S.brightness = Math.round(Math.min(1, Math.max(0.02, glow - dy / (window.innerHeight * 0.7))) * 100) / 100
                A.setBrightness(S.brightness)
                aviso('Brillo ' + Math.round(S.brightness * 100) + ' %')
                e.stopPropagation()
            }
        }
        if (holding) e.stopPropagation()
    }, { capture: true, passive: true })
    win.addEventListener('touchend', e => {
        clearTimeout(holdTimer)
        const t = e.changedTouches[0]
        // (el touchend sí le llega a foliate: así la página vuelve a su sitio si se llegó a mover un poco al empezar)
        if (glow !== null) { glow = null; edge = false; swiped = true; saveSettings(); holding = false; return }
        // PDF: se pasa de página deslizando (foliate no lo hace solo en los PDF)
        // (leyendo hacia abajo no se pasa de página deslizando de lado: solo se desplaza)
        if (isPDF && !haciaAbajo() && t0 && t && !holding) {
            const dx = t.clientX - t0.x, dy = t.clientY - t0.y
            if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.4 && e.timeStamp - t0.time < 900) {
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
            // PDF: tocar nunca pasa de página, solo abre o cierra el menú (las páginas, deslizando)
            if (S.flow === 'paginated' && !isPDF) {
                const fr = doc.defaultView.frameElement?.getBoundingClientRect()
                const x = e.clientX + (fr ? fr.left : 0)
                const w = window.innerWidth
                if (x < w * 0.27) { view.goLeft(); if (chromeOn) showChrome(false); return }
                if (x > w * 0.73) { view.goRight(); if (chromeOn) showChrome(false); return }
            }
            showChrome(!chromeOn)
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
        ? (r.lemma ? `<div class="gris" style="margin-bottom:4px">De «${esc(r.lemma)}»:</div>` : '') + `<ol>${r.defs.map(d => `<li>${esc(d)}</li>`).join('')}</ol>`
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
    const n = { value, color: 'subrayado', text, note: '', created: Date.now(), index, label: label() }
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
    A.systemBars(on)
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
    if (n) A.share(`«${n.text}»${n.note ? `\n${n.note}` : ''}\n— ${st.title || ''}`)
    hideSel()
})
$('#s-buscar').addEventListener('click', () => { const t = selection?.note.text; hideSel(); if (t) openSearch(t) })
$('#s-borrar').addEventListener('click', () => {
    const n = selection?.note
    hideSel()
    if (!n) return
    removeNote(n)
    aviso('Subrayado quitado')
})
function removeNote(n) {
    notes = notes.filter(x => x !== n)
    paint(n.index)
    saveNotes()
}

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

// Ajustes de letra
function openSettings() {
    // Al tocar una opción el panel se repinta: que no vuelva arriba del todo
    const keep = $('#panel').classList.contains('on') ? $('#panel-cuerpo').scrollTop : 0
    const chips = (list, cur, key) => list.map(([v, t]) => `<button class="chip ${cur === v ? 'on' : ''}" data-k="${key}" data-v="${v}">${t}</button>`).join('')
    openPanel(`<h2>Letra y página</h2>
      <h3>Tema</h3>
      <div class="fila">${THEMES.map(([v, t]) => `<button class="tema ${S.theme === v ? 'on' : ''}" data-theme-btn="${v}" title="${t}" style="background:var(--t-${v})">Aa</button>`).join('')}</div>
      <div class="fila" style="margin-top:10px">${chips([[false, 'Siempre este'], [true, 'Noche de 21:00 a 7:00']], S.nightAuto, 'nightAuto')}</div>
      ${isPDF ? `
        <h3>Pasar página</h3>
        <div class="fila">${chips([['pages', 'Página a página'], ['scroll', 'Deslizando hacia abajo'], ['strip', 'Tira continua (manga, webtoon)']], S.pdfFlow, 'pdfFlow')}</div>
        ${S.pdfFlow === 'pages' ? `
        <h3>Tamaño de la página</h3>
        <div class="fila">${chips([['fit-width', 'Ajustar al ancho'], ['fit-page', 'Página entera']], S.zoom, 'zoom')}</div>` : `
        <p class="sub" style="margin-top:8px">Las páginas, una debajo de otra y al ancho de la pantalla. Los botones de volumen bajan una pantalla.</p>`}
      ` : `
        <h3>Tamaño de letra</h3>
        <div class="paso"><button data-step="size" data-d="-10">−</button><b>${S.size}%</b><button data-step="size" data-d="10">+</button></div>
        <h3>Tipo de letra</h3>
        <div class="fila">${chips(FONTS, S.font, 'font')}</div>
        <h3>Interlineado</h3>
        <div class="paso"><button data-step="spacing" data-d="-0.1">−</button><b>${S.spacing.toFixed(1)}</b><button data-step="spacing" data-d="0.1">+</button></div>
        <h3>Márgenes</h3>
        <div class="fila">${chips([['s', 'Estrechos'], ['m', 'Normales'], ['l', 'Anchos']], S.margin, 'margin')}</div>
        <h3>Texto</h3>
        <div class="fila">${chips([[true, 'Justificado'], [false, 'Alineado a la izquierda']], S.justify, 'justify')}</div>
        <div class="fila" style="margin-top:8px">${chips([[true, 'Partir palabras con guion'], [false, 'Sin guiones']], S.hyphens, 'hyphens')}</div>
        <h3>Párrafos</h3>
        <div class="fila">${chips([[0, 'Como el libro'], [1, 'Con un poco de aire'], [2, 'Separados, sin sangría']], S.paraSpace, 'paraSpace')}</div>
        <h3>Pasar página</h3>
        <div class="fila">${chips([['paginated', 'Páginas'], ['scrolled', 'Desplazamiento continuo']], S.flow, 'flow')}</div>
        <div class="fila" style="margin-top:8px">${chips([[true, 'Con animación'], [false, 'Sin animación']], S.animated, 'animated')}</div>
        <h3>Columnas</h3>
        <div class="fila">${chips([[false, 'Una'], [true, 'Dos con el móvil en horizontal']], S.columns, 'columns')}</div>
      `}
      <h3>Abajo de la página</h3>
      <div class="fila">${chips([[true, isPDF ? 'Página y %' : 'Lo que queda del capítulo y %'], [false, 'Nada']], S.status, 'status')}</div>
      <h3>Pantalla</h3>
      <div class="fila">${chips([[true, 'Siempre encendida'], [false, 'Normal']], S.keepOn, 'keepOn')}</div>
      <p class="sub" style="margin-top:14px">Truco: desliza el dedo arriba o abajo por el borde izquierdo de la página para cambiar el brillo.</p>`, el => {
        el.scrollTop = keep
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
            if (k === 'pdfFlow' && isPDF && (v === 'pages') !== !haciaAbajo()) { reabrir(); return }
            if (k === 'pdfFlow' && isPDF) view.renderer?.setAttribute('gap', v === 'strip' ? '0' : '10')
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

function openBrightness() {
    openPanel(`<h2>Brillo</h2>
      <div class="fila"><button class="chip ${S.brightness < 0 ? 'on' : ''}" id="br-auto">Automático (el del móvil)</button></div>
      <h3>Brillo de esta pantalla</h3>
      <input class="deslizar" type="range" id="br" min="0.02" max="1" step="0.01" value="${S.brightness < 0 ? 0.6 : S.brightness}">
      <h3>Luz cálida (menos luz azul)</h3>
      <input class="deslizar" type="range" id="warm" min="0" max="1" step="0.05" value="${S.warm}">`, el => {
        el.querySelector('#br-auto').onclick = () => { S.brightness = -1; applySettings(); saveSettings(); openBrightness() }
        el.querySelector('#br').oninput = e => { S.brightness = parseFloat(e.target.value); A.setBrightness(S.brightness); el.querySelector('#br-auto').classList.remove('on') }
        el.querySelector('#br').onchange = saveSettings
        el.querySelector('#warm').oninput = e => { S.warm = parseFloat(e.target.value); applySettings() }
        el.querySelector('#warm').onchange = saveSettings
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
        <div><b>${esc(ms2txt(stats.total))}</b><span>leído en total</span></div>
        <div><b>${esc(ms2txt(stats.days[today()] || 0))}</b><span>hoy</span></div>
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
        el.querySelectorAll('li[data-i]').forEach(li => li.onclick = () => { view.goTo(items[li.dataset.i][0].href); closePanel() })
        el.querySelector('.actual')?.scrollIntoView({ block: 'center' })
        const go = () => {
            const n = parseFloat(el.querySelector('#ir-n').value)
            if (!isFinite(n)) return
            if (isPDF) view.goTo(Math.min(pages, Math.max(1, Math.round(n))) - 1)
            else view.goToFraction(Math.min(100, Math.max(0, n)) / 100)
            closePanel()
        }
        el.querySelector('#ir-si').onclick = go
        el.querySelector('#ir-n').addEventListener('keydown', e => { if (e.key === 'Enter') go() })
    })
}

// Subrayados, notas y marcadores
function openNotes(tab = 'notas') {
    const list = tab === 'notas'
        ? (notes.length ? `<ul class="lista">${[...notes].sort((a, b) => (a.index - b.index) || (a.created - b.created)).map((n, i) => `
            <li data-v="${esc(n.value)}"><div class="cita">${esc(n.text)}</div>
              ${n.note ? `<div class="nota">✎ ${esc(n.note)}</div>` : ''}
              <div class="sub">${esc(n.label || '')} · ${fecha(n.created)}</div>
              <div class="mini"><button data-nota="${esc(n.value)}">${n.note ? 'Editar nota' : 'Añadir nota'}</button><button data-quitar="${esc(n.value)}" class="peligro">Quitar</button></div></li>`).join('')}</ul>`
            : '<p class="vacio">Mantén pulsada una palabra y toca Subrayar o Nota.</p>')
        : (marks.length ? `<ul class="lista">${[...marks].sort((a, b) => a.fraction - b.fraction).map(m => `
            <li data-v="${esc(m.value)}"><b>${esc(m.label || 'Marcador')}</b><div class="sub">${pct(m.fraction)} · ${fecha(m.created)}</div></li>`).join('')}</ul>`
            : '<p class="vacio">Toca el marcador de arriba para guardar la página.</p>')
    openPanel(`<h2>Tus notas</h2>
      <div class="pestanas"><button class="chip ${tab === 'notas' ? 'on' : ''}" data-tab="notas">Subrayados y notas (${notes.length})</button>
      <button class="chip ${tab === 'marcas' ? 'on' : ''}" data-tab="marcas">Marcadores (${marks.length})</button></div>
      ${list}
      ${tab === 'notas' && notes.length ? '<div class="fila" style="margin-top:14px"><button class="boton suave" id="exportar">Compartir todas</button></div>' : ''}`, el => {
        el.querySelectorAll('[data-tab]').forEach(b => b.onclick = () => openNotes(b.dataset.tab))
        el.querySelectorAll('li[data-v]').forEach(li => li.onclick = () => { view.goTo(li.dataset.v); closePanel() })
        const byValue = v => notes.find(n => n.value === v)
        el.querySelectorAll('[data-nota]').forEach(b => b.onclick = e => { e.stopPropagation(); const n = byValue(b.dataset.nota); if (n) editNote(n, () => openNotes('notas')) })
        el.querySelectorAll('[data-quitar]').forEach(b => b.onclick = e => { e.stopPropagation(); const n = byValue(b.dataset.quitar); if (n) { removeNote(n); openNotes('notas') } })
        el.querySelector('#exportar')?.addEventListener('click', () => {
            const txt = `${st.title || ''}\n\n` + [...notes].sort((a, b) => (a.index - b.index) || (a.created - b.created))
                .map(n => `«${n.text}»${n.note ? `\n   ✎ ${n.note}` : ''}${n.label ? `\n   — ${n.label}` : ''}`).join('\n\n')
            A.share(txt)
        })
    })
}

// Buscar
function openSearch(q = '') {
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
            li.onclick = () => { view.goTo(idx); closePanel() }
            out.append(li)
            at = ft.indexOf(nq, at + nq.length)
        }
        if (i % 5 === 0) estado.textContent = `Buscando… ${pct(i / pdf.numPages)}`
    }
    estado.textContent = n ? `${n}${n >= 300 ? '+' : ''} resultados` : 'Nada encontrado.'
}

async function runSearch(q, el) {
    if (!q || !view) return
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
        for await (const r of view.search({ query: q })) {
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
                li.onclick = () => { view.goTo(it.cfi); closePanel() }
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
    v.addEventListener('link', ev => { ev.preventDefault(); closePie(); view.goTo(ev.detail.href) })
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
    let seg = voz.segs[voz.i]
    while (seg && !seg.text) { if (seg.mark) view.tts.setMark(seg.mark); voz.i++; seg = voz.segs[voz.i] }
    if (!seg) { vozLoad(view.tts.next()); return }
    if (seg.mark) view.tts.setMark(seg.mark)
    vozN++
    A.speak(seg.text, 'v' + vozN, S.ttsRate, bookLang())
}
window.atrioSpoken = (id, error) => {
    if (id !== 'v' + vozN || !vozOn || vozPaused) return
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
    try { A.stopSpeaking() } catch (e) { }
    unmark()
    $('#voz').classList.add('oculta')
}
function drawVoz() {
    $('#v-pausa').textContent = vozPaused ? 'Seguir' : 'Pausa'
    $('#v-vel').textContent = String(S.ttsRate).replace('.', ',') + '×'
}
$('#v-pausa').onclick = vozPause
$('#v-sig').onclick = vozSkip
$('#v-parar').onclick = vozStop
$('#v-vel').onclick = () => {
    S.ttsRate = RATES[(RATES.indexOf(S.ttsRate) + 1) % RATES.length] || 1
    saveSettings(); drawVoz()
}

// ---------------------------------------------------------------- Botones
$('#b-cerrar').onclick = () => A.close()
$('#b-indice').onclick = openToc
$('#b-voz').onclick = () => vozOn ? vozStop() : vozStart()
$('#b-notas').onclick = () => openNotes('notas')
$('#b-ajustes').onclick = openSettings
$('#b-brillo').onclick = openBrightness
$('#b-buscar').onclick = () => openSearch()
$('#b-marca').onclick = () => {
    const m = currentMark()
    if (m) { marks = marks.filter(x => x !== m); aviso('Marcador quitado') }
    else if (last) { marks.push({ value: last.cfi, label: label() || pct(last.fraction), fraction: last.fraction || 0, created: Date.now() }); aviso('Página marcada') }
    $('#b-marca').classList.toggle('on', !!currentMark())
    $('#b-marca-cinta')?.classList.toggle('oculta', !currentMark())
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
// Teclas de volumen para pasar página (las manda la app)
window.atrioPage = d => { if (d > 0) view?.goRight(); else view?.goLeft() }

open()
