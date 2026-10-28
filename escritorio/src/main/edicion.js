'use strict';
// La edición «solo revista»: Atrio sin el lanzador. Es lo que se instala en el Mac (sin Modo Juegos, sin «Tu PC», sin abrir
// juegos, sin streaming, sin controlar la máquina desde el móvil): la revista, tu cuenta y Escribir, lo mismo que verías en
// el móvil. En Windows y Linux sigue todo como siempre. Para probarla en Linux: ATRIO_SOLO_REVISTA=1.
module.exports = { SOLO_REVISTA: process.platform === 'darwin' || process.env.ATRIO_SOLO_REVISTA === '1' };
