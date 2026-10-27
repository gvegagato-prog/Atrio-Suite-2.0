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
      // (un fondo de color: se aplica entero ya, para que no haya un destello con el papel; base.js lo repite igual)
      if (t.fondo) {
        const osc = { granate: 1, bosque: 1, marino: 1, petroleo: 1, berenjena: 1, chocolate: 1, pizarra: 1 }[t.fondo] === 1;
        document.documentElement.dataset.fondo = t.fondo;
        document.documentElement.dataset.tema = osc ? 'oscuro' : 'claro';
        if ((t.acento || 'rojo') === 'rojo') document.documentElement.dataset.acento = 'fondo';
      }
    }
  } catch { /* sin almacenamiento local: se aplica al leer la configuración */ }
})();
