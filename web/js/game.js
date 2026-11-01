'use strict';
// Cœur du jeu (équivalent de GameView.kt) : état, sauts, atterrissages, boucle de jeu.
// Vue isométrique : écranX = (x - z) * cos30 * k ; écranY = -(x + z) * sin30 * k - y * k.

const State = {
  READY: 'ready', CHARGING: 'charging', JUMPING: 'jumping', FALLING: 'falling', BONUS: 'bonus',
  GAME_OVER: 'gameover', LEADERBOARD: 'leaderboard', CUSTOMIZE: 'customize', SETTINGS: 'settings',
  PAUSED: 'paused', MISSIONS: 'missions', REVIVE: 'revive',
};
const FALL_ANIMS = ['spin', 'panic', 'parachute', 'cartoon'];
const TRICKS = ['frontflip', 'backflip', 'pirouette', 'tuck'];

const cos30 = 0.8660254, sin30 = 0.5;
// Réglages du gameplay (unités du monde)
const maxChargeTime = 1.4, maxReach = 5.6, platformDepth = 1.3, playerHalf = 0.24, playerHeight = 0.55;
const bonusEvery = 10, bonusIntro = 1.2, mashDuration = 3, starsDuration = 6;
const wheelValues = [2, 5, 0, 3, 10, 1, 4, 0];
const crumbleFromScore = 25, trampolineFromScore = 8, miniFromScore = 15, movingFromScore = 20;
const scareMargin = 0.12, scareDuration = 1.2;
const lifeEvery = 30, maxLives = 3, reviveDuration = 1.5;
const palette = ['#FF8A65', '#4DB6AC', '#FFD54F', '#9575CD', '#81C784', '#F06292', '#64B5F6', '#ECEFF1'].map(Color.parseColor);

// --- Etat ---
let state = State.READY, score = 0, best = profile.best, newBest = false;
let runTime = 0, coinsThisRun = 0, pendingBounce = false, bounceDelay = 0, autoJump = false;
let pausedFrom = State.READY, missionsReturnState = State.READY, boardTab = 0, pseudoConflict = false;
let pendingBuy = null, pendingTint = null;
let particles = [], banners = [], ribbon = [], popups = [];
let shakeT = 0, shakeAmp = 0, shakeX = 0, shakeY = 0;
let skyTop = 0, skyBottom = 0, skyName = '';
const skyStars = Array.from({ length: 70 }, () => [rnd(), rnd() * 0.75, rnd() * 6.28]);
let lastWheelIdx = -1, settingsReturnState = State.READY;
const sfxSlider = new RectF(), musicSlider = new RectF();
let dragSlider = 0;
let boardScroll = 0, boardMaxScroll = 0, boardListTop = 0, boardListBottom = 0, boardDragY = -1, boardDragStart = 0, boardScrollToMe = false;
let boardReturnState = State.READY, worldStatus = 'idle', worldEntries = [], worldRank = null, personalRuns = [];
let syncing = false;
let buttons = [];
let platforms = [], current = null, next = null;

// Personnage
let px = 0, pz = 0, py = 0, alongX = true, charge = 0;
let jumpT = 0, jumpDuration = 0.5, jumpFrom = 0, jumpDist = 0, jumpHeight = 0, jumpTrick = 'none';
let fallVy = 0, fallT = 0, fallAnim = 'spin';
let lookSpecial = 'none', lookShape = 'cube', lookGlasses = 'none', lookHat = 'none', lookShoes = 'none', lookColor = 'orange';
let lookBack = 'none', lookTrail = 'none', trailTimer = 0;
const tintColors = new Map();
let lives = 0, reviveT = 0, reviveFromX = 0, reviveFromY = 0, reviveFromZ = 0, reviveTarget = null;
let unlockedLabels = [];
let custSlot = Slot.SHAPE, custReturnState = State.READY, custMsg = '', custMsgT = 0;
let landSquash = 0, gameOverTime = 0, gameOverReason = null, time = 0, jumps = 0, scareT = 0, crumbleHintShown = false;
let perfectStreak = 0, perfectBarAlpha = 0, perfectPulse = 0, lastPerfectPoints = 2, shownStreak = 3;
// Bonus
let bonusCount = 0, bonusT = 0, bonusPos = -1, bonusDir = 1, bonusSpeed = 1.4, bonusResult = 0, bonusResultT = 0;
let bonusType = BONUS_TYPES[0], bonusGain = 0, bonusTaps = 0, bonusPulse = 0;
let stars = [], sparkles = [], starSpawn = 0, wheelAngle = 0, wheelSpeed = 0, wheelStopping = false;
// Caméra
let camX = 0, camY = 0, camTargetX = 0, camTargetY = 0;
// Dimensions (pixels physiques)
let w = 0, h = 0, k = 1, insetTop = 0, insetBottom = 0;
const ub = () => h - insetBottom;

// --- Peintures ---
const P = (o) => new Paint(o);
const bgPaint = P();
const playerTop = P({ color: Color.parseColor('#FFA270') });
const playerLeft = P({ color: Color.parseColor('#FF7A45') });
const playerRight = P({ color: Color.parseColor('#D95B2B') });
const accPaint = P();
const accStroke = P({ style: Style.STROKE, strokeCap: 'round' });
const lockedPaint = P({ color: Color.parseColor('#C9CED8') });
const itemPaint = P();
const shopPaint = P({ color: Color.parseColor('#F5C542') });
const bubbleTextPaint = P({ color: Color.parseColor('#2E3A59'), textAlign: Align.CENTER, bold: true });
const bubbleStroke = P({ style: Style.STROKE, color: Color.parseColor('#2E3A59') });
const shadowPaint = P({ color: Color.argb(60, 0, 0, 0) });
const eyeWhitePaint = P({ color: Color.WHITE });
const pupilPaint = P({ color: Color.parseColor('#1B1B1B') });
const barBgPaint = P({ color: Color.argb(90, 0, 0, 0) });
const barPaint = P();
const overlayPaint = P({ color: Color.argb(150, 10, 15, 30) });
const scorePaint = P({ color: Color.WHITE, textAlign: Align.CENTER, bold: true });
const textPaint = P({ color: Color.WHITE, textAlign: Align.CENTER, bold: true });
const primaryBtnPaint = P({ color: Color.parseColor('#FF7A45') });
const secondaryBtnPaint = P({ color: Color.argb(60, 255, 255, 255) });
const chipPaint = P({ color: Color.argb(70, 0, 0, 0) });
const panelPaint = P({ color: Color.parseColor('#FAFAFA') });
const tabOnPaint = P({ color: Color.parseColor('#2E3A59') });
const tabOffPaint = P({ color: Color.parseColor('#E3E7EE') });
const highlightPaint = P({ color: Color.parseColor('#FFE0D1') });
const btnTextPaint = P({ color: Color.WHITE, textAlign: Align.CENTER, bold: true });
const darkTextPaint = P({ color: Color.parseColor('#2E3A59'), bold: true });
const greyTextPaint = P({ color: Color.parseColor('#7A869A'), textAlign: Align.CENTER });
const greenZonePaint = P({ color: Color.parseColor('#3DBE6E') });
const cursorPaint = P({ color: Color.WHITE });
const cursorStroke = P({ style: Style.STROKE, color: Color.parseColor('#2E3A59') });
const bubblePaint = P({ color: Color.WHITE });
const bangPaint = P({ color: Color.parseColor('#E53950'), textAlign: Align.CENTER, bold: true });
const starPaint = P({ color: Color.parseColor('#FFD54F') });
const wheelText = P({ color: Color.WHITE, textAlign: Align.CENTER, bold: true });
const perfectBarPaint = P();
const perfectBgPaint = P({ color: Color.argb(110, 30, 30, 50) });
const ribbonPaint = P({ style: Style.STROKE, strokeCap: 'round', strokeJoin: 'round' });
const rect = new RectF();
const tmpPath = new Path();

// --- Projection ---
const isoX = (x, z) => (x - z) * cos30 * k;
const isoY = (x, y, z) => -(x + z) * sin30 * k - y * k;
const sx = (x, z) => w * 0.5 - camX + shakeX + isoX(x, z);
const sy = (x, y, z) => h * 0.6 - camY + shakeY + isoY(x, y, z);

function quad(path, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4) {
  path.reset();
  path.moveTo(isoX(x1, z1), isoY(x1, y1, z1));
  path.lineTo(isoX(x2, z2), isoY(x2, y2, z2));
  path.lineTo(isoX(x3, z3), isoY(x3, y3, z3));
  path.lineTo(isoX(x4, z4), isoY(x4, y4, z4));
  path.close();
}

function shade(c, f) {
  return Color.rgb(clamp(Math.floor(Color.red(c) * f), 0, 255), clamp(Math.floor(Color.green(c) * f), 0, 255), clamp(Math.floor(Color.blue(c) * f), 0, 255));
}

function onSizeChanged(width, height) {
  w = width; h = height; k = w / 9.5;
  scorePaint.textSize = w * 0.24;
  scorePaint.setShadowLayer(8, 0, 4, Color.argb(90, 0, 0, 0));
  textPaint.setShadowLayer(6, 0, 3, Color.argb(90, 0, 0, 0));
  skyTop = 0;
  loadLook();
  resetGame();
}

function resetGame() {
  platforms = [];
  current = new Platform(0, 0, 0.85, false, Color.parseColor('#B0BEC5'));
  platforms.push(current);
  px = 0; pz = 0; py = 0; score = 0; jumps = 0; bonusCount = 0; scareT = 0; crumbleHintShown = false;
  perfectStreak = 0; perfectBarAlpha = 0; runTime = 0; coinsThisRun = 0; lives = 0;
  pendingBounce = false; autoJump = false; particles = []; ribbon = []; gameOverReason = null;
  newBest = false; charge = 0; landSquash = 0; popups = [];
  spawnNext();
  updateCameraTarget();
  camX = camTargetX; camY = camTargetY;
  state = State.READY;
}

/** Fait apparaître une nouvelle plateforme dans la direction +x ou +z. */
function spawnNext() {
  const difficulty = Math.min(score / 40, 1);
  alongX = rnd() < 0.5;
  const r = rnd();
  const trampChance = score >= trampolineFromScore ? 0.10 : 0;
  const miniChance = score >= miniFromScore ? 0.09 : 0;
  const movingChance = score >= movingFromScore ? 0.12 : 0;
  const crumbleChance = score < crumbleFromScore ? 0 : Math.min(0.45 + (score - crumbleFromScore) / 60, 0.8);
  let kind;
  if (current.kind === Kind.TRAMPOLINE) kind = Kind.NORMAL;
  else if (r < trampChance) kind = Kind.TRAMPOLINE;
  else if (r < trampChance + miniChance) kind = Kind.MINI;
  else if (r < trampChance + miniChance + movingChance) kind = Kind.MOVING;
  else if (rnd() < crumbleChance) kind = Kind.CRUMBLY;
  else kind = Kind.NORMAL;
  const half = kind === Kind.MINI ? 0.3 : (0.85 - 0.4 * difficulty) * (0.8 + rnd() * 0.35);
  const round = rnd() < 0.4;
  const moving = kind === Kind.MOVING;
  const minDist = current.half + half + 0.45 + (moving ? 0.8 : 0);
  const dist = minDist + rnd() * (moving ? 1.2 : 2.3);
  const color = kind === Kind.MINI ? Color.parseColor('#F5C542') : palette[rndInt(palette.length)];
  next = new Platform(alongX ? current.cx + dist : px, alongX ? pz : current.cz + dist, half, round, color, kind, alongX);
  const c = rnd();
  next.coin = c < 0.02 ? 5 : c < 0.18 ? 1 : 0;
  // Un cœur (vie bonus) sur la plateforme du 30e, 60e, 90e… saut réussi
  if ((jumps + 1) % lifeEvery === 0 && lives < maxLives) { next.heart = true; next.coin = 0; }
  platforms.push(next);
  while (platforms.length > 6) platforms.shift();
}

function updateCameraTarget() {
  const mx = (current.cx + next.cx) / 2, mz = (current.cz + next.cz) / 2;
  camTargetX = isoX(mx, mz);
  camTargetY = isoY(mx, 0, mz);
}

function vibrate(ms) {
  if (profile.vibrationOn && navigator.vibrate) { try { navigator.vibrate(ms); } catch (e) { /* ignoré */ } }
}

/** Fait avancer les missions du jour et crédite les pièces gagnées. */
function missionEvent(type, amount = 1) {
  const r = missions.record(type, amount);
  if (r.coins <= 0) return;
  profile.coins = profile.coins + r.coins;
  for (const m of r.completed) {
    banners.push({ text: (m.challenge ? '🏅 Défi du jour réussi ! +' : '✅ Mission réussie ! +') + m.reward + ' 🪙', life: 3 });
  }
  if (r.allDoneBonus) banners.push({ text: `🎉 Missions du jour terminées : +${missions.ALL_DONE_REWARD} 🪙`, life: 3.2 });
  sounds.play('UNLOCK');
}

function collectCoin(p) {
  const n = p.coin;
  p.coin = 0;
  coinsThisRun += n;
  profile.coins = profile.coins + n;
  const x = sx(p.cx, p.cz) + camX, y = sy(p.cx, 0.55, p.cz) + camY;
  spawnSparkles(x, y, Color.parseColor('#FFC93C'), n > 1 ? 22 : 12);
  popups.push({ text: `+${n} 🪙`, x, y: y - k * 0.4, life: 0.9 });
  sounds.play('COIN');
  missionEvent(MissionType.COINS, n);
}

function collectHeart(p) {
  p.heart = false;
  lives = Math.min(lives + 1, maxLives);
  const x = sx(p.cx, p.cz) + camX, y = sy(p.cx, 0.6, p.cz) + camY;
  spawnSparkles(x, y, Color.parseColor('#FF4D8D'), 18);
  popups.push({ text: '+1 vie ❤️', x, y: y - k * 0.4, life: 1.1 });
  banners.push({ text: '❤️ Vie bonus ! Si tu tombes, tu reviens', life: 2.6 });
  sounds.play('UNLOCK');
}

function startRevive() {
  lives--;
  reviveFromX = px; reviveFromY = py; reviveFromZ = pz;
  reviveTarget = !current.collapsed ? current : next;
  reviveT = 0; perfectStreak = 0; gameOverReason = null;
  banners.push({ text: lives > 0 ? `😇 Sauvé ! Il te reste ${lives} ❤️` : '😇 Sauvé ! Plus de vie en réserve', life: 2.6 });
  sounds.play('REVIVE');
  state = State.REVIVE;
}

function updateRevive(dt) {
  const target = reviveTarget;
  if (!target) return;
  reviveT += dt;
  const f = clamp(reviveT / reviveDuration, 0, 1);
  const e = f * f * (3 - 2 * f);
  px = reviveFromX + (target.cx - reviveFromX) * e;
  pz = reviveFromZ + (target.cz - reviveFromZ) * e;
  py = reviveFromY * (1 - e) + Math.sin(f * Math.PI) * 1.6;
  if (f >= 1) {
    py = 0; px = target.cx; pz = target.cz; landSquash = 1;
    spawnDust(px, pz);
    if (target.crumbly && target.crumbleT >= 0) target.crumbleT = 0;
    if (target === next) {
      next.frozen = true;
      if (next.coin > 0) collectCoin(next);
      if (next.heart) collectHeart(next);
      current = next;
      spawnNext();
      updateCameraTarget();
    }
    reviveTarget = null;
    state = State.READY;
  }
}

function canPause() {
  if (state === State.READY) return jumps > 0 || score > 0;
  return state === State.JUMPING || state === State.CHARGING || state === State.BONUS;
}

function pauseGame() {
  if (!canPause()) return;
  if (state === State.CHARGING) { state = State.READY; charge = 0; }
  pausedFrom = state;
  state = State.PAUSED;
}

function resumeGame() { state = pausedFrom; lastFrame = 0; }

function startAutoJump() {
  jumpFrom = alongX ? px : pz;
  jumpDist = (alongX ? next.cx : next.cz) - jumpFrom;
  jumpDuration = 0.85; jumpHeight = 2.6; jumpT = 0; jumpTrick = 'doubleflip';
  autoJump = true; current.bounce = 1;
  sounds.play('JUMP', 1, 1.4);
  state = State.JUMPING;
}

function startFall() {
  fallVy = 0; fallT = 0; fallAnim = FALL_ANIMS[rndInt(FALL_ANIMS.length)];
  perfectStreak = 0; pendingBounce = false; autoJump = false;
  sounds.play('FALL');
  vibrate(60);
  shake(0.06);
  state = State.FALLING;
}

function endGame(reason) {
  state = State.GAME_OVER;
  gameOverTime = 0;
  gameOverReason = reason;
  const before = profile.totalPoints;
  profile.queueRun(score, runTime, jumps);
  missionEvent(MissionType.GAMES);
  missionEvent(MissionType.SCORE, score);
  newBest = profile.addRun(score);
  unlockedLabels = Skins.unlockedBetween(before, profile.totalPoints).map((it) => it.label);
  const unlocked = unlockedLabels.length > 0;
  sounds.playGameOver(() => { if (unlocked) sounds.play('UNLOCK'); });
  best = profile.best;
  syncOnline();
}

function startCrumble(p) {
  if (!p.crumbly || p.crumbleT >= 0) return;
  const f = clamp((score - crumbleFromScore) / 35, 0, 1);
  p.crumbleDur = 2.2 - 1.0 * f;
  p.crumbleT = 0;
  if (!crumbleHintShown) {
    crumbleHintShown = true;
    popups.push({ text: "Elle s'effrite, vite !", x: sx(px, pz) + camX, y: sy(px, playerHeight * 3.4, pz) + camY, life: 1.4 });
  }
}

function checkScare(p) {
  if (p.edgeMargin(px, pz) >= scareMargin) return false;
  scareT = scareDuration;
  sounds.play('SCARE');
  vibrate(40);
  return true;
}

function startJump() {
  const ratio = charge / maxChargeTime;
  jumpDist = ratio * maxReach;
  jumpFrom = alongX ? px : pz;
  jumpDuration = 0.35 + 0.3 * ratio;
  jumpHeight = 0.6 + jumpDist * 0.3;
  jumpT = 0;
  sounds.play('JUMP', 1, 0.9 + 0.3 * ratio);
  jumpTrick = ratio < 0.3 ? 'none' : TRICKS[rndInt(TRICKS.length)];
  state = State.JUMPING;
}

function update(dt) {
  if (state === State.PAUSED || (state === State.SETTINGS && settingsReturnState === State.PAUSED)) return;
  time += dt;
  updateEffects(dt);
  for (const p of platforms) p.update(dt);
  if (state === State.CHARGING || state === State.JUMPING || state === State.FALLING || state === State.BONUS ||
      state === State.REVIVE || (state === State.READY && jumps > 0)) runTime += dt;
  if (state === State.READY && pendingBounce) {
    bounceDelay -= dt;
    if (bounceDelay <= 0) { pendingBounce = false; startAutoJump(); }
  }
  if (landSquash > 0) landSquash = Math.max(0, landSquash - dt * 4);

  const c = Math.min(1, dt * 6);
  camX += (camTargetX - camX) * c;
  camY += (camTargetY - camY) * c;
  if (scareT > 0) scareT = Math.max(0, scareT - dt);
  if (perfectPulse > 0) perfectPulse = Math.max(0, perfectPulse - dt * 3);
  const barTarget = perfectStreak >= 3 ? 1 : 0;
  perfectBarAlpha += (barTarget - perfectBarAlpha) * Math.min(1, dt * 8);

  // Plateformes fragiles (en pause pendant le bonus et les écrans)
  if (![State.BONUS, State.GAME_OVER, State.LEADERBOARD, State.CUSTOMIZE, State.SETTINGS, State.MISSIONS].includes(state)) {
    for (const p of platforms) {
      if (p.collapsed) { p.dropV += 30 * dt; p.dropY += p.dropV * dt; }
      else if (p.crumbleT >= 0 && p.crumbleSafe) p.crumbleT = Math.min(p.crumbleT + dt, p.crumbleDur * 0.85);
      else if (p.crumbleT >= 0) {
        p.crumbleT += dt;
        if (p.crumbleT >= p.crumbleDur) {
          p.dropY = 0.001; p.dropV = 0;
          sounds.play('CRUMBLE', 0.9);
          spawnDebris(p);
          shake(0.08);
          const standing = (state === State.READY || state === State.CHARGING) && p === current;
          if (standing) { gameOverReason = "La plateforme s'est effondrée !"; startFall(); }
        }
      }
    }
  }

  switch (state) {
    case State.CHARGING: charge = Math.min(charge + dt, maxChargeTime); break;
    case State.JUMPING: {
      emitTrail(dt);
      jumpT += dt;
      const f = Math.min(jumpT / jumpDuration, 1);
      const pos = jumpFrom + jumpDist * f;
      if (alongX) px = pos; else pz = pos;
      py = 4 * jumpHeight * f * (1 - f);
      if (f >= 1) land();
      break;
    }
    case State.FALLING: {
      fallT += dt;
      if (fallAnim === 'cartoon') { if (fallT > 0.8) fallVy += 45 * dt; }
      else if (fallAnim === 'parachute') {
        if (fallT > 0.25 && fallT < 1.9) fallVy += (1.4 - fallVy) * Math.min(1, dt * 6); else fallVy += 30 * dt;
      } else if (fallAnim === 'panic') fallVy += 22 * dt;
      else fallVy += 30 * dt;
      py -= fallVy * dt;
      if (lives > 0 && fallT > 0.6) startRevive();
      else if (sy(px, py + 1.2, pz) > h) endGame(gameOverReason);
      break;
    }
    case State.BONUS: updateBonus(dt); break;
    case State.REVIVE: updateRevive(dt); break;
    case State.GAME_OVER: gameOverTime += dt; break;
    case State.CUSTOMIZE: if (custMsgT > 0) custMsgT -= dt; break;
    default: break;
  }

  for (const p of popups) { p.life -= dt; p.y -= w * 0.15 * dt; }
  popups = popups.filter((p) => p.life > 0);
}

function land() {
  py = 0;
  if (next.contains(px, pz)) {
    const wasAuto = autoJump;
    autoJump = false;
    jumps++;
    landSquash = 1;
    next.frozen = true;
    const scared = !wasAuto && checkScare(next);
    const perfect = !wasAuto && !scared && Math.abs(px - next.cx) < 0.13 && Math.abs(pz - next.cz) < 0.13;
    if (!wasAuto) perfectStreak = perfect ? perfectStreak + 1 : 0;
    let points = perfect ? Scoring.perfectPoints(perfectStreak) : 1;
    if (perfect && perfectStreak >= 3) { perfectPulse = 1; lastPerfectPoints = points; shownStreak = perfectStreak; }
    if (next.kind === Kind.MINI) points += Scoring.MINI_BONUS;
    score += points;
    popups.push({ text: perfect ? `Parfait ! +${points}` : `+${points}`, x: sx(px, pz) + camX, y: sy(px, playerHeight * 2.2, pz) + camY, life: 0.8 });
    spawnDust(px, pz);
    if (perfect) spawnSparkles(sx(px, pz) + camX, sy(px, 0.3, pz) + camY, Color.parseColor('#FFD54F'), 16);
    if (!scared) vibrate(15);
    if (perfect) sounds.play('PERFECT');
    else if (!scared && rndInt(5) === 0 && sounds.has('GREAT')) sounds.play('GREAT');
    else sounds.play('LAND');
    if (next.coin > 0) collectCoin(next);
    if (next.heart) collectHeart(next);
    missionEvent(MissionType.JUMPS);
    if (perfect) { missionEvent(MissionType.PERFECTS); missionEvent(MissionType.STREAK, perfectStreak); }
    if (scared) missionEvent(MissionType.SCARES);
    missionEvent(MissionType.SCORE, score);
    current = next;
    spawnNext();
    updateCameraTarget();
    startCrumble(current);
    if (current.kind === Kind.TRAMPOLINE) {
      pendingBounce = true; bounceDelay = 0.3; current.bounce = 1;
      popups.push({ text: 'Boing !', x: sx(px, pz) + camX, y: sy(px, playerHeight * 3.4, pz) + camY, life: 0.9 });
      missionEvent(MissionType.TRAMPOLINES);
    }
    if (jumps % bonusEvery === 0) startBonus(); else state = State.READY;
  } else if (current.contains(px, pz)) {
    landSquash = 1; perfectStreak = 0; autoJump = false;
    sounds.play('LAND', 0.8);
    spawnDust(px, pz);
    checkScare(current);
    startCrumble(current);
    if (current.kind === Kind.TRAMPOLINE) { pendingBounce = true; bounceDelay = 0.3; current.bounce = 1; }
    state = State.READY;
  } else startFall();
}

// ---------- Dessin ----------
let lastFrame = 0;

function draw(canvas) {
  drawSky(canvas);
  const order = platforms.slice().sort((a, b) => b.farDepth - a.farDepth);
  const playerDepth = px + pz;
  let playerDrawn = false;
  for (const p of order) {
    if (!playerDrawn && playerDepth > p.farDepth) { drawPlayer(canvas); playerDrawn = true; }
    p.draw(canvas);
  }
  if (!playerDrawn) drawPlayer(canvas);
  drawParticles(canvas);

  // Jauge de charge
  if (state === State.CHARGING) {
    const ratio = charge / maxChargeTime;
    const bw = w * 0.16, bh = w * 0.022;
    const cx = sx(px, pz), top = sy(px, playerHeight, pz) - w * 0.09;
    rect.set(cx - bw / 2, top, cx + bw / 2, top + bh);
    canvas.drawRoundRect(rect, bh / 2, bh / 2, barBgPaint);
    barPaint.color = Color.rgb(Math.floor(80 + 175 * ratio), Math.floor(220 - 150 * ratio), 90);
    rect.set(cx - bw / 2, top, cx - bw / 2 + bw * ratio, top + bh);
    canvas.drawRoundRect(rect, bh / 2, bh / 2, barPaint);
  }

  textPaint.textSize = w * 0.06;
  for (const p of popups) {
    textPaint.alpha = clamp(Math.floor(255 * (p.life / 0.8)), 0, 255);
    canvas.drawText(p.text, p.x - camX, p.y - camY, textPaint);
  }
  textPaint.alpha = 255;

  canvas.drawText(String(score), w / 2, insetTop + h * 0.17, scorePaint);
  textPaint.textSize = w * 0.045;
  const livesText = lives > 0 ? '   ' + '❤️'.repeat(lives) : '';
  canvas.drawText(`Record : ${best}   🪙 ${profile.coins}${livesText}`, w / 2, insetTop + h * 0.17 + w * 0.08, textPaint);
  drawBanners(canvas);

  buttons = [];

  if (canPause() && state !== State.BONUS) {
    const ps = w * 0.11, top = insetTop + w * 0.04;
    drawButton(canvas, new RectF(w * 0.97 - ps, top, w * 0.97, top + ps), '⏸', chipPaint, w * 0.05, () => pauseGame());
  }

  if (state === State.READY && score === 0) {
    textPaint.textSize = w * 0.05;
    canvas.drawText('Maintiens appuyé pour sauter', w / 2, ub() - w * 0.27, textPaint);
    textPaint.textSize = w * 0.04;
    canvas.drawText('plus longtemps = plus loin', w / 2, ub() - w * 0.2, textPaint);
    const ch = w * 0.085, top = insetTop + w * 0.04;
    drawButton(canvas, new RectF(w * 0.03, top, w * 0.33, top + ch), '✎ ' + (profile.pseudo || 'Pseudo'), chipPaint, w * 0.036, () => askPseudo(false));
    drawButton(canvas, new RectF(w * 0.67, top, w * 0.97, top + ch), '🏆 Classement', chipPaint, w * 0.036, () => openBoard(0));
    const ty = top + ch + w * 0.025;
    const doneCount = missions.list().filter((_, i) => missions.isDone(i)).length;
    drawButton(canvas, new RectF(w * 0.67, ty, w * 0.97, ty + ch), `📋 Missions ${doneCount}/4`, chipPaint, w * 0.034, () => openMissions());
    drawButton(canvas, new RectF(w * 0.03, ty, w * 0.33, ty + ch),
      (sounds.soundVolume > 0 || sounds.musicVolume > 0 ? '🔊' : '🔇') + ' Son', chipPaint, w * 0.036, () => openSettings());
    const vy = ub() - w * 0.11;
    drawButton(canvas, new RectF(w * 0.6, vy, w * 0.97, vy + w * 0.075), '🎨 Personnage', chipPaint, w * 0.034, () => openCustomize());
    drawButton(canvas, new RectF(w * 0.03, vy, w * 0.3, vy + w * 0.075), 'Web ' + WEB_VERSION, chipPaint, w * 0.032, () => {});
  }

  if (perfectBarAlpha > 0.02 && state !== State.GAME_OVER) drawPerfectBar(canvas);
  if (state === State.BONUS) drawBonus(canvas);
  if (state === State.GAME_OVER) drawGameOver(canvas);
  if (state === State.LEADERBOARD) drawLeaderboard(canvas);
  if (state === State.CUSTOMIZE) drawCustomize(canvas);
  if (state === State.PAUSED) drawPause(canvas);
  if (state === State.MISSIONS) drawMissions(canvas);
  if (state === State.SETTINGS) drawSettings(canvas);
}

// ---------- Entrées tactiles ----------
function onPointerDown(x, y) {
  sounds.unlock();
  // Défilement du classement avec le doigt
  if (state === State.LEADERBOARD && y >= boardListTop && y <= boardListBottom) { boardDragY = y; boardDragStart = boardScroll; return; }
  if (state === State.SETTINGS) {
    const pad = w * 0.06;
    dragSlider = (y >= sfxSlider.top - pad && y <= sfxSlider.bottom + pad) ? 1
      : (y >= musicSlider.top - pad && y <= musicSlider.bottom + pad) ? 2 : 0;
    if (dragSlider !== 0) { dragTo(x); return; }
  }
  const hit = buttons.find((b) => b.rect.contains(x, y));
  if (hit) { sounds.play('CLICK', 0.6); hit.action(); return; }
  if (state === State.READY) { if (!pendingBounce) { state = State.CHARGING; charge = 0; } }
  else if (state === State.BONUS) bonusTap(x, y);
}
function onPointerMove(x, y) {
  if (state === State.SETTINGS && dragSlider) dragTo(x);
  if (state === State.LEADERBOARD && boardDragY >= 0) boardScroll = clamp(boardDragStart - (y - boardDragY), 0, boardMaxScroll);
}
function onPointerUp() {
  boardDragY = -1;
  if (dragSlider === 1) sounds.play('GREAT');
  dragSlider = 0;
  if (state === State.CHARGING) startJump();
}
