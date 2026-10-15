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
# Juegos (validador propio: géneros y etiquetas de RAWG)
paths = [os.path.join(HERE, f) for f in ('juegos-a.json', 'juegos-b.json')]
subprocess.run([sys.executable, os.path.join(HERE, 'validar_juegos.py')] + paths, check=True)
allq = []
for p in paths: allq += json.load(open(p, encoding='utf-8'))
json.dump(allq, open(os.path.join(OUT, 'juegos.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print('juegos', len(allq), 'preguntas')

# La revista de escritorio usa los mismos bancos, como scripts (la página no puede leer ficheros JSON sueltos)
DESK = os.path.join(HERE, '..', '..', 'escritorio', 'src', 'renderer', 'revista', 'datos')
os.makedirs(DESK, exist_ok=True)
for dom, name in ((0, 'juegos'), (1, 'libros'), (2, 'pantalla')):
    data = json.load(open(os.path.join(OUT, name + '.json'), encoding='utf-8'))
    with open(os.path.join(DESK, 'banco-' + name + '.js'), 'w', encoding='utf-8') as f:
        f.write('// Generado por herramientas/preguntas/unir.py: no se edita a mano.\n')
        f.write('window.RV_BANCOS = window.RV_BANCOS || {};\n')
        f.write('window.RV_BANCOS[%d] = ' % dom + json.dumps(data, ensure_ascii=False, separators=(',', ':')) + ';\n')
    print('escritorio', name, len(data))
