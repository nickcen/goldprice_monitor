#!/usr/bin/env bash
# Build locally and publish the admin SPA and Java API to the Chidugaiyi host.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REMOTE_HOST="${REMOTE_HOST:-8.131.90.13}"
REMOTE_USER="${REMOTE_USER:-root}"
SSH_KEY="${SSH_KEY:-/Volumes/Personal/Documents/cyh_chidu.pem}"
DOMAIN="gold.chidugaiyi.com"
WEB_ROOT="/var/www/goldprice-monitor"
APP_ROOT="/opt/goldprice-monitor"
SERVICE="goldprice-monitor"
PORT="18080"

case "${1:-deploy}" in
  deploy|status) ACTION="${1:-deploy}" ;;
  -h|--help) echo "Usage: ./deploy.sh [deploy|status]"; exit 0 ;;
  *) echo "Usage: ./deploy.sh [deploy|status]" >&2; exit 2 ;;
esac

[[ -f "$SSH_KEY" ]] || { echo "SSH key missing: $SSH_KEY" >&2; exit 1; }
SSH=(ssh -i "$SSH_KEY" -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=20)
remote() { "${SSH[@]}" "${REMOTE_USER}@${REMOTE_HOST}" "$@"; }

if [[ "$ACTION" == status ]]; then
  remote "systemctl status $SERVICE --no-pager && curl -fsS http://127.0.0.1:$PORT/actuator/health && nginx -t"
  exit
fi

[[ -s "$ROOT/.env.prod" ]] || { echo "Production config missing or empty: $ROOT/.env.prod" >&2; exit 1; }
for command in mvn npm rsync; do
  command -v "$command" >/dev/null || { echo "Missing local command: $command" >&2; exit 1; }
done

echo "Building API and admin locally"
mvn -f "$ROOT/api/pom.xml" -DskipTests package
(cd "$ROOT/admin" && npm ci && VITE_API_BASE_URL= npm run build)
JAR="$(find "$ROOT/api/target" -maxdepth 1 -name '*.jar' ! -name '*.original' -print -quit)"
[[ -n "$JAR" && -f "$ROOT/admin/dist/index.html" ]] || { echo "Build output missing" >&2; exit 1; }

# A provisioned certificate for *.chidugaiyi.com is shared with the reference project.
remote 'bash -s' <<'REMOTE_CHECK'
set -euo pipefail
test "$(id -u)" -eq 0 || { echo 'Deploy requires root remote user' >&2; exit 1; }
test -s /etc/nginx/ssl/chidugaiyi.com.cer
test -s /etc/nginx/ssl/chidugaiyi.com.key
openssl x509 -in /etc/nginx/ssl/chidugaiyi.com.cer -noout -checkend 604800 >/dev/null
openssl x509 -in /etc/nginx/ssl/chidugaiyi.com.cer -noout -ext subjectAltName | grep -Fq '*.chidugaiyi.com'
command -v java >/dev/null
command -v nginx >/dev/null
mkdir -p /opt/goldprice-monitor /var/www/goldprice-monitor
REMOTE_CHECK

RSYNC_SSH="ssh"
for option in "${SSH[@]:1}"; do printf -v RSYNC_SSH '%s %q' "$RSYNC_SSH" "$option"; done
rsync -az "$JAR" -e "$RSYNC_SSH" "${REMOTE_USER}@${REMOTE_HOST}:${APP_ROOT}/api.jar.new"
rsync -az --chmod=F600 "$ROOT/.env.prod" -e "$RSYNC_SSH" "${REMOTE_USER}@${REMOTE_HOST}:${APP_ROOT}/.env.prod.new"
rsync -az --delete "$ROOT/admin/dist/" -e "$RSYNC_SSH" "${REMOTE_USER}@${REMOTE_HOST}:${WEB_ROOT}/"
rsync -az "$ROOT/deploy/goldprice-monitor.service" -e "$RSYNC_SSH" "${REMOTE_USER}@${REMOTE_HOST}:/etc/systemd/system/${SERVICE}.service"
rsync -az "$ROOT/deploy/goldprice-monitor.conf" -e "$RSYNC_SSH" "${REMOTE_USER}@${REMOTE_HOST}:/etc/nginx/conf.d/${SERVICE}.conf"

remote 'bash -s' <<'REMOTE_FINISH'
set -euo pipefail
mv /opt/goldprice-monitor/api.jar.new /opt/goldprice-monitor/api.jar
mv /opt/goldprice-monitor/.env.prod.new /opt/goldprice-monitor/.env.prod
chmod 600 /opt/goldprice-monitor/.env.prod
systemctl daemon-reload
nginx -t
systemctl enable --now goldprice-monitor
systemctl restart goldprice-monitor
systemctl reload nginx
for attempt in {1..30}; do
  if curl -fs http://127.0.0.1:18080/actuator/health | grep -q '"status":"UP"'; then
    echo 'API healthy'; exit 0
  fi
  sleep 2
done
journalctl -u goldprice-monitor -n 40 --no-pager >&2
exit 1
REMOTE_FINISH
echo "Published https://$DOMAIN/ (DNS must point to $REMOTE_HOST)"
