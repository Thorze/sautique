# Serveur de scores Sautique

Petit serveur Python sans dépendance (base SQLite) qui stocke le meilleur score
et le pseudo de chaque joueur.

## Installation sur le VPS

### Option A : Docker (le plus simple)

```bash
git clone https://github.com/Thorze/sautique.git
cd sautique/server
docker compose up -d --build
```

Les scores sont stockés dans `sautique/server/data/scores.db`.

### Option B : Python + systemd

```bash
sudo useradd --system --home /opt/sautique sautique
sudo mkdir -p /opt/sautique
sudo cp sautique_server.py /opt/sautique/
sudo chown -R sautique: /opt/sautique
sudo cp sautique.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now sautique
```

### Vérifier

```bash
curl http://localhost:8080/health
```

Doit répondre `"ok"`. Depuis ton téléphone ou ton PC : `http://IP_DU_VPS:8080/health`.
Si ça ne répond pas, ouvre le port 8080 dans le pare-feu (ex. `sudo ufw allow 8080/tcp`)
et dans le panneau de ton hébergeur si besoin.

## HTTPS (recommandé si tu as un nom de domaine)

Avec [Caddy](https://caddyserver.com), le certificat est automatique.
`/etc/caddy/Caddyfile` :

```
scores.mondomaine.fr {
    reverse_proxy localhost:8080
}
```

L'adresse à mettre dans l'appli devient alors `https://scores.mondomaine.fr`.
Sans domaine, `http://IP_DU_VPS:8080` fonctionne aussi.

## Clé d'accès (facultatif)

Si tu définis `SAUTIQUE_KEY` sur le serveur, l'appli doit envoyer la même valeur
(`SCORES_KEY`). Ça évite que n'importe qui écrive dans ton classement sans l'appli.

## Commandes utiles

```bash
# voir les scores
sqlite3 data/scores.db "SELECT pseudo, best FROM players ORDER BY best DESC;"
# supprimer un joueur
sqlite3 data/scores.db "DELETE FROM players WHERE pseudo = 'Tricheur';"
```
