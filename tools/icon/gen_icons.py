#!/usr/bin/env python3
"""Genera el ícono adaptativo de Android desde el diseño de iOS (Calarm/Tools/icon/light.svg):
despertador blanco con las agujas en forma de check sobre un degradado.

Escribe en app/src/main/res:
- drawable/ic_launcher_fg_<variante>.xml   (primer plano, 108 dp, dentro de la zona segura)
- drawable/ic_launcher_bg_<variante>.xml   (degradado de fondo)
- drawable/ic_launcher_mono.xml            (íconos temáticos de Android 13+)
- mipmap-anydpi/ic_launcher[_<variante>].xml y ic_launcher_round.xml

Variantes (Calarmi Plus): las mismas de iOS, girando el tono del naranja.
Uso: python3 tools/icon/gen_icons.py
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# (claro, oscuro, cara del reloj, marcas)
VARIANTS = {
    "": ("#FFA066", "#EE4E1B", "#FFF3EA", "#F6B896"),
    "blue": ("#66B5FF", "#1B6FEE", "#EAF4FF", "#96C4F6"),
    "purple": ("#C88CFF", "#8A35E6", "#F5EAFF", "#CDA6F2"),
    "green": ("#7FE08C", "#1FA046", "#EAFBEE", "#9ED9AB"),
    "pink": ("#FF8CC4", "#E8237E", "#FFEAF4", "#F5A6CB"),
}

# El SVG mide 1024; el contenido (centro ~512,538) se escala al centro del lienzo de 108 dp.
SCALE = 0.1
TX = 54 - 512 * SCALE
TY = 54 - 538 * SCALE


def circle(cx, cy, r):
    return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z"


def foreground(face, ticks, check, white="#FFFFFF", mono=False):
    face_fill = white if mono else face
    body = f'''    <group android:scaleX="{SCALE}" android:scaleY="{SCALE}" android:translateX="{TX:.2f}" android:translateY="{TY:.2f}">
        <group android:translateY="-34">
            <path android:strokeColor="{white}" android:strokeWidth="56" android:strokeLineCap="round" android:pathData="M372,790 L322,872 M652,790 L702,872 M512,300 L512,264" />
            <path android:fillColor="{white}" android:pathData="{circle(512, 246, 30)}" />
            <group android:translateX="318" android:translateY="318" android:rotation="-42">
                <path android:fillColor="{white}" android:pathData="M-118,36 A118,118 0 0,1 118,36 Z" />
            </group>
            <group android:translateX="706" android:translateY="318" android:rotation="42">
                <path android:fillColor="{white}" android:pathData="M-118,36 A118,118 0 0,1 118,36 Z" />
            </group>
            <path android:fillColor="{white}" android:pathData="{circle(512, 572, 284)}" />'''
    if mono:
        # Monocromo: la cara se recorta para que el check se lea en un solo color.
        body += f'''
            <path android:fillColor="#00000000" android:strokeColor="#000000" android:strokeWidth="0" android:pathData="{circle(512, 572, 236)}" />'''
    else:
        body += f'''
            <path android:fillColor="{face_fill}" android:pathData="{circle(512, 572, 236)}" />
            <path android:strokeColor="{ticks}" android:strokeWidth="26" android:strokeLineCap="round" android:pathData="M512,366 L512,396 M718,572 L688,572 M512,778 L512,748 M306,572 L336,572" />'''
    check_color = "#000000" if mono else check
    body += f'''
            <path android:fillColor="#00000000" android:strokeColor="{check_color}" android:strokeWidth="64" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M410,566 L486,642 L640,470" />
        </group>
    </group>'''
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por tools/icon/gen_icons.py. No editar a mano. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
{body}
</vector>
'''


def mono():
    # Un solo trazo: silueta blanca del reloj con el check calado no es posible en un
    # vector sin máscaras, así que el monocromo es el reloj con el check encima.
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por tools/icon/gen_icons.py. No editar a mano. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:scaleX="{SCALE}" android:scaleY="{SCALE}" android:translateX="{TX:.2f}" android:translateY="{TY:.2f}">
        <group android:translateY="-34">
            <path android:strokeColor="#000000" android:strokeWidth="56" android:strokeLineCap="round" android:pathData="M372,790 L322,872 M652,790 L702,872 M512,300 L512,264" />
            <path android:fillColor="#000000" android:pathData="{circle(512, 246, 30)}" />
            <group android:translateX="318" android:translateY="318" android:rotation="-42">
                <path android:fillColor="#000000" android:pathData="M-118,36 A118,118 0 0,1 118,36 Z" />
            </group>
            <group android:translateX="706" android:translateY="318" android:rotation="42">
                <path android:fillColor="#000000" android:pathData="M-118,36 A118,118 0 0,1 118,36 Z" />
            </group>
            <path android:strokeColor="#000000" android:strokeWidth="48" android:fillColor="#00000000" android:pathData="{circle(512, 572, 260)}" />
            <path android:fillColor="#00000000" android:strokeColor="#000000" android:strokeWidth="64" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M410,566 L486,642 L640,470" />
        </group>
    </group>
</vector>
'''


def background(light, dark):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por tools/icon/gen_icons.py. No editar a mano. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" android:endY="108">
                <item android:offset="0" android:color="{light}" />
                <item android:offset="1" android:color="{dark}" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="24" android:centerY="11" android:gradientRadius="92">
                <item android:offset="0" android:color="#42FFFFFF" />
                <item android:offset="0.6" android:color="#00FFFFFF" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''


def adaptive(fg, bg, with_mono=True):
    mono_line = '\n    <monochrome android:drawable="@drawable/ic_launcher_mono" />' if with_mono else ""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por tools/icon/gen_icons.py. No editar a mano. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/{bg}" />
    <foreground android:drawable="@drawable/{fg}" />{mono_line}
</adaptive-icon>
'''


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def main():
    for name, (light, dark, face, ticks) in VARIANTS.items():
        suffix = f"_{name}" if name else ""
        write(os.path.join(RES, "drawable", f"ic_launcher_fg{suffix}.xml"), foreground(face, ticks, dark))
        write(os.path.join(RES, "drawable", f"ic_launcher_bg{suffix}.xml"), background(light, dark))
        write(os.path.join(RES, "mipmap-anydpi", f"ic_launcher{suffix}.xml"), adaptive(f"ic_launcher_fg{suffix}", f"ic_launcher_bg{suffix}"))
    write(os.path.join(RES, "mipmap-anydpi", "ic_launcher_round.xml"), adaptive("ic_launcher_fg", "ic_launcher_bg"))
    write(os.path.join(RES, "drawable", "ic_launcher_mono.xml"), mono())
    # Splash de Android 12+: el mismo primer plano.
    write(os.path.join(RES, "drawable", "ic_splash.xml"), foreground(*VARIANTS[""][2:], VARIANTS[""][1]))


if __name__ == "__main__":
    main()
