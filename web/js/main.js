'use strict';
// Démarrage : canvas plein écran, boucle d'animation, toucher, pause en arrière-plan.

const WEB_VERSION = '1.0';

(function main() {
  const el = document.getElementById('game');
  const ctx = el.getContext('2d');
  const canvas = new Canvas(ctx);
  const probe = document.getElementById('safe-probe');

  function resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, 3);
    // Sur ordinateur (écran large), le jeu garde un format portrait centré
    const ch = window.innerHeight, cw = Math.min(window.innerWidth, Math.round(ch * 0.62));
    el.width = Math.round(cw * dpr); el.height = Math.round(ch * dpr);
    el.style.width = cw + 'px'; el.style.height = ch + 'px';
    el.style.left = Math.round((window.innerWidth - cw) / 2) + 'px';
    const cs = getComputedStyle(probe);
    insetTop = (parseFloat(cs.paddingTop) || 0) * dpr;
    insetBottom = (parseFloat(cs.paddingBottom) || 0) * dpr;
    const first = w === 0;
    if (first) onSizeChanged(el.width, el.height);
    else { w = el.width; h = el.height; k = w / 9.5; scorePaint.textSize = w * 0.24; skyTop = 0; loadLook(); resetGameIfIdle(); }
  }
  function resetGameIfIdle() { if (state === State.READY && score === 0) resetGame(); else updateCameraTarget(); }

  const pos = (e) => {
    const r = el.getBoundingClientRect(), dpr = el.width / r.width;
    return [(e.clientX - r.left) * dpr, (e.clientY - r.top) * dpr];
  };
  let activePointer = null;
  if (!window.PointerEvent) {
    // Anciens iPhone (iOS 12 et avant) : pas d'événements « pointer », on utilise le toucher
    const tpos = (t) => pos(t);
    let touchId = null;
    el.addEventListener('touchstart', (e) => {
      e.preventDefault();
      if (touchId !== null) return;
      const t = e.changedTouches[0]; touchId = t.identifier;
      const [x, y] = tpos(t); onPointerDown(x, y);
    }, { passive: false });
    el.addEventListener('touchmove', (e) => {
      e.preventDefault();
      for (const t of e.changedTouches) if (t.identifier === touchId) { const [x, y] = tpos(t); onPointerMove(x, y); }
    }, { passive: false });
    const tend = (e) => { for (const t of e.changedTouches) if (t.identifier === touchId) { touchId = null; onPointerUp(); } };
    el.addEventListener('touchend', tend);
    el.addEventListener('touchcancel', tend);
    el.addEventListener('mousedown', (e) => { const [x, y] = pos(e); onPointerDown(x, y); });
    window.addEventListener('mouseup', () => onPointerUp());
  }
  el.addEventListener('pointerdown', (e) => {
    e.preventDefault();
    if (activePointer !== null) return;
    activePointer = e.pointerId;
    try { el.setPointerCapture(e.pointerId); } catch (err) { /* ignoré */ }
    const [x, y] = pos(e); onPointerDown(x, y);
  });
  el.addEventListener('pointermove', (e) => { if (e.pointerId === activePointer) { const [x, y] = pos(e); onPointerMove(x, y); } });
  const up = (e) => { if (e.pointerId !== activePointer) return; activePointer = null; onPointerUp(); };
  el.addEventListener('pointerup', up);
  el.addEventListener('pointercancel', up);
  // Empêche le zoom, le défilement et le menu contextuel pendant le jeu
  ['touchstart', 'touchmove', 'gesturestart', 'contextmenu'].forEach((t) => el.addEventListener(t, (e) => e.preventDefault(), { passive: false }));

  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { pauseGame(); sounds.pause(); } else { lastFrame = 0; sounds.resume(); }
  });
  // Molette (ordinateur) : défilement du classement
  el.addEventListener('wheel', (e) => {
    if (state !== State.LEADERBOARD) return;
    e.preventDefault();
    boardScroll = clamp(boardScroll + e.deltaY * (el.width / el.getBoundingClientRect().width), 0, boardMaxScroll);
  }, { passive: false });
  window.addEventListener('resize', resize);

  resize();

  function frame(ts) {
    const dt = lastFrame === 0 ? 0 : clamp((ts - lastFrame) / 1000, 0, 0.05);
    lastFrame = ts;
    try {
      update(dt);
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      draw(canvas);
    } catch (err) {
      showError(err);
    }
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);

  if (!profile.pseudo) setTimeout(() => askPseudo(true), 300);
  else { syncOnline(); if (!profile.customized) openCustomize(); }
})();
