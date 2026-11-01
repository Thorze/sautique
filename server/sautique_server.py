#!/usr/bin/env python3
"""Serveur de scores pour Sautique.

Aucune dépendance : Python 3.8+ et SQLite (inclus avec Python).

API :
  POST /api/scores   {"id": "<uuid>", "pseudo": "Tom", "score": 12, "duration": 41.5, "jumps": 10}
       -> garde le meilleur score du joueur (de tous les temps et de la semaine)
          et met à jour son pseudo. 409 si le pseudo est déjà pris par un autre joueur,
          422 si le score n'est pas plausible (durée / nombre de sauts).
          Score 0 = simple mise à jour du pseudo.
  GET  /api/top?limit=10&me=<uuid>&period=all|week
       -> {"top": [{"pseudo": "Zoé", "best": 30, "me": false}, ...],
           "me": {"rank": 4, "best": 12} ou null}
  GET  /api/pseudo?name=Tom&me=<uuid> -> {"available": true}
  GET  /health -> "ok"

Variables d'environnement :
  SAUTIQUE_PORT  (8080)        port d'écoute
  SAUTIQUE_HOST  (0.0.0.0)     adresse d'écoute
  SAUTIQUE_DB    (scores.db)   chemin de la base SQLite
  SAUTIQUE_KEY   (vide)        si défini, l'appli doit envoyer l'en-tête X-Api-Key
"""

import json
import os
import re
import sqlite3
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PORT = int(os.environ.get("SAUTIQUE_PORT", "8080"))
HOST = os.environ.get("SAUTIQUE_HOST", "0.0.0.0")
DB_PATH = os.environ.get("SAUTIQUE_DB", "scores.db")
API_KEY = os.environ.get("SAUTIQUE_KEY", "")

MAX_SCORE = 100_000
MAX_PSEUDO = 16
MAX_BODY = 2048
UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
RATE_LIMIT = 30  # envois max par minute et par adresse IP

_db_lock = threading.Lock()
_db = sqlite3.connect(DB_PATH, check_same_thread=False)
_db.execute(
    """CREATE TABLE IF NOT EXISTS players (
        id TEXT PRIMARY KEY,
        pseudo TEXT NOT NULL,
        best INTEGER NOT NULL DEFAULT 0,
        updated_at REAL NOT NULL
    )"""
)
_db.execute("CREATE INDEX IF NOT EXISTS players_best ON players (best DESC, updated_at ASC)")
_db.execute("CREATE INDEX IF NOT EXISTS players_pseudo ON players (lower(pseudo))")
_db.execute(
    """CREATE TABLE IF NOT EXISTS weekly (
        player_id TEXT NOT NULL,
        week TEXT NOT NULL,
        best INTEGER NOT NULL,
        updated_at REAL NOT NULL,
        PRIMARY KEY (player_id, week)
    )"""
)
_db.execute("CREATE INDEX IF NOT EXISTS weekly_best ON weekly (week, best DESC, updated_at ASC)")
_db.commit()

# Limites pour repérer les scores impossibles
MAX_POINTS_PER_JUMP = 7          # parfait enchaîné (+5) sur une mini-plateforme (+2)
MAX_BONUS_POINTS = 10            # un niveau bonus tous les 10 sauts
MIN_SECONDS_PER_JUMP = 0.35      # un saut ne peut pas être plus court
MAX_JUMPS = 5000


def current_week(now=None):
    """Semaine ISO, ex. 2026-W44 (heure UTC)."""
    d = (now or datetime.now(timezone.utc)).isocalendar()
    return "%d-W%02d" % (d[0], d[1])


def plausible(score, duration, jumps):
    """Vérifie qu'un score est atteignable en `jumps` sauts et `duration` secondes."""
    if score == 0:
        return True
    if jumps <= 0 or jumps > MAX_JUMPS:
        return False
    if duration < jumps * MIN_SECONDS_PER_JUMP:
        return False
    max_score = jumps * MAX_POINTS_PER_JUMP + (jumps // 10 + 1) * MAX_BONUS_POINTS
    return score <= max_score


def pseudo_taken(pseudo, player_id):
    with _db_lock:
        row = _db.execute(
            "SELECT 1 FROM players WHERE lower(pseudo) = lower(?) AND id != ? LIMIT 1",
            (pseudo, player_id.lower()),
        ).fetchone()
    return row is not None

_rate = {}
_rate_lock = threading.Lock()


def rate_limited(ip):
    now = time.time()
    with _rate_lock:
        hits = [t for t in _rate.get(ip, []) if now - t < 60]
        limited = len(hits) >= RATE_LIMIT
        if not limited:
            hits.append(now)
        _rate[ip] = hits
        if len(_rate) > 10_000:  # évite que la table grossisse sans fin
            _rate.clear()
        return limited


def clean_pseudo(value):
    if not isinstance(value, str):
        return None
    p = "".join(ch for ch in value if ch.isprintable()).strip()
    if not p or len(p) > MAX_PSEUDO:
        return None
    return p


def submit(player_id, pseudo, score):
    now = time.time()
    week = current_week()
    with _db_lock:
        if score > 0:
            _db.execute(
                """INSERT INTO weekly (player_id, week, best, updated_at) VALUES (?, ?, ?, ?)
                   ON CONFLICT(player_id, week) DO UPDATE SET
                     updated_at = CASE WHEN excluded.best > weekly.best
                                       THEN excluded.updated_at ELSE weekly.updated_at END,
                     best = MAX(weekly.best, excluded.best)""",
                (player_id.lower(), week, score, now),
            )
        _db.execute(
            """INSERT INTO players (id, pseudo, best, updated_at) VALUES (?, ?, ?, ?)
               ON CONFLICT(id) DO UPDATE SET
                 pseudo = excluded.pseudo,
                 updated_at = CASE WHEN excluded.best > players.best
                                   THEN excluded.updated_at ELSE players.updated_at END,
                 best = MAX(players.best, excluded.best)""",
            (player_id.lower(), pseudo, score, now),
        )
        _db.commit()


def top_week(limit, me):
    week = current_week()
    with _db_lock:
        rows = _db.execute(
            "SELECT w.player_id, p.pseudo, w.best FROM weekly w JOIN players p ON p.id = w.player_id "
            "WHERE w.week = ? AND w.best > 0 ORDER BY w.best DESC, w.updated_at ASC LIMIT ?",
            (week, limit),
        ).fetchall()
        mine = None
        if me:
            row = _db.execute("SELECT best FROM weekly WHERE player_id = ? AND week = ?", (me.lower(), week)).fetchone()
            if row and row[0] > 0:
                better = _db.execute(
                    "SELECT COUNT(*) FROM weekly WHERE week = ? AND best > ?", (week, row[0])
                ).fetchone()[0]
                mine = {"rank": better + 1, "best": row[0]}
    return {
        "top": [{"pseudo": p, "best": b, "me": bool(me) and i == me.lower()} for i, p, b in rows],
        "me": mine,
        "week": week,
    }


def top(limit, me):
    with _db_lock:
        rows = _db.execute(
            "SELECT id, pseudo, best FROM players WHERE best > 0 "
            "ORDER BY best DESC, updated_at ASC LIMIT ?",
            (limit,),
        ).fetchall()
        mine = None
        if me:
            row = _db.execute("SELECT best FROM players WHERE id = ?", (me.lower(),)).fetchone()
            if row and row[0] > 0:
                better = _db.execute("SELECT COUNT(*) FROM players WHERE best > ?", (row[0],)).fetchone()[0]
                mine = {"rank": better + 1, "best": row[0]}
    # Les identifiants ne sont jamais renvoyés : personne ne peut se faire passer pour un autre.
    return {
        "top": [{"pseudo": p, "best": b, "me": bool(me) and i == me.lower()} for i, p, b in rows],
        "me": mine,
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "Sautique/1.0"

    def log_message(self, fmt, *args):
        print("%s %s" % (self.client_ip(), fmt % args), flush=True)

    def client_ip(self):
        # Derrière un reverse proxy (Caddy, nginx), l'IP réelle est dans X-Forwarded-For
        fwd = self.headers.get("X-Forwarded-For")
        return fwd.split(",")[0].strip() if fwd else self.client_address[0]

    def cors_headers(self):
        # Autorise la version web du jeu (GitHub Pages) à appeler l'API depuis le navigateur
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-Api-Key")
        self.send_header("Access-Control-Max-Age", "86400")

    def do_OPTIONS(self):
        self.send_response(204)
        self.cors_headers()
        self.send_header("Content-Length", "0")
        self.end_headers()

    def send_json(self, code, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.cors_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def authorized(self):
        if API_KEY and self.headers.get("X-Api-Key") != API_KEY:
            self.send_json(401, {"error": "cle invalide"})
            return False
        return True

    def do_GET(self):
        url = urlparse(self.path)
        if url.path == "/health":
            self.send_json(200, "ok")
            return
        if url.path not in ("/api/top", "/api/pseudo"):
            self.send_json(404, {"error": "introuvable"})
            return
        if not self.authorized():
            return
        q = parse_qs(url.query)
        if url.path == "/api/pseudo":
            name = clean_pseudo(q.get("name", [""])[0])
            me = q.get("me", [""])[0]
            if not UUID_RE.match(me):
                me = "00000000-0000-0000-0000-000000000000"
            self.send_json(200, {"available": name is not None and not pseudo_taken(name, me)})
            return
        try:
            limit = max(1, min(int(q.get("limit", ["10"])[0]), 100))
        except ValueError:
            limit = 10
        me = q.get("me", [""])[0]
        if me and not UUID_RE.match(me):
            me = ""
        period = q.get("period", ["all"])[0]
        self.send_json(200, top_week(limit, me) if period == "week" else top(limit, me))

    def do_POST(self):
        if urlparse(self.path).path != "/api/scores":
            self.send_json(404, {"error": "introuvable"})
            return
        if not self.authorized():
            return
        if rate_limited(self.client_ip()):
            self.send_json(429, {"error": "trop de requetes"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if length <= 0 or length > MAX_BODY:
            self.send_json(400, {"error": "corps invalide"})
            return
        try:
            data = json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            self.send_json(400, {"error": "json invalide"})
            return
        if not isinstance(data, dict):
            self.send_json(400, {"error": "json invalide"})
            return
        player_id = data.get("id")
        pseudo = clean_pseudo(data.get("pseudo"))
        score = data.get("score")
        if not isinstance(player_id, str) or not UUID_RE.match(player_id):
            self.send_json(400, {"error": "id invalide"})
            return
        if pseudo is None:
            self.send_json(400, {"error": "pseudo invalide (1 a 16 caracteres)"})
            return
        if not isinstance(score, int) or isinstance(score, bool) or not 0 <= score <= MAX_SCORE:
            self.send_json(400, {"error": "score invalide"})
            return
        duration = data.get("duration")
        jumps = data.get("jumps")
        if not isinstance(duration, (int, float)) or isinstance(duration, bool) or \
                not isinstance(jumps, int) or isinstance(jumps, bool):
            self.send_json(400, {"error": "version de l'appli trop ancienne, mets-la à jour"})
            return
        if not plausible(score, float(duration), jumps):
            self.send_json(422, {"error": "score refusé"})
            return
        if pseudo_taken(pseudo, player_id):
            self.send_json(409, {"error": "pseudo déjà pris"})
            return
        submit(player_id, pseudo, score)
        self.send_json(200, {"ok": True})


def main():
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print("Serveur Sautique sur %s:%d (base : %s)" % (HOST, PORT, DB_PATH), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
