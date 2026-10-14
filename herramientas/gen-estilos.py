#!/usr/bin/env python3
"""Genera los esquemas de color y las letras de Atrio en el PC a partir de las paletas del móvil
(movil/.../Skin.kt), para que el PC y el móvil tengan los mismos estilos.

Salida:
  escritorio/src/renderer/styles/estilos.css   esquemas (body[data-scheme]) y juegos de letras (body[data-font])
  escritorio/src/renderer/js/estilos.js        listas para Ajustes → Apariencia (A.ESTILOS)

Uso (desde la raíz del repositorio): python3 herramientas/gen-estilos.py
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIN = os.path.join(ROOT, 'movil/app/src/main/java/com/gonzalo/gamerate/Skin.kt')
FORM = os.path.join(ROOT, 'movil/app/src/main/java/com/gonzalo/gamerate/Form.kt')
OUT_CSS = os.path.join(ROOT, 'escritorio/src/renderer/styles/estilos.css')
OUT_JS = os.path.join(ROOT, 'escritorio/src/renderer/js/estilos.js')

# Los esquemas que ya tenía el PC se quedan con su nombre (en el móvil hay otros con el mismo id)
SKIP = {'oled', 'papel'}
# Paletas de editores: también su versión clara como esquema aparte
LIGHT_TOO = {'dracula': 'Alucard', 'nord': 'Nord claro', 'catppuccin': 'Catppuccin Latte', 'gruvbox': 'Gruvbox claro',
             'solarized': 'Solarized claro', 'tokyonight': 'Tokyo Day', 'rosepine': 'Rosé Pine Dawn', 'everforest': 'Everforest claro',
             'atrio': 'Atrio claro', 'sakura': 'Sakura claro', 'hielo': 'Hielo claro'}

# Juegos de letras: id → (nombre, letra de títulos, letra del texto, ancho de títulos, grueso, espaciado)
FONT_FACES = [
    # familia, fichero, gruesos
    ('Fraunces', 'fraunces-latin-wght-normal.woff2', '100 900'),
    ('Playfair Display', 'playfair-display-latin-wght-normal.woff2', '400 900'),
    ('Cinzel', 'cinzel-latin-wght-normal.woff2', '400 900'),
    ('Orbitron', 'orbitron-latin-wght-normal.woff2', '400 900'),
    ('Rajdhani', 'rajdhani-latin-500-normal.woff2', '500'),
    ('Rajdhani', 'rajdhani-latin-700-normal.woff2', '700'),
    ('Press Start 2P', 'press-start-2p-latin-400-normal.woff2', '400'),
    ('VT323', 'vt323-latin-400-normal.woff2', '400'),
    ('Space Grotesk', 'space-grotesk-latin-wght-normal.woff2', '300 700'),
    ('Bebas Neue', 'bebas-neue-latin-400-normal.woff2', '400'),
    ('Exo 2', 'exo-2-latin-wght-normal.woff2', '100 900'),
    ('Bricolage Grotesque', 'bricolage-grotesque-latin-wght-normal.woff2', '200 800'),
    ('Unbounded', 'unbounded-latin-wght-normal.woff2', '200 900'),
    ('Syne', 'syne-latin-wght-normal.woff2', '400 800'),
    ('JetBrains Mono', 'jetbrains-mono-latin-wght-normal.woff2', '100 800'),
    ('Silkscreen', 'silkscreen-latin-400-normal.woff2', '400'),
    ('Righteous', 'righteous-latin-400-normal.woff2', '400'),
]
SETS = [
    ('editorial', 'Editorial', 'Fraunces', 'Instrument Sans', '100%', 560, '-0.02em'),
    ('elegante', 'Elegante', 'Playfair Display', 'Newsreader', '100%', 600, '-0.01em'),
    ('fantasia', 'Fantasía', 'Cinzel', 'Newsreader', '100%', 600, '0.02em'),
    ('cyberpunk', 'Cyberpunk', 'Orbitron', 'Rajdhani', '100%', 700, '0.03em'),
    ('arcade', 'Arcade', 'Press Start 2P', 'Space Grotesk', '100%', 400, '0'),
    ('pixel', 'Píxel', 'Silkscreen', 'JetBrains Mono', '100%', 400, '0.02em'),
    ('terminal', 'Terminal', 'VT323', 'JetBrains Mono', '100%', 400, '0.01em'),
    ('deportiva', 'Deportiva', 'Bebas Neue', 'Instrument Sans', '100%', 400, '0.03em'),
    ('scifi', 'Ciencia ficción', 'Exo 2', 'Exo 2', '100%', 700, '0'),
    ('moderna', 'Moderna', 'Bricolage Grotesque', 'Bricolage Grotesque', '100%', 700, '-0.02em'),
    ('geometrica', 'Geométrica', 'Unbounded', 'Space Grotesk', '100%', 600, '-0.01em'),
    ('vanguardia', 'Vanguardia', 'Syne', 'Space Grotesk', '100%', 700, '-0.01em'),
    ('retro', 'Retro', 'Righteous', 'Nunito', '100%', 400, '0'),
]
OLD_FONTS = [('grotesca', 'Grotesca'), ('serif', 'Serif'), ('mono', 'Monoespaciada'), ('redonda', 'Redondeada')]

# Combinaciones del PC: esquema + letras (como las del móvil)
COMBOS = [
    ('Atrio', 'noche', 'grotesca', 'clasico'), ('Revista', 'p-cafe', 'elegante', 'editorial'), ('Editorial nocturno', 'p-medianoche', 'editorial', 'editorial'),
    ('Arcade', 'p-gameboy', 'arcade', 'pixel'), ('Píxel neón', 'p-neon', 'pixel', 'pixel'), ('Cyberpunk', 'p-neon', 'cyberpunk', 'neon'),
    ('Ciencia ficción', 'p-oceano', 'scifi', 'neon'), ('Terminal', 'p-ambar', 'terminal', 'terminal'), ('Fantasía', 'p-vino', 'fantasia', 'editorial'),
    ('Nórdico', 'p-nord', 'moderna', 'minimal'), ('Dracula', 'p-dracula', 'geometrica', 'neon'), ('Catppuccin', 'p-catppuccin', 'retro', 'suave'),
    ('Gruvbox', 'p-gruvbox', 'editorial', 'terminal'), ('Tokyo Night', 'p-tokyonight', 'vanguardia', 'neon'), ('Rosé Pine', 'p-rosepine', 'elegante', 'suave'),
    ('Deportivo', 'p-pizarra', 'deportiva', 'brutal'), ('Brutalista', 'oled', 'geometrica', 'brutal'), ('Minimal', 'p-hielo-claro', 'grotesca', 'minimal'),
    ('Cómic', 'p-atardecer', 'retro', 'comic'), ('Militar', 'p-bosque', 'deportiva', 'militar'),
]


def hexs(v):
    return '#%06x' % (v & 0xFFFFFF)


def rgb(v):
    v &= 0xFFFFFF
    return (v >> 16) & 255, (v >> 8) & 255, v & 255


def mix(a, b, t):
    ra, rb = rgb(a), rgb(b)
    return '#%02x%02x%02x' % tuple(round(x + (y - x) * t) for x, y in zip(ra, rb))


def lum(v):
    r, g, b = rgb(v)
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255


def parse():
    src = open(SKIN, encoding='utf-8').read()
    out = []
    for m in re.finditer(r'Palette\("(\w+)", "([^"]+)", "([^"]+)",\s*t\(([^)]*)\),\s*t\(([^)]*)\)\)', src):
        def tones(s):
            parts = [p.strip() for p in s.split(',')]
            pos = [int(p, 16) for p in parts if not '=' in p]
            named = {p.split('=')[0].strip(): int(p.split('=')[1].strip(), 16) for p in parts if '=' in p}
            keys = ['paper', 'crudo', 'ink', 'general', 'gfx', 'story', 'music', 'gameplay', 'gold', 'sand', 'line', 'edge', 'muted']
            d = dict(zip(keys, pos))
            d.update(named)
            return d
        out.append((m.group(1), m.group(2), m.group(3), tones(m.group(4)), tones(m.group(5))))
    return out


def parse_forms():
    """Las formas del móvil (Form.kt): id, nombre y descripción."""
    src = open(FORM, encoding='utf-8').read()
    return [{'id': m.group(1), 'name': m.group(2), 'desc': m.group(3)} for m in re.finditer(r'    Form\("(\w+)", "([^"]+)", "([^"]+)"', src)]


def scheme_css(sid, t):
    bg, fg = t['paper'], t['ink']
    light = lum(bg) > 0.5
    fr, fgg, fb = rgb(fg)
    br, bgg, bb = rgb(bg)
    cr, cg, cb = rgb(t['crudo'])
    acc = t['gfx'] if not light else t['ink']
    return light, (
        f'body[data-scheme="{sid}"] {{\n'
        f'  --fg-rgb: {fr}, {fgg}, {fb};\n'
        f'  --bg: {hexs(bg)}; --fg: {hexs(fg)}; --fg-2: {mix(bg, fg, .72)}; --fg-3: {mix(bg, fg, .48)};\n'
        f'  --line: rgba({fr},{fgg},{fb},.14); --surface: rgba({cr},{cg},{cb},{.92 if light else .86}); --surface-2: rgba({fr},{fgg},{fb},{.05 if light else .07});\n'
        f'  --scrim: {br}, {bgg}, {bb}; --accent-base: {hexs(acc)}; --on-accent: {hexs(bg)}; --art-dim: {.78 if light else .9};\n'
        f'}}\n')


def main():
    pal = parse()
    css = ['/* Generado por herramientas/gen-estilos.py a partir de las paletas del móvil (Skin.kt): no editar a mano. */\n']
    for fam, file, w in FONT_FACES:
        css.append(f"@font-face {{ font-family: '{fam}'; font-weight: {w}; font-display: block; src: url('../fonts/{file}') format('woff2'); }}\n")
    css.append('\n/* Juegos de letras */\n')
    fallback = {'Newsreader': 'Georgia, serif', 'JetBrains Mono': 'monospace', 'Nunito': 'system-ui, sans-serif'}
    for sid, _, disp, body, stretch, weight, track in SETS:
        css.append(f"body[data-font=\"{sid}\"] {{ --font: '{body}', {fallback.get(body, 'system-ui, sans-serif')}; --font-display: '{disp}', system-ui, sans-serif;\n"
                   f"  --display-stretch: {stretch}; --display-weight: {weight}; --display-tracking: {track}; }}\n")
    # Letras de píxel muy anchas: en las portadas sin imagen, el nombre va con la letra del texto
    css.append('body[data-font="arcade"] .tile .ph, body[data-font="pixel"] .tile .ph, body[data-font="terminal"] .tile .ph { font-family: var(--font); }\n')
    css.append('\n/* Esquemas de color (los mismos que en el móvil) */\n')
    schemes = []
    for pid, name, group, light_t, dark_t in pal:
        if pid in SKIP:
            continue
        sid = f'p-{pid}'
        is_light, block = scheme_css(sid, dark_t)
        css.append(block)
        schemes.append({'id': sid, 'name': name, 'group': 'Editores' if group == 'Editores' else ('Clásicos' if group == 'Clásicos' else 'Atrio'),
                        'bg': hexs(dark_t['paper']), 'fg': hexs(dark_t['ink']), 'accent': hexs(dark_t['gfx']), 'light': is_light})
        if pid in LIGHT_TOO:
            sid2 = f'p-{pid}-claro'
            is_light2, block2 = scheme_css(sid2, light_t)
            css.append(block2)
            schemes.append({'id': sid2, 'name': LIGHT_TOO[pid], 'group': 'Claros', 'bg': hexs(light_t['paper']), 'fg': hexs(light_t['ink']),
                            'accent': hexs(light_t['gfx']), 'light': is_light2})
    open(OUT_CSS, 'w', encoding='utf-8').write(''.join(css))
    js = {
        'schemes': schemes,
        'fonts': [{'id': i, 'name': n} for i, n in OLD_FONTS] + [{'id': s[0], 'name': s[1], 'display': s[2], 'body': s[3]} for s in SETS],
        'combos': [{'name': n, 'scheme': s, 'font': f, 'form': fo} for n, s, f, fo in COMBOS],
        'forms': parse_forms(),
    }
    open(OUT_JS, 'w', encoding='utf-8').write(
        "'use strict';\n/* global A */\n// Generado por herramientas/gen-estilos.py (paletas del móvil): no editar a mano.\n"
        f"A.ESTILOS = {json.dumps(js, ensure_ascii=False, indent=1)};\n")
    print(f'{len(schemes)} esquemas, {len(SETS)} juegos de letras nuevos, {len(COMBOS)} combinaciones, {len(js["forms"])} formas')


if __name__ == '__main__':
    main()
