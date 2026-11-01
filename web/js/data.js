'use strict';
// Règles du jeu : skins, ciel, points, missions et profil (équivalents de Skins.kt, Theme.kt,
// Scoring.kt, Missions.kt et LocalScores.kt).

// ---------- Skins ----------
const Slot = {
  CHARACTER: { key: 'character', title: 'Persos' },
  SHAPE: { key: 'shape', title: 'Forme' },
  COLOR: { key: 'color', title: 'Couleur' },
  GLASSES: { key: 'glasses', title: 'Lunettes' },
  HAT: { key: 'hat', title: 'Chapeau' },
  SHOES: { key: 'shoes', title: 'Chaussures' },
  BACK: { key: 'back', title: 'Dos' },
  TRAIL: { key: 'trail', title: 'Traînée' },
};
const SLOTS = [Slot.CHARACTER, Slot.SHAPE, Slot.COLOR, Slot.GLASSES, Slot.HAT, Slot.SHOES, Slot.BACK, Slot.TRAIL];

const Skins = (() => {
  const NONE = 'none', RAINBOW = 'arcenciel', GALAXY = 'galaxie', ORIGINAL = 'origine';
  const I = (slot, id, label, cost, color = 0, price = 0) => ({ slot, id, label, cost, color, price, isShop: price > 0 });
  const items = [
    // Personnages complets (non personnalisables), achetés avec les pièces
    I(Slot.CHARACTER, NONE, 'Le mien', 0),
    I(Slot.CHARACTER, 'donut', 'Donut', 0, 0, 300), I(Slot.CHARACTER, 'bonhomme', 'Bonhomme de neige', 0, 0, 400),
    I(Slot.CHARACTER, 'sapin', 'Sapin de Noël', 0, 0, 500), I(Slot.CHARACTER, 'perenoel', 'Père Noël', 0, 0, 600),
    I(Slot.CHARACTER, 'hippo', 'Hippopotame', 0, 0, 700), I(Slot.CHARACTER, 'capybara', 'Capybara', 0, 0, 800),
    I(Slot.CHARACTER, 'girafe', 'Girafe', 0, 0, 900),

    I(Slot.SHAPE, 'cube', 'Cube', 0), I(Slot.SHAPE, 'boule', 'Boule', 150),
    I(Slot.SHAPE, 'cylindre', 'Cylindre', 500), I(Slot.SHAPE, 'tour', 'Tour', 1200),

    I(Slot.COLOR, 'orange', 'Orange', 0, 0xFFFF7A45), I(Slot.COLOR, 'bleu', 'Bleu', 0, 0xFF3F8EFC),
    I(Slot.COLOR, 'vert', 'Vert', 0, 0xFF3DBE6E), I(Slot.COLOR, 'rose', 'Rose', 0, 0xFFF06292),
    I(Slot.COLOR, 'violet', 'Violet', 60, 0xFF8E5CD9), I(Slot.COLOR, 'jaune', 'Jaune', 150, 0xFFFFC93C),
    I(Slot.COLOR, 'turquoise', 'Turquoise', 300, 0xFF26C6DA), I(Slot.COLOR, 'rouge', 'Rouge', 500, 0xFFE53950),
    I(Slot.COLOR, 'noir', 'Nuit', 800, 0xFF3A3F58), I(Slot.COLOR, 'or', 'Or', 1500, 0xFFE0B03C),
    I(Slot.COLOR, RAINBOW, 'Arc-en-ciel', 0, 0xFFFF5DA2, 600), I(Slot.COLOR, GALAXY, 'Galaxie', 0, 0xFF4B2C83, 900),

    I(Slot.GLASSES, NONE, 'Aucune', 0), I(Slot.GLASSES, 'rondes', 'Rondes', 100),
    I(Slot.GLASSES, 'soleil', 'Soleil', 400), I(Slot.GLASSES, 'coeur', 'Cœurs', 900),
    I(Slot.GLASSES, 'pixel', 'Pixel', 0, 0, 150), I(Slot.GLASSES, 'monocle', 'Monocle', 0, 0, 250),

    I(Slot.HAT, NONE, 'Aucun', 0), I(Slot.HAT, 'casquette', 'Casquette', 200),
    I(Slot.HAT, 'hautdeforme', 'Haut-de-forme', 700), I(Slot.HAT, 'couronne', 'Couronne', 2000),
    I(Slot.HAT, 'magicien', 'Magicien', 0, 0, 200), I(Slot.HAT, 'viking', 'Viking', 0, 0, 350),
    I(Slot.HAT, 'aureole', 'Auréole', 0, 0, 700), I(Slot.HAT, 'bonnet', 'Bonnet', 450),
    I(Slot.HAT, 'cowboy', 'Cowboy', 0, 0, 300),

    I(Slot.SHOES, NONE, 'Aucune', 0), I(Slot.SHOES, 'baskets', 'Baskets', 300),
    I(Slot.SHOES, 'bottes', 'Bottes', 850), I(Slot.SHOES, 'fusees', 'Fusées', 0, 0, 450),

    I(Slot.BACK, NONE, 'Rien', 0), I(Slot.BACK, 'cape', 'Cape', 250), I(Slot.BACK, 'sac', 'Sac à dos', 600),
    I(Slot.BACK, 'papillon', 'Ailes de papillon', 0, 0, 400), I(Slot.BACK, 'jetpack', 'Jetpack', 0, 0, 650),

    I(Slot.TRAIL, NONE, 'Aucune', 0), I(Slot.TRAIL, 'trait', 'Trait', 80), I(Slot.TRAIL, 'etoiles', 'Étoiles', 200),
    I(Slot.TRAIL, 'bulles', 'Bulles', 550), I(Slot.TRAIL, 'notes', 'Notes', 0, 0, 250),
    I(Slot.TRAIL, 'coeurs', 'Cœurs', 0, 0, 300), I(Slot.TRAIL, 'arcenciel', 'Arc-en-ciel', 0, 0, 500),
  ];
  const T = (id, label, color, price) => ({ id, label, color, price });
  const tints = [
    T(ORIGINAL, 'Origine', 0, 0), T('rouge', 'Rouge', 0xFFE53950, 60), T('bleu', 'Bleu', 0xFF3F8EFC, 60),
    T('vert', 'Vert', 0xFF3DBE6E, 60), T('jaune', 'Jaune', 0xFFFFC93C, 80), T('rose', 'Rose', 0xFFFF6FB0, 80),
    T('violet', 'Violet', 0xFF8E5CD9, 100), T('blanc', 'Blanc', 0xFFF4F4F4, 120), T('noir', 'Noir', 0xFF26262E, 120),
    T('or', 'Or', 0xFFE0B03C, 250),
  ];
  const bySlot = (slot) => items.filter((it) => it.slot === slot);
  return {
    NONE, RAINBOW, GALAXY, ORIGINAL, items, tints,
    tintable: new Set([Slot.GLASSES, Slot.HAT, Slot.SHOES, Slot.BACK]),
    tint: (id) => tints.find((t) => t.id === id) || tints[0],
    bySlot,
    def: (slot) => bySlot(slot)[0].id,
    find: (slot, id) => bySlot(slot).find((it) => it.id === id) || bySlot(slot)[0],
    unlockedBetween: (before, after) => items.filter((it) => !it.isShop && it.cost > before && it.cost <= after),
    nextUnlock: (points) => items.filter((it) => !it.isShop && it.cost > points).sort((a, b) => a.cost - b.cost)[0] || null,
  };
})();

// ---------- Ciel ----------
const Theme = (() => {
  const stops = [
    [0, 0xFFFDE2C8, 0xFFC9E4F5, 0, 'Matin'], [20, 0xFF7CC6F2, 0xFFE3F5FF, 0, 'Journée'],
    [40, 0xFFFF8E72, 0xFFFFD6A5, 0.1, 'Coucher de soleil'], [60, 0xFF0F1C3F, 0xFF35507A, 1, 'Nuit'],
    [80, 0xFF14203D, 0xFF2E8B74, 1, 'Aurore'], [100, 0xFF05010F, 0xFF3A1460, 1, 'Espace'],
  ];
  const BLEND = 8;
  function lerp(a, b, t) {
    const ch = (s) => { const ca = (a >>> s) & 255, cb = (b >>> s) & 255; return (clamp(Math.round(ca + (cb - ca) * t), 0, 255)) << s; };
    return (ch(24) | ch(16) | ch(8) | ch(0)) >>> 0;
  }
  function sky(score) {
    let i = 0;
    for (let j = 0; j < stops.length; j++) if (stops[j][0] <= score) i = j;
    const cur = stops[i], nxt = stops[i + 1];
    const mk = (s) => ({ top: s[1], bottom: s[2], night: s[3], name: s[4] });
    if (!nxt) return mk(cur);
    const start = nxt[0] - BLEND;
    if (score < start) return mk(cur);
    const t = clamp((score - start) / BLEND, 0, 1);
    return { top: lerp(cur[1], nxt[1], t), bottom: lerp(cur[2], nxt[2], t), night: cur[3] + (nxt[3] - cur[3]) * t, name: t < 0.5 ? cur[4] : nxt[4] };
  }
  return { sky, lerp };
})();

// ---------- Points ----------
const Scoring = {
  perfectPoints: (streak) => (streak >= 3 ? Math.min(streak - 1, 5) : 1),
  cursorPoints(pos) { const f = clamp(1 - Math.abs(pos), 0, 1); return Math.round(f * f * 10); },
  MINI_BONUS: 2,
};

// ---------- Profil (localStorage) ----------
const store = (() => {
  const P = 'sautique.';
  const safe = (fn, def) => { try { return fn(); } catch (e) { return def; } };
  return {
    get: (k) => safe(() => localStorage.getItem(P + k), null),
    set: (k, v) => safe(() => localStorage.setItem(P + k, String(v)), null),
    del: (k) => safe(() => localStorage.removeItem(P + k), null),
    getInt(k, def = 0) { const v = this.get(k); return v === null ? def : (parseInt(v, 10) || 0); },
    getFloat(k, def = 0) { const v = this.get(k); return v === null ? def : (parseFloat(v) || 0); },
    getBool(k, def) { const v = this.get(k); return v === null ? def : v === '1'; },
    setBool(k, v) { this.set(k, v ? '1' : '0'); },
    getSet(k) { const v = this.get(k); return new Set(v ? v.split('|').filter(Boolean) : []); },
    setSet(k, s) { this.set(k, [...s].join('|')); },
  };
})();

function uuid() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0; return (c === 'x' ? r : (r & 3) | 8).toString(16);
  });
}

const profile = {
  MAX_RUNS: 10,
  get playerId() { let id = store.get('player_id'); if (!id) { id = uuid(); store.set('player_id', id); } return id; },
  get pseudo() { return store.get('pseudo'); },
  set pseudo(v) { store.set('pseudo', v); },
  get best() { return store.getInt('best'); },
  get syncedPseudo() { return store.get('synced_pseudo'); },
  set syncedPseudo(v) { store.set('synced_pseudo', v); },
  get totalPoints() { return store.getInt('total_points'); },
  set totalPoints(v) { store.set('total_points', v); },
  get customized() { return store.getBool('customized', false); },
  set customized(v) { store.setBool('customized', v); },
  selected(slot) { return store.get('skin_' + slot.key) || Skins.def(slot); },
  select(slot, id) { store.set('skin_' + slot.key, id); },
  get coins() { return store.getInt('coins'); },
  set coins(v) { store.set('coins', Math.max(0, v)); },
  key(item) { return item.slot.key + ':' + item.id; },
  owns(item) { return !item.isShop || store.getSet('owned').has(this.key(item)); },
  buy(item) {
    if (this.owns(item) || this.coins < item.price) return false;
    this.coins -= item.price;
    const o = store.getSet('owned'); o.add(this.key(item)); store.setSet('owned', o); return true;
  },
  available(item) { return item.isShop ? this.owns(item) : item.cost <= this.totalPoints; },
  ownsTint(t) { return t.price === 0 || store.getSet('owned').has('tint:' + t.id); },
  buyTint(t) {
    if (this.ownsTint(t) || this.coins < t.price) return false;
    this.coins -= t.price;
    const o = store.getSet('owned'); o.add('tint:' + t.id); store.setSet('owned', o); return true;
  },
  selectedTint(slot) { return store.get('tint_' + slot.key) || Skins.ORIGINAL; },
  selectTint(slot, id) { store.set('tint_' + slot.key, id); },
  get vibrationOn() { return store.getBool('vibration_on', true); },
  set vibrationOn(v) { store.setBool('vibration_on', v); },
  queueRun(score, duration, jumps) {
    if (score <= 0 || store.getInt('pending_score') >= score) return;
    store.set('pending_score', score); store.set('pending_duration', duration); store.set('pending_jumps', jumps);
  },
  get pendingScore() { return store.getInt('pending_score'); },
  get pendingDuration() { return store.getFloat('pending_duration'); },
  get pendingJumps() { return store.getInt('pending_jumps'); },
  clearPending(sent) { if (this.pendingScore <= sent) { store.del('pending_score'); store.del('pending_duration'); store.del('pending_jumps'); } },
  needsSync() { return this.pendingScore > 0 || (this.pseudo != null && this.pseudo !== this.syncedPseudo); },
  topRuns() {
    const raw = store.get('runs') || '';
    return raw.split(';').map((s) => s.split(':')).filter((p) => p.length === 2)
      .map(([s, t]) => ({ score: parseInt(s, 10), time: parseInt(t, 10) })).filter((r) => !isNaN(r.score));
  },
  addRun(score) {
    if (score <= 0) return false;
    this.totalPoints = this.totalPoints + score;
    const runs = this.topRuns().concat([{ score, time: Date.now() }])
      .sort((a, b) => b.score - a.score || a.time - b.time).slice(0, this.MAX_RUNS);
    store.set('runs', runs.map((r) => r.score + ':' + r.time).join(';'));
    const isBest = score > this.best;
    if (isBest) store.set('best', score);
    return isBest;
  },
};

// ---------- Missions du jour ----------
const MissionType = {
  SCORE: { max: true, text: 'Atteins %d points en une partie' },
  PERFECTS: { max: false, text: 'Fais %d atterrissages parfaits' },
  STREAK: { max: true, text: "Enchaîne %d parfaits d'affilée" },
  COINS: { max: false, text: 'Ramasse %d pièces' },
  BONUS_WIN: { max: false, text: 'Réussis %d niveaux bonus' },
  TRAMPOLINES: { max: false, text: 'Rebondis sur %d trampolines' },
  GAMES: { max: false, text: 'Joue %d parties' },
  JUMPS: { max: false, text: 'Réussis %d sauts' },
  SCARES: { max: false, text: 'Atterris %d fois tout au bord' },
};

const missions = (() => {
  const ALL_DONE_REWARD = 25;
  const easy = [
    [MissionType.SCORE, [15, 20, 25]], [MissionType.PERFECTS, [4, 6, 8]], [MissionType.STREAK, [2, 3]],
    [MissionType.COINS, [5, 8, 10]], [MissionType.BONUS_WIN, [1, 2]], [MissionType.TRAMPOLINES, [2, 3, 4]],
    [MissionType.GAMES, [3, 5]], [MissionType.JUMPS, [40, 60, 80]], [MissionType.SCARES, [2, 3]],
  ];
  const hard = [[MissionType.SCORE, [50, 60, 75]], [MissionType.STREAK, [4, 5, 6]], [MissionType.PERFECTS, [15, 20]], [MissionType.COINS, [25, 30]]];
  function seeded(str) {
    let h = 2166136261;
    for (const ch of str) { h ^= ch.charCodeAt(0); h = Math.imul(h, 16777619); }
    let a = h >>> 0;
    return () => { a += 0x6D2B79F5; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  }
  function generate(date) {
    const r = seeded(date);
    const pool = easy.slice();
    for (let i = pool.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [pool[i], pool[j]] = [pool[j], pool[i]]; }
    const list = pool.slice(0, 3).map(([type, targets]) => {
      const i = Math.floor(r() * targets.length);
      return { type, target: targets[i], reward: 5 + 5 * i, challenge: false };
    });
    const [ht, hts] = hard[Math.floor(r() * hard.length)];
    list.push({ type: ht, target: hts[Math.floor(r() * hts.length)], reward: 50, challenge: true });
    list.forEach((m) => { m.label = m.type.text.replace('%d', m.target); });
    return list;
  }
  const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
  let date = '', list = [];
  function refresh() {
    const d = today();
    if (d === date) return;
    date = d; list = generate(d);
    if (store.get('missions_date') !== d) {
      store.set('missions_date', d);
      list.forEach((_, i) => { store.set('mission_p' + i, 0); store.set('mission_done' + i, 0); });
      store.set('missions_all_done', 0);
    }
  }
  const api = {
    ALL_DONE_REWARD,
    list() { refresh(); return list; },
    progress(i) { refresh(); return store.getInt('mission_p' + i); },
    isDone(i) { refresh(); return store.getInt('mission_done' + i) === 1; },
    allMissionsDone() { refresh(); return [0, 1, 2].every((i) => api.isDone(i)); },
    record(type, amount = 1) {
      refresh();
      let coins = 0; const done = [];
      list.forEach((m, i) => {
        if (m.type !== type || api.isDone(i)) return;
        const old = store.getInt('mission_p' + i);
        const p = type.max ? Math.max(old, amount) : old + amount;
        store.set('mission_p' + i, Math.min(p, m.target));
        if (p >= m.target) { store.set('mission_done' + i, 1); coins += m.reward; done.push(m); }
      });
      let allDoneBonus = false;
      if (done.length && api.allMissionsDone() && store.getInt('missions_all_done') === 0) {
        store.set('missions_all_done', 1); coins += ALL_DONE_REWARD; allDoneBonus = true;
      }
      return { coins, completed: done, allDoneBonus };
    },
  };
  return api;
})();
