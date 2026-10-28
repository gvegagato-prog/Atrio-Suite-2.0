#!/usr/bin/env node
'use strict';
// Notas de Rotten Tomatoes en el PC (escritorio/src/main/rt.js): elegir la ficha que toca en los resultados y leer las dos
// notas de su página, con trozos reales de la web (herramientas/casos/rt-*.html, recortados el 28/10/2026), y el recorrido
// completo contra un servidor de mentira (con la caché de una semana y sin conexión). Uso: node herramientas/prueba-rt.js
const fs = require('fs');
const os = require('os');
const path = require('path');
const http = require('http');
process.env.ATRIO_HOME = fs.mkdtempSync(path.join(os.tmpdir(), 'rt-'));
const RT = require('../escritorio/src/main/rt.js');

let fallos = 0;
const ok = (c, m) => { console.log(`${c ? '  ✔' : '  ✘'} ${m}`); if (!c) fallos++; };
const busqueda = fs.readFileSync(path.join(__dirname, 'casos', 'rt-busqueda.html'), 'utf8');
const pagina = fs.readFileSync(path.join(__dirname, 'casos', 'rt-pagina.html'), 'utf8');

console.log('Elegir la ficha en los resultados');
ok(RT.buscaUrl(busqueda, 'Inception', '2010', false) === 'https://www.rottentomatoes.com/m/inception', 'Inception (2010) → /m/inception');
ok(RT.buscaUrl(busqueda, 'Inception', '2011', false) === 'https://www.rottentomatoes.com/m/inception', 'con un año de diferencia también');
ok(RT.buscaUrl(busqueda, 'Inception', '1999', false) === 'https://www.rottentomatoes.com/m/inception', 'con otro año, por el nombre exacto (como en el móvil)');
ok(RT.buscaUrl(busqueda, 'Inception: The Cobol Job', '', false) === 'https://www.rottentomatoes.com/m/inception_the_cobol_job', 'un título con dos puntos');
ok(RT.buscaUrl(busqueda, 'Nada que ver', '2010', false) === null, 'sin coincidencia → null');
ok(RT.buscaUrl(busqueda, "Rock 'N' Roll Inventions", '', true) !== null, 'las series salen de su sección (y «&#39;» se entiende)');
ok(RT.buscaUrl(busqueda, 'Inception', '2010', true) === null, 'una película no se confunde con una serie');
ok(RT.buscaUrl('<html>nada</html>', 'Inception', '2010', false) === null, 'sin resultados → null');
ok(RT.norm('Amélie & Co.') === 'amelie and co', 'normaliza tildes y «&»');

console.log('Leer las notas de la página');
const n = RT.notas(pagina, 'https://www.rottentomatoes.com/m/inception');
ok(n && n.tm === 86 && n.pc === 91 && n.cf === true, `Inception: crítica ${n && n.tm} %, público ${n && n.pc} % (certificada)`);
ok(RT.notas('<html></html>', 'x') === null, 'sin JSON → null');
ok(RT.notas('"criticsScore":{"score":"40"}', 'x').pc === -1, 'si falta una nota, −1');

console.log('El recorrido completo (servidor de mentira)');
(async () => {
  let peticiones = 0;
  const srv = http.createServer((q, s) => {
    peticiones++;
    if (q.url.startsWith('/search')) { s.end(busqueda); return; }
    if (q.url === '/m/inception') { s.end(pagina); return; }
    s.statusCode = 404; s.end('no');
  });
  await new Promise((r) => srv.listen(0, '127.0.0.1', r));
  process.env.ATRIO_RT_BASE = `http://127.0.0.1:${srv.address().port}`;
  const base = process.env.ATRIO_RT_BASE;
  const fetchImpl = async (url, o) => {
    const u = String(url);
    if (u.startsWith('https://api.themoviedb.org/3/movie/27205')) return { ok: true, json: async () => ({ title: 'Inception', release_date: '2010-07-15' }) };
    if (u.startsWith(base)) { const r = await fetch(u, o); return { ok: r.ok, status: r.status, text: () => r.text(), json: () => r.json() }; }
    throw new Error('sin conexión');
  };
  const rt = RT.crear({ fetchImpl });
  const r1 = await rt.puntuacion({ tv: false, id: 27205, nombre: 'Origen', claveTmdb: 'clave' });
  ok(r1 && r1.tm === 86 && r1.pc === 91 && /\/m\/inception$/.test(r1.u), `«Origen» (TMDB 27205 → «Inception»): ${r1 && `${r1.tm} % / ${r1.pc} %`}`);
  const antes = peticiones;
  const r2 = await rt.puntuacion({ tv: false, id: 27205, nombre: 'Origen', claveTmdb: 'clave' });
  ok(r2 && r2.tm === 86 && peticiones === antes, 'la segunda vez sale de la caché (sin más peticiones)');
  const rt2 = RT.crear({ fetchImpl: async () => { throw new Error('sin conexión'); } });
  const r3 = await rt2.puntuacion({ tv: false, id: 27205, nombre: 'Origen', claveTmdb: 'clave' });
  ok(r3 && r3.tm === 86, 'y se guarda en el disco: otra vez abierta y sin internet, se ve igual');
  const r4 = await rt2.puntuacion({ tv: false, id: 1, nombre: 'Una cosa inventada', claveTmdb: '' });
  ok(r4 === null, 'sin conexión y sin nada guardado → null (la casilla dice «sin notas»)');
  srv.close();
  fs.rmSync(process.env.ATRIO_HOME, { recursive: true, force: true });
  console.log(fallos ? `\n${fallos} fallos` : '\nTodo bien');
  process.exit(fallos ? 1 : 0);
})();
