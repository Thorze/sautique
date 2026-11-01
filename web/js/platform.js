'use strict';
// Plateformes (équivalent de Platform.kt).

const Kind = { NORMAL: 'normal', CRUMBLY: 'crumbly', TRAMPOLINE: 'trampoline', MINI: 'mini', MOVING: 'moving' };

class Platform {
  constructor(baseX, baseZ, half, round, color, kind = Kind.NORMAL, moveAlongX = true) {
    this.baseX = baseX; this.baseZ = baseZ; this.half = half; this.round = round;
    this.kind = kind; this.moveAlongX = moveAlongX; this.baseColor = color;
    this.moveAmp = 0.8; this.movePhase = rnd() * 6.283; this.offset = 0; this.frozen = false;
    this.coin = 0; this.heart = false;
    this.crumbleT = -1; this.crumbleDur = 2; this.crumbleSafe = false; this.dropY = 0; this.dropV = 0;
    this.bounce = 0;
    this.topPaint = P({ color: shade(color, 1.08) });
    this.leftPaint = P({ color: shade(color, 0.82) });
    this.rightPaint = P({ color: shade(color, 0.62) });
    this.markPaint = P({ color: shade(color, 0.9) });
    this.detailPaint = P();
    this.strokePaint = P({ style: Style.STROKE, strokeJoin: 'round', strokeCap: 'round' });
    this.topPath = new Path(); this.leftPath = new Path(); this.rightPath = new Path();
    this.crackPath = new Path(); this.arrowPath = new Path();
    this.topOval = new RectF(); this.sideRect = new RectF(); this.bottomOval = new RectF();
    this.markOval = new RectF(); this.oval = new RectF();
    const d = platformDepth;
    if (round) {
      const rx = 1.2247449 * k * half, ry = 0.70710677 * k * half, dp = d * k;
      this.topOval.set(-rx, -ry, rx, ry);
      this.sideRect.set(-rx, 0, rx, dp);
      this.bottomOval.set(-rx, dp - ry, rx, dp + ry);
      this.leftPaint.shader = new LinearGradient(-rx, 0, rx, 0, shade(color, 0.88), shade(color, 0.55));
    } else {
      const a = half;
      quad(this.topPath, -a, 0, -a, a, 0, -a, a, 0, a, -a, 0, a);
      quad(this.rightPath, -a, 0, -a, a, 0, -a, a, -d, -a, -a, -d, -a);
      quad(this.leftPath, -a, 0, a, -a, 0, -a, -a, -d, -a, -a, -d, a);
    }
    if (kind === Kind.CRUMBLY) {
      for (let i = 0; i < 4; i++) {
        const angle = (i + rnd() * 0.6) * (Math.PI / 2);
        this.crackPath.moveTo(0, 0);
        let r = 0, a2 = angle;
        while (r < half * 0.8) {
          r += half * (0.18 + rnd() * 0.12);
          a2 += (rnd() - 0.5) * 0.9;
          const rr = Math.min(r, half * 0.85);
          const dx = Math.cos(a2) * rr, dz = Math.sin(a2) * rr;
          this.crackPath.lineTo(isoX(dx, dz), isoY(dx, 0, dz));
        }
      }
    }
    if (kind === Kind.MOVING) {
      const l = half * 0.6, hh = half * 0.2;
      const p = (t, side) => { const wx = moveAlongX ? t : side, wz = moveAlongX ? side : t; return [isoX(wx, wz), isoY(wx, 0, wz)]; };
      const [x0, y0] = p(-l, 0), [x1, y1] = p(l, 0);
      const ap = this.arrowPath;
      ap.moveTo(x0, y0); ap.lineTo(x1, y1);
      let [a1x, a1y] = p(l - hh, hh), [a2x, a2y] = p(l - hh, -hh);
      ap.moveTo(a1x, a1y); ap.lineTo(x1, y1); ap.lineTo(a2x, a2y);
      [a1x, a1y] = p(-l + hh, hh); [a2x, a2y] = p(-l + hh, -hh);
      ap.moveTo(a1x, a1y); ap.lineTo(x0, y0); ap.lineTo(a2x, a2y);
    }
    const m = 0.13;
    this.markOval.set(-1.2247449 * k * m, -0.70710677 * k * m, 1.2247449 * k * m, 0.70710677 * k * m);
  }

  get cx() { return this.baseX + (this.kind === Kind.MOVING && this.moveAlongX ? this.offset : 0); }
  get cz() { return this.baseZ + (this.kind === Kind.MOVING && !this.moveAlongX ? this.offset : 0); }
  get crumbly() { return this.kind === Kind.CRUMBLY; }
  get collapsed() { return this.dropY > 0; }
  get farDepth() { return this.round ? this.cx + this.cz + this.half * 1.4142135 : this.cx + this.cz + 2 * this.half; }

  update(dt) {
    if (this.kind === Kind.MOVING && !this.frozen) { this.movePhase += dt * 1.6; this.offset = Math.sin(this.movePhase) * this.moveAmp; }
    if (this.bounce > 0) this.bounce = Math.max(0, this.bounce - dt * 4);
  }

  contains(x, z) {
    if (this.collapsed) return false;
    const dx = x - this.cx, dz = z - this.cz;
    return this.round ? dx * dx + dz * dz <= this.half * this.half : Math.abs(dx) <= this.half && Math.abs(dz) <= this.half;
  }

  edgeMargin(x, z) {
    const dx = x - this.cx, dz = z - this.cz;
    return this.round ? this.half - Math.sqrt(dx * dx + dz * dz) : this.half - Math.max(Math.abs(dx), Math.abs(dz));
  }

  flatOval(r, dy = 0) {
    this.oval.set(-1.2247449 * k * r, -0.70710677 * k * r + dy, 1.2247449 * k * r, 0.70710677 * k * r + dy);
  }

  draw(canvas) {
    canvas.save();
    let sh = 0;
    if (this.crumbleT >= 0 && !this.collapsed) {
      const f = clamp(this.crumbleT / this.crumbleDur, 0, 1);
      sh = Math.sin(time * 55) * k * (0.015 + 0.05 * f);
    }
    canvas.translate(sx(this.cx, this.cz) + sh, sy(this.cx, 0, this.cz) + this.dropY * k);
    if (this.round) {
      canvas.drawOval(this.bottomOval, this.leftPaint);
      canvas.drawRect(this.sideRect, this.leftPaint);
      canvas.drawOval(this.topOval, this.topPaint);
    } else {
      canvas.drawPath(this.leftPath, this.leftPaint);
      canvas.drawPath(this.rightPath, this.rightPaint);
      canvas.drawPath(this.topPath, this.topPaint);
    }
    const dp = this.detailPaint, st = this.strokePaint;
    if (this.kind === Kind.TRAMPOLINE) {
      const sink = this.bounce * k * 0.12;
      this.flatOval(this.half * 0.72); dp.color = Color.parseColor('#E53950'); canvas.drawOval(this.oval, dp);
      this.flatOval(this.half * 0.6, sink); dp.color = Color.parseColor('#2E3A59'); canvas.drawOval(this.oval, dp);
      st.color = Color.argb(90, 255, 255, 255); st.strokeWidth = k * 0.02;
      this.flatOval(this.half * 0.4, sink); canvas.drawOval(this.oval, st);
    } else if (this.kind === Kind.MINI) {
      textPaint.textSize = k * 0.32;
      canvas.drawText('+' + Scoring.MINI_BONUS, 0, -(textPaint.descent() + textPaint.ascent()) / 2, textPaint);
    } else if (this.kind === Kind.MOVING) {
      st.color = Color.argb(150, 255, 255, 255); st.strokeWidth = k * 0.04;
      canvas.drawPath(this.arrowPath, st);
      canvas.drawOval(this.markOval, this.markPaint);
    } else canvas.drawOval(this.markOval, this.markPaint);
    if (this.crumbly) {
      const f = this.crumbleT < 0 ? 0 : clamp(this.crumbleT / this.crumbleDur, 0, 1);
      st.color = Color.argb(Math.floor(110 + 145 * f), 60, 45, 40); st.strokeWidth = k * 0.035;
      canvas.drawPath(this.crackPath, st);
    }
    if (this.heart) this.drawHeartPickup(canvas); else if (this.coin > 0) this.drawCoin(canvas);
    canvas.restore();
  }

  drawHeartPickup(canvas) {
    const beat = 1 + 0.12 * Math.abs(Math.sin(time * 5));
    const cy = isoY(0, 0.65, 0) + Math.sin(time * 3) * k * 0.05;
    const dp = this.detailPaint;
    this.flatOval(0.16); dp.color = Color.argb(50, 0, 0, 0); canvas.drawOval(this.oval, dp);
    dp.color = Color.argb(90, 255, 120, 160); canvas.drawCircle(0, cy, k * 0.3 * beat, dp);
    dp.color = Color.parseColor('#FF4D8D'); drawHeart(canvas, 0, cy, k * 0.16 * beat, dp);
  }

  drawCoin(canvas) {
    const big = this.coin > 1;
    const r = k * (big ? 0.24 : 0.17);
    const bob = Math.sin(time * 3) * k * 0.05;
    const cy = isoY(0, 0.55, 0) + bob;
    const spin = Math.max(0.12, Math.abs(Math.cos(time * 3.2)));
    const dp = this.detailPaint, st = this.strokePaint;
    this.flatOval(0.14); dp.color = Color.argb(50, 0, 0, 0); canvas.drawOval(this.oval, dp);
    this.oval.set(-r * spin, cy - r, r * spin, cy + r);
    dp.color = Color.parseColor(big ? '#FFB300' : '#FFC93C'); canvas.drawOval(this.oval, dp);
    st.color = Color.parseColor('#C98A00'); st.strokeWidth = k * 0.025; canvas.drawOval(this.oval, st);
    if (big && spin > 0.5) {
      textPaint.textSize = r * 1.1;
      canvas.drawText('5', 0, cy - (textPaint.descent() + textPaint.ascent()) / 2, textPaint);
    }
  }
}
