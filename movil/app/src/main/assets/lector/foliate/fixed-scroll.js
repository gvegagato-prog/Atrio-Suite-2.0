// Atrio: PDF y cómics (CBZ) leyendo hacia abajo, con las páginas una debajo de otra (para manga y webtoons).
// Es la alternativa a fixed-layout.js (que enseña una página cada vez) con la misma interfaz para view.js:
// open, goTo, next, prev, getContents, destroy y los eventos «load» y «relocate».
// Solo se cargan las páginas que están cerca de la pantalla; las lejanas se descargan para no gastar memoria.

const parseViewport = str => str
    ?.split(/[,;\s]/)
    ?.filter(x => x)
    ?.map(x => x.split('=').map(x => x.trim()))

// (como en fixed-layout.js)
const getViewport = (doc, viewport) => {
    if (doc.documentElement.localName === 'svg') {
        const [, , width, height] = doc.documentElement.getAttribute('viewBox')?.split(/\s/) ?? []
        return { width, height }
    }
    const meta = parseViewport(doc.querySelector('meta[name="viewport"]')?.getAttribute('content'))
    if (meta) return Object.fromEntries(meta)
    if (typeof viewport === 'string') return parseViewport(viewport)
    if (viewport?.width && viewport.height) return viewport
    const img = doc.querySelector('img')
    if (img) return { width: img.naturalWidth, height: img.naturalHeight }
    return { width: 1000, height: 1414 }
}

// Cuánto se carga alrededor de lo que se ve (en pantallas) y a partir de dónde se descarga
const AHEAD = 2, BEHIND = 1, DROP = 4
const MAX_WIDTH = 1000

export class FixedScroll extends HTMLElement {
    static observedAttributes = ['gap']
    #root = this.attachShadow({ mode: 'closed' })
    #column
    #pages = []      // { el, ratio, known, frame: { iframe, doc, width, height, onZoom } | null, loading }
    #tops = []
    #gap = 10
    #width = 0
    #index = -1
    #raf = 0
    #settle = 0
    #observer = new ResizeObserver(() => this.#relayout())
    constructor() {
        super()
        const sheet = new CSSStyleSheet()
        this.#root.adoptedStyleSheets = [sheet]
        sheet.replaceSync(`:host {
            display: block;
            width: 100%;
            height: 100%;
            overflow-x: hidden;
            overflow-y: auto;
            overscroll-behavior: contain;
            -webkit-overflow-scrolling: touch;
            scrollbar-width: none;
        }
        :host::-webkit-scrollbar { display: none; }
        .col { position: relative; }
        .pag { position: absolute; left: 0; overflow: hidden; }
        iframe { border: 0; display: block; transform-origin: top left; }`)
        this.#column = document.createElement('div')
        this.#column.className = 'col'
        this.#root.append(this.#column)
        this.addEventListener('scroll', () => {
            if (!this.#raf) this.#raf = requestAnimationFrame(() => { this.#raf = 0; this.#update('scroll') })
        }, { passive: true })
        this.#observer.observe(this)
    }
    attributeChangedCallback(name, _, value) {
        if (name === 'gap') {
            this.#gap = Math.max(0, parseFloat(value) || 0)
            this.#relayout()
        }
    }
    open(book) {
        this.book = book
        this.defaultViewport = book.rendition?.viewport
        this.rtl = book.dir === 'rtl'
        this.#pages = book.sections.map(() => {
            const el = document.createElement('div')
            el.className = 'pag'
            this.#column.append(el)
            return { el, ratio: 1.414, known: false, frame: null, loading: null }
        })
        // PDF: el tamaño real de cada página, sin dibujarla (pdf.js lo sabe sin renderizar)
        const pdf = book.pdf
        if (pdf) (async () => {
            for (let i = 0; i < this.#pages.length; i++) {
                if (!this.#pages.length) return  // (cerrado)
                try {
                    const vp = (await pdf.getPage(i + 1)).getViewport({ scale: 1 })
                    this.#setRatio(i, vp.height / vp.width, i % 20 === 19 || i === this.#pages.length - 1)
                } catch { /* página rota: se queda con el tamaño estimado */ }
            }
        })()
    }
    // Una página de la que ya se sabe el tamaño; las desconocidas toman el de la última conocida
    #setRatio(i, ratio, now = true) {
        const p = this.#pages[i]
        if (!p || !(ratio > 0)) return
        p.ratio = ratio
        p.known = true
        for (let j = i + 1; j < this.#pages.length && !this.#pages[j].known; j++) this.#pages[j].ratio = ratio
        if (now) this.#relayout()
    }
    get #viewH() { return this.clientHeight || 1 }
    #pageW() { return Math.min(this.clientWidth || 0, MAX_WIDTH) }
    // Recoloca todas las páginas sin que se mueva lo que estás leyendo
    #relayout() {
        if (!this.#pages.length) return
        const W = this.#pageW()
        if (!W) return
        const anchor = this.#anchor()
        let y = 0
        const left = Math.max(0, ((this.clientWidth || W) - W) / 2)
        this.#tops = this.#pages.map((p, i) => {
            const h = Math.round(W * p.ratio)
            Object.assign(p.el.style, { top: `${y}px`, left: `${left}px`, width: `${W}px`, height: `${h}px` })
            const top = y
            y += h + (i < this.#pages.length - 1 ? this.#gap : 0)
            return top
        })
        this.#column.style.height = `${y}px`
        const resized = W !== this.#width
        this.#width = W
        if (resized) for (const p of this.#pages) if (p.frame) this.#fit(p)
        if (anchor) this.scrollTop = this.#tops[anchor.i] + anchor.f * this.#height(anchor.i)
        this.#update()
    }
    #height(i) { return (this.#tops[i + 1] ?? parseFloat(this.#column.style.height)) - this.#tops[i] - (i < this.#pages.length - 1 ? this.#gap : 0) }
    #anchor() {
        if (!this.#tops.length || this.#index < 0) return null
        const i = this.#index
        return { i, f: Math.max(0, (this.scrollTop - this.#tops[i]) / (this.#height(i) || 1)) }
    }
    // La página que hay a un tercio de la pantalla (la que estás leyendo)
    #current() {
        const y = this.scrollTop + this.#viewH / 3
        let lo = 0, hi = this.#tops.length - 1
        while (lo < hi) {
            const mid = (lo + hi + 1) >> 1
            if (this.#tops[mid] <= y) lo = mid; else hi = mid - 1
        }
        return lo
    }
    #update(reason) {
        if (!this.#tops.length) return
        const H = this.#viewH, top = this.scrollTop, bottom = top + H
        // Cargar lo cercano (primero lo que se ve) y soltar lo lejano
        const want = []
        this.#pages.forEach((p, i) => {
            const a = this.#tops[i], b = a + this.#height(i)
            if (b >= top - BEHIND * H && a <= bottom + AHEAD * H) want.push(i)
            else if ((b < top - DROP * H || a > bottom + DROP * H) && p.frame) this.#unload(p)
        })
        const cur = this.#current()
        want.sort((x, y) => Math.abs(x - cur) - Math.abs(y - cur))
        for (const i of want) this.#load(i)
        if (cur !== this.#index) {
            this.#index = cur
            this.#report(reason || 'scroll')
        } else if (reason === 'scroll') {
            clearTimeout(this.#settle)
            this.#settle = setTimeout(() => this.#report('scroll'), 250)
        }
    }
    #report(reason) {
        const i = this.#index
        if (i < 0) return
        const h = this.#height(i) || 1
        const fraction = Math.min(1, Math.max(0, (this.scrollTop - this.#tops[i]) / h))
        this.dispatchEvent(new CustomEvent('relocate', { detail:
            { reason, range: null, index: i, fraction, size: Math.min(1, this.#viewH / h) } }))
    }
    #load(i) {
        const p = this.#pages[i]
        if (p.frame || p.loading) return
        const section = this.book.sections[i]
        p.loading = (async () => {
            const srcOption = await section.load()
            if (!p.loading) return
            const src = typeof srcOption === 'string' ? srcOption : srcOption?.src
            const onZoom = typeof srcOption === 'string' ? null : srcOption?.onZoom
            if (!src) return
            const iframe = document.createElement('iframe')
            iframe.setAttribute('sandbox', 'allow-same-origin allow-scripts')
            iframe.setAttribute('scrolling', 'no')
            iframe.setAttribute('part', 'filter')
            iframe.style.visibility = 'hidden'
            p.el.append(iframe)
            await new Promise(resolve => {
                iframe.addEventListener('load', resolve, { once: true })
                iframe.src = src
            })
            if (!p.loading || !iframe.isConnected) return
            const doc = iframe.contentDocument
            const { width, height } = getViewport(doc, this.defaultViewport)
            p.frame = { iframe, doc, width: parseFloat(width) || 1000, height: parseFloat(height) || 1414, onZoom }
            const ratio = p.frame.height / p.frame.width
            if (!p.known || Math.abs(ratio - p.ratio) > 0.002) this.#setRatio(i, ratio)
            this.#fit(p)
            iframe.style.visibility = ''
            this.dispatchEvent(new CustomEvent('load', { detail: { doc, index: i } }))
        })().catch(e => console.warn('Lector: página', i + 1, e)).finally(() => { p.loading = null })
    }
    #fit(p) {
        const f = p.frame
        if (!f) return
        const scale = this.#pageW() / f.width
        if (f.onZoom) {
            Object.assign(f.iframe.style, { width: `${f.width * scale}px`, height: `${f.height * scale}px`, transform: 'none' })
            // (si la página se suelta o el libro se cierra mientras se dibuja, pdf.js cancela el dibujo: no es un fallo)
            Promise.resolve(f.onZoom({ doc: f.doc, scale })).catch(e => { if (e?.name !== 'RenderingCancelledException') console.warn(e) })
        } else {
            Object.assign(f.iframe.style, { width: `${f.width}px`, height: `${f.height}px`, transform: `scale(${scale})` })
        }
    }
    #unload(p) {
        p.loading = null
        p.frame?.iframe.remove()
        p.frame = null
    }
    get index() { return this.#index }
    async goTo(target) {
        const resolved = await target
        const i = resolved?.index
        if (!(i >= 0 && i < this.#pages.length)) return
        if (!this.#tops.length) this.#relayout()
        this.scrollTop = this.#tops[i] ?? 0
        this.#index = -1
        this.#update('page')
    }
    async select(target) { await this.goTo(target) }
    // Pasar «página» con los botones de volumen o deslizando de lado: una pantalla (si la página es más alta
    // que la pantalla) o hasta el principio de la siguiente
    async next() {
        const H = this.#viewH, y = this.scrollTop, i = this.#current()
        const end = this.#tops[i] + this.#height(i)
        if (end - y > H * 1.05) this.scrollTop = y + H * 0.9
        else if (i < this.#pages.length - 1) this.scrollTop = this.#tops[i + 1]
        else this.scrollTop = end - H
    }
    async prev() {
        const H = this.#viewH, y = this.scrollTop, i = this.#current()
        if (y - this.#tops[i] > 4) this.scrollTop = Math.max(this.#tops[i], y - H * 0.9)
        else if (i > 0) this.scrollTop = Math.max(this.#tops[i - 1], this.#tops[i] - H * 0.9)
    }
    getContents() {
        return this.#pages.map((p, index) => (p.frame ? { doc: p.frame.doc, index } : null)).filter(Boolean)
    }
    destroy() {
        this.#observer.unobserve(this)
        for (const p of this.#pages) this.#unload(p)
        this.#pages = []
    }
}

customElements.define('foliate-fxl-scroll', FixedScroll)
