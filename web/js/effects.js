'use strict';
// Effets visuels, traînées et ciel (équivalent de Effects.kt).

const PK = { CIRCLE: 0, STAR: 1, NOTE: 2, HEART: 3, BUBBLE: 4 };
const mkParticle = (x, y, vx, vy, life, size, color, gravity, kind = PK.CIRCLE) => ({ x, y, vx, vy, life, maxLife: life, size, color, gravity, kind });

function spawnDust(x, z) {
  const bx = sx(x, z) + camX, by = sy(x, 0, z) + camY;
  for (let i = 0; i < 8; i++) {
    const a = rnd() * 6.283, sp = k * (0.6 + rnd() * 0.8);
    particles.push(mkParticle(bx, by, Math.cos(a) * sp, Math.sin(a) * sp * 0.4 - k * 0.3, 0.45, k * (0.05 + rnd() * 0.05), Color.argb(170, 235, 230, 225), 0));
  }
}

function spawnSparkles(x, y, color, count = 12) {
  for (let i = 0; i < count; i++) {
    const a = rnd() * 6.283, sp = k * (1.5 + rnd() * 2);
    particles.push(mkParticle(x, y, Math.cos(a) * sp, Math.sin(a) * sp - k * 1.2, 0.7, k * (0.06 + rnd() * 0.05), color, k * 4, PK.STAR));
  }
}

function spawnDebris(p) {
  const bx = sx(p.cx, p.cz) + camX, by = sy(p.cx, 0, p.cz) + camY;
  for (let i = 0; i < 14; i++) {
    particles.push(mkParticle(bx + (rnd() - 0.5) * k * p.half * 2.4, by + rnd() * k * 0.8, (rnd() - 0.5) * k * 2, -rnd() * k * 1.5,
      1.1, k * (0.08 + rnd() * 0.1), shade(p.baseColor, 0.7 + rnd() * 0.3), k * 9));
  }
}

function shake(amount) {
  shakeT = 0.35;
  shakeAmp = Math.max(shakeAmp * (shakeT / 0.35), amount * k);
}

const RIBBON_LIFE = 0.35;

function updateEffects(dt) {
  for (const p of particles) {
    p.life -= dt;
    p.vy += p.gravity * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.vx *= 1 - 1.5 * dt;
  }
  particles = particles.filter((p) => p.life > 0);
  if (shakeT > 0) {
    shakeT = Math.max(0, shakeT - dt);
    const f = shakeT / 0.35;
    shakeX = (rnd() * 2 - 1) * shakeAmp * f; shakeY = (rnd() * 2 - 1) * shakeAmp * f;
  } else { shakeX = 0; shakeY = 0; shakeAmp = 0; }
  for (const r of ribbon) r[2] -= dt;
  ribbon = ribbon.filter((r) => r[2] > 0);
  for (const b of banners) b.life -= dt;
  banners = banners.filter((b) => b.life > 0);
}

function drawStar(canvas, x, y, r, paint) {
  tmpPath.reset();
  for (let i = 0; i < 10; i++) {
    const rr = i % 2 === 0 ? r : r * 0.45;
    const a = -Math.PI / 2 + i * Math.PI / 5;
    const X = x + rr * Math.cos(a), Y = y + rr * Math.sin(a);
    if (i === 0) tmpPath.moveTo(X, Y); else tmpPath.lineTo(X, Y);
  }
  tmpPath.close();
  canvas.drawPath(tmpPath, paint);
}

function drawHeart(canvas, x, y, sz, paint) {
  tmpPath.reset();
  tmpPath.moveTo(x, y + sz * 0.9);
  tmpPath.cubicTo(x - sz * 1.6, y - sz * 0.2, x - sz * 0.6, y - sz * 1.3, x, y - sz * 0.45);
  tmpPath.cubicTo(x + sz * 0.6, y - sz * 1.3, x + sz * 1.6, y - sz * 0.2, x, y + sz * 0.9);
  tmpPath.close();
  canvas.drawPath(tmpPath, paint);
}

function drawParticles(canvas) {
  for (const p of particles) {
    const a = clamp(p.life / p.maxLife, 0, 1);
    accPaint.color = p.color;
    accPaint.alpha = Math.floor(Color.alpha(p.color) * a);
    const x = p.x - camX + shakeX, y = p.y - camY + shakeY;
    switch (p.kind) {
      case PK.STAR: drawStar(canvas, x, y, p.size * (0.6 + 0.4 * a), accPaint); break;
      case PK.HEART: drawHeart(canvas, x, y, p.size, accPaint); break;
      case PK.NOTE:
        bangPaint.color = p.color; bangPaint.alpha = accPaint.alpha; bangPaint.textSize = p.size * 3;
        canvas.drawText('♪', x, y, bangPaint);
        bangPaint.color = Color.parseColor('#E53950');
        break;
      case PK.BUBBLE: {
        accStroke.color = p.color; accStroke.alpha = accPaint.alpha; accStroke.strokeWidth = k * 0.03;
        const r = p.size * (1.6 - 0.6 * a);
        accPaint.color = Color.argb(Math.floor(90 * a), 200, 230, 255);
        canvas.drawCircle(x, y, r, accPaint);
        canvas.drawCircle(x, y, r, accStroke);
        accStroke.alpha = 255;
        break;
      }
      default: canvas.drawCircle(x, y, p.size * (1.5 - 0.5 * a), accPaint);
    }
  }
  accPaint.alpha = 255;
}

function drawBanners(canvas) {
  let y = insetTop + h * 0.17 + w * 0.3;
  for (const b of banners) {
    const a = clamp(b.life / 0.4, 0, 1);
    bubbleTextPaint.textSize = w * 0.042;
    const tw = bubbleTextPaint.measureText(b.text), bh = w * 0.085;
    rect.set(w / 2 - tw / 2 - w * 0.04, y, w / 2 + tw / 2 + w * 0.04, y + bh);
    bubblePaint.alpha = Math.floor(235 * a);
    canvas.drawRoundRect(rect, bh / 2, bh / 2, bubblePaint);
    bubblePaint.alpha = 255;
    bubbleTextPaint.alpha = Math.floor(255 * a);
    canvas.drawText(b.text, w / 2, rect.centerY() - (bubbleTextPaint.descent() + bubbleTextPaint.ascent()) / 2, bubbleTextPaint);
    bubbleTextPaint.alpha = 255;
    y += bh + w * 0.02;
  }
}

const SKY_NAMES = { 'Journée': '☀️ Grand soleil', 'Coucher de soleil': '🌇 Coucher de soleil', 'Nuit': '🌙 La nuit tombe', 'Aurore': '🌌 Aurore boréale', 'Espace': "🚀 Dans l'espace !" };

function drawSky(canvas) {
  const sky = Theme.sky(score);
  if (sky.top !== skyTop || sky.bottom !== skyBottom) {
    skyTop = sky.top; skyBottom = sky.bottom;
    bgPaint.shader = new LinearGradient(0, 0, 0, h, sky.top, sky.bottom);
  }
  if (sky.name !== skyName) {
    if (skyName && state !== State.GAME_OVER && score > 0) banners.push({ text: SKY_NAMES[sky.name] || sky.name, life: 2.2 });
    skyName = sky.name;
  }
  canvas.drawRect(0, 0, w, h, bgPaint);
  const cx = w * 0.82, cy = h * 0.33, r = w * 0.075;
  if (sky.night < 0.9) {
    accPaint.color = Color.argb(Math.floor(200 * (1 - sky.night)), 255, 236, 170); canvas.drawCircle(cx, cy, r * 1.35, accPaint);
    accPaint.color = Color.argb(Math.floor(255 * (1 - sky.night)), 255, 214, 102); canvas.drawCircle(cx, cy, r, accPaint);
  }
  if (sky.night > 0.1) {
    accPaint.color = Color.argb(Math.floor(235 * sky.night), 245, 243, 225); canvas.drawCircle(cx, cy, r * 0.85, accPaint);
    accPaint.color = Theme.lerp(sky.top, sky.bottom, cy / h); accPaint.alpha = Math.floor(255 * sky.night);
    canvas.drawCircle(cx + r * 0.38, cy - r * 0.2, r * 0.72, accPaint);
  }
  if (sky.night > 0.05) {
    for (const s of skyStars) {
      const tw = 0.5 + 0.5 * Math.sin(time * 2 + s[2]);
      accPaint.color = Color.argb(Math.floor(220 * sky.night * tw), 255, 255, 255);
      canvas.drawCircle(s[0] * w, s[1] * h, w * 0.004 * (1 + s[2] % 1), accPaint);
    }
  }
  accPaint.alpha = 255;
}

function emitTrail(dt) {
  if (lookTrail === Skins.NONE) return;
  if (lookTrail === 'trait') { ribbon.push([sx(px, pz) + camX, sy(px, py + 0.28, pz) + camY, RIBBON_LIFE]); return; }
  trailTimer -= dt;
  if (trailTimer > 0) return;
  trailTimer = lookTrail === 'arcenciel' ? 0.02 : 0.05;
  const x = sx(px, pz) + camX + (rnd() - 0.5) * k * 0.3, y = sy(px, py + 0.25, pz) + camY + (rnd() - 0.5) * k * 0.3;
  const drift = (rnd() - 0.5) * k * 0.4, PC = Color.parseColor;
  switch (lookTrail) {
    case 'etoiles': particles.push(mkParticle(x, y, drift, k * 0.4, 0.8, k * 0.16, PC('#FFB300'), 0, PK.STAR)); break;
    case 'bulles': particles.push(mkParticle(x, y, drift, -k * 0.6, 0.9, k * 0.11, PC('#3F8EFC'), 0, PK.BUBBLE)); break;
    case 'notes': particles.push(mkParticle(x, y, drift, -k * 0.7, 0.9, k * 0.13, PC(['#3F8EFC', '#8E5CD9', '#2E3A59', '#E53950'][rndInt(4)]), 0, PK.NOTE)); break;
    case 'coeurs': particles.push(mkParticle(x, y, drift, -k * 0.5, 0.9, k * 0.12, PC('#FF4D8D'), 0, PK.HEART)); break;
    case 'arcenciel': particles.push(mkParticle(sx(px, pz) + camX, sy(px, py + 0.25, pz) + camY, 0, 0, 0.7, k * 0.17,
      Color.HSVToColor([(time * 400) % 360, 0.8, 1]), 0)); break;
    default: break;
  }
}

function drawRibbon(canvas) {
  if (ribbon.length < 2) return;
  ribbonPaint.color = (lookColor === Skins.RAINBOW || lookColor === Skins.GALAXY) ? Color.parseColor('#FFD54F') : playerLeft.color;
  let start = 0;
  while (start < ribbon.length - 1) {
    const end = Math.min(start + 5, ribbon.length - 1);
    tmpPath.reset();
    tmpPath.moveTo(ribbon[start][0] - camX + shakeX, ribbon[start][1] - camY + shakeY);
    for (let i = start + 1; i <= end; i++) tmpPath.lineTo(ribbon[i][0] - camX + shakeX, ribbon[i][1] - camY + shakeY);
    const f = clamp(ribbon[end][2] / RIBBON_LIFE, 0, 1);
    ribbonPaint.alpha = Math.floor(115 * f);
    ribbonPaint.strokeWidth = k * 0.22 * (0.35 + 0.65 * f);
    canvas.drawPath(tmpPath, ribbonPaint);
    start = end;
  }
}
