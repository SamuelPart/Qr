#!/usr/bin/env bash
#
# hacer-iconos-android.sh — regenera el icono de la app en todas las densidades
# ---------------------------------------------------------------------------
# Toma build/icono-fuente.png (1024×1024) y produce lo que Android necesita:
#
#   mipmap-*/ic_launcher.png             icono heredado, cuadrado redondeado
#   mipmap-*/ic_launcher_round.png       icono heredado, circular
#   mipmap-*/ic_launcher_foreground.png  capa frontal del icono adaptativo (API 26+)
#   values/ic_launcher_background.xml    color de fondo del icono adaptativo
#
# Sobre el icono adaptativo: el lanzador recorta la capa frontal a un círculo,
# un cuadrado redondeado o una gota, según el fabricante. Android garantiza que
# solo se ve el 66% central, así que el arte se reduce a esa zona segura; si no,
# los anillos exteriores quedarían cortados por la máscara.
#
# Uso:  ./scripts/hacer-iconos-android.sh
# Requiere: ImageMagick (sudo apt install imagemagick)

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RES="$RAIZ/android/app/src/main/res"
SRC="$RAIZ/build/icono-fuente.png"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if ! command -v convert >/dev/null 2>&1; then
  echo "Falta ImageMagick.  Instálalo con:  sudo apt install imagemagick"
  exit 1
fi

if [ ! -f "$SRC" ]; then
  echo "No encuentro el arte fuente: $SRC"
  echo "Debe ser un PNG cuadrado de 1024×1024 o mayor."
  exit 1
fi

LADO="$(identify -format '%w' "$SRC")"
echo "Arte fuente: ${LADO}×${LADO} px"

# ── Zona segura del icono adaptativo ────────────────────────────────────────
# Lienzo de 1024 con el arte dentro del 66% central.
convert "$SRC" -resize 1024x1024 -alpha on \
  \( -size 1024x1024 xc:none -fill white -draw "rectangle 341,341 683,683" \) \
  -compose DstIn -composite "$TMP/foreground.png"

# ── Color de fondo = el del propio arte, muestreado lejos de los bordes ─────
# (los bordes pueden ser transparentes o tener un marco claro)
BG_HEX="$(convert "$SRC" -resize 1024x1024 -format '%[hex:p{512,60}]' info: | cut -c1-6)"
echo "Color de fondo: #$BG_HEX"

cat > "$RES/values/ic_launcher_background.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Generado por scripts/hacer-iconos-android.sh a partir del arte del icono. -->
    <color name="ic_launcher_background">#$BG_HEX</color>
</resources>
XML

# ── Todas las densidades ───────────────────────────────────────────────────
for par in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  DEN="${par%%:*}"
  PX="${par##*:}"
  R2=$(( PX / 2 ))
  RADIO=$(( PX * 22 / 100 ))

  # Heredado: cuadrado redondeado.
  # El arte trae sus propias esquinas, que se enmascaran a transparente para que
  # no aparezca un recuadro claro sobre fondos oscuros.
  convert "$SRC" -resize "${PX}x${PX}" -alpha set \
    \( -size "${PX}x${PX}" xc:none -fill white \
       -draw "roundrectangle 0,0 $((PX - 1)),$((PX - 1)) $RADIO,$RADIO" \) \
    -compose DstIn -composite "$RES/mipmap-$DEN/ic_launcher.png"

  # Heredado: circular (lanzadores que piden el redondo).
  convert "$SRC" -resize "${PX}x${PX}" -alpha set \
    \( -size "${PX}x${PX}" xc:none -fill white \
       -draw "circle $R2,$R2 $R2,0" \) \
    -compose DstIn -composite "$RES/mipmap-$DEN/ic_launcher_round.png"

  # Adaptativo: lienzo de 108dp para que el arte ocupe los 72dp visibles.
  LADO_FG=$(( PX * 108 / 48 ))
  convert "$TMP/foreground.png" -resize "${LADO_FG}x${LADO_FG}" \
    "$RES/mipmap-$DEN/ic_launcher_foreground.png"

  printf '  %-10s %3d px\n' "$DEN" "$PX"
done

echo
echo "Iconos regenerados. Para verlos en el celular:"
echo "    npm run android:sync   y vuelve a compilar"
