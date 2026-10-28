#!/usr/bin/env python3
"""El icono de Atrio para macOS (escritorio/build/icon-mac.png, 1024 px): el mismo dibujo que build/icon.png (la puerta
en arco sobre azul noche), redibujado a 1024 con la retícula de los iconos de macOS (cuerpo de 824 px centrado, esquinas
redondeadas y una sombra suave), para que en el Dock no se vea más grande que los demás. Uso: python3 icono-mac.py"""
import os
from PIL import Image, ImageDraw, ImageFilter

K = 4                       # se dibuja a 4x y se reduce (bordes suaves)
N = 1024 * K
CUERPO = 824 * K
M = (N - CUERPO) // 2
S = CUERPO / 456            # del dibujo original (cuerpo de 456 px en un lienzo de 512)


def p(x, y):
    """Un punto del icono original (512) en el nuevo."""
    return (M + (x - 28) * S, M + (y - 28) * S)


def degradado(w, h, a, b, vertical=False):
    g = Image.new('RGBA', (w, h))
    px = g.load()
    for j in range(h):
        for i in range(w):
            t = (j / (h - 1)) if vertical else ((i + j) / (w + h - 2))
            px[i, j] = tuple(int(a[k] + (b[k] - a[k]) * t) for k in range(3)) + (255,)
    return g


def main():
    raiz = os.path.join(os.path.dirname(__file__), '..', 'escritorio', 'build')
    lienzo = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    radio = int(CUERPO * 0.225)
    # Sombra
    sombra = Image.new('L', (N, N), 0)
    ImageDraw.Draw(sombra).rounded_rectangle((M, M + 10 * K, M + CUERPO, M + CUERPO + 10 * K), radio, fill=95)
    sombra = sombra.filter(ImageFilter.GaussianBlur(14 * K))
    lienzo.paste((0, 0, 0, 255), (0, 0), sombra)
    # Cuerpo: azul noche en diagonal
    mascara = Image.new('L', (N, N), 0)
    ImageDraw.Draw(mascara).rounded_rectangle((M, M, M + CUERPO, M + CUERPO), radio, fill=255)
    cuerpo = degradado(256, 256, (29, 39, 56), (10, 14, 21)).resize((CUERPO, CUERPO), Image.BICUBIC)
    capa = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    capa.paste(cuerpo, (M, M))
    lienzo.paste(capa, (0, 0), mascara)
    d = ImageDraw.Draw(lienzo)
    # La puerta: arco blanco (radio 78 en el original), dentro de noche a ámbar, y el alféizar azul
    cx, _ = p(255.5, 0)
    top = p(0, 131)[1]
    r_ext = 78 * S
    r_int = 50 * S
    base = p(0, 361)[1]
    blanco = (238, 241, 245, 255)
    d.ellipse((cx - r_ext, top, cx + r_ext, top + 2 * r_ext), fill=blanco)
    d.rectangle((cx - r_ext, top + r_ext, cx + r_ext, base), fill=blanco)
    interior = degradado(8, 256, (22, 29, 42), (150, 110, 66), vertical=True).resize((int(2 * r_int) + 2, int(base - (top + r_ext - r_int)) + 2), Image.BICUBIC)
    mi = Image.new('L', (N, N), 0)
    dm = ImageDraw.Draw(mi)
    it = top + (r_ext - r_int)
    dm.ellipse((cx - r_int, it, cx + r_int, it + 2 * r_int), fill=255)
    dm.rectangle((cx - r_int, it + r_int, cx + r_int, base), fill=255)
    capa = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    capa.paste(interior, (int(cx - r_int), int(it)))
    lienzo.paste(capa, (0, 0), mi)
    d.rectangle((cx - r_ext, base, cx + r_ext, p(0, 375)[1]), fill=(143, 179, 217, 255))
    lienzo = lienzo.resize((1024, 1024), Image.LANCZOS)
    salida = os.path.join(raiz, 'icon-mac.png')
    lienzo.save(salida, optimize=True)
    print(salida)


if __name__ == '__main__':
    main()
