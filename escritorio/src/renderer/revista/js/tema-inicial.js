'use strict';
/* global document, localStorage */
// Antes de pintar nada: el tema y el acento de la última vez (copia local de la configuración de Atrio),
// para que la revista no aparezca un instante con otros colores. Lo que manda es la configuración: base.js
// la vuelve a aplicar en cuanto la lee.
(function () {
  try {
    const t = JSON.parse(localStorage.getItem('revista:tema') || 'null');
    if (t && typeof t === 'object') {
      document.documentElement.dataset.tema = t.tema || 'auto';
      document.documentElement.dataset.acento = t.acento || 'rojo';
    }
  } catch { /* sin almacenamiento local: se aplica al leer la configuración */ }
})();
