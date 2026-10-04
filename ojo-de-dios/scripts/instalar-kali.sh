#!/usr/bin/env bash
#
# instalar-kali.sh — pone en marcha Ojo de Dios en Kali Linux (o cualquier Debian)
# ---------------------------------------------------------------------------
#   ./scripts/instalar-kali.sh                comprueba el entorno e instala dependencias
#   ./scripts/instalar-kali.sh --arrancar     además, arranca el servidor en primer plano
#   ./scripts/instalar-kali.sh --servicio     instala un servicio systemd (arranque automático)
#   ./scripts/instalar-kali.sh --desinstalar  quita el servicio systemd
#
# Ojo de Dios solo lee feeds públicos abiertos. No necesita root para funcionar:
# sudo se usa únicamente si tú eliges instalar Node o el servicio del sistema.

set -uo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NODE_MINIMO=18
PUERTO="${PORT:-3000}"
NOMBRE_SERVICIO="ojo-de-dios"

if [ -t 1 ]; then
  R=$'\e[0m'; B=$'\e[1m'; C=$'\e[36m'; V=$'\e[32m'; A=$'\e[33m'; E=$'\e[31m'
else
  R=""; B=""; C=""; V=""; A=""; E=""
fi

titulo() { printf '\n%s%s%s\n%s\n' "$B$C" "$1" "$R" "${C}$(printf '─%.0s' {1..64})$R"; }
ok()     { printf '  %s✔%s %s\n' "$V" "$R" "$1"; }
aviso()  { printf '  %s!%s %s\n' "$A" "$R" "$1"; }
error()  { printf '  %s✗%s %s\n' "$E" "$R" "$1"; }
paso()   { printf '  %s›%s %s\n' "$C" "$R" "$1"; }

# Kali suele usarse como root, donde sudo puede no existir. Se adapta a ambos.
privilegiado() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    error "Hace falta root (o sudo) para: $*"
    return 1
  fi
}

hay_privilegios() { [ "$(id -u)" -eq 0 ] || command -v sudo >/dev/null 2>&1; }

# ─────────────────────────────────────────────────────────────────────────────
# Desinstalar el servicio
# ─────────────────────────────────────────────────────────────────────────────
if [ "${1:-}" = "--desinstalar" ]; then
  titulo "Quitando el servicio $NOMBRE_SERVICIO"
  privilegiado systemctl stop "$NOMBRE_SERVICIO" 2>/dev/null
  privilegiado systemctl disable "$NOMBRE_SERVICIO" 2>/dev/null
  privilegiado rm -f "/etc/systemd/system/$NOMBRE_SERVICIO.service"
  privilegiado systemctl daemon-reload
  ok "Servicio eliminado."
  exit 0
fi

titulo "Ojo de Dios · instalación en Kali Linux"
printf '  Proyecto: %s\n' "$RAIZ"

# ─────────────────────────────────────────────────────────────────────────────
# 1. Comprobar el sistema
# ─────────────────────────────────────────────────────────────────────────────
titulo "1/4 · Comprobando el sistema"

if [ -f /etc/os-release ]; then
  . /etc/os-release
  ok "Distribución: ${PRETTY_NAME:-desconocida}"
else
  aviso "No se pudo identificar la distribución; se continúa igualmente."
fi

ARQ="$(uname -m)"
ok "Arquitectura: $ARQ"

# ─────────────────────────────────────────────────────────────────────────────
# 2. Node.js
# ─────────────────────────────────────────────────────────────────────────────
titulo "2/4 · Comprobando Node.js"

version_nodo() { node -v 2>/dev/null | sed 's/^v//'; }

instalar_con_apt() {
  paso "Instalando Node.js desde los repositorios de Kali…"
  privilegiado apt-get update -qq && privilegiado apt-get install -y nodejs npm
}

instalar_con_nodesource() {
  paso "Añadiendo el repositorio oficial de NodeSource (Node 22)…"
  if ! command -v curl >/dev/null 2>&1; then
    privilegiado apt-get update -qq && privilegiado apt-get install -y curl
  fi
  curl -fsSL https://deb.nodesource.com/setup_22.x | privilegiado env bash -
  privilegiado apt-get install -y nodejs
}

if ! command -v node >/dev/null 2>&1; then
  aviso "Node.js no está instalado."
  echo
  echo "  Kali trae una versión reciente en sus repositorios. Elige:"
  echo "    1) Instalar desde los repositorios de Kali  (recomendado)"
  echo "    2) Instalar Node 22 desde NodeSource"
  echo "    3) Salir y hacerlo yo a mano"
  read -rp "  Opción [1]: " opcion
  case "${opcion:-1}" in
    1) instalar_con_apt ;;
    2) instalar_con_nodesource ;;
    *) error "Instala Node.js 18 o superior y vuelve a ejecutar esto."; exit 1 ;;
  esac
fi

if ! command -v node >/dev/null 2>&1; then
  error "Node.js sigue sin estar disponible. Abortando."
  exit 1
fi

VN="$(version_nodo)"
VN_MAYOR="${VN%%.*}"

if [ "${VN_MAYOR:-0}" -ge "$NODE_MINIMO" ]; then
  ok "Node.js v$VN (se necesita 18 o superior)"
else
  aviso "Node.js v$VN es demasiado antiguo: hace falta 18 o superior."
  echo "  El código usa fetch nativo y AbortSignal.timeout, que no existen antes."
  read -rp "  ¿Instalar Node 22 desde NodeSource ahora? [s/N]: " r
  if [[ "${r:-n}" =~ ^[sSyY]$ ]]; then
    instalar_con_nodesource
    VN="$(version_nodo)"; VN_MAYOR="${VN%%.*}"
    if [ "${VN_MAYOR:-0}" -lt "$NODE_MINIMO" ]; then
      error "Sigue siendo v$VN. Abortando."; exit 1
    fi
    ok "Node.js v$VN instalado"
  else
    error "Sin Node 18+ no se puede continuar."
    exit 1
  fi
fi

if command -v npm >/dev/null 2>&1; then
  ok "npm $(npm -v)"
else
  error "npm no está disponible."
  echo "  En Kali suele venir con nodejs. Prueba:  sudo apt install npm"
  exit 1
fi

# ─────────────────────────────────────────────────────────────────────────────
# 3. Dependencias
# ─────────────────────────────────────────────────────────────────────────────
titulo "3/4 · Instalando dependencias"

cd "$RAIZ" || { error "No se pudo entrar en $RAIZ"; exit 1; }

# Las tres dependencias son JavaScript puro: no hace falta build-essential.
paso "express, leaflet y satellite.js son JS puro; no se compila nada nativo."

if npm install --no-audit --no-fund; then
  ok "Dependencias instaladas ($(du -sh node_modules 2>/dev/null | cut -f1))"
else
  error "npm install falló."
  echo "  Si el problema son permisos, no uses sudo: revisa el dueño de node_modules"
  echo "  con  ls -la node_modules | head"
  exit 1
fi

# Prueba de humo: confirma que el motor arranca antes de molestar al usuario.
if node test/smoke.mjs >/tmp/ojo-smoke.log 2>&1; then
  ok "Prueba de humo superada: el motor arranca y degrada a respaldo sin red."
else
  aviso "La prueba de humo falló. Registro en /tmp/ojo-smoke.log:"
  sed 's/^/      /' /tmp/ojo-smoke.log | tail -15
fi

# ─────────────────────────────────────────────────────────────────────────────
# 4. Datos y red
# ─────────────────────────────────────────────────────────────────────────────
titulo "4/4 · Datos y acceso"

paso "Actualizando instantáneas desde las fuentes públicas…"
if node scripts/refresh-data.mjs 2>&1 | sed 's/^/      /'; then
  ok "Instantáneas actualizadas."
else
  aviso "Alguna fuente no respondió; se conservan las instantáneas incluidas."
fi

# Detección de IP de red local. Se descartan loopback (127.), link-local (169.254.)
# y rangos de contenedor, que no sirven para que entre otro dispositivo.
es_ip_util() {
  case "$1" in
    127.*|169.254.*|0.0.0.0|"") return 1 ;;
    *) return 0 ;;
  esac
}

detectar_ip() {
  local candidata

  candidata="$(ip route get 1.1.1.1 2>/dev/null | grep -oP 'src \K\S+' | head -1)"
  if es_ip_util "$candidata"; then printf '%s' "$candidata"; return; fi

  for candidata in $(hostname -I 2>/dev/null) $(ip -4 -o addr show scope global 2>/dev/null | awk '{print $4}' | cut -d/ -f1); do
    if es_ip_util "$candidata"; then printf '%s' "$candidata"; return; fi
  done
}

IP_LAN="$(detectar_ip)"

printf '\n  %sAcceso:%s\n' "$B" "$R"
printf '    En esta máquina      →  http://localhost:%s\n' "$PUERTO"

if [ -n "$IP_LAN" ]; then
  printf '    Desde la red local   →  http://%s:%s\n' "$IP_LAN" "$PUERTO"
else
  printf '    Desde la red local   →  sin IP de LAN detectable\n'
  printf '                            (¿estás tras una VPN? usa: ip -4 addr)\n'
fi

if command -v nmcli >/dev/null 2>&1 && nmcli -t -f TYPE connection show --active 2>/dev/null | grep -qi vpn; then
  aviso "Hay una VPN activa: la IP de arriba puede pertenecer al túnel, no a tu red."
fi

if [ "${VN_MAYOR:-0}" -le 20 ] && [ "${VN_MAYOR:-0}" -ge 18 ]; then
  aviso "Con Node 18/20 verás un aviso de 'fetch experimental'. Es inofensivo."
fi

# ─────────────────────────────────────────────────────────────────────────────
# Servicio systemd
# ─────────────────────────────────────────────────────────────────────────────
if [ "${1:-}" = "--servicio" ]; then
  titulo "Instalando el servicio systemd"

  if ! hay_privilegios; then
    error "Instalar un servicio del sistema requiere root o sudo."
    echo "  Vuelve a ejecutarlo con:  sudo ./scripts/instalar-kali.sh --servicio"
    echo "  O arranca sin servicio con:  npm start"
    exit 1
  fi

  USUARIO="$(id -un)"
  UNIDAD="/etc/systemd/system/$NOMBRE_SERVICIO.service"

  privilegiado tee "$UNIDAD" >/dev/null <<EOF
[Unit]
Description=Ojo de Dios — consola de vigilancia sobre fuentes públicas abiertas
Documentation=file://$RAIZ/README.md
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$USUARIO
WorkingDirectory=$RAIZ
ExecStart=$(command -v node) $RAIZ/server.js
Environment=NODE_ENV=production
Environment=PORT=$PUERTO
Restart=on-failure
RestartSec=5
# Endurecimiento básico: el proceso solo necesita leer su propio directorio.
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=read-only
ReadWritePaths=$RAIZ

[Install]
WantedBy=multi-user.target
EOF

  privilegiado systemctl daemon-reload
  privilegiado systemctl enable --now "$NOMBRE_SERVICIO"
  sleep 2

  if systemctl is-active --quiet "$NOMBRE_SERVICIO"; then
    ok "Servicio activo y habilitado al arrancar."
    printf '    Estado:   systemctl status %s\n' "$NOMBRE_SERVICIO"
    printf '    Registro: journalctl -u %s -f\n' "$NOMBRE_SERVICIO"
    printf '    Parar:    sudo systemctl stop %s\n' "$NOMBRE_SERVICIO"
    printf '    Quitar:   ./scripts/instalar-kali.sh --desinstalar\n'
  else
    error "El servicio no arrancó. Revisa:  journalctl -u $NOMBRE_SERVICIO -n 30"
    exit 1
  fi

  if command -v ufw >/dev/null 2>&1 && privilegiado ufw status 2>/dev/null | grep -q "Status: active"; then
    aviso "ufw está activo. Para entrar desde otro dispositivo de la red:"
    printf '      sudo ufw allow %s/tcp\n' "$PUERTO"
  fi

  exit 0
fi

# ─────────────────────────────────────────────────────────────────────────────
# Arranque en primer plano
# ─────────────────────────────────────────────────────────────────────────────
titulo "Listo"

if [ "${1:-}" = "--arrancar" ]; then
  paso "Arrancando el servidor. Corta con Ctrl+C."
  printf '\n'
  exec node server.js
fi

printf '  Para arrancarlo ahora:\n'
printf '      %scd %s && npm start%s\n\n' "$B" "$RAIZ" "$R"
printf '  Para que arranque solo al encender la máquina:\n'
printf '      %s./scripts/instalar-kali.sh --servicio%s\n\n' "$B" "$R"
printf '  Recuerda: solo feeds públicos abiertos. El panel "Límites legales"\n'
printf '  de la interfaz explica dónde está la frontera.\n\n'
