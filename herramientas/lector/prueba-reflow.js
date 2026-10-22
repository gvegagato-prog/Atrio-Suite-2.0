#!/usr/bin/env node
'use strict';
// Prueba del modo «texto adaptable» de los PDF (foliate/pdf-reflow.js) en Chromium.
// Uso: node herramientas/lector/prueba-reflow.js novela.pdf [novela.json] [--vuelca]
// Con el .json que escribe generar-novela.py compara los párrafos extraídos con los de verdad.
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');
const ASSETS = path.resolve(__dirname, '../../movil/app/src/main/assets');
const MIME = { html: 'text/html', js: 'text/javascript', mjs: 'text/javascript', css: 'text/css', json: 'application/json' };
const args = process.argv.slice(2).filter((a) => !a.startsWith('--'));
const vuelca = process.argv.includes('--vuelca');
const [pdf, verdadF] = args;
const srv = http.createServer((req, res) => {
  const u = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (u === '/t.html') { res.writeHead(200, { 'Content-Type': 'text/html' }); return res.end('<!doctype html><meta charset=utf-8><body>'); }
  let f = null;
  if (u.startsWith('/assets/')) f = path.join(ASSETS, u.slice(8));
  if (u === '/p.pdf') f = pdf;
  if (!f || !fs.existsSync(f)) { res.writeHead(404); return res.end('no'); }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(f).slice(1)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});
const norm = (t) => t.replace(/\s+/g, ' ').trim();
(async () => {
  await new Promise((r) => srv.listen(0, r));
  const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined });
  const page = await browser.newPage();
  page.on('console', (m) => { if (m.type() === 'error') console.log('[navegador]', m.text()); });
  page.on('pageerror', (e) => console.log('[error]', e.message));
  await page.goto(`http://localhost:${srv.address().port}/t.html`);
  const r = await page.evaluate(async () => {
    const m = await import('/assets/lector/foliate/pdf-reflow.js');
    const { abrirPDF } = await import('/assets/lector/foliate/pdf.js');
    const blob = await (await fetch('/p.pdf')).blob();
    const file = new File([blob], 'p.pdf');
    const doc = await abrirPDF(file, { fontExtraProperties: true });
    const t0 = performance.now();
    const pages = await m.extraer(doc);
    const t1 = performance.now();
    const a = m.analizar(pages);
    const caps = m.capitulos(a.blocks, { paginas: pages.length });
    const t2 = performance.now();
    return {
      ms: [Math.round(t1 - t0), Math.round(t2 - t1)], paginas: pages.length, columnas: pages.filter((p) => p.cols).length, stats: a.stats,
      blocks: a.blocks.map((b) => ({ k: b.k, page: b.page, cap: b.cap, t: m.textoBloque(b), it: b.runs ? b.runs.filter((x) => x.i).map((x) => x.t) : undefined })),
      caps: caps.map((c) => ({ titulo: c.titulo, n: c.blocks.length })),
    };
  });
  const ok = (c, msg) => { console.log((c ? '  ✔ ' : '  ✘ ') + msg); if (!c) process.exitCode = 1; };
  console.log(`${path.basename(pdf)}: ${r.paginas} páginas · extraer ${r.ms[0]} ms · analizar ${r.ms[1]} ms`);
  console.log('  páginas a dos columnas:', r.columnas);
  console.log('  cuerpo', r.stats.body, 'interlineado', r.stats.lead, 'justificado', r.stats.justificado, 'márgenes', r.stats.marg, 'borde', r.stats.borde);
  console.log('  capítulos:', r.caps.map((c) => `${c.titulo} (${c.n})`).join(' | '));
  if (vuelca) for (const b of r.blocks.slice(0, 60)) console.log(`  [${b.k} p${b.page}${b.cap ? ' CAP' : ''}] ${b.t.slice(0, 110)}`);
  if (verdadF) {
    const v = JSON.parse(fs.readFileSync(verdadF, 'utf8'));
    const verdad = v.parrafos.filter((p) => !p.startsWith('## ') && p !== '* * *').map(norm);
    const sacados = r.blocks.filter((b) => b.k === 'p').map((b) => norm(b.t));
    const setS = new Set(sacados);
    const exactos = verdad.filter((p) => setS.has(p)).length;
    const faltan = verdad.filter((p) => !setS.has(p));
    const sobran = sacados.filter((p) => !new Set(verdad).has(p));
    console.log(`  párrafos de verdad ${verdad.length}, extraídos ${sacados.length}, exactos ${exactos}`);
    ok(exactos / verdad.length >= 0.97, `≥ 97 % de los párrafos exactos (${Math.round((100 * exactos) / verdad.length)} %)`);
    ok(Math.abs(sacados.length - verdad.length) <= verdad.length * 0.03, 'nº de párrafos parecido');
    const seps = r.blocks.filter((b) => b.k === 'sep').length;
    ok(seps === v.parrafos.filter((p) => p === '* * *').length, `separadores de escena: ${seps}`);
    const chs = r.blocks.filter((b) => b.k === 'h' && b.cap).length;
    ok(chs === v.capitulos.length, `capítulos detectados: ${chs} de ${v.capitulos.length}`);
    for (const f of faltan.slice(0, 4)) console.log('    falta:', f.slice(0, 120));
    for (const f of sobran.slice(0, 4)) console.log('    sobra:', f.slice(0, 120));
    const cursivas = r.blocks.filter((b) => b.it && b.it.length).length;
    console.log('  párrafos con cursiva:', cursivas);
  }
  await browser.close(); srv.close();
})();
