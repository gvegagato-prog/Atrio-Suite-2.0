#!/usr/bin/env node
'use strict';
// Casos concretos del análisis de «texto adaptable» (foliate/pdf-reflow.js · analizar), con páginas hechas a mano:
// líneas con su posición, tamaño y texto, como las que salen de pdf.js. Corre en Chromium (el módulo es del navegador).
// Uso: node herramientas/lector/prueba-analizar.js
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');
const ASSETS = path.resolve(__dirname, '../../movil/app/src/main/assets');
const MIME = { html: 'text/html', js: 'text/javascript', mjs: 'text/javascript', css: 'text/css' };
const srv = http.createServer((req, res) => {
  const u = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (u === '/t.html') { res.writeHead(200, { 'Content-Type': 'text/html' }); return res.end('<!doctype html><meta charset=utf-8>'); }
  const f = u.startsWith('/assets/') ? path.join(ASSETS, u.slice(8)) : null;
  if (!f || !fs.existsSync(f)) { res.writeHead(404); return res.end('no'); }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(f).slice(1)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});
(async () => {
  await new Promise((r) => srv.listen(0, r));
  const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined });
  const page = await browser.newPage();
  page.on('pageerror', (e) => console.log('[error]', e.message));
  await page.goto(`http://localhost:${srv.address().port}/t.html`);
  const r = await page.evaluate(async () => {
    const m = await import('/assets/lector/foliate/pdf-reflow.js');
    // Página de 396 × 612 pt, texto de 52 a 344, letra de 11 pt, interlineado 14.5
    const M = 52, B = 344, S = 11, L = 14.5;
    const ln = (x0, x1, y, t, o = {}) => ({ x0, x1, y, s: o.s || S, n: t.replace(/\s/g, '').length, runs: [{ t, i: !!o.i, b: !!o.b, sup: false, s: o.s || S, x0 }] });
    const RELLENO = 'Había aprendido, a fuerza de equivocarse, que las casas viejas guardan sus secretos en los rincones';
    // Páginas de relleno: párrafos normales con sangría (para que el análisis sepa cómo es el libro)
    const relleno = (n) => Array.from({ length: n }, () => {
      const lines = []; let y = 70;
      for (let k = 0; k < 34; k++) { const fin = k % 6 === 5; lines.push(ln(k % 6 === 0 ? 66 : M, fin ? 200 : B, y, fin ? 'menos pensados del mundo.' : RELLENO)); y += L; }
      return { w: 396, h: 612, lines };
    });
    const pagina = (lines) => ({ w: 396, h: 612, lines });
    const analiza = (lines) => {
      const a = m.analizar([...relleno(4), pagina(lines)]);
      return a.blocks.filter((b) => b.page === 4).map((b) => ({ k: b.k, al: b.al, t: m.textoBloque(b) }));
    };
    const casos = {};
    let y = 70;
    const sig = () => (y += L);
    // 1. Una carta sangrada a los dos lados, de cuatro líneas, entre dos párrafos normales; y su firma a la derecha
    y = 70;
    casos.carta = analiza([
      ln(66, B, y, 'Abrió el sobre con cuidado y leyó despacio, como quien teme romper algo frágil'), ln(M, 230, sig(), 'con la mirada.'),
      ln(84, 312, sig(), 'Querida Marta: te escribo desde el puerto, donde la lluvia no'), ln(84, 312, sig(), 'ha parado en tres días y el farero dice que no parará nunca,'),
      ln(84, 312, sig(), 'que el mar se ha cansado de nosotros y que ya no sabe cómo'), ln(84, 250, sig(), 'pedirnos que nos vayamos.'),
      ln(250, B, sig(), 'Tu hermano, Tomás.'),
      ln(66, B, sig(), 'Dobló la carta y la guardó en el bolsillo del abrigo sin decir'), ln(M, 160, sig(), 'una palabra.'),
    ]);
    // 2. Un epígrafe centrado
    y = 70;
    casos.epigrafe = analiza([
      ln(120, 276, y, 'Todo pasa y todo queda.', { i: true }),
      ln(66, B, sig() + L, 'La lluvia llevaba tres días cayendo sobre el puerto y nadie recordaba'), ln(M, 210, sig(), 'ya el color del cielo.'),
    ]);
    // 3. Una línea que empieza con puntuación (tras «—dijo—» al final de la anterior)
    y = 70;
    casos.puntuacion = analiza([
      ln(66, B, y, '—No pienso quedarme aquí a esperar que escampe —dijo—'), ln(M, 180, sig(), ', y se marchó sin más.'),
    ]);
    // 4. Versos (líneas cortas sin punto, cada una empieza en mayúscula) dentro de un párrafo
    y = 70;
    casos.versos = analiza([
      ln(66, 200, y, 'Caminante, son tus huellas'), ln(M, 190, sig(), 'el camino y nada más'), ln(M, 160, sig(), 'Caminante, no hay camino'), ln(M, 170, sig(), 'se hace camino al andar.'),
    ]);
    // 5. Texto sin justificar: la línea es corta porque la palabra siguiente es muy larga; no es un verso
    y = 70;
    casos.ragged = analiza([
      ln(66, 250, y, 'Llegó una carta firmada por la señora'), ln(M, 300, sig(), 'Wolfeschlegelsteinhausenbergerdorff, que nadie conocía.'),
    ]);
    // 6. Un título de verdad con una palabra larga que el libro no usa: no se parte
    y = 150;
    casos.titulo = analiza([
      ln(120, 276, y, 'Incertidumbres', { s: 20, b: true }),
      ln(M, B, y + 60, RELLENO), ln(M, B, y + 60 + L, RELLENO), ln(M, B, y + 60 + 2 * L, RELLENO), ln(M, 220, y + 60 + 3 * L, 'y nada más que eso.'),
    ]);
    // 7. Un título con las letras separadas (pdf.js no ve los espacios entre palabras): se parte con las palabras del libro
    y = 150;
    casos.espaciado = analiza([
      ln(120, 276, y, 'L a s c a s a s v i e j a s', { s: 20, b: true }),
      ln(M, B, y + 60, RELLENO), ln(M, B, y + 60 + L, RELLENO), ln(M, B, y + 60 + 2 * L, RELLENO), ln(M, 220, y + 60 + 3 * L, 'y nada más que eso.'),
    ]);
    // 8. Libro con párrafos separados por espacio y sin sangría
    const bloque = Array.from({ length: 5 }, () => {
      const lines = []; let yy = 70;
      for (let k = 0; k < 30; k++) { const fin = k % 5 === 4; lines.push(ln(M, fin ? 200 : B, yy, fin ? 'menos pensados del mundo.' : RELLENO)); yy += L + (fin ? 8 : 0); }
      return { w: 396, h: 612, lines };
    });
    casos.estiloBloque = m.analizar(bloque).estilo;
    casos.estiloSangria = m.analizar(relleno(5)).estilo;
    return casos;
  });
  const ok = (c, msg) => { console.log((c ? '  ✔ ' : '  ✘ ') + msg); if (!c) process.exitCode = 1; };
  const ps = (k) => r[k].filter((b) => b.k === 'p');
  console.log('Casos del análisis');
  ok(ps('carta').length === 4 && /^Querida Marta: .*pedirnos que nos vayamos\.$/.test(ps('carta')[1].t), `una carta sangrada es un solo párrafo (${ps('carta').length} párrafos: ${ps('carta').map((p) => p.t.slice(0, 18)).join(' | ')})`);
  ok(ps('carta')[2]?.al === 'd' && ps('carta')[2]?.t === 'Tu hermano, Tomás.', `la firma va a la derecha (${ps('carta')[2]?.al})`);
  ok(ps('epigrafe')[0]?.al === 'c', `un epígrafe centrado se queda centrado (${ps('epigrafe')[0]?.al})`);
  ok(ps('puntuacion')[0]?.t.includes('—dijo—, y se marchó'), `sin espacio delante de una coma que empieza línea («${ps('puntuacion')[0]?.t.slice(-34)}»)`);
  ok((ps('versos')[0]?.t.match(/\n/g) || []).length === 3, `versos con su salto de línea (${JSON.stringify(ps('versos')[0]?.t)})`);
  ok(!ps('ragged')[0]?.t.includes('\n') && ps('ragged').length === 1, 'una línea corta porque no cabía la palabra siguiente no es un verso');
  ok(r.titulo.some((b) => b.k === 'h' && b.t === 'Incertidumbres'), `un título con una palabra larga no se parte («${(r.titulo.find((b) => b.k === 'h') || {}).t}»)`);
  ok(r.espaciado.some((b) => b.k === 'h' && b.t === 'Las casas viejas'), `un título con las letras separadas recupera sus espacios («${(r.espaciado.find((b) => b.k === 'h') || {}).t}»)`);
  ok(r.estiloBloque === 'bloque' && r.estiloSangria === 'sangria', `el estilo de párrafo del libro (${r.estiloBloque} / ${r.estiloSangria})`);
  await browser.close(); srv.close();
})();
