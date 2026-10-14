#!/usr/bin/env python3
"""Une los bancos de preguntas en los ficheros que lleva la app (movil/app/src/main/assets/descubrir/)."""
import json, os, subprocess, sys
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'movil', 'app', 'src', 'main', 'assets', 'descubrir')
os.makedirs(OUT, exist_ok=True)
for dom, files in (('pantalla', ['pantalla-a.json', 'pantalla-b.json']), ('libros', ['libros-a.json', 'libros-b.json'])):
    paths = [os.path.join(HERE, f) for f in files]
    subprocess.run([sys.executable, os.path.join(HERE, 'validar.py'), dom] + paths, check=True)
    allq = []
    for p in paths: allq += json.load(open(p, encoding='utf-8'))
    json.dump(allq, open(os.path.join(OUT, dom + '.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
    print(dom, len(allq), 'preguntas')
