"""Tests du serveur de scores : python3 -m unittest test_server (dans le dossier server/)."""
import json
import os
import tempfile
import threading
import unittest
import urllib.error
import urllib.request

_tmp = tempfile.mkdtemp()
os.environ["SAUTIQUE_DB"] = os.path.join(_tmp, "test.db")
os.environ["SAUTIQUE_KEY"] = ""

import sautique_server as srv  # noqa: E402

A = "11111111-1111-1111-1111-111111111111"
B = "22222222-2222-2222-2222-222222222222"


class ServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.httpd = srv.ThreadingHTTPServer(("127.0.0.1", 0), srv.Handler)
        cls.base = "http://127.0.0.1:%d" % cls.httpd.server_address[1]
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()

    def post(self, data):
        req = urllib.request.Request(self.base + "/api/scores", json.dumps(data).encode(),
                                     {"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def get(self, path):
        with urllib.request.urlopen(self.base + path) as r:
            return json.loads(r.read())

    def test_flow(self):
        self.assertEqual(self.post({"id": A, "pseudo": "Tom", "score": 12, "duration": 30, "jumps": 10})[0], 200)
        self.assertEqual(self.post({"id": A, "pseudo": "Tom", "score": 5, "duration": 30, "jumps": 5})[0], 200)
        self.assertEqual(self.post({"id": B, "pseudo": "Zoé", "score": 30, "duration": 60, "jumps": 25})[0], 200)
        top = self.get("/api/top?me=" + A)
        self.assertEqual([e["pseudo"] for e in top["top"]], ["Zoé", "Tom"])
        self.assertEqual(top["me"], {"rank": 2, "best": 12})
        week = self.get("/api/top?period=week&me=" + B)
        self.assertEqual(week["top"][0]["pseudo"], "Zoé")
        self.assertEqual(week["me"]["rank"], 1)

    def test_pseudo_unique(self):
        self.post({"id": A, "pseudo": "Tom", "score": 0, "duration": 0, "jumps": 0})
        code, _ = self.post({"id": B, "pseudo": "TOM", "score": 3, "duration": 10, "jumps": 3})
        self.assertEqual(code, 409)
        self.assertFalse(self.get("/api/pseudo?name=tom&me=" + B)["available"])
        self.assertTrue(self.get("/api/pseudo?name=tom&me=" + A)["available"])
        self.assertTrue(self.get("/api/pseudo?name=Personne&me=" + B)["available"])

    def test_implausible_scores(self):
        # Trop de points pour le nombre de sauts
        self.assertEqual(self.post({"id": A, "pseudo": "Tom", "score": 500, "duration": 100, "jumps": 10})[0], 422)
        # Trop rapide
        self.assertEqual(self.post({"id": A, "pseudo": "Tom", "score": 20, "duration": 2, "jumps": 20})[0], 422)
        # Ancienne version de l'appli (pas de durée ni de sauts)
        self.assertEqual(self.post({"id": A, "pseudo": "Tom", "score": 20})[0], 400)

    def test_plausible_rules(self):
        self.assertTrue(srv.plausible(0, 0, 0))
        self.assertTrue(srv.plausible(10, 20, 10))
        self.assertTrue(srv.plausible(10 * 7 + 20, 20, 10))
        self.assertFalse(srv.plausible(10 * 7 + 21, 20, 10))


if __name__ == "__main__":
    unittest.main()
