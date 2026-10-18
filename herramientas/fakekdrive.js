'use strict';
// Un kDrive de mentira para las pruebas: imita las llamadas de la API de Infomaniak que usa Atrio
// (las mismas que el servidor MCP oficial de kDrive): listar, crear carpeta, subir, enlace, bajar y borrar.
//   node herramientas/fakekdrive.js [puerto]      → kDrive 777, token «tok»
const http = require('http');

function create({ drive = '777', token = 'tok' } = {}) {
  const files = new Map([[1, { id: 1, name: 'Mi carpeta', type: 'dir', parent: 0 }]]);
  let next = 100;
  const log = [];
  const ok = (res, data, extra = {}) => { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ result: 'success', data, ...extra })); };
  const err = (res, code, description) => { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ result: 'error', error: { code: String(code), description } })); };
  const body = (req) => new Promise((r) => { const b = []; req.on('data', (c) => b.push(c)); req.on('end', () => r(Buffer.concat(b))); });

  const server = http.createServer(async (req, res) => {
    const u = new URL(req.url, 'http://x');
    const raw = await body(req);
    log.push(`${req.method} ${u.pathname}`);
    if (req.headers.authorization !== `Bearer ${token}`) return err(res, 401, 'not_authorized');
    const m = u.pathname.match(/^\/([23])\/drive\/(\d+)(\/.*)$/);
    if (!m) return err(res, 404, 'not_found');
    if (m[2] !== drive) return err(res, 404, 'drive_not_found');
    const p = m[3];
    let x;
    if (req.method === 'GET' && (x = p.match(/^\/files\/(\d+)\/files$/))) {
      const kids = [...files.values()].filter((f) => f.parent === Number(x[1])).map(({ id, name, type }) => ({ id, name, type }));
      return ok(res, kids, { has_more: false });
    }
    if (req.method === 'POST' && (x = p.match(/^\/files\/(\d+)\/directory$/))) {
      const id = next++;
      files.set(id, { id, name: JSON.parse(raw.toString()).name, type: 'dir', parent: Number(x[1]) });
      return ok(res, { id, name: files.get(id).name, type: 'dir' });
    }
    if (req.method === 'POST' && p === '/upload') {
      const size = Number(u.searchParams.get('total_size'));
      if (size !== raw.length) return err(res, 400, 'size_mismatch');
      const id = next++;
      files.set(id, { id, name: u.searchParams.get('file_name'), type: 'file', parent: Number(u.searchParams.get('directory_id')), buf: raw });
      return ok(res, { id, name: files.get(id).name, type: 'file', size });
    }
    if (req.method === 'POST' && (x = p.match(/^\/files\/(\d+)\/link$/))) {
      if (!files.has(Number(x[1]))) return err(res, 404, 'file_not_found');
      return ok(res, { url: `https://kdrive.example/app/share/${drive}/${x[1]}` });
    }
    if (req.method === 'GET' && (x = p.match(/^\/files\/(\d+)\/download$/))) {
      const f = files.get(Number(x[1]));
      if (!f || !f.buf) return err(res, 404, 'file_not_found');
      res.writeHead(200, { 'Content-Type': 'application/octet-stream' });
      return res.end(f.buf);
    }
    if (req.method === 'DELETE' && (x = p.match(/^\/files\/(\d+)$/))) {
      files.delete(Number(x[1]));
      return ok(res, true);
    }
    return err(res, 404, 'not_found');
  });
  // Ruta completa de un archivo («Atrio/Capturas/Hades II/x.png») → archivo
  const pathOf = (f) => { const out = []; let c = f; while (c && c.id !== 1) { out.unshift(c.name); c = files.get(c.parent); } return out.join('/'); };
  const byPath = (pth) => [...files.values()].find((f) => pathOf(f) === pth) || null;
  return { server, files, log, pathOf, byPath };
}

module.exports = { create };

if (require.main === module) {
  const k = create();
  k.server.listen(Number(process.argv[2]) || 8787, '127.0.0.1', () => console.log('kDrive de prueba en', k.server.address().port));
}
