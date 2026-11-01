'use strict';
// Personnages complets achetés avec les pièces (équivalent de Specials.kt).
// Dessinés de profil tournés vers la droite, en unités du monde : pieds en (0, 0), y vers le haut.

function specialHeight(id) {
  switch (id) {
    case 'bonhomme': return 1.0; case 'perenoel': return 0.95; case 'donut': return 0.6;
    case 'sapin': return 0.98; case 'hippo': return 0.62; case 'girafe': return 1.25;
    case 'capybara': return 0.56; default: return 0.55;
  }
}

const decoColors = [0xFFE53950, 0xFFFFC93C, 0xFF3F8EFC, 0xFF3DBE6E, 0xFFFFFFFF, 0xFF8E5CD9];

function drawSpecialCharacter(canvas, y0, scaleW, scaleH, dirX, scared, front, angel) {
  const base = isoY(0, y0, 0);
  const sw = k * scaleW, sh = k * scaleH;
  const X = (x) => x * sw, Y = (y) => base - y * sh;
  const p = accPaint;
  const BLACK = 0xFF1B1B1B, WHITE = 0xFFFFFFFF;

  const circle = (x, y, r, c) => { p.color = c; canvas.drawCircle(X(x), Y(y), r * (sw + sh) / 2, p); };
  const oval = (cx, cy, rx, ry, c) => { p.color = c; rect.set(X(cx - rx), Y(cy + ry), X(cx + rx), Y(cy - ry)); canvas.drawOval(rect, p); };
  const box = (l, b, r, t, c, radius = 0) => {
    p.color = c; rect.set(X(l), Y(t), X(r), Y(b));
    if (radius > 0) canvas.drawRoundRect(rect, radius * sw, radius * sh, p); else canvas.drawRect(rect, p);
  };
  const poly = (c, ...pts) => {
    tmpPath.reset(); tmpPath.moveTo(X(pts[0]), Y(pts[1]));
    for (let i = 2; i < pts.length; i += 2) tmpPath.lineTo(X(pts[i]), Y(pts[i + 1]));
    tmpPath.close(); p.color = c; canvas.drawPath(tmpPath, p);
  };
  const line = (xa, ya, xb, yb, c, width) => {
    accStroke.color = c; accStroke.strokeWidth = width * k;
    canvas.drawLine(X(xa), Y(ya), X(xb), Y(yb), accStroke);
  };
  const closed = !scared && (time % 3.2) > 3.05;
  const eye = (x, y, r) => {
    const rr = r * (scared ? 1.25 : 1);
    if (closed) { box(x - rr, y - rr * 0.15, x + rr, y + rr * 0.15, BLACK); return; }
    circle(x, y, rr, WHITE);
    circle(x + (front ? 0 : rr * 0.35), y + rr * 0.1, rr * (scared ? 0.32 : 0.5), BLACK);
  };
  const dotEye = (x, y, r) => {
    const rr = r * (scared ? 1.4 : 1);
    if (closed) { box(x - rr, y - rr * 0.2, x + rr, y + rr * 0.2, BLACK); return; }
    circle(x, y, rr, BLACK);
    circle(x - rr * 0.3, y + rr * 0.3, rr * 0.3, 0x99FFFFFF);
  };

  canvas.save();
  if (!front && !dirX) canvas.scale(-1, 1);

  if (angel) {
    const hgt = specialHeight(lookSpecial), flap = Math.sin(time * 14) * 0.06;
    oval(-0.32, hgt * 0.6 + flap, 0.22, 0.1, 0xF0FFFFFF);
    oval(0.32, hgt * 0.6 + flap, 0.22, 0.1, 0xF0FFFFFF);
  }

  switch (lookSpecial) {
    case 'bonhomme': {
      for (const [cy, r] of [[0.24, 0.24], [0.6, 0.18], [0.87, 0.14]]) {
        circle(0, cy, r, 0xFFC9D6E8);
        circle(-r * 0.12, cy + r * 0.08, r * 0.88, 0xFFF7FAFF);
      }
      line(0.12, 0.62, 0.32, 0.76, 0xFF7B4A2A, 0.025);
      line(-0.12, 0.62, -0.3, 0.74, 0xFF7B4A2A, 0.025);
      box(-0.15, 0.72, 0.15, 0.78, 0xFFE53950, 0.03);
      box(-0.12, 0.58, -0.06, 0.75, 0xFFC62839, 0.02);
      dotEye(0.04, 0.62, 0.022);
      dotEye(0.05, 0.52, 0.022);
      dotEye(0.02, 0.92, 0.022);
      dotEye(0.09, 0.91, 0.022);
      poly(0xFFFF8C1A, 0.1, 0.87, 0.1, 0.83, 0.27, 0.85);
      box(-0.14, 0.98, 0.14, 1.01, 0xFF22222C);
      box(-0.09, 1.0, 0.09, 1.13, 0xFF22222C);
      break;
    }
    case 'perenoel': {
      oval(-0.09, 0.04, 0.08, 0.05, 0xFF22222C);
      oval(0.1, 0.04, 0.08, 0.05, 0xFF22222C);
      oval(0, 0.3, 0.26, 0.27, 0xFFC62839);
      oval(-0.03, 0.33, 0.21, 0.23, 0xFFE53950);
      box(-0.25, 0.06, 0.25, 0.11, WHITE, 0.03);
      box(-0.26, 0.25, 0.26, 0.31, 0xFF22222C);
      box(0.05, 0.24, 0.13, 0.32, 0xFFF5C542, 0.01);
      box(0.07, 0.26, 0.11, 0.30, 0xFF22222C);
      circle(0.03, 0.64, 0.15, 0xFFF9C9A8);
      oval(0.03, 0.55, 0.17, 0.12, WHITE);
      oval(0.1, 0.6, 0.08, 0.03, WHITE);
      eye(0.05, 0.68, 0.035);
      eye(0.13, 0.67, 0.035);
      circle(0.17, 0.62, 0.035, 0xFFF08A8A);
      poly(0xFFE53950, -0.13, 0.74, 0.19, 0.74, -0.08, 0.98, -0.2, 0.9);
      box(-0.15, 0.71, 0.21, 0.78, WHITE, 0.035);
      circle(-0.2, 0.9, 0.055, WHITE);
      break;
    }
    case 'donut': {
      const cy = 0.3;
      const ring = (rOut, rIn, c, dx = 0) => {
        tmpPath.reset(); tmpPath.fillType = 'evenodd';
        tmpPath.addCircle(X(dx), Y(cy), rOut * (sw + sh) / 2);
        tmpPath.addCircle(X(dx), Y(cy), rIn * (sw + sh) / 2);
        p.color = c; canvas.drawPath(tmpPath, p);
        tmpPath.fillType = 'nonzero';
      };
      ring(0.3, 0.09, 0xFFB97A3D);
      ring(0.28, 0.1, 0xFFD9A066, -0.01);
      ring(0.25, 0.12, 0xFFFF7BAC, -0.01);
      for (let i = 0; i < 12; i++) {
        const a = i * 0.52 + 0.3, r = i % 2 === 0 ? 0.2 : 0.16;
        const x = r * Math.cos(a), y = cy + r * Math.sin(a);
        if (y > cy + 0.06 && Math.abs(x) < 0.08) continue;
        box(x - 0.018, y - 0.008, x + 0.018, y + 0.008, decoColors[i % decoColors.length], 0.008);
      }
      eye(-0.07, cy + 0.17, 0.045);
      eye(0.07, cy + 0.17, 0.045);
      accStroke.color = 0xFF7A3B1E; accStroke.strokeWidth = k * 0.02;
      rect.set(X(-0.07), Y(cy - 0.12), X(0.07), Y(cy - 0.2));
      canvas.drawArc(rect, 0, 180, false, accStroke);
      break;
    }
    case 'sapin': {
      box(-0.05, 0, 0.05, 0.13, 0xFF7B4A2A);
      for (const [b, t, half] of [[0.1, 0.5, 0.3], [0.35, 0.72, 0.24], [0.58, 0.9, 0.17]]) {
        poly(0xFF1F7A4A, -half, b, half, b, 0, t);
        poly(0xFF2E9E5E, -half, b, 0, b, 0, t);
      }
      const balls = [[-0.16, 0.18], [0.12, 0.22], [-0.02, 0.3], [-0.12, 0.42], [0.14, 0.44], [0.05, 0.64], [-0.07, 0.7]];
      balls.forEach(([x, y], i) => circle(x, y, 0.03, decoColors[i % 4]));
      p.color = 0xFFFFD54F;
      drawStar(canvas, X(0), Y(0.94), 0.08 * (sw + sh) / 2, p);
      eye(0.0, 0.52, 0.04);
      eye(0.1, 0.51, 0.04);
      break;
    }
    case 'hippo': {
      const c = 0xFF8E8AA8, dark = 0xFF6F6A8C;
      for (const x of [-0.22, -0.08, 0.07, 0.19]) box(x, 0, x + 0.08, 0.15, dark, 0.02);
      oval(-0.02, 0.3, 0.3, 0.2, dark);
      oval(-0.04, 0.32, 0.27, 0.17, c);
      oval(-0.04, 0.24, 0.2, 0.07, 0xFFB4AFC9);
      box(-0.36, 0.3, -0.3, 0.34, dark, 0.02);
      oval(0.27, 0.4, 0.16, 0.14, c);
      oval(0.39, 0.32, 0.13, 0.11, 0xFFB4AFC9);
      circle(0.45, 0.37, 0.018, 0xFF4A4660);
      circle(0.4, 0.38, 0.018, 0xFF4A4660);
      oval(0.2, 0.53, 0.035, 0.03, dark);
      oval(0.29, 0.54, 0.035, 0.03, dark);
      eye(0.24, 0.47, 0.04);
      eye(0.33, 0.47, 0.04);
      break;
    }
    case 'girafe': {
      const c = 0xFFF2C14E, spot = 0xFFB5651D;
      for (const x of [-0.19, -0.11, 0.06, 0.14]) {
        box(x, 0.02, x + 0.05, 0.38, c);
        box(x, 0, x + 0.05, 0.04, 0xFF4E2E1A);
      }
      line(-0.23, 0.47, -0.3, 0.32, 0xFF7B4A2A, 0.015);
      oval(0, 0.46, 0.25, 0.15, c);
      poly(c, 0.1, 0.5, 0.21, 0.55, 0.33, 1.06, 0.24, 1.08);
      for (const [x, y] of [[-0.1, 0.48], [0.04, 0.42], [0.12, 0.52], [-0.02, 0.54], [0.2, 0.68], [0.25, 0.86]]) oval(x, y, 0.04, 0.03, spot);
      oval(0.31, 1.1, 0.1, 0.07, c);
      oval(0.39, 1.07, 0.06, 0.05, 0xFFE8C9A0);
      line(0.27, 1.15, 0.26, 1.24, 0xFF7B4A2A, 0.02);
      line(0.32, 1.15, 0.32, 1.24, 0xFF7B4A2A, 0.02);
      circle(0.26, 1.24, 0.022, 0xFF4E2E1A);
      circle(0.32, 1.24, 0.022, 0xFF4E2E1A);
      oval(0.22, 1.14, 0.04, 0.02, c);
      eye(0.31, 1.13, 0.03);
      eye(0.36, 1.12, 0.025);
      break;
    }
    case 'capybara': {
      const c = 0xFFA0703F, dark = 0xFF7E5530;
      for (const x of [-0.22, -0.12, 0.04, 0.12]) box(x, 0, x + 0.07, 0.12, dark, 0.02);
      box(-0.3, 0.08, 0.2, 0.42, dark, 0.16);
      box(-0.28, 0.1, 0.18, 0.42, c, 0.15);
      box(0.08, 0.17, 0.4, 0.46, dark, 0.1);
      box(0.08, 0.19, 0.38, 0.46, 0xFF8E6236, 0.1);
      oval(0.37, 0.33, 0.035, 0.03, 0xFF3A2614);
      oval(0.14, 0.47, 0.04, 0.03, dark);
      dotEye(0.25, 0.4, 0.025);
      circle(0.22, 0.5, 0.055, 0xFFFF9F1C);
      oval(0.25, 0.56, 0.03, 0.012, 0xFF3DBE6E);
      break;
    }
  }
  canvas.restore();

  if (angel) {
    accStroke.color = 0xFFFFD54F; accStroke.strokeWidth = k * 0.035;
    const ay = Y(specialHeight(lookSpecial) + 0.15);
    rect.set(-k * 0.2, ay - k * 0.06, k * 0.2, ay + k * 0.06);
    canvas.drawOval(rect, accStroke);
  }
}
