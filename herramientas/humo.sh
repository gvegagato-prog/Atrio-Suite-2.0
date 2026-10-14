#!/bin/bash
# Prueba de humo en un emulador: instala la versión optimizada, la recorre y hace capturas.
set -u
PKG=com.gonzalo.gamerate
OUT=humo_out
mkdir -p "$OUT"
LOG="$OUT/pasos.txt"
: > "$LOG"
step() { echo "== $*" | tee -a "$LOG"; }
shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; }
alive() {
  if ! adb shell pidof "$PKG" >/dev/null 2>&1; then
    echo "LA APP SE HA CERRADO en: $1" | tee -a "$LOG"
    adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
    adb logcat -d | grep -E "AndroidRuntime|FATAL" -A40 >> "$OUT/crash.txt"
    exit 1
  fi
}
# Toca el elemento cuyo texto o descripción coincide (o empieza por) $1.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/ui.xml"
  xy=$(python3 - "$1" "$OUT/ui.xml" <<'PY'
import sys, re
t, f = sys.argv[1], sys.argv[2]
x = open(f, encoding='utf-8', errors='ignore').read()
best = None
for m in re.finditer(r'<node [^>]*>', x):
    n = m.group(0)
    txt = re.search(r' text="([^"]*)"', n); d = re.search(r'content-desc="([^"]*)"', n)
    vals = [v.group(1) for v in (txt, d) if v]
    if any(v == t for v in vals) or (best is None and any(v.startswith(t) for v in vals)):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            a = list(map(int, b.groups()))
            best = ((a[0]+a[2])//2, (a[1]+a[3])//2)
            if any(v == t for v in vals): break
print("" if best is None else f"{best[0]} {best[1]}")
PY
)
  if [ -z "$xy" ]; then echo "   no encuentro «$1»" | tee -a "$LOG"; return 1; fi
  echo "   toco «$1» en $xy" >> "$LOG"
  adb shell input tap $xy
  sleep 1.5
}
type() { adb shell input text "$1"; sleep 0.8; }

step "Instalar"
adb install -r movil/app/build/outputs/apk/release/app-release.apk | tee -a "$LOG"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null
step "Abrir"
adb shell am start -W -n "$PKG/.MainActivity" | tee -a "$LOG"
shot 01_menu 6; alive "abrir"

step "Nueva categoría"
# Con Películas y Series ya creadas, la casilla queda abajo: se baja antes
adb shell input swipe 500 1500 500 500 300; sleep 1
tap "Nueva categoría" && shot 02_dialogo; alive "dialogo"
tap "Nombre (p. ej. Películas)" && type "Peliculas"
tap "Criterio 1" && type "Guion"
tap "Criterio 2" && type "Actuacion"
adb shell input keyevent 4; sleep 1
tap "Guardar"; shot 03_menu_categoria; alive "crear categoria"

step "Entrar en la categoría y añadir"
adb shell input swipe 500 1500 500 500 300; sleep 1
tap "Peliculas"; shot 04_seccion; alive "seccion"
tap "Añadir"; shot 05_anadir; alive "anadir"
tap "Nombre" && type "Dune%sParte%sDos"
adb shell input keyevent 4; sleep 1
tap "Añadir a la lista"; shot 06_ficha 3; alive "ficha"
tap "Añadir capítulo o episodio" && type "Primera%sparte" && adb shell input keyevent 66
sleep 1; tap "Añadir capítulo o episodio" && type "Segunda%sparte" && adb shell input keyevent 66
adb shell input keyevent 4; sleep 1
tap "Primera parte"; shot 07_capitulo; alive "capitulo"
adb shell input swipe 500 1600 500 600 300; shot 08_ficha_abajo; alive "scroll"

step "Volver y pestañas"
adb shell input keyevent 4; sleep 1.5
shot 09_lista; alive "volver"
tap "Calendario"; shot 10_calendario; alive "calendario"
tap "Estadísticas"; shot 11_estadisticas; alive "estadisticas"

step "Accesos directos"
adb shell am start -W -n "$PKG/.MainActivity" --es go stats_game >/dev/null; shot 12_atajo_stats 3; alive "atajo stats"
adb shell am start -W -n "$PKG/.MainActivity" --es go add_book >/dev/null; shot 13_atajo_libro 3; alive "atajo libro"
adb shell input keyevent 4; sleep 1; adb shell input keyevent 4; sleep 1

step "Ajustes y tema"
tap "Ajustes"; shot 14_ajustes; alive "ajustes"
tap "Claro"; sleep 0.25; adb exec-out screencap -p > "$OUT/15_cambio_tema.png"; shot 16_claro 2; alive "tema"
tap "Oscuro"; shot 17_oscuro 2; alive "tema2"
adb shell input keyevent 4; sleep 1
step "Juegos"
tap "Videojuegos"; shot 18_juegos; alive "juegos"
tap "Siguiente"; shot 19_siguiente 3; alive "siguiente"

# ---------------- Cuentas (Firebase de mentira en el propio ordenador: 10.0.2.2 desde el emulador) ----------------
# Busca un texto bajando por la pantalla.
seek() {
  for i in 1 2 3 4 5 6 7 8 9 10; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml > "$OUT/ui.xml"
    grep -q "text=\"$1" "$OUT/ui.xml" && return 0
    adb shell input swipe 500 1500 500 900 400; sleep 0.8
  done
  echo "   no aparece «$1»" | tee -a "$LOG"; return 1
}
must() { if ! "$@"; then echo "FALLO: $*" | tee -a "$LOG"; adb logcat -d | grep -E "AndroidRuntime|FATAL|gamerate" -A10 | tail -80 > "$OUT/crash.txt"; exit 1; fi; }
SIM="node herramientas/fb_sim.js"

step "Cuentas: Firebase de mentira"
node herramientas/fakefirebase.js 9000 "$OUT/fb.json" > "$OUT/fb.log" 2>&1 &
sleep 2
adb shell am force-stop "$PKG"; sleep 1
adb shell am start -W -n "$PKG/.MainActivity" --es fbtest http://10.0.2.2:9000 | tee -a "$LOG"
sleep 4; alive "abrir con prueba"
tap "Ajustes"; sleep 1
must seek "Crear cuenta"
kb() { adb shell input keyevent 4; sleep 1; }
seek "Usuario"; tap "Usuario" && type "gonzalo" && kb
seek "Contraseña"; tap "Contraseña" && type "secreto1" && kb
seek "Tu nombre (lo verá tu pareja)"; tap "Tu nombre (lo verá tu pareja)" && type "Gonzalo" && kb
seek "Crear cuenta"; shot 20_cuenta_form 1
tap "Crear cuenta"; sleep 8; shot 21_cuenta_creada; alive "crear cuenta"
must $SIM has gonzalo secreto1 '"games"'
echo "   la cuenta tiene lo del móvil" | tee -a "$LOG"

step "Cuentas: un cambio hecho en otro dispositivo (como Atrio) llega al móvil"
must $SIM add gonzalo secreto1 990001 "Juego desde Atrio"
sleep 40; alive "esperar mezcla"
must $SIM has gonzalo secreto1 "Juego desde Atrio"
echo "   el móvil lo ha mezclado y vuelto a subir" | tee -a "$LOG"

step "Cuentas: un borrado en otro dispositivo se respeta"
must $SIM del gonzalo secreto1 990001
sleep 40; alive "esperar borrado"
if $SIM has gonzalo secreto1 "Juego desde Atrio"; then echo "FALLO: el borrado no ha llegado" | tee -a "$LOG"; exit 1; fi
echo "   borrado también en el móvil" | tee -a "$LOG"

step "Cuentas: pareja por nombre de usuario"
must $SIM pair pareja secreto2 Lucia gonzalo
must seek "Usuario de tu pareja"
tap "Usuario de tu pareja" && type "pareja" && kb
seek "Compartir con mi pareja"; tap "Compartir con mi pareja"; sleep 6
shot 22_pareja; alive "pareja"
must seek "Ya compartís"
echo "   ve lo de su pareja" | tee -a "$LOG"
curl -s http://127.0.0.1:9000/__dump > "$OUT/fb_final.json"

step "Cuentas: cerrar sesión y volver a entrar (juntar)"
must seek "Cerrar sesión"
tap "Cerrar sesión"; sleep 2
for i in 1 2 3; do adb shell input swipe 500 800 500 1500 300; sleep 0.6; done
must seek "Entrar"
# El usuario se queda puesto al cerrar sesión: solo hace falta la contraseña
seek "Contraseña"; tap "Contraseña" && type "secreto1" && kb
seek "Entrar"; tap "Entrar"; sleep 5; shot 23_entrar_dialogo; alive "entrar"
tap "Juntarlo todo"; sleep 6; shot 24_juntado; alive "juntar"
must seek "Has entrado como gonzalo"

# ---------------- La suite: el móvil con Atrio de verdad (bajo Xvfb) en el mismo ordenador ----------------
# ¿Aparece ese texto en la pantalla (en cualquier parte)?
see() {
  for i in $(seq 1 ${2:-20}); do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml > "$OUT/ui.xml"
    grep -q "$1" "$OUT/ui.xml" && return 0
    sleep 2
  done
  echo "   no aparece «$1»" | tee -a "$LOG"; return 1
}
top() { for i in 1 2 3 4; do adb shell input swipe 500 700 500 1700 250; sleep 0.4; done; }

step "Suite: Atrio se conecta y el móvil lo ve en directo"
node herramientas/lanzar-atrio.js http://127.0.0.1:9000 gonzalo secreto1 "$OUT" > "$OUT/lanzar-atrio.log" 2>&1 &
adb shell input keyevent 4; sleep 2
top
must see "Conectado, sin jugar" 30
shot 25_suite_conectado 1; alive "suite conectado"

step "Suite: captura del PC a calidad original desde el móvil"
tap "Captura"; sleep 3
must see "PNG original" 20
shot 26_captura 2; alive "captura"
must $SIM at gonzalo secreto1 suite/shots '"got"'
echo "   la captura ha llegado entera y el PC sabe que el móvil la tiene" | tee -a "$LOG"
adb shell input keyevent 4; sleep 2

step "Suite: lanzar un juego del PC desde el móvil"
tap "Mando"; sleep 2; shot 27_mando 1; alive "mando"
tap "Biblioteca"; sleep 2; shot 28_biblioteca_pc 1
tap "Juego de Prueba"; sleep 2
shot 28b_ficha_juego_pc 1
tap "Jugar en el PC"; sleep 4
adb shell input keyevent 4; sleep 2
top
must see "EN DIRECTO" 30
shot 29_en_directo 2; alive "en directo"

step "Suite: apunte rápido mientras se juega"
tap "Apunte"; sleep 2
# En Compose, «input text» de golpe pierde letras: se escribe a trozos
tap "Lo que está pasando" && for w in Apu nte dep rue ba; do adb shell input text "$w"; sleep 0.8; done; sleep 2
must see "Apuntedeprueba" 5
shot 30_apunte 1
tap "Guardar"; sleep 8; alive "apunte"
must $SIM has gonzalo secreto1 "Apuntedeprueba"
echo "   el apunte está en la cuenta" | tee -a "$LOG"

step "Suite: al cerrar el juego, la partida llega para valorarla"
sleep 30
pkill -f juego-de-prueba || true
top
must see "POR VALORAR" 40
shot 31_por_valorar 1; alive "por valorar"
tap "Valorar"; sleep 3
must see "Valorando" 10
shot 32_valorar_partida 1
seek "Guardar valoración"; tap "Guardar valoración"; sleep 6; alive "valorar"
must $SIM has gonzalo secreto1 '"seg":"a'
echo "   la partida tiene su valoración" | tee -a "$LOG"
step "Guías: compartir una web desde el navegador del móvil a un juego del PC"
adb shell am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://example.com/" -n "$PKG/.MainActivity" >/dev/null
sleep 3
must see "Guardar como guía" 10
shot 33_compartir_guia 1
tap "Juego de Prueba"; sleep 6; alive "compartir"
must $SIM at gonzalo secreto1 suite/lib 'example.com'
echo "   la web está en el juego del PC" | tee -a "$LOG"

step "Guías en la ficha del juego: se abren dentro de la app"
sleep 3
top
tap "Videojuegos"; sleep 2
tap "Juego de Prueba"; sleep 3
must see "Guías" 10
must see "example.com" 15
shot 34_ficha_guias 1; alive "ficha guias"
# Los dos botones grandes: jugar en el PC y en streaming (sin Nova instalado, ofrece descargarlo)
must see "Jugar en el PC" 10
must see "Streaming" 5
tap "Streaming"; sleep 2
must see "Falta Nova" 8
shot 37_falta_nova 1
tap "Ahora no"; sleep 1
echo "   botones de jugar y de streaming en la ficha" | tee -a "$LOG"
tap "example.com"; sleep 10
shot 35_guia_abierta 1; alive "guia abierta"
must see "Abrir en el navegador" 5
must see "Example Domain" 20
see "documentation examples" 30 || { cp "$OUT/ui.xml" "$OUT/ui_guia.xml"; shot 35b_guia_sin_texto 1; echo "   FALLO: la web no pinta su contenido" | tee -a "$LOG"; exit 1; }
shot 35_guia_abierta 1
echo "   la guía se abre dentro de Atrio" | tee -a "$LOG"
# Sales un rato de la app (botón de inicio) y vuelves desde el icono: la guía sigue abierta
adb shell input keyevent 3; sleep 4
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 5
must see "documentation examples" 15
shot 36_guia_al_volver 1; alive "guia al volver"
echo "   al volver a la app la guía sigue ahí" | tee -a "$LOG"
# Cerrada con ✕ y vuelta a abrir: instantánea, en la misma página
tap "Cerrar"; sleep 2
must see "Guías" 10
tap "example.com"; sleep 3
must see "documentation examples" 10
echo "   cerrada y reabierta, sigue en su sitio" | tee -a "$LOG"
adb shell input keyevent 4; sleep 2
# La ficha de «Jugando» se abre en modo lectura: los campos salen con «Editar»
must seek "Editar"
shot 38_ficha_lectura 1
tap "Editar"; sleep 1
must seek "Listo"
shot 39_ficha_editando 1
tap "Listo"; sleep 1
echo "   la ficha se abre en modo lectura y se edita con «Editar»" | tee -a "$LOG"
pkill -f lanzar-atrio || true

step "Estilo: combinaciones, colores, letras e icono de la app"
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 2
for i in 1 2 3 4 5; do see "Ajustes" 1 && break; adb shell input keyevent 4; sleep 1.5; done
tap "Ajustes"; sleep 2
must see "Estilo" 10
tap "Estilo"; sleep 2
must see "Combinaciones" 10
shot 40_estilo 2; alive "estilo"
tap "Revista"; sleep 3; shot 41_revista 2; alive "revista"
tap "Arcade"; sleep 3; shot 42_arcade 2; alive "arcade"
tap "Colores"; sleep 2; shot 43_paletas 2
tap "Medianoche"; sleep 3; shot 44_medianoche 2; alive "paleta"
tap "Títulos"; sleep 3; shot 45_letras 2
tap "Tamaño"; sleep 1; tap "Grande"; sleep 2; shot 46_grande 2; tap "Normal"; sleep 1
tap "Icono"; sleep 2; shot 47_iconos 2
tap "Neón"; sleep 4; alive "icono"
adb shell dumpsys package "$PKG" > "$OUT/paquete.txt" 2>/dev/null || true
icono_neon() { grep -A30 "enabledComponents" "$OUT/paquete.txt" | grep -q "IconNeon"; }
must icono_neon
echo "   el icono de la app ha cambiado a Neón" | tee -a "$LOG"
tap "Atrio"; sleep 3
tap "Combinaciones"; sleep 1; tap "Atrio"; sleep 2
adb shell input keyevent 4; sleep 1

step "OK: la app no se ha cerrado en ningún paso"
adb logcat -d | grep -E "AndroidRuntime|FATAL" -A20 > "$OUT/errores_log.txt" || true
