// Lector de Atrio: EPUB, PDF, MOBI/AZW3, FB2 y CBZ con foliate-js (y pdf.js para PDF).
// Todo se guarda en el móvil a través de window.Atrio (puente con la app): posición, ajustes, subrayados y marcadores.
import './foliate/view.js'
import { Overlayer } from './foliate/overlayer.js'

const A = window.Atrio
const $ = s => document.querySelector(s)
const st = JSON.parse(A.state())
const S = Object.assign({
    theme: 'papel', size: 100, font: 'original', spacing: 1.5, margin: 'm', justify: true,
    flow: 'paginated', brightness: -1, warm: 0, keepOn: true, zoom: 'fit-width',
}, st.settings || {})
let notes = st.annotations || []      // { value, color, text, note, created, index, label }
let marks = st.bookmarks || []        // { value, label, fraction, created }
const COLORS = { amarillo: '#FFD54A', verde: '#8BE38F', azul: '#7FB8FF', rosa: '#FF99C2', subrayado: '#E5484D', marca: '#E85A2E' }
const THEMES = [['papel', 'Papel'], ['sepia', 'Sepia'], ['gris', 'Gris'], ['noche', 'Noche'], ['negro', 'Negro']]
const FONTS = [['original', 'Original'], ['serif', 'Serif'], ['sans', 'Sans'], ['facil', 'Fácil lectura'], ['mono', 'Mono']]
let view, isPDF = false, last = null, chromeOn = false, selection = null, annJustShown = false

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
    document.body.dataset.theme = S.theme
    try {
        paso = 'leer el archivo'
        const res = await fetch('/libro/' + encodeURIComponent(st.file))
        if (!res.ok) throw new Error('No se puede leer el archivo (' + res.status + '). ¿Lo has movido o borrado?')
        const file = new File([await res.blob()], st.file)
        paso = 'preparar el visor'
        view = document.createElement('foliate-view')
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
    view.addEventListener('create-overlay', e => {
        for (const n of notes) if (n.index === e.detail.index) view.addAnnotation(n)
    })
    view.addEventListener('draw-annotation', e => {
        const { draw, annotation } = e.detail
        if (annotation.color === 'subrayado') draw(Overlayer.underline, { color: COLORS.subrayado, width: 2 })
        else draw(Overlayer.highlight, { color: COLORS[annotation.color] || COLORS.amarillo })
    })
    view.addEventListener('show-annotation', e => {
        annJustShown = true
        const n = notes.find(x => x.value === e.detail.value)
        if (n) showSel({ mode: 'edit', note: n, range: e.detail.range })
    })
    view.addEventListener('external-link', e => { e.preventDefault(); A.openUrl(e.detail.a.href) })
    if (!st.title) $('#t-libro').textContent = (typeof view.book.metadata?.title === 'string' ? view.book.metadata.title : '') || ''
    applySettings()
    await view.init({ lastLocation: st.location || null, showTextStart: !st.location })
    paso = 'listo'
    $('#carga').classList.add('fuera')
    showChrome(true)
    setTimeout(() => showChrome(false), 1600)
}

// ---------------------------------------------------------------- Ajustes de lectura
function css() {
    const cs = getComputedStyle(document.body)
    const fg = cs.getPropertyValue('--fg').trim(), accent = cs.getPropertyValue('--accent').trim()
    const dark = ['gris', 'noche', 'negro'].includes(S.theme)
    const fam = { original: '', serif: 'Georgia, "Noto Serif", serif', sans: 'system-ui, Roboto, "Noto Sans", sans-serif', facil: 'Verdana, "Atkinson Hyperlegible", sans-serif', mono: '"Roboto Mono", "Noto Sans Mono", monospace' }[S.font]
    return `
@namespace epub "http://www.idpf.org/2007/ops";
html { color: ${fg} !important; background: transparent !important; font-size: ${S.size}% !important; color-scheme: ${dark ? 'dark' : 'light'}; }
body { background: transparent !important; color: inherit !important; ${fam ? `font-family: ${fam} !important;` : ''} }
${fam ? 'body *:not(code):not(pre) { font-family: inherit !important; }' : ''}
p, li, blockquote, dd { line-height: ${S.spacing} !important; text-align: ${S.justify ? 'justify' : 'start'}; -webkit-hyphens: auto; hyphens: auto; widows: 2; orphans: 2; }
h1, h2, h3, h4, p, li, span, div { color: inherit !important; }
a:link, a:visited { color: ${accent} !important; }
img, svg, video { max-width: 100%; ${dark ? 'filter: brightness(.86);' : ''} }
pre { white-space: pre-wrap !important; }
aside[epub|type~="footnote"], aside[epub|type~="endnote"], aside[epub|type~="rearnote"] { display: none; }
html, body, body * { -webkit-user-select: none !important; user-select: none !important; -webkit-touch-callout: none !important; }`
}

function applySettings() {
    document.body.dataset.theme = S.theme
    A.setTheme(S.theme)
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
        view.renderer.setAttribute('max-column-count', '1')
    }
    A.setBrightness(S.brightness)
    A.keepScreenOn(!!S.keepOn)
    let w = $('#calido')
    if (!w) { w = document.createElement('div'); w.id = 'calido'; document.body.append(w) }
    Object.assign(w.style, { position: 'fixed', inset: '0', pointerEvents: 'none', zIndex: 15, background: `rgba(255,120,20,${S.warm * 0.28})`, mixBlendMode: 'multiply' })
}

// ---------------------------------------------------------------- Posición
const saveProgress = debounce(() => {
    if (!last) return
    A.saveProgress(last.cfi || '', last.fraction || 0, label())
}, 700)

function onRelocate({ detail }) {
    last = detail
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
    // Nada de la selección del navegador (con sus asas): mantener pulsado lo lleva el lector (también en PDF)
    const stl = doc.createElement('style')
    stl.textContent = 'html,body,body *{-webkit-user-select:none!important;user-select:none!important;-webkit-touch-callout:none!important}'
    ;(doc.head || doc.documentElement).append(stl)
    const win = doc.defaultView

    // Mantener pulsado (sin mover el dedo): la palabra de debajo. Mientras, la página NO se mueve.
    let t0 = null, holding = false, holdTimer = 0, swiped = false, held = false
    win.addEventListener('touchstart', e => {
        const t = e.touches[0]
        t0 = t ? { x: t.clientX, y: t.clientY, time: e.timeStamp } : null
        swiped = false; held = false; holding = false
        clearTimeout(holdTimer)
        if (t && e.touches.length === 1) holdTimer = setTimeout(() => {
            holding = true; held = true
            onHold(doc, index, t0.x, t0.y)
        }, 450)
    }, { capture: true, passive: true })
    win.addEventListener('touchmove', e => {
        const t = e.touches[0]
        if (t0 && t && Math.hypot(t.clientX - t0.x, t.clientY - t0.y) > 12 && !holding) clearTimeout(holdTimer)
        if (holding) e.stopPropagation()
    }, { capture: true, passive: true })
    win.addEventListener('touchend', e => {
        clearTimeout(holdTimer)
        const t = e.changedTouches[0]
        // PDF: se pasa de página deslizando (foliate no lo hace solo en los PDF)
        if (isPDF && t0 && t && !holding) {
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

    doc.addEventListener('click', e => {
        if (e.target.closest?.('a')) return
        if (swiped) { swiped = false; return }
        if (held) { held = false; return }
        setTimeout(() => {
            if (annJustShown) { annJustShown = false; return }
            if (!$('#palabra').classList.contains('oculta')) { hideWord(); return }
            if (selection) { hideSel(); return }
            if ($('#panel').classList.contains('on')) { closePanel(); return }
            if (pend) return
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
let marca = null     // la palabra marcada (anotación temporal, no se guarda)
let palabra = null   // { doc, index, range, word, sentence, lang }
async function mark(index, range) {
    await unmark()
    marca = { value: view.getCFI(index, range), color: 'marca', temp: true }
    try { await view.addAnnotation(marca) } catch (e) { }
}
async function unmark() {
    if (marca) { try { await view.deleteAnnotation(marca) } catch (e) { } marca = null }
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
$('#p-nota').onclick = () => {
    const p = palabra
    if (!p) return
    $('#palabra').classList.add('oculta'); palabra = null
    unmark()
    selection = { mode: 'new', value: view.getCFI(p.index, p.range), text: p.word, index: p.index, doc: p.doc }
    highlight('amarillo', true)
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
    const text = r.toString().replace(/\s+/g, ' ').trim()
    if (!text) return
    // Mientras eliges el color, el tramo entero se ve marcado
    mark(index, r)
    showSel({ mode: 'new', value: view.getCFI(index, r), text, index, doc, y: yOf(doc, r) })
}

function showChrome(on) {
    // Solo si cambia: esconder o enseñar las barras del móvil cuando ya lo estaban recoloca la página
    if (on === chromeOn) return
    chromeOn = on
    $('#arriba').classList.toggle('oculta', !on)
    $('#abajo').classList.toggle('oculta', !on)
    A.systemBars(on)
}

// ---------------------------------------------------------------- Selección y subrayados
function showSel(sel) {
    selection = sel
    showChrome(false)
    const bar = $('#sel')
    bar.classList.remove('oculta')
    const lower = (sel.y ?? window.innerHeight / 2) > window.innerHeight * 0.55
    bar.style.top = lower ? 'calc(env(safe-area-inset-top, 0px) + 14px)' : ''
    bar.style.bottom = lower ? '' : 'calc(env(safe-area-inset-bottom, 0px) + 18px)'
    for (const b of bar.querySelectorAll('.c')) b.classList.toggle('on', sel.mode === 'edit' && sel.note.color === b.dataset.c)
    $('#s-borrar').classList.toggle('oculta', sel.mode !== 'edit')
    $('#s-buscar').classList.toggle('oculta', sel.mode === 'edit')
}
function hideSel() {
    if (selection?.mode === 'new') { selection.doc?.getSelection()?.removeAllRanges(); unmark() }
    selection = null
    $('#sel').classList.add('oculta')
}

async function highlight(color, withNote) {
    const sel = selection
    if (!sel) return
    if (sel.mode === 'edit') {
        sel.note.color = color
        await view.addAnnotation(sel.note)
        saveNotes()
        hideSel()
        if (withNote) editNote(sel.note)
        return
    }
    const n = { value: sel.value, color, text: sel.text, note: '', created: Date.now(), index: sel.index, label: label() }
    notes.push(n)
    await view.addAnnotation(n)
    saveNotes()
    hideSel()
    if (withNote) editNote(n)
    else aviso('Subrayado')
}
for (const b of document.querySelectorAll('#sel .c')) b.addEventListener('click', () => highlight(b.dataset.c, false))
$('#s-nota').addEventListener('click', () => {
    if (selection?.mode === 'edit') { const n = selection.note; hideSel(); editNote(n) }
    else highlight('amarillo', true)
})
$('#s-copiar').addEventListener('click', () => { const t = selection?.mode === 'edit' ? selection.note.text : selection?.text; if (t) { A.copy(t); aviso('Copiado') } hideSel() })
$('#s-compartir').addEventListener('click', () => {
    const t = selection?.mode === 'edit' ? selection.note.text : selection?.text
    if (t) A.share(`«${t}»\n— ${st.title || ''}`)
    hideSel()
})
$('#s-buscar').addEventListener('click', () => { const t = selection?.text; hideSel(); if (t) openSearch(t) })
$('#s-borrar').addEventListener('click', () => {
    const n = selection?.note
    hideSel()
    if (!n) return
    notes = notes.filter(x => x !== n)
    view.deleteAnnotation(n)
    saveNotes()
    aviso('Subrayado borrado')
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
$('#velo').addEventListener('click', closePanel)

function editNote(n) {
    openPanel(`<h2>Tu nota</h2>
        <p class="cita" style="--c:${COLORS[n.color] || COLORS.amarillo}">${esc(n.text)}</p>
        <textarea id="nota-txt" placeholder="Escribe lo que te sugiere…">${esc(n.note)}</textarea>
        <div class="fila" style="margin-top:12px;justify-content:flex-end">
          <button class="boton suave" id="nota-no">Cancelar</button><button class="boton" id="nota-si">Guardar</button>
        </div>`, el => {
        const t = el.querySelector('#nota-txt'); setTimeout(() => t.focus(), 300)
        el.querySelector('#nota-no').onclick = closePanel
        el.querySelector('#nota-si').onclick = () => { n.note = t.value.trim(); saveNotes(); closePanel(); aviso('Nota guardada') }
    })
}

// Ajustes de letra
function openSettings() {
    const chips = (list, cur, key) => list.map(([v, t]) => `<button class="chip ${cur === v ? 'on' : ''}" data-k="${key}" data-v="${v}">${t}</button>`).join('')
    openPanel(`<h2>Letra y página</h2>
      <h3>Tema</h3>
      <div class="fila">${THEMES.map(([v, t]) => `<button class="tema ${S.theme === v ? 'on' : ''}" data-theme-btn="${v}" title="${t}" style="background:var(--t-${v})">Aa</button>`).join('')}</div>
      ${isPDF ? `
        <h3>Tamaño de la página</h3>
        <div class="fila">${chips([['fit-width', 'Ajustar al ancho'], ['fit-page', 'Página entera']], S.zoom, 'zoom')}</div>
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
        <h3>Pasar página</h3>
        <div class="fila">${chips([['paginated', 'Páginas'], ['scrolled', 'Desplazamiento continuo']], S.flow, 'flow')}</div>
      `}
      <h3>Pantalla</h3>
      <div class="fila">${chips([[true, 'Siempre encendida'], [false, 'Normal']], S.keepOn, 'keepOn')}</div>`, el => {
        el.querySelectorAll('[data-theme-btn]').forEach(b => {
            const t = b.dataset.themeBtn
            b.style.background = { papel: '#FBF8F1', sepia: '#F1E6CF', gris: '#2B2D31', noche: '#121317', negro: '#000' }[t]
            b.style.color = { papel: '#1F1D1A', sepia: '#43341F', gris: '#E3E1DC', noche: '#D9D6CF', negro: '#BEBBB4' }[t]
            b.onclick = () => { S.theme = t; applySettings(); saveSettings(); openSettings() }
        })
        el.querySelectorAll('.chip[data-k]').forEach(b => b.onclick = () => {
            const k = b.dataset.k; let v = b.dataset.v
            if (v === 'true' || v === 'false') v = v === 'true'
            S[k] = v; applySettings(); saveSettings(); openSettings()
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

// Índice
function openToc() {
    const toc = view?.book?.toc || []
    const cur = last?.tocItem?.href
    const items = []
    const walk = (list, depth) => { for (const t of list) { items.push([t, depth]); if (t.subitems?.length) walk(t.subitems, depth + 1) } }
    walk(toc, 0)
    openPanel(`<h2>Índice</h2>${items.length ? `<ul class="lista">${items.map(([t, d], i) =>
        `<li class="n${Math.min(d, 3)} ${t.href === cur ? 'actual' : ''}" data-i="${i}">${esc(t.label?.trim() || 'Sin título')}</li>`).join('')}</ul>`
        : '<p class="vacio">Este libro no trae índice.</p>'}`, el => {
        el.querySelectorAll('li[data-i]').forEach(li => li.onclick = () => { view.goTo(items[li.dataset.i][0].href); closePanel() })
        el.querySelector('.actual')?.scrollIntoView({ block: 'center' })
    })
}

// Subrayados, notas y marcadores
function openNotes(tab = 'notas') {
    const list = tab === 'notas'
        ? (notes.length ? `<ul class="lista">${[...notes].sort((a, b) => (a.index - b.index) || (a.created - b.created)).map((n, i) => `
            <li data-v="${esc(n.value)}"><div class="cita" style="--c:${COLORS[n.color] || COLORS.amarillo}">${esc(n.text)}</div>
              ${n.note ? `<div class="nota">✎ ${esc(n.note)}</div>` : ''}
              <div class="sub">${esc(n.label || '')} · ${fecha(n.created)}</div></li>`).join('')}</ul>`
            : '<p class="vacio">Mantén pulsado el texto para subrayarlo o dejar una nota.</p>')
        : (marks.length ? `<ul class="lista">${[...marks].sort((a, b) => a.fraction - b.fraction).map(m => `
            <li data-v="${esc(m.value)}"><b>${esc(m.label || 'Marcador')}</b><div class="sub">${pct(m.fraction)} · ${fecha(m.created)}</div></li>`).join('')}</ul>`
            : '<p class="vacio">Toca el marcador de arriba para guardar la página.</p>')
    openPanel(`<h2>Tus notas</h2>
      <div class="pestanas"><button class="chip ${tab === 'notas' ? 'on' : ''}" data-tab="notas">Subrayados (${notes.length})</button>
      <button class="chip ${tab === 'marcas' ? 'on' : ''}" data-tab="marcas">Marcadores (${marks.length})</button></div>
      ${list}
      ${tab === 'notas' && notes.length ? '<div class="fila" style="margin-top:14px"><button class="boton suave" id="exportar">Compartir todas</button></div>' : ''}`, el => {
        el.querySelectorAll('[data-tab]').forEach(b => b.onclick = () => openNotes(b.dataset.tab))
        el.querySelectorAll('li[data-v]').forEach(li => li.onclick = () => { view.goTo(li.dataset.v); closePanel() })
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

// ---------------------------------------------------------------- Botones
$('#b-cerrar').onclick = () => A.close()
$('#b-indice').onclick = openToc
$('#b-notas').onclick = () => openNotes('notas')
$('#b-ajustes').onclick = openSettings
$('#b-brillo').onclick = openBrightness
$('#b-buscar').onclick = () => openSearch()
$('#b-marca').onclick = () => {
    const m = currentMark()
    if (m) { marks = marks.filter(x => x !== m); aviso('Marcador quitado') }
    else if (last) { marks.push({ value: last.cfi, label: label() || pct(last.fraction), fraction: last.fraction || 0, created: Date.now() }); aviso('Página marcada') }
    $('#b-marca').classList.toggle('on', !!currentMark())
    saveMarks()
}

// Atrás de Android: primero se cierra lo que haya abierto
window.atrioBack = () => {
    if (!$('#palabra').classList.contains('oculta')) { hideWord(); return true }
    if (pend) { pend = null; $('#pend').classList.add('oculta'); unmark(); return true }
    if (selection) { hideSel(); return true }
    if ($('#panel').classList.contains('on')) { closePanel(); return true }
    if (chromeOn) { showChrome(false); return true }
    return false
}
// Teclas de volumen para pasar página (las manda la app)
window.atrioPage = d => { if (d > 0) view?.goRight(); else view?.goLeft() }

open()
