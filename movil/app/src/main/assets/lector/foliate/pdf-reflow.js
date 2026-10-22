// Atrio: PDF de novelas como texto que se adapta a la pantalla («reflow»).
// Se saca el texto de cada página con pdf.js (con su posición y su fuente), se reconstruyen las líneas, los
// párrafos, los diálogos, los separadores de escena y los capítulos, y todo se le da a foliate como un libro
// normal (capítulos en HTML): así valen el tipo de letra, los temas, los subrayados, la búsqueda y la lectura en voz alta.
// Es heurístico: sirve para texto en una columna (novelas). Un PDF escaneado no tiene texto (no hay OCR).
import { abrirPDF, portadaPDF } from './pdf.js'

// Si cambia cómo se reconstruye el texto, sube este número: la posición guardada ya no encajaría del todo
export const VERSION = 5

const FUENTE_I = /ital|obli|cursiv|kursiv|slanted|-it\b|\bit$/i
const FUENTE_B = /bold|black|heavy|semibold|demi|\bbd\b|-bd\b|negrita|fett|extrabold/i
const R = x => Math.round(x * 10) / 10
const tick = () => new Promise(r => setTimeout(r))

// ---------------------------------------------------------------- 1. Extraer: páginas → líneas
function infoFuente(page, k) {
    try {
        const f = page.commonObjs.get(k)
        const n = String(f?.name || f?.loadedName || '')
        return { i: f?.italic === true || FUENTE_I.test(n), b: f?.bold === true || FUENTE_B.test(n) }
    } catch (e) { return { i: false, b: false } }
}

/** Dos columnas: el hueco vertical entre ellas (o null). */
function canalCentral(items, W, H) {
    // (sin las cabeceras y los pies, que suelen cruzar el hueco)
    const finos = items.filter(i => i.x1 - i.x0 < W * 0.55 && i.t.trim() && i.y > H * 0.11 && i.y < H * 0.89)
    if (finos.length < 20) return null
    const lo = Math.floor(W * 0.3), hi = Math.ceil(W * 0.7)
    const cubierto = new Uint16Array(hi - lo + 1)
    for (const i of finos) for (let x = Math.max(lo, Math.floor(i.x0)); x <= Math.min(hi, Math.ceil(i.x1)); x++) cubierto[x - lo]++
    let best = null, ini = -1
    for (let x = 0; x <= hi - lo + 1; x++) {
        const vacio = x <= hi - lo && cubierto[x] === 0
        if (vacio && ini < 0) ini = x
        if (!vacio && ini >= 0) { if (x - ini >= 10 && (!best || x - ini > best[1] - best[0])) best = [ini, x]; ini = -1 }
    }
    if (!best) return null
    const g = lo + (best[0] + best[1]) / 2
    const izq = finos.filter(i => (i.x0 + i.x1) / 2 < g).length
    if (izq < finos.length * 0.25 || izq > finos.length * 0.75) return null
    return g
}

function aLineas(items) {
    const lines = []
    items.sort((a, b) => a.y - b.y || a.x0 - b.x0)
    let cur = null
    for (const it of items) {
        // misma línea: casi la misma altura, o un superíndice/subíndice (más pequeño, algo subido o bajado)
        const mismaLinea = cur && (Math.abs(it.y - cur.y) <= 0.6 * Math.min(it.s, cur.s) || (() => {
            const gr = Math.max(it.s, cur.s), pq = Math.min(it.s, cur.s)
            return gr / pq >= 1.15 && gr / pq <= 1.8 && Math.abs(it.y - cur.y) <= 0.55 * gr
        })())
        if (mismaLinea) {
            cur.items.push(it)
            if (it.s > cur.s) { cur.s = it.s; cur.y = it.y }
        } else lines.push(cur = { y: it.y, s: it.s, items: [it] })
    }
    return lines.map(l => montar(l)).filter(Boolean)
}

/** Las piezas de una línea → texto con cursivas y negritas. */
function montar(l) {
    const its = l.items.sort((a, b) => a.x0 - b.x0)
    const runs = []
    let prev = null, chars = 0, sizeW = 0
    for (const it of its) {
        let t = it.t.replace(/ /g, ' ')
        if (!t) continue
        let pre = ''
        // una pieza que solo es un espacio: la separación entre palabras (así se distingue de la separación entre letras)
        if (!t.trim()) { if (prev && !/\s$/.test(prev.t)) t = '  '; else continue }
        else if (prev) {
            const gap = it.x0 - prev.x1
            const em = Math.max(it.s, prev.s)
            if (gap > em * 0.5 && !/\s$/.test(prev.t) && !/^\s/.test(t)) pre = '  '
            else if (gap > em * 0.15 && !/\s$/.test(prev.t) && !/^\s/.test(t)) pre = ' '
        }
        const sup = it.s < l.s * 0.8 && it.y < l.y - l.s * 0.15
        const last = runs[runs.length - 1]
        // (una letra capital, o un cambio de tamaño grande, no se mezcla con el resto)
        if (last && last.i === it.i && last.b === it.b && !!last.sup === sup && Math.abs(last.s - it.s) < last.s * 0.4) last.t += pre + t
        else runs.push({ t: pre + t, i: it.i, b: it.b, sup, s: R(it.s), x0: R(it.x0) })
        if (t.trim()) { chars += t.trim().length; sizeW += it.s * t.trim().length }
        prev = t.trim() ? it : { ...it, t: '  ', x1: it.x1 }
    }
    if (!runs.length || !chars) return null
    const x0 = its[0].x0, x1 = Math.max(...its.map(i => i.x1))
    return { x0: R(x0), x1: R(x1), y: R(l.y), s: R(sizeW / chars), n: chars, runs }
}

function piezasDePagina(tc, vp, fuentes, Util) {
    const W = vp.width, H = vp.height
    const items = []
    for (const it of tc.items) {
        if (!it.str || !it.str.length) continue
        const m = Util.transform(vp.transform, it.transform)
        const s = Math.hypot(m[0], m[1])
        if (!(s > 1) || Math.abs(m[1]) > Math.abs(m[0]) * 0.5) continue      // vacío o girado (marcas de agua, lomo)
        const f = fuentes.get(it.fontName) || {}
        // (cursiva también si el texto va inclinado: fuentes sin «Italic» en el nombre, o cursiva simulada)
        const sesgo = Math.abs(m[2]) / (Math.abs(m[3]) || 1)
        items.push({ t: it.str, x0: m[4], x1: m[4] + (it.width || 0), y: m[5], s, i: !!f.i || (sesgo > 0.1 && sesgo < 0.7), b: !!f.b })
    }
    return { W, H, items }
}

function lineasDePagina({ W, H, items }, gDoc) {
    let g = canalCentral(items, W, H)
    // un título que cruza el hueco entre columnas no debe hacer que esa página se lea como una sola
    if (g == null && gDoc != null) {
        const finos = items.filter(i => i.x1 - i.x0 < W * 0.55 && i.t.trim() && i.y > H * 0.11 && i.y < H * 0.89)
        const izq = finos.filter(i => (i.x0 + i.x1) / 2 < gDoc).length
        if (finos.length >= 20 && izq >= finos.length * 0.25 && izq <= finos.length * 0.75) g = gDoc
    }
    let lines
    if (g == null) lines = aLineas(items)
    else {
        // lo que cruza el hueco (títulos, cabeceras, pies, números de página) va a todo el ancho, no a una columna
        const cruza = i => i.y < H * 0.11 || i.y > H * 0.89 || (i.x0 < g - 2 && i.x1 > g + 2) || i.x1 - i.x0 >= W * 0.55
        const ancho = items.filter(cruza)
        const fuera = new Set(ancho)
        let col = items.filter(i => !fuera.has(i))
        // donde empiezan las dos columnas: lo que hay más arriba en una sola (el «1» de «CAPÍTULO 1») es del título
        const arriba = lado => Math.min(...col.filter(lado).map(i => i.y))
        const top = Math.max(arriba(i => (i.x0 + i.x1) / 2 < g), arriba(i => (i.x0 + i.x1) / 2 >= g))
        for (const i of col) if (i.y < top - 3) fuera.add(i)
        col = col.filter(i => !fuera.has(i))
        const izq = col.filter(i => (i.x0 + i.x1) / 2 < g), der = col.filter(i => (i.x0 + i.x1) / 2 >= g)
        const arribaAncho = items.filter(i => fuera.has(i) && i.y < top - 3)
        const encima = arribaAncho, debajo = items.filter(i => fuera.has(i) && i.y >= top - 3)
        const c = n => l => ({ ...l, col: n })
        lines = [...aLineas(encima).map(c(-1)), ...aLineas(izq).map(c(0)), ...aLineas(der).map(c(1)), ...aLineas(debajo).map(c(-1))]
    }
    return { cols: g != null, lines }
}

/** Todas las páginas. `onProgress(n, total)` y `cancelado()` opcionales. */
export async function extraer(pdf, { onProgress, cancelado } = {}) {
    const Util = globalThis.pdfjsLib.Util
    const fuentes = new Map()
    const piezas = new Array(pdf.numPages)
    let hechas = 0
    const una = async n => {
        const page = await pdf.getPage(n)
        const vp = page.getViewport({ scale: 1 })
        const tc = await page.getTextContent()
        if (Object.keys(tc.styles).some(k => !fuentes.has(k))) {
            try { await page.getOperatorList() } catch (e) { }
            for (const k of Object.keys(tc.styles)) if (!fuentes.has(k)) fuentes.set(k, infoFuente(page, k))
        }
        piezas[n - 1] = piezasDePagina(tc, vp, fuentes, Util)
        page.cleanup()
    }
    // (de seis en seis: el hilo de pdf.js trabaja mientras éste ordena lo anterior)
    for (let n = 1; n <= pdf.numPages; n += 6) {
        if (cancelado?.()) throw new Error('cancelado')
        const lote = []
        for (let k = n; k < Math.min(n + 6, pdf.numPages + 1); k++) lote.push(una(k))
        await Promise.all(lote)
        hechas += lote.length
        // un PDF escaneado no tiene texto: no hace falta mirarlo entero para saberlo
        if (hechas >= 24 && hechas < 36 && pdf.numPages > 60) {
            const letras = piezas.slice(0, hechas).reduce((a, p) => a + p.items.reduce((b, i) => b + i.t.trim().length, 0), 0)
            if (letras < hechas * 8) { const e = new Error('Este PDF no tiene texto que adaptar (parece escaneado).'); e.sinTexto = true; throw e }
        }
        onProgress?.(hechas, pdf.numPages); await tick()
    }
    // dos columnas: si la mayoría de las páginas tienen el mismo hueco central, vale para todas
    const gs = piezas.map(p => canalCentral(p.items, p.W, p.H)).filter(g => g != null).sort((a, b) => a - b)
    const gDoc = gs.length >= Math.max(2, piezas.length * 0.4) ? gs[gs.length >> 1] : null
    return piezas.map(p => ({ w: R(p.W), h: R(p.H), ...lineasDePagina(p, gDoc) }))
}

// ---------------------------------------------------------------- 2. Analizar: líneas → párrafos y capítulos
const pct = (arr, p) => { if (!arr.length) return 0; const a = [...arr].sort((x, y) => x - y); return a[Math.min(a.length - 1, Math.max(0, Math.floor(p * (a.length - 1))))] }
const moda = (arr, paso) => {
    const m = new Map()
    for (const [v, w] of arr) { const k = Math.round(v / paso); m.set(k, (m.get(k) || 0) + w) }
    let best = null, bw = -1
    for (const [k, w] of m) if (w > bw) { bw = w; best = k }
    return best == null ? 0 : best * paso
}
const textoDe = l => l.runs.map(r => r.t).join('')
const CAP_RE = /^(cap[ií]tulo|cap\.|chapter|parte|part|libro|book|secci[oó]n|section|pr[oó]logo|prologue|ep[ií]logo|epilogue|interludio|introducci[oó]n|introduction|pref[aá]cio|prefacio|preface|agradecimientos|acknowledg|nota del autor|ap[eé]ndice|appendix|cap[ií]tol|chapitre|kapitel|capitolo)(\s|$|[.:\-–—])/i
// (con mayúscula inicial y sin una frase dentro: «libro de mareas. En el piso…» es texto, no un título)
const esTipoCap = t => CAP_RE.test(t) && /^\p{Lu}/u.test(t) && !/[.!?…]\s+\p{Lu}/u.test(t) && t.length <= 70
const NUM_RE = /^\s*(\d{1,3}|[ivxlcdm]{1,8})\s*[.)]?\s*$/i
const NUM_PAG = /^\s*[-–—·•\[(]?\s*(\d{1,4}|[ivxlcdm]{1,7})\s*[-–—·•\])]?\s*$|^\s*(p[aá]g(ina|\.)?|page)\s*\d+(\s*(de|of|\/)\s*\d+)?\s*$|^\s*\d+\s*[\/|]\s*\d+\s*$/i
const SEP_RE = /^[\s*·•⁂~∗✱#_=+◆◇❖✦❧☙§†‡∙○●■□▪◦–—\-.]{1,14}$/
const normaliza = t => t.toLowerCase().replace(/\d+/g, '#').replace(/\s+/g, ' ').trim()
const esSep = t => {
    const s = t.trim()
    if (!s || !SEP_RE.test(s) || /\d|\p{L}/u.test(s)) return false
    const simb = s.replace(/\s/g, '')
    if (/^[–—\-.]+$/.test(simb)) return simb.length >= 3 && !/^\.{3}$/.test(simb) && !/^…$/.test(simb)
    return true
}
const FIN_RE = /[.!?…:;»”"’')\]]["»”’')\]]*$/
// (la raya de un diálogo nuevo va seguida de mayúscula o signo de apertura; «—dijo ella» en minúscula es una aclaración del mismo párrafo)
const RAYA_RE = /^\s*[—–―]\s?[¿¡«“"'\p{Lu}\d]/u
// (algunos PDF escriben los diálogos con un guion normal: «-¿Qué quieres? -dijo»)
const GUION_RE = /^\s*-\s?[¿¡\p{Lu}«“"]/u

/** El texto «de verdad» de las páginas: sin cabeceras ni números de página, con párrafos, diálogos y capítulos. */
export function analizar(pages, { outline } = {}) {
    // ---- tamaño del texto, interlineado
    const tam = []
    for (const p of pages) for (const l of p.lines) tam.push([l.s, l.n])
    const body = moda(tam, 0.25) || 11
    const cuerpo = l => Math.abs(l.s - body) <= body * 0.14
    const pitch = []
    for (const p of pages) for (let i = 1; i < p.lines.length; i++) {
        const a = p.lines[i - 1], b = p.lines[i], d = b.y - a.y
        if (cuerpo(a) && cuerpo(b) && d > body * 0.85 && d < body * 2.2) pitch.push([d, 1])
    }
    const lead = moda(pitch, 0.25) || body * 1.3

    // ---- cabeceras y pies: lo que se repite arriba y abajo de las páginas, y los números de página
    const cuenta = new Map()
    const zona = (p, l) => l.y < p.h * 0.11 || l.y > p.h * 0.89
    for (const p of pages) {
        const seen = new Set()
        for (const l of [...p.lines.slice(0, 2), ...p.lines.slice(-2)]) {
            if (!zona(p, l)) continue
            const k = normaliza(textoDe(l)); if (seen.has(k)) continue
            seen.add(k); cuenta.set(k, (cuenta.get(k) || 0) + 1)
        }
    }
    const minRep = Math.max(3, Math.round(pages.length * 0.1))
    // Cabeceras con el título del capítulo: se repiten en las páginas vecinas aunque en todo el libro sean pocas
    const claveArriba = pages.map(p => { const l = p.lines[0]; return l && zona(p, l) ? normaliza(textoDe(l)) : null })
    const claveAbajo = pages.map(p => { const l = p.lines[p.lines.length - 1]; return l && zona(p, l) ? normaliza(textoDe(l)) : null })
    const aparte = (p, i, arriba) => {      // ¿separada del texto, más pequeña, en cursiva o en mayúsculas?
        const l = p.lines[i], v = p.lines[arriba ? i + 1 : i - 1]
        const t = textoDe(l).trim()
        return (v && Math.abs(v.y - l.y) >= lead * 1.35) || l.s <= body * 0.95 || l.runs.every(r => r.i || !r.t.trim()) || (t === t.toUpperCase() && /\p{L}/u.test(t))
    }
    const repetida = (claves, i) => {
        if (!claves[i]) return false
        for (let d = 1; d <= 8; d++) if (claves[i - d] === claves[i] || claves[i + d] === claves[i]) return true
        return false
    }
    const limpias = pages.map((p, pi) => {
        // una página de índice (líneas con puntos de relleno y un número al final) no se lleva al texto: el libro ya tiene su índice
        const conNum = p.lines.filter(l => /(\.{3,}|…|\s{2,})\s*(\d{1,4}|[ivxlc]{1,6})\s*$/i.test(textoDe(l).trimEnd())).length
        if (p.lines.length >= 6 && conNum >= p.lines.length * 0.7) return { ...p, lines: [] }
        const lines = p.lines.filter((l, i) => {
            // (a dos columnas, la cabecera y el pie no están ni al principio ni al final de la lista de líneas: se mira dónde caen)
            const extremo = p.cols || i < 2 || i >= p.lines.length - 2
            if (!extremo || !zona(p, l)) return true
            const t = textoDe(l)
            if (NUM_PAG.test(t)) return false
            if ((cuenta.get(normaliza(t)) || 0) >= minRep) return false
            if (i === 0 && repetida(claveArriba, pi) && aparte(p, 0, true)) return false
            if (i === p.lines.length - 1 && repetida(claveAbajo, pi) && aparte(p, i, false)) return false
            return true
        })
        return { ...p, lines }
    })
    const guionDialogo = limpias.reduce((a, p) => a + p.lines.filter(l => GUION_RE.test(textoDe(l))).length, 0) >= 10
    const empiezaDialogo = t => RAYA_RE.test(t) || (guionDialogo && GUION_RE.test(t))
    // El vocabulario del libro: palabras compuestas con guion (para no pegar «franco-prusiano» al partirlas) y palabras sueltas
    const compuestas = new Set(), palabras = new Map()
    for (const p of limpias) for (const l of p.lines) {
        const t = textoDe(l)
        for (const m of t.matchAll(/\p{L}[\p{L}'’]*-\p{L}[\p{L}'’-]*/gu)) compuestas.add(m[0].toLowerCase())
        for (const m of t.matchAll(/\p{L}+/gu)) { const w = m[0].toLowerCase(); palabras.set(w, (palabras.get(w) || 0) + 1) }
    }
    // Separación entre párrafos de este libro (con espacio entre párrafos), para distinguir luego un cambio de escena
    const huecos = []
    for (const p of limpias) for (let i = 1; i < p.lines.length; i++) {
        const a = p.lines[i - 1], b = p.lines[i], d = b.y - a.y
        if (cuerpo(a) && cuerpo(b) && d > lead * 1.15 && d < lead * 2.6) huecos.push([d, 1])
    }
    const g0 = huecos.length >= 8 ? moda(huecos, 0.25) : lead
    const hueco = Math.max(lead * 1.75, g0 + lead * 0.85)

    // ---- márgenes y borde derecho por paridad de página (márgenes espejo)
    const cols = [[], []], der = [[], []], todo = { x0: [], x1: [] }
    const colX = [[[], []], [[], []]]     // dos columnas: [columna][x0, x1]
    limpias.forEach((p, k) => {
        if (p.cols) {
            for (const l of p.lines) if (cuerpo(l) && l.n > 8 && (l.col === 0 || l.col === 1)) { colX[l.col][0].push(l.x0); colX[l.col][1].push(l.x1); todo.x1.push(l.x1) }
            return
        }
        for (const l of p.lines) if (cuerpo(l) && l.n > 12) { cols[k % 2].push(l.x0); der[k % 2].push(l.x1); todo.x0.push(l.x0); todo.x1.push(l.x1) }
    })
    const marg = [0, 1].map(k => (cols[k].length > 25 ? pct(cols[k], 0.1) : pct(todo.x0, 0.1)))
    const borde = [0, 1].map(k => (der[k].length > 25 ? pct(der[k], 0.92) : pct(todo.x1, 0.92)))
    if (Math.abs(marg[0] - marg[1]) < 2.5) marg[0] = marg[1] = Math.min(...marg)
    if (Math.abs(borde[0] - borde[1]) < 2.5) borde[0] = borde[1] = Math.max(...borde)
    const p92 = pct(todo.x1, 0.92)
    const margC = [0, 1].map(c => pct(colX[c][0], 0.1)), bordeC = [0, 1].map(c => pct(colX[c][1], 0.92))
    if (!todo.x0.length && colX[0][0].length) { marg[0] = marg[1] = margC[0]; borde[0] = borde[1] = bordeC[1] }
    const MB = (pi, l) => (l.col === 0 || l.col === 1) && colX[l.col][0].length ? [margC[l.col], bordeC[l.col]] : [marg[pi % 2], borde[pi % 2]]
    const justificado = todo.x1.length > 0 && todo.x1.filter(x => x >= p92 - 1.5).length / todo.x1.length > 0.4

    // ---- principio de página «normal» (para saber qué capítulos empiezan más abajo)
    const primeras = limpias.filter(p => p.lines.length > 8).map(p => p.lines[0].y)
    const topY = pct(primeras, 0.3)

    // ---- a partir de aquí, bloques
    const blocks = []
    let par = null, prev = null     // párrafo abierto y su última línea
    let capX = null, capN = 0       // sangría de las líneas junto a una letra capital
    let notas = []                  // notas al pie de la página: van detrás del párrafo que estaba abierto
    const sueltaNotas = () => { for (const n of notas) blocks.push(n); notas = [] }
    const cierra = () => { if (par) blocks.push(par); par = null; prev = null; sueltaNotas() }
    const abre = (l, page) => { par = { k: 'p', runs: [], page }; prev = null }
    const centro = (l, p, k) => { const [m, b] = MB(k, l); return Math.abs((l.x0 + l.x1) / 2 - (m + b) / 2) < p.w * 0.045 }

    limpias.forEach((p, pi) => {
        const k = pi
        let ls = p.lines
        // letra pequeña: en una página casi vacía (créditos, ISBN) se descarta; al pie de una página de texto son notas
        const nCuerpo = ls.filter(cuerpo).length
        const pequena = l => l.s <= body * 0.9 && !l.runs.some(r => r.sup && false)
        const notaLs = []
        if (nCuerpo < 10) ls = ls.filter(l => !pequena(l))
        else {
            let ult = -1
            ls.forEach((l, i) => { if (cuerpo(l)) ult = i })
            ls = ls.filter((l, i) => { if (i > ult && pequena(l) && l.y > p.h * 0.6) { notaLs.push(l); return false } return true })
        }
        // letra capital (una letra enorme al principio de un párrafo): se pega a la primera línea del párrafo
        for (let i = 0; i < ls.length; i++) {
            const l = ls[i], r0 = l.runs[0]
            if (r0 && r0.s >= body * 1.8 && r0.t.trim().length <= 2 && /^\p{L}/u.test(r0.t.trim()) && l.runs.length > 1 || (r0 && r0.s >= body * 1.8 && r0.t.trim().length <= 2 && /^\p{L}/u.test(r0.t.trim()) && l.n <= 2 && i + 1 < ls.length)) {
                const cap = r0.t.trim()
                const xFin = r0.x0 + r0.s * 0.7 * cap.length
                l.runs.shift(); l.n -= cap.length
                if (!l.runs.length) { ls.splice(i, 1); i-- }
                else { l.x0 = l.runs[0].x0; l.s = body }
                // la primera línea del párrafo: la más alta de las que van pegadas a la letra
                let j = i
                while (j - 1 >= 0 && ls[j - 1].x0 > r0.x0 + r0.s * 0.3 && l.y - ls[j - 1].y < lead * 3.2) j--
                if (j >= 0 && j < ls.length) { const t = ls[j]; t.xTexto = t.x0; t.runs.unshift({ t: cap, i: false, b: false, sup: false, s: body, x0: r0.x0 }); t.x0 = Math.min(t.x0, r0.x0); t.n += cap.length; t.cap = true; void xFin }
            }
        }
        const info = ls.map((l, i) => {
            const t = textoDe(l)
            const texto = t.replace(/\s+/g, ' ').trim()
            const tipoCap = esTipoCap(texto)
            const num = NUM_RE.test(texto)
            const grande = l.s >= body * 1.18
            const todoNegra = l.runs.every(r => r.b || !r.t.trim())
            const mayus = /\p{L}/u.test(texto) && texto === texto.toUpperCase() && texto.length >= 3 && texto.length <= 60
            const cen = centro(l, p, k)
            const arriba = i === 0 ? Infinity : l.y - ls[i - 1].y, abajo = i === ls.length - 1 ? Infinity : ls[i + 1].y - l.y
            const aislada = arriba > lead * 1.5 && abajo > lead * 1.5
            const corta = texto.length <= 70
            let head = false
            if (corta && !esSep(texto)) {
                if (grande && l.n <= 70 && !FIN_RE.test(texto)) head = true
                else if (tipoCap && (cen || grande || mayus || todoNegra)) head = true
                else if (todoNegra && cen && !FIN_RE.test(texto)) head = true
                else if (mayus && cen && !l.runs.some(r => r.i) && texto.length >= 4) head = true
                else if (num && (cen || grande || aislada)) head = true
            }
            const cap1 = head && (tipoCap || num || l.s >= body * 1.4 || (i === 0 && l.y > topY + lead * 3.5) || (i > 0 && ls[i - 1].y < l.y - lead * 3.5 && i <= 1))
            return { texto, raw: t.trim(), head, cap1, sep: esSep(texto), l }
        })
        const ant = i => info[i - 1]
        for (let i = 0; i < ls.length; i++) {
            const { l, texto, head, sep } = info[i]
            if (sep) { cierra(); blocks.push({ k: 'sep', page: pi }); continue }
            if (head) {
                cierra()
                const lastB = blocks[blocks.length - 1]
                const dg = lastB && lastB.k === 'h' && lastB.page === pi && lastB._i === i - 1 && (info[i].l.y - ant(i).l.y) < lead * 3
                if (dg) { lastB.lines.push(info[i].raw); lastB.cap = lastB.cap || info[i].cap1; lastB._i = i }
                else blocks.push({ k: 'h', lines: [info[i].raw], cap: info[i].cap1, page: pi, _i: i })
                continue
            }
            // línea de texto
            let nuevo = !par
            let salto = false   // salto de línea dentro del párrafo (versos, cartas, letras de canciones)
            if (par && prev) {
                const P = prev
                const mismaPag = prev.pi === pi
                const gap = mismaPag ? l.y - P.l.y : null
                const sangra = l.x0 - MB(k, l)[0] > Math.max(body * 0.55, 4)
                const [mb, bb] = MB(prev.pi, P.l)
                // ¿cabría en la línea anterior la primera palabra de esta? Entonces el párrafo acabó ahí (si no, la habrían subido)
                const pal = texto.split(' ')[0] || ''
                const cw = (l.x1 - l.x0) / Math.max(texto.length, 1)
                const cabe = !/[\u00AD\u2010\u2011-]$/.test(textoDe(P.l).trim()) && bb - P.l.x1 > (pal.length + 1) * cw * 1.04
                const corta = justificado ? P.l.x1 < bb - body * 0.9 : (P.l.x1 < bb - 0.3 * (bb - mb) || cabe)
                const finP = FIN_RE.test(textoDe(P.l).trim())
                if (gap != null && gap > lead * 1.38) nuevo = true
                else if (sangra && !l.cap && !(capX != null && capN > 0 && Math.abs(l.x0 - capX) < 2)) nuevo = true
                else if (empiezaDialogo(texto) && (finP || corta)) nuevo = true
                else if (corta && finP) nuevo = true
                // una línea corta que no acaba en punto y tras la que sigue otra: el autor cortó ahí la línea (verso)
                else if (!finP && P.l.x1 < bb - (justificado ? body * 2.5 : 0.3 * (bb - mb)) && !/[,;]$/.test(textoDe(P.l).trim()) && /^[\p{Lu}¿¡«“"]/u.test(texto)) salto = true
            }
            if (nuevo) {
                // un hueco de una línea entera (o más) entre dos párrafos: cambio de escena sin símbolos
                if (par && prev && prev.pi === pi && l.y - prev.l.y > hueco) { cierra(); blocks.push({ k: 'sep', page: pi, hueco: true }) }
                else cierra()
                abre(l, pi); capX = null; capN = 0
            }
            // con letra capital, las dos o tres primeras líneas van sangradas junto a ella: siguen siendo el mismo párrafo
            if (l.cap) { capX = l.xTexto; capN = 3 } else if (capN > 0) capN--
            // unir con el párrafo: guion de final de línea, espacio entre líneas
            const lr = l.runs.map(r => ({ ...r }))
            if (par.runs.length) {
                const u = par.runs[par.runs.length - 1]
                const primero = lr[0].t.replace(/^\s+/, '')
                if (/\p{L}[­‐‑-]$/u.test(u.t) && /^\p{Ll}/u.test(primero)) {
                    // «franco-» + «prusiano»: si el libro escribe «franco-prusiano» en otro sitio, el guion es de la palabra
                    const ini = (u.t.match(/(\p{L}+)[­‐‑-]$/u) || [])[1] || '', fin = (primero.match(/^\p{L}+/u) || [''])[0]
                    const propio = u.t.length > 0 && compuestas.has((ini + '-' + fin).toLowerCase()) && !palabras.has((ini + fin).toLowerCase())
                    if (!propio) u.t = u.t.slice(0, -1)
                }
                else if (/[­]$/.test(u.t)) u.t = u.t.slice(0, -1)
                else u.t = u.t.replace(/\s+$/, '') + (salto ? '\n' : ' ')
                lr[0].t = lr[0].t.replace(/^\s+/, '')
            } else lr[0].t = lr[0].t.replace(/^\s+/, '')
            par.runs.push(...lr)
            prev = { l, pi }
        }
        // las notas al pie de esta página
        let nota = null
        for (const l of notaLs) {
            const t = textoDe(l).trim()
            if (!nota || /^[\d*†‡¹²³]+[\s.)]/.test(t)) { nota = { k: 'n', runs: [], page: pi }; notas.push(nota) }
            if (nota.runs.length) nota.runs[nota.runs.length - 1].t += ' '
            nota.runs.push(...l.runs.map(r => ({ ...r })))
        }
        if (!par) sueltaNotas()
        // (un párrafo puede seguir en la página siguiente: no se cierra aquí)
    })
    cierra()

    // ---- limpiar los textos de los párrafos (espacios, ligaduras, letras separadas de los títulos)
    for (const b of blocks) {
        if (b.k === 'p' || b.k === 'n') {
            b.runs = fusiona(b.runs.map(r => ({ ...r, t: limpia(r.t) })))
            if (!b.runs.length || !b.runs.some(r => r.t.trim())) b.k = 'x'
        } else if (b.k === 'h') b.lines = b.lines.map(t => segmenta(limpia(letras(t)), palabras)).filter(Boolean)
    }
    const out = blocks.filter(b => b.k !== 'x')
    // un título solo cuenta como capítulo si tras él hay texto (no en la portada ni en la página de créditos)
    out.forEach((b, i) => {
        if (b.k !== 'h' || !b.cap) return
        let n = 0
        for (let j = i + 1; j < out.length && out[j].k !== 'h'; j++) if (out[j].k === 'p') n += out[j].runs.reduce((a, r) => a + r.t.length, 0)
        if (n < 250) b.cap = false
    })
    return { blocks: out, stats: { body, lead, justificado, marg, borde, paginas: pages.length, outline: !!outline } }
}

/** «Lacasadelfaro» → «La casa del faro» con las palabras del propio libro (títulos con las letras separadas, donde pdf.js no ve los espacios). */
const CORTAS = new Set('a y o e u de la el en un al lo se su no me te mi tu si ya es le las los una del con por que sin'.split(' '))
export function segmenta(texto, palabras) {
    if (palabras.size < 30) return texto
    const total = [...palabras.values()].reduce((a, b) => a + b, 0)
    return texto.split(' ').map(tok => {
        const t = tok.toLowerCase()
        if (t.length < 10 || palabras.has(t) || !/^\p{L}+$/u.test(tok)) return tok
        const n = t.length
        const coste = new Array(n + 1).fill(Infinity), ant = new Array(n + 1).fill(-1)
        coste[0] = 0
        for (let i = 1; i <= n; i++) for (let j = Math.max(0, i - 20); j < i; j++) {
            if (coste[j] === Infinity) continue
            const w = t.slice(j, i), len = i - j
            let c = palabras.get(w)
            // (la misma palabra en plural o en femenino cuenta como conocida)
            if (!c && len >= 4) for (const suf of ['s', 'es', 'a', 'as', 'o', 'os']) if (w.endsWith(suf) && palabras.get(w.slice(0, -suf.length))) c = palabras.get(w.slice(0, -suf.length)) * 0.4
            let k
            if (c && (len >= 3 || CORTAS.has(w))) k = -Math.log(c / total) + 1.5
            else if (len >= 4 && len < n) k = 14 + 2 * len  // una palabra que el libro no usa (el título puede tener alguna)
            else continue
            k += coste[j]
            if (k < coste[i]) { coste[i] = k; ant[i] = j }
        }
        if (coste[n] === Infinity) return tok
        const cortes = []
        for (let i = n; i > 0; i = ant[i]) cortes.unshift(i)
        if (cortes.length < 2) return tok
        let ini = 0
        return cortes.map(c => { const w = tok.slice(ini, c); ini = c; return w }).join(' ')
    }).join(' ')
}

const LIG = { 'ﬀ': 'ff', 'ﬁ': 'fi', 'ﬂ': 'fl', 'ﬃ': 'ffi', 'ﬄ': 'ffl', 'ﬅ': 'st', 'ﬆ': 'st' }
function limpia(t) {
    return t.replace(/[ﬀ-ﬆ]/g, c => LIG[c] || c).replace(/­/g, '').replace(/[​﻿]/g, '').replace(/[ \t -  ]+/g, ' ').replace(/(?:\.\s?){4,}/g, ' … ')
}
/** «C A P Í T U L O  1» → «CAPÍTULO 1» (títulos con las letras muy separadas). */
function letras(t) {
    const tk = t.split(/ {2,}/).map(w => {
        const ls = w.split(' ')
        return ls.length >= 4 && ls.filter(x => x.length === 1).length >= ls.length * 0.75 ? ls.join('') : w
    })
    return tk.join('  ')
}
function fusiona(runs) {
    const out = []
    for (const r of runs) {
        const l = out[out.length - 1]
        if (l && l.i === r.i && l.b === r.b && !!l.sup === !!r.sup) l.t += r.t
        else out.push({ t: r.t, i: r.i, b: r.b, sup: !!r.sup })
    }
    // espacios pegados a las cursivas fuera de ellas, y un espacio solo no abre una cursiva
    for (const r of out) r.t = r.t.replace(/ {2,}/g, ' ')
    return out.filter(r => r.t.length)
}

// ---------------------------------------------------------------- 3. Capítulos y HTML
const IDIOMAS = {
    es: ' el la los las de que y en un una por con para es se no lo su al como más pero sus le ya o este sí porque esta entre cuando muy sin sobre ',
    en: ' the of and to in a is that it was for on with as he she his her they you i had at by this from but not be have ',
    fr: ' le la les de des et en un une est que qui dans pour pas sur au avec ce il elle ne se plus par mais ',
    de: ' der die das und ist nicht ein eine zu den mit von auf er sie es ich für dem im auch als ',
    it: ' il lo la i gli le di che e in un una è per con non si da come più ma del della ',
    pt: ' o a os as de que e em um uma é para com não se por mais mas do da no na como ',
    ca: ' el la els les de que i en un una és per amb no es com més però del ',
}
export function idioma(texto) {
    const palabras = texto.toLowerCase().match(/\p{L}+/gu) || []
    const cnt = {}
    const sets = Object.fromEntries(Object.entries(IDIOMAS).map(([k, v]) => [k, new Set(v.trim().split(/\s+/))]))
    for (const w of palabras.slice(0, 4000)) for (const k in sets) if (sets[k].has(w)) cnt[k] = (cnt[k] || 0) + 1
    const best = Object.entries(cnt).sort((a, b) => b[1] - a[1])[0]
    return best && best[1] > 15 ? best[0] : 'es'
}

const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
const runsHTML = runs => runs.map(r => {
    let h = esc(r.t).replace(/\n/g, '<br/>')
    if (r.sup) h = `<sup>${h}</sup>`
    if (r.i) h = `<i>${h}</i>`
    if (r.b) h = `<b>${h}</b>`
    return h
}).join('')
const ETIQUETA = /^(cap[ií]tulo|cap\.|chapter|parte|part|libro|book|secci[oó]n|section|cap[ií]tol|chapitre|kapitel|capitolo)\s+[\w.]{1,12}$|^\s*(\d{1,3}|[ivxlcdm]{1,8})\s*[.)]?\s*$/i
/** Las líneas de un título: «CAPÍTULO 1» + «La casa» van en dos líneas; un título largo partido en tres, en una sola. */
export const lineasTitulo = lines => {
    const out = []
    for (const l of lines) {
        const u = out[out.length - 1]
        if (u && !ETIQUETA.test(u.trim()) && !/[.!?:]$/.test(u.trim())) out[out.length - 1] = u + ' ' + l
        else out.push(l)
    }
    return out
}
export const textoBloque = b => b.k === 'p' || b.k === 'n' ? b.runs.map(r => r.t).join('') : b.k === 'h' ? lineasTitulo(b.lines).join(' · ') : '* * *'

/** Los bloques en capítulos: [{ titulo, blocks }]. Con índice del PDF (outline), por él; si no, por los títulos que se ven. */
export function capitulos(blocks, { outline, paginas } = {}) {
    let cortes = []     // índices de bloque donde empieza un capítulo
    if (outline && outline.length >= 3) {
        for (const o of outline) {
            let idx = blocks.findIndex(b => b.page >= o.page && b.page === o.page && b.k === 'h' && coincide(textoBloque(b), o.title))
            if (idx < 0) idx = blocks.findIndex(b => b.page >= o.page)
            if (idx >= 0) cortes.push({ i: idx, titulo: o.title, nivel: o.nivel || 0 })
        }
    } else {
        blocks.forEach((b, i) => { if (b.k === 'h' && b.cap) cortes.push({ i, titulo: textoBloque(b).replace(/ · /g, ' · '), nivel: 0 }) })
    }
    // sin repeticiones en el mismo bloque
    cortes = cortes.filter((c, k) => k === 0 || c.i > cortes[k - 1].i)
    if (cortes.length < 2) {
        // sin capítulos: trozos de unas 12 páginas
        const paso = 12, res = []
        let ini = 0, pag0 = blocks[0]?.page ?? 0
        blocks.forEach((b, i) => {
            if (b.page - pag0 >= paso && b.k !== 'sep' && i > ini) { res.push({ titulo: `Páginas ${pag0 + 1}–${b.page}`, nivel: 0, blocks: blocks.slice(ini, i) }); ini = i; pag0 = b.page }
        })
        res.push({ titulo: res.length ? `Páginas ${pag0 + 1}–${(paginas || blocks[blocks.length - 1]?.page + 1)}` : 'Texto', nivel: 0, blocks: blocks.slice(ini) })
        return res
    }
    const res = []
    if (cortes[0].i > 0) res.push({ titulo: 'Inicio', nivel: 0, blocks: blocks.slice(0, cortes[0].i), previo: true })
    cortes.forEach((c, k) => res.push({ titulo: c.titulo, nivel: c.nivel, blocks: blocks.slice(c.i, k + 1 < cortes.length ? cortes[k + 1].i : blocks.length) }))
    return res.filter(c => c.blocks.length)
}
const norm = t => t.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, ' ').trim()
const coincide = (a, b) => { a = norm(a); b = norm(b); return !!a && !!b && (a.includes(b) || b.includes(a)) }

function htmlDe(cap, lang, idx) {
    const partes = cap.blocks.map(b => {
        if (b.k === 'h') return `<h2>${lineasTitulo(b.lines).map(esc).join('<br/>')}</h2>`
        if (b.k === 'sep') return b.hueco ? '<p class="sep esp">&#160;</p>' : '<p class="sep">* * *</p>'
        if (b.k === 'n') return `<p class="nota">${runsHTML(b.runs)}</p>`
        return `<p>${runsHTML(b.runs)}</p>`
    })
    return `<!DOCTYPE html><html class="atrio-reflow" lang="${lang}" xml:lang="${lang}"><head><meta charset="utf-8"/><title>${esc(cap.titulo)}</title></head><body>${partes.join('\n')}</body></html>`
}

// ---------------------------------------------------------------- 4. Caché (la extracción es lo lento)
const DB = 'atrio-reflow'
const idb = () => new Promise((ok, no) => {
    try {
        const r = indexedDB.open(DB, 1)
        r.onupgradeneeded = () => r.result.createObjectStore('x')
        r.onsuccess = () => ok(r.result)
        r.onerror = () => no(r.error)
    } catch (e) { no(e) }
})
// (si el almacenamiento del WebView no responde, no se espera: se hace sin caché)
const limite = (p, ms) => Promise.race([p, new Promise(ok => setTimeout(() => ok(null), ms))])
async function leeCache(clave) {
    try {
        return await limite((async () => {
            const db = await idb()
            return await new Promise(ok => { const q = db.transaction('x').objectStore('x').get(clave); q.onsuccess = () => ok(q.result || null); q.onerror = () => ok(null) })
        })(), 3000)
    } catch (e) { return null }
}
async function guardaCache(clave, v) {
    try {
        await limite((async () => {
            const db = await idb()
            // solo se guardan los últimos libros: se borra lo anterior
            const tx = db.transaction('x', 'readwrite'); const st = tx.objectStore('x')
            const claves = await new Promise(ok => { const q = st.getAllKeys(); q.onsuccess = () => ok(q.result || []); q.onerror = () => ok([]) })
            for (const k of claves.slice(0, Math.max(0, claves.length - 5))) st.delete(k)
            st.put(v, clave)
            await new Promise(ok => { tx.oncomplete = ok; tx.onerror = ok; tx.onabort = ok })
        })(), 8000)
    } catch (e) { }
}

/** Un resumen del contenido del archivo (principio y final): otro PDF con el mismo nombre y tamaño no usa la caché de éste. */
async function huella(file) {
    try {
        const b = new Uint8Array(await new Blob([file.slice(0, 4096), file.slice(Math.max(0, file.size - 4096))]).arrayBuffer())
        let h = 2166136261
        for (let i = 0; i < b.length; i++) { h ^= b[i]; h = Math.imul(h, 16777619) >>> 0 }
        return h.toString(16)
    } catch (e) { return '0' }
}

// ---------------------------------------------------------------- 5. El libro para foliate
/**
 * `file`: el PDF. Devuelve un «libro» de foliate con el texto adaptable, o lanza Error con `.sinTexto = true` si el PDF
 * no tiene texto (escaneado). `onProgress(n, total)` mientras se prepara.
 */
export async function makePDFReflow(file, { onProgress, cancelado, forzar } = {}) {
    const pdf = await abrirPDF(file, { fontExtraProperties: true })
    const clave = `v${VERSION}|${file.name}|${file.size}|${await huella(file)}`
    let pages = (await leeCache(clave))?.pages
    if (!pages) {
        try { pages = await extraer(pdf, { onProgress, cancelado }) } catch (e) { try { pdf.destroy() } catch (x) { } throw e }
        const chars = pages.reduce((a, p) => a + p.lines.reduce((b, l) => b + l.n, 0), 0)
        if (chars < Math.max(100, pages.length * 15)) { const e = new Error('Este PDF no tiene texto que adaptar (parece escaneado).'); e.sinTexto = true; try { pdf.destroy() } catch (x) { } throw e }
        guardaCache(clave, { pages })
    }
    onProgress?.(pages.length, pages.length)
    const { metadata, info } = (await pdf.getMetadata().catch(() => null)) ?? {}

    // el índice del PDF, si lo hay
    let outline = null
    try {
        const o = await pdf.getOutline()
        if (o?.length) {
            const plano = []
            const rec = async (items, nivel) => {
                for (const it of items) {
                    try {
                        const dest = typeof it.dest === 'string' ? await pdf.getDestination(it.dest) : it.dest
                        if (dest) plano.push({ title: String(it.title || '').trim(), page: await pdf.getPageIndex(dest[0]), nivel })
                    } catch (e) { }
                    if (it.items?.length) await rec(it.items, nivel + 1)
                }
            }
            await rec(o, 0)
            outline = plano.filter(p => p.title)
        }
    } catch (e) { }

    const { blocks } = analizar(pages, { outline })
    // Poco texto por página (un cómic, un libro ilustrado, un folleto): adaptarlo estropearía más que ayuda
    const letrasTotal = blocks.reduce((a, b) => a + (b.k === 'p' ? b.runs.reduce((x, r) => x + r.t.length, 0) : 0), 0)
    if (!forzar && letrasTotal / pages.length < 350) {
        const e = new Error('Este PDF tiene muy poco texto por página.'); e.pocoTexto = true
        try { pdf.destroy() } catch (x) { }
        throw e
    }
    const caps = capitulos(blocks, { outline, paginas: pages.length })
    const todo = blocks.filter(b => b.k === 'p').map(textoBloque).join(' ')
    const lang = (typeof metadata?.get?.('dc:language') === 'string' && metadata.get('dc:language').slice(0, 2)) || idioma(todo)

    const urls = []
    const book = { rendition: { layout: 'reflowable' }, dir: 'ltr' }
    book.metadata = {
        title: metadata?.get?.('dc:title') ?? info?.Title,
        author: metadata?.get?.('dc:creator') ?? info?.Author,
        language: lang,
        description: metadata?.get?.('dc:description') ?? info?.Subject,
    }
    book.sections = caps.map((c, index) => {
        const html = htmlDe(c, lang, index)
        const blob = new Blob([html], { type: 'text/html' })
        const url = URL.createObjectURL(blob)
        urls.push(url)
        return {
            id: index, load: () => url, unload: () => { },
            createDocument: () => new DOMParser().parseFromString(html, 'text/html'),
            size: blob.size, linear: undefined,
        }
    })
    // el índice del libro: capítulos (anidados según el índice del PDF si lo hay)
    const toc = []
    const pila = []
    caps.forEach((c, i) => {
        if (c.previo) return
        const item = { label: c.titulo, href: String(i), subitems: null }
        const nivel = c.nivel || 0
        while (pila.length && pila[pila.length - 1].nivel >= nivel) pila.pop()
        const padre = pila[pila.length - 1]
        if (padre) (padre.item.subitems ||= []).push(item); else toc.push(item)
        pila.push({ nivel, item })
    })
    book.toc = toc
    book.resolveHref = href => ({ index: Number(String(href).split('#')[0]) || 0 })
    book.splitTOCHref = href => [Number(String(href).split('#')[0]) || 0, null]
    book.getTOCFragment = doc => doc.documentElement
    book.isExternal = uri => /^\w+:/i.test(uri)
    book.getCover = async () => portadaPDF(pdf)
    book.destroy = () => { for (const u of urls) URL.revokeObjectURL(u); try { pdf.destroy() } catch (e) { } }
    book.pdf = pdf
    book.reflow = true
    book.paginasPDF = pages.length
    return book
}
