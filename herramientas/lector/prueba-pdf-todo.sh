#!/bin/bash
# Genera novelas de prueba en PDF (varios estilos de editorial) y pasa las pruebas del modo «texto adaptable».
# Uso: PW=/ruta/a/playwright bash herramientas/lector/prueba-pdf-todo.sh [carpeta-de-trabajo]
set -u
AQUI=$(cd "$(dirname "$0")" && pwd)
OUT=${1:-/tmp/atrio-pdf-prueba}
mkdir -p "$OUT/nov" "$OUT/capturas"
G="python3 $AQUI/generar-novela.py"
$G $OUT/nov/sangria.pdf --estilo sangria >/dev/null
$G $OUT/nov/bloque.pdf --estilo bloque >/dev/null
$G $OUT/nov/derecha.pdf --estilo derecha >/dev/null
$G $OUT/nov/plano.pdf --estilo plano >/dev/null
$G $OUT/nov/cap.pdf --capitular >/dev/null
$G $OUT/nov/notas.pdf --notas >/dev/null
$G $OUT/nov/palabras.pdf --palabras --notas >/dev/null
$G $OUT/nov/mix.pdf --cabecera capitulo --compuestas --escena hueco --titulo espaciado >/dev/null
$G $OUT/nov/indice.pdf --indice >/dev/null
for t in discreto numero romano; do $G $OUT/nov/t-$t.pdf --titulo $t >/dev/null; done
python3 - "$OUT" <<'PY'
import sys
from reportlab.pdfgen import canvas
out = sys.argv[1]
c = canvas.Canvas(out + '/nov/escaneado.pdf', pagesize=(396, 612))
for p in range(3):
    c.setFillColorRGB(0.9, 0.9, 0.85); c.rect(0, 0, 396, 612, fill=1); c.setFillColorRGB(0.2, 0.2, 0.2)
    for k in range(30): c.rect(50, 560 - k * 16, 280 - (k % 5) * 20, 6, fill=1)
    c.showPage()
c.save()
c = canvas.Canvas(out + '/nov/ilustrado.pdf', pagesize=(396, 612))
for p in range(8):
    c.setFillColorRGB(0.8, 0.85, 0.9); c.rect(30, 200, 336, 360, fill=1); c.setFillColorRGB(0, 0, 0); c.setFont('Helvetica', 14)
    c.drawString(60, 150, 'El gato salta sobre la valla.'); c.drawString(60, 130, '¡Miau!')
    c.showPage()
c.save()
PY
FALLOS=0
for f in sangria bloque derecha cap notas palabras indice t-discreto t-numero t-romano mix; do
  echo "== $f"
  HUECO=1 node $AQUI/prueba-reflow.js $OUT/nov/$f.pdf $OUT/nov/$f.json | grep "✘\|exactos [0-9]*$" || true
  HUECO=1 node $AQUI/prueba-reflow.js $OUT/nov/$f.pdf $OUT/nov/$f.json | grep -q "✘" && FALLOS=$((FALLOS+1))
done
echo "== plano (sin sangría ni espacio entre párrafos: a veces no se puede saber dónde acaba un párrafo; basta el 90 %)"
node $AQUI/prueba-reflow.js $OUT/nov/plano.pdf $OUT/nov/plano.json | grep "exactos [0-9]*$"
echo "== casos del análisis"
node $AQUI/prueba-analizar.js | grep -v "^  ✔" ; node $AQUI/prueba-analizar.js | grep -q "✘" && FALLOS=$((FALLOS+1))
echo "== lector"
node $AQUI/prueba-pdf-texto.js $OUT/nov/sangria.pdf $OUT/nov/escaneado.pdf $OUT/capturas $OUT/nov/ilustrado.pdf | grep -v "^  ✔" ; node $AQUI/prueba-pdf-texto.js $OUT/nov/sangria.pdf $OUT/nov/escaneado.pdf $OUT/capturas $OUT/nov/ilustrado.pdf | grep -q "✘" && FALLOS=$((FALLOS+1))
echo "fallos: $FALLOS"
exit $FALLOS
