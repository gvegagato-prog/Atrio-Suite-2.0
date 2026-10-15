#!/bin/bash
# Prueba de humo de Atrio 6 en un emulador: instala la versión optimizada, la recorre entera y hace capturas.
set -u
PKG=com.gonzalo.gamerate
OUT=humo_out
mkdir -p "$OUT"
LOG="$OUT/pasos.txt"
: > "$LOG"
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
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
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui.xml"; }
# Toca el elemento cuyo texto o descripción coincide (o empieza por) $1. Con $2 = n, el n-ésimo que coincida exacto.
tap() {
  local xy="" try
  for try in 1 2; do
    dump
    xy=$(python3 - "$1" "$OUT/ui.xml" "${2:-1}" "$try" <<'PY'
import sys, re
t, f, nth, attempt = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
x = open(f, encoding='utf-8', errors='ignore').read()
H = max([int(b) for b in re.findall(r'bounds="\[\d+,\d+\]\[\d+,(\d+)\]"', x)] or [2280])
best = None; seen = 0; hidden = False
for m in re.finditer(r'<node [^>]*>', x):
    n = m.group(0)
    txt = re.search(r' text="([^"]*)"', n); d = re.search(r'content-desc="([^"]*)"', n)
    vals = [v.group(1) for v in (txt, d) if v]
    vals = [v[2:-2] if v.startswith('[ ') and v.endswith(' ]') else v for v in vals]  # [ botones ] de la forma Terminal
    exact = any(v == t for v in vals)
    if exact or (best is None and any(v.startswith(t) for v in vals)):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            a = list(map(int, b.groups()))
            if a[2] - a[0] < 4 or a[3] - a[1] < 4: hidden = True; continue  # nodos sin tamaño (fuera de pantalla)
            if exact:
                seen += 1
                if nth and seen < nth: continue
            best = ((a[0]+a[2])//2, (a[1]+a[3])//2)
            if exact and nth: break
DOCK = {"Inicio", "Juegos", "Libros", "Cine", "Diario"}
# Lo que queda tapado por el dock o la barra de navegación: primero se sube un poco la pantalla
low = attempt == 1 and t not in DOCK and ((best is not None and best[1] > H - H * 0.16) or (best is None and hidden))
print("BAJO" if low else ("" if best is None else f"{best[0]} {best[1]}"))
PY
)
    if [ "$xy" = "BAJO" ]; then adb shell input swipe 540 1300 540 800 500; sleep 1.5; continue; fi
    break
  done
  if [ -z "$xy" ]; then
    NF=$(( ${NF:-0} + 1 )); cp "$OUT/ui.xml" "$OUT/no_encuentro_$NF.xml"; adb exec-out screencap -p > "$OUT/no_encuentro_$NF.png"
    echo "   no encuentro «$1» (no_encuentro_$NF)" | tee -a "$LOG"; return 1
  fi
  echo "   toco «$1» en $xy" >> "$LOG"
  adb shell input tap $xy
  sleep 1.5
}
type() { adb shell input text "$1"; sleep 0.8; }
# Busca un texto bajando por la pantalla.
seek() {
  for i in $(seq 1 14); do
    dump
    # Visible de verdad: los nodos fuera de pantalla salen con tamaño cero
    grep -o "\(text\|content-desc\)=\"$1[^>]*bounds=\"[^\"]*\"" "$OUT/ui.xml" | grep -vq '\[0,0\]\[0,0\]' && return 0
    # Despacio, para que la lista no siga deslizándose y se salte lo que busco
    adb shell input swipe 500 1500 500 1050 1000; sleep 0.8
  done
  echo "   no aparece «$1»" | tee -a "$LOG"; return 1
}
must() { if ! "$@"; then echo "FALLO: $*" | tee -a "$LOG"; adb logcat -d | grep -E "AndroidRuntime|FATAL|gamerate|chromium|Console" -A10 | tail -120 > "$OUT/crash.txt"; exit 1; fi; }
# ¿Aparece ese texto en la pantalla (en cualquier parte)?
see() {
  for i in $(seq 1 ${2:-20}); do
    dump
    grep -q "$1" "$OUT/ui.xml" && return 0
    sleep 2
  done
  echo "   no aparece «$1»" | tee -a "$LOG"; return 1
}
top() { for i in 1 2 3 4; do adb shell input swipe 500 700 500 1700 250; sleep 0.4; done; }
kb() { adb shell input keyevent 4; sleep 1; }
back() { adb shell input keyevent 4; sleep 1.5; }
# Destino del dock (los iconos llevan su nombre como descripción)
dock() { must tap "$1" 0; sleep 1.5; }
home() { for i in 1 2 3 4; do see "Inicio" 1 && break; back; done; dock "Inicio"; top; }
SIM="node herramientas/fb_sim.js"

step "Instalar (Android $SDK)"
adb install -r movil/app/build/outputs/apk/release/app-release.apk | tee -a "$LOG"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null
step "Abrir: Inicio"
adb shell am start -W -n "$PKG/.MainActivity" --ei shell 0 | tee -a "$LOG"
shot 01_inicio 6; alive "abrir"
must see "Buen" 10

step "Colección nueva desde Inicio"
must seek "Nueva colección"
tap "Nueva colección" && shot 02_dialogo; alive "dialogo"
tap "Nombre (p. ej. Películas)" && type "Peliculas"
tap "Criterio 1" && type "Guion"
tap "Criterio 2" && type "Actuacion"
kb
tap "Guardar"; sleep 1; alive "crear coleccion"
must seek "Peliculas"
shot 03_inicio_coleccion 1
tap "Peliculas"; sleep 2; shot 04_coleccion; alive "coleccion"
tap "Añadir"; shot 05_anadir; alive "anadir"
tap "Nombre" && type "Dune%sParte%sDos"
kb
tap "Añadir a la lista"; shot 06_ficha 3; alive "ficha"
tap "Añadir capítulo o episodio" && type "Primera%sparte" && adb shell input keyevent 66
sleep 1.5
# Con el primero añadido, la lista se cierra y se amplía con «Editar lista»
must seek "Primera parte"; tap "Primera parte"; shot 07_capitulo; alive "capitulo"
must see "0 de 1\|1 de 1" 5
adb shell input swipe 500 1600 500 600 300; shot 08_ficha_abajo; alive "scroll"
back; shot 09_coleccion_lista; alive "volver"

step "Dock: Juegos, Libros, Cine y Diario"
home
dock "Juegos"; shot 10_juegos; alive "juegos"
tap "Pasados"; sleep 1; shot 10b_pasados
must tap "Siguiente juego"; shot 11_siguiente 3; alive "siguiente"
back
dock "Libros"; shot 12_libros; alive "libros"
dock "Cine"; shot 13_cine; alive "cine"
tap "Series"; sleep 1.5; shot 13b_series; alive "series"
dock "Diario"; shot 14_diario; alive "diario"
tap "Libros"; sleep 1; shot 14b_diario_libros

step "Descubrir libros con preguntas"
dock "Libros"
must tap "Descubrir"; sleep 1.5
must see "Qué leo ahora" 8
shot 15_descubrir 1
must tap "5"; sleep 1.5
shot 16_pregunta 1; alive "pregunta"
for i in 1 2 3 4 5; do tap "A" || tap "B"; sleep 1.2; done
must see "A mi lista\|No ha salido nada\|resultados" 40
shot 17_resultados 3; alive "resultados"
if see "A mi lista" 2; then tap "A mi lista"; sleep 2; must see "En tu lista" 6; echo "   libro descubierto añadido a la lista" | tee -a "$LOG"; fi
back

step "Perfil y ajustes"
home
must tap "Perfil y ajustes"; sleep 2
must see "Perfil" 5
shot 18_perfil 1; alive "perfil"
must seek "Ajustes"; tap "Ajustes"; sleep 2
shot 19_ajustes 1; alive "ajustes"
back

step "Estilo: tema, combinaciones, formas, notas, acento, interfaz, fondo e icono"
top
must tap "Estilo"; sleep 2
must see "Combinaciones" 10
shot 20_estilo 2; alive "estilo"
tap "Claro"; sleep 0.25; adb exec-out screencap -p > "$OUT/21_cambio_tema.png"; shot 21b_claro 2; alive "tema"
tap "Oscuro"; sleep 2
seek "Revista"; tap "Revista"; sleep 3; shot 22_revista 2; alive "revista"
seek "Arcade"; tap "Arcade"; sleep 3; top; shot 23_arcade 2; alive "arcade"
must tap "Forma"; sleep 1; must seek "Píxel"; tap "Píxel"; sleep 2; top; shot 24_forma_pixel 1
must tap "Notas"; sleep 1; must seek "Estrellas"; tap "Estrellas"; sleep 2; top; shot 25_notas_estrellas 1
must tap "Acento"; sleep 1; shot 26_acento 1; alive "acento"
must tap "Interfaz"; sleep 1; tap "Sobrio"; sleep 1; must seek "Grandes"; tap "Grandes"; sleep 1; shot 27_interfaz 1; tap "Normales"; top; tap "Vivo"; top
must tap "Fondo"; sleep 1; must see "Camaleón" 5; shot 28_fondo 1; alive "fondo"
tap "Sorpréndeme"; sleep 3; shot 29_sorpresa 1; alive "sorpresa"
tap "Icono"; sleep 2; shot 30_iconos 2
tap "Neón"; sleep 4; alive "icono"
adb shell dumpsys package "$PKG" > "$OUT/paquete.txt" 2>/dev/null || true
icono_neon() { grep -A30 "enabledComponents" "$OUT/paquete.txt" | grep -q "IconNeon"; }
must icono_neon
echo "   el icono de la app ha cambiado a Neón" | tee -a "$LOG"
tap "Atrio"; sleep 3
must tap "Combinaciones"; sleep 1; top; tap "Vitrina"; sleep 2
back; back

# ---------------- Cuentas (Firebase de mentira en el propio ordenador: 10.0.2.2 desde el emulador) ----------------
step "Cuentas: Firebase de mentira"
node herramientas/fakefirebase.js 9000 "$OUT/fb.json" > "$OUT/fb.log" 2>&1 &
sleep 2
adb shell am force-stop "$PKG"; sleep 1
adb shell am start -W -n "$PKG/.MainActivity" --ei shell 0 --es fbtest http://10.0.2.2:9000 | tee -a "$LOG"
sleep 4; alive "abrir con prueba"
must tap "Perfil y ajustes"; sleep 1
must seek "Crear cuenta"
seek "Usuario"; tap "Usuario" && type "gonzalo" && kb
seek "Contraseña"; tap "Contraseña" && type "secreto1" && kb
seek "Tu nombre (lo verá tu pareja)"; tap "Tu nombre (lo verá tu pareja)" && type "Gonzalo" && kb
seek "Crear cuenta"; shot 31_cuenta_form 1
tap "Crear cuenta"; sleep 8; shot 32_cuenta_creada; alive "crear cuenta"
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
shot 33_pareja; alive "pareja"
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
seek "Entrar"; tap "Entrar"; sleep 5; shot 34_entrar_dialogo; alive "entrar"
tap "Juntarlo todo"; sleep 6; shot 35_juntado; alive "juntar"
must seek "Has entrado como gonzalo"

# ---------------- La suite: el móvil con Atrio de verdad (bajo Xvfb) en el mismo ordenador ----------------
step "Suite: Atrio se conecta y el móvil lo ve en directo (Inicio)"
node herramientas/lanzar-atrio.js http://127.0.0.1:9000 gonzalo secreto1 "$OUT" > "$OUT/lanzar-atrio.log" 2>&1 &
back; sleep 2
top
must seek "Conectado, sin jugar"
shot 36_suite_conectado 1; alive "suite conectado"

step "Suite: captura del PC a calidad original desde el móvil"
tap "Captura"; sleep 3
must see "PNG original" 20
shot 37_captura 2; alive "captura"
must $SIM at gonzalo secreto1 suite/shots '"got"'
echo "   la captura ha llegado entera y el PC sabe que el móvil la tiene" | tee -a "$LOG"
back

step "Suite: lanzar un juego del PC desde el móvil"
must seek "Mando"; tap "Mando"; sleep 2; shot 38_mando 1; alive "mando"
tap "Biblioteca"; sleep 2; shot 39_biblioteca_pc 1
tap "Juego de Prueba"; sleep 2
shot 39b_ficha_juego_pc 1
tap "Jugar en el PC"; sleep 4
back
top
must seek "EN DIRECTO"
shot 40_en_directo 2; alive "en directo"

step "Suite: apunte rápido mientras se juega"
tap "Apunte"; sleep 2
# En Compose, «input text» de golpe pierde letras: se escribe a trozos
tap "Lo que está pasando" && for w in Apu nte dep rue ba; do adb shell input text "$w"; sleep 0.8; done; sleep 2
must see "Apuntedeprueba" 5
shot 41_apunte 1
tap "Guardar"; sleep 8; alive "apunte"
must $SIM has gonzalo secreto1 "Apuntedeprueba"
echo "   el apunte está en la cuenta" | tee -a "$LOG"

step "Suite: al cerrar el juego, la partida llega para valorarla"
sleep 30
pkill -f juego-de-prueba || true
top
must seek "POR VALORAR"
shot 42_por_valorar 1; alive "por valorar"
tap "Valorar"; sleep 3
must see "Valorando" 10
shot 43_valorar_partida 1
seek "Guardar valoración"; tap "Guardar valoración"; sleep 6; alive "valorar"
must $SIM has gonzalo secreto1 '"seg":"a'
echo "   la partida tiene su valoración" | tee -a "$LOG"

step "Guías: compartir una web desde el navegador del móvil a un juego del PC"
adb shell am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://example.com/" -n "$PKG/.MainActivity" >/dev/null
sleep 3
must see "Guardar como guía" 10
shot 44_compartir_guia 1
tap "Juego de Prueba"; sleep 6; alive "compartir"
must $SIM at gonzalo secreto1 suite/lib 'example.com'
echo "   la web está en el juego del PC" | tee -a "$LOG"

step "Ficha del juego: guías, jugar en el PC y streaming"
sleep 3
home
dock "Juegos"
must seek "Juego de Prueba"; tap "Juego de Prueba"; sleep 3
must see "Guías" 10
must seek "example.com"
shot 45_ficha_guias 1; alive "ficha guias"
# Los dos botones grandes: jugar en el PC y en streaming (sin Nova instalado, ofrece descargarlo)
must seek "Jugar en el PC"
must see "Streaming" 5
tap "Streaming"; sleep 2
must see "Falta Nova" 8
shot 46_falta_nova 1
tap "Ahora no"; sleep 1
echo "   botones de jugar y de streaming en la ficha" | tee -a "$LOG"
must seek "example.com"; tap "example.com"; sleep 10
shot 47_guia_abierta 1; alive "guia abierta"
must see "Abrir en el navegador" 5
must see "Example Domain" 20
see "documentation examples" 30 || { cp "$OUT/ui.xml" "$OUT/ui_guia.xml"; shot 47b_guia_sin_texto 1; echo "   FALLO: la web no pinta su contenido" | tee -a "$LOG"; exit 1; }
echo "   la guía se abre dentro de Atrio" | tee -a "$LOG"
# Sales un rato de la app (botón de inicio) y vuelves desde el icono: la guía sigue abierta
adb shell input keyevent 3; sleep 4
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 5
must see "documentation examples" 15
shot 48_guia_al_volver 1; alive "guia al volver"
echo "   al volver a la app la guía sigue ahí" | tee -a "$LOG"
tap "Cerrar"; sleep 2
# Si «Cerrar» ya ha devuelto a la biblioteca, se vuelve a la ficha
see "Guías" 3 || { tap "Juego de Prueba"; sleep 3; }
# La ficha de «Jugando» se abre en modo lectura: los campos salen con «Editar»
top
must see "Editar" 10
shot 49_ficha_lectura 1
tap "Editar"; sleep 1
must see "Listo" 8
shot 50_ficha_editando 1
tap "Listo"; sleep 1
must see "Editar" 8
echo "   la ficha se abre en modo lectura y se edita con «Editar»" | tee -a "$LOG"
pkill -f lanzar-atrio || true

# ---------------- Lector de libros (WebView moderno: Android 12 o más) ----------------
if [ "${SDK:-0}" -ge 31 ]; then
  step "Lector: adjuntar un EPUB del móvil y leerlo"
  adb push herramientas/lector/atrio-prueba.epub /sdcard/Download/atrio-prueba.epub >/dev/null
  adb push herramientas/lector/atrio-prueba.pdf /sdcard/Download/atrio-prueba.pdf >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/atrio-prueba.epub >/dev/null 2>&1
  # El libro llega por la cuenta, como si se hubiera añadido en otro dispositivo (no depende de buscar en internet)
  must $SIM book gonzalo secreto1 990077 "Libro de prueba"
  sleep 40; alive "esperar libro"
  home
  dock "Libros"
  tap "En lista"; sleep 2
  shot 51_libros_en_lista 1
  must seek "Libro de prueba"; tap "Libro de prueba"; sleep 3
  must seek "Elegir archivo"; shot 52_ficha_libro 1
  tap "Elegir archivo"; sleep 4
  shot 53_selector 1
  # Selector de archivos de Android: Descargas → el archivo
  tap "atrio-prueba.epub" || { tap "Show roots" || tap "Mostrar raíces"; sleep 2; tap "Downloads" || tap "Descargas"; sleep 3; tap "atrio-prueba.epub"; }
  sleep 6
  # Aviso de Android «Viewing full screen» la primera vez que una app oculta las barras
  tap "GOT IT" || tap "Entendido" || true
  sleep 4
  shot 54_lector 2; alive "lector"
  if ! see "Capítulo primero\|En un lugar de la Mancha" 20; then
    adb logcat -d -s AtrioLector > "$OUT/lector_consola.txt"; shot 54b_lector_fallo 1
    echo "FALLO: el libro no se ve en el lector (consola en lector_consola.txt)" | tee -a "$LOG"; exit 1
  fi
  adb logcat -d -s AtrioLector > "$OUT/lector_consola.txt"
  echo "   el libro se abre en el lector" | tee -a "$LOG"
  adb shell input tap 540 1200; sleep 2
  shot 55_lector_barras 1
  tap "Índice" && sleep 2 && shot 56_lector_indice 1
  must see "Capítulo tercero" 6
  back; sleep 1
  tap "Letra" && sleep 2 && shot 57_lector_letra 1
  back; sleep 1
  # Mantener pulsada una palabra: su menú (significado, RAE…); «Subrayar» y mantener pulsada la última palabra
  adb shell input swipe 400 1100 400 1100 1200; sleep 4
  shot 57b_lector_palabra 1; alive "palabra"
  must see "Subrayar" 6
  must see "RAE" 3
  echo "   mantener pulsada una palabra abre su menú" | tee -a "$LOG"
  must tap "Subrayar"; sleep 1.5
  must see "última palabra" 5
  adb shell input swipe 600 1400 600 1400 1200; sleep 2.5
  shot 57c_lector_tramo 1
  must tap "Verde"; sleep 2
  echo "   subrayado de la primera a la última palabra" | tee -a "$LOG"
  adb shell input tap 540 1200; sleep 2
  must tap "Notas"; sleep 2; shot 57d_lector_notas 1
  must see "Subrayados (1)" 6
  echo "   el subrayado se guarda" | tee -a "$LOG"
  back; sleep 1
  adb shell input keyevent 25; sleep 1.5; adb shell input keyevent 25; sleep 1.5
  shot 58_lector_pagina 1
  back; back; sleep 2
  must see "leído" 10
  shot 59_ficha_leyendo 1
  echo "   la ficha muestra lo leído y «Seguir leyendo»" | tee -a "$LOG"
else
  echo "== Lector: se prueba en Android 12 o más (este emulador es Android $SDK)" | tee -a "$LOG"
fi

step "Revista: portada, imagen de portada, biblioteca y ficha en casillas"
adb shell am force-stop "$PKG"; sleep 1
adb shell am start -W -n "$PKG/.MainActivity" --ei shell 2 >/dev/null; sleep 5
shot 60_revista 2; alive "revista"
must see "EN PORTADA" 10
if tap "CAMBIAR IMAGEN"; then sleep 6; shot 60b_cambiar_imagen 1; alive "cambiar imagen"; back; sleep 2; fi
must seek "Videojuegos"; tap "Videojuegos"; sleep 2
shot 61_revista_biblioteca 1; alive "biblioteca revista"
must seek "Juego de Prueba"; tap "Juego de Prueba"; sleep 3
shot 62_revista_ficha 1; alive "ficha revista"
must see "GUÍAS" 5
# Modo edición: el diseño de la página
must tap "EDITAR"; sleep 2
must see "FILA 1" 6
shot 63_diseno 1
# La línea se arrastra: el círculo ⇔ tiene que moverse
pos() { dump; python3 - "$1" "$OUT/ui.xml" <<'PY'
import sys, re
t, f = sys.argv[1], sys.argv[2]
for m in re.finditer(r'<node [^>]*>', open(f, encoding='utf-8', errors='ignore').read()):
    n = m.group(0)
    if f'text="{t}"' in n or f'content-desc="{t}"' in n:
        b = list(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()))
        print((b[0]+b[2])//2, (b[1]+b[3])//2); break
PY
}
P1=$(pos "⇔"); echo "   línea en $P1" >> "$LOG"
if [ -n "$P1" ]; then
  set -- $P1; adb shell input swipe $1 $2 $(( $1 + 220 )) $2 900; sleep 1.5
  P2=$(pos "⇔"); echo "   línea movida a $P2" >> "$LOG"; set -- $P1 $P2
  if [ -n "$P2" ] && [ $(( $3 - $1 )) -gt 60 ]; then echo "   la línea se mueve arrastrándola" | tee -a "$LOG"; else echo "FALLO: la línea no se mueve" | tee -a "$LOG"; exit 1; fi
fi
shot 63b_linea 1
# El alto de la fila: se arrastra el círculo ⇕ hacia abajo y la fila crece
seek "Alto de la fila 1"
# Que el círculo quede a media pantalla (con sitio para arrastrarlo hacia abajo)
H0=$(pos "Alto de la fila 1")
if [ -n "$H0" ]; then set -- $H0; if [ $2 -gt 1100 ]; then adb shell input swipe 540 $(( $2 - 160 )) 540 $(( 900 - 160 )) 900; sleep 1.5; fi; fi
H1=$(pos "Alto de la fila 1"); echo "   alto en $H1" >> "$LOG"
if [ -n "$H1" ]; then
  set -- $H1; adb shell input swipe $1 $2 $1 $(( $2 + 260 )) 900; sleep 1.5
  H2=$(pos "Alto de la fila 1"); echo "   alto movido a $H2" >> "$LOG"; set -- $H1 $H2
  if [ -n "$H2" ] && [ $(( $4 - $2 )) -gt 100 ]; then echo "   la fila cambia de alto arrastrando" | tee -a "$LOG"; else echo "FALLO: la fila no cambia de alto" | tee -a "$LOG"; exit 1; fi
fi
shot 63e_alto 1
tap "ALTO AUTO" || true; sleep 1
top
# Texto libre en la casilla de las guías
must tap "Casilla Guías"; sleep 1.5
must see "¿Qué va aquí?" 5
must tap "Texto libre (título y texto)"; sleep 1.5
tap "Título (opcional)" && type "Mi%sveredicto"
tap "Texto" && type "Lo%smejor%sdel%sjuego"
kb
must tap "Guardar"; sleep 1.5
must see "Casilla Mi veredicto" 5
echo "   texto libre en una casilla" | tee -a "$LOG"
# Imagen: se abre el selector (en el emulador no hay clave de SteamGridDB)
must tap "Casilla Mi veredicto"; sleep 1.5
must tap "Imagen"; sleep 3; shot 63c_imagen 1
must see "PORTADA" 5
tap "Cerrar"; sleep 1
must tap "LISTO"; sleep 1.5
shot 63d_diseno_hecho 1; alive "diseño"
must see "MI VEREDICTO" 5
echo "   el diseño se guarda" | tee -a "$LOG"
adb shell input swipe 540 1600 540 700 500; sleep 1; shot 62b_revista_ficha 1
must seek "EDITAR"
back; back; sleep 1; alive "volver revista"
echo "   Revista: portada, biblioteca y ficha en casillas" | tee -a "$LOG"

step "OK: la app no se ha cerrado en ningún paso"
adb logcat -d | grep -E "AndroidRuntime|FATAL" -A20 > "$OUT/errores_log.txt" || true
