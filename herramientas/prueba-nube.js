'use strict';
// Prueba de la nube del escritorio (escritorio/src/main/nube.js) contra un kDrive de mentira:
// conectar, subir una captura (con su enlace y su registro en la cuenta) y bajar una foto de portada del móvil.
//   node herramientas/prueba-nube.js
const fs = require('fs');
const os = require('os');
const path = require('path');
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
  const data = { nube: [] };
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
  ok(r.drive === '777' && /Conectado/.test(r.msg), `conectar con la dirección del navegador (${r.drive})`);
  conf.nube = { token: 'tok', drive: r.drive, shots: true };
  ok(nube.on(), 'nube conectada');

  // Una captura: a Atrio/Capturas/<juego>/ con su enlace y su registro
  const buf = Buffer.from('PNG de prueba '.repeat(500));
  const it = await nube.shot({ id: 'abc', name: 'Hades II 2026-10-18 13.47.00.png', buf, game: 'Hades II', sg: 1145360, t: 1, w: 3840, h: 2160, mime: 'image/png', pc: 'pc' });
  const f = k.byPath('Atrio/Capturas/Hades II/Hades II 2026-10-18 13.47.00.png');
  ok(f && f.buf.equals(buf), 'captura subida a Atrio/Capturas/Hades II, tal cual');
  ok(it && it.k === 'shot:abc' && it.f === f.id && /share/.test(it.l) && it.m.sg === 1145360 && it.id === nube.nubeId('shot:abc'), 'registro de la captura con su enlace y sus datos');
  ok(edits.length === 1 && edits[0].op === 'nubeAdd', 'el registro va a la cuenta (nubeAdd)');
  // Una segunda no vuelve a crear las carpetas
  const dirsAntes = [...k.files.values()].filter((x) => x.type === 'dir').length;
  await nube.shot({ id: 'abd', name: 'otra.png', buf, game: 'Hades II', t: 2, w: 1, h: 1, mime: 'image/png', pc: 'pc' });
  ok([...k.files.values()].filter((x) => x.type === 'dir').length === dirsAntes, 'las carpetas se reutilizan');
  // Sin subir capturas (Ajustes → No)
  conf.nube.shots = false;
  ok((await nube.shot({ id: 'no', name: 'no.png', buf, game: 'X' })) === null, 'con «Subir las capturas: No» no se sube');
  conf.nube.shots = true;

  // Una foto de portada que elegiste en el móvil: está en tu nube y falta en este PC
  const foto = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4]);
  const port = k.files.get(Number([...k.files.values()].find((x) => x.name === 'Atrio').id));
  ok(!!port, 'carpeta Atrio');
  k.files.set(9001, { id: 9001, name: 'foto_1700000000000.jpg', type: 'file', parent: 1, buf: foto });
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
