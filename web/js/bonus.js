'use strict';
// Niveaux bonus (équivalent de Bonus.kt).

const BONUS_TYPES = [
  'timing', 'mash', 'stars', 'wheel',
].map((id) => ({ id, get title() { return tr('bonus.' + id + '.title'); }, get hint() { return tr('bonus.' + id + '.hint'); } }));

function startBonus() {
  bonusCount++;
  const types = BONUS_TYPES.filter((t) => bonusCount === 1 || t !== bonusType);
  bonusType = types[rndInt(types.length)];
  bonusSpeed = Math.min(1.4 + 0.3 * (bonusCount - 1), 3.6);
  bonusPos = -1; bonusDir = 1; bonusT = 0; bonusResult = 0; bonusResultT = 0; bonusGain = 0; bonusTaps = 0; bonusPulse = 0;
  stars = []; sparkles = []; starSpawn = 0;
  wheelAngle = rnd() * 360; wheelSpeed = 620; wheelStopping = false; lastWheelIdx = -1;
  sounds.playBonusMusic();
  state = State.BONUS;
}

function bonusTap(x, y) {
  if (bonusResult !== 0 || bonusT < bonusIntro) return;
  switch (bonusType.id) {
    case 'timing': finishBonus(Scoring.cursorPoints(bonusPos)); break;
    case 'mash':
      bonusTaps++; bonusPulse = 1;
      sounds.play('TAP', 0.7, 0.8 + Math.min(bonusTaps, 40) * 0.02);
      break;
    case 'stars': {
      const r = w * 0.1;
      const hit = stars.find((s) => (s[0] - x) ** 2 + (s[1] - y) ** 2 < r * r);
      if (hit) {
        stars = stars.filter((s) => s !== hit);
        bonusTaps++;
        sparkles.push([hit[0], hit[1], 0.5]);
        sounds.play('STAR', 1, 0.9 + Math.min(bonusTaps, 12) * 0.05);
        vibrate(10);
      }
      break;
    }
    case 'wheel': wheelStopping = true; break;
    default: break;
  }
}

function finishBonus(gain) {
  bonusGain = gain; score += gain; bonusResult = 1; bonusResultT = 0;
  sounds.play(gain > 0 ? 'BONUS_WIN' : 'BONUS_LOSE');
  if (gain > 0) missionEvent(MissionType.BONUS_WIN);
  vibrate(gain > 0 ? 15 : 60);
}

function wheelIndex() {
  const seg = 360 / wheelValues.length;
  const a = ((270 - wheelAngle) % 360 + 360) % 360;
  return clamp(Math.floor(a / seg), 0, wheelValues.length - 1);
}

function drawTimeBar(canvas, y, remaining) {
  const left = w * 0.15, right = w * 0.85, bh = w * 0.025;
  rect.set(left, y, right, y + bh); canvas.drawRoundRect(rect, bh / 2, bh / 2, barBgPaint);
  rect.set(left, y, left + (right - left) * clamp(remaining, 0, 1), y + bh); canvas.drawRoundRect(rect, bh / 2, bh / 2, greenZonePaint);
}

function drawBonus(canvas) {
  canvas.drawRect(0, 0, w, h, overlayPaint);
  const titleY = h * 0.2;
  textPaint.textSize = w * 0.12; canvas.drawText('BONUS', w / 2, titleY, textPaint);
  textPaint.textSize = w * 0.06; canvas.drawText(bonusType.title, w / 2, titleY + w * 0.1, textPaint);
  textPaint.textSize = w * 0.04; canvas.drawText(bonusType.hint, w / 2, titleY + w * 0.17, textPaint);
  const t = Math.max(0, bonusT - bonusIntro);
  const cy = h * 0.55, PC = Color.parseColor;
  if (bonusType.id === 'timing') {
    const left = w * 0.1, right = w * 0.9, bh = w * 0.1, half = (right - left) / 2, mid = (left + right) / 2;
    rect.set(left, cy - bh / 2, right, cy + bh / 2);
    itemPaint.shader = new LinearGradient(left, 0, right, 0, ['#E53950', '#FFC93C', '#3DBE6E', '#FFC93C', '#E53950'].map(PC), [0, 0.3, 0.5, 0.7, 1]);
    itemPaint.color = Color.WHITE;
    canvas.drawRoundRect(rect, bh / 2, bh / 2, itemPaint);
    itemPaint.shader = null;
    const cx = mid + half * bonusPos;
    if (bonusResult === 0 && bonusT >= bonusIntro) {
      textPaint.textSize = w * 0.06; canvas.drawText('+' + Scoring.cursorPoints(bonusPos), cx, cy - bh / 2 - w * 0.07, textPaint);
    }
    const cw = w * 0.025, ch = bh + w * 0.07;
    rect.set(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2);
    cursorStroke.strokeWidth = w * 0.008;
    canvas.drawRoundRect(rect, cw / 2, cw / 2, cursorPaint);
    canvas.drawRoundRect(rect, cw / 2, cw / 2, cursorStroke);
  } else if (bonusType.id === 'mash') {
    textPaint.textSize = w * 0.1; canvas.drawText(String(bonusTaps), w / 2, cy - w * 0.27, textPaint);
    canvas.drawCircle(w / 2, cy, w * 0.19 * (1 + 0.1 * bonusPulse), primaryBtnPaint);
    btnTextPaint.color = Color.WHITE; btnTextPaint.textSize = w * 0.08;
    canvas.drawText(tr('bonus.tap_button'), w / 2, cy - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2, btnTextPaint);
    drawTimeBar(canvas, cy + w * 0.27, 1 - t / mashDuration);
    textPaint.textSize = w * 0.045; canvas.drawText(tr('bonus.points', { n: Math.min(Math.floor(bonusTaps / 4), 10) }), w / 2, cy + w * 0.38, textPaint);
  } else if (bonusType.id === 'stars') {
    for (const st of stars) {
      const f = st[2] / st[3];
      starPaint.alpha = Math.floor(255 * Math.min(1, f * 3));
      drawStar(canvas, st[0], st[1], w * 0.075 * Math.min(1, (1 - f) * 6), starPaint);
    }
    for (const sp of sparkles) {
      const f = sp[2] / 0.5;
      starPaint.alpha = Math.floor(255 * f);
      for (let j = 0; j < 6; j++) {
        const a = j * Math.PI / 3, d = w * 0.12 * (1 - f);
        drawStar(canvas, sp[0] + d * Math.cos(a), sp[1] + d * Math.sin(a), w * 0.02, starPaint);
      }
    }
    starPaint.alpha = 255;
    textPaint.textSize = w * 0.06; canvas.drawText('★ ' + bonusTaps, w / 2, ub() - w * 0.2, textPaint);
    drawTimeBar(canvas, ub() - w * 0.16, 1 - t / starsDuration);
  } else {
    const r = w * 0.34, n = wheelValues.length, seg = 360 / n;
    rect.set(w / 2 - r, cy - r, w / 2 + r, cy + r);
    for (let i = 0; i < n; i++) {
      itemPaint.color = wheelValues[i] === 0 ? PC('#5C6378') : wheelValues[i] === 10 ? PC('#E0B03C') : palette[i % palette.length];
      canvas.drawArc(rect, wheelAngle + i * seg, seg, true, itemPaint);
    }
    wheelText.textSize = w * 0.065;
    for (let i = 0; i < n; i++) {
      const a = (wheelAngle + i * seg + seg / 2) * Math.PI / 180;
      const tx = w / 2 + r * 0.68 * Math.cos(a), ty = cy + r * 0.68 * Math.sin(a);
      canvas.drawText(wheelValues[i] === 0 ? '0' : '+' + wheelValues[i], tx, ty - (wheelText.descent() + wheelText.ascent()) / 2, wheelText);
    }
    canvas.drawCircle(w / 2, cy, r * 0.12, cursorPaint);
    tmpPath.reset();
    tmpPath.moveTo(w / 2 - w * 0.04, cy - r - w * 0.05); tmpPath.lineTo(w / 2 + w * 0.04, cy - r - w * 0.05); tmpPath.lineTo(w / 2, cy - r + w * 0.03);
    tmpPath.close();
    canvas.drawPath(tmpPath, cursorPaint);
    cursorStroke.strokeWidth = w * 0.006; canvas.drawPath(tmpPath, cursorStroke);
  }
  const msg = bonusResult !== 0 && bonusGain > 0 ? tr('bonus.win', { n: bonusGain })
    : bonusResult !== 0 ? tr('bonus.miss') : bonusT < bonusIntro ? tr('bonus.ready') : '';
  if (msg) {
    textPaint.textSize = w * 0.085;
    canvas.drawText(msg, w / 2, bonusType.id === 'stars' ? h * 0.55 : ub() - w * 0.08, textPaint);
  }
}

function updateBonus(dt) {
  bonusT += dt;
  if (bonusPulse > 0) bonusPulse = Math.max(0, bonusPulse - dt * 6);
  for (const s of sparkles) s[2] -= dt;
  sparkles = sparkles.filter((s) => s[2] > 0);
  const t = bonusT - bonusIntro;
  if (bonusResult === 0 && t >= 0) {
    switch (bonusType.id) {
      case 'timing':
        bonusPos += bonusDir * bonusSpeed * dt;
        if (bonusPos > 1) { bonusPos = 2 - bonusPos; bonusDir = -1; }
        if (bonusPos < -1) { bonusPos = -2 - bonusPos; bonusDir = 1; }
        break;
      case 'mash': if (t >= mashDuration) finishBonus(Math.min(Math.floor(bonusTaps / 4), 10)); break;
      case 'stars':
        starSpawn -= dt;
        if (t < starsDuration && starSpawn <= 0 && stars.length < 3) {
          const life = Math.max(0.75, 1.4 - 0.08 * bonusCount);
          stars.push([w * (0.15 + rnd() * 0.7), h * (0.36 + rnd() * 0.38), life, life]);
          starSpawn = 0.45;
        }
        for (const s of stars) s[2] -= dt;
        stars = stars.filter((s) => s[2] > 0);
        if (t >= starsDuration && stars.length === 0) finishBonus(bonusTaps);
        break;
      case 'wheel': {
        wheelAngle = (wheelAngle + wheelSpeed * dt) % 360;
        const wi = wheelIndex();
        if (wi !== lastWheelIdx) { lastWheelIdx = wi; sounds.play('WHEEL_TICK', 0.5); }
        if (t > 6) wheelStopping = true;
        if (wheelStopping) {
          wheelSpeed *= Math.exp(-1.6 * dt);
          if (wheelSpeed < 12) { wheelSpeed = 0; finishBonus(wheelValues[wheelIndex()]); }
        }
        break;
      }
      default: break;
    }
  } else if (bonusResult !== 0) {
    bonusResultT += dt;
    if (bonusResultT > 1.3) {
      if (current.crumbly) current.crumbleSafe = true;
      state = State.READY;
      sounds.playPlaylist();
    }
  }
}
