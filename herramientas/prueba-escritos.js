#!/usr/bin/env node
'use strict';
// Prueba de «Escribir» en el PC (escritorio/src/main/escritos.js) contra el Firebase de mentira: guardado en archivos,
// escribir sin conexión y subir después, dos dispositivos a la vez (mezcla y conflictos sin perder texto), borrados y las
// palabras del día. Los mismos casos que EscritosTest.kt del móvil. Uso (desde la raíz): node herramientas/prueba-escritos.js
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const assert = require('assert');
const { crear, palabras, lapida } = require('../escritorio/src/main/escritos');
const M = require('../escritorio/src/main/escritos-mezcla');

const port = 9300 + Math.floor(Math.random() * 600);
const base = `http://127.0.0.1:${port}`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let fallos = 0;
const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) fallos++; };

async function http(method, url, body) {
  const r = await fetch(url, { method, headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  if (!r.ok) throw new Error(`HTTP ${r.status}: ${t}`);
  return t ? JSON.parse(t) : null;
}

async function cuenta() {
  const su = await http('POST', `${base}/v1/accounts:signUp?key=k`, { email: `p${Date.now()}${Math.random()}@ronron.example.com`, password: 'secreto1' });
  return { uid: su.localId, token: su.idToken };
}
function dispositivo(c, nombre, urlBase = base) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `esc-${nombre}-`));
  const net = {
    uid: () => c.uid,
    db: async (method, p, body, query) => http(method, `${c.urlBase || urlBase}${p}.json?auth=${c.token}${query ? `&${query}` : ''}`, body),
  };
  return { dir, e: crear({ dir, dev: nombre, net }) };
}

(async () => {
  const srv = spawn('node', [path.join(__dirname, 'fakefirebase.js'), String(port)], { stdio: 'ignore' });
  await sleep(700);
  try {
    console.log('Mezcla (casos de herramientas/casos/mezcla.json, los mismos que el móvil)');
    {
      const casos = JSON.parse(fs.readFileSync(path.join(__dirname, 'casos', 'mezcla.json'), 'utf8'));
      for (const c of casos.texto) {
        const m = M.texto(c.base, c.nuestro, c.suyo, c.prefiere === 'nuestro', !!c.ambos);
        ok(m.texto === c.esperado && m.choques === c.choques, `texto: ${c.nombre}${m.texto === c.esperado ? '' : ` → ${JSON.stringify(m.texto)}`}`);
      }
      for (const c of casos.docs) {
        const m = M.docs(c.base, c.nuestro, c.suyo, c.prefiere === 'nuestro', palabras);
        const bien = M.canon(m.o) === M.canon(c.esperado) && m.choques === c.choques && (!c.chocan || [...m.chocan].sort().join() === [...c.chocan].sort().join());
        ok(bien, `documento: ${c.nombre}${bien ? '' : ` → ${M.canon(m.o)} (${m.choques})`}`);
      }
      const baseL = Array.from({ length: 3000 }, (_, i) => `Párrafo ${i + 1}.`).join('\n');
      const t0 = Date.now();
      const m = M.texto(baseL, baseL.replace('Párrafo 2.\n', 'Párrafo 2 (móvil).\n'), baseL.replace('Párrafo 2999.', 'Párrafo 2999 (PC).'), true);
      ok(m.choques === 0 && m.texto === baseL.replace('Párrafo 2.\n', 'Párrafo 2 (móvil).\n').replace('Párrafo 2999.', 'Párrafo 2999 (PC).') && Date.now() - t0 < 3000, 'una escena de 3.000 párrafos se junta sin choques y deprisa');
    }
    console.log('Núcleo');
    {
      const { e } = dispositivo({ uid: null }, 'solo');
      ok(e.palabras ? true : true, 'carga');
      const { palabras } = require('../escritorio/src/main/escritos');
      ok(palabras('# Capítulo uno\n\n*Hola* **mundo**, ¡qué tal-vez! No\'s') === 7, 'cuenta palabras como el móvil (7)');
      const o = e.nuevo('p', '', { titulo: 'La torre' });
      const c = e.nuevo('c', o.id, { titulo: 'Uno' });
      const s = e.nuevo('e', o.id, { cid: c.id, titulo: 'Llegada', texto: 'Aldara subió la torre. El Cuervo la esperaba.' });
      e.nuevo('f', o.id, { tipo: 'personaje', nombre: 'Aldara', alias: 'la hechicera, Ald' });
      e.nuevo('f', o.id, { tipo: 'personaje', nombre: 'El Cuervo' });
      e.nuevo('f', o.id, { tipo: 'lugar', nombre: 'Valle' });
      ok(e.palabrasObra(o.id) === 8, 'palabras de la obra');
      ok(JSON.stringify(e.fichasEn(o.id, e.get(s.id).texto).map((f) => f.nombre).sort()) === '["Aldara","El Cuervo"]', 'reconoce las fichas por nombre y alias');
      ok(e.busca(o.id, 'TORRE').filter((h) => h.doc.t === 'e').length === 1, 'busca sin mirar mayúsculas');
      ok(e.reemplaza(o.id, 'cuervo', 'Halcón') === 1 && e.get(s.id).texto.includes('El Halcón la esperaba'), 'reemplaza');
      e.escribe(s.id, `${e.get(s.id).texto} Y entonces ocurrió algo terrible.`);
      ok(e.escritas(e.hoy(), o.id) >= 5 && e.racha(o.id) === 1, 'cuenta lo escrito hoy y la racha');
      const c2 = e.nuevo('c', o.id, { titulo: 'Dos' });
      e.mueve(c2.id, null, c.id);
      ok(e.capitulos(o.id).map((x) => x.titulo).join() === 'Dos,Uno', 'reordena capítulos');
      ok(e.instantanea(s.id, 'primera', { fuerza: true }) && !e.instantanea(s.id, 'primera', { fuerza: true }), 'versiones sin repetir');
      const pal = e.palabrasObra(o.id);
      e.borra(o.id);
      ok(e.todos().filter((x) => x.t !== 'l').length === 0, 'borrar una obra la manda a la papelera con todo lo suyo');
      ok(e.papelera().map((x) => x.id).join() === o.id && e.escritas(e.hoy(), o.id) >= 5, 'en la papelera solo se ve la obra; las palabras del día se quedan');
      e.restaura(o.id);
      ok(e.palabrasObra(o.id) === pal && e.capitulos(o.id).length === 2 && e.fichas(o.id).length === 3 && !e.papelera().length, 'y vuelve con todo');
    }
    console.log('Disco');
    {
      const { dir, e } = dispositivo({ uid: null }, 'disco');
      const o = e.nuevo('p', '', { titulo: 'Persistente' });
      const c = e.nuevo('c', o.id, { titulo: 'Uno' });
      e.nuevo('e', o.id, { cid: c.id, texto: 'Texto que no se pierde' });
      const otra = crear({ dir, dev: 'disco', net: { uid: () => null } });
      ok(otra.obras()[0].titulo === 'Persistente' && otra.escenasDe(o.id)[0].texto === 'Texto que no se pierde', 'todo vuelve al reabrir');
    }
    console.log('Sin conexión');
    {
      const c = { ...(await cuenta()), urlBase: 'http://127.0.0.1:1' };
      const { e } = dispositivo(c, 'tren');
      const o = e.nuevo('p', '', { titulo: 'En el tren' });
      const ca = e.nuevo('c', o.id, { titulo: 'Uno' });
      const s = e.nuevo('e', o.id, { cid: ca.id, texto: 'Escrito sin cobertura' });
      let fallo = null;
      try { await e.sincroniza(); } catch (x) { fallo = x; }
      ok(!!fallo && e.pendientes().length === 3, 'sin red falla y todo queda pendiente');
      e.escribe(s.id, 'Escrito sin cobertura, y seguí escribiendo');
      c.urlBase = base;
      await e.sincroniza();
      ok(e.pendientes().length === 0, 'vuelve la red: sube todo');
      const raw = await http('GET', `${base}/users/${c.uid}/escritos/${s.id}.json?auth=${c.token}`);
      ok(raw.texto.endsWith('seguí escribiendo'), 'lo último también');
      e.parar();
    }
    console.log('Dos dispositivos');
    {
      const c = await cuenta();
      const a = dispositivo(c, 'pc').e; const b = dispositivo(c, 'tablet').e;
      const o = a.nuevo('p', '', { titulo: 'Compartida' });
      const ca = a.nuevo('c', o.id, { titulo: 'Uno' });
      const s = a.nuevo('e', o.id, { cid: ca.id, texto: 'Primera línea.' });
      await a.sincroniza(); await b.sincroniza();
      ok(b.escenasDe(o.id)[0].texto === 'Primera línea.', 'llega la obra entera al otro');
      b.escribe(s.id, 'Primera línea. Segunda línea.');
      await b.sincroniza(); await a.sincroniza();
      ok(a.get(s.id).texto === 'Primera línea. Segunda línea.' && !a.pendientes().length && !b.pendientes().length, 'lo que escribe el otro llega');
      a.borra(s.id);
      await a.sincroniza(); await b.sincroniza();
      ok(!b.get(s.id) && !b.escenasDe(o.id).length, 'el borrado se propaga');
      a.parar(); b.parar();
    }
    console.log('Editar lo mismo a la vez');
    {
      const c = await cuenta();
      const a = dispositivo(c, 'pc').e; const b = dispositivo(c, 'tablet').e;
      const o = a.nuevo('p', '', { titulo: 'Conflicto' });
      const ca = a.nuevo('c', o.id, { titulo: 'Uno' });
      const s = a.nuevo('e', o.id, { cid: ca.id, texto: 'Base.' });
      await a.sincroniza(); await b.sincroniza();
      a.escribe(s.id, 'Base. Lo que escribí en el PC.');
      await sleep(5);
      b.escribe(s.id, 'Base. Lo que escribí en la tablet.');
      await a.sincroniza(); await b.sincroniza(); await a.sincroniza();
      ok(b.get(s.id).texto === 'Base. Lo que escribí en la tablet.' && a.get(s.id).texto === b.get(s.id).texto, 'gana el más nuevo y los dos acaban igual');
      const bor = b.borradores(s.id);
      ok(bor.length === 1 && bor[0].texto === 'Base. Lo que escribí en el PC.' && bor[0].conflicto === 1, 'el mismo párrafo cambiado en los dos: el texto del otro se guarda como borrador');
      ok(a.borradores(s.id).length === 1 && !a.pendientes().length && !b.pendientes().length, 'el borrador llega al otro, una sola vez');
      a.parar(); b.parar();
    }
    console.log('Párrafos y campos distintos a la vez');
    {
      const c = await cuenta();
      const a = dispositivo(c, 'pc').e; const b = dispositivo(c, 'tablet').e;
      const o = a.nuevo('p', '', { titulo: 'Juntar' });
      const ca = a.nuevo('c', o.id, { titulo: 'Uno' });
      const s = a.nuevo('e', o.id, { cid: ca.id, titulo: 'Escena', texto: 'Primer párrafo.\n\nSegundo párrafo.\n\nTercero.' });
      await a.sincroniza(); await b.sincroniza();
      a.escribe(s.id, 'Primer párrafo, en el PC.\n\nSegundo párrafo.\n\nTercero.');
      a.edita(s.id, { titulo: 'Título del PC' });
      await sleep(5);
      b.escribe(s.id, 'Primer párrafo.\n\nSegundo párrafo.\n\nTercero, en la tablet.\n\nY uno nuevo.');
      b.edita(s.id, { resumen: 'Resumen de la tablet' });
      await a.sincroniza(); await b.sincroniza(); await a.sincroniza();
      const esperado = 'Primer párrafo, en el PC.\n\nSegundo párrafo.\n\nTercero, en la tablet.\n\nY uno nuevo.';
      ok(a.get(s.id).texto === esperado && b.get(s.id).texto === esperado, 'se juntan los dos textos, igual en los dos');
      ok(a.get(s.id).titulo === 'Título del PC' && b.get(s.id).resumen === 'Resumen de la tablet' && b.get(s.id).titulo === 'Título del PC' && a.get(s.id).resumen === 'Resumen de la tablet', 'y los campos de cada uno');
      ok(a.get(s.id).palabras === palabras(esperado) && !a.borradores(s.id).length && !b.borradores(s.id).length, 'sin choques no hay borradores');
      ok(!a.pendientes().length && !b.pendientes().length, 'nada pendiente');
      a.parar(); b.parar();
    }
    console.log('Una subida que no vio lo del otro');
    {
      const c = await cuenta();
      const a = dispositivo(c, 'pc').e; const b = dispositivo(c, 'tablet').e;
      const o = a.nuevo('p', '', { titulo: 'Carrera' });
      const ca = a.nuevo('c', o.id, { titulo: 'Uno' });
      const s = a.nuevo('e', o.id, { cid: ca.id, texto: 'Uno.\n\nDos.' });
      await a.sincroniza(); await b.sincroniza();
      a.escribe(s.id, 'Uno (PC).\n\nDos.');
      await a.sincroniza();
      b.escribe(s.id, 'Uno.\n\nDos (tablet).');
      const d = b.get(s.id);
      await http('PATCH', `${base}/users/${c.uid}.json?auth=${c.token}`, { [`escritos/${s.id}`]: d, [`escritosIdx/${s.id}`]: d.u });
      await a.sincroniza();
      ok(a.get(s.id).texto === 'Uno (PC).\n\nDos (tablet).', 'no pisa lo del otro: lo junta');
      await b.sincroniza(); await a.sincroniza();
      ok(b.get(s.id).texto === 'Uno (PC).\n\nDos (tablet).' && !a.borradores(s.id).length && !a.pendientes().length && !b.pendientes().length, 'y los dos acaban igual');
      a.parar(); b.parar();
    }
    console.log('Papelera');
    {
      const c = await cuenta();
      const a = dispositivo(c, 'pc').e; const b = dispositivo(c, 'tablet').e;
      const o = a.nuevo('p', '', { titulo: 'Papelera' });
      const ca = a.nuevo('c', o.id, { titulo: 'Uno' });
      const s1 = a.nuevo('e', o.id, { cid: ca.id, texto: 'La primera escena.' });
      const s2 = a.nuevo('e', o.id, { cid: ca.id, texto: 'La segunda escena.' });
      const n = a.nuevo('n', o.id, { titulo: 'Idea', texto: 'Algo' });
      await a.sincroniza(); await b.sincroniza();
      a.borra(s1.id); a.borra(ca.id); a.borra(n.id);
      await a.sincroniza(); await b.sincroniza();
      ok(!b.escenasDe(o.id).length && !b.capitulos(o.id).length && !b.notas(o.id).length, 'se borra en el otro');
      ok(b.papelera().map((x) => x.id).sort().join() === [n.id, ca.id, s1.id].sort().join() && b.conElBorrado(ca.id).map((x) => x.id).join() === s2.id, 'y está en su papelera (el capítulo con su escena dentro)');
      b.restaura(ca.id);
      await b.sincroniza(); await a.sincroniza();
      ok(a.escenasDe(o.id).map((x) => x.id).join() === s2.id && a.get(s2.id).texto === 'La segunda escena.', 'se recupera desde el otro y vuelve con lo suyo');
      a.restaura(s1.id);
      await a.sincroniza(); await b.sincroniza();
      ok(b.escenasDe(o.id).length === 2, 'la escena que se borró sola también');
      b.vaciaPapelera();
      await b.sincroniza(); await a.sincroniza();
      a.restaura(n.id);
      const raw = await http('GET', `${base}/users/${c.uid}/escritos/${n.id}.json?auth=${c.token}`);
      ok(!a.papelera().length && !b.papelera().length && !a.get(n.id) && lapida(raw), 'vaciar la papelera la vacía en todos lados (queda solo la marca)');
      a.borra(s1.id); a.borra(ca.id);
      a.restaura(s1.id);
      ok(a.escenasDe(o.id).length === 2 && !a.papelera().length, 'recuperar una escena de un capítulo borrado trae el capítulo');
      a.borra(s1.id);
      a.purga(null, Date.now() + 1);
      ok(!a.papelera().length, 'lo viejo se vacía solo');
      a.parar(); b.parar();
    }
    console.log('Borrado en un sitio y escrito en otro');
    {
      const c = await cuenta();
      const a = dispositivo(c, 'pc').e; const b = dispositivo(c, 'tablet').e;
      const o = a.nuevo('p', '', { titulo: 'Cruce' });
      const ca = a.nuevo('c', o.id, { titulo: 'Uno' });
      const s = a.nuevo('e', o.id, { cid: ca.id, texto: 'Antes.' });
      await a.sincroniza(); await b.sincroniza();
      a.borra(s.id);
      await sleep(5);
      b.escribe(s.id, 'Antes. Y lo que escribí sin saberlo.');
      await a.sincroniza(); await b.sincroniza(); await a.sincroniza();
      const p = b.papelera().find((x) => x.id === s.id);
      ok(!b.get(s.id) && p && p.texto === 'Antes. Y lo que escribí sin saberlo.', 'queda en la papelera con lo último escrito');
      a.restaura(s.id);
      await a.sincroniza(); await b.sincroniza();
      ok(b.get(s.id) && b.get(s.id).texto === 'Antes. Y lo que escribí sin saberlo.', 'y se recupera');
      a.parar(); b.parar();
    }
    console.log('Seguir escribiendo en los dos');
    {
      const c = await cuenta();
      const a = dispositivo(c, 'pc').e; const b = dispositivo(c, 'tablet').e;
      const o = a.nuevo('p', '', { titulo: 'Seguido' });
      const ca = a.nuevo('c', o.id, { titulo: 'Uno' });
      const s = a.nuevo('e', o.id, { cid: ca.id, texto: 'Una' });
      await a.sincroniza(); await b.sincroniza();
      let txt = 'Una';
      for (let i = 0; i < 6; i++) { txt += ' palabra'; a.escribe(s.id, txt); await a.sincroniza(); await b.sincroniza(); }
      b.escribe(s.id, `${txt}\n\nDesde la tablet.`);
      a.escribe(s.id, `${txt} final`);
      await b.sincroniza(); await a.sincroniza(); await b.sincroniza();
      ok(a.get(s.id).texto === `${txt} final\n\nDesde la tablet.` && b.get(s.id).texto === a.get(s.id).texto && !a.borradores(s.id).length, 'uno sigue el párrafo y el otro añade otro: se juntan (aunque guarden en el mismo milisegundo)');
      a.parar(); b.parar();
    }
    console.log('Palabras del día');
    {
      const c = await cuenta();
      const a = dispositivo(c, 'pc').e; const b = dispositivo(c, 'tablet').e;
      const o = a.nuevo('p', '', { titulo: 'Palabras' });
      await a.sincroniza(); await b.sincroniza();
      a.registra(o.id, 300); b.registra(o.id, 450);
      await a.sincroniza(); await b.sincroniza(); await a.sincroniza(); await b.sincroniza();
      ok(a.escritas(a.hoy(), o.id) === 750 && b.escritas(b.hoy(), o.id) === 750, 'se suman las de todos los dispositivos');
      a.registra(o.id, 50);
      await a.sincroniza(); await b.sincroniza();
      ok(b.escritas(b.hoy(), o.id) === 800, 'más palabras no pisan las del otro');
      a.parar(); b.parar();
    }
    console.log('Partes de verdad (las antiguas, de texto, se pasan solas)');
    {
      const c = await cuenta();
      const A = dispositivo(c, 'pa').e; const B = dispositivo(c, 'pb').e;
      const o = A.nuevo('p', '', { titulo: 'Con partes' });
      const c0 = A.nuevo('c', o.id, { titulo: 'Prólogo' });
      const c1 = A.nuevo('c', o.id, { titulo: 'Uno', parte: 'Parte I' });
      const c2 = A.nuevo('c', o.id, { titulo: 'Dos', parte: 'Parte I' });
      const c3 = A.nuevo('c', o.id, { titulo: 'Tres', parte: 'Parte II' });
      for (const x of [c0, c1, c2, c3]) A.nuevo('e', o.id, { cid: x.id, titulo: 'E', texto: `Texto de ${x.titulo}.` });
      await A.sincroniza(); await B.sincroniza();
      // Los dos dispositivos las pasan a la vez, sin verse
      ok(A.migraPartes(o.id) === 3 && B.migraPartes(o.id) === 3, 'cada dispositivo pasa los tres capítulos con parte');
      await A.sincroniza(); await B.sincroniza(); await A.sincroniza();
      ok(A.partes(o.id).length === 2 && B.partes(o.id).length === 2, 'y salen dos partes, no cuatro (mismo id en los dos)');
      ok(A.partes(o.id).map((x) => x.titulo).join() === 'Parte I,Parte II', 'con su nombre');
      ok(A.capitulos(o.id).map((x) => x.titulo).join() === 'Prólogo,Uno,Dos,Tres' && B.capitulos(o.id).map((x) => x.titulo).join() === 'Prólogo,Uno,Dos,Tres', 'el libro sigue en el mismo orden en los dos');
      ok(A.get(c1.id).aid === A.partes(o.id)[0].id && !A.get(c1.id).parte, 'el capítulo queda dentro de su parte');
      ok(A.migraPartes(o.id) === 0, 'una segunda vez no hace nada');
      // Un capítulo nuevo dentro de la parte II va al final de esa parte; uno suelto, al final del manuscrito
      const p2 = A.partes(o.id)[1];
      const c4 = A.nuevo('c', o.id, { titulo: 'Cuatro', aid: p2.id });
      const c5 = A.nuevo('c', o.id, { titulo: 'Epílogo' });
      ok(A.capitulos(o.id).map((x) => x.titulo).join() === 'Prólogo,Uno,Dos,Tres,Cuatro,Epílogo', 'capítulos nuevos: al final de su parte, o sueltos al final');
      A.mueveCapitulo(c4.id, A.partes(o.id)[0].id, null, null);
      ok(A.capitulos(o.id).map((x) => x.titulo).join() === 'Prólogo,Uno,Dos,Cuatro,Tres,Epílogo', 'mover un capítulo a otra parte');
      // Exportar: los títulos de parte salen de las partes
      const X = require('../escritorio/src/main/escritos-export');
      const bl = X.manuscrito(A, o.id);
      ok(bl.filter((b) => b.tipo === 'parte').map((b) => b.texto).join() === 'Parte I,Parte II', 'al exportar salen «Parte I» y «Parte II»');
      // Borrar una parte se lleva sus capítulos y sus escenas; recuperarla los devuelve
      A.borra(p2.id);
      ok(!A.get(c3.id) && A.escenas(c3.id).length === 0 && A.capitulos(o.id).length === 5, 'borrar la parte II se lleva su capítulo');
      A.restaura(p2.id);
      ok(A.get(c3.id) && A.escenas(c3.id).length === 1 && A.capitulos(o.id).length === 6, 'y recuperarla lo devuelve con su escena');
      // Restaurar un capítulo de una parte borrada devuelve también la parte
      A.borra(p2.id); A.restaura(c3.id);
      ok(A.get(p2.id) && A.get(c3.id), 'recuperar un capítulo trae también su parte');
      await A.sincroniza(); await B.sincroniza();
      ok(B.capitulos(o.id).map((x) => x.titulo).join() === A.capitulos(o.id).map((x) => x.titulo).join(), 'el otro dispositivo lo ve igual');
      // Fichas y sus uniones del árbol: borrar la ficha se lleva sus uniones
      const f1 = A.nuevo('f', o.id, { nombre: 'Ana', tipo: 'personaje' }); const f2 = A.nuevo('f', o.id, { nombre: 'Juan', tipo: 'personaje' });
      const v = A.nuevo('v', o.id, { a: f1.id, b: f2.id, titulo: 'Enemigos', texto: 'Desde la guerra.' });
      A.borra(f2.id);
      ok(!A.get(v.id), 'borrar una ficha se lleva sus uniones');
      A.restaura(v.id);
      ok(A.get(v.id) && A.get(f2.id), 'y recuperar la unión trae la ficha');
      A.parar(); B.parar();
    }
    console.log('Otra cuenta');
    {
      const c1 = await cuenta(); const c2 = await cuenta();
      const d = dispositivo(c1, 'pc');
      const o = d.e.nuevo('p', '', { titulo: 'Mía' });
      d.e.cuenta(c1.uid); await d.e.sincroniza();
      ok(d.e.pendientes().length === 0, 'subida a la primera cuenta');
      // Misma carpeta, otra cuenta: lo escrito aquí se sube también a la nueva
      const net2 = { uid: () => c2.uid, db: async (m, p, b, q) => http(m, `${base}${p}.json?auth=${c2.token}${q ? `&${q}` : ''}`, b) };
      const e2 = crear({ dir: d.dir, dev: 'pc', net: net2 });
      e2.cuenta(c2.uid);
      ok(e2.pendientes().length >= 1, 'al cambiar de cuenta todo vuelve a estar pendiente');
      await e2.sincroniza();
      const sub = await http('GET', `${base}/users/${c2.uid}/escritos/${o.id}.json?auth=${c2.token}`);
      ok(sub && sub.titulo === 'Mía', 'y llega a la cuenta nueva');
      d.e.parar(); e2.parar();
    }
  } catch (e) {
    console.error(e);
    fallos++;
  }
  srv.kill();
  console.log(fallos ? `FALLAN ${fallos}` : 'Todo bien');
  process.exit(fallos ? 1 : 0);
})();
