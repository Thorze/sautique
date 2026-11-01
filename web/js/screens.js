'use strict';
// Écrans (équivalent de Screens.kt) : pseudo, classement, personnage, missions, pause, son, fin de partie.

// ---------- Pseudo (fenêtre HTML) ----------
let pseudoDialogShown = false;
function askPseudo(first, error = null) {
  if (pseudoDialogShown) return;
  pseudoDialogShown = true;
  const dlg = document.getElementById('pseudo-dialog');
  const input = document.getElementById('pseudo-input');
  document.getElementById('pseudo-title').textContent = first ? 'Choisis ton pseudo' : 'Changer de pseudo';
  document.getElementById('pseudo-msg').textContent = error || 'Il apparaîtra dans le classement mondial.';
  const cancel = document.getElementById('pseudo-cancel');
  const mustChoose = first || pseudoConflict;
  cancel.style.display = mustChoose ? 'none' : '';
  input.value = profile.pseudo || '';
  dlg.classList.add('open');
  setTimeout(() => input.focus(), 50);
  const close = () => { dlg.classList.remove('open'); pseudoDialogShown = false; input.blur(); };
  document.getElementById('pseudo-form').onsubmit = (e) => {
    e.preventDefault();
    const p = input.value.trim().slice(0, 16);
    close();
    if (!p) {
      if (!profile.pseudo) profile.pseudo = 'Joueur' + (1000 + rndInt(9000));
      afterPseudo();
      return;
    }
    online.checkPseudo(p, profile.playerId).then((free) => {
      if (free === false) askPseudo(first, `« ${p} » est déjà pris, choisis-en un autre.`);
      else { profile.pseudo = p; pseudoConflict = false; afterPseudo(); }
    });
  };
  cancel.onclick = () => close();
}

function afterPseudo() {
  syncOnline();
  if (!profile.customized) openCustomize();
}

/** Envoie la meilleure partie en attente (et le pseudo) au serveur. */
function syncOnline() {
  const pseudo = profile.pseudo;
  if (syncing || !pseudo || !profile.needsSync()) return;
  syncing = true;
  const sc = profile.pendingScore;
  online.submit(profile.playerId, pseudo, sc, profile.pendingDuration, profile.pendingJumps).then((result) => {
    syncing = false;
    if (result === 'ok') {
      profile.clearPending(sc);
      profile.syncedPseudo = pseudo;
      if (state === State.LEADERBOARD && boardTab > 0) loadWorld();
      if (profile.needsSync() && (profile.pendingScore !== sc || profile.pseudo !== pseudo)) syncOnline();
    } else if (result === 'taken') {
      pseudoConflict = true;
      askPseudo(false, `Le pseudo « ${pseudo} » est déjà utilisé par un autre joueur. Choisis-en un autre.`);
    } else if (result === 'rejected') {
      profile.clearPending(sc);
      profile.syncedPseudo = pseudo;
    }
  });
}

/** Nombre de joueurs affichés dans les classements en ligne. */
const BOARD_SIZE = 50;

function openBoard(tab) {
  if (state !== State.LEADERBOARD) boardReturnState = state;
  state = State.LEADERBOARD;
  boardTab = tab;
  boardScroll = 0;
  personalRuns = profile.topRuns();
  if (tab > 0) loadWorld();
}

function loadWorld() {
  worldStatus = 'loading';
  worldRank = null;
  const tab = boardTab;
  online.fetchBoard(BOARD_SIZE, profile.playerId, tab === 1 ? 'week' : 'all').then((board) => {
    if (boardTab !== tab) return;
    if (!board) worldStatus = 'error';
    else { worldEntries = board.top; worldRank = board.myRank; worldStatus = 'ok'; boardScrollToMe = true; }
  });
}

function openMissions() { if (state === State.MISSIONS) return; missionsReturnState = state; state = State.MISSIONS; }
function openCustomize() { if (state === State.CUSTOMIZE) return; custReturnState = state; custMsg = ''; state = State.CUSTOMIZE; }
function closeCustomize() { profile.customized = true; state = custReturnState; }
function openSettings() { if (state === State.SETTINGS) return; settingsReturnState = state; state = State.SETTINGS; }

function dragTo(x) {
  const r = dragSlider === 1 ? sfxSlider : dragSlider === 2 ? musicSlider : null;
  if (!r) return;
  const v = clamp((x - r.left) / r.width(), 0, 1);
  const snapped = v < 0.03 ? 0 : v;
  if (dragSlider === 1) sounds.soundVolume = snapped; else sounds.musicVolume = snapped;
}

// ---------- Boutons ----------
const isLight = (c) => Color.red(c) * 0.3 + Color.green(c) * 0.59 + Color.blue(c) * 0.11 > 170;

function drawButton(canvas, r, label, bg, textSize, action) {
  const radius = r.height() / 2;
  canvas.drawRoundRect(r, radius, radius, bg);
  btnTextPaint.textSize = textSize;
  btnTextPaint.color = (bg === tabOffPaint || bg === lockedPaint || bg === shopPaint || (bg === itemPaint && isLight(bg.color)))
    ? Color.parseColor('#2E3A59') : Color.WHITE;
  canvas.drawText(ellipsize(label, btnTextPaint, r.width() - radius), r.centerX(), r.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2, btnTextPaint);
  buttons.push({ rect: new RectF(r.left, r.top, r.right, r.bottom), action });
}

function ellipsize(text, paint, maxWidth) {
  if (paint.measureText(text) <= maxWidth) return text;
  let t = text;
  while (t.length > 1 && paint.measureText(t + '…') > maxWidth) t = t.slice(0, -1);
  return t + '…';
}

function panel(canvas, left, top, right, bottom) {
  canvas.drawRect(0, 0, w, h, overlayPaint);
  rect.set(left, top, right, bottom);
  canvas.drawRoundRect(rect, w * 0.05, w * 0.05, panelPaint);
}

// ---------- Classement ----------
function drawLeaderboard(canvas) {
  const left = w * 0.06, right = w * 0.94, top = insetTop + h * 0.05, bottom = ub() - h * 0.03;
  panel(canvas, left, top, right, bottom);
  darkTextPaint.textAlign = Align.CENTER; darkTextPaint.textSize = w * 0.075;
  canvas.drawText('Classement', w / 2, top + w * 0.12, darkTextPaint);
  const tabTop = top + w * 0.17, tabH = w * 0.1;
  const tabs = ['Perso', 'Semaine', 'Mondial'];
  const tw = (right - left - w * 0.08) / tabs.length;
  tabs.forEach((label, i) => {
    const x0 = left + w * 0.04 + i * tw;
    drawButton(canvas, new RectF(x0 + w * 0.006, tabTop, x0 + tw - w * 0.006, tabTop + tabH), label,
      boardTab === i ? tabOnPaint : tabOffPaint, w * 0.04, () => openBoard(i));
  });
  const listTop = tabTop + tabH + w * 0.06, footer = bottom - w * 0.2, listBottom = footer - w * 0.06;
  const rowH = (footer - w * 0.08 - listTop) / profile.MAX_RUNS;
  // Liste défilable (classements en ligne : jusqu'à 50 joueurs)
  const count = boardTab === 0 ? personalRuns.length : worldStatus === 'ok' ? worldEntries.length : 0;
  boardListTop = listTop; boardListBottom = listBottom;
  boardMaxScroll = Math.max(0, count * rowH - (listBottom - listTop));
  if (boardScrollToMe) {
    boardScrollToMe = false;
    const me = worldEntries.findIndex((e) => e.me);
    if (me >= 0) boardScroll = (me + 0.5) * rowH - (listBottom - listTop) / 2;
  }
  boardScroll = clamp(boardScroll, 0, boardMaxScroll);
  darkTextPaint.textSize = Math.min(w * 0.048, rowH * 0.5);
  greyTextPaint.textSize = w * 0.045;
  const colRank = left + w * 0.06, colName = left + w * 0.17, colScore = right - w * 0.06;
  const centerY = (listTop + footer) / 2;
  const row = (i, name, value, mine) => {
    const y = listTop + rowH * i - boardScroll;
    if (y + rowH < listTop || y > listBottom) return;
    if (mine) { rect.set(left + w * 0.03, y, right - w * 0.03, y + rowH * 0.92); canvas.drawRoundRect(rect, rowH * 0.3, rowH * 0.3, highlightPaint); }
    const ty = y + rowH * 0.46 - (darkTextPaint.descent() + darkTextPaint.ascent()) / 2;
    darkTextPaint.textAlign = Align.LEFT;
    canvas.drawText(`${i + 1}.`, colRank, ty, darkTextPaint);
    canvas.drawText(ellipsize(name, darkTextPaint, colScore - colName - w * 0.15), colName, ty, darkTextPaint);
    darkTextPaint.textAlign = Align.RIGHT;
    canvas.drawText(value, colScore, ty, darkTextPaint);
  };
  if (boardTab === 0) {
    if (!personalRuns.length) canvas.drawText('Pas encore de partie.', w / 2, centerY, greyTextPaint);
    else personalRuns.forEach((r, i) => {
      const d = new Date(r.time);
      row(i, `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getFullYear()).slice(2)}`, String(r.score), false);
    });
    greyTextPaint.textSize = w * 0.04;
    canvas.drawText('Record : ' + best, w / 2, footer, greyTextPaint);
  } else {
    if (worldStatus === 'loading' || worldStatus === 'idle') canvas.drawText('Chargement…', w / 2, centerY, greyTextPaint);
    else if (worldStatus === 'error') {
      canvas.drawText('Impossible de charger le classement.', w / 2, centerY, greyTextPaint);
      canvas.drawText('Vérifie ta connexion.', w / 2, centerY + w * 0.07, greyTextPaint);
    } else {
      if (!worldEntries.length) canvas.drawText("Aucun score pour l'instant.", w / 2, centerY, greyTextPaint);
      canvas.save();
      canvas.clipRect(left, listTop, right, listBottom);
      worldEntries.forEach((e, i) => row(i, e.pseudo, String(e.best), e.me));
      canvas.restore();
      if (boardMaxScroll > 0) {
        const trackH = listBottom - listTop;
        const thumbH = Math.max(w * 0.06, trackH * trackH / (trackH + boardMaxScroll));
        const ty0 = listTop + (trackH - thumbH) * (boardScroll / boardMaxScroll);
        rect.set(right - w * 0.025, ty0, right - w * 0.012, ty0 + thumbH);
        canvas.drawRoundRect(rect, w * 0.01, w * 0.01, lockedPaint);
      }
    }
    greyTextPaint.textSize = w * 0.04;
    const pseudo = profile.pseudo || '';
    const info = best <= 0 ? 'Joue une partie pour entrer au classement !'
      : worldRank != null && boardTab === 1 ? `${pseudo} : place n°${worldRank} cette semaine`
        : worldRank != null ? `${pseudo} : place n°${worldRank}  (record ${best})`
          : boardTab === 1 ? 'Pas encore de score cette semaine' : 'Ton record : ' + best;
    canvas.drawText(ellipsize(info, greyTextPaint, right - left - w * 0.06), w / 2, footer, greyTextPaint);
  }
  drawButton(canvas, new RectF(w * 0.3, bottom - w * 0.15, w * 0.7, bottom - w * 0.05), 'Retour', primaryBtnPaint, w * 0.045, () => { state = boardReturnState; });
}

// ---------- Barre Perfect ----------
function drawPerfectBar(canvas) {
  const a = clamp(perfectBarAlpha, 0, 1);
  const scale = 1 + 0.12 * perfectPulse;
  const cy = insetTop + h * 0.17 + w * 0.17;
  const bw = w * 0.62 * scale, bh = w * 0.085 * scale;
  canvas.save();
  canvas.translate(w / 2, cy);
  rect.set(-bw / 2, -bh / 2, bw / 2, bh / 2);
  perfectBgPaint.alpha = Math.floor(110 * a);
  canvas.drawRoundRect(rect, bh / 2, bh / 2, perfectBgPaint);
  const level = clamp((Math.max(shownStreak, 3) - 2) / 4, 0, 1);
  const fillW = (bw - bh * 0.3) * level;
  if (fillW > 0) {
    rect.set(-bw / 2 + bh * 0.15, -bh / 2 + bh * 0.15, -bw / 2 + bh * 0.15 + fillW, bh / 2 - bh * 0.15);
    perfectBarPaint.shader = new LinearGradient(rect.left, 0, Math.max(rect.right, rect.left + 1), 0, Color.parseColor('#FFD54F'), Color.parseColor('#FF7A45'));
    perfectBarPaint.alpha = Math.floor(255 * a);
    canvas.drawRoundRect(rect, bh * 0.35, bh * 0.35, perfectBarPaint);
  }
  const points = perfectStreak >= 3 ? Scoring.perfectPoints(perfectStreak) : lastPerfectPoints;
  textPaint.textSize = bh * 0.55; textPaint.alpha = Math.floor(255 * a);
  canvas.drawText(`PERFECT  ×${shownStreak}   +${points}`, 0, -(textPaint.descent() + textPaint.ascent()) / 2, textPaint);
  textPaint.alpha = 255;
  canvas.restore();
}

// ---------- Son ----------
function drawSlider(canvas, r, label, value) {
  darkTextPaint.textAlign = Align.LEFT; darkTextPaint.textSize = w * 0.05;
  canvas.drawText(label, r.left, r.top - w * 0.05, darkTextPaint);
  darkTextPaint.textAlign = Align.RIGHT;
  canvas.drawText(value <= 0 ? 'Coupé' : Math.floor(value * 100) + ' %', r.right, r.top - w * 0.05, darkTextPaint);
  const bh = r.height();
  canvas.drawRoundRect(r, bh / 2, bh / 2, tabOffPaint);
  rect.set(r.left, r.top, r.left + r.width() * value, r.bottom);
  canvas.drawRoundRect(rect, bh / 2, bh / 2, primaryBtnPaint);
  const kx = r.left + r.width() * value;
  canvas.drawCircle(kx, r.centerY(), bh * 1.1, cursorPaint);
  cursorStroke.strokeWidth = w * 0.006;
  canvas.drawCircle(kx, r.centerY(), bh * 1.1, cursorStroke);
}

function drawSettings(canvas) {
  const left = w * 0.06, right = w * 0.94, top = h * 0.25, bottom = top + w * 1.12;
  panel(canvas, left, top, right, bottom);
  darkTextPaint.textAlign = Align.CENTER; darkTextPaint.textSize = w * 0.07;
  canvas.drawText('Son et vibrations', w / 2, top + w * 0.11, darkTextPaint);
  const sl = left + w * 0.08, sr = right - w * 0.08, bh = w * 0.035;
  sfxSlider.set(sl, top + w * 0.3, sr, top + w * 0.3 + bh);
  musicSlider.set(sl, top + w * 0.55, sr, top + w * 0.55 + bh);
  drawSlider(canvas, sfxSlider, 'Effets sonores', sounds.soundVolume);
  drawSlider(canvas, musicSlider, 'Musique', sounds.musicVolume);
  if (navigator.vibrate) {
    drawButton(canvas, new RectF(sl, top + w * 0.68, sr, top + w * 0.79), '📳 Vibrations : ' + (profile.vibrationOn ? 'oui' : 'non'),
      profile.vibrationOn ? tabOnPaint : tabOffPaint, w * 0.042, () => { profile.vibrationOn = !profile.vibrationOn; if (profile.vibrationOn) vibrate(15); });
  }
  drawButton(canvas, new RectF(w * 0.3, bottom - w * 0.17, w * 0.7, bottom - w * 0.06), 'OK', primaryBtnPaint, w * 0.05, () => { state = settingsReturnState; });
}

// ---------- Personnage ----------
function drawCustomize(canvas) {
  const left = w * 0.04, right = w * 0.96, top = insetTop + h * 0.03, bottom = ub() - h * 0.02;
  panel(canvas, left, top, right, bottom);
  darkTextPaint.textAlign = Align.CENTER; darkTextPaint.textSize = w * 0.07;
  canvas.drawText('Ton personnage', w / 2, top + w * 0.1, darkTextPaint);

  // Mise en page adaptative : le bas (texte + OK) est fixe, la peinture juste au-dessus,
  // l'aperçu et les boutons rétrécissent si l'écran est court.
  const footY = bottom - w * 0.2;
  const tintable = Skins.tintable.has(custSlot);
  const tintH = w * 0.17;
  const tintTop = footY - w * 0.05 - tintH;
  const tabH = w * 0.085, tabsH = 2 * tabH + w * 0.015;
  const rowsMax = 6;   // l'onglet Couleur a le plus d'éléments
  const idealGrid = rowsMax * w * 0.104;
  const avail = (footY - w * 0.05 - tintH) - (top + w * 0.14) - tabsH - w * 0.05 - idealGrid;
  const previewH = clamp(avail, w * 0.2, w * 0.36);
  const pScale = 2 * clamp(previewH / (w * 0.36), 0.6, 1);
  const pcx = w / 2, pcy = top + w * 0.13 + previewH * 0.8;
  rect.set(pcx - k * 0.375 * pScale, pcy - k * 0.15 * pScale, pcx + k * 0.375 * pScale, pcy + k * 0.15 * pScale);
  canvas.drawOval(rect, shadowPaint);
  canvas.save();
  canvas.translate(pcx, pcy);
  canvas.scale(pScale, pScale);
  drawCharacter(canvas, 0, 0, 0, 1, 1, Math.floor(time / 2.5) % 2 === 0, false, false);
  canvas.restore();

  const tabTop = top + w * 0.13 + previewH + w * 0.02;
  SLOTS.forEach((slot, i) => {
    const row = i < 4 ? 0 : 1, inRow = row === 0 ? 4 : SLOTS.length - 4, col = row === 0 ? i : i - 4;
    const cw = (right - left - w * 0.04) / inRow;
    const x0 = left + w * 0.02 + col * cw, y0 = tabTop + row * (tabH + w * 0.015);
    drawButton(canvas, new RectF(x0 + w * 0.006, y0, x0 + cw - w * 0.006, y0 + tabH), slot.title,
      slot === custSlot ? tabOnPaint : tabOffPaint, w * 0.034, () => { custSlot = slot; });
  });

  const pts = profile.totalPoints;
  const selected = currentLook(custSlot);
  const gridTop = tabTop + tabsH + w * 0.035;
  const rows = Math.ceil(Skins.bySlot(custSlot).length / 2);
  const gridBottom = (tintable ? tintTop : footY - w * 0.05) - w * 0.01;
  const step = Math.min(w * 0.104, (gridBottom - gridTop) / rows);
  const cellH = step * 0.85, cellW = (right - left - w * 0.08) / 2;
  Skins.bySlot(custSlot).forEach((item, i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x0 = left + w * 0.04 + col * cellW, y0 = gridTop + row * step;
    const r = new RectF(x0 + w * 0.01, y0, x0 + cellW - w * 0.01, y0 + cellH);
    const unlocked = profile.available(item);
    const isSel = item.id === selected;
    const label = !unlocked && item.isShop ? `🪙 ${item.price} · ${item.label}` : !unlocked ? `🔒 ${item.label} · ${item.cost}` : isSel ? '✓ ' + item.label : item.label;
    let bg;
    if (!unlocked && item.isShop) bg = shopPaint;
    else if (!unlocked) bg = lockedPaint;
    else if (custSlot === Slot.COLOR) { itemPaint.color = item.color; bg = itemPaint; }
    else bg = isSel ? tabOnPaint : tabOffPaint;
    if (isSel && custSlot === Slot.COLOR) {
      bubbleStroke.strokeWidth = w * 0.008;
      const rr = new RectF(r.left - w * 0.008, r.top - w * 0.008, r.right + w * 0.008, r.bottom + w * 0.008);
      canvas.drawRoundRect(rr, rr.height() / 2, rr.height() / 2, bubbleStroke);
    }
    drawButton(canvas, r, label, bg, Math.min(w * 0.036, cellH * 0.42), () => {
      if (unlocked) { profile.select(custSlot, item.id); loadLook(); }
      else if (item.isShop && profile.coins < item.price) { custMsg = `Il te manque ${item.price - profile.coins} 🪙 pour « ${item.label} »`; custMsgT = 2.5; }
      else if (item.isShop && pendingBuy === item && custMsgT > 0) {
        if (profile.buy(item)) {
          profile.select(custSlot, item.id); loadLook();
          custMsg = `🎉 « ${item.label} » acheté !`; custMsgT = 2.5; sounds.play('UNLOCK');
        }
        pendingBuy = null;
      } else if (item.isShop) { pendingBuy = item; custMsg = `Touche encore pour acheter « ${item.label} » (${item.price} 🪙)`; custMsgT = 3; }
      else { custMsg = `🔒 Se débloque à ${item.cost} points cumulés`; custMsgT = 2.5; }
    });
  });

  if (tintable) {
    // Juste sous les objets s'il y a la place, sinon calée au-dessus du texte du bas
    const ty = Math.min(gridTop + rows * step + w * 0.02, tintTop) + w * 0.035;
    darkTextPaint.textAlign = Align.LEFT; darkTextPaint.textSize = w * 0.036;
    canvas.drawText("Peinture de l'objet", left + w * 0.05, ty, darkTextPaint);
    const chosen = profile.selectedTint(custSlot);
    const tstep = (right - left - w * 0.1) / Skins.tints.length;
    const rad = Math.min(tstep * 0.4, w * 0.04);
    Skins.tints.forEach((t, i) => {
      const cx = left + w * 0.05 + tstep * (i + 0.5), cy = ty + w * 0.065;
      const owned = profile.ownsTint(t);
      if (t.id === Skins.ORIGINAL) {
        itemPaint.color = Color.parseColor('#E3E7EE'); canvas.drawCircle(cx, cy, rad, itemPaint);
        bubbleStroke.strokeWidth = w * 0.006;
        canvas.drawLine(cx - rad * 0.6, cy + rad * 0.6, cx + rad * 0.6, cy - rad * 0.6, bubbleStroke);
      } else { itemPaint.color = t.color; canvas.drawCircle(cx, cy, rad, itemPaint); }
      if (t.id === chosen) { bubbleStroke.strokeWidth = w * 0.008; canvas.drawCircle(cx, cy, rad + w * 0.01, bubbleStroke); }
      if (!owned) { greyTextPaint.textSize = w * 0.026; canvas.drawText(String(t.price), cx, cy + rad + w * 0.035, greyTextPaint); }
      buttons.push({
        rect: new RectF(cx - tstep / 2, cy - rad - w * 0.02, cx + tstep / 2, cy + rad + w * 0.04),
        action: () => {
          const lbl = t.label.toLowerCase();
          if (owned) { profile.selectTint(custSlot, t.id); loadLook(); }
          else if (profile.coins < t.price) { custMsg = `Il te manque ${t.price - profile.coins} 🪙 pour la peinture ${lbl}`; custMsgT = 2.5; }
          else if (pendingTint === t && custMsgT > 0) {
            if (profile.buyTint(t)) {
              profile.selectTint(custSlot, t.id); loadLook();
              custMsg = `🎨 Peinture ${lbl} achetée ! Utilisable sur tous les objets`; custMsgT = 3; sounds.play('UNLOCK');
            }
            pendingTint = null;
          } else { pendingTint = t; custMsg = `Touche encore pour acheter la peinture ${lbl} (${t.price} 🪙)`; custMsgT = 3; }
        },
      });
    });
  }

  greyTextPaint.textSize = w * 0.038;
  const nx = Skins.nextUnlock(pts);
  const info = custMsgT > 0 ? custMsg : nx ? `🪙 ${profile.coins} · ${pts} pts · prochain : ${nx.label} à ${nx.cost}` : `🪙 ${profile.coins} · ${pts} pts · tout est débloqué !`;
  canvas.drawText(ellipsize(info, greyTextPaint, right - left - w * 0.06), w / 2, footY, greyTextPaint);
  drawButton(canvas, new RectF(w * 0.3, bottom - w * 0.15, w * 0.7, bottom - w * 0.05), 'OK', primaryBtnPaint, w * 0.05, () => closeCustomize());
}

// ---------- Fin de partie ----------
function drawGameOver(canvas) {
  canvas.drawRect(0, 0, w, h, overlayPaint);
  const y0 = h * 0.34;
  if (gameOverReason) { textPaint.textSize = w * 0.05; canvas.drawText(gameOverReason, w / 2, y0 - w * 0.14, textPaint); }
  textPaint.textSize = w * 0.12; canvas.drawText('Perdu !', w / 2, y0, textPaint);
  textPaint.textSize = w * 0.07;
  canvas.drawText('Score : ' + score, w / 2, y0 + w * 0.14, textPaint);
  canvas.drawText(newBest ? 'Nouveau record !' : 'Record : ' + best, w / 2, y0 + w * 0.24, textPaint);
  if (coinsThisRun > 0) {
    textPaint.textSize = w * 0.045;
    canvas.drawText(`+${coinsThisRun} 🪙 ramassées (total ${profile.coins})`, w / 2, y0 + w * 0.31, textPaint);
  }
  if (gameOverTime > 0.6) {
    const bh = w * 0.13, sh = w * 0.11;
    let y = y0 + w * 0.36;
    drawButton(canvas, new RectF(w * 0.2, y, w * 0.8, y + bh), 'Rejouer', primaryBtnPaint, w * 0.06, () => resetGame());
    y += bh + w * 0.04;
    drawButton(canvas, new RectF(w * 0.1, y, w * 0.49, y + sh), '🏆 Classement', secondaryBtnPaint, w * 0.042, () => openBoard(1));
    drawButton(canvas, new RectF(w * 0.51, y, w * 0.9, y + sh), '📋 Missions', secondaryBtnPaint, w * 0.042, () => openMissions());
    y += sh + w * 0.04;
    drawButton(canvas, new RectF(w * 0.1, y, w * 0.49, y + sh), '✎ ' + (profile.pseudo || 'Pseudo'), chipPaint, w * 0.04, () => askPseudo(false));
    drawButton(canvas, new RectF(w * 0.51, y, w * 0.9, y + sh), '🎨 Personnage', chipPaint, w * 0.04, () => openCustomize());
    y += sh + w * 0.08;
    textPaint.textSize = w * 0.042;
    if (unlockedLabels.length) {
      canvas.drawText(ellipsize('🎁 Débloqué : ' + unlockedLabels.join(', '), textPaint, w * 0.92), w / 2, y, textPaint);
      y += w * 0.065;
    }
    const pts = profile.totalPoints, nx = Skins.nextUnlock(pts);
    if (nx) { textPaint.textSize = w * 0.036; canvas.drawText(`Prochain : ${nx.label} (encore ${nx.cost - pts} pts)`, w / 2, y, textPaint); }
  }
}

// ---------- Pause ----------
function drawPause(canvas) {
  canvas.drawRect(0, 0, w, h, overlayPaint);
  const y0 = h * 0.3;
  textPaint.textSize = w * 0.12; canvas.drawText('Pause', w / 2, y0, textPaint);
  textPaint.textSize = w * 0.045; canvas.drawText('Score : ' + score, w / 2, y0 + w * 0.09, textPaint);
  const bh = w * 0.13, sh = w * 0.11;
  let y = y0 + w * 0.17;
  drawButton(canvas, new RectF(w * 0.2, y, w * 0.8, y + bh), '▶ Reprendre', primaryBtnPaint, w * 0.06, () => resumeGame());
  y += bh + w * 0.04;
  drawButton(canvas, new RectF(w * 0.2, y, w * 0.8, y + sh), '🔊 Son', secondaryBtnPaint, w * 0.045, () => openSettings());
  y += sh + w * 0.03;
  drawButton(canvas, new RectF(w * 0.2, y, w * 0.8, y + sh), 'Abandonner', chipPaint, w * 0.045, () => endGame('Partie abandonnée'));
}

// ---------- Missions ----------
function drawMissions(canvas) {
  const left = w * 0.05, right = w * 0.95, top = insetTop + h * 0.06, bottom = ub() - h * 0.04;
  panel(canvas, left, top, right, bottom);
  darkTextPaint.textAlign = Align.CENTER; darkTextPaint.textSize = w * 0.07;
  canvas.drawText('Missions du jour', w / 2, top + w * 0.11, darkTextPaint);
  greyTextPaint.textSize = w * 0.04;
  canvas.drawText(`🪙 ${profile.coins} pièces · nouvelles missions chaque jour`, w / 2, top + w * 0.18, greyTextPaint);
  let y = top + w * 0.26;
  const cardH = w * 0.2;
  missions.list().forEach((m, i) => {
    if (m.challenge) {
      darkTextPaint.textAlign = Align.LEFT; darkTextPaint.textSize = w * 0.045;
      canvas.drawText('🏅 Défi du jour', left + w * 0.05, y + w * 0.06, darkTextPaint);
      y += w * 0.09;
    }
    const done = missions.isDone(i), p = missions.progress(i);
    rect.set(left + w * 0.04, y, right - w * 0.04, y + cardH);
    canvas.drawRoundRect(rect, w * 0.03, w * 0.03, done ? highlightPaint : tabOffPaint);
    darkTextPaint.textAlign = Align.LEFT; darkTextPaint.textSize = w * 0.042;
    canvas.drawText(ellipsize((done ? '✅ ' : '') + m.label, darkTextPaint, right - left - w * 0.3), left + w * 0.08, y + w * 0.075, darkTextPaint);
    darkTextPaint.textAlign = Align.RIGHT;
    canvas.drawText(`+${m.reward} 🪙`, right - w * 0.08, y + w * 0.075, darkTextPaint);
    const bl = left + w * 0.08, br = right - w * 0.08, by = y + w * 0.12, bh = w * 0.035;
    rect.set(bl, by, br, by + bh); canvas.drawRoundRect(rect, bh / 2, bh / 2, lockedPaint);
    const f = clamp(p / m.target, 0, 1);
    if (f > 0) { rect.set(bl, by, bl + (br - bl) * f, by + bh); canvas.drawRoundRect(rect, bh / 2, bh / 2, done ? greenZonePaint : primaryBtnPaint); }
    greyTextPaint.textSize = w * 0.032;
    canvas.drawText(`${p} / ${m.target}`, w / 2, by + bh + w * 0.035, greyTextPaint);
    y += cardH + w * 0.03;
  });
  greyTextPaint.textSize = w * 0.038;
  canvas.drawText(missions.allMissionsDone() ? '🎉 Les 3 missions sont terminées !' : `Termine les 3 missions : +${missions.ALL_DONE_REWARD} 🪙 en plus`,
    w / 2, bottom - w * 0.2, greyTextPaint);
  drawButton(canvas, new RectF(w * 0.3, bottom - w * 0.15, w * 0.7, bottom - w * 0.05), 'OK', primaryBtnPaint, w * 0.05, () => { state = missionsReturnState; });
}
