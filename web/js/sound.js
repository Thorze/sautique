'use strict';
// Sons et musiques avec la Web Audio API (équivalent de Sounds.kt).
// Sur iPhone, le son n'est autorisé qu'après un premier appui : unlock() est appelé à chaque toucher.

const SFX = {
  JUMP: ['saut'], LAND: ['atterrissage'], GREAT: ['great'], PERFECT: ['perfect', 'oh_yeah', 'ifeelgood'],
  SCARE: ['limite', 'limite2', 'limite3'], FALL: ['chute'], CRUMBLE: ['effondrement'],
  BONUS_WIN: ['bonus_gagne'], BONUS_LOSE: ['bonus_rate'], TAP: ['tape'], STAR: ['etoile'], COIN: ['piece'],
  WHEEL_TICK: ['roue'], UNLOCK: ['congratulations', 'deblocage'], REVIVE: ['ange'], CLICK: ['clic'],
};
// Fichiers réellement présents dans web/sons/
const AVAILABLE = new Set(['bonus', 'boucle1', 'boucle2', 'boucle3', 'boucle4', 'chute', 'congratulations', 'gameover2',
  'great', 'ifeelgood', 'limite', 'limite2', 'limite3', 'oh_yeah', 'perfect']);
const LOOPS = ['boucle1', 'boucle2', 'boucle3', 'boucle4'];

const sounds = (() => {
  let ctx = null, sfxGain = null, musicGain = null;
  const buffers = new Map();   // nom -> AudioBuffer
  const loading = new Map();   // nom -> Promise
  let mode = 'playlist', musicSrc = null, loopIndex = -1, repeatsLeft = 0, musicToken = 0, paused = true;
  let soundVolume = store.getFloat('sound_volume', 0.8);
  let musicVolume = store.getFloat('music_volume', 0.5);

  function load(name) {
    if (!AVAILABLE.has(name) || !ctx) return Promise.resolve(null);
    if (buffers.has(name)) return Promise.resolve(buffers.get(name));
    if (!loading.has(name)) {
      loading.set(name, fetch('sons/' + name + '.mp3').then((r) => r.arrayBuffer())
        .then((data) => new Promise((ok, ko) => ctx.decodeAudioData(data, ok, ko)))
        .then((buf) => { buffers.set(name, buf); return buf; })
        .catch(() => null));
    }
    return loading.get(name);
  }

  function unlock() {
    if (!ctx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      ctx = new AC();
      sfxGain = ctx.createGain(); sfxGain.gain.value = soundVolume; sfxGain.connect(ctx.destination);
      musicGain = ctx.createGain(); musicGain.gain.value = musicVolume; musicGain.connect(ctx.destination);
      // Effets chargés tout de suite (petits fichiers)
      Object.values(SFX).flat().forEach((n) => load(n));
      load('gameover2');
    }
    if (ctx.state === 'suspended') ctx.resume();
    if (paused) { paused = false; startMode(); }
  }

  function playBuffer(buf, gainNode, volume = 1, rate = 1) {
    const src = ctx.createBufferSource();
    src.buffer = buf;
    src.playbackRate.value = clamp(rate, 0.5, 2);
    const g = ctx.createGain(); g.gain.value = volume;
    src.connect(g); g.connect(gainNode);
    src.start();
    return src;
  }

  function has(id) { return SFX[id].some((n) => AVAILABLE.has(n)); }

  function play(id, volume = 1, rate = 1) {
    if (!ctx || soundVolume <= 0) return;
    const names = SFX[id].filter((n) => AVAILABLE.has(n));
    if (!names.length) return;
    const name = names[rndInt(names.length)];
    const buf = buffers.get(name);
    if (buf) playBuffer(buf, sfxGain, volume, rate);
  }

  function stopMusic() {
    musicToken++;
    if (musicSrc) { try { musicSrc.onended = null; musicSrc.stop(); } catch (e) { /* déjà arrêté */ } }
    musicSrc = null;
  }

  function startMode() {
    if (!ctx || paused || musicSrc) return;
    if (mode === 'playlist') nextLoop();
    else if (mode === 'bonus') {
      if (musicVolume <= 0) return;
      const token = ++musicToken;
      load('bonus').then((buf) => {
        if (!buf || token !== musicToken || mode !== 'bonus') return;
        musicSrc = playBuffer(buf, musicGain); musicSrc.loop = true;
      });
    }
  }

  /** Une autre boucle au hasard, répétée 3 ou 4 fois. */
  function nextLoop() {
    if (musicVolume <= 0) return;
    let idx = rndInt(LOOPS.length);
    if (LOOPS.length > 1 && idx === loopIndex) idx = (idx + 1 + rndInt(LOOPS.length - 1)) % LOOPS.length;
    loopIndex = idx;
    repeatsLeft = 3 + rndInt(2);
    const token = ++musicToken;
    load(LOOPS[idx]).then((buf) => {
      if (!buf || token !== musicToken || mode !== 'playlist' || paused) return;
      const playOnce = () => {
        musicSrc = playBuffer(buf, musicGain);
        musicSrc.onended = () => {
          if (token !== musicToken) return;
          musicSrc = null;
          repeatsLeft--;
          if (repeatsLeft > 0) playOnce(); else nextLoop();
        };
      };
      playOnce();
    });
    // Libère la mémoire des boucles qui ne jouent pas
    LOOPS.forEach((n, i) => { if (i !== idx) { buffers.delete(n); loading.delete(n); } });
  }

  return {
    unlock, has, play,
    get soundVolume() { return soundVolume; },
    set soundVolume(v) { soundVolume = clamp(v, 0, 1); store.set('sound_volume', soundVolume); if (sfxGain) sfxGain.gain.value = soundVolume; },
    get musicVolume() { return musicVolume; },
    set musicVolume(v) {
      musicVolume = clamp(v, 0, 1); store.set('music_volume', musicVolume);
      if (musicGain) musicGain.gain.value = musicVolume;
      if (musicVolume <= 0) stopMusic(); else startMode();
    },
    playPlaylist() { if (mode === 'playlist' && musicSrc) return; mode = 'playlist'; stopMusic(); startMode(); },
    playBonusMusic() { mode = 'bonus'; stopMusic(); startMode(); },
    playGameOver(onDone) {
      mode = 'gameover'; stopMusic();
      const buf = buffers.get('gameover2');
      if (!ctx || !buf || soundVolume <= 0) { mode = 'playlist'; onDone(); startMode(); return; }
      const src = playBuffer(buf, sfxGain);
      const token = musicToken;
      src.onended = () => { if (token !== musicToken) return; mode = 'playlist'; onDone(); startMode(); };
    },
    pause() { paused = true; stopMusic(); if (ctx) ctx.suspend(); },
    resume() { if (!ctx) return; ctx.resume(); paused = false; if (mode === 'gameover') mode = 'playlist'; startMode(); },
  };
})();
