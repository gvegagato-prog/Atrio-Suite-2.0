'use strict';
// Prueba de la nube del escritorio (escritorio/src/main/nube.js) contra un kDrive de mentira:
// conectar, subir una captura (con su enlace y su registro en la cuenta) y bajar una foto de portada del móvil.
//   node herramientas/prueba-nube.js
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const { create } = require('./fakekdrive');
const nube = require('../escritorio/src/main/nube');

let fallos = 0;
const ok = (c, t) => { console.log(`${c ? '  ✔' : '  ✘'} ${t}`); if (!c) fallos++; };

(async () => {
  const k = create();
  await new Promise((r) => k.server.listen(0, '127.0.0.1', r));
  nube.setApi(`http://127.0.0.1:${k.server.address().port}`);
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'atrio-nube-'));
  const conf = { nube: { token: '', drive: '', shots: true } };
  // La ficha del juego se llama distinto que en el PC: las capturas van a la carpeta de la ficha
  const data = { nube: [], plays: [{ id: 1145360, name: 'Hades II', status: 1 }] };
  const edits = [];
  let fotosAvisadas = 0;
  nube.start({
    config: () => conf, data: () => data, log: () => {}, fotosDir: path.join(tmp, 'fotos'),
    edit: async (op) => { edits.push(op); if (op.op === 'nubeAdd') data.nube.push(...op.items); },
    onFotos: () => { fotosAvisadas++; },
  });

  // El mismo id que calcula el móvil (Nube.kt → nubeId), comprobado también en NubeTest
  ok(nube.nubeId('libro:123') === 2498222623274449, `id igual que en el móvil (${nube.nubeId('libro:123')})`);

  // Conectar: token malo, kDrive que no es, y bien (con la dirección entera del navegador)
  let e1 = ''; try { await nube.test('malo', '777'); } catch (e) { e1 = e.message; }
  ok(/token/.test(e1), `token malo: «${e1}»`);
  let e2 = ''; try { await nube.test('tok', '999'); } catch (e) { e2 = e.message; }
  ok(/kDrive/.test(e2), `kDrive equivocado: «${e2}»`);
  const r = await nube.test('tok', 'https://ksuite.infomaniak.com/kdrive/app/drive/777/files');
  ok(r.drive === '777' && /Mi carpeta/.test(r.msg), `conectar con la dirección del navegador (${r.drive}): «${r.msg}»`);
  conf.nube = { token: 'tok', drive: r.drive, shots: true };
  ok(nube.on(), 'nube conectada');

  // Una captura: a Atrio/Capturas/<juego>/ con su enlace y su registro
  const buf = Buffer.from('PNG de prueba '.repeat(500));
  const it = await nube.shot({ id: 'abc', name: 'Hades II 2026-10-18 13.47.00.png', buf, game: 'Hades II: Steam', sg: 1145360, t: 1, w: 3840, h: 2160, mime: 'image/png', pc: 'pc' });
  const f = k.byPath('Atrio/Videojuegos/Hades II/Capturas/Hades II 2026-10-18 13.47.00.png');
  ok(f && f.buf.equals(buf), 'captura en Mi carpeta/Atrio/Videojuegos/Hades II/Capturas, tal cual');
  ok(![...k.files.values()].some((x) => x.parent === 1 && x.name === 'Atrio'), 'nada en la raíz del kDrive');
  ok(it && it.k === 'shot:abc' && it.f === f.id && /share/.test(it.l) && it.m.sg === 1145360 && it.id === nube.nubeId('shot:abc'), 'registro de la captura con su enlace y sus datos');
  ok(edits.length === 1 && edits[0].op === 'nubeAdd', 'el registro va a la cuenta (nubeAdd)');
  // Una segunda no vuelve a crear las carpetas
  const dirsAntes = [...k.files.values()].filter((x) => x.type === 'dir').length;
  await nube.shot({ id: 'abd', name: 'otra.png', buf, game: 'Hades II', sg: 1145360, t: 2, w: 1, h: 1, mime: 'image/png', pc: 'pc' });
  ok([...k.files.values()].filter((x) => x.type === 'dir').length === dirsAntes, 'las carpetas se reutilizan');
  // Una captura grande (más de 5 MB): a trozos, y llega entera
  const grande = crypto.randomBytes(12 * 1024 * 1024 + 123);
  const ig = await nube.shot({ id: 'big', name: 'grande.png', buf: grande, game: 'Balatro', t: 3, w: 7680, h: 4320, mime: 'image/png', pc: 'pc' });
  const fg = k.byPath('Atrio/Videojuegos/Balatro/Capturas/grande.png');
  ok(fg && fg.buf.equals(grande) && ig.f === fg.id && k.log.some((l) => l.endsWith('/finish')), 'captura de 12 MB subida a trozos, entera');
  // El enlace sirve para bajarla sin cuenta (lo que usa la app de tu pareja)
  const uuid = it.l.match(/\/app\/share\/777\/([a-z0-9-]+)/)[1];
  const pub = await fetch(`http://127.0.0.1:${k.server.address().port}/2/app/777/share/${uuid}/files/${it.f}/download`);
  ok(pub.ok && Buffer.from(await pub.arrayBuffer()).equals(buf), 'el enlace baja el archivo sin cuenta');
  // Sin subir capturas (Ajustes → No)
  conf.nube.shots = false;
  ok((await nube.shot({ id: 'no', name: 'no.png', buf, game: 'X' })) === null, 'con «Subir las capturas: No» no se sube');
  conf.nube.shots = true;

  // Una imagen del lanzador puesta desde un archivo: a la carpeta de su ficha, como «Portada.png»
  const img = path.join(tmp, 'mi-portada.png');
  fs.writeFileSync(img, Buffer.from('imagen'));
  const pa = await nube.pcArt({ key: 'steam:1145360', name: 'Hades II: Steam', sg: 1145360, type: 'cover', file: img });
  ok(pa && k.byPath('Atrio/Videojuegos/Hades II/Portadas/Portada.png') && pa.k === 'pcart:steam:1145360:cover', 'imagen del lanzador en Atrio/Videojuegos/Hades II/Portadas/Portada.png');
  await nube.pcArt({ key: 'steam:1145360', name: 'Hades II', sg: 1145360, type: 'cover', file: img });
  ok([...k.files.values()].filter((x) => x.name === 'Portada.png').length === 1, 'cambiarla otra vez no la duplica (versión nueva)');

  // Una foto de portada que elegiste en el móvil: está en tu nube y falta en este PC
  const foto = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4]);
  const atrio = [...k.files.values()].find((x) => x.name === 'Atrio' && x.parent === 2);
  ok(!!atrio, 'carpeta Atrio dentro de Mi carpeta');
  k.files.set(9001, { id: 9001, name: 'foto_1700000000000.jpg', type: 'file', parent: atrio.id, buf: foto });
  data.nube.push({ id: nube.nubeId('foto:foto_1700000000000.jpg'), k: 'foto:foto_1700000000000.jpg', f: 9001, n: 'foto_1700000000000.jpg', s: foto.length });
  const n = await nube.fotos();
  const bajada = path.join(tmp, 'fotos', 'foto_1700000000000.jpg');
  ok(n === 1 && fs.existsSync(bajada) && fs.readFileSync(bajada).equals(foto), 'foto de portada bajada a la carpeta de fotos');
  ok(fotosAvisadas === 1, 'la revista se entera de la foto nueva');
  ok((await nube.fotos()) === 0, 'la segunda vez no la vuelve a bajar');

  k.server.close();
  fs.rmSync(tmp, { recursive: true, force: true });
  console.log(fallos ? `${fallos} fallos` : 'Todo bien');
  process.exit(fallos ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
