'use strict';
// Fausses publicités parodiques (équivalent de Ads.kt). Tout est dessiné en code, sans son.

const FakeAds = (() => {
  const c = (s) => Color.parseColor(s);
  // Textes traduits : clés « ad.<id>.brand », « .title », « .lines », « .legal », « .cta »…
  const A = (id, bg, text, accent, ctaColor) => ({
    id, bg, text, accent, ctaColor,
    t(part, args) { return tr('ad.' + id + '.' + part, args); },
    get brand() { return this.t('brand'); }, get title() { return this.t('title'); }, get lines() { return this.t('lines'); },
    get legal() { return this.t('legal'); }, get cta() { return this.t('cta'); },
  });
  return {
    EVERY: 3,
    CLOSE_DELAY: 3,
    all: [
      A('assurance', c('#EAF2FB'), c('#1D3557'), c('#1D4E89'), c('#1D4E89')),
      A('cereales', c('#FFF3C4'), c('#5A2D0C'), c('#E53950'), c('#E53950')),
      A('immo', c('#F4F1EA'), c('#2B2B2B'), c('#2F6B4F'), c('#2F6B4F')),
      A('genie', c('#1E1E3A'), Color.WHITE, c('#FFD54F'), c('#3DBE6E')),
      A('crypto', c('#0F2027'), Color.WHITE, c('#3DDC97'), c('#F7931A')),
      A('love', c('#FFE4EC'), c('#5B1A33'), c('#E0457B'), c('#E0457B')),
      A('premium', c('#2E3A59'), Color.WHITE, c('#FFD54F'), c('#E0A800')),
    ],
  };
})();

let adGames = 0, sessionFalls = 0, adBag = [], currentAd = null, adTime = 0, adMsg = null, adClicks = 0;
let adToastText = '', adToastT = 0, adCloseLeft = false, adDodge = false;
const adPaint = new Paint();
const adCtaPaint = new Paint();
const adText = new Paint({ textAlign: Align.CENTER });
const adBold = new Paint({ textAlign: Align.CENTER, bold: true });
const adStroke = new Paint({ style: Style.STROKE, strokeCap: 'round' });

/** « Rejouer » : relance la partie, avec de temps en temps une fausse pub avant. */
function replay() {
  if (profile.fakeAdsOn && ++adGames >= FakeAds.EVERY) { adGames = 0; openAd(); } else resetGame();
}

function openAd() {
  if (adBag.length === 0) adBag = FakeAds.all.slice().sort(() => Math.random() - 0.5);
  currentAd = adBag.shift();
  adTime = 0; adMsg = null; adClicks = 0; adToastT = 0; adCloseLeft = false;
  adDodge = Math.random() < 0.4;
  state = State.AD;
}

function closeAd() { currentAd = null; resetGame(); }

function adToast(text) { adToastText = text; adToastT = 1.6; }

function updateAd(dt) { adTime += dt; if (adToastT > 0) adToastT -= dt; }

function adClick(ad) {
  adClicks++;
  if (ad.id === 'assurance') {
    if (adClicks < 3) { adToast(ad.t('click', { n: adClicks })); adMsg = null; } else adMsg = ad.t('answer', { n: sessionFalls });
  } else if (ad.id === 'immo') adMsg = profile.coins < 3600 ? ad.t('answer', { n: profile.coins }) : ad.t('answer_rich');
  else adMsg = ad.t('answer');
}

function wrapText(text, paint, maxW) {
  const out = [];
  for (const para of text.split('\n')) {
    let line = '';
    for (const word of para.split(' ')) {
      const t = line === '' ? word : line + ' ' + word;
      if (line === '' || paint.measureText(t) <= maxW) line = t;
      else { out.push(line); line = word; }
    }
    out.push(line);
  }
  return out;
}

function drawAd(canvas) {
  const ad = currentAd;
  if (!ad) return;
  const top = insetTop, bottom = ub();
  adPaint.color = ad.bg;
  canvas.drawRect(0, 0, w, h, adPaint);

  adText.textAlign = Align.LEFT; adText.textSize = w * 0.03; adText.color = ad.text; adText.alpha = 140;
  canvas.drawText(tr('ad.label'), w * 0.05, top + w * 0.08, adText);
  adText.alpha = 255; adText.textAlign = Align.CENTER;

  adBold.color = ad.accent; adBold.textSize = w * 0.05;
  canvas.drawText(ellipsize(ad.brand, adBold, w * 0.9), w / 2, top + w * 0.2, adBold);

  const artTop = top + w * 0.25;
  const artH = Math.min(w * 0.68, (bottom - top) * 0.33);
  drawAdArt(canvas, ad, w / 2, artTop + artH / 2, artH);

  const pulse = 1 + 0.035 * Math.sin(adTime * 7);
  const cw = w * 0.4 * pulse, ch = w * 0.065 * pulse, cy = bottom - w * 0.14;
  adText.textSize = w * 0.024;
  const legal = wrapText(ad.legal, adText, w * 0.86);
  const legalTop = cy - w * 0.065 - w * 0.04 - legal.length * w * 0.032;

  // Titre et arguments, centrés entre l'illustration et les mentions légales
  adBold.textSize = w * 0.058; adText.textSize = w * 0.04;
  const titleLines = wrapText(ad.title, adBold, w * 0.88);
  const bodyLines = wrapText(ad.lines, adText, w * 0.88);
  const blockH = titleLines.length * w * 0.07 + w * 0.02 + bodyLines.length * w * 0.056;
  const free = legalTop - w * 0.08 - (artTop + artH);
  let y = artTop + artH + w * 0.06 + Math.max(0, (free - blockH) / 2);
  adBold.color = ad.text;
  for (const l of titleLines) { canvas.drawText(l, w / 2, y, adBold); y += w * 0.07; }
  y += w * 0.02;
  adText.color = ad.text;
  for (const l of bodyLines) { canvas.drawText(l, w / 2, y, adText); y += w * 0.056; }

  adText.textSize = w * 0.024; adText.alpha = 150;
  let ly = cy - ch - w * 0.04 - (legal.length - 1) * w * 0.032;
  for (const l of legal) { canvas.drawText(l, w / 2, ly, adText); ly += w * 0.032; }
  adText.alpha = 255;
  adCtaPaint.color = ad.ctaColor;
  drawButton(canvas, new RectF(w / 2 - cw, cy - ch, w / 2 + cw, cy + ch), ad.cta, adCtaPaint, w * 0.048, () => adClick(ad));
  if (adToastT > 0) {
    adBold.color = ad.text; adBold.textSize = w * 0.04; adBold.alpha = 255 * Math.min(1, adToastT);
    canvas.drawText(adToastText, w / 2, cy - ch - w * 0.04 - legal.length * w * 0.032 - w * 0.03, adBold);
    adBold.alpha = 255;
  }

  // Croix : compte à rebours, puis elle esquive parfois le premier appui
  const cr = w * 0.045, cx = adCloseLeft ? w * 0.08 : w * 0.92, ccy = top + w * 0.07;
  adPaint.color = Color.argb(60, 0, 0, 0);
  canvas.drawCircle(cx, ccy, cr, adPaint);
  const remain = FakeAds.CLOSE_DELAY - adTime;
  adText.color = ad.text;
  if (remain > 0) {
    adText.textSize = w * 0.04;
    canvas.drawText(String(Math.ceil(remain)), cx, ccy + w * 0.014, adText);
  } else {
    adStroke.color = ad.text; adStroke.strokeWidth = w * 0.007;
    const d = cr * 0.4;
    canvas.drawLine(cx - d, ccy - d, cx + d, ccy + d, adStroke);
    canvas.drawLine(cx + d, ccy - d, cx - d, ccy + d, adStroke);
    if (adMsg === null) {
      buttons.push({
        rect: new RectF(cx - cr * 1.8, ccy - cr * 1.8, cx + cr * 1.8, ccy + cr * 1.8),
        action: () => {
          if (adDodge) { adDodge = false; adCloseLeft = !adCloseLeft; adToast(tr('ad.dodge')); } else closeAd();
        },
      });
    }
  }

  if (adMsg !== null) {
    buttons.length = 0;
    canvas.drawRect(0, 0, w, h, overlayPaint);
    darkTextPaint.textAlign = Align.CENTER; darkTextPaint.textSize = w * 0.045;
    const lines = wrapText(adMsg, darkTextPaint, w * 0.72);
    const lh = w * 0.062, ph = lines.length * lh + w * 0.3, pt = h / 2 - ph / 2;
    rect.set(w * 0.08, pt, w * 0.92, pt + ph);
    canvas.drawRoundRect(rect, w * 0.05, w * 0.05, panelPaint);
    let ty = pt + w * 0.11;
    for (const l of lines) { canvas.drawText(l, w / 2, ty, darkTextPaint); ty += lh; }
    drawButton(canvas, new RectF(w * 0.32, pt + ph - w * 0.15, w * 0.68, pt + ph - w * 0.05), 'OK', primaryBtnPaint, w * 0.045, () => { adMsg = null; });
  }
}

// ---------------- Illustrations ----------------

function adFace(canvas, c, ...p) {
  tmpPath.reset(); tmpPath.moveTo(p[0], p[1]);
  for (let i = 2; i < p.length; i += 2) tmpPath.lineTo(p[i], p[i + 1]);
  tmpPath.close(); adPaint.color = c; canvas.drawPath(tmpPath, adPaint);
}

function isoBox(canvas, cx, by, rx, ry, hh, top, left, right) {
  adFace(canvas, left, cx, by, cx - rx, by - ry, cx - rx, by - ry - hh, cx, by - hh);
  adFace(canvas, right, cx, by, cx + rx, by - ry, cx + rx, by - ry - hh, cx, by - hh);
  adFace(canvas, top, cx, by - hh, cx - rx, by - ry - hh, cx, by - 2 * ry - hh, cx + rx, by - ry - hh);
}

function adCube(canvas, cx, by, s, color, eyes = true) {
  isoBox(canvas, cx, by, s * 0.866, s * 0.5, s, shade(color, 1.18), color, shade(color, 0.78));
  if (!eyes) return;
  for (const dx of [-0.45, 0.45]) {
    const ex = cx + dx * s * 0.866, ey = by - s * 0.62;
    adPaint.color = Color.WHITE; canvas.drawCircle(ex, ey, s * 0.13, adPaint);
    adPaint.color = Color.parseColor('#1B1B1B'); canvas.drawCircle(ex, ey + s * 0.02, s * 0.07, adPaint);
  }
}

/** Texte dans une illustration, réduit pour tenir dans [maxW]. */
function artText(canvas, text, x, y, size, maxW) {
  adBold.textSize = size;
  const tw = adBold.measureText(text);
  if (tw > maxW) adBold.textSize = size * maxW / tw;
  canvas.drawText(text, x, y, adBold);
}

function adEmoji(canvas, text, x, y, size) {
  adText.textAlign = Align.CENTER; adText.textSize = size; adText.color = 0xFF000000;
  canvas.drawText(text, x, y + size * 0.35, adText);
}

function drawAdArt(canvas, ad, cx, cy, s) {
  const t = adTime, orange = Color.parseColor('#FF7A45'), PC = Color.parseColor;
  switch (ad.id) {
    case 'assurance': {
      const by = cy + s * 0.3, e = s * 0.32;
      adStroke.color = PC('#8D6E63'); adStroke.strokeWidth = s * 0.03;
      canvas.drawLine(cx - e * 1.2, by + s * 0.04, cx - e * 0.95, by - e * 1.2, adStroke);
      canvas.drawLine(cx + e * 1.2, by + s * 0.04, cx + e * 0.95, by - e * 1.2, adStroke);
      adCube(canvas, cx, by, e, orange);
      adStroke.color = Color.WHITE; adStroke.strokeWidth = s * 0.045;
      canvas.drawLine(cx - e * 0.866, by - e * 0.5 - e * 0.85, cx, by - e * 0.85, adStroke);
      canvas.drawLine(cx, by - e * 0.85, cx + e * 0.866, by - e * 0.5 - e * 0.85, adStroke);
      adPaint.color = PC('#F5D0A9');
      canvas.save(); canvas.rotate(-30, cx + e * 0.45, by - e * 0.3);
      rect.set(cx + e * 0.25, by - e * 0.36, cx + e * 0.65, by - e * 0.24);
      canvas.drawRoundRect(rect, e * 0.05, e * 0.05, adPaint);
      canvas.restore();
      for (let i = 0; i < 3; i++) {
        const a = t * 3 + i * 2.1;
        adPaint.color = PC('#FFC93C');
        drawStar(canvas, cx + Math.cos(a) * e * 0.9, by - e * 2.2 + Math.sin(a) * e * 0.2, s * 0.04, adPaint);
      }
      adPaint.color = ad.accent;
      canvas.drawCircle(cx + s * 0.6, cy - s * 0.28, s * 0.13, adPaint);
      adBold.color = Color.WHITE; artText(canvas, ad.t('art1'), cx + s * 0.6, cy - s * 0.26, s * 0.06, s * 0.24);
      break;
    }
    case 'cereales': {
      // Barre céréalique dans son emballage, penchée, avec des miettes
      canvas.save(); canvas.rotate(-14, cx - s * 0.2, cy);
      adPaint.color = PC('#B71C2C');
      for (const end of [-0.66, 0.21]) {
        for (let i = 0; i < 4; i++) {
          const y0 = cy - s * 0.14 + i * s * 0.07;
          rect.set(cx + end * s, y0, cx + (end + 0.06) * s, y0 + s * 0.07);
          canvas.drawRect(rect, adPaint);
        }
      }
      rect.set(cx - s * 0.62, cy - s * 0.15, cx + s * 0.23, cy + s * 0.15);
      adPaint.color = PC('#E53950'); canvas.drawRoundRect(rect, s * 0.04, s * 0.04, adPaint);
      rect.set(cx - s * 0.52, cy - s * 0.11, cx + s * 0.08, cy + s * 0.11);
      adPaint.color = PC('#FFC93C'); canvas.drawRoundRect(rect, s * 0.04, s * 0.04, adPaint);
      adBold.color = PC('#E53950'); artText(canvas, ad.t('art1'), cx - s * 0.22, cy - s * 0.01, s * 0.085, s * 0.48);
      artText(canvas, ad.t('art2'), cx - s * 0.22, cy + s * 0.075, s * 0.062, s * 0.48);
      canvas.restore();
      for (let i = 0; i < 6; i++) {
        adPaint.color = i % 2 === 0 ? PC('#C68642') : PC('#8D5524');
        const bx = cx - s * 0.5 + i * s * 0.11, byy = cy + s * 0.32 + (i % 3) * s * 0.03;
        rect.set(bx, byy, bx + s * 0.04, byy + s * 0.04);
        canvas.drawRect(rect, adPaint);
      }
      canvas.save(); canvas.rotate(12 + 4 * Math.sin(t * 5), cx - s * 0.5, cy - s * 0.42);
      adPaint.color = PC('#FFEB3B'); drawStar(canvas, cx - s * 0.5, cy - s * 0.42, s * 0.16, adPaint);
      adBold.color = PC('#E53950'); adBold.textSize = s * 0.065;
      canvas.drawText('+400%', cx - s * 0.5, cy - s * 0.4, adBold);
      canvas.restore();
      const hop = Math.abs(Math.sin(t * 4)) * s * 0.08, e = s * 0.22, mx = cx + s * 0.38, mby = cy + s * 0.38 - hop;
      adCube(canvas, mx, mby, e, orange, false);
      adPaint.color = PC('#1B1B1B');
      for (const dx of [-0.45, 0.45]) {
        rect.set(mx + dx * e * 0.866 - e * 0.2, mby - e * 0.72, mx + dx * e * 0.866 + e * 0.2, mby - e * 0.52);
        canvas.drawRoundRect(rect, e * 0.08, e * 0.08, adPaint);
      }
      adStroke.color = PC('#1B1B1B'); adStroke.strokeWidth = s * 0.012;
      canvas.drawLine(mx - e * 0.3, mby - e * 0.65, mx + e * 0.3, mby - e * 0.65, adStroke);
      break;
    }
    case 'immo': {
      const bob = Math.sin(t * 2) * s * 0.02;
      adPaint.color = Color.argb(200, 255, 255, 255);
      for (const [x, y, r] of [[-0.5, -0.25, 0.08], [-0.42, -0.25, 0.1], [0.45, 0.1, 0.09], [0.54, 0.1, 0.07]]) canvas.drawCircle(cx + x * s, cy + y * s, r * s, adPaint);
      const by = cy + s * 0.38 + bob;
      isoBox(canvas, cx, by, s * 0.12, s * 0.07, s * 0.4, PC('#C8B89A'), PC('#A8977A'), PC('#8A7A60'));
      adStroke.color = PC('#4E4232'); adStroke.strokeWidth = s * 0.008;
      canvas.drawLine(cx - s * 0.05, by - s * 0.4, cx - s * 0.08, by - s * 0.32, adStroke);
      canvas.drawLine(cx - s * 0.08, by - s * 0.32, cx - s * 0.04, by - s * 0.24, adStroke);
      canvas.drawLine(cx + s * 0.06, by - s * 0.2, cx + s * 0.09, by - s * 0.1, adStroke);
      const top = by - s * 0.4 - s * 0.14;
      adStroke.color = PC('#6D4C41'); adStroke.strokeWidth = s * 0.015;
      canvas.drawLine(cx, top + s * 0.1, cx, top - s * 0.18, adStroke);
      rect.set(cx - s * 0.17, top - s * 0.36, cx + s * 0.17, top - s * 0.18);
      adPaint.color = ad.accent; canvas.drawRoundRect(rect, s * 0.02, s * 0.02, adPaint);
      adBold.color = Color.WHITE; artText(canvas, ad.t('art1'), cx, top - s * 0.245, s * 0.07, s * 0.3);
      adPaint.color = PC('#A8977A');
      for (let i = 0; i < 3; i++) {
        const f = (t * 0.7 + i * 0.33) % 1;
        canvas.drawRect(cx - s * 0.05 + i * s * 0.05, by + f * s * 0.2, cx - s * 0.035 + i * s * 0.05, by + f * s * 0.2 + s * 0.015, adPaint);
      }
      break;
    }
    case 'genie': {
      const ph = t % 3, gap = s * 0.42, by = cy + s * 0.3, slabTop = PC('#B0BEC5');
      isoBox(canvas, cx - gap / 2, by, s * 0.17, s * 0.1, s * 0.08, slabTop, PC('#8FA0A8'), PC('#78868D'));
      isoBox(canvas, cx + gap / 2, by, s * 0.17, s * 0.1, s * 0.08, slabTop, PC('#8FA0A8'), PC('#78868D'));
      const e = s * 0.11, baseY = by - s * 0.18;
      let x, y;
      if (ph < 1) { x = cx - gap / 2; y = baseY; }
      else if (ph < 1.5) { const f = (ph - 1) / 0.5; x = cx - gap / 2 + f * gap * 0.45; y = baseY - Math.sin(f * Math.PI) * s * 0.06; }
      else { x = cx - gap / 2 + gap * 0.45; y = baseY + (ph - 1.5) * (ph - 1.5) * s * 0.9; }
      if (y < cy + s * 0.55) adCube(canvas, x, y, e, PC('#9E9E9E'));
      if (ph > 1.9) {
        adStroke.color = PC('#E53950'); adStroke.strokeWidth = s * 0.04;
        const d = s * 0.14;
        canvas.drawLine(cx - d, cy - s * 0.2 - d, cx + d, cy - s * 0.2 + d, adStroke);
        canvas.drawLine(cx + d, cy - s * 0.2 - d, cx - d, cy - s * 0.2 + d, adStroke);
        adBold.color = PC('#FFD54F'); artText(canvas, ad.t('art1'), cx, cy - s * 0.4, s * 0.11, s * 1.2);
      }
      adStroke.color = PC('#E53950'); adStroke.strokeWidth = s * 0.02;
      const ax = cx + gap / 2, ay = by - s * 0.32 - Math.abs(Math.sin(t * 5)) * s * 0.04;
      canvas.drawLine(ax, ay - s * 0.14, ax, ay, adStroke);
      canvas.drawLine(ax - s * 0.04, ay - s * 0.04, ax, ay, adStroke);
      canvas.drawLine(ax + s * 0.04, ay - s * 0.04, ax, ay, adStroke);
      break;
    }
    case 'crypto': {
      const l = cx - s * 0.6, r = cx + s * 0.6, b = cy + s * 0.4, tp = cy - s * 0.4;
      adStroke.color = Color.argb(80, 255, 255, 255); adStroke.strokeWidth = s * 0.008;
      for (let i = 0; i <= 3; i++) { const gy = b - i * (b - tp) / 3; canvas.drawLine(l, gy, r, gy, adStroke); }
      const pts = [0, 0.2, 0.12, 0.35, 0.22, 0.15, 0.35, 0.5, 0.48, 0.3, 0.6, 0.62, 0.72, 0.45, 0.85, 0.8, 1, 0.95];
      const reveal = Math.min(1, (t % 4) / 2.5);
      adStroke.color = ad.accent; adStroke.strokeWidth = s * 0.025;
      let lastX = l, lastY = b;
      for (let i = 0; i + 3 < pts.length; i += 2) {
        if (pts[i] > reveal) break;
        const x0 = l + pts[i] * (r - l), y0 = b - pts[i + 1] * (b - tp), x1 = l + pts[i + 2] * (r - l), y1 = b - pts[i + 3] * (b - tp);
        const f = clamp((reveal - pts[i]) / (pts[i + 2] - pts[i]), 0, 1);
        lastX = x0 + (x1 - x0) * f; lastY = y0 + (y1 - y0) * f;
        canvas.drawLine(x0, y0, lastX, lastY, adStroke);
      }
      adEmoji(canvas, '🚀', lastX, lastY - s * 0.06, s * 0.12);
      adBold.textSize = s * 0.07; adBold.color = Color.argb(180, 255, 255, 255);
      canvas.drawText('3 🪙', l + s * 0.1, b + s * 0.1, adBold);
      adBold.color = ad.accent;
      canvas.drawText('4 🪙 !!!', r - s * 0.12, tp - s * 0.04, adBold);
      break;
    }
    case 'love': {
      const by = cy + s * 0.3, sway = Math.sin(t * 2.5) * s * 0.015;
      for (const [dx, col] of [[-0.3, '#FF8FB1'], [0.3, '#8EC5FF']]) {
        const px0 = cx + dx * s + (dx < 0 ? sway : -sway), c = PC(col);
        isoBox(canvas, px0, by, s * 0.2, s * 0.12, s * 0.12, shade(c, 1.15), c, shade(c, 0.8));
        for (const ex of [-0.07, 0.07]) {
          adPaint.color = Color.WHITE; canvas.drawCircle(px0 + ex * s, by - s * 0.08, s * 0.035, adPaint);
          adPaint.color = PC('#1B1B1B'); canvas.drawCircle(px0 + ex * s + (dx < 0 ? s * 0.012 : -s * 0.012), by - s * 0.08, s * 0.018, adPaint);
        }
        adPaint.color = Color.argb(120, 255, 80, 120);
        canvas.drawCircle(px0 - s * 0.13, by - s * 0.03, s * 0.025, adPaint);
        canvas.drawCircle(px0 + s * 0.13, by - s * 0.03, s * 0.025, adPaint);
      }
      for (let i = 0; i < 5; i++) {
        const f = (t * 0.5 + i * 0.2) % 1, hx = cx + Math.sin(f * 6 + i) * s * 0.08;
        adEmoji(canvas, '❤️', hx, by - s * 0.25 - f * s * 0.5, s * (0.06 + 0.05 * (1 - f)));
      }
      break;
    }
    default: {
      const gold = PC('#FFC93C'), bw = s * 0.45, b = cy + s * 0.2;
      tmpPath.reset();
      tmpPath.moveTo(cx - bw, b); tmpPath.lineTo(cx - bw, b - s * 0.3); tmpPath.lineTo(cx - bw * 0.5, b - s * 0.12);
      tmpPath.lineTo(cx, b - s * 0.42); tmpPath.lineTo(cx + bw * 0.5, b - s * 0.12); tmpPath.lineTo(cx + bw, b - s * 0.3);
      tmpPath.lineTo(cx + bw, b); tmpPath.close();
      adPaint.color = gold; canvas.drawPath(tmpPath, adPaint);
      rect.set(cx - bw, b - s * 0.04, cx + bw, b + s * 0.06);
      adPaint.color = PC('#E0A800'); canvas.drawRect(rect, adPaint);
      for (const [x, col] of [[-0.25, '#E53950'], [0, '#3F8EFC'], [0.25, '#3DBE6E']]) {
        adPaint.color = PC(col); canvas.drawCircle(cx + x * s, b - s * 0.08, s * 0.035, adPaint);
      }
      for (let i = 0; i < 4; i++) {
        const f = (t * 0.8 + i * 0.25) % 1, a = i * 1.7;
        adPaint.color = Color.argb(Math.round(255 * (1 - f)), 255, 236, 140);
        drawStar(canvas, cx + Math.cos(a) * s * 0.6, cy - s * 0.15 + Math.sin(a) * s * 0.3, s * 0.05 * (0.5 + f), adPaint);
      }
      adBold.color = gold; artText(canvas, ad.t('art1'), cx, b + s * 0.22, s * 0.1, s * 0.9);
    }
  }
}
