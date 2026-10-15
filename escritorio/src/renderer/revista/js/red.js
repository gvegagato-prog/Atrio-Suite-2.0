'use strict';
/* global window */
// Buscadores de la revista (Open Library, TMDB, TVmaze y RAWG), con los mismos ids que usa el móvil para que una
// ficha creada en el PC sea la misma que en el móvil. La página no puede salir a internet (CSP): las peticiones
// las hace el proceso principal (atrio.netJson), solo a estas webs.
(function (RV) {
  const N = {};
  RV.red = N;

  N.json = async (url, token) => {
    if (!RV.api.netJson) throw new Error('Esta versión de Atrio no puede buscar en internet.');
    return RV.api.netJson(url, token || '');
  };
  const enc = encodeURIComponent;

  // Las claves de TMDB y RAWG viajan con tu cuenta (las pusiste en el móvil: Ajustes → Servicios)
  N.claves = () => (RV.D.datos().keys || {});
  N.tmdbClave = () => String(N.claves().tmdb || '').trim();
  N.rawgClave = () => String(N.claves().rawg || '').trim();

  // TMDB: la clave v3 va en la dirección; el token de lectura («eyJ…»), en la cabecera
  N.tmdb = (path) => {
    const k = N.tmdbClave();
    if (!k) throw new Error('Falta tu clave de TMDB: ponla en el móvil (Perfil → Ajustes → Servicios) y llegará sola.');
    const token = k.startsWith('eyJ');
    const sep = path.includes('?') ? '&' : '?';
    return N.json(`https://api.themoviedb.org/3${path}${token ? '' : `${sep}api_key=${enc(k)}`}`, token ? k : '');
  };

  // ---------------------------------------------------------------- Buscar para añadir
  // Cada resultado: { id, name, sub, cover, creator, pages }

  // Libros: Open Library (id = el número de la obra, como OpenLib.search del móvil)
  N.libros = async (q) => {
    const o = await N.json(`https://openlibrary.org/search.json?q=${enc(q.trim())}&fields=key,title,author_name,first_publish_year,cover_i,number_of_pages_median&limit=20`);
    return (o.docs || []).map((d) => {
      const id = Number(String(d.key || '').replace(/\D/g, ''));
      if (!id) return null;
      const author = (d.author_name || [])[0] || '';
      const pages = Number(d.number_of_pages_median) || 0;
      return {
        id, name: d.title || '', creator: author, pages,
        sub: [author, d.first_publish_year || '', pages ? `${pages} págs.` : ''].filter(Boolean).join(' · '),
        cover: d.cover_i ? `https://covers.openlibrary.org/b/id/${d.cover_i}-L.jpg` : '',
      };
    }).filter(Boolean);
  };

  // Películas: TMDB (id de TMDB, como Tmdb.search del móvil)
  N.pelis = async (q) => {
    const o = await N.tmdb(`/search/movie?query=${enc(q.trim())}&language=es-ES&include_adult=false`);
    return (o.results || []).map((m) => {
      if (!m.id) return null;
      const title = m.title || m.original_title || '';
      const orig = m.original_title || '';
      return {
        id: Number(m.id), name: title, creator: '', pages: 0,
        sub: [String(m.release_date || '').slice(0, 4), orig && RV.normName(orig) !== RV.normName(title) ? orig : ''].filter(Boolean).join(' · '),
        cover: m.poster_path ? `https://image.tmdb.org/t/p/w500${m.poster_path}` : '',
      };
    }).filter(Boolean);
  };
  // El director (para la ficha)
  N.director = async (id) => {
    const o = await N.tmdb(`/movie/${id}/credits`);
    return [...new Set((o.crew || []).filter((c) => c.job === 'Director').map((c) => c.name).filter(Boolean))].slice(0, 2).join(', ');
  };

  // Series: TVmaze (id de TVmaze, como TvMaze.search del móvil; no necesita clave)
  N.series = async (q) => {
    const a = await N.json(`https://api.tvmaze.com/search/shows?q=${enc(q.trim())}`);
    return (Array.isArray(a) ? a : []).map((x) => x && x.show).filter((s) => s && s.id).map((s) => ({
      id: Number(s.id), name: s.name || '', creator: '', pages: 0,
      sub: [String(s.premiered || '').slice(0, 4), ((s.network || s.webChannel) || {}).name || ''].filter(Boolean).join(' · '),
      cover: (s.image && s.image.original) || '',
    }));
  };
  N.creadores = async (id) => {
    const a = await N.json(`https://api.tvmaze.com/shows/${id}/crew`);
    return [...new Set((Array.isArray(a) ? a : []).filter((c) => c.type === 'Creator').map((c) => c.person && c.person.name).filter(Boolean))].slice(0, 3).join(', ');
  };

  // El buscador que toca a cada categoría (null: a mano)
  N.buscadorDe = (k) => (k.isBook ? N.libros : k.isMovie ? N.pelis : k.isTv ? N.series : null);
  N.fuenteDe = (k) => (k.isBook ? 'Open Library' : k.isMovie ? 'TMDB' : k.isTv ? 'TVmaze' : '');
})(window.RV);
