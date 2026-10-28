#!/usr/bin/env node
'use strict';
// Exportar e importar «Escribir» en el PC: Markdown, texto, Word y EPUB (archivos bien formados, comprobados con el
// analizador XML de Chromium) y de vuelta; los mismos casos que EscribirExportTest.kt del móvil.
const fs = require('fs');
const os = require('os');
const path = require('path');
let chromium = null;
try { ({ chromium } = require(process.env.PW || 'playwright')); } catch { /* sin Playwright: no se comprueba el XML con Chromium */ }
const { crear } = require('../escritorio/src/main/escritos');
const X = require('../escritorio/src/main/escritos-export');
let fallos = 0;
const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) fallos++; };

const e = crear({ dir: fs.mkdtempSync(path.join(os.tmpdir(), 'esc-exp-')), dev: 'pc', net: { uid: () => null } });
const o = e.nuevo('p', '', { titulo: 'La torre & el <cuervo>', autor: 'Gonzalo' });
const c1 = e.nuevo('c', o.id, { titulo: 'Uno', parte: 'Parte I' });
const c2 = e.nuevo('c', o.id, { titulo: 'Dos' });
e.nuevo('e', o.id, { cid: c1.id, titulo: 'Llegada', estado: 2, texto: 'Aldara subió la torre.\nSe quedó *mirando* el valle.\n\n> Dicen que **nadie** vuelve.' });
e.nuevo('e', o.id, { cid: c1.id, titulo: 'Noche', estado: 1, texto: 'La noche cayó.\n\n***\n\nOtra escena dentro.' });
e.nuevo('e', o.id, { cid: c2.id, titulo: 'Fin', estado: 0, texto: '# Un título dentro\n\nEl final.' });

(async () => {
  const md = X.markdown(e, o.id);
  ok(md.startsWith('# La torre & el <cuervo>\n\nGonzalo\n\n# Parte I\n\n## Uno\n\nAldara subió la torre. Se quedó *mirando* el valle.\n\n> Dicen que **nadie** vuelve.\n\n* * *\n\nLa noche cayó.'), 'Markdown');
  ok(!X.markdown(e, o.id, { soloEstadoMinimo: 1 }).includes('El final.'), 'solo revisión y finales');
  const tx = X.texto(e, o.id);
  ok(tx.startsWith('LA TORRE & EL <CUERVO>') && tx.includes('Se quedó mirando el valle.') && !tx.includes('*mirando*'), 'texto plano');

  const dx = X.docx(e, o.id);
  const ep = X.epub(e, o.id);
  ok(ep.subarray(30, 38).toString() === 'mimetype', 'EPUB: «mimetype» primero');
  const nombres = (buf) => { const out = []; let p = buf.readUInt32LE(buf.length - 22 + 16); const n = buf.readUInt16LE(buf.length - 22 + 10); for (let k = 0; k < n; k++) { const nl = buf.readUInt16LE(p + 28); out.push(buf.toString('utf8', p + 46, p + 46 + nl)); p += 46 + nl + buf.readUInt16LE(p + 30) + buf.readUInt16LE(p + 32); } return out; };
  ok(nombres(dx).join() === '[Content_Types].xml,_rels/.rels,word/_rels/document.xml.rels,word/document.xml,word/styles.xml,word/footer1.xml,docProps/core.xml', 'Word: sus archivos (con el pie con el número de página)');
  const ms = X.docx(e, o.id, { estilo: 'manuscrito', interlineado: '2' });
  const msDoc = X.leeZip(ms, 'word/document.xml').toString(); const msEst = X.leeZip(ms, 'word/styles.xml').toString();
  ok(/palabras/.test(msDoc) && msDoc.includes('<w:titlePg/>') && msDoc.includes('>FIN<') && msEst.includes('Times New Roman') && msEst.includes('w:line="480"'), 'Word (manuscrito): portada con recuento, sin número en la portada, Times 12 a doble espacio y FIN');
  ok(X.recuento(84700) === 'unas 85.000 palabras' && X.recuento(3200) === 'unas 3000 palabras' && X.recuento(812) === '812 palabras', 'recuento de palabras como el móvil');
  const hp = X.htmlPdf(e, o.id, { estilo: 'libro' });
  ok(/@page\{size:A5/.test(hp) && /@bottom-center\{content:counter\(page\)/.test(hp) && /class="capitulo"/.test(hp), 'PDF (libro): A5, numeración y capítulos en página nueva');
  ok(nombres(ep).includes('OEBPS/p3.xhtml'), 'EPUB: un archivo por capítulo');

  // Bien formados (el analizador XML de Chromium)
  if (chromium) {
    const b = await chromium.launch(); const p = await b.newPage();
    const mal = await p.evaluate(({ xmls }) => xmls.map(([n, t]) => { const d = new DOMParser().parseFromString(t, 'application/xml'); return d.querySelector('parsererror') ? n : null; }).filter(Boolean),
      { xmls: [...nombres(dx).filter((n) => !n.endsWith('/')).map((n) => ['docx:' + n, X.leeZip(dx, n).toString('utf8')]), ...nombres(ep).filter((n) => n !== 'mimetype' && !n.endsWith('.css')).map((n) => ['epub:' + n, X.leeZip(ep, n).toString('utf8')])] });
    ok(mal.length === 0, `todos los XML bien formados ${mal.join(', ')}`);
    await b.close();
  } else console.log('  (sin Playwright: no se comprueba que el XML esté bien formado)');
  const doc = X.leeZip(dx, 'word/document.xml').toString('utf8');
  ok(doc.includes('<w:b/>') && doc.includes('<w:i/>') && doc.includes('La torre &amp; el &lt;cuervo&gt;') && doc.includes('w:val="Heading2"'), 'Word: formato y estilos');
  const p2 = X.leeZip(ep, 'OEBPS/p2.xhtml').toString('utf8');
  ok(p2.includes('<em>mirando</em>') && p2.includes('<strong>nadie</strong>') && p2.includes('* * *'), 'EPUB: cursiva, negrita y salto');

  // Importar
  const id = X.importaTexto(e, 'Importada', '# Uno\n\nTexto uno.\n\n## Dos\n\nTexto dos.\n\nMás texto.');
  ok(e.capitulos(id).map((x) => x.titulo).join() === 'Uno,Dos' && e.escenas(e.capitulos(id)[1].id)[0].texto === 'Texto dos.\n\nMás texto.', 'importar Markdown a capítulos');
  ok(e.capitulos(X.importaTexto(e, 'Plana', 'Solo texto\nsin capítulos.')).length === 1, 'importar texto sin títulos');
  const t2 = X.textoDeDocx(dx);
  ok(t2.includes('# Uno') && t2.includes('Aldara subió la torre.'), 'importar el Word que se exportó');
  console.log(fallos ? `FALLAN ${fallos}` : 'Todo bien');
  process.exit(fallos ? 1 : 0);
})();
