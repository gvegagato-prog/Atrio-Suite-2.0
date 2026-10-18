'use strict';
// Un kDrive de mentira para las pruebas: imita las llamadas de la API de Infomaniak que usa Atrio (las mismas que
// la app oficial de kDrive para Android y su servidor MCP): listar, crear carpeta, subir (de una vez, con
// directory_path, o a trozos con una sesión), enlace público, bajar (con token o por el enlace) y borrar.
// Como el de verdad: en la raíz no se puede escribir; lo tuyo va en «Mi carpeta» (visibility is_private_space).
//   node herramientas/fakekdrive.js [puerto]      → kDrive 777, token «tok»
const http = require('http');
const crypto = require('crypto');

function create({ drive = '777', token = 'tok' } = {}) {
  const files = new Map([
    [1, { id: 1, name: '', type: 'dir', parent: 0, visibility: 'is_root' }],
    [2, { id: 2, name: 'Mi carpeta', type: 'dir', parent: 1, visibility: 'is_private_space' }],
    [3, { id: 3, name: 'Documentos comunes', type: 'dir', parent: 1, visibility: 'is_team_space' }],
  ]);
  let next = 100;
  const log = [];
  const sessions = new Map();
  const links = new Map(); // uuid -> id
  let base = '';
  const ok = (res, data, extra = {}) => { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ result: 'success', data, ...extra })); };
  const err = (res, code, description) => { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ result: 'error', error: { code: String(code), description } })); };
  const body = (req) => new Promise((r) => { const b = []; req.on('data', (c) => b.push(c)); req.on('end', () => r(Buffer.concat(b))); });
  const deny = (parent) => parent === 1; // la raíz: «You do not have the required permissions»

  // Las carpetas de «a/b/c» dentro de una (se crean las que falten)
  function ensure(parent, p) {
    let cur = parent;
    for (const name of String(p || '').split('/').filter(Boolean)) {
      const f = [...files.values()].find((x) => x.parent === cur && x.type === 'dir' && x.name === name);
      if (f) cur = f.id;
      else { const id = next++; files.set(id, { id, name, type: 'dir', parent: cur }); cur = id; }
    }
    return cur;
  }
  // Un archivo nuevo, o versión nueva del que ya había con ese nombre (conflict=version)
  function put(dir, name, buf, conflict) {
    const same = [...files.values()].find((x) => x.parent === dir && x.type === 'file' && x.name === name);
    if (same && conflict === 'version') { same.buf = buf; same.versions = (same.versions || 1) + 1; return same; }
    if (same && conflict !== 'rename') return null;
    const id = next++;
    files.set(id, { id, name: same ? `${name.replace(/(\.[^.]+)?$/, ' (1)$1')}` : name, type: 'file', parent: dir, buf });
    return files.get(id);
  }

  const server = http.createServer(async (req, res) => {
    const u = new URL(req.url, 'http://x');
    const raw = await body(req);
    log.push(`${req.method} ${u.pathname}`);
    // Descarga pública por enlace (sin cuenta)
    let x = u.pathname.match(/^\/2\/app\/(\d+)\/share\/([a-z0-9-]+)\/files\/(\d+)\/download$/);
    if (x) {
      const f = files.get(Number(x[3]));
      if (x[1] !== drive || links.get(x[2]) !== Number(x[3]) || !f) return err(res, 404, 'not_found');
      res.writeHead(200, { 'Content-Type': 'application/octet-stream' });
      return res.end(f.buf);
    }
    if (req.headers.authorization !== `Bearer ${token}`) return err(res, 401, 'not_authorized');
    const m = u.pathname.match(/^\/([23])\/drive\/(\d+)(\/.*)$/);
    if (!m) return err(res, 404, 'not_found');
    if (m[2] !== drive) return err(res, 404, 'drive_not_found');
    const p = m[3];
    if (req.method === 'GET' && (x = p.match(/^\/files\/(\d+)\/files$/))) {
      const kids = [...files.values()].filter((f) => f.parent === Number(x[1])).map(({ id, name, type, visibility }) => ({ id, name, type, visibility: visibility || '' }));
      return ok(res, kids, { has_more: false });
    }
    if (req.method === 'POST' && (x = p.match(/^\/files\/(\d+)\/directory$/))) {
      if (deny(Number(x[1]))) return err(res, 403, 'You do not have the required permissions');
      const id = next++;
      files.set(id, { id, name: JSON.parse(raw.toString()).name, type: 'dir', parent: Number(x[1]) });
      return ok(res, { id, name: files.get(id).name, type: 'dir' });
    }
    if (req.method === 'POST' && p === '/upload') {
      const size = Number(u.searchParams.get('total_size'));
      if (size !== raw.length) return err(res, 400, 'size_mismatch');
      const parent = Number(u.searchParams.get('directory_id'));
      if (deny(parent) && !u.searchParams.get('directory_path')) return err(res, 403, 'You do not have the required permissions');
      if (deny(parent)) return err(res, 403, 'You do not have the required permissions');
      const f = put(ensure(parent, u.searchParams.get('directory_path')), u.searchParams.get('file_name'), raw, u.searchParams.get('conflict'));
      if (!f) return err(res, 400, 'file_already_exists_error');
      return ok(res, { id: f.id, name: f.name, type: 'file', size });
    }
    if (req.method === 'POST' && p === '/upload/session/start') {
      const b = JSON.parse(raw.toString());
      if (deny(Number(b.directory_id))) return err(res, 403, 'You do not have the required permissions');
      const tok = crypto.randomBytes(8).toString('hex');
      sessions.set(tok, { ...b, chunks: new Map() });
      return ok(res, { token: tok, upload_url: base, directory_id: b.directory_id, directory_path: b.directory_path, file_name: b.file_name });
    }
    if (req.method === 'POST' && (x = p.match(/^\/upload\/session\/([a-f0-9]+)\/chunk$/))) {
      const s = sessions.get(x[1]);
      if (!s) return err(res, 404, 'session_not_found');
      if (Number(u.searchParams.get('chunk_size')) !== raw.length) return err(res, 400, 'chunk_size_mismatch');
      s.chunks.set(Number(u.searchParams.get('chunk_number')), raw);
      return ok(res, { number: Number(u.searchParams.get('chunk_number')), status: 'ok' });
    }
    if (req.method === 'POST' && (x = p.match(/^\/upload\/session\/([a-f0-9]+)\/finish$/))) {
      const s = sessions.get(x[1]);
      if (!s || s.chunks.size !== s.total_chunks) return err(res, 400, 'missing_chunks');
      const buf = Buffer.concat([...s.chunks.keys()].sort((a, b) => a - b).map((k) => s.chunks.get(k)));
      if (buf.length !== s.total_size) return err(res, 400, 'size_mismatch');
      sessions.delete(x[1]);
      const f = put(ensure(Number(s.directory_id), s.directory_path), s.file_name, buf, s.conflict);
      return ok(res, { token: x[1], file: { id: f.id, name: f.name, type: 'file', size: buf.length } });
    }
    if (req.method === 'POST' && (x = p.match(/^\/files\/(\d+)\/link$/))) {
      if (!files.has(Number(x[1]))) return err(res, 404, 'file_not_found');
      const uuid = crypto.randomUUID();
      links.set(uuid, Number(x[1]));
      return ok(res, { url: `https://kdrive.infomaniak.com/app/share/${drive}/${uuid}`, file_id: Number(x[1]), right: 'public' });
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
  server.on('listening', () => { base = `http://127.0.0.1:${server.address().port}`; });
  // Ruta de un archivo desde «Mi carpeta» («Atrio/Videojuegos/Hades II/Capturas/x.png») → archivo
  const pathOf = (f) => { const out = []; let c = f; while (c && c.id !== 2 && c.id !== 1) { out.unshift(c.name); c = files.get(c.parent); } return out.join('/'); };
  const byPath = (pth) => [...files.values()].find((f) => f.type === 'file' && pathOf(f) === pth) || null;
  return { server, files, log, pathOf, byPath };
}

module.exports = { create };

if (require.main === module) {
  const k = create();
  k.server.listen(Number(process.argv[2]) || 8787, '127.0.0.1', () => console.log('kDrive de prueba en', k.server.address().port));
}
