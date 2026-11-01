'use strict';
// Serveur de scores (équivalent de ScoreServer.kt). Même serveur et même classement que l'appli Android.

// window.SAUTIQUE_SCORES_URL permet de viser un autre serveur (tests)
const SCORES_URL = window.SAUTIQUE_SCORES_URL || 'https://playerplus-vps.tail5d3355.ts.net/sautique';

const online = {
  async request(method, path, body) {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), 8000);
    try {
      const r = await fetch(SCORES_URL + path, {
        method, signal: ctrl.signal,
        headers: body ? { 'Content-Type': 'application/json' } : {},
        body: body ? JSON.stringify(body) : undefined,
      });
      const text = await r.text();
      return { code: r.status, text };
    } finally { clearTimeout(timer); }
  },
  async submit(id, pseudo, score, duration, jumps) {
    try {
      const { code } = await this.request('POST', '/api/scores', { id, pseudo, score, duration, jumps });
      if (code >= 200 && code < 300) return 'ok';
      if (code === 409) return 'taken';
      if (code >= 400 && code < 500) return 'rejected';
      return 'error';
    } catch (e) { return 'error'; }
  },
  async fetchBoard(limit, me, period) {
    try {
      const { code, text } = await this.request('GET', `/api/top?limit=${limit}&period=${period}&me=${encodeURIComponent(me)}`);
      if (code < 200 || code >= 300) return null;
      const json = JSON.parse(text);
      return { top: json.top.map((e) => ({ pseudo: e.pseudo, best: e.best, me: !!e.me })), myRank: json.me ? json.me.rank : null };
    } catch (e) { return null; }
  },
  async checkPseudo(name, me) {
    try {
      const { code, text } = await this.request('GET', `/api/pseudo?name=${encodeURIComponent(name)}&me=${encodeURIComponent(me)}`);
      if (code < 200 || code >= 300) return null;
      return JSON.parse(text).available !== false;
    } catch (e) { return null; }
  },
};
