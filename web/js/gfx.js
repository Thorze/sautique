'use strict';
// Petite couche qui imite l'API de dessin d'Android (Color, Paint, RectF, Path, Canvas,
// LinearGradient) au-dessus du Canvas 2D du navigateur : le code du jeu reste très proche
// de la version Android (app/src/main/java/com/sautique/game).

const Color = {
  WHITE: 0xFFFFFFFF,
  parseColor(s) { return (0xFF000000 | parseInt(s.slice(1), 16)) >>> 0; },
  rgb(r, g, b) { return (0xFF000000 | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255)) >>> 0; },
  argb(a, r, g, b) { return (((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255)) >>> 0; },
  alpha(c) { return (c >>> 24) & 255; },
  red(c) { return (c >>> 16) & 255; },
  green(c) { return (c >>> 8) & 255; },
  blue(c) { return c & 255; },
  HSVToColor(hsv) {
    const [h, s, v] = hsv;
    const c = v * s, x = c * (1 - Math.abs(((h / 60) % 2) - 1)), m = v - c;
    let r = 0, g = 0, b = 0;
    if (h < 60) [r, g, b] = [c, x, 0]; else if (h < 120) [r, g, b] = [x, c, 0];
    else if (h < 180) [r, g, b] = [0, c, x]; else if (h < 240) [r, g, b] = [0, x, c];
    else if (h < 300) [r, g, b] = [x, 0, c]; else [r, g, b] = [c, 0, x];
    return Color.rgb(Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
  },
};

function css(c, alphaMul = 1) {
  const a = ((c >>> 24) & 255) / 255 * alphaMul;
  return `rgba(${(c >>> 16) & 255},${(c >>> 8) & 255},${c & 255},${a.toFixed(3)})`;
}

class LinearGradient {
  constructor(x0, y0, x1, y1, colors, positions, tile) {
    this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
    if (Array.isArray(colors)) { this.colors = colors; this.positions = positions; }
    else { this.colors = [colors, positions]; this.positions = null; }
    this.tile = tile || 'clamp';
  }
  make(ctx) {
    const g = ctx.createLinearGradient(this.x0, this.y0, this.x1, this.y1);
    const n = this.colors.length;
    this.colors.forEach((c, i) => {
      const p = this.positions ? this.positions[i] : i / (n - 1);
      g.addColorStop(Math.min(1, Math.max(0, p)), css(c));
    });
    return g;
  }
}
const Shader = { TileMode: { CLAMP: 'clamp', MIRROR: 'mirror' } };

class Paint {
  constructor(props) {
    this.color = 0xFF000000;
    this.style = 'fill';
    this.strokeWidth = 1;
    this.textSize = 16;
    this.textAlign = 'left';
    this.bold = false;
    this.shader = null;
    this.shadow = null;
    this.strokeCap = 'butt';
    this.strokeJoin = 'miter';
    if (props) Object.assign(this, props);
  }
  get alpha() { return (this.color >>> 24) & 255; }
  set alpha(a) { this.color = ((this.color & 0x00FFFFFF) | ((Math.max(0, Math.min(255, a | 0))) << 24)) >>> 0; }
  font() { return `${this.bold ? '700 ' : ''}${this.textSize}px -apple-system, system-ui, "Segoe UI", Roboto, sans-serif`; }
  measureText(t) { const ctx = Canvas.measureCtx; ctx.font = this.font(); return ctx.measureText(t).width; }
  ascent() { return -0.93 * this.textSize; }
  descent() { return 0.24 * this.textSize; }
  setShadowLayer(blur, dx, dy, c) { this.shadow = { blur, dx, dy, c }; }
}
const Align = { LEFT: 'left', CENTER: 'center', RIGHT: 'right' };
const Style = { FILL: 'fill', STROKE: 'stroke' };

class RectF {
  constructor(l = 0, t = 0, r = 0, b = 0) { this.set(l, t, r, b); }
  set(l, t, r, b) {
    if (l instanceof RectF) { this.left = l.left; this.top = l.top; this.right = l.right; this.bottom = l.bottom; return; }
    this.left = l; this.top = t; this.right = r; this.bottom = b;
  }
  width() { return this.right - this.left; }
  height() { return this.bottom - this.top; }
  centerX() { return (this.left + this.right) / 2; }
  centerY() { return (this.top + this.bottom) / 2; }
  contains(x, y) { return x >= this.left && x < this.right && y >= this.top && y < this.bottom; }
  sort() {
    if (this.left > this.right) [this.left, this.right] = [this.right, this.left];
    if (this.top > this.bottom) [this.top, this.bottom] = [this.bottom, this.top];
  }
}

class Path {
  constructor() { this.reset(); }
  reset() { this.p = new Path2D(); }
  moveTo(x, y) { this.p.moveTo(x, y); }
  lineTo(x, y) { this.p.lineTo(x, y); }
  quadTo(x1, y1, x2, y2) { this.p.quadraticCurveTo(x1, y1, x2, y2); }
  cubicTo(x1, y1, x2, y2, x3, y3) { this.p.bezierCurveTo(x1, y1, x2, y2, x3, y3); }
  close() { this.p.closePath(); }
}

class Canvas {
  constructor(ctx) { this.ctx = ctx; }
  save() { this.ctx.save(); }
  restore() { this.ctx.restore(); }
  translate(x, y) { this.ctx.translate(x, y); }
  rotate(deg, px = 0, py = 0) {
    const c = this.ctx;
    c.translate(px, py); c.rotate(deg * Math.PI / 180); c.translate(-px, -py);
  }
  scale(sx, sy, px = 0, py = 0) {
    const c = this.ctx;
    c.translate(px, py); c.scale(sx, sy); c.translate(-px, -py);
  }
  clipRect(l, t, r, b) { const c = this.ctx; c.beginPath(); c.rect(l, t, r - l, b - t); c.clip(); }

  _apply(p) {
    const c = this.ctx;
    let style;
    if (p.shader) { style = p.shader.make(c); c.globalAlpha = p.alpha / 255; }
    else { style = css(p.color); c.globalAlpha = 1; }
    if (p.style === 'stroke') {
      c.strokeStyle = style; c.lineWidth = p.strokeWidth; c.lineCap = p.strokeCap; c.lineJoin = p.strokeJoin;
    } else c.fillStyle = style;
    if (p.shadow) {
      c.shadowBlur = p.shadow.blur; c.shadowOffsetX = p.shadow.dx; c.shadowOffsetY = p.shadow.dy;
      c.shadowColor = css(p.shadow.c, p.alpha / 255);
    } else c.shadowColor = 'transparent';
  }
  _paint(p) { const c = this.ctx; if (p.style === 'stroke') c.stroke(); else c.fill(); c.globalAlpha = 1; }

  drawRect(a, b, cc, d, e) {
    let l, t, r, bo, p;
    if (a instanceof RectF) { l = a.left; t = a.top; r = a.right; bo = a.bottom; p = b; } else { l = a; t = b; r = cc; bo = d; p = e; }
    this._apply(p); const c = this.ctx; c.beginPath(); c.rect(l, t, r - l, bo - t); this._paint(p);
  }
  drawRoundRect(rect, rx, ry, p) {
    this._apply(p); const c = this.ctx; c.beginPath();
    const r = Math.max(0, Math.min(rx, rect.width() / 2, rect.height() / 2));
    if (c.roundRect) c.roundRect(rect.left, rect.top, rect.width(), rect.height(), r);
    else {
      const l = rect.left, t = rect.top, R = rect.right, B = rect.bottom;
      c.moveTo(l + r, t); c.arcTo(R, t, R, B, r); c.arcTo(R, B, l, B, r); c.arcTo(l, B, l, t, r); c.arcTo(l, t, R, t, r); c.closePath();
    }
    this._paint(p);
  }
  drawOval(rect, p) {
    const rx = Math.abs(rect.width() / 2), ry = Math.abs(rect.height() / 2);
    if (rx <= 0 || ry <= 0) return;
    this._apply(p); const c = this.ctx; c.beginPath();
    c.ellipse(rect.centerX(), rect.centerY(), rx, ry, 0, 0, Math.PI * 2); this._paint(p);
  }
  drawCircle(x, y, r, p) {
    if (r <= 0) return;
    this._apply(p); const c = this.ctx; c.beginPath(); c.arc(x, y, r, 0, Math.PI * 2); this._paint(p);
  }
  drawArc(rect, start, sweep, useCenter, p) {
    const rx = Math.abs(rect.width() / 2), ry = Math.abs(rect.height() / 2);
    if (rx <= 0 || ry <= 0) return;
    this._apply(p); const c = this.ctx; c.beginPath();
    const a0 = start * Math.PI / 180, a1 = (start + sweep) * Math.PI / 180;
    if (useCenter) c.moveTo(rect.centerX(), rect.centerY());
    c.ellipse(rect.centerX(), rect.centerY(), rx, ry, 0, a0, a1, sweep < 0);
    if (useCenter) c.closePath();
    this._paint(p);
  }
  drawLine(x0, y0, x1, y1, p) {
    const c = this.ctx; const old = p.style; p.style = 'stroke'; this._apply(p);
    c.beginPath(); c.moveTo(x0, y0); c.lineTo(x1, y1); c.stroke(); c.globalAlpha = 1; p.style = old;
  }
  drawPath(path, p) {
    this._apply(p); const c = this.ctx;
    if (p.style === 'stroke') c.stroke(path.p); else c.fill(path.p);
    c.globalAlpha = 1;
  }
  drawText(text, x, y, p) {
    this._apply(p); const c = this.ctx;
    c.font = p.font(); c.textAlign = p.textAlign; c.textBaseline = 'alphabetic';
    c.fillStyle = p.shader ? p.shader.make(c) : css(p.color);
    c.fillText(text, x, y);
    c.globalAlpha = 1; c.shadowColor = 'transparent';
  }
}
Canvas.measureCtx = document.createElement('canvas').getContext('2d');

const rnd = () => Math.random();
const rndInt = (n) => Math.floor(Math.random() * n);
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
