'use strict';
// Traductions (équivalent de I18n.kt). Un fichier JSON par langue dans lang/ (copie du dossier
// langues/ du dépôt) ; le français sert de secours pour une clé absente.

const LANGUAGES = ['fr', 'en', 'es', 'de', 'pt', 'it'];
const LANG_AUTO = 'auto';

const I18n = {
  files: {},
  lang: 'fr',
  /** Langue réellement utilisée pour un réglage (« auto » = langue du navigateur, anglais sinon). */
  resolve(pref) {
    if (LANGUAGES.includes(pref)) return pref;
    const nav = (navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || 'en'])
      .map((l) => String(l).slice(0, 2).toLowerCase());
    return nav.find((l) => LANGUAGES.includes(l)) || 'en';
  },
  async loadAll() {
    await Promise.all(LANGUAGES.map(async (code) => {
      try { this.files[code] = await (await fetch('lang/' + code + '.json', { cache: 'no-cache' })).json(); } catch (e) { this.files[code] = null; }
    }));
  },
  use(pref) {
    this.lang = this.resolve(pref);
    if (!this.files[this.lang]) this.lang = 'fr';
    document.documentElement.lang = this.lang;
    // Textes de la page HTML (fenêtre du pseudo, message d'erreur)
    for (const el of document.querySelectorAll('[data-i18n]')) el.textContent = tr(el.dataset.i18n);
    for (const el of document.querySelectorAll('[data-i18n-placeholder]')) el.placeholder = tr(el.dataset.i18nPlaceholder);
  },
  nameOf(code) { return (this.files[code] && this.files[code]._nom) || code; },
};

/** Texte traduit de la clé [key] ; {n}, {name}… remplacés par les valeurs de [args]. */
function tr(key, args) {
  const cur = I18n.files[I18n.lang], fb = I18n.files.fr;
  let s = (cur && cur[key]) || (fb && fb[key]) || key;
  if (args) for (const k in args) s = s.split('{' + k + '}').join(String(args[k]));
  return s;
}

function yesNo(b) { return tr(b ? 'common.yes' : 'common.no'); }
