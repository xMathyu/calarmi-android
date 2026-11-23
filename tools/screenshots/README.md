# Capturas de Google Play

Con el emulador encendido (`calarmi36`, Android 16) y la build de **debug** instalada:

```sh
adb root && adb shell wm size 1080x1920          # 16:9 — Play rechaza más de 2:1
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter   # barra de estado limpia (9:41)
tools/screenshots/capture.sh es es    store/screenshots/es-419
tools/screenshots/capture.sh en en-US store/screenshots/en-US
tools/screenshots/capture.sh pt pt-BR store/screenshots/pt-BR
adb shell wm size reset
```

Los datos salen de `app/src/debug/.../DemoSeedReceiver.kt` (los mismos de las capturas de iOS);
ese receptor solo existe en la build de debug.
