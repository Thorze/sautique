'use strict';
// Apparence et dessin du personnage (équivalent de Character.kt).

function shapeHeight(scaleH) {
  if (lookSpecial !== Skins.NONE) return specialHeight(lookSpecial) * scaleH;
  return scaleH * (lookShape === 'tour' ? 0.82 : lookShape === 'boule' ? 0.58 : playerHeight);
}

function drawPlayer(canvas) {
  drawRibbon(canvas);
  if (state !== State.FALLING && (current.contains(px, pz) || next.contains(px, pz))) {
    const s = clamp(1 - py / 4, 0.4, 1);
    const r = playerHalf * 1.5 * s;
    const cx = sx(px, pz), cy = sy(px, 0, pz);
    rect.set(cx - 1.2247449 * k * r, cy - 0.70710677 * k * r, cx + 1.2247449 * k * r, cy + 0.70710677 * k * r);
    canvas.drawOval(rect, shadowPaint);
  }
  let scaleW = 1, scaleH = 1;
  if (state === State.CHARGING) {
    const r = charge / maxChargeTime; scaleH = 1 - 0.4 * r; scaleW = 1 + 0.18 * r;
  } else if (landSquash > 0) {
    scaleH = 1 - 0.22 * landSquash; scaleW = 1 + 0.1 * landSquash;
  } else if (state === State.JUMPING) {
    if (jumpTrick === 'tuck') {
      const f = clamp(jumpT / jumpDuration, 0, 1);
      const s = 1 - 0.4 * Math.sin(f * Math.PI);
      scaleH = s; scaleW = 1 + 0.15 * (1 - s);
    } else { scaleH = 1.1; scaleW = 0.95; }
  }
  const scared = scareT > 0;
  let ox = sx(px, pz);
  const oy = sy(px, 0, pz);
  if (scared) ox += Math.sin(time * 75) * k * 0.035 * Math.max(0.3, scareT / scareDuration);

  const falling = state === State.FALLING;
  let rotation = 0, panic = false, front = false;
  const chute = falling && fallAnim === 'parachute' && fallT > 0.25 && fallT < 1.9;
  if (falling) {
    if (fallAnim === 'spin') rotation = fallT * 620 * (alongX ? 1 : -1);
    else if (fallAnim === 'panic') { panic = true; ox += Math.sin(time * 70) * k * 0.05; }
    else if (fallAnim === 'parachute') { if (chute) rotation = Math.sin(fallT * 5) * 10; }
    else if (fallAnim === 'cartoon') front = fallT < 1.1;
  }
  const bodyH = shapeHeight(scaleH);
  let flipScaleX = 1;
  if (state === State.JUMPING && jumpTrick !== 'none') {
    const f = clamp(jumpT / jumpDuration, 0, 1);
    const e = f * f * (3 - 2 * f);
    const dirSign = alongX ? 1 : -1;
    if (jumpTrick === 'frontflip') rotation = 360 * e * dirSign;
    else if (jumpTrick === 'doubleflip') rotation = 720 * e * dirSign;
    else if (jumpTrick === 'backflip') rotation = -360 * e * dirSign;
    else if (jumpTrick === 'pirouette') flipScaleX = Math.cos(e * 2 * Math.PI);
  }
  canvas.save();
  const pivotY = oy + isoY(0, py + bodyH / 2, 0);
  if (rotation !== 0) canvas.rotate(rotation, ox, pivotY);
  if (flipScaleX !== 1) canvas.scale(Math.abs(flipScaleX) < 0.05 ? 0.05 : flipScaleX, 1, ox, pivotY);
  if (chute) drawParachute(canvas, ox, oy + isoY(0, py + bodyH, 0));
  drawCharacter(canvas, ox, oy, py, scaleW, scaleH, alongX, scared || panic, front, state === State.REVIVE);
  canvas.restore();

  const headY = oy + isoY(0, py + bodyH + 0.5, 0);
  if (scared) {
    const elapsed = scareDuration - scareT;
    const r = k * 0.2 * Math.min(1, elapsed / 0.15);
    canvas.drawCircle(ox, headY, r, bubblePaint);
    bangPaint.textSize = r * 1.5;
    canvas.drawText('!', ox, headY - (bangPaint.descent() + bangPaint.ascent()) / 2, bangPaint);
  }
  if (falling) {
    if (fallAnim === 'panic') drawBubble(canvas, ox, headY, tr('fall.panic'));
    else if (fallAnim === 'spin') { if (fallT < 0.8) drawBubble(canvas, ox, headY - k * 0.3, tr('fall.spin')); }
    else if (fallAnim === 'cartoon') {
      if (fallT >= 0.2 && fallT <= 0.8) drawBubble(canvas, ox, headY, '…');
      else if (fallT > 0.8 && fallT <= 1.5) drawBubble(canvas, ox, headY, tr('fall.oops'));
    } else if (fallAnim === 'parachute' && fallT >= 1.9 && fallT <= 2.6) drawBubble(canvas, ox, headY, tr('fall.crack'));
  }
}

function drawParachute(canvas, cx, topY) {
  const half = k * 0.6, canopyBase = topY - k * 0.55;
  accStroke.color = Color.parseColor('#2E3A59'); accStroke.strokeWidth = k * 0.015;
  canvas.drawLine(cx - half, canopyBase, cx - k * 0.08, topY, accStroke);
  canvas.drawLine(cx, canopyBase, cx, topY, accStroke);
  canvas.drawLine(cx + half, canopyBase, cx + k * 0.08, topY, accStroke);
  rect.set(cx - half, canopyBase - half * 0.8, cx + half, canopyBase + half * 0.8);
  accPaint.color = Color.parseColor('#E53950'); canvas.drawArc(rect, 180, 180, true, accPaint);
  accPaint.color = Color.WHITE;
  canvas.drawArc(rect, 216, 36, true, accPaint);
  canvas.drawArc(rect, 288, 36, true, accPaint);
}

function drawBubble(canvas, x, bottomY, text) {
  bubbleTextPaint.textSize = w * 0.04;
  const tw = bubbleTextPaint.measureText(text);
  const pad = w * 0.025, bh = w * 0.07, y = bottomY - w * 0.02;
  rect.set(x - tw / 2 - pad, y - bh, x + tw / 2 + pad, y);
  tmpPath.reset();
  tmpPath.moveTo(x - w * 0.015, y - 1); tmpPath.lineTo(x + w * 0.015, y - 1); tmpPath.lineTo(x, y + w * 0.025); tmpPath.close();
  bubbleStroke.strokeWidth = w * 0.005;
  canvas.drawRoundRect(rect, bh / 2, bh / 2, bubblePaint);
  canvas.drawRoundRect(rect, bh / 2, bh / 2, bubbleStroke);
  canvas.drawPath(tmpPath, bubblePaint);
  canvas.drawText(text, x, rect.centerY() - (bubbleTextPaint.descent() + bubbleTextPaint.ascent()) / 2, bubbleTextPaint);
}

/** Dessine le personnage, pieds en (ox, oy) à la hauteur y0. */
function drawCharacter(canvas, ox, oy, y0, scaleW, scaleH, dirX, scared, front, angel = false) {
  canvas.save();
  canvas.translate(ox, oy);
  if (lookSpecial !== Skins.NONE) {
    drawSpecialCharacter(canvas, y0, scaleW, scaleH, dirX, scared, front, angel);
    canvas.restore();
    return;
  }
  const round = lookShape === 'cylindre' || lookShape === 'boule';
  const a = scaleW * (lookShape === 'tour' ? 0.18 : lookShape === 'cylindre' ? 0.25 : lookShape === 'boule' ? 0.29 : playerHalf);
  const hgt = shapeHeight(scaleH);
  const y1 = y0 + hgt;
  const fdx = front ? 0 : dirX ? 0.866 : -0.866;
  const fdy = front ? 0.5 : -0.5;
  const inAir = y0 > 0.05 || state === State.FALLING;
  const PC = Color.parseColor;

  // --- Dos ---
  const bx = -fdx * a * k * 0.9;
  const midY = isoY(0, y0 + hgt * 0.55, 0), topY = isoY(0, y1, 0), botY = isoY(0, y0, 0);
  const wing = (side, color, len, flap) => {
    canvas.save();
    canvas.rotate(side * (25 + flap), side * a * k * 0.6, midY);
    accPaint.color = color;
    rect.set(side * a * k * 0.4, midY - len * 0.35, side * a * k * 0.4 + side * len, midY + len * 0.35);
    rect.sort();
    canvas.drawOval(rect, accPaint);
    canvas.restore();
  };
  if (lookBack === 'cape') {
    const flutter = inAir ? k * 0.25 : k * 0.06 * Math.sin(time * 3);
    const by = botY + (inAir ? -k * 0.05 : k * 0.04);
    tmpPath.reset();
    tmpPath.moveTo(-a * k * 0.9, topY + k * 0.08); tmpPath.lineTo(a * k * 0.9, topY + k * 0.08);
    tmpPath.lineTo(a * k * 1.15 + bx * 1.4 - fdx * flutter, by); tmpPath.lineTo(-a * k * 1.15 + bx * 1.4 - fdx * flutter, by);
    tmpPath.close();
    accPaint.color = tinted(Slot.BACK, PC('#C62839')); canvas.drawPath(tmpPath, accPaint);
  } else if (lookBack === 'sac') {
    accPaint.color = tinted(Slot.BACK, PC('#7A5230'));
    rect.set(bx - a * k * 0.55, midY - k * 0.2, bx + a * k * 0.55, midY + k * 0.17);
    canvas.drawRoundRect(rect, k * 0.08, k * 0.08, accPaint);
    accPaint.color = tinted(Slot.BACK, PC('#5C3C22'), 0.75);
    rect.set(bx - a * k * 0.4, midY + k * 0.02, bx + a * k * 0.4, midY + k * 0.13);
    canvas.drawRoundRect(rect, k * 0.04, k * 0.04, accPaint);
  } else if (lookBack === 'papillon') {
    const flap = Math.sin(time * (inAir ? 18 : 4)) * 15;
    for (const side of [-1, 1]) {
      wing(side, tinted(Slot.BACK, PC('#FF8FC7')), k * 0.42, flap);
      wing(side, tinted(Slot.BACK, PC('#9C6BFF'), 0.7), k * 0.26, flap - 30);
    }
  } else if (lookBack === 'jetpack') {
    for (const dx of [-1, 1]) {
      const cx = bx + dx * a * k * 0.38;
      accPaint.color = tinted(Slot.BACK, PC('#9AA3B5'));
      rect.set(cx - k * 0.08, midY - k * 0.22, cx + k * 0.08, midY + k * 0.16);
      canvas.drawRoundRect(rect, k * 0.06, k * 0.06, accPaint);
      accPaint.color = PC('#E53950');
      canvas.drawRect(cx - k * 0.08, midY - k * 0.12, cx + k * 0.08, midY - k * 0.08, accPaint);
      if (inAir) {
        const fl = k * (0.22 + 0.08 * Math.sin(time * 40 + dx));
        tmpPath.reset();
        tmpPath.moveTo(cx - k * 0.06, midY + k * 0.16); tmpPath.lineTo(cx + k * 0.06, midY + k * 0.16); tmpPath.lineTo(cx, midY + k * 0.16 + fl);
        tmpPath.close();
        accPaint.color = PC('#FF9F1C'); canvas.drawPath(tmpPath, accPaint);
      }
    }
  }
  if (angel) {
    const flap = Math.sin(time * 14) * 22;
    for (const side of [-1, 1]) {
      wing(side, Color.argb(240, 255, 255, 255), k * 0.55, flap);
      wing(side, Color.argb(255, 235, 240, 250), k * 0.36, flap - 25);
    }
  }

  // --- Chaussures (sous le corps) ---
  if (lookShoes !== Skins.NONE) {
    const fa = lookShape === 'boule' ? a * 0.6 : a;
    for (let i = 0; i <= 1; i++) {
      const wx = i === 0 ? -fa * 0.85 : fa * 0.15;
      const wz = i === 0 ? fa * 0.15 : -fa * 0.85;
      const sx0 = isoX(wx, wz) + fdx * k * 0.07, sy0 = isoY(wx, y0, wz) + k * 0.05;
      const sw = k * 0.12, sh = k * 0.065;
      if (lookShoes === 'fusees') {
        accPaint.color = tinted(Slot.SHOES, PC('#9AA3B5'));
        rect.set(sx0 - sw * 0.8, sy0 - k * 0.12, sx0 + sw * 0.8, sy0 + sh * 0.5);
        canvas.drawRoundRect(rect, sw * 0.4, sw * 0.4, accPaint);
        accPaint.color = PC('#E53950');
        canvas.drawRect(sx0 - sw * 0.8, sy0 - k * 0.07, sx0 + sw * 0.8, sy0 - k * 0.04, accPaint);
        if (inAir) {
          const fl = k * (0.14 + 0.06 * Math.sin(time * 40 + i));
          tmpPath.reset();
          tmpPath.moveTo(sx0 - sw * 0.55, sy0 + sh * 0.5); tmpPath.lineTo(sx0 + sw * 0.55, sy0 + sh * 0.5); tmpPath.lineTo(sx0, sy0 + sh * 0.5 + fl);
          tmpPath.close();
          accPaint.color = PC('#FF9F1C'); canvas.drawPath(tmpPath, accPaint);
          accPaint.color = PC('#FFE066'); canvas.drawCircle(sx0, sy0 + sh * 0.5 + fl * 0.3, sw * 0.25, accPaint);
        }
      } else if (lookShoes === 'bottes') {
        accPaint.color = tinted(Slot.SHOES, PC('#4E2E1A'), 0.55);
        rect.set(sx0 - sw * 1.1, sy0 - sh * 0.9, sx0 + sw * 1.1, sy0 + sh * 1.15); canvas.drawOval(rect, accPaint);
        accPaint.color = tinted(Slot.SHOES, PC('#7B4A2A'));
        rect.set(sx0 - sw * 1.05, sy0 - sh * 1.1, sx0 + sw * 1.05, sy0 + sh * 0.8); canvas.drawOval(rect, accPaint);
      } else {
        accPaint.color = tinted(Slot.SHOES, Color.WHITE);
        rect.set(sx0 - sw, sy0 - sh, sx0 + sw, sy0 + sh); canvas.drawOval(rect, accPaint);
        accPaint.color = PC('#E53950');
        canvas.drawRect(sx0 - sw * 0.5, sy0 - sh * 0.35, sx0 + sw * 0.5, sy0 + sh * 0.1, accPaint);
      }
    }
  }

  // --- Corps ---
  if (lookShape === 'cylindre') {
    const rx = 1.2247449 * k * a, ry = 0.70710677 * k * a;
    const yb = isoY(0, y0, 0), yt = isoY(0, y1, 0);
    rect.set(-rx, yb - ry, rx, yb + ry);
    canvas.drawOval(rect, playerLeft);
    canvas.drawRect(-rx, yt, rx, yb, playerLeft);
    canvas.save();
    canvas.clipRect(0, yt - ry * 2, rx, yb + ry);
    canvas.drawOval(rect, playerRight);
    canvas.drawRect(-rx, yt, rx, yb, playerRight);
    canvas.restore();
    rect.set(-rx, yt - ry, rx, yt + ry);
    canvas.drawOval(rect, playerTop);
  } else if (lookShape === 'boule') {
    const cy = isoY(0, y0 + hgt / 2, 0), rx = a * k * 1.15, ryv = hgt / 2 * k;
    rect.set(-rx, cy - ryv, rx, cy + ryv); canvas.drawOval(rect, playerRight);
    rect.set(-rx * 0.98, cy - ryv, rx * 0.84, cy + ryv * 0.86); canvas.drawOval(rect, playerLeft);
    canvas.drawCircle(-rx * 0.38, cy - ryv * 0.45, rx * 0.2, playerTop);
  } else {
    quad(tmpPath, -a, y1, a, -a, y1, -a, -a, y0, -a, -a, y0, a); canvas.drawPath(tmpPath, playerLeft);
    quad(tmpPath, -a, y1, -a, a, y1, -a, a, y0, -a, -a, y0, -a); canvas.drawPath(tmpPath, playerRight);
    quad(tmpPath, -a, y1, -a, a, y1, -a, a, y1, a, -a, y1, a); canvas.drawPath(tmpPath, playerTop);
  }

  // --- Yeux ---
  const eyeY = y0 + hgt * (lookShape === 'boule' ? 0.6 : 0.68);
  const eyeR = k * 0.058 * (scared ? 1.25 : 1);
  const ex = [0, 0], ey = [0, 0];
  for (let i = 0; i <= 1; i++) {
    let wx, wz;
    if (!round) {
      if (front) { wx = i === 0 ? -a : -a * 0.45; wz = i === 0 ? -a * 0.45 : -a; }
      else { const t = i === 0 ? 0.25 : 0.68; wx = dirX ? -a + 2 * a * t : -a; wz = dirX ? -a : -a + 2 * a * t; }
    } else {
      const rr = lookShape === 'boule' ? a * 0.68 : a;
      const th = front ? (i === 0 ? -2.0 : -2.75) : dirX ? (i === 0 ? -1.9 : -1.3) : (i === 0 ? 3.47 : 2.87);
      wx = rr * Math.cos(th); wz = rr * Math.sin(th);
    }
    ex[i] = isoX(wx, wz); ey[i] = isoY(wx, eyeY, wz);
  }
  const closed = !scared && (time % 3.2) > 3.05;
  const pupilDir = front ? 0 : dirX ? 1 : -1;
  for (let i = 0; i <= 1; i++) {
    if (closed) canvas.drawRect(ex[i] - eyeR, ey[i] - eyeR * 0.15, ex[i] + eyeR, ey[i] + eyeR * 0.15, pupilPaint);
    else {
      canvas.drawCircle(ex[i], ey[i], eyeR, eyeWhitePaint);
      canvas.drawCircle(ex[i] + pupilDir * eyeR * 0.35, ey[i] - eyeR * 0.15, eyeR * (scared ? 0.32 : 0.5), pupilPaint);
    }
  }

  // --- Lunettes ---
  const bridge = (r) => {
    const dx = ex[1] - ex[0], dy = ey[1] - ey[0], len = Math.sqrt(dx * dx + dy * dy);
    if (len > 2 * r) canvas.drawLine(ex[0] + dx / len * r, ey[0] + dy / len * r, ex[1] - dx / len * r, ey[1] - dy / len * r, accStroke);
  };
  if (lookGlasses === 'rondes') {
    accStroke.color = tinted(Slot.GLASSES, PC('#2E2A28')); accStroke.strokeWidth = k * 0.022;
    const r = eyeR * 1.55;
    for (let i = 0; i <= 1; i++) canvas.drawCircle(ex[i], ey[i], r, accStroke);
    bridge(r);
  } else if (lookGlasses === 'soleil') {
    accPaint.color = tinted(Slot.GLASSES, Color.rgb(25, 25, 35));
    accStroke.color = tinted(Slot.GLASSES, Color.rgb(25, 25, 35)); accStroke.strokeWidth = k * 0.025;
    for (let i = 0; i <= 1; i++) {
      rect.set(ex[i] - eyeR * 1.6, ey[i] - eyeR * 1.15, ex[i] + eyeR * 1.6, ey[i] + eyeR * 1.15);
      canvas.drawRoundRect(rect, eyeR * 0.6, eyeR * 0.6, accPaint);
    }
    bridge(eyeR * 1.4);
    accPaint.color = Color.argb(150, 255, 255, 255);
    for (let i = 0; i <= 1; i++) canvas.drawCircle(ex[i] - eyeR * 0.6, ey[i] - eyeR * 0.4, eyeR * 0.3, accPaint);
  } else if (lookGlasses === 'pixel') {
    accPaint.color = tinted(Slot.GLASSES, Color.rgb(20, 20, 25));
    const gw = eyeR * 1.7, gy = (ey[0] + ey[1]) / 2;
    canvas.drawRect(Math.min(ex[0], ex[1]) - gw, gy - eyeR, Math.max(ex[0], ex[1]) + gw, gy - eyeR * 0.45, accPaint);
    for (let i = 0; i <= 1; i++) {
      canvas.drawRect(ex[i] - gw, gy - eyeR * 0.5, ex[i] + gw * 0.9, gy + eyeR * 0.3, accPaint);
      canvas.drawRect(ex[i] - gw * 0.6, gy + eyeR * 0.25, ex[i] + gw * 0.5, gy + eyeR * 0.85, accPaint);
    }
    accPaint.color = Color.WHITE;
    for (let i = 0; i <= 1; i++) canvas.drawRect(ex[i] - gw * 0.7, gy - eyeR * 0.35, ex[i] - gw * 0.35, gy, accPaint);
  } else if (lookGlasses === 'monocle') {
    const i = 1;
    accStroke.color = tinted(Slot.GLASSES, PC('#D4A017')); accStroke.strokeWidth = k * 0.022;
    canvas.drawCircle(ex[i], ey[i], eyeR * 1.6, accStroke);
    accStroke.strokeWidth = k * 0.01;
    canvas.drawLine(ex[i] + eyeR * 1.2, ey[i] + eyeR * 1.1, ex[i] + eyeR * 1.6, ey[i] + eyeR * 5, accStroke);
  } else if (lookGlasses === 'coeur') {
    accPaint.color = tinted(Slot.GLASSES, PC('#FF4D8D'));
    const sz = eyeR * 1.2;
    for (let i = 0; i <= 1; i++) drawHeart(canvas, ex[i], ey[i], sz, accPaint);
    accStroke.color = tinted(Slot.GLASSES, PC('#FF4D8D')); accStroke.strokeWidth = k * 0.02;
    bridge(sz * 1.1);
  }

  // --- Chapeau ---
  const ball = lookShape === 'boule';
  const hs = a * k * (ball ? 0.85 : 1.1);
  const hy = isoY(0, y1, 0) + (ball ? hs * 0.5 : 0);
  switch (lookHat) {
    case 'casquette': {
      accPaint.color = tinted(Slot.HAT, PC('#E53950'));
      rect.set(-hs * 0.85, hy - hs * 0.75, hs * 0.85, hy + hs * 0.35); canvas.drawArc(rect, 180, 180, true, accPaint);
      accPaint.color = tinted(Slot.HAT, PC('#B32638'), 0.75);
      const vx = fdx * hs * 0.9, vy = hy - hs * 0.2 + fdy * hs * 0.25;
      rect.set(vx - hs * 0.55, vy - hs * 0.2, vx + hs * 0.55, vy + hs * 0.2); canvas.drawOval(rect, accPaint);
      break;
    }
    case 'hautdeforme':
      accPaint.color = tinted(Slot.HAT, PC('#22222C'));
      rect.set(-hs * 1.2, hy - hs * 0.37, hs * 1.2, hy + hs * 0.27); canvas.drawOval(rect, accPaint);
      canvas.drawRect(-hs * 0.72, hy - hs * 1.45, hs * 0.72, hy - hs * 0.05, accPaint);
      accPaint.color = PC('#E53950');
      canvas.drawRect(-hs * 0.72, hy - hs * 0.45, hs * 0.72, hy - hs * 0.2, accPaint);
      accPaint.color = tinted(Slot.HAT, PC('#3A3A48'), 1.15);
      rect.set(-hs * 0.72, hy - hs * 1.65, hs * 0.72, hy - hs * 1.25); canvas.drawOval(rect, accPaint);
      break;
    case 'magicien':
      accPaint.color = tinted(Slot.HAT, PC('#2F3FA8'));
      rect.set(-hs * 1.15, hy - hs * 0.3, hs * 1.15, hy + hs * 0.25); canvas.drawOval(rect, accPaint);
      tmpPath.reset();
      tmpPath.moveTo(-hs * 0.75, hy - hs * 0.05); tmpPath.lineTo(hs * 0.75, hy - hs * 0.05); tmpPath.lineTo(fdx * hs * 0.4, hy - hs * 2.1);
      tmpPath.close(); canvas.drawPath(tmpPath, accPaint);
      accPaint.color = PC('#FFD54F');
      drawStar(canvas, -hs * 0.15, hy - hs * 0.6, hs * 0.2, accPaint);
      drawStar(canvas, hs * 0.25, hy - hs * 1.15, hs * 0.14, accPaint);
      canvas.drawCircle(fdx * hs * 0.4, hy - hs * 2.1, hs * 0.1, accPaint);
      break;
    case 'viking':
      accPaint.color = PC('#F3EEDF');
      for (const side of [-1, 1]) {
        tmpPath.reset();
        tmpPath.moveTo(side * hs * 0.55, hy - hs * 0.35);
        tmpPath.quadTo(side * hs * 1.35, hy - hs * 0.6, side * hs * 1.15, hy - hs * 1.35);
        tmpPath.quadTo(side * hs * 1.0, hy - hs * 0.75, side * hs * 0.45, hy - hs * 0.6);
        tmpPath.close(); canvas.drawPath(tmpPath, accPaint);
      }
      accPaint.color = tinted(Slot.HAT, PC('#8D96A8'));
      rect.set(-hs * 0.85, hy - hs * 0.85, hs * 0.85, hy + hs * 0.35); canvas.drawArc(rect, 180, 180, true, accPaint);
      accPaint.color = PC('#C98A00');
      canvas.drawRect(-hs * 0.85, hy - hs * 0.3, hs * 0.85, hy - hs * 0.15, accPaint);
      break;
    case 'bonnet':
      accPaint.color = tinted(Slot.HAT, PC('#3F8EFC'));
      rect.set(-hs * 0.9, hy - hs * 1.1, hs * 0.9, hy + hs * 0.5); canvas.drawArc(rect, 180, 180, true, accPaint);
      accPaint.color = tinted(Slot.HAT, PC('#2C6ED5'), 0.8);
      rect.set(-hs * 0.95, hy - hs * 0.42, hs * 0.95, hy - hs * 0.08); canvas.drawRoundRect(rect, hs * 0.15, hs * 0.15, accPaint);
      accPaint.color = Color.WHITE; canvas.drawCircle(0, hy - hs * 1.15, hs * 0.22, accPaint);
      break;
    case 'cowboy':
      accPaint.color = tinted(Slot.HAT, PC('#A0673A'));
      rect.set(-hs * 1.5, hy - hs * 0.4, hs * 1.5, hy + hs * 0.25); canvas.drawOval(rect, accPaint);
      tmpPath.reset();
      tmpPath.moveTo(-hs * 0.7, hy - hs * 0.15); tmpPath.lineTo(-hs * 0.6, hy - hs * 1.05);
      tmpPath.quadTo(0, hy - hs * 0.75, hs * 0.6, hy - hs * 1.05); tmpPath.lineTo(hs * 0.7, hy - hs * 0.15);
      tmpPath.close();
      accPaint.color = tinted(Slot.HAT, PC('#B57A45'), 1.1); canvas.drawPath(tmpPath, accPaint);
      accPaint.color = PC('#4E2E1A'); canvas.drawRect(-hs * 0.68, hy - hs * 0.42, hs * 0.68, hy - hs * 0.25, accPaint);
      break;
    case 'aureole': {
      accStroke.color = tinted(Slot.HAT, PC('#FFD54F')); accStroke.strokeWidth = k * 0.035;
      const ay = hy - hs * 0.7 + Math.sin(time * 3) * hs * 0.08;
      rect.set(-hs * 0.85, ay - hs * 0.25, hs * 0.85, ay + hs * 0.25); canvas.drawOval(rect, accStroke);
      break;
    }
    case 'couronne':
      accPaint.color = tinted(Slot.HAT, PC('#F5C542'));
      tmpPath.reset();
      tmpPath.moveTo(-hs * 0.8, hy + hs * 0.05); tmpPath.lineTo(-hs * 0.8, hy - hs * 0.75); tmpPath.lineTo(-hs * 0.4, hy - hs * 0.35);
      tmpPath.lineTo(0, hy - hs * 0.95); tmpPath.lineTo(hs * 0.4, hy - hs * 0.35); tmpPath.lineTo(hs * 0.8, hy - hs * 0.75);
      tmpPath.lineTo(hs * 0.8, hy + hs * 0.05); tmpPath.close(); canvas.drawPath(tmpPath, accPaint);
      accPaint.color = PC('#E53950'); canvas.drawCircle(0, hy - hs * 0.2, hs * 0.13, accPaint);
      break;
    default: break;
  }
  if (angel) {
    accStroke.color = PC('#FFD54F'); accStroke.strokeWidth = k * 0.035;
    const ay = isoY(0, y1, 0) - k * 0.3;
    rect.set(-a * k * 0.9, ay - a * k * 0.28, a * k * 0.9, ay + a * k * 0.28); canvas.drawOval(rect, accStroke);
  }
  canvas.restore();
}

function tinted(slot, original, f = 1) {
  const t = tintColors.get(slot) || 0;
  return t === 0 ? original : shade(t, f);
}

function loadLook() {
  const pick = (slot) => { const it = Skins.find(slot, profile.selected(slot)); return profile.available(it) ? it.id : Skins.def(slot); };
  lookSpecial = pick(Slot.CHARACTER); lookShape = pick(Slot.SHAPE); lookGlasses = pick(Slot.GLASSES); lookHat = pick(Slot.HAT); lookShoes = pick(Slot.SHOES);
  lookBack = pick(Slot.BACK); lookTrail = pick(Slot.TRAIL); lookColor = pick(Slot.COLOR);
  for (const slot of Skins.tintable) {
    const t = Skins.tint(profile.selectedTint(slot));
    tintColors.set(slot, profile.ownsTint(t) ? t.color : 0);
  }
  const c = Skins.find(Slot.COLOR, lookColor).color;
  playerTop.color = shade(c, 1.18); playerLeft.color = c; playerRight.color = shade(c, 0.78);
  const r = k * 0.35, PC = Color.parseColor;
  const shader = (f) => {
    if (lookColor === Skins.RAINBOW) return new LinearGradient(-r, -r * 2, r, 0, ['#FF4D4D', '#FFC93C', '#3DBE6E', '#3F8EFC', '#8E5CD9'].map((s) => shade(PC(s), f)), null);
    if (lookColor === Skins.GALAXY) return new LinearGradient(-r, -r * 2, r, 0, ['#1B1446', '#6A3FB5', '#E0609E'].map((s) => shade(PC(s), f)), null);
    return null;
  };
  playerTop.shader = shader(1.15); playerLeft.shader = shader(1); playerRight.shader = shader(0.75);
}

function currentLook(slot) {
  switch (slot) {
    case Slot.CHARACTER: return lookSpecial; case Slot.SHAPE: return lookShape; case Slot.GLASSES: return lookGlasses; case Slot.HAT: return lookHat;
    case Slot.SHOES: return lookShoes; case Slot.BACK: return lookBack; case Slot.TRAIL: return lookTrail;
    default: return lookColor;
  }
}
