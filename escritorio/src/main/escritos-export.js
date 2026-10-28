'use strict';
// Sacar la obra a archivos (Markdown, texto, Word y libro electrónico EPUB) e importar de vuelta; igual que EscribirExport.kt
// del móvil. Todo se hace aquí, sin enviar tu texto a ningún sitio.
const zlib = require('zlib');

// ---------------------------------------------------------------- Bloques del manuscrito
const B = { TITULO: 'titulo', PARTE: 'parte', CAPITULO: 'capitulo', ESCENA: 'escena', PARRAFO: 'parrafo', CITA: 'cita', SALTO: 'salto', H1: 'h1', H2: 'h2', H3: 'h3' };

function bloques(texto) {
  const out = []; let par = [];
  const cierra = () => { const t = par.join(' ').trim(); if (t) out.push({ tipo: B.PARRAFO, texto: t }); par = []; };
  for (const l of String(texto || '').replace(/\r\n/g, '\n').split('\n')) {
    const t = l.trim();
    if (!t) cierra();
    else if (t === '***' || t === '* * *' || t === '---') { cierra(); out.push({ tipo: B.SALTO, texto: '' }); }
    else if (t.startsWith('### ')) { cierra(); out.push({ tipo: B.H3, texto: t.slice(4) }); }
    else if (t.startsWith('## ')) { cierra(); out.push({ tipo: B.H2, texto: t.slice(3) }); }
    else if (t.startsWith('# ')) { cierra(); out.push({ tipo: B.H1, texto: t.slice(2) }); }
    else if (t.startsWith('> ')) { cierra(); out.push({ tipo: B.CITA, texto: t.slice(2) }); }
    else par.push(t);
  }
  cierra();
  return out;
}

// **negrita** y *cursiva* en trozos
function trozos(t) {
  const out = []; let neg = false; let cur = false; let b = '';
  const vuelca = () => { if (b) { out.push({ texto: b, neg, cur }); b = ''; } };
  for (let i = 0; i < t.length;) {
    if (t.startsWith('**', i)) { vuelca(); neg = !neg; i += 2; }
    else if (t[i] === '*') { vuelca(); cur = !cur; i++; }
    else if (t[i] === '\\' && i + 1 < t.length && '*#>'.includes(t[i + 1])) { b += t[i + 1]; i += 2; }
    else { b += t[i]; i++; }
  }
  vuelca();
  return out;
}
const plano = (t) => trozos(t).map((x) => x.texto).join('');

function manuscrito(e, pid, op = {}) {
  const o = { titulosDeCapitulo: true, titulosDeEscena: false, soloEstadoMinimo: 0, saltoEntreEscenas: true, ...op };
  const obra = e.get(pid);
  if (!obra) return [];
  const out = [{ tipo: B.TITULO, texto: obra.titulo || 'Sin título' }];
  if (obra.autor) out.push({ tipo: B.PARRAFO, texto: obra.autor });
  let parte = '';
  for (const c of e.capitulos(pid)) {
    const esc = e.escenas(c.id).filter((s) => (Number(s.estado) || 0) >= o.soloEstadoMinimo);
    if (!esc.length) continue;
    if (c.parte && c.parte !== parte) { parte = c.parte; out.push({ tipo: B.PARTE, texto: parte }); }
    if (o.titulosDeCapitulo) out.push({ tipo: B.CAPITULO, texto: c.titulo || '' });
    esc.forEach((s, i) => {
      if (i > 0 && o.saltoEntreEscenas) out.push({ tipo: B.SALTO, texto: '' });
      if (o.titulosDeEscena && s.titulo) out.push({ tipo: B.ESCENA, texto: s.titulo });
      out.push(...bloques(s.texto));
    });
  }
  return out;
}

function markdown(e, pid, op) {
  let sb = '';
  for (const b of manuscrito(e, pid, op)) {
    if (b.tipo === B.TITULO || b.tipo === B.PARTE) sb += `# ${b.texto}\n\n`;
    else if (b.tipo === B.CAPITULO || b.tipo === B.H1) sb += `## ${b.texto}\n\n`;
    else if (b.tipo === B.ESCENA || b.tipo === B.H2) sb += `### ${b.texto}\n\n`;
    else if (b.tipo === B.H3) sb += `#### ${b.texto}\n\n`;
    else if (b.tipo === B.CITA) sb += `> ${b.texto}\n\n`;
    else if (b.tipo === B.SALTO) sb += '* * *\n\n';
    else sb += `${b.texto}\n\n`;
  }
  return `${sb.trimEnd()}\n`;
}

function texto(e, pid, op) {
  let sb = '';
  for (const b of manuscrito(e, pid, op)) {
    if (b.tipo === B.TITULO || b.tipo === B.PARTE) sb += `${b.texto.toLocaleUpperCase('es')}\n\n`;
    else if (b.tipo === B.CAPITULO || b.tipo === B.ESCENA || b.tipo === B.H1 || b.tipo === B.H2 || b.tipo === B.H3) sb += `${b.texto}\n\n`;
    else if (b.tipo === B.CITA) sb += `    ${plano(b.texto)}\n\n`;
    else if (b.tipo === B.SALTO) sb += '        * * *\n\n';
    else sb += `${plano(b.texto)}\n\n`;
  }
  return `${sb.trimEnd()}\n`;
}

const xml = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// ---------------------------------------------------------------- Zip (escribir y leer)
const TABLA = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = (buf) => { let c = 0xffffffff; for (let i = 0; i < buf.length; i++) c = TABLA[(c ^ buf[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };

// entradas: [{ nombre, datos: Buffer, guardado?: true }]
function zip(entradas) {
  const partes = []; const central = []; let pos = 0;
  for (const { nombre, datos, guardado } of entradas) {
    const n = Buffer.from(nombre, 'utf8');
    const crc = crc32(datos);
    const comp = guardado ? datos : zlib.deflateRawSync(datos);
    const metodo = guardado ? 0 : 8;
    const loc = Buffer.alloc(30);
    loc.writeUInt32LE(0x04034b50, 0); loc.writeUInt16LE(20, 4); loc.writeUInt16LE(0x0800, 6); loc.writeUInt16LE(metodo, 8);
    loc.writeUInt16LE(0, 10); loc.writeUInt16LE(0x21, 12); loc.writeUInt32LE(crc, 14); loc.writeUInt32LE(comp.length, 18); loc.writeUInt32LE(datos.length, 22);
    loc.writeUInt16LE(n.length, 26); loc.writeUInt16LE(0, 28);
    partes.push(loc, n, comp);
    const cd = Buffer.alloc(46);
    cd.writeUInt32LE(0x02014b50, 0); cd.writeUInt16LE(20, 4); cd.writeUInt16LE(20, 6); cd.writeUInt16LE(0x0800, 8); cd.writeUInt16LE(metodo, 10);
    cd.writeUInt16LE(0, 12); cd.writeUInt16LE(0x21, 14); cd.writeUInt32LE(crc, 16); cd.writeUInt32LE(comp.length, 20); cd.writeUInt32LE(datos.length, 24);
    cd.writeUInt16LE(n.length, 28); cd.writeUInt32LE(pos, 42);
    central.push(cd, n);
    pos += loc.length + n.length + comp.length;
  }
  const cdBuf = Buffer.concat(central);
  const fin = Buffer.alloc(22);
  fin.writeUInt32LE(0x06054b50, 0); fin.writeUInt16LE(entradas.length, 8); fin.writeUInt16LE(entradas.length, 10); fin.writeUInt32LE(cdBuf.length, 12); fin.writeUInt32LE(pos, 16);
  return Buffer.concat([...partes, cdBuf, fin]);
}

// Lee un archivo de un zip (null si no está)
function leeZip(buf, nombre) {
  let fin = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 70000); i--) if (buf.readUInt32LE(i) === 0x06054b50) { fin = i; break; }
  if (fin < 0) throw new Error('no es un archivo zip');
  const n = buf.readUInt16LE(fin + 10); let p = buf.readUInt32LE(fin + 16);
  for (let k = 0; k < n; k++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) break;
    const metodo = buf.readUInt16LE(p + 10); const csize = buf.readUInt32LE(p + 20);
    const nl = buf.readUInt16LE(p + 28); const el = buf.readUInt16LE(p + 30); const cl = buf.readUInt16LE(p + 32); const off = buf.readUInt32LE(p + 42);
    const nom = buf.toString('utf8', p + 46, p + 46 + nl);
    if (nom === nombre) {
      const ln = buf.readUInt16LE(off + 26); const le = buf.readUInt16LE(off + 28);
      const datos = buf.subarray(off + 30 + ln + le, off + 30 + ln + le + csize);
      return metodo === 0 ? Buffer.from(datos) : zlib.inflateRawSync(datos);
    }
    p += 46 + nl + el + cl;
  }
  return null;
}

// ---------------------------------------------------------------- Word
// Dos estilos: «libro» (Georgia, justificado, capítulos centrados) y «manuscrito» (el de enviar a editoriales: Times New
// Roman 12, márgenes de 2,5 cm, interlineado 1,5 o doble, portada con el recuento de palabras y páginas numeradas).
// No hay un formato único para todas las editoriales: si una pide otra cosa, sus normas mandan.
const runs = (t, extra = '') => trozos(t).map((r) => `<w:r><w:rPr>${extra}${r.neg ? '<w:b/>' : ''}${r.cur ? '<w:i/>' : ''}</w:rPr><w:t xml:space="preserve">${xml(r.texto)}</w:t></w:r>`).join('');
const palabrasDe = (bs) => bs.filter((b) => b.tipo === B.PARRAFO || b.tipo === B.CITA).reduce((n, b) => n + (plano(b.texto).match(/[\p{L}\p{N}]+(?:['’-][\p{L}\p{N}]+)*/gu) || []).length, 0);
// «unas 85.000 palabras» (redondeado como se suele dar en un manuscrito)
const recuento = (n) => (n < 1000 ? `${n} palabras` : `unas ${new Intl.NumberFormat('es-ES').format(Math.round(n / 1000) * 1000)} palabras`);

function docx(e, pid, op = {}) {
  const ms = op.estilo === 'manuscrito';
  const obra = e.get(pid) || {};
  const bs = manuscrito(e, pid, op);
  let body = ''; let primera = true;
  if (ms) {
    // Portada: autor y recuento arriba; título en el centro
    body += `<w:p><w:pPr><w:jc w:val="left"/></w:pPr>${runs(obra.autor || '')}</w:p><w:p><w:pPr><w:jc w:val="left"/></w:pPr>${runs(recuento(palabrasDe(bs)))}</w:p>`
      + `<w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr>${runs((obra.titulo || 'Sin título').toLocaleUpperCase('es'))}</w:p>`
      + (obra.autor ? `<w:p><w:pPr><w:jc w:val="center"/></w:pPr>${runs(`de ${obra.autor}`)}</w:p>` : '')
      + (obra.genero ? `<w:p><w:pPr><w:jc w:val="center"/></w:pPr>${runs(obra.genero)}</w:p>` : '');
  }
  for (const b of (ms ? bs.slice(obra.autor ? 2 : 1) : bs)) {
    switch (b.tipo) {
      case B.TITULO: body += `<w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr>${runs(b.texto)}</w:p>`; break;
      case B.PARTE: body += `<w:p><w:pPr><w:pStyle w:val="Heading1"/><w:pageBreakBefore/></w:pPr>${runs(b.texto)}</w:p>`; primera = true; break;
      case B.CAPITULO: case B.H1: body += `<w:p><w:pPr><w:pStyle w:val="Heading2"/>${b.tipo === B.CAPITULO ? '<w:pageBreakBefore/>' : ''}</w:pPr>${runs(b.texto)}</w:p>`; primera = true; break;
      case B.ESCENA: case B.H2: case B.H3: body += `<w:p><w:pPr><w:pStyle w:val="Heading3"/></w:pPr>${runs(b.texto)}</w:p>`; primera = true; break;
      case B.CITA: body += `<w:p><w:pPr><w:pStyle w:val="Quote"/></w:pPr>${runs(b.texto)}</w:p>`; break;
      case B.SALTO: body += '<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>* * *</w:t></w:r></w:p>'; primera = true; break;
      default: body += `<w:p><w:pPr><w:pStyle w:val="Normal"/>${primera ? '' : `<w:ind w:firstLine="${ms ? 709 : 567}"/>`}</w:pPr>${runs(b.texto)}</w:p>`; primera = false;
    }
  }
  if (ms) body += '<w:p><w:pPr><w:spacing w:before="480"/><w:jc w:val="center"/></w:pPr><w:r><w:t>FIN</w:t></w:r></w:p>';
  const W = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"';
  const numerar = op.numerar !== false;
  const margen = ms ? 1418 : 1440; // 2,5 cm
  const sect = `<w:sectPr>${numerar ? '<w:footerReference w:type="default" r:id="rId2"/>' : ''}<w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="${margen}" w:right="${margen}" w:bottom="${margen}" w:left="${margen}" w:header="708" w:footer="708" w:gutter="0"/>${ms ? '<w:titlePg/>' : ''}</w:sectPr>`;
  const doc = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document ${W}><w:body>${body}${sect}</w:body></w:document>`;
  const letra = ms ? 'Times New Roman' : 'Georgia';
  const linea = ms ? (String(op.interlineado) === '2' ? 480 : 360) : 360;
  const estilos = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles ${W}><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="${letra}" w:hAnsi="${letra}" w:cs="${letra}" w:eastAsia="${letra}"/><w:sz w:val="24"/><w:szCs w:val="24"/><w:lang w:val="es-ES"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="0" w:line="${linea}" w:lineRule="auto"/><w:jc w:val="${ms ? 'left' : 'both'}"/></w:pPr></w:pPrDefault></w:docDefaults>`
    + '<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:widowControl/></w:pPr></w:style>'
    + (ms
      ? '<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="4800" w:after="240"/><w:jc w:val="center"/></w:pPr><w:rPr><w:b/></w:rPr></w:style>'
        + '<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="3600" w:after="480"/><w:jc w:val="center"/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:caps/></w:rPr></w:style>'
        + '<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="2880" w:after="480"/><w:jc w:val="center"/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/></w:rPr></w:style>'
        + '<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="240"/><w:jc w:val="center"/><w:outlineLvl w:val="2"/></w:pPr><w:rPr><w:i/></w:rPr></w:style>'
      : '<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="3000" w:after="400"/><w:jc w:val="center"/></w:pPr><w:rPr><w:b/><w:sz w:val="56"/></w:rPr></w:style>'
        + '<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="2400" w:after="600"/><w:jc w:val="center"/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:sz w:val="40"/></w:rPr></w:style>'
        + '<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="1200" w:after="480"/><w:jc w:val="center"/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style>'
        + '<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="360" w:after="240"/><w:outlineLvl w:val="2"/></w:pPr><w:rPr><w:b/><w:i/><w:sz w:val="26"/></w:rPr></w:style>')
    + '<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="240" w:after="240"/><w:ind w:left="720" w:right="720"/></w:pPr><w:rPr><w:i/></w:rPr></w:style>'
    + '<w:style w:type="paragraph" w:styleId="Footer"><w:name w:val="footer"/><w:basedOn w:val="Normal"/></w:style></w:styles>';
  const hdr = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>';
  const pie = `${hdr}<w:ftr ${W}><w:p><w:pPr><w:pStyle w:val="Footer"/><w:jc w:val="${ms ? 'right' : 'center'}"/></w:pPr><w:fldSimple w:instr=" PAGE "><w:r><w:t>1</w:t></w:r></w:fldSimple></w:p></w:ftr>`;
  return zip([
    { nombre: '[Content_Types].xml', datos: Buffer.from(`${hdr}<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>${numerar ? '<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>' : ''}<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/></Types>`) },
    { nombre: '_rels/.rels', datos: Buffer.from(`${hdr}<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>`) },
    { nombre: 'word/_rels/document.xml.rels', datos: Buffer.from(`${hdr}<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>${numerar ? '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>' : ''}</Relationships>`) },
    { nombre: 'word/document.xml', datos: Buffer.from(doc) },
    { nombre: 'word/styles.xml', datos: Buffer.from(estilos) },
    ...(numerar ? [{ nombre: 'word/footer1.xml', datos: Buffer.from(pie) }] : []),
    { nombre: 'docProps/core.xml', datos: Buffer.from(`${hdr}<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:title>${xml(obra.titulo || '')}</dc:title><dc:creator>${xml(obra.autor || '')}</dc:creator><dc:language>es-ES</dc:language></cp:coreProperties>`) },
  ]);
}

// ---------------------------------------------------------------- PDF (una página web que Electron imprime: escritos-pdf.js)
// «libro»: A5, con las letras de la revista (Fraunces), justificado y con los capítulos en página nueva; «manuscrito»: A4,
// Times New Roman 12 con interlineado 1,5 o doble, como el Word. Numeradas abajo (la portada, sin número).
function htmlPdf(e, pid, op = {}, fuentes = '') {
  const ms = op.estilo === 'manuscrito';
  const obra = e.get(pid) || {};
  const bs = manuscrito(e, pid, op);
  const cuerpo = [];
  const titulo = obra.titulo || 'Sin título';
  if (ms) cuerpo.push(`<section class="portada"><p class="arriba">${xml(obra.autor || '')}<br>${xml(recuento(palabrasDe(bs)))}</p><h1>${xml(titulo.toLocaleUpperCase('es'))}</h1>${obra.autor ? `<p>de ${xml(obra.autor)}</p>` : ''}${obra.genero ? `<p>${xml(obra.genero)}</p>` : ''}</section>`);
  else cuerpo.push(`<section class="portada"><h1>${xml(titulo)}</h1>${obra.autor ? `<p class="autor">${xml(obra.autor)}</p>` : ''}</section>`);
  let primero = true;
  for (const b of bs.slice(obra.autor ? 2 : 1)) {
    if (b.tipo === B.PARTE) { cuerpo.push(`<h1 class="parte">${xml(b.texto)}</h1>`); primero = true; } else if (b.tipo === B.CAPITULO) { cuerpo.push(`<h2 class="capitulo">${xml(b.texto)}</h2>`); primero = true; } else if (b.tipo === B.H1) { cuerpo.push(`<h2>${xml(b.texto)}</h2>`); primero = true; } else if (b.tipo === B.ESCENA || b.tipo === B.H2 || b.tipo === B.H3) { cuerpo.push(`<h3>${xml(b.texto)}</h3>`); primero = true; } else if (b.tipo === B.CITA) cuerpo.push(`<blockquote>${html(b.texto)}</blockquote>`);
    else if (b.tipo === B.SALTO) { cuerpo.push('<p class="salto">* * *</p>'); primero = true; } else { cuerpo.push(`<p${primero ? ' class="primero"' : ''}>${html(b.texto)}</p>`); primero = false; }
  }
  if (ms) cuerpo.push('<p class="salto fin">FIN</p>');
  const linea = ms ? (String(op.interlineado) === '2' ? 2 : 1.5) : 1.42;
  const css = ms
    ? `@page{size:A4;margin:2.5cm;@bottom-right{content:counter(page);font:12pt 'Times New Roman','Liberation Serif','Tinos',serif}}@page:first{@bottom-right{content:none}}
body{font:12pt/${linea} 'Times New Roman','Liberation Serif','Tinos',serif;margin:0;color:#000}p{margin:0;text-indent:1.25cm}p.primero{text-indent:0}
.portada{break-after:page;text-align:center;padding-top:8cm;position:relative}.portada .arriba{position:absolute;top:0;left:0;text-align:left;text-indent:0}.portada h1{font-size:12pt;font-weight:bold;margin:0 0 1em}.portada p{text-indent:0}
h1.parte{break-before:page;text-align:center;font-size:12pt;text-transform:uppercase;padding-top:6cm}h2{text-align:center;font-size:12pt;font-weight:bold;margin:0 0 2em}h2.capitulo{break-before:page;padding-top:5cm}
h3{text-align:center;font-size:12pt;font-style:italic;font-weight:normal;margin:1em 0}p.salto{text-align:center;text-indent:0;margin:1em 0}blockquote{margin:1em 1.25cm;font-style:italic}`
    : `${fuentes}@page{size:A5;margin:1.9cm 1.6cm 2.1cm;@bottom-center{content:counter(page);font:9pt 'Libro',Georgia,serif;color:#555}}@page:first{@bottom-center{content:none}}
body{font:10.5pt/${linea} 'Libro',Georgia,serif;margin:0;color:#111;hyphens:auto;text-align:justify;font-variant-numeric:oldstyle-nums}p{margin:0;text-indent:1.3em;orphans:2;widows:2}p.primero{text-indent:0}
.portada{break-after:page;text-align:center;padding-top:52mm}.portada h1{font-size:26pt;line-height:1.1;font-weight:600;margin:0 0 .6em}.portada .autor{text-indent:0;font-style:italic;font-size:12pt}
h1.parte{break-before:page;text-align:center;font-size:16pt;font-weight:600;padding-top:60mm;letter-spacing:.08em;text-transform:uppercase}h2{text-align:center;font-weight:600;font-size:15pt;margin:0 0 1.6em}h2.capitulo{break-before:page;padding-top:38mm}
h3{text-align:center;font-style:italic;font-weight:400;font-size:11pt;margin:1.2em 0 .8em}p.salto{text-align:center;text-indent:0;margin:.9em 0}blockquote{margin:.8em 1.4em;font-style:italic}h1,h2,h3{break-after:avoid;hyphens:none}`;
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>${xml(titulo)}</title><style>${css}</style></head><body>${cuerpo.join('\n')}</body></html>`;
}

// ---------------------------------------------------------------- EPUB
const html = (t) => trozos(t).map((r) => { let x = xml(r.texto); if (r.cur) x = `<em>${x}</em>`; if (r.neg) x = `<strong>${x}</strong>`; return x; }).join('');

function epub(e, pid, op) {
  const obra = e.get(pid);
  if (!obra) return Buffer.alloc(0);
  const titulo = obra.titulo || 'Sin título';
  const pags = [{ titulo, cuerpo: `<div class="portada"><h1>${xml(titulo)}</h1>${obra.autor ? `<p class="autor">${xml(obra.autor)}</p>` : ''}</div>` }];
  let ultimaPar = false;
  for (const b of manuscrito(e, pid, op).slice(obra.autor ? 2 : 1)) {
    if (b.tipo === B.PARTE) { pags.push({ titulo: b.texto, cuerpo: `<h1 class="parte">${xml(b.texto)}</h1>` }); ultimaPar = false; continue; }
    if (b.tipo === B.CAPITULO) { pags.push({ titulo: b.texto, cuerpo: `<h2>${xml(b.texto)}</h2>` }); ultimaPar = false; continue; }
    if (pags.length === 1) pags.push({ titulo: 'Texto', cuerpo: '' });
    const p = pags[pags.length - 1];
    if (b.tipo === B.ESCENA || b.tipo === B.H2 || b.tipo === B.H3) { p.cuerpo += `<h3>${xml(b.texto)}</h3>`; ultimaPar = false; }
    else if (b.tipo === B.H1) { p.cuerpo += `<h2>${xml(b.texto)}</h2>`; ultimaPar = false; }
    else if (b.tipo === B.CITA) p.cuerpo += `<blockquote><p>${html(b.texto)}</p></blockquote>`;
    else if (b.tipo === B.SALTO) { p.cuerpo += '<p class="salto">* * *</p>'; ultimaPar = false; }
    else if (b.tipo === B.PARRAFO) { p.cuerpo += `<p${ultimaPar ? '' : ' class="primero"'}>${html(b.texto)}</p>`; ultimaPar = true; }
  }
  const cab = (t, c, extra = '') => `<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xml:lang="es"><head><meta charset="utf-8"/><title>${xml(t)}</title><link rel="stylesheet" type="text/css" href="estilo.css"/></head><body>${extra}${c}</body></html>`;
  const manif = pags.map((_, i) => `<item id="p${i}" href="p${i}.xhtml" media-type="application/xhtml+xml"/>`).join('');
  const espina = pags.map((_, i) => `<itemref idref="p${i}"/>`).join('');
  const mod = new Date().toISOString().replace(/\.\d+Z$/, 'Z');
  const opf = `<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid" xml:lang="es"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">${xml(`atrio-${pid}`)}</dc:identifier><dc:title>${xml(titulo)}</dc:title><dc:creator>${xml(obra.autor || 'Anónimo')}</dc:creator><dc:language>es</dc:language><meta property="dcterms:modified">${mod}</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="css" href="estilo.css" media-type="text/css"/>${manif}</manifest><spine>${espina}</spine></package>`;
  const nav = `<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="es"><head><meta charset="utf-8"/><title>${xml(titulo)}</title></head><body><nav epub:type="toc"><h1>Índice</h1><ol>${pags.map((p, i) => `<li><a href="p${i}.xhtml">${xml(p.titulo)}</a></li>`).join('')}</ol></nav></body></html>`;
  const css = 'body{font-family:Georgia,serif;line-height:1.5;margin:5%}p{margin:0;text-align:justify;text-indent:1.4em}p.primero,p.autor,p.salto{text-indent:0}p.salto{text-align:center;margin:1.2em 0}h1,h2,h3{text-align:center;text-indent:0;margin:1.6em 0 1em}.portada{margin-top:30%;text-align:center}blockquote{margin:1em 2em;font-style:italic}';
  return zip([
    { nombre: 'mimetype', datos: Buffer.from('application/epub+zip'), guardado: true },
    { nombre: 'META-INF/container.xml', datos: Buffer.from('<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>') },
    { nombre: 'OEBPS/content.opf', datos: Buffer.from(opf) }, { nombre: 'OEBPS/nav.xhtml', datos: Buffer.from(nav) }, { nombre: 'OEBPS/estilo.css', datos: Buffer.from(css) },
    ...pags.map((p, i) => ({ nombre: `OEBPS/p${i}.xhtml`, datos: Buffer.from(cab(p.titulo, p.cuerpo)) })),
  ]);
}

// ---------------------------------------------------------------- Importar
/** Un texto (Markdown o plano) a una obra nueva: los «# » o «## » abren un capítulo. Devuelve el id de la obra. */
function importaTexto(e, titulo, txt) {
  const obra = e.nuevo('p', '', { titulo: titulo || 'Sin título' });
  let cap = null; let cuerpo = [];
  const cierra = () => {
    if (!cap) return;
    const t = cuerpo.join('\n').trim();
    if (t || !e.escenas(cap.id).length) e.nuevo('e', obra.id, { cid: cap.id, titulo: 'Escena 1', texto: t });
    cuerpo = [];
  };
  for (const l of String(txt || '').replace(/\r\n/g, '\n').split('\n')) {
    const m = /^#{1,2}\s+(.*)$/.exec(l.trim());
    if (m) { cierra(); cap = e.nuevo('c', obra.id, { titulo: m[1].trim() }); }
    else { if (!cap && !l.trim()) continue; if (!cap) cap = e.nuevo('c', obra.id, { titulo: 'Capítulo 1' }); cuerpo.push(l); }
  }
  cierra();
  return obra.id;
}

/** El texto de un .docx (sus párrafos, con «# » en los títulos), para importarlo. */
function textoDeDocx(buf) {
  const d = leeZip(buf, 'word/document.xml');
  const x = d ? d.toString('utf8') : '';
  let sb = '';
  for (const p of x.match(/<w:p[ >].*?<\/w:p>/gs) || []) {
    const tit = /<w:pStyle w:val="(?:Heading|Titulo|Ttulo)(\d)"/.exec(p);
    const txt = [...p.matchAll(/<w:t[^>]*>(.*?)<\/w:t>/gs)].map((m) => m[1]).join('').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&amp;/g, '&');
    if (tit && txt.trim()) sb += `${tit[1] === '1' || tit[1] === '2' ? '# ' : '## '}${txt}\n\n`; else sb += `${txt}\n\n`;
  }
  return sb;
}

module.exports = { bloques, trozos, manuscrito, markdown, texto, docx, epub, htmlPdf, importaTexto, textoDeDocx, zip, leeZip, palabrasDe, recuento };
