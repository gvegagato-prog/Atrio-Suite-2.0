'use strict';
/* global window, document, localStorage */
// Descubrir en el escritorio: los mismos tests de preguntas que el móvil (juegos, libros, pelis y series), con las
// mismas búsquedas (RAWG, Open Library y TMDB) y las claves que pusiste en el móvil, que llegan con tu cuenta.
// Los bancos de preguntas son los del móvil (herramientas/preguntas/unir.py los copia a datos/).
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  // Géneros, temas y épocas: los mismos que Descubrir.kt
  const M = {"MOVIE_G": {"action": 28, "adventure": 12, "animation": 16, "comedy": 35, "crime": 80, "documentary": 99, "drama": 18, "family": 10751, "fantasy": 14, "history": 36, "horror": 27, "music": 10402, "mystery": 9648, "romance": 10749, "scifi": 878, "thriller": 53, "war": 10752, "western": 37, "kids": 10751, "politics": 18, "reality": 99}, "TV_G": {"action": 10759, "adventure": 10759, "animation": 16, "comedy": 35, "crime": 80, "documentary": 99, "drama": 18, "family": 10751, "fantasy": 10765, "history": 18, "horror": 9648, "music": 18, "mystery": 9648, "romance": 18, "scifi": 10765, "thriller": 80, "war": 10768, "western": 37, "kids": 10762, "politics": 10768, "reality": 10764}, "G_NAMES": {"action": "Acción", "adventure": "Aventura", "animation": "Animación", "comedy": "Comedia", "crime": "Crimen", "documentary": "Documental", "drama": "Drama", "family": "Familia", "fantasy": "Fantasía", "history": "Historia", "horror": "Terror", "music": "Música", "mystery": "Misterio", "romance": "Romance", "scifi": "Ciencia ficción", "thriller": "Suspense", "war": "Bélico", "western": "Western", "kids": "Infantil", "reality": "Reality", "politics": "Política"}, "ERA_S": {"classic": ["1900-01-01", "1979-12-31"], "80s90s": ["1980-01-01", "1999-12-31"], "2000s": ["2000-01-01", "2014-12-31"], "recent": ["2015-01-01", ""]}, "ERA_NAMES": {"classic": "Clásicos", "80s90s": "Años 80 y 90", "2000s": "Años 2000", "recent": "Recientes", "20c": "Siglo XX", "modern": "2000–2014"}, "BOOK_NAMES": {"adventure stories": "Aventuras", "alternative history": "Historia alternativa", "american fiction": "Literatura estadounidense", "animals": "Animales", "art": "Arte", "artificial intelligence": "Inteligencia artificial", "astronomy": "Astronomía", "autobiography": "Autobiografías", "biography": "Biografías", "business": "Empresa", "cats": "Gatos", "classic literature": "Clásicos", "comic books, strips": "Cómic", "coming of age": "Crecer", "cooking": "Cocina", "crime": "Crimen", "cyberpunk": "Cyberpunk", "detective": "Detectives", "detective and mystery stories": "Novela policiaca", "dogs": "Perros", "dragons": "Dragones", "drama": "Teatro", "dystopias": "Distopías", "economics": "Economía", "english fiction": "Literatura inglesa", "epic fantasy": "Fantasía épica", "essays": "Ensayo", "fairy tales": "Cuentos de hadas", "families": "Familias", "family life": "Vida familiar", "fantasy": "Fantasía", "feminism": "Feminismo", "fiction": "Novela", "french fiction": "Literatura francesa", "friendship": "Amistad", "ghost stories": "Fantasmas", "gothic fiction": "Gótica", "graphic novels": "Novela gráfica", "historical fiction": "Novela histórica", "history": "Historia", "horror": "Terror", "humor": "Humor", "japanese fiction": "Literatura japonesa", "juvenile fiction": "Infantil", "kings and rulers": "Reyes y reinos", "latin american fiction": "Literatura latinoamericana", "love stories": "Historias de amor", "magic": "Magia", "magical realism": "Realismo mágico", "mathematics": "Matemáticas", "medicine": "Medicina", "memoirs": "Memorias", "murder": "Asesinatos", "music": "Música", "mystery": "Misterio", "mythology": "Mitología", "nature": "Naturaleza", "nonfiction": "No ficción", "philosophy": "Filosofía", "physics": "Física", "pirates": "Piratas", "plays": "Teatro", "poetry": "Poesía", "police": "Policías", "politics": "Política", "psychological fiction": "Novela psicológica", "psychology": "Psicología", "quests": "Grandes misiones", "religion": "Religión", "robots": "Robots", "romance": "Romántica", "russian fiction": "Literatura rusa", "satire": "Sátira", "school stories": "Colegios", "science": "Ciencia", "science fiction": "Ciencia ficción", "self-help": "Crecimiento personal", "short stories": "Relatos", "sociology": "Sociología", "space opera": "Ópera espacial", "spanish fiction": "Literatura española", "sports": "Deporte", "spy stories": "Espías", "superheroes": "Superhéroes", "survival": "Supervivencia", "suspense": "Suspense", "thriller": "Thriller", "time travel": "Viajes en el tiempo", "travel": "Viajes", "true crime": "Crímenes reales", "utopias": "Utopías", "vampires": "Vampiros", "war stories": "Guerra", "witches": "Brujas", "women": "Mujeres", "world war, 1939-1945": "Segunda Guerra Mundial", "young adult fiction": "Juvenil"}, "ERA_B": {"classic": "[* TO 1949]", "20c": "[1950 TO 1999]", "modern": "[2000 TO 2014]", "recent": "[2015 TO *]"}, "TAG_NAMES": {"story-rich": "Con buena historia", "difficult": "Difíciles", "relaxing": "Relajantes", "atmospheric": "Con atmósfera", "funny": "Divertidos", "exploration": "Explorar", "mystery": "Misterio", "cute": "Bonitos", "dark": "Oscuros", "emotional": "Emotivos", "open-world": "Mundo abierto", "fantasy": "Fantasía", "horror": "Terror", "sci-fi": "Ciencia ficción", "survival": "Supervivencia", "roguelike": "Roguelike", "souls-like": "Soulslike", "co-op": "Cooperativo", "competitive": "Competitivos", "short": "Cortos", "historical": "Históricos", "stealth": "Sigilo", "choices-matter": "Decisiones", "space": "Espacio", "cyberpunk": "Cyberpunk", "pixel-graphics": "Píxel", "farming": "Granjas", "management": "Gestión", "crafting": "Construir y fabricar", "walking-simulator": "Paseos", "turn-based": "Por turnos", "metroidvania": "Metroidvania", "visual-novel": "Novela visual", "post-apocalyptic": "Postapocalípticos"}, "GAME_ERA": {"retro": ["1970-01-01", "1999-12-31"], "classic": ["2000-01-01", "2009-12-31"], "modern": ["2010-01-01", "2019-12-31"], "recent": ["2020-01-01", ""]}, "GAME_ERA_NAMES": {"retro": "Retro", "classic": "Años 2000", "modern": "Años 2010", "recent": "Recientes"}, "GAME_GENRES": {"action": "Acción", "adventure": "Aventura", "role-playing-games-rpg": "Rol (RPG)", "shooter": "Disparos", "indie": "Indie", "platformer": "Plataformas", "puzzle": "Puzles", "racing": "Carreras", "sports": "Deportes", "strategy": "Estrategia", "simulation": "Simulación", "fighting": "Lucha", "family": "Familiar", "arcade": "Arcade", "casual": "Casual", "massively-multiplayer": "Multijugador masivo", "board-games": "De mesa", "card": "Cartas"}};

  // ---------------------------------------------------------------- Lo que van diciendo tus respuestas (Taste)
  class Gusto {
    constructor() { this.pesos = {}; this.tags = {}; this.kws = {}; this.votos = {}; this.nota = 0; }
    sumar(e) {
      for (const [k, v] of Object.entries(e || {})) {
        if (k === 'g' || k === 's') for (const [g, w] of Object.entries(v)) this.pesos[g] = (this.pesos[g] || 0) + Number(w);
        else if (k === 'tg') for (const [g, w] of Object.entries(v)) this.tags[g] = (this.tags[g] || 0) + Number(w);
        else if (k === 'kw') for (const w of v) this.kws[w] = (this.kws[w] || 0) + 1;
        else if (k === 'rate') this.nota = Math.max(this.nota, Number(v));
        else { const m = (this.votos[k] = this.votos[k] || {}); m[v] = (m[v] || 0) + 1; }
      }
    }
    // La opción más votada (si hay empate, ninguna)
    elegido(k) {
      const m = this.votos[k];
      if (!m) return null;
      const arr = Object.entries(m).sort((a, b) => b[1] - a[1]);
      if (!arr.length || (arr[1] && arr[1][1] === arr[0][1])) return null;
      return arr[0][0];
    }
    gustan(min = 1.5) { return Object.entries(this.pesos).filter(([, w]) => w >= min).sort((a, b) => b[1] - a[1]).map(([g]) => g); }
    evita() { return Object.entries(this.pesos).filter(([, w]) => w <= -2).map(([g]) => g); }
    tagsGustan(min = 1.5) { return Object.entries(this.tags).filter(([, w]) => w >= min).sort((a, b) => b[1] - a[1]).map(([g]) => g); }
  }

  // ---------------------------------------------------------------- Preguntas
  const FICHERO = { 0: 'banco-juegos.js', 1: 'banco-libros.js', 2: 'banco-pantalla.js' };
  // Los bancos se cargan la primera vez que hacen falta (son unos 125 KB cada uno)
  function banco(dom) {
    if (window.RV_BANCOS && window.RV_BANCOS[dom]) return Promise.resolve(window.RV_BANCOS[dom]);
    return new Promise((ok, mal) => {
      const s = document.createElement('script');
      s.src = `datos/${FICHERO[dom]}`;
      s.onload = () => (window.RV_BANCOS && window.RV_BANCOS[dom] ? ok(window.RV_BANCOS[dom]) : mal(new Error('No se han podido leer las preguntas.')));
      s.onerror = () => mal(new Error('No se han podido leer las preguntas.'));
      document.head.append(s);
    });
  }
  const leerRecientes = (dom) => { try { return JSON.parse(localStorage.getItem(`descubrir:recientes:${dom}`) || '[]'); } catch { return []; } };
  const guardarRecientes = (dom, ids) => { try { localStorage.setItem(`descubrir:recientes:${dom}`, JSON.stringify(ids.slice(0, 120))); } catch { /* */ } };
  const barajar = (l) => { const a = [...l]; for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
  // Elige n preguntas variadas (un eje distinto cada vez, mientras se pueda) evitando las de las últimas rondas
  async function elegir(dom, n) {
    const todas = barajar(await banco(dom));
    const recientes = leerRecientes(dom);
    const frescas = todas.filter((q) => !recientes.includes(q.id));
    const lista = frescas.length ? frescas : todas;
    const out = [];
    if (dom === 2) { const f = lista.find((q) => q.axis === 'format'); if (f) out.push(f); }
    const ejes = new Set(out.map((q) => q.axis));
    for (const q of lista) { if (out.length >= n) break; if (!out.includes(q) && !ejes.has(q.axis)) { out.push(q); ejes.add(q.axis); } }
    for (const q of lista) { if (out.length >= n) break; if (!out.includes(q)) out.push(q); }
    guardarRecientes(dom, [...out.map((q) => q.id), ...recientes]);
    return out;
  }

  // ---------------------------------------------------------------- Resumen (arriba de los resultados)
  function resumen(t, dom, filtros) {
    const out = [];
    if (dom === 1) out.push(...t.gustan().slice(0, 3).map((s) => M.BOOK_NAMES[s] || s.charAt(0).toUpperCase() + s.slice(1)));
    else if (dom === 0) {
      out.push(...t.gustan().slice(0, 3).map((g) => M.GAME_GENRES[g]).filter(Boolean));
      out.push(...t.tagsGustan(2).slice(0, 3).map((g) => M.TAG_NAMES[g]).filter(Boolean));
      const p = { pc: 'PC', playstation: 'PlayStation', xbox: 'Xbox', nintendo: 'Nintendo', mobile: 'Móvil' }[t.elegido('plat')]; if (p) out.push(p);
      const m = { solo: 'Para ti solo', coop: 'En cooperativo', online: 'En línea' }[t.elegido('mode')]; if (m) out.push(m);
      const e = M.GAME_ERA_NAMES[t.elegido('era')]; if (e) out.push(e);
    } else {
      const ty = t.elegido('type'); if (ty === 'movie') out.push('Películas'); if (ty === 'tv') out.push('Series');
      out.push(...t.gustan().slice(0, 3).map((g) => M.G_NAMES[g]).filter(Boolean));
    }
    if (dom !== 0) { const e = M.ERA_NAMES[t.elegido('era')]; if (e) out.push(e); }
    const pop = { hidden: 'Joyas ocultas', mainstream: 'Éxitos', cult: 'De culto' }[t.elegido('pop')]; if (pop) out.push(pop);
    const len = { short: 'Cortas', long: 'Largas' }[t.elegido('len')]; if (len && dom !== 0) out.push(len);
    if ((t.nota >= 7.5 && dom !== 0) || t.nota >= 85) out.push('Solo lo mejor');
    if (filtros && filtros.anos) out.push(`${filtros.anos[0]}–${filtros.anos[1]}`);
    if (filtros && filtros.plats && filtros.plats.length) out.push(filtros.plats.map((p) => PLATS.find((x) => x[0] === p)[1]).join(', '));
    return [...new Set(out)];
  }

  // ---------------------------------------------------------------- Buscar: pelis y series (TMDB)
  async function pantalla(t, mios) {
    const N = RV.red;
    const tipo = t.elegido('type');
    const tipos = tipo === 'movie' ? [false] : tipo === 'tv' ? [true] : [false, true];
    const gustan = t.gustan();
    const evita = t.evita();
    const kwIds = [];
    for (const [nombre] of Object.entries(t.kws).sort((a, b) => b[1] - a[1]).slice(0, 3)) {
      try {
        const r = await N.tmdb(`/search/keyword?query=${encodeURIComponent(nombre)}`);
        const hit = (r.results || []).find((x) => String(x.name || '').toLowerCase() === nombre.toLowerCase());
        if (hit) kwIds.push(hit.id);
      } catch { /* sin ese tema */ }
    }
    const era = M.ERA_S[t.elegido('era')];
    const len = t.elegido('len');
    const l0 = t.elegido('lang');
    const lang = l0 == null ? null : l0 === 'foreign' ? 'es|fr|it|ja|ko|de|hi|pt|sv|da|no|zh|tr' : l0;
    const pop = t.elegido('pop');
    const minNota = t.nota > 0 ? t.nota : 6.2;
    const vistos = new Map();
    for (const tv of tipos) {
      const gmap = tv ? M.TV_G : M.MOVIE_G;
      const gCon = [...new Set(gustan.slice(0, 3).map((g) => gmap[g]).filter(Boolean))];
      const gSin = [...new Set(evita.map((g) => gmap[g]).filter((g) => g && !gCon.includes(g)))];
      const consulta = (conKw, relajada) => {
        let q = `${tv ? '/discover/tv' : '/discover/movie'}?language=es-ES&include_adult=false`;
        if (gCon.length) q += `&with_genres=${gCon.join('|')}`;
        if (gSin.length) q += `&without_genres=${gSin.join(',')}`;
        if (conKw && kwIds.length) q += `&with_keywords=${kwIds.join('|')}`;
        if (!relajada && era) {
          const f = tv ? 'first_air_date' : 'primary_release_date';
          q += `&${f}.gte=${era[0]}`; if (era[1]) q += `&${f}.lte=${era[1]}`;
        }
        if (!relajada && !tv) q += { short: '&with_runtime.lte=100', long: '&with_runtime.gte=140', medium: '&with_runtime.gte=95&with_runtime.lte=145' }[len] || '';
        if (lang) q += `&with_original_language=${lang}`;
        q += {
          hidden: '&vote_count.gte=40&vote_count.lte=1500&sort_by=vote_average.desc',
          mainstream: '&vote_count.gte=2000&sort_by=popularity.desc',
          cult: '&vote_count.gte=300&vote_count.lte=6000&sort_by=vote_average.desc',
        }[pop] || `&vote_count.gte=${tv ? 80 : 200}&sort_by=popularity.desc`;
        return `${q}&vote_average.gte=${minNota}`;
      };
      const amplia = `${tv ? '/discover/tv' : '/discover/movie'}?language=es-ES&include_adult=false&vote_count.gte=150&sort_by=popularity.desc${gCon.length ? `&with_genres=${gCon.slice(0, 2).join('|')}` : ''}`;
      const planes = [kwIds.length ? consulta(true, false) : null, consulta(false, false), consulta(false, true), amplia].filter(Boolean);
      for (const [pi, q] of planes.entries()) {
        for (let page = 1; page <= 3; page++) {
          let res;
          try { res = await N.tmdb(`${q}&page=${page}`); } catch (e) { if (!vistos.size && pi === 0 && page === 1) throw e; break; }
          for (const o of res.results || []) {
            const titulo = tv ? o.name : o.title;
            const orig = tv ? o.original_name : o.original_title;
            if (!titulo || mios.has(RV.normName(titulo)) || mios.has(RV.normName(orig))) continue;
            const k = (tv ? 't' : 'm') + o.id;
            if (vistos.has(k)) continue;
            let punt = 0;
            for (const [g, w] of Object.entries(t.pesos)) { const id = gmap[g]; if (id && (o.genre_ids || []).includes(id)) punt += w; }
            if (q.includes('with_keywords')) punt += 3;
            punt += (Number(o.vote_average) || 6) * 0.6;
            vistos.set(k, {
              dom: 2, tv, id: Number(o.id), titulo, original: orig || titulo, ano: String((tv ? o.first_air_date : o.release_date) || '').slice(0, 4),
              portada: o.poster_path ? `https://image.tmdb.org/t/p/w342${o.poster_path}` : '', creador: '', resumen: o.overview || '', nota: Number(o.vote_average) || 0, encaje: punt,
            });
          }
          if (page >= (res.total_pages || 1)) break;
        }
        if (vistos.size >= 90) break;
        if (pi === planes.length - 2 && vistos.size >= 60) break;
      }
    }
    return escala([...vistos.values()]);
  }

  // ---------------------------------------------------------------- Buscar: libros (Open Library)
  async function libros(t, mios) {
    const N = RV.red;
    const gustan = t.gustan(1);
    const evita = t.evita();
    const consulta = (temas, relajada, orden) => {
      const p = [];
      if (temas.length) p.push(`(${temas.map((s) => `subject:"${s}"`).join(' OR ')})`);
      for (const s of evita) p.push(`-subject:"${s}"`);
      const fic = t.elegido('fic');
      if (fic === 'nonfiction') p.push('-subject:"fiction"'); else if (fic === 'fiction') p.push('subject:"fiction"');
      if (!relajada) {
        const e = M.ERA_B[t.elegido('era')]; if (e) p.push(`first_publish_year:${e}`);
        const l = { short: 'number_of_pages_median:[* TO 250]', long: 'number_of_pages_median:[500 TO *]', medium: 'number_of_pages_median:[250 TO 500]' }[t.elegido('len')]; if (l) p.push(l);
        if (t.elegido('lang') === 'spa') p.push('language:spa');
      }
      p.push({ hidden: 'ratings_count:[5 TO 150]', mainstream: 'ratings_count:[150 TO *]' }[t.elegido('pop')] || 'ratings_count:[15 TO *]');
      if (!temas.length && p.length <= 1) p.push('subject:"fiction"');
      return `https://openlibrary.org/search.json?q=${encodeURIComponent(p.join(' '))}&sort=${orden}&limit=100&fields=key,title,author_name,first_publish_year,cover_i,number_of_pages_median,ratings_average,ratings_count,subject`;
    };
    const orden = t.elegido('pop') === 'mainstream' ? 'readinglog' : 'rating';
    const amplia = `https://openlibrary.org/search.json?q=${encodeURIComponent(`${gustan.length ? `(${gustan.slice(0, 2).map((s) => `subject:"${s}"`).join(' OR ')})` : 'subject:"fiction"'} ratings_count:[20 TO *]`)}&sort=rating&limit=100&fields=key,title,author_name,first_publish_year,cover_i,number_of_pages_median,ratings_average,ratings_count,subject`;
    const planes = [consulta(gustan.slice(0, 4), false, orden), consulta(gustan.slice(0, 2), false, 'readinglog'), consulta(gustan.slice(0, 4), true, orden), amplia];
    const vistos = new Map();
    let error = null;
    for (const [pi, url] of planes.entries()) {
      if (pi === 3 && vistos.size >= 60) break;
      let docs;
      try { docs = (await N.json(url)).docs || []; } catch (e) { error = e; continue; }
      for (const o of docs) {
        const id = Number(String(o.key || '').replace(/\D/g, ''));
        if (!id || vistos.has(id) || !o.title || mios.has(RV.normName(o.title))) continue;
        const temas = (o.subject || []).map((x) => String(x).toLowerCase());
        let punt = 0;
        for (const [s, w] of Object.entries(t.pesos)) if (temas.some((x) => x === s || x.includes(s))) punt += w;
        punt += (Number(o.ratings_average) || 3.5) * 1.5;
        const pags = Number(o.number_of_pages_median) || 0;
        vistos.set(id, {
          dom: 1, tv: false, id, titulo: o.title, original: o.title, ano: o.first_publish_year ? String(o.first_publish_year) : '',
          portada: o.cover_i ? `https://covers.openlibrary.org/b/id/${o.cover_i}-L.jpg` : '', creador: (o.author_name || [])[0] || '',
          resumen: pags ? `${pags} páginas` : '', paginas: pags, nota: Number(o.ratings_average) || 0, encaje: punt,
        });
      }
      if (vistos.size >= 120) break;
    }
    if (!vistos.size && error) throw error;
    return escala([...vistos.values()].filter((x) => x.portada));
  }

  // ---------------------------------------------------------------- Buscar: juegos (RAWG)
  const RAWG = 'https://api.rawg.io/api';
  // Ids de las «plataformas padre» de RAWG: se piden una vez y se guardan (no se dan por supuestos)
  async function padres(clave) {
    try { const c = JSON.parse(localStorage.getItem('descubrir:rawg-padres') || 'null'); if (c && Object.keys(c).length) return c; } catch { /* */ }
    const o = await RV.red.json(`${RAWG}/platforms/lists/parents?key=${encodeURIComponent(clave)}&page_size=40`);
    const m = {};
    for (const p of o.results || []) if (p.slug && p.id) m[p.slug] = p.id;
    try { localStorage.setItem('descubrir:rawg-padres', JSON.stringify(m)); } catch { /* */ }
    return m;
  }
  async function juegos(t, mios, filtros) {
    const N = RV.red;
    const clave = N.rawgClave();
    if (!clave) throw new Error('Para descubrir juegos hace falta tu clave gratuita de RAWG: ponla en el móvil (Ajustes → Servicios y copias) y llegará sola.');
    const gustan = t.gustan().filter((g) => M.GAME_GENRES[g] || g === 'educational');
    const tagsGustan = t.tagsGustan();
    const malos = Object.entries(t.tags).filter(([, w]) => w <= -2).map(([g]) => g);
    const modoTag = { solo: 'singleplayer', coop: 'co-op', online: 'multiplayer' }[t.elegido('mode')] || null;
    const pp = await padres(clave).catch(() => ({}));
    const ids = (p) => ({ pc: [pp.pc], playstation: [pp.playstation], xbox: [pp.xbox], nintendo: [pp.nintendo], mobile: [pp.ios, pp.android] }[p] || []).filter(Boolean);
    const misPlats = (filtros.plats || []).length > 0;
    const plat = misPlats ? [...new Set(filtros.plats.flatMap(ids))] : ids(t.elegido('plat'));
    if (misPlats && !plat.length) throw new Error('No se han podido cargar las plataformas de RAWG. Prueba otra vez.');
    const misAnos = !!filtros.anos;
    const era = misAnos ? [`${filtros.anos[0]}-01-01`, `${filtros.anos[1]}-12-31`] : M.GAME_ERA[t.elegido('era')];
    const pop = t.elegido('pop');
    const len = t.elegido('len');
    const orden0 = { mainstream: '-added', hidden: '-metacritic', cult: '-rating' }[pop] || '-added';
    const consulta = (generos, tags, filtrar, conNota, orden = orden0) => {
      let q = `${RAWG}/games?key=${encodeURIComponent(clave)}&page_size=40&exclude_additions=true&ordering=${orden}`;
      if (generos.length) q += `&genres=${encodeURIComponent(generos.join(','))}`;
      if (tags.length) q += `&tags=${encodeURIComponent(tags.join(','))}`;
      if ((filtrar || misPlats) && plat.length) q += `&parent_platforms=${plat.join(',')}`;
      if ((filtrar || misAnos) && era) q += `&dates=${era[0]},${era[1] || new Date().toISOString().slice(0, 10)}`;
      if (conNota && t.nota > 0) q += `&metacritic=${Math.round(t.nota)},100`;
      return q;
    };
    const tagQ = [...new Set([...tagsGustan.slice(0, 2), ...(modoTag ? [modoTag] : [])])].slice(0, 2);
    const planes = [
      tagQ.length ? consulta(gustan.slice(0, 2), tagQ, true, true) : null,
      consulta(gustan.slice(0, 2), [], true, true),
      consulta(gustan.slice(0, 2), [], true, false),
      tagsGustan.length ? consulta([], tagsGustan.slice(0, 1), true, false) : null,
      consulta(gustan.slice(0, 1), [], false, false, '-added'),
    ].filter(Boolean);
    const vistos = new Map();
    let error = null;
    for (const [pi, url] of planes.entries()) {
      if (pi === planes.length - 1 && vistos.size >= 50) break;
      for (let page = 1; page <= 2; page++) {
        let res;
        try { res = await N.json(`${url}&page=${page}`); } catch (e) { error = e; break; }
        for (const o of res.results || []) {
          if (!o.id || !o.name || vistos.has(o.id) || mios.has(RV.normName(o.name))) continue;
          if (filtros.anos) { const y = Number(String(o.released || '').slice(0, 4)); if (!y || y < filtros.anos[0] || y > filtros.anos[1]) continue; }
          const gs = o.genres || [];
          const ts = new Set((o.tags || []).map((x) => x && x.slug).filter(Boolean));
          let punt = 0;
          for (const g of gs) punt += t.pesos[g.slug] || 0;
          for (const [tg, w] of Object.entries(t.tags)) if (ts.has(tg)) punt += w * 0.8;
          if (modoTag && ts.has(modoTag)) punt += 2;
          if (malos.some((x) => ts.has(x))) punt -= 4;
          const mc = Number(o.metacritic) || 0;
          punt += mc > 0 ? (mc - 60) * 0.12 : (Number(o.rating) || 3) * 0.8;
          const horas = Number(o.playtime) || 0;
          if (horas > 0) punt += { short: horas <= 12 ? 2 : -2, long: horas >= 35 ? 2 : -1, medium: horas >= 8 && horas <= 40 ? 1.5 : -0.5 }[len] || 0;
          if (pop === 'hidden') punt -= Math.log(1 + (Number(o.added) || 0)) * 0.4;
          if (pi === 0) punt += 2;
          vistos.set(o.id, {
            dom: 0, tv: false, id: Number(o.id), titulo: o.name, original: o.name, ano: String(o.released || '').slice(0, 4), portada: o.background_image || '',
            creador: gs.slice(0, 3).map((g) => M.GAME_GENRES[g.slug] || g.name).join(' · '), resumen: horas ? `Unas ${horas} h de media` : '', nota: mc / 10, encaje: punt,
          });
        }
        if (!res.next) break;
      }
      if (vistos.size >= 100) break;
    }
    if (!vistos.size && error) throw new Error(/401/.test(RV.limpiaError(error)) ? 'La clave de RAWG no es válida (revísala en el móvil).' : `RAWG: ${RV.limpiaError(error)}`);
    return escala([...vistos.values()]);
  }
  // Resumen y nota de Metacritic de un juego (al abrirlo)
  async function fichaJuego(id) {
    const o = await RV.red.json(`${RAWG}/games/${id}?key=${encodeURIComponent(RV.red.rawgClave())}`);
    const txt = String(o.description_raw || '').trim() || String(o.description || '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
    return { texto: txt, metacritic: Number(o.metacritic) || 0 };
  }

  // Del encaje en bruto a un porcentaje (el primero, 99 %; ninguno por debajo de 40 %)
  function escala(lista) {
    const l = lista.sort((a, b) => b.encaje - a.encaje);
    const top = Math.max(1, (l[0] && l[0].encaje) || 1);
    return l.map((x) => ({ ...x, encaje: Math.max(40, Math.min(99, Math.round(55 + (44 * x.encaje) / top))) }));
  }

  // ---------------------------------------------------------------- La página
  const PLATS = [['pc', 'PC'], ['playstation', 'PlayStation'], ['xbox', 'Xbox'], ['nintendo', 'Nintendo'], ['mobile', 'Móvil']];
  const QUE = { 0: 'juegos', 1: 'libros', 2: 'pelis y series' };
  const TITULO = { 0: '¿A qué juego ahora?', 1: '¿Qué leo ahora?', 2: '¿Qué vemos hoy?' };
  const ANO_MIN = 1980;
  // Lo que llevas de cada test mientras la revista está abierta (y los filtros de juegos, que se recuerdan)
  const estados = {};
  const leerFiltros = () => { try { const f = JSON.parse(localStorage.getItem('descubrir:filtros') || 'null'); if (f && typeof f === 'object') return { anos: Array.isArray(f.anos) ? f.anos : null, plats: Array.isArray(f.plats) ? f.plats : [] }; } catch { /* */ } return { anos: null, plats: [] }; };
  const filtros = leerFiltros();
  const guardarFiltros = () => { try { localStorage.setItem('descubrir:filtros', JSON.stringify(filtros)); } catch { /* */ } };
  const estDe = (dom) => (estados[dom] = estados[dom] || { fase: 'intro', n: 0, qs: [], i: 0, resp: [], gusto: null, res: null, error: '', anadidos: new Set(), cargando: false, busqueda: 0 });

  // Tus títulos (para no proponerte lo que ya tienes)
  function mios(dom) {
    const ks = D.tipos().filter((k) => (dom === 0 ? k.isGame : dom === 1 ? k.isBook : k.isMovie || k.isTv));
    const s = new Set();
    for (const k of ks) for (const o of [...D.valorados(k), ...D.fichas(k)]) s.add(RV.normName(o.name));
    return s;
  }

  async function empezar(dom, n) {
    const s = estDe(dom);
    s.cargando = true; s.error = ''; RV.refrescar();
    try {
      s.qs = await elegir(dom, n);
      Object.assign(s, { fase: 'preguntas', n, i: 0, resp: [], gusto: null, res: null, cargando: false });
    } catch (e) { s.cargando = false; s.error = RV.limpiaError(e); }
    RV.refrescar();
  }
  function responder(dom, efecto) {
    const s = estDe(dom);
    s.resp = [...s.resp, efecto || {}];
    if (s.i + 1 >= s.qs.length) terminar(dom); else s.i += 1;
    RV.refrescar();
  }
  function atras(dom) {
    const s = estDe(dom);
    if (s.i > 0) { s.i -= 1; s.resp = s.resp.slice(0, -1); } else s.fase = 'intro';
    RV.refrescar();
  }
  function terminar(dom) {
    const s = estDe(dom);
    const g = new Gusto();
    for (const e of s.resp) g.sumar(e);
    s.gusto = g;
    s.fase = 'resultados';
    buscar(dom);
  }
  async function buscar(dom) {
    const s = estDe(dom);
    const n = ++s.busqueda;
    s.res = null; s.error = '';
    RV.refrescar();
    try {
      const m = mios(dom);
      const r = dom === 0 ? await juegos(s.gusto, m, filtros) : dom === 1 ? await libros(s.gusto, m) : await pantalla(s.gusto, m);
      if (n === s.busqueda) s.res = r;
    } catch (e) {
      if (n === s.busqueda) { s.error = RV.limpiaError(e); s.res = []; }
    }
    if (n === s.busqueda) RV.refrescar();
  }

  // ---------------------------------------------------------------- Añadir lo descubierto a tu lista
  async function anadir(dom, x) {
    const s = estDe(dom);
    const clave = `${x.tv}${x.id}`;
    if (!D.conCuenta()) { RV.aviso('Entra con tu cuenta de Atrio (en Ajustes) para guardar fichas.', 'error'); return; }
    const k = dom === 0 ? D.tipo(0) : dom === 1 ? D.tipo(1) : D.tipos().find((t) => (x.tv ? t.isTv : t.isMovie));
    if (!k) { RV.aviso(`No tienes la categoría de ${x.tv ? 'series' : 'películas'}: créala en el móvil y vuelve a probar.`, 'error'); return; }
    const yaEsta = (id, name) => [...D.valorados(k), ...D.fichas(k)].some((o) => Number(o.id) === Number(id) || RV.normName(o.name) === RV.normName(name));
    if (yaEsta(x.id, x.titulo)) { s.anadidos.add(clave); RV.aviso(`Ya tenías ${x.titulo}`); RV.refrescar(); return; }
    RV.aviso(`Añadiendo ${x.titulo}…`);
    try {
      let base;
      if (dom === 0) {
        // Como en el móvil: el juego se busca en SteamGridDB (su id es el de la ficha) para la portada, el fondo y el logo
        const hits = await RV.api.sgdbSearch(x.titulo);
        const hit = (hits || []).find((h0) => RV.normName(h0.name) === RV.normName(x.titulo)) || (hits || [])[0];
        if (!hit) throw new Error(`No encuentro ${x.titulo} en SteamGridDB.`);
        if (yaEsta(hit.id, hit.name)) { s.anadidos.add(clave); RV.aviso(`Ya tenías ${x.titulo}`); RV.refrescar(); return; }
        const art = await RV.api.ronronArt(hit.id).catch(() => ({ cover: '', hero: '', logo: '' }));
        base = { id: Number(hit.id), name: hit.name, cover: art.cover || x.portada || '', hero: art.hero || '', logo: art.logo || '' };
      } else if (dom === 1) {
        base = { id: x.id, name: x.titulo, cover: x.portada, creator: x.creador, pages: x.paginas || 0 };
      } else if (x.tv) {
        // Las series del móvil van con TVmaze: se busca la misma allí (si no aparece, se queda la de TMDB)
        let id = x.id; let cover = x.portada;
        try {
          const hs = await RV.red.series(x.original || x.titulo);
          const h0 = hs.find((y) => RV.normName(y.name) === RV.normName(x.original) && (!x.ano || y.sub.startsWith(x.ano)))
            || hs.find((y) => RV.normName(y.name) === RV.normName(x.titulo)) || hs[0];
          if (h0) { id = h0.id; if (!cover) cover = h0.cover; }
        } catch { /* sin TVmaze: con el id de TMDB */ }
        if (yaEsta(id, x.titulo)) { s.anadidos.add(clave); RV.aviso(`Ya tenías ${x.titulo}`); RV.refrescar(); return; }
        base = { id, name: x.titulo, cover, creator: await RV.red.creadores(id).catch(() => '') };
      } else {
        base = { id: x.id, name: x.titulo, cover: x.portada, creator: await RV.red.director(x.id).catch(() => '') };
      }
      const item = D.completarFicha({ ...base, status: 0, entries: [], memos: [] });
      if (await RV.editar({ op: 'savePlay', kind: k.id, item }, `${item.name}, a tu lista`)) s.anadidos.add(clave);
    } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
    RV.refrescar();
  }

  // ---------------------------------------------------------------- Ficha de un resultado
  function abrir(dom, x) {
    const s = estDe(dom);
    const texto = h('p.texto', x.resumen && dom !== 0 ? x.resumen : '');
    const mc = h('div.desc-mc');
    const pintarNota = (v) => {
      mc.replaceChildren();
      if (dom === 0) {
        mc.append(h(`span.cuadro.${v >= 75 ? 'verde' : v >= 50 ? 'amarillo' : v > 0 ? 'rojo' : 'nada'}`, v > 0 ? String(v) : '–'),
          h('span.versal', v > 0 ? 'Metacritic' : 'Sin nota en Metacritic'));
      } else if (x.nota > 0) {
        mc.append(h('span.cuadro.tinta', dom === 1 ? `★ ${x.nota.toFixed(1).replace('.', ',')}` : x.nota.toFixed(1).replace('.', ',')),
          h('span.versal', dom === 1 ? 'Open Library' : 'TMDB'));
      }
    };
    pintarNota(dom === 0 ? Math.round(x.nota * 10) : x.nota);
    if (dom === 0) {
      texto.textContent = 'Cargando el resumen…';
      fichaJuego(x.id).then((f) => {
        texto.textContent = f.texto || 'RAWG no tiene resumen de este juego.';
        if (f.metacritic) pintarNota(f.metacritic);
      }).catch(() => { texto.textContent = 'No se ha podido cargar el resumen.'; });
    }
    const hecho = s.anadidos.has(`${x.tv}${x.id}`);
    RV.dialogo({
      ante: [QUE[dom], x.ano, `${x.encaje} % para ti`].filter(Boolean).join(' · '), titulo: x.titulo, ancho: true,
      cuerpo: [
        x.creador ? h('p.versal', x.creador) : null,
        x.original && RV.normName(x.original) !== RV.normName(x.titulo) ? h('p.cursiva', x.original) : null,
        mc, texto,
        dom === 0 ? h('p.versal', { style: { marginTop: '8px' } }, 'El resumen viene de RAWG, en inglés.') : null,
      ],
      acciones: ['hueco', { texto: 'Cerrar', fn: (d) => d.cerrar() },
        { texto: hecho ? 'Ya está en tu lista' : 'Añadir a mi lista', principal: !hecho, fn: async (d) => { if (!hecho) { d.cerrar(); await anadir(dom, x); } } }],
      fueraCierra: true,
    });
  }

  // ---------------------------------------------------------------- Piezas
  function pestanas(dom) {
    return h('div.pestanas', { role: 'tablist' }, [0, 1, 2].map((d) => h('button.pestana', {
      type: 'button', role: 'tab', 'aria-selected': String(d === dom), onclick: () => RV.ir({ v: 'descubrir', dom: d }, { reemplazar: true }),
    }, d === 0 ? 'Juegos' : d === 1 ? 'Libros' : 'Pelis y series')));
  }

  // Filtros de juegos: años (de 1980 a hoy) y plataformas (ninguna = todas)
  function bloqueFiltros(alCambiar) {
    const hoy = new Date().getFullYear();
    const [a0, b0] = filtros.anos || [ANO_MIN, hoy];
    const desde = h('input', { type: 'number', min: String(ANO_MIN), max: String(hoy), value: String(a0), 'aria-label': 'Desde el año' });
    const hasta = h('input', { type: 'number', min: String(ANO_MIN), max: String(hoy), value: String(b0), 'aria-label': 'Hasta el año' });
    const aplicarAnos = () => {
      let a = Math.round(Number(desde.value) || ANO_MIN); let b = Math.round(Number(hasta.value) || hoy);
      a = Math.max(ANO_MIN, Math.min(hoy, a)); b = Math.max(ANO_MIN, Math.min(hoy, b));
      if (a > b) [a, b] = [b, a];
      filtros.anos = a <= ANO_MIN && b >= hoy ? null : [a, b];
      guardarFiltros(); alCambiar();
    };
    for (const i of [desde, hasta]) { i.addEventListener('change', aplicarAnos); i.addEventListener('keydown', (e) => { if (e.key === 'Enter') aplicarAnos(); }); }
    const atajos = [['Cualquiera', null], ['Antes de 2000', [ANO_MIN, 1999]], ['2000–2009', [2000, 2009]], ['2010–2019', [2010, 2019]], [`2020–${hoy}`, [2020, hoy]], ['Este año', [hoy, hoy]]];
    const igual = (x, y) => JSON.stringify(x) === JSON.stringify(y);
    return h('div.desc-filtros',
      h('div.fila',
        h('span.versal.k', 'Años'),
        h('label.anos', desde, h('span', '—'), hasta),
        h('div.chips', atajos.map(([n, v]) => h('button.enlace', {
          type: 'button', 'aria-pressed': String(igual(filtros.anos, v)), onclick: () => { filtros.anos = v; guardarFiltros(); alCambiar(); },
        }, n)))),
      h('div.fila',
        h('span.versal.k', 'Plataformas'),
        h('div.chips',
          h('button.enlace', { type: 'button', 'aria-pressed': String(!filtros.plats.length), onclick: () => { filtros.plats = []; guardarFiltros(); alCambiar(); } }, 'Todas'),
          PLATS.map(([id, n]) => h('button.enlace', {
            type: 'button', 'aria-pressed': String(filtros.plats.includes(id)),
            onclick: () => { filtros.plats = filtros.plats.includes(id) ? filtros.plats.filter((p) => p !== id) : [...filtros.plats, id]; guardarFiltros(); alCambiar(); },
          }, n)))));
  }

  RV.vistas = RV.vistas || {};
  RV.vistas.descubrir = (r) => {
    const dom = [0, 1, 2].includes(Number(r.dom)) ? Number(r.dom) : 0;
    const s = estDe(dom);
    const root = h(`div.hoja.descubrir.fase-${s.fase}`);

    if (s.fase === 'intro') {
      root.append(h('header.cabeza',
        h('div.ante', `Descubrir · ${QUE[dom]}`),
        h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, TITULO[dom]), h('div.herramientas', pestanas(dom))),
        h('div.filete'),
        h('p.entradilla', `Unas pocas preguntas, de más de trescientas y nunca las mismas, y una lista de ${dom === 0 ? 'juegos' : dom === 1 ? 'libros' : 'películas y series'} hecha a tu medida.`)));
      if (dom === 0) root.append(h('section.desc-bloque', RV.seccion('Antes de empezar, si quieres'), bloqueFiltros(() => RV.refrescar())));
      if (dom === 0 && !RV.red.rawgClave()) root.append(RV.vacio('Ojo: hace falta tu clave gratuita de RAWG. Ponla en el móvil (Ajustes → Servicios y copias) y llegará sola a este equipo.'));
      if (dom === 2 && !RV.red.tmdbClave()) root.append(RV.vacio('Ojo: hace falta tu clave gratuita de TMDB. Ponla en el móvil (Ajustes → Servicios y copias) y llegará sola a este equipo.'));
      root.append(h('section.desc-bloque', RV.seccion('¿Cuántas preguntas?'),
        h('div.desc-n', [[5, 'Rápido'], [7, 'Normal'], [10, 'A fondo']].map(([n, l]) => h(`button${n === 7 ? '.acento' : ''}`, {
          type: 'button', disabled: s.cargando, onclick: () => empezar(dom, n),
        }, h('span.cifra', String(n)), h('span.versal', l))))));
      if (s.error) root.append(RV.vacio(s.error));
      return root;
    }

    if (s.fase === 'preguntas') {
      const q = s.qs[s.i];
      if (!q) { s.fase = 'intro'; return RV.vistas.descubrir(r); }
      root.append(h('div.desc-progreso',
        h('button.enlace', { type: 'button', onclick: () => atras(dom) }, '← Atrás'),
        h('span.hueco'),
        h('span.versal', `Pregunta ${s.i + 1} de ${s.qs.length}`)),
      h('div.desc-barra', h('span', { style: { width: `${((s.i + 1) * 100) / s.qs.length}%` } })));
      root.append(h('div.ante', { style: { marginTop: '26px' } }, QUE[dom]),
        h('h1.desc-pregunta.ajusta', { 'data-min': '28' }, q.q),
        h('ol.desc-respuestas', q.a.map((a, j) => h('li', h('button', { type: 'button', onclick: () => responder(dom, a.e), title: `Tecla ${String.fromCharCode(65 + j)}` },
          h('span.letra', String.fromCharCode(65 + j)), h('span.t', a.t))))),
        h('button.enlace.apagado', { type: 'button', style: { marginTop: '16px' }, onclick: () => responder(dom, {}), title: 'Tecla espacio' }, 'Me da igual, siguiente →'),
        h('p.versal', { style: { marginTop: '22px', color: 'var(--gris)' } }, 'Atajos: A, B, C… para responder · espacio para saltar · Esc para salir'));
      return root;
    }

    // Resultados
    const res = s.res;
    root.append(h('header.cabeza',
      h('div.ante', res == null ? 'Buscando…' : `${res.length} resultados · ${QUE[dom]}`),
      h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, 'Para ti'),
        h('div.herramientas',
          h('button.enlace.acento', { type: 'button', onclick: () => empezar(dom, s.n || 7) }, 'Otra ronda ↻'),
          h('button.enlace', { type: 'button', onclick: () => { s.fase = 'intro'; RV.refrescar(); } }, 'Empezar de cero'))),
      h('div.filete'),
      h('p.entradilla', resumen(s.gusto, dom, dom === 0 ? filtros : null).join(' · ') || 'A tu medida')));
    if (dom === 0) root.append(h('details.desc-bloque', { open: filtros.anos || filtros.plats.length ? true : null },
      h('summary.enlace.acento', 'Filtrar por año y plataforma'), bloqueFiltros(() => buscar(dom))));
    if (res == null) root.append(h('p.vacio.grande', 'Buscando lo que encaja contigo…'));
    else if (s.error) root.append(RV.vacio(s.error, true));
    else if (!res.length) root.append(RV.vacio('No ha salido nada con esa mezcla. Prueba otra ronda (o afloja los filtros).', true));
    else {
      root.append(h('ol.desc-res', res.map((x, i) => {
        const hecho = s.anadidos.has(`${x.tv}${x.id}`);
        return h('li',
          h('button.ver', { type: 'button', onclick: () => abrir(dom, x) },
            h('span.num', String(i + 1).padStart(2, '0')),
            h('span.cuerpo',
              h('span.tit', x.titulo),
              h('span.versal', [x.ano, dom === 2 ? (x.tv ? 'Serie' : 'Película') : x.creador].filter(Boolean).join(' · '))),
            h('span.encaje', h('b', String(x.encaje)), h('span.versal', '% para ti'))),
          h('button.enlace.acento.anadir', { type: 'button', disabled: hecho, onclick: () => anadir(dom, x) }, hecho ? 'En tu lista ✓' : 'A mi lista +'));
      })));
    }
    return root;
  };

  // Teclado en las preguntas: A, B, C… responden; espacio salta
  window.addEventListener('keydown', (e) => {
    const r = RV.ruta && RV.ruta();
    if (!r || r.v !== 'descubrir' || RV.hayDialogo() || e.ctrlKey || e.metaKey || e.altKey) return;
    const act = document.activeElement;
    if (act && (act.tagName === 'INPUT' || act.tagName === 'TEXTAREA')) return;
    const dom = [0, 1, 2].includes(Number(r.dom)) ? Number(r.dom) : 0;
    const s = estDe(dom);
    if (s.fase !== 'preguntas') return;
    const q = s.qs[s.i];
    if (!q) return;
    if (e.key === ' ') { e.preventDefault(); responder(dom, {}); return; }
    const j = e.key.length === 1 ? e.key.toUpperCase().charCodeAt(0) - 65 : -1;
    if (j >= 0 && j < q.a.length) { e.preventDefault(); responder(dom, q.a[j].e); }
  });
})(window.RV);
