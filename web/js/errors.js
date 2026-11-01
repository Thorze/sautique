'use strict';
// Affiche les erreurs JavaScript à l'écran (pour qu'un joueur puisse envoyer une capture).
(function () {
  let shown = false;
  window.showError = function (err) {
    if (shown) return;
    shown = true;
    const box = document.getElementById('error-box');
    if (!box) return;
    const msg = (err && (err.stack || err.message)) || String(err);
    document.getElementById('error-text').textContent =
      msg.slice(0, 600) + '\n\n' + navigator.userAgent;
    box.style.display = 'flex';
    try { console.error(err); } catch (e) { /* ignoré */ }
  };
  window.addEventListener('error', function (e) { window.showError(e.error || e.message); });
  window.addEventListener('unhandledrejection', function (e) { window.showError(e.reason); });
})();
