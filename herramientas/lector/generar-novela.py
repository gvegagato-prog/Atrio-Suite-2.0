#!/usr/bin/env python3
"""Genera novelas de prueba en PDF (texto inventado) para probar el modo «texto adaptable» del lector.

Uso: generar-novela.py salida.pdf [--estilo sangria|bloque|derecha] [--columnas 2] [--paginas-capitulo N]
Escribe también salida.json con los párrafos «de verdad» (para comprobar lo que extrae el lector).
Maqueta como una editorial: justificado con guiones al final de línea, cabecera corrida, número de página,
portada, página de créditos, capítulos que empiezan más abajo, diálogos con raya, cursivas y separadores de escena.
"""
import argparse, json, random, re
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

F = '/usr/share/fonts/truetype/liberation/LiberationSerif-'
pdfmetrics.registerFont(TTFont('R', F + 'Regular.ttf'))
pdfmetrics.registerFont(TTFont('I', F + 'Italic.ttf'))
pdfmetrics.registerFont(TTFont('B', F + 'Bold.ttf'))

NARRA = [
    "La lluvia llevaba tres días cayendo sobre el puerto y nadie recordaba ya el color exacto del cielo.",
    "Marta cruzó la plaza con el paraguas cerrado, como si el agua fuese un asunto que no le concerniera.",
    "En el piso de arriba alguien arrastraba una silla, despacio, con la paciencia de quien no espera visitas.",
    "Había aprendido, a fuerza de equivocarse, que las casas viejas guardan sus secretos en los rincones menos pensados.",
    "El farero, un hombre enjuto de manos enormes, la observó desde la ventana sin decir una palabra.",
    "Al otro lado de la bahía, las luces de los pesqueros dibujaban una línea temblorosa e irregular.",
    "Recordó la carta que su padre nunca llegó a enviar, y que ella encontró doblada en cuatro dentro de un libro de mareas.",
    "No era miedo exactamente; era más bien la sospecha de que algo importante estaba a punto de ocurrir sin ella.",
    "La escalera crujió bajo sus pies con un quejido largo, casi humano, que se apagó en la oscuridad del rellano.",
    "Encendió una vela porque la electricidad llevaba horas ausente y porque le gustaba, en el fondo, el silencio de la llama.",
    "Durante años había evitado aquel lugar, convencida de que las cosas perdidas es mejor dejarlas donde se pierden.",
    "El mar, mientras tanto, seguía hablando solo, con esa terquedad de los viejos que repiten siempre la misma historia.",
    "Sobre la mesa descansaban una taza desportillada, un mapa a medio desplegar y un reloj que marcaba una hora imposible.",
    "Pensó en llamar a su hermano, pero el teléfono, como todo lo demás en aquella casa, parecía haberse rendido de antemano.",
    "Cuando por fin abrió el cajón, comprendió que la verdad no se parecía en nada a lo que había imaginado durante tantas noches.",
    "Un perro ladró a lo lejos, dos veces, y después la calle volvió a quedarse quieta, expectante, igual que un teatro antes de alzarse el telón.",
    "Se sentó en el suelo, con la espalda contra la pared, y dejó que los recuerdos fueran llegando en el orden que les daba la gana.",
    "Hay mañanas en que el mundo parece un favor que alguien nos hace sin pedir nada a cambio, y aquella fue una de ellas.",
]
DIALOGO = [
    ["—¿Has dormido algo? —preguntó él desde la puerta.", "—Lo suficiente —mintió ella—. ¿Y tú?", "—Yo no duermo desde que llegaste, Marta. No me lo tomes a mal."],
    ["—Dime solo una cosa —dijo el farero, sin levantar la vista—. ¿Quién te dio la llave?", "—Nadie. La encontré donde siempre estuvo.", "—Eso es lo que me temía."],
    ["—No pienso quedarme aquí a esperar que escampe.", "—Pues vas a mojarte.", "—Ya estoy mojada, Tomás. Lo estoy desde hace años."],
    ["—*Todo lo que se pierde acaba volviendo* —recitó él—. Eso decía mi abuela.", "—Tu abuela mentía muchísimo.", "—Sí, pero lo hacía con tanta gracia que costaba reprochárselo."],
    ["—¿Y si no hay nada detrás de esa puerta? —susurró ella.", "—Entonces habremos perdido una tarde —respondió Tomás, encogiéndose de hombros—. No sería la primera."],
]
ITAL = ["*Esperanza*", "*La Gaviota*", "*sin remedio*", "*nunca*"]
TITULOS = ["La casa del faro", "Mareas", "Lo que dijo el mar", "Una llave sin puerta", "El reloj parado", "Cartas sin enviar",
           "La tormenta", "Después del silencio", "Nombres en la arena", "El regreso", "Cenizas", "La última marea"]


def silabas(w):
    """Sílabas muy aproximadas (para dónde partir una palabra al final de línea)."""
    v = 'aeiouáéíóúüAEIOUÁÉÍÓÚ'
    cortes = []
    for i in range(2, len(w) - 2):
        a, b, c = w[i - 1], w[i], w[i + 1]
        if a in v and b not in v and c in v: cortes.append(i)
        elif a in v and b not in v and c not in v and w[i:i + 2] not in ('ch', 'll', 'rr', 'pr', 'tr', 'br', 'cr', 'gr', 'fr', 'pl', 'bl', 'cl', 'gl', 'fl', 'dr'): cortes.append(i + 1) if i + 2 < len(w) - 1 else None
    return sorted(set(c for c in cortes if 2 <= c <= len(w) - 2))


def ancho(txt, fuente, pt):
    if fuente == 'S': return pdfmetrics.stringWidth(txt, 'R', pt * 0.65)
    return pdfmetrics.stringWidth(txt, fuente, pt)


def tokens(par):
    """Palabras: cada una, una lista de (trozo, estilo) a partir de «texto con *cursiva*»."""
    out = [[]]; est = 'R'; sup = False
    for ch in par:
        if ch == '^': sup = True; continue
        if sup:
            if ch.isdigit():
                if out[-1] and out[-1][-1][1] == 'S': out[-1][-1] = (out[-1][-1][0] + ch, 'S')
                else: out[-1].append((ch, 'S'))
                continue
            sup = False
        if ch == '*': est = 'I' if est == 'R' else 'R'; continue
        if ch == ' ':
            if out[-1]: out.append([])
            continue
        if out[-1] and out[-1][-1][1] == est: out[-1][-1] = (out[-1][-1][0] + ch, est)
        else: out[-1].append((ch, est))
    return [w for w in out if w]


def ancho_p(w, pt):
    return sum(ancho(t, f, pt) for t, f in w)


def texto_p(w): return ''.join(t for t, _ in w)


def parte(w, k):
    """Parte una palabra (lista de segmentos) en el carácter k."""
    a, b, n = [], [], 0
    for t, f in w:
        if n + len(t) <= k: a.append((t, f))
        elif n >= k: b.append((t, f))
        else: a.append((t[:k - n], f)); b.append((t[k - n:], f))
        n += len(t)
    return a, b


class Maqueta:
    def __init__(self, ruta, a):
        self.a = a
        self.c = canvas.Canvas(ruta, pagesize=(396, 612))
        self.c.setTitle('La casa del faro'); self.c.setAuthor('A. Prueba')
        self.W, self.H = 396, 612
        self.mi, self.md, self.ms, self.mb = 52, 52, 62, 62
        self.pt, self.lead = 11, 14.6
        self.pag = 0
        self.y = None
        self.verdad = []        # párrafos de verdad
        self.capitulos = []
        self.col = 0
        self.cols = a.columnas
        self.pend = []; self.res = 0; self.notas_verdad = []
        self.gap = 22

    @property
    def ancho_col(self):
        return (self.W - self.mi - self.md - (self.cols - 1) * self.gap) / self.cols

    def x0(self): return self.mi + self.col * (self.ancho_col + self.gap)

    def nueva(self, cabecera=True):
        if self.pag: self.pie(); self.pie_y_cabecera(); self.c.showPage()
        self.pag += 1
        self.y = self.H - self.ms
        self.col_top = self.y
        self.col = 0
        self.cabecera = cabecera

    def pie_y_cabecera(self):
        c = self.c
        if not getattr(self, 'cabecera', True): return
        c.setFont('I', 8.5)
        txt = 'A. Prueba' if self.pag % 2 == 0 else (self.a.cabecera == 'capitulo' and getattr(self, 'titulo_actual', None) or 'La casa del faro')
        c.drawCentredString(self.W / 2, self.H - 34, txt)
        c.setFont('R', 9.5)
        c.drawCentredString(self.W / 2, 30, str(self.pag - 2))

    def salto_col(self):
        if self.col + 1 < self.cols:
            self.col += 1; self.y = self.col_top
        else:
            self.nueva()

    def linea_palabras(self, runs, x, y, ancho_l, justificar):
        """Cada palabra como una pieza de texto aparte y sin espacios en el texto: el hueco es solo posición."""
        c = self.c
        natural = sum(ancho(r[0], r[1], self.pt) for r in runs)
        espacios = sum(r[0].count(' ') for r in runs)
        extra = (ancho_l - natural) / espacios if (justificar and espacios) else 0
        for txt, f in runs:
            partes = txt.split(' ')
            for k, parte in enumerate(partes):
                if k > 0: x += ancho(' ', 'R', self.pt) + extra
                if parte:
                    if f == 'S': c.setFont('R', self.pt * 0.65); c.drawString(x, y + self.pt * 0.4, parte)
                    else: c.setFont(f, self.pt); c.drawString(x, y, parte)
                    x += ancho(parte, f, self.pt)

    def linea(self, runs, x, y, ancho_l, justificar, centrar=False):
        if self.a.palabras: return self.linea_palabras(runs, x, y, ancho_l, justificar)
        c = self.c
        t = c.beginText(); t.setTextOrigin(x, y)
        espacios = sum(r[0].count(' ') for r in runs)
        natural = sum(ancho(r[0], r[1], self.pt) for r in runs)
        extra = (ancho_l - natural) / espacios if (justificar and espacios) else 0
        if centrar: t.setTextOrigin(x + (ancho_l - natural) / 2, y)
        t.setCharSpace(0); t.setWordSpace(extra)
        for txt, f in runs:
            if f == 'S': t.setFont('R', self.pt * 0.65); t.setRise(self.pt * 0.4); t.textOut(txt); t.setRise(0)
            else: t.setFont(f, self.pt); t.textOut(txt)
        c.drawText(t)

    def pie(self):
        """Dibuja las notas al pie pendientes de la página (letra pequeña, con filete)."""
        if not self.pend: return
        c = self.c
        y = self.mb + 4 + 10 * len(self.pend)
        c.setLineWidth(0.4); c.line(self.mi, y + 8, self.mi + 70, y + 8)
        c.setFont('R', 8)
        for n, t in self.pend:
            c.drawString(self.mi, y, f'{n} {t}'); y -= 10
        self.pend = []; self.res = 0

    def parrafo(self, par, sangria=True, primero=False, capital=False):
        a = self.a
        m = re.search(r'\^(\d+)\{(.*?)\}', par)
        nota = None
        if m:
            nota = (m.group(1), m.group(2))
            par = par.replace(m.group(0), '^' + m.group(1))
            self.res += 10
        self.verdad.append(re.sub(r'\*', '', par).replace('^', ''))
        if nota: self.notas_verdad.append(nota[0] + ' ' + nota[1])
        toks = tokens(par)
        cap = None
        if capital and toks:
            seg = toks[0][0]; ch = seg[0][0]; rest = seg[0][1:]
            if rest: toks[0][0] = (rest, seg[1])
            else:
                toks[0].pop(0)
                if not toks[0]: toks.pop(0)
            cap = ch
        ind = 0
        if a.estilo == 'sangria' and sangria and not primero: ind = 1.3 * self.pt
        capw = ancho(cap, 'B', self.pt * 3.1) + 3 if cap else 0
        if a.estilo in ('derecha',) and sangria and not primero: ind = 1.3 * self.pt
        justificar = a.estilo not in ('derecha', 'plano')
        i = 0; primera = True
        lineas = []
        while i < len(toks):
            ncap = len(lineas)
            sg_ = capw if (cap and ncap < 3) else (ind if primera else 0)
            disp = self.ancho_col - sg_
            runs = []; w_acum = 0
            while i < len(toks):
                w = toks[i]
                sp = ancho(' ', 'R', self.pt) if runs else 0
                wi = ancho_p(w, self.pt)
                if w_acum + sp + wi <= disp:
                    pre = ' ' if runs else ''
                    for j, (t, f) in enumerate(w): runs.append(((pre if j == 0 else '') + t, f))
                    w_acum += sp + wi; i += 1
                else:
                    txt = texto_p(w)
                    guion = [m_.end() for m_ in re.finditer('-', txt) if 1 < m_.end() < len(txt)]
                    for k in reversed(guion):
                        a_, b_ = parte(w, k)
                        if w_acum + sp + ancho_p(a_, self.pt) <= disp:
                            pre = ' ' if runs else ''
                            for j, (t, f) in enumerate(a_): runs.append(((pre if j == 0 else '') + t, f))
                            toks[i] = b_
                            w_acum += sp + ancho_p(a_, self.pt)
                            break
                    else:
                        guion = []
                    if guion and toks[i] is not w:
                        break
                    for k in reversed(silabas(txt)):
                        a_, b_ = parte(w, k)
                        if w_acum + sp + ancho_p(a_, self.pt) + ancho('-', a_[-1][1], self.pt) <= disp and k >= 2 and len(txt) - k >= 2 and re.match(r'^[a-záéíóúüñA-Z]+$', txt):
                            pre = ' ' if runs else ''
                            for j, (t, f) in enumerate(a_): runs.append(((pre if j == 0 else '') + t + ('-' if j == len(a_) - 1 else ''), f))
                            toks[i] = b_
                            w_acum += sp + ancho_p(a_, self.pt) + ancho('-', a_[-1][1], self.pt); break
                    break
            ultima = i >= len(toks)
            lineas.append((runs, ultima, sg_))
            primera = False
        # viudas y huérfanas: nada fino; si no cabe, a la siguiente columna/página
        if cap and self.y - 4 * self.lead < self.mb + 6 + self.res: self.salto_col()
        for k, (runs, ultima, sg) in enumerate(lineas):
            if self.y - self.lead < self.mb + 6 + self.res: self.salto_col()
            if cap and k == 0: y_cap = self.y - 2 * self.lead; self.c.setFont('B', self.pt * 3.1); self.c.drawString(self.x0(), y_cap, cap)
            self.linea(runs, self.x0() + sg, self.y, self.ancho_col - sg, justificar and not ultima)
            self.y -= self.lead
        if nota: self.pend.append(nota)
        if a.estilo == 'bloque': self.y -= 0.55 * self.lead

    def escena(self):
        self.verdad.append('* * *')
        if self.a.escena == 'hueco':
            self.y -= self.lead
            return
        self.y -= self.lead * 0.5
        if self.y - self.lead * 2 < self.mb: self.salto_col()
        self.c.setFont('R', self.pt)
        self.c.drawCentredString(self.x0() + self.ancho_col / 2, self.y, '*   *   *')
        self.y -= self.lead * 1.5

    def capitulo(self, n, titulo):
        self.nueva()
        self.capitulos.append((n, titulo, self.pag - 2))
        self.y = self.H - self.ms - 90
        c = self.c
        modo = self.a.titulo
        if modo == 'normal':
            t = c.beginText(); t.setTextOrigin(self.W / 2 - 45, self.y); t.setCharSpace(2.4); t.setFont('R', 10); t.textOut(f'CAPÍTULO {n}'); t.setCharSpace(0); c.drawText(t)
            self.y -= 30
            c.setFont('B', 19); c.drawCentredString(self.W / 2, self.y, titulo)
            self.y -= 40
        elif modo == 'espaciado':
            t = c.beginText(); t.setTextOrigin(self.W / 2 - 45, self.y); t.setCharSpace(2.4); t.setFont('R', 10); t.textOut(f'CAPÍTULO {n}')
            self.y -= 30
            t.setTextOrigin(self.W / 2 - 100, self.y); t.setFont('B', 19); t.textOut(titulo); t.setCharSpace(0); c.drawText(t)
            self.y -= 40
        elif modo == 'discreto':
            c.setFont('B', 11); c.drawCentredString(self.W / 2, self.y, f'Capítulo {n}')
            self.y -= 36
        elif modo == 'numero':
            c.setFont('R', 26); c.drawCentredString(self.W / 2, self.y, str(n))
            self.y -= 44
        else:
            rom = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI', 'XII'][n - 1]
            c.setFont('R', 14); c.drawCentredString(self.W / 2, self.y, rom)
            self.y -= 40
        self.titulo_actual = titulo
        if self.a.indice:
            self.c.bookmarkPage(f'cap{n}'); self.c.addOutlineEntry(f'Capítulo {n}: {titulo}', f'cap{n}', level=0)
        self.col_top = self.y
        self.verdad.append(f'## CAPÍTULO {n} · {titulo}')

    def portada(self):
        self.nueva(cabecera=False)
        self.pag = 1
        c = self.c
        c.setFont('B', 30); c.drawCentredString(self.W / 2, 400, 'LA CASA DEL FARO')
        c.setFont('R', 15); c.drawCentredString(self.W / 2, 340, 'A. Prueba')
        self.nueva(cabecera=False)
        self.pag = 2
        c.setFont('R', 8)
        for k, l in enumerate(['Primera edición: 2026', 'Todos los derechos reservados.', 'Obra de ficción: personajes y lugares son inventados.', 'ISBN 000-00-0000-000-0']):
            c.drawString(self.mi, 200 - k * 11, l)
        self.nueva()
        self.pag = 3

    def cerrar(self):
        self.pie(); self.pie_y_cabecera(); self.c.save()


COMPUESTAS = ['Era un asunto teórico-práctico más complicado de lo previsto', 'El acuerdo franco-prusiano había quedado atrás', 'Fue un tratado hispano-americano y nada más', 'Hablaban en un tono tragicómico-melancólico muy raro']


def contenido(rnd, n_par, notas=False, compuestas=False):
    """Párrafos de un capítulo: [('p', texto) | ('escena',)]"""
    out = []
    for k in range(n_par):
        r = rnd.random()
        if r < 0.22:
            for l in rnd.choice(DIALOGO): out.append(('d', l))
        elif r < 0.27 and k > 2:
            out.append(('escena',))
        else:
            n = rnd.randint(2, 7)
            s = [rnd.choice(NARRA) for _ in range(n)]
            if rnd.random() < 0.4: s[rnd.randrange(n)] += ' ' + rnd.choice(ITAL).replace('*', '*') + ' lo sabía.'
            if compuestas and rnd.random() < 0.5: s[rnd.randrange(n)] += ' ' + rnd.choice(COMPUESTAS) + '.'
            if notas and rnd.random() < 0.18: s[rnd.randrange(n)] += f'^{rnd.randint(1, 9)}{{Nota del traductor: expresión local.}}'
            out.append(('p', ' '.join(s)))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('salida')
    ap.add_argument('--estilo', default='sangria', choices=['sangria', 'bloque', 'derecha', 'plano'])
    ap.add_argument('--columnas', type=int, default=1)
    ap.add_argument('--capitular', action='store_true')
    ap.add_argument('--titulo', default='normal', choices=['normal', 'discreto', 'numero', 'romano', 'espaciado'])
    ap.add_argument('--cabecera', default='libro', choices=['libro', 'capitulo'])
    ap.add_argument('--compuestas', action='store_true')
    ap.add_argument('--indice', action='store_true', help='con índice (marcadores) del PDF')
    ap.add_argument('--palabras', action='store_true', help='cada palabra suelta, sin espacios en el texto (como algunos PDF de editorial)')
    ap.add_argument('--escena', default='asteriscos', choices=['asteriscos', 'hueco'])
    ap.add_argument('--notas', action='store_true')
    ap.add_argument('--capitulos', type=int, default=6)
    ap.add_argument('--parrafos', type=int, default=26)
    a = ap.parse_args()
    rnd = random.Random(7)
    m = Maqueta(a.salida, a)
    m.portada()
    for n in range(1, a.capitulos + 1):
        m.capitulo(n, TITULOS[(n - 1) % len(TITULOS)])
        primero = True
        for item in contenido(rnd, a.parrafos, a.notas, a.compuestas):
            if item[0] == 'escena': m.escena(); primero = True
            else:
                m.parrafo(item[1], primero=primero and item[0] == 'p', capital=a.capitular and primero and item[0] == 'p'); primero = False
    m.cerrar()
    json.dump({'parrafos': m.verdad, 'capitulos': m.capitulos, 'paginas': m.pag, 'notas': m.notas_verdad}, open(re.sub(r'\.pdf$', '', a.salida) + '.json', 'w'), ensure_ascii=False, indent=1)
    print(a.salida, m.pag, 'páginas,', len(m.verdad), 'párrafos')


main()
