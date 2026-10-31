#!/usr/bin/env python3
"""Serveur de scores pour Sautique.

Aucune dépendance : Python 3.8+ et SQLite (inclus avec Python).

API :
  POST /api/scores   {"id": "<uuid>", "pseudo": "Tom", "score": 12}
       -> garde le meilleur score du joueur et met à jour son pseudo.
  GET  /api/top?limit=10&me=<uuid>
       -> {"top": [{"pseudo": "Zoé", "best": 30, "me": false}, ...],
           "me": {"rank": 4, "best": 12} ou null}
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
_db.commit()

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
    with _db_lock:
        _db.execute(
            """INSERT INTO players (id, pseudo, best, updated_at) VALUES (?, ?, ?, ?)
               ON CONFLICT(id) DO UPDATE SET
                 pseudo = excluded.pseudo,
                 updated_at = CASE WHEN excluded.best > players.best
                                   THEN excluded.updated_at ELSE players.updated_at END,
                 best = MAX(players.best, excluded.best)""",
            (player_id.lower(), pseudo, score, time.time()),
        )
        _db.commit()


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

    def send_json(self, code, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
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
        if url.path != "/api/top":
            self.send_json(404, {"error": "introuvable"})
            return
        if not self.authorized():
            return
        q = parse_qs(url.query)
        try:
            limit = max(1, min(int(q.get("limit", ["10"])[0]), 100))
        except ValueError:
            limit = 10
        me = q.get("me", [""])[0]
        if me and not UUID_RE.match(me):
            me = ""
        self.send_json(200, top(limit, me))

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
