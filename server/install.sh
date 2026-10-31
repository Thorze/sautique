#!/usr/bin/env bash
# Installe (ou met à jour) le serveur de scores Sautique sur un VPS Linux (systemd).
# Usage : sudo bash install.sh            (port 8080 par défaut)
#         sudo PORT=9000 KEY=monsecret bash install.sh
set -euo pipefail

PORT="${PORT:-8080}"
KEY="${KEY:-}"
DIR=/opt/sautique
RAW_URL="https://raw.githubusercontent.com/Thorze/sautique/claude/sleepy-euler-aowuk7/server/sautique_server.py"

if [ "$(id -u)" -ne 0 ]; then
  echo "Lance ce script avec sudo : sudo bash $0" >&2
  exit 1
fi

echo "==> Python 3"
if ! command -v python3 >/dev/null 2>&1; then
  if command -v apt-get >/dev/null 2>&1; then apt-get update -q && apt-get install -y -q python3
  elif command -v dnf >/dev/null 2>&1; then dnf install -y python3
  elif command -v yum >/dev/null 2>&1; then yum install -y python3
  else echo "Installe python3 puis relance ce script." >&2; exit 1
  fi
fi

echo "==> Utilisateur et dossier $DIR"
id sautique >/dev/null 2>&1 || useradd --system --home "$DIR" --shell /usr/sbin/nologin sautique
mkdir -p "$DIR"

echo "==> Serveur"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$HERE/sautique_server.py" ]; then
  cp "$HERE/sautique_server.py" "$DIR/sautique_server.py"
else
  curl -fsSL "$RAW_URL" -o "$DIR/sautique_server.py"
fi
chown -R sautique: "$DIR"

echo "==> Service systemd"
cat > /etc/systemd/system/sautique.service <<EOF
[Unit]
Description=Serveur de scores Sautique
After=network.target

[Service]
User=sautique
WorkingDirectory=$DIR
Environment=SAUTIQUE_PORT=$PORT
Environment=SAUTIQUE_DB=$DIR/scores.db
Environment=SAUTIQUE_KEY=$KEY
Environment=PYTHONUNBUFFERED=1
ExecStart=$(command -v python3) $DIR/sautique_server.py
Restart=always

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable sautique >/dev/null
systemctl restart sautique

echo "==> Pare-feu"
if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
  ufw allow "$PORT/tcp" >/dev/null && echo "Port $PORT ouvert dans ufw"
elif command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
  firewall-cmd --permanent --add-port="$PORT/tcp" >/dev/null && firewall-cmd --reload >/dev/null
  echo "Port $PORT ouvert dans firewalld"
else
  echo "Aucun pare-feu local actif détecté"
fi

sleep 1
echo "==> Vérification"
if curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null; then
  IP="$(curl -fsS -4 https://ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')"
  echo
  echo "Serveur Sautique OK !"
  echo "Adresse à mettre dans SCORES_URL : http://$IP:$PORT"
  echo "Test depuis ton téléphone : http://$IP:$PORT/health"
  echo "(Si ça ne répond pas de l'extérieur, ouvre le port $PORT dans le panneau de ton hébergeur.)"
else
  echo "Le serveur ne répond pas. Logs : journalctl -u sautique -n 50" >&2
  exit 1
fi
