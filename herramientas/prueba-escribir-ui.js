#!/usr/bin/env node
'use strict';
// Escribir en la revista del PC, en un Chromium de verdad: la interfaz real (revista/js) con el módulo real de guardado
// (escritorio/src/main/escritos.js) detrás. Escribe, da formato, deshace, busca, abre el panel, compara versiones…
// y hace capturas. Uso (desde la raíz): node herramientas/prueba-escribir-ui.js [carpeta-capturas]
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');
const { crear } = require('../escritorio/src/main/escritos');

const RAIZ = path.join(__dirname, '../escritorio/src/renderer/revista');
const OUT = path.resolve(process.argv[2] || os.tmpdir());
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let fallos = 0;
const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) fallos++; };
// Barras de desplazamiento que sobran: cajas con scroll que desbordan solo unos píxeles (en Windows y en un Mac con
// ratón las barras se ven siempre). La página entera tampoco debe pasarse de la ventana por poco.
const barrasSueltas = (p) => p.evaluate(() => {
  const out = [];
  const nombre = (n) => n.tagName.toLowerCase() + (n.className && typeof n.className === 'string' ? '.' + n.className.trim().split(/\s+/).join('.') : '');
  for (const n of document.querySelectorAll('body *')) {
    const s = getComputedStyle(n);
    if (s.display === 'none') continue;
    const dy = n.scrollHeight - n.clientHeight; const dx = n.scrollWidth - n.clientWidth;
    if (/auto|scroll/.test(s.overflowY) && dy > 0 && dy <= 8) out.push(`${nombre(n)} ↕${dy}`);
    if (/auto|scroll/.test(s.overflowX) && dx > 0 && dx <= 8) out.push(`${nombre(n)} ↔${dx}`);
  }
  const d = document.scrollingElement; const dy = d.scrollHeight - window.innerHeight;
  if (dy > 0 && dy <= 60) out.push(`página ↕${dy}`);
  return out;
});
const num4 = (x) => Number(x) || 0;
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.ttf': 'font/ttf', '.png': 'image/png' };

const RV_hecho3 = (escr_, pid) => escr_.notas(pid).filter((n) => n.titulo === 'Esquema de la historia').some((n) => /Planteamiento:\s*\S[\s\S]*Nudo:\s*\S[\s\S]*Desenlace:\s*\S/.test(n.texto));
const escr = crear({ dir: fs.mkdtempSync(path.join(os.tmpdir(), 'esc-ui-')), dev: 'pc', net: { uid: () => null } });

function siembra() {
  const o = escr.nuevo('p', '', { titulo: 'La torre del cuervo', autor: 'Gonzalo', genero: 'Fantasía oscura', meta: 80000, diaria: 600, color: '#B0102F',
    sinopsis: 'Aldara sube a la torre donde nadie ha vuelto a ser la misma persona. Lo que encuentra arriba no es un monstruo, sino un espejo.' });
  const c1 = escr.nuevo('c', o.id, { titulo: 'La subida', parte: 'Parte I · El valle' });
  const c2 = escr.nuevo('c', o.id, { titulo: 'El cuervo' });
  const c3 = escr.nuevo('c', o.id, { titulo: 'Lo que dice el espejo', parte: 'Parte II · La torre' });
  const prosa = 'Aldara subió la torre sin mirar atrás. El viento del valle le arrancaba el capuchón y le llevaba las palabras de la hechicera, aquellas que había repetido tantas veces que ya no sabía si las recordaba o si las inventaba cada vez.\n\n—No hay nada arriba —dijo el Cuervo, posado en la barandilla—. Lo sabes.\n\n*Lo sé*, pensó ella, y siguió subiendo. Cada escalón era una pequeña mentira que se negaba a creer.\n\n***\n\nA medianoche encontró la primera puerta. Estaba abierta, y detrás había **una habitación igual que la suya**.';
  const e1 = escr.nuevo('e', o.id, { cid: c1.id, titulo: 'Al pie de la torre', estado: 2, texto: prosa, resumen: 'Aldara llega al valle y decide subir.', cuando: 'Día 1' });
  escr.nuevo('e', o.id, { cid: c1.id, titulo: 'La primera puerta', estado: 1, texto: 'La habitación olía a su infancia. Aldara tocó la colcha y la colcha le devolvió el gesto.\n\n> Todo lo que subes, lo bajas.\n\nNo era una amenaza. Era una instrucción.', resumen: 'Encuentra una habitación idéntica a la suya.', cuando: 'Día 1, medianoche' });
  escr.nuevo('e', o.id, { cid: c2.id, titulo: 'Un pacto en la barandilla', estado: 0, texto: 'El Cuervo no mentía, pero tampoco decía toda la verdad. Esa era su forma de ser honesto.', resumen: 'El Cuervo ofrece un trato.', cuando: 'Día 2' });
  escr.nuevo('e', o.id, { cid: c3.id, titulo: 'El espejo', estado: 0, texto: '', resumen: 'El clímax: Aldara se ve a sí misma.' });
  escr.nuevo('f', o.id, { tipo: 'personaje', nombre: 'Aldara', alias: 'la hechicera, Ald', resumen: 'Joven aprendiz que huye de su pasado.', color: '#B0102F', campos: [{ k: 'Edad', v: '23' }, { k: 'Teme', v: 'Parecerse a su maestra' }] });
  escr.nuevo('f', o.id, { tipo: 'personaje', nombre: 'El Cuervo', resumen: 'Guardián de la torre. Dice la verdad a medias.', color: '#4A6FA5' });
  escr.nuevo('f', o.id, { tipo: 'lugar', nombre: 'El valle', resumen: 'Tierra de nieblas al pie de la torre.', color: '#5B8C5A' });
  escr.nuevo('n', o.id, { tipo: 'idea', titulo: '¿Y si el espejo miente?', texto: 'Que lo que Aldara ve no sea ella, sino lo que la torre quiere que sea.', color: '#C9A227', fija: 1 });
  escr.nuevo('n', o.id, { tipo: 'plantilla', titulo: 'Ficha de escena', texto: 'Objetivo:\nConflicto:\nQué cambia:\n', color: '#7B5EA7' });
  escr.nuevo('n', o.id, { tipo: 'investigacion', titulo: 'Torres de vigía medievales', texto: 'Escaleras de caracol hacia la derecha: el defensor tenía libre el brazo de la espada.', color: '#3E8E9E' });
  escr.nuevo('h', o.id, { titulo: 'Aldara llega al valle', cuando: 'Día 1', eid: e1.id, nota: 'Sale al amanecer.' });
  escr.nuevo('h', o.id, { titulo: 'La primera puerta', cuando: 'Día 1, medianoche' });
  const hoy = escr.hoy();
  [[0, 340], [1, 720], [3, 510], [4, 880], [5, 430], [6, 650], [8, 300], [9, 910], [11, 520]].forEach(([k, n]) => escr.pon({ id: `l_${o.id}_${hoy - k}`, t: 'l', pid: o.id, dia: hoy - k, dev: { pc: n } }));
  return { o, e1 };
}

(async () => {
  const srv = http.createServer((q, r) => {
    const f = path.join(RAIZ, decodeURIComponent(new URL(q.url, 'http://x').pathname));
    if (!f.startsWith(RAIZ) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
    r.writeHead(200, { 'content-type': MIME[path.extname(f)] || 'application/octet-stream' }); r.end(fs.readFileSync(f));
  }).listen(0);
  const url = `http://127.0.0.1:${srv.address().port}/index.html`;
  const b = await chromium.launch();
  const ctx = await b.newContext({ viewport: { width: 1500, height: 950 }, locale: 'es-ES' });
  const p = await ctx.newPage();
  const errores = [];
  p.on('pageerror', (e) => errores.push(e.message));
  p.on('console', (m) => { if (m.type() === 'error') errores.push(m.text()); });
  await p.exposeFunction('__escr', async (op, ...a) => { const r = escr[op](...a); return r === undefined ? null : r; });
  await p.exposeFunction('__todo', async () => ({ docs: escr.todos(), estado: { ...escr.estado(), signed: false } }));
  await p.exposeFunction('__expo', async () => 'ruta');
  escr.onChange(() => { p.evaluate((x) => window.__emit && window.__emit('escritos', x), { docs: escr.todos(), estado: { ...escr.estado(), signed: false } }).catch(() => {}); });
  await p.addInitScript(() => {
    const cb = {};
    window.__emit = (c, x) => (cb[c] || []).forEach((f) => f(x));
    window.atrio = new Proxy({
      platform: 'linux',
      on: (c, f) => { (cb[c] = cb[c] || []).push(f); },
      state: async () => ({ config: { revista: { tema: 'claro' } }, games: [], play: { sessions: [], active: [] } }),
      ronron: async () => ({ status: { signed: false }, data: null }),
      fotoList: async () => ({}),
      escritosTodo: () => window.__todo(),
      escritos: (op, ...a) => window.__escr(op, ...a),
      escritosExportar: () => window.__expo(),
      escritosImportar: async () => null,
      log: async () => {},
    }, { get: (t, k) => (k in t ? t[k] : async () => null) });
  });
  await p.goto(url);
  await p.waitForFunction(() => window.RV && window.RV.E);
  await p.evaluate(() => window.RV.cambiarModo('revista'));

  console.log('Biblioteca vacía');
  await p.evaluate(() => window.RV.ir({ v: 'escribir' }));
  await sleep(500);
  ok(await p.locator('.e-vacio h2').count() === 1, 'invita a crear la primera obra');
  await p.screenshot({ path: path.join(OUT, 'pc-e01-vacio.png') });

  const { o, e1 } = siembra();
  await sleep(500);
  await p.evaluate(() => window.RV.refrescar());
  await sleep(300);
  ok(await p.locator('.e-obra').count() === 1 && /La torre del cuervo/.test(await p.locator('.e-obra .titulo').innerText()), 'la obra sale en la biblioteca');
  await p.screenshot({ path: path.join(OUT, 'pc-e02-biblioteca.png') });

  console.log('La obra');
  await p.locator('.e-obra').click();
  await sleep(400);
  ok(await p.locator('.e-escena').count() === 4 && await p.locator('.e-cap').count() === 3, 'manuscrito con 3 capítulos y 4 escenas');
  await p.screenshot({ path: path.join(OUT, 'pc-e03-manuscrito.png') });
  { const b = await barrasSueltas(p); ok(!b.length, `manuscrito sin barras de desplazamiento sobrantes ${b.join(', ')}`); }
  for (const [i, n] of [[1, 'fichas'], [2, 'notas'], [3, 'linea'], [4, 'cifras']]) {
    await p.locator('.e-tabs button').nth(i).click(); await sleep(350);
    await p.screenshot({ path: path.join(OUT, `pc-e0${3 + i}-${n}.png`) });
    { const b = await barrasSueltas(p); ok(!b.length, `${n}: sin barras de desplazamiento sobrantes ${b.join(', ')}`); }
    if (i === 1) ok(await p.locator('.e-ficha-tarj').count() === 3 && /sale en 2 escenas/i.test(await p.locator(".e-ficha-tarj").first().innerText()), 'fichas: «Aldara sale en 2 escenas»');
  }
  await p.locator('.e-tabs button').nth(0).click(); await sleep(300);

  console.log('El editor');
  await p.locator('.e-escena-cuerpo').first().click();
  await sleep(700);
  ok(await p.locator('.ed-texto').count() === 1, 'se abre el editor');
  ok(await p.locator('.ed-texto .e-neg').count() >= 1 && await p.locator('.ed-texto .e-cur').count() >= 1 && await p.locator('.ed-texto .e-ficha').count() >= 2, 'negrita, cursiva y fichas subrayadas');
  const texto0 = () => escr.get(e1.id).texto;
  ok((await p.locator('.ed-texto').evaluate((n) => n.textContent.replace(/​/g, ''))) === texto0(), 'lo que se ve es exactamente el texto guardado');
  await p.screenshot({ path: path.join(OUT, 'pc-e08-editor.png') });
  { const b = await barrasSueltas(p); ok(!b.length, `editor sin barras de desplazamiento sobrantes ${b.join(', ')}`); }
  // Escribir al final: letras, Enter, formato con teclas
  await p.locator('.ed-texto').click();
  await p.keyboard.press('Control+End');
  await p.keyboard.type('\n\nSubió otro tramo y *sonrió*.\nOtra línea con ñ, «comillas» y —raya—.');
  await sleep(1000);
  const t1 = texto0();
  ok(t1.endsWith('\n\nSubió otro tramo y *sonrió*.\nOtra línea con ñ, «comillas» y —raya—.'), `escribir queda guardado tal cual, con sus saltos de línea (${JSON.stringify(t1.slice(-70))})`);
  ok(await p.locator('.ed-texto .e-cur').count() >= 2, 'el formato nuevo se pinta tras una pausa');
  // Negrita con Ctrl+B sobre la selección, y deshacer
  await p.keyboard.press('Shift+Control+ArrowLeft');
  await p.keyboard.press('Control+b');
  await sleep(900);
  ok(/\*\*—raya—\.\*\*|—\*\*raya\*\*—|\*\*raya\*\*/.test(texto0()) || texto0().includes('**'), `Ctrl+B pone negrita (${JSON.stringify(texto0().slice(-30))})`);
  const conNegrita = texto0();
  await p.keyboard.press('Control+z');
  await sleep(900);
  ok(texto0() === t1, 'Ctrl+Z la deshace');
  await p.keyboard.press('Control+Shift+z');
  await sleep(900);
  ok(texto0() === conNegrita, 'Ctrl+Mayús+Z la rehace');
  await p.keyboard.press('Control+z'); await sleep(500);
  await p.keyboard.press('Control+y'); await sleep(900);
  ok(texto0() === conNegrita, 'y Ctrl+Y también');
  // Un Enter final (línea vacía al final) se conserva
  await p.keyboard.press('Control+End');
  await p.keyboard.press('Enter');
  await sleep(900);
  ok(texto0().endsWith('\n'), 'un salto de línea al final no se pierde');
  ok(escr.escritas(escr.hoy(), o.id) > 340, 'las palabras escritas suben las del día');
  await p.screenshot({ path: path.join(OUT, 'pc-e08b-escribiendo.png') });

  console.log('Tipografía automática y sprint');
  await p.keyboard.press('Control+End');
  await p.keyboard.type('\n- Vamos --dijo-- a "casa"...');
  await sleep(900);
  ok(texto0().endsWith('\n—Vamos —dijo— a «casa»…'), `-- es una raya, - al empezar es un diálogo, " son « », ... es … (${JSON.stringify(texto0().slice(-30))})`);
  await p.keyboard.press('Control+z'); await sleep(700);
  ok(texto0().endsWith('a «casa»...'), 'deshacer quita solo el último cambio de la tipografía');
  await p.keyboard.press('Control+Shift+z'); await sleep(700);
  ok(texto0().endsWith('a «casa»…'), `y rehacer lo vuelve a poner (${JSON.stringify(texto0().slice(-20))})`);
  await p.locator('.ed-barra button', { hasText: 'Sprint' }).click(); await sleep(300);
  await p.locator('.dialogo .e-chip', { hasText: '10 min' }).click();
  await p.locator('.dialogo input[type=number]').fill('20');
  await p.locator('.dialogo button', { hasText: 'Empezar' }).click(); await sleep(400);
  await p.locator('.ed-texto').click(); await p.keyboard.press('Control+End');
  await p.keyboard.type(' Y siguió escribiendo sin parar un buen rato.');
  await sleep(1500);
  ok(/sprint 9:5\d/i.test(await p.locator('.ed-cuentas').innerText()) && /\+\d+ de 20/.test(await p.locator('.ed-cuentas').innerText()), `el sprint enseña el tiempo y lo escrito (${(await p.locator('.ed-cuentas').innerText()).replace(/\n/g, ' ')})`);
  await p.screenshot({ path: path.join(OUT, 'pc-e08c-sprint.png') });
  await p.locator('.ed-cuentas button', { hasText: 'Terminar' }).click(); await sleep(400);
  const fin = await p.locator('.dialogo').innerText();
  ok(/sprint terminado/i.test(fin) && /palabras/i.test(fin), 'al terminar, el resultado');
  await p.screenshot({ path: path.join(OUT, 'pc-e08d-sprint-fin.png') });
  await p.keyboard.press('Escape'); await sleep(200);

  console.log('Menú Edición de macOS');
  await p.locator('.ed-texto').click(); await p.keyboard.press('Control+End');
  await p.keyboard.type(' Frase del menú.'); await sleep(1000);
  const conFrase = texto0();
  await p.evaluate(() => window.__emit('menu', 'deshacer')); await sleep(900);
  ok(!texto0().includes('Frase del menú.'), 'Edición → Deshacer deshace en el editor');
  await p.evaluate(() => window.__emit('menu', 'rehacer')); await sleep(900);
  ok(texto0() === conFrase, 'Edición → Rehacer lo rehace');
  await p.keyboard.press('Control+z'); await p.evaluate(() => window.__emit('menu', 'deshacer')); await sleep(900);
  await p.keyboard.press('Control+Shift+z'); await sleep(900);
  ok(texto0() === conFrase, 'si la tecla ya lo hizo, el menú no lo repite');
  await p.evaluate(() => window.__emit('menu', 'buscar')); await sleep(300);
  ok(await p.locator('.ed-busca').isVisible(), 'Edición → Buscar abre la búsqueda del editor');
  await p.keyboard.press('Escape'); await sleep(200);

  console.log('Panel, buscar y concentración');
  await p.locator('.ed-tecla.pan').click(); await sleep(400);
  await p.screenshot({ path: path.join(OUT, 'pc-e09-panel-escena.png') });
  for (const [i, n] of [[1, 'fichas'], [2, 'notas'], [3, 'borradores'], [4, 'historial']]) {
    await p.locator('.ed-panel-tabs button').nth(i).click(); await sleep(300);
    await p.screenshot({ path: path.join(OUT, `pc-e1${i - 1}-panel-${n}.png`) });
  }
  ok(await p.locator('.ed-panel .ed-item').count() >= 1, 'el historial muestra versiones');
  await p.locator('.ed-panel-tabs button').nth(1).click(); await sleep(250);
  await p.locator('.ed-panel .ed-item').first().locator('button', { hasText: 'Poner el nombre' }).click();
  await sleep(900);
  ok(/Aldara$|Aldara\n$/.test(texto0()), 'poner el nombre de una ficha lo escribe en el cursor');
  await p.locator('.ed-barra button', { hasText: 'Buscar' }).click();
  await p.locator('.ed-busca input[type=search]').fill('torre');
  await sleep(500);
  ok(await p.locator('.ed-texto .e-busca').count() >= 1, 'buscar resalta lo encontrado');
  await p.screenshot({ path: path.join(OUT, 'pc-e12-buscar.png') });
  await p.keyboard.press('Escape'); await sleep(200);
  ok(await p.locator('.ed-busca').isHidden(), 'Esc cierra la búsqueda (y no sale del editor)');
  await p.locator('.ed-tecla.pan').click();
  await p.locator('.ed-barra button', { hasText: 'Concentración' }).click(); await sleep(300);
  ok(await p.evaluate(() => document.body.classList.contains('ed-foco')), 'modo concentración');
  { const b = await barrasSueltas(p); ok(!b.length, `concentración sin barras de desplazamiento sobrantes ${b.join(', ')}`); }
  await p.screenshot({ path: path.join(OUT, 'pc-e13-concentracion.png') });
  await p.keyboard.press('Escape'); await sleep(200);
  ok(!(await p.evaluate(() => document.body.classList.contains('ed-foco'))), 'Esc sale de la concentración');
  ok((await p.evaluate(() => window.RV.ruta().v)) === 'editor', 'y sigue en el editor');

  console.log('Conflicto, borradores y comparar');
  await p.locator('.ed-tecla.pan').click(); await p.locator('.ed-panel-tabs button').nth(3).click(); await sleep(300);
  await p.locator('.ed-panel button', { hasText: 'Guardar el texto actual como borrador' }).click(); await sleep(500);
  ok(escr.borradores(e1.id).length === 1, 'guardar un borrador');
  const antes = texto0();
  escr.escribe(e1.id, `${antes}\nOtro dispositivo añadió esto.`); // llega un cambio de fuera
  await sleep(500);
  ok((await p.locator('.ed-texto').evaluate((n) => n.textContent.replace(/​/g, ''))).endsWith('Otro dispositivo añadió esto.'), 'un cambio de otro dispositivo se ve al momento, sin recargar la página');
  await p.locator('.ed-panel .ed-item button', { hasText: 'Comparar y mezclar' }).first().click(); await sleep(400);
  ok(await p.locator('.dialogo .e-cambio').count() >= 1, 'comparar enseña las diferencias');
  await p.screenshot({ path: path.join(OUT, 'pc-e14-comparar.png') });
  await p.keyboard.press('Escape');

  console.log('Salir y volver');
  await p.locator('.ed-cab button').first().click(); await sleep(400);
  ok((await p.evaluate(() => window.RV.ruta().v)) === 'obra', 'volver a la obra');

  console.log('Seguir escribiendo, portada y etiquetas');
  const [ea, eb] = escr.escenasDe(o.id);
  escr.edita(ea.id, { etq: ['flashback'] }); escr.edita(eb.id, { etq: ['flashback', 'Cuervo'] });
  escr.escribe(eb.id, `${escr.get(eb.id).texto}\n\nY la colcha le contó un secreto.`);
  await sleep(300);
  await p.evaluate(() => window.RV.ir({ v: 'escribir' })); await sleep(500);
  ok(/sigue escribiendo/i.test(await p.locator('.e-sigue').innerText()) && /La primera puerta/.test(await p.locator('.e-sigue').innerText()), 'la biblioteca: «Sigue escribiendo» con la última escena');
  await p.screenshot({ path: path.join(OUT, 'pc-e17-sigue.png') });
  await p.evaluate(() => window.RV.ir({ v: 'portada' })); await sleep(600);
  ok(await p.locator('.p-escribiendo').count() === 1 && /La torre del cuervo/.test(await p.locator('.p-escribiendo').innerText()), 'la portada: «Escribiendo» con la obra');
  await p.locator('.p-escribiendo').scrollIntoViewIfNeeded();
  await p.screenshot({ path: path.join(OUT, 'pc-e18-portada.png') });
  await p.locator('.p-escribiendo button', { hasText: 'Seguir con' }).click(); await sleep(600);
  ok((await p.evaluate(() => window.RV.ruta())).id === eb.id, '«Seguir con…» abre esa escena en el editor');
  await p.evaluate((id) => window.RV.ir({ v: 'obra', id, tab: 0 }), o.id); await sleep(500);
  const todas = await p.locator('.e-escena').count();
  await p.locator('.e-filtro .e-chip', { hasText: '#Cuervo' }).click(); await sleep(400);
  ok(await p.locator('.e-escena').count() === 1 && todas === 4, `filtrar por etiqueta deja solo esas escenas (${await p.locator('.e-escena').count()} de ${todas})`);
  await p.screenshot({ path: path.join(OUT, 'pc-e19-filtro.png') });
  await p.locator('.e-filtro .e-chip', { hasText: 'Todas' }).click(); await sleep(300);
  ok(await p.locator('.e-escena').count() === todas, 'y «Todas» las vuelve a enseñar');

  console.log('Colores de la obra');
  await p.evaluate((id) => window.RV.E.dlgObra(window.RV.E.get(id)), o.id); await sleep(400);
  await p.locator('.dialogo .e-color').nth(4).click(); await sleep(100);
  ok(await p.locator('.dialogo .e-color').nth(4).getAttribute('aria-pressed') === 'true' && await p.locator('.dialogo .e-color[aria-pressed=true]').count() === 1, 'elegir un color marca ese (y no el primero)');
  await p.locator('.dialogo button', { hasText: 'Guardar' }).click(); await sleep(400);
  ok(escr.get(o.id).color === '#3E8E9E', `y se guarda (${escr.get(o.id).color})`);

  console.log('Papelera');
  const s2 = escr.escenasDe(o.id)[1];
  await p.evaluate((id) => window.RV.E.aPapelera(window.RV.E.get(id)), s2.id); await sleep(500);
  ok(!escr.get(s2.id) && await p.locator('.e-escena').count() === 3, 'borrar una escena la quita del manuscrito');
  ok(/en la papelera/.test(await p.locator('#aviso').innerText()) && await p.locator('#aviso .aviso-accion').isVisible(), 'el aviso dice que está en la papelera, con «Deshacer»');
  await p.screenshot({ path: path.join(OUT, 'pc-e15-deshacer.png') });
  await p.locator('#aviso .aviso-accion').click(); await sleep(500);
  ok(!!escr.get(s2.id) && await p.locator('.e-escena').count() === 4, '«Deshacer» la trae de vuelta');
  const c2 = escr.capitulos(o.id)[1];
  await p.evaluate((id) => window.RV.E.aPapelera(window.RV.E.get(id)), c2.id); await sleep(300);
  await p.evaluate(() => window.RV.ir({ v: 'escribir' })); await sleep(500);
  ok(/papelera · 1/i.test(await p.locator('.e-ir-papelera', { hasText: 'Papelera' }).innerText()), 'la biblioteca enseña cuánto hay en la papelera');
  await p.locator('.e-ir-papelera', { hasText: 'Papelera' }).click(); await sleep(500);
  ok(await p.locator('.dialogo .e-pap').count() === 1 && /con 1 escena/i.test(await p.locator('.dialogo .e-pap').innerText()), 'en la papelera, el capítulo con su escena dentro');
  await p.screenshot({ path: path.join(OUT, 'pc-e16-papelera.png') });
  await p.locator('.dialogo .e-pap button', { hasText: 'Recuperar' }).click(); await sleep(500);
  ok(escr.capitulos(o.id).length === 3 && escr.escenasDe(o.id).length === 4 && await p.locator('.dialogo .e-pap-vacia').count() === 1, 'recuperar lo devuelve todo a su sitio');
  await p.keyboard.press('Escape');

  console.log('La guía de la novela');
  // Una obra nueva con la guía activada, como la deja «Nueva obra → Guiarme paso a paso»
  const g = escr.nuevo('p', '', { titulo: 'Mi primera novela', gOn: 1, meta: 1000, diaria: 300 });
  const gc = escr.nuevo('c', g.id, { titulo: 'Capítulo 1' }); escr.nuevo('e', g.id, { cid: gc.id, titulo: 'Escena 1' });
  await sleep(300);
  await p.evaluate((id) => window.RV.ir({ v: 'obra', id, tab: 0 }), g.id); await sleep(500);
  ok(await p.locator('.e-paso-ahora').count() === 1 && /paso 1 de 11/i.test(await p.locator('.e-paso-ahora').innerText()), 'el manuscrito enseña el paso que toca (el 1 de 11)');
  await p.screenshot({ path: path.join(OUT, 'pc-e20-guia-tarjeta.png') });
  await p.locator('.e-paso-ahora button', { hasText: 'Escribir mi frase' }).click(); await sleep(400);
  await p.locator('.dialogo textarea').fill('Una aprendiz sube a una torre para salvar a su hermana.');
  await p.locator('.dialogo button', { hasText: 'Guardar' }).click(); await sleep(500);
  ok(escr.get(g.id).idea.startsWith('Una aprendiz') && /paso 2 de 11/i.test(await p.locator('.e-paso-ahora').innerText()), 'guardar la frase marca el 1 y pasa al 2');
  await p.locator('.e-paso-ahora button', { hasText: 'Ver la guía' }).click(); await sleep(500);
  ok(await p.locator('.e-paso').count() === 11 && await p.locator('.e-paso.hecho').count() === 1 && /1 de 11 pasos/.test(await p.locator('.e-guia .e-resumen').innerText()), 'la pestaña Guía: 11 pasos y 1 hecho');
  ok(/Una aprendiz/.test(await p.locator('.e-idea').innerText()) && await p.locator('.e-paso.ahora .e-paso-cuerpo').count() === 1, 'enseña tu frase y el paso de ahora abierto');
  await p.screenshot({ path: path.join(OUT, 'pc-e21-guia.png') });
  { const b = await barrasSueltas(p); ok(!b.length, `la guía, sin barras de desplazamiento sobrantes ${b.join(', ')}`); }
  // El paso 3 crea una nota «Esquema de la historia» (fuera del manuscrito) con un apartado por acto
  const antesCaps = escr.capitulos(g.id).length;
  await p.locator('.e-paso-cab', { hasText: 'Un esquema ligero' }).click(); await sleep(300);
  await p.locator('.e-paso-cuerpo button', { hasText: 'Crear los tres actos' }).click(); await sleep(700);
  const esq = escr.notas(g.id).find((n) => n.titulo === 'Esquema de la historia');
  ok(!!esq && /Planteamiento:[\s\S]*Nudo:[\s\S]*Desenlace:/.test(esq.texto) && escr.capitulos(g.id).length === antesCaps, 'el esquema es una nota con Planteamiento, Nudo y Desenlace, y no toca el manuscrito');
  ok(await p.locator('.dialogo textarea').count() >= 1, 'y se abre para escribir en ella');
  await p.locator('.dialogo textarea').last().fill('Planteamiento:\nUna aprendiz sube a la torre.\n\nNudo:\nLa torre la cambia.\n\nDesenlace:\nSe encuentra a sí misma.\n');
  await p.locator('.dialogo button', { hasText: 'Guardar' }).click(); await sleep(600);
  ok(RV_hecho3(escr, g.id), 'con los tres apartados escritos, el paso 3 queda hecho');
  // Un paso a mano: marcar, saltar y quitar la marca
  await p.evaluate((id) => window.RV.ir({ v: 'obra', id, tab: 5 }), g.id); await sleep(400);
  await p.locator('.e-paso-cab', { hasText: 'Promesa, progreso y recompensa' }).click(); await sleep(300);
  await p.locator('.e-paso-cuerpo button', { hasText: 'Ya lo tengo' }).click(); await sleep(500);
  ok(num4(escr.get(g.id).g4) === 1 && await p.locator('.e-paso.hecho').count() === 3, '«Ya lo tengo» marca un paso a mano');
  ok(/Un hábito realista/.test(await p.locator('.e-paso.abierto, .e-paso:has(.e-paso-cuerpo)').first().innerText()), 'y abre el paso siguiente');
  await p.locator('.e-paso-cab', { hasText: 'Promesa, progreso y recompensa' }).click(); await sleep(300);
  await p.locator('.e-paso-cuerpo button', { hasText: 'Quitar la marca' }).click(); await sleep(500);
  ok(await p.locator('.e-paso.hecho').count() === 2, '«Quitar la marca» lo deja como estaba');
  // El reposo: empezar y que cuente días
  await p.locator('.e-paso-cab', { hasText: 'Dejarlo reposar' }).click(); await sleep(300);
  await p.locator('.e-paso-cuerpo button', { hasText: 'Empezar el reposo' }).click(); await sleep(500);
  ok(escr.get(g.id).g7t > 0 && /Reposo: 0 días/i.test(await p.locator('.e-paso', { hasText: 'Dejarlo reposar' }).innerText()), 'el reposo empieza a contar días');
  // Desactivar: ya no sale la tarjeta en el manuscrito
  await p.locator('.e-guia button', { hasText: 'Desactivar' }).click(); await sleep(400);
  await p.evaluate((id) => window.RV.ir({ v: 'obra', id, tab: 0 }), g.id); await sleep(400);
  ok(await p.locator('.e-paso-ahora').count() === 0, 'con la guía desactivada no sale el paso en el manuscrito');
  // «Nueva obra»: la casilla viene marcada y lleva a la guía
  await p.evaluate(() => window.RV.E.dlgObra(null)); await sleep(400);
  ok(await p.locator('.dialogo .e-guiarme input').isChecked(), 'en «Nueva obra», «Guiarme paso a paso» viene marcada');
  await p.locator('.dialogo input[data-autofocus]').fill('Otra novela');
  await p.locator('.dialogo button', { hasText: 'Crear' }).click(); await sleep(700);
  ok(await p.locator('.e-guia').count() === 1 && await p.locator('.e-paso.ahora').count() === 1, 'al crearla se abre la guía, con el paso 1 por delante');
  await p.screenshot({ path: path.join(OUT, 'pc-e22-guia-nueva.png') });
  ok(errores.length === 0, `sin errores en la página ${errores.slice(0, 3).join(' | ')}`);
  await b.close(); srv.close();
  console.log(fallos ? `FALLAN ${fallos}` : 'Todo bien');
  process.exit(fallos ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
