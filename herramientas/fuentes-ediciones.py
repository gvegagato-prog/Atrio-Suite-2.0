#!/usr/bin/env python3
"""Baja de Google Fonts (licencia OFL) las letras de las ediciones de la revista y las deja en res/font, recortadas a los
caracteres latinos (español, inglés, francés, alemán…), las flechas y los símbolos que usa la app.

Uso: python3 herramientas/fuentes-ediciones.py   (necesita fonttools: pip install fonttools)
"""
import io
import os
import re
import urllib.request

from fontTools import subset
from fontTools.ttLib import TTFont

AQUI = os.path.dirname(os.path.abspath(__file__))
DEST = os.path.join(AQUI, '..', 'movil', 'app', 'src', 'main', 'res', 'font')

# nombre del archivo → (familia, grueso, cursiva)
FUENTES = {
    'ed_unifraktur': ('UnifrakturMaguntia', 400, False),
    'ed_playfair_bold': ('Playfair Display', 700, False),
    'ed_playfair_italic': ('Playfair Display', 400, True),
    'ed_sourceserif_regular': ('Source Serif 4', 400, False),
    'ed_sourceserif_semibold': ('Source Serif 4', 600, False),
    'ed_sourceserif_bold': ('Source Serif 4', 700, False),
    'ed_sourceserif_italic': ('Source Serif 4', 400, True),
    'ed_bodoni_regular': ('Bodoni Moda', 400, False),
    'ed_bodoni_bold': ('Bodoni Moda', 700, False),
    'ed_bodoni_italic': ('Bodoni Moda', 400, True),
    'ed_jost_regular': ('Jost', 400, False),
    'ed_jost_medium': ('Jost', 500, False),
    'ed_jost_semibold': ('Jost', 600, False),
    'ed_jost_bold': ('Jost', 700, False),
    'ed_jost_italic': ('Jost', 400, True),
    'ed_caslondisplay': ('Libre Caslon Display', 400, False),
    'ed_caslon_regular': ('Libre Caslon Text', 400, False),
    'ed_caslon_bold': ('Libre Caslon Text', 700, False),
    'ed_caslon_italic': ('Libre Caslon Text', 400, True),
    'ed_spacegrotesk_medium': ('Space Grotesk', 500, False),
    'ed_spacegrotesk_bold': ('Space Grotesk', 700, False),
    'ed_plexsans_regular': ('IBM Plex Sans', 400, False),
    'ed_plexsans_medium': ('IBM Plex Sans', 500, False),
    'ed_plexsans_semibold': ('IBM Plex Sans', 600, False),
    'ed_plexsans_bold': ('IBM Plex Sans', 700, False),
    'ed_plexsans_italic': ('IBM Plex Sans', 400, True),
    'ed_plexmono_medium': ('IBM Plex Mono', 500, False),
    'ed_plexmono_bold': ('IBM Plex Mono', 700, False),
    'ed_anton': ('Anton', 400, False),
    'ed_barlow_regular': ('Barlow', 400, False),
    'ed_barlow_medium': ('Barlow', 500, False),
    'ed_barlow_semibold': ('Barlow', 600, False),
    'ed_barlow_bold': ('Barlow', 700, False),
    'ed_barlow_italic': ('Barlow', 400, True),
    'ed_abril': ('Abril Fatface', 400, False),
    'ed_karla_regular': ('Karla', 400, False),
    'ed_karla_medium': ('Karla', 500, False),
    'ed_karla_bold': ('Karla', 700, False),
    'ed_karla_italic': ('Karla', 400, True),
    'ed_archivoblack': ('Archivo Black', 400, False),
    'ed_courier_regular': ('Courier Prime', 400, False),
    'ed_courier_bold': ('Courier Prime', 700, False),
    'ed_courier_italic': ('Courier Prime', 400, True),
    'ed_cormorant_semibold': ('Cormorant Garamond', 600, False),
    'ed_cormorant_bold': ('Cormorant Garamond', 700, False),
    'ed_cormorant_italic': ('Cormorant Garamond', 600, True),
    'ed_lato_regular': ('Lato', 400, False),
    'ed_lato_bold': ('Lato', 700, False),
    'ed_lato_black': ('Lato', 900, False),
    'ed_lato_italic': ('Lato', 400, True),
}

# Latín (con sus acentos), puntuación, comillas, flechas, ✓, ★, cuadraditos y algún símbolo
UNICODES = list(range(0x20, 0x250)) + list(range(0x2000, 0x2070)) + [0x20AC, 0x2116, 0x2122, 0x2190, 0x2191, 0x2192, 0x2193,
    0x2194, 0x2197, 0x2198, 0x2212, 0x2215, 0x2219, 0x221E, 0x2260, 0x2264, 0x2265, 0x25A0, 0x25A1, 0x25AA, 0x25CF, 0x2605,
    0x2606, 0x2713, 0x2715, 0x2026, 0xFB01, 0xFB02]


def url_de(familia, grueso, cursiva):
    q = familia.replace(' ', '+') + (':ital,wght@1,%d' % grueso if cursiva else ':wght@%d' % grueso)
    css = urllib.request.urlopen('https://fonts.googleapis.com/css2?family=' + q, timeout=30).read().decode()
    m = re.search(r'src: url\((https://[^)]+\.ttf)\)', css)
    if not m:
        raise RuntimeError('sin TTF para %s %s %s' % (familia, grueso, cursiva))
    return m.group(1)


def main():
    os.makedirs(DEST, exist_ok=True)
    total = 0
    for nombre, (familia, grueso, cursiva) in FUENTES.items():
        datos = urllib.request.urlopen(url_de(familia, grueso, cursiva), timeout=60).read()
        f = TTFont(io.BytesIO(datos))
        opts = subset.Options()
        opts.layout_features = ['*']
        opts.name_IDs = ['*']
        opts.notdef_outline = True
        s = subset.Subsetter(opts)
        s.populate(unicodes=UNICODES)
        s.subset(f)
        ruta = os.path.join(DEST, nombre + '.ttf')
        f.save(ruta)
        tam = os.path.getsize(ruta)
        total += tam
        print('%-28s %-22s %d%s  %5d KB' % (nombre, familia, grueso, ' cursiva' if cursiva else '', tam // 1024))
    print('total', total // 1024, 'KB')


main()
