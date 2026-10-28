'use strict';
/* global window, module */
// La guía para escribir una novela paso a paso (la misma que EscribirGuia.kt del móvil): los pasos, sus textos y cómo se
// sabe lo que ya llevas hecho. Funciones puras: se prueban con node (herramientas/prueba-escribir-texto.js, con los
// casos de herramientas/casos/guia.json, los mismos que el móvil) y se usan en la revista.
//
// Lo que se guarda en la obra (y se sincroniza como cualquier otro campo): gOn = 1 si la guía está activada, y g1…g11 con
// 1 (hecho a mano) o 2 (saltado) y, solo en el paso del reposo, g7t con el momento en que empezó (milisegundos).
(function (raiz) {
  const G = {};

  // Cada paso: id, título, qué es, cómo, el botón de acción, y si se marca solo (auto) cuando detecta lo hecho
  G.PASOS = [
    { id: 1, titulo: 'Una idea pequeña', auto: true, accion: 'Escribir mi frase',
      que: 'No hace falta tener la historia entera. Basta una frase: quién quiere qué y qué se lo impide.',
      como: ['Escríbela en una sola frase, corta y sin nombres propios.', 'Si solo tienes una imagen o una escena, anótala igual: de ahí sale la frase.', 'No busques la idea perfecta: se puede cambiar más adelante.'],
      hecho: 'Tienes tu frase.' },
    { id: 2, titulo: 'Tus personajes', auto: true, accion: 'Crear un personaje',
      que: 'Para cada protagonista: qué quiere, qué se lo impide y qué pasa si fracasa.',
      como: ['Empieza solo por quien lleva la historia.', 'Rellena «Quiere», «Se lo impide» y «Si fracasa» en su ficha.', 'Los demás personajes pueden esperar a que los necesites.'],
      hecho: 'Ya tienes al menos un personaje.' },
    { id: 3, titulo: 'Un esquema ligero', auto: true, accion: 'Crear los tres actos',
      que: 'Una lista corta de lo que pasa, no un guion detallado. Lo habitual son tres actos: planteamiento, nudo y desenlace.',
      como: ['Resume en una o dos líneas qué pasa en cada parte.', 'Después divide cada parte en capítulos, cada uno con su resumen.', 'Si planificar te agobia, haz un esquema mínimo y sigue: se puede ampliar mientras escribes.'],
      hecho: 'Tienes el esquema (al menos tres resúmenes).' },
    { id: 4, titulo: 'Promesa, progreso y recompensa', auto: false, accion: 'Anotar mis respuestas',
      que: 'Una idea de las clases de Brandon Sanderson: el principio promete (tono, género, conflicto), el medio avanza y el final cumple lo prometido.',
      como: ['¿Qué le prometes al lector en las primeras páginas?', '¿Qué cambia en cada tramo para que el medio avance y no dé vueltas?', '¿Cómo cumple el final esas promesas?', 'No es una plantilla rígida: es una brújula.'],
      hecho: 'Ya lo tienes pensado.' },
    { id: 5, titulo: 'Un hábito realista', auto: true, accion: 'Poner mi meta diaria',
      que: 'Una meta pequeña que cumplas todos los días pesa más que una grande que abandones.',
      como: ['Elige una cantidad que te parezca casi demasiado fácil (por ejemplo 300 palabras; es una sugerencia de Atrio, no de las guías).', 'Si puedes, escribe a la misma hora.', 'Un sprint de 10 o 25 minutos ayuda a empezar.'],
      hecho: 'Ya has escrito en tres días distintos.' },
    { id: 6, titulo: 'El primer borrador', auto: true, accion: 'Seguir escribiendo',
      que: 'Escribir hasta el final sin corregir. Será imperfecto, y está bien: se arregla después.',
      como: ['No releas ni corrijas mientras escribes: apunta una nota y sigue.', 'Si te atascas, salta a una escena que sí sepas escribir.', 'El objetivo es llegar al final, no que quede bonito.'],
      hecho: 'Has llegado a la meta de palabras.' },
    { id: 7, titulo: 'Dejarlo reposar', auto: false, accion: 'Empezar el reposo',
      que: 'Aparta el borrador unas semanas antes de releerlo, para leerlo casi como un lector (consejo de Atrio, sin una fuente concreta).',
      como: ['Pulsa «Empezar el reposo»: Atrio cuenta los días.', 'Mientras tanto no lo abras: lee, descansa o piensa en otra historia.', 'Cuando quieras volver, márcalo como hecho.'],
      hecho: 'Ya has descansado del borrador.' },
    { id: 8, titulo: 'Revisar lo grande', auto: false, accion: 'Ver el manuscrito',
      que: 'Primera pasada: lo que afecta a la historia. La gramática se deja para la siguiente.',
      como: ['Agujeros de trama: ¿algo no tiene sentido o se olvida por el camino?', 'Personajes: ¿cambian y quieren cosas claras?', 'Ritmo: ¿hay tramos que se arrastran o que van demasiado deprisa?', 'Marca cada escena como «Revisión» (y luego «Final») según la repasas.'],
      hecho: 'Has revisado la historia.' },
    { id: 9, titulo: 'Revisar lo pequeño', auto: false, accion: 'Ver el manuscrito',
      que: 'Segunda pasada: frases, gramática y repeticiones.',
      como: ['Lee en voz alta: lo que suena mal, suele fallar.', 'Cambia las palabras repetidas; el editor te da sinónimos en el menú del botón derecho (PC) o en «⋯» (móvil).', 'Mira la ortografía.'],
      hecho: 'Has revisado el texto.' },
    { id: 10, titulo: 'Lectores de prueba', auto: false, accion: 'Exportar el manuscrito',
      que: 'Que alguien lea tu novela y te diga qué funciona: lectores beta o un editor profesional.',
      como: ['Exporta el manuscrito en Word o PDF.', 'Pide a dos o tres personas que lo lean y te digan dónde se aburrieron o se perdieron.', 'Pregunta y escucha: no te defiendas.'],
      hecho: 'Ya te han leído.' },
    { id: 11, titulo: 'Corrección final y publicar', auto: false, accion: 'Exportar el manuscrito',
      que: 'Última lectura para las erratas y decidir si buscas editorial o publicas tú.',
      como: ['Corrige las erratas con calma.', 'El Word «manuscrito» sigue el formato que suelen pedir las editoriales, pero cada una tiene sus normas: léelas antes de enviar.', 'Decide: editorial o autopublicación.'],
      hecho: 'Tu novela está lista.' },
  ];
  G.FUENTES = 'Basada en guías de Grammarly, MasterClass y Escritor Flexible, el método Snowflake de Randy Ingermanson y las clases de Brandon Sanderson. Las cantidades y plazos son orientativos.';
  G.DIA = 86400000;

  // Los datos de una obra que la guía necesita (se juntan igual en el PC y en el móvil):
  //   obra { idea, gOn, g1…g11, g7t, meta },  personajes (fichas de tipo personaje), resumenes (capítulos y escenas con
  //   resumen, más los hitos de la línea), dias (días distintos con palabras escritas), palabras (de toda la obra), ahora
  // Devuelve por paso: 'hecho', 'saltado', 'curso' (el reposo empezado) o 'pendiente'; y por qué (a mano o solo).
  const num = (x) => { const n = Number(x); return Number.isFinite(n) ? n : 0; };
  G.automatico = (id, d) => {
    const o = d.obra || {};
    if (id === 1) return String(o.idea || '').trim() !== '';
    if (id === 2) return num(d.personajes) >= 1;
    if (id === 3) return num(d.resumenes) >= 3;
    if (id === 5) return num(d.dias) >= 3;
    if (id === 6) return num(o.meta) > 0 && num(d.palabras) >= num(o.meta);
    return false;
  };
  G.estados = (d) => G.PASOS.map((p) => {
    const o = d.obra || {};
    const g = num(o[`g${p.id}`]);
    if (g === 1) return { id: p.id, estado: 'hecho', aMano: true };
    if (g === 2) return { id: p.id, estado: 'saltado', aMano: true };
    if (G.automatico(p.id, d)) return { id: p.id, estado: 'hecho', aMano: false };
    if (p.id === 7 && num(o.g7t) > 0) return { id: p.id, estado: 'curso', aMano: false, dias: Math.max(0, Math.floor((num(d.ahora) - num(o.g7t)) / G.DIA)) };
    return { id: p.id, estado: 'pendiente', aMano: false };
  });
  // El primer paso que falta (el que toca ahora), o null si ya está todo
  G.siguiente = (est) => (est.find((e) => e.estado === 'pendiente' || e.estado === 'curso') || {}).id || null;
  G.hechos = (est) => est.filter((e) => e.estado === 'hecho').length;
  G.activa = (obra) => num((obra || {}).gOn) === 1;

  if (typeof module !== 'undefined' && module.exports) module.exports = G;
  else raiz.RV = Object.assign(raiz.RV || {}, { G });
})(typeof window !== 'undefined' ? window : globalThis);
