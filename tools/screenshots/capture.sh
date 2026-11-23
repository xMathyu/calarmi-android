#!/bin/bash
# shots.sh <lang:es|en|pt> <locale tag> <outdir>
set -u
ADB=$HOME/Library/Android/sdk/platform-tools/adb
SP=$(dirname "$0"); T=$SP/tapt.sh  # tools/screenshots/
LANG_=$1; TAG=$2; OUT=$3; mkdir -p "$OUT"
P=com.mathyusolutions.calarmi.debug
A=$P/com.mathyusolutions.calarmi.LauncherDefault
shot() { sleep 1.2; $ADB exec-out screencap -p > "$OUT/$1.png"; echo "  $1"; }
$ADB shell am force-stop $P
$ADB shell pm clear $P >/dev/null
for p in POST_NOTIFICATIONS READ_CALENDAR; do $ADB shell pm grant $P android.permission.$p 2>/dev/null; done
$ADB shell cmd locale set-app-locales $P --user 0 --locales $TAG
$ADB shell am start -n $A >/dev/null; sleep 3
$ADB shell am broadcast -a com.mathyusolutions.calarmi.debug.SEED_DEMO --es lang $LANG_ -p $P >/dev/null; sleep 2
$ADB shell am force-stop $P; $ADB shell am start -n $A >/dev/null; sleep 3
shot 01-alarmas
# Editor del cumpleaños
case $LANG_ in es) B="Cumpleaños de mamá"; C="Calendario"; D="Cuenta regresiva"; E="Asistente"; Q="Recuérdame la pastilla todos los días a las 9";;
  en) B="Mom's birthday"; C="Calendar"; D="Countdown"; E="Assistant"; Q="Remind me to take my pill every day at 9";;
  pt) B="Aniversário da mamãe"; C="Calendário"; D="Contagem regressiva"; E="Assistente"; Q="Me lembre do remédio todos os dias às 9";; esac
$ADB shell input swipe 540 1500 540 700 300; sleep 0.8
$T "$B" && shot 02-editor
$ADB shell input keyevent KEYCODE_BACK; sleep 1.5
$T "$C" && shot 03-calendario
$T "$D" && shot 04-cuentas
$T "$E" && sleep 1 && $T "$Q" && sleep 3 && shot 05-asistente
