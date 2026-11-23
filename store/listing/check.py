#!/usr/bin/env python3
"""Valida los textos de la ficha de Google Play y arma el bloque de notas de versión.

Uso, desde la raíz del repo:  python3 store/listing/check.py

- Comprueba los límites de Play Console (título 30, descripción breve 80,
  descripción completa 4000, notas de la versión 500 caracteres).
- Avisa si se coló algo que la versión de Android no tiene (iCloud, Siri, Isla Dinámica…):
  la ficha no puede prometer funciones de iOS.
- Escribe release_notes_play_console.txt con las etiquetas <es-419>…</es-419> que
  acepta el cuadro «Notas de la versión» al crear un lanzamiento.

Sale con código 1 si algo no cumple.
"""
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
LOCALES = ["es-419", "en-US", "pt-BR", "de-DE"]
LIMITS = {
    "title.txt": 30,
    "short_description.txt": 80,
    "full_description.txt": 4000,
    "release_notes.txt": 500,
}
# Lo que la versión de Android no tiene. Compartir y personas de confianza SÍ están
# (Firebase, también con iPhone), y «iPhone» vale cuando dice «iPhone o Android».
IOS_ONLY = re.compile(
    r"\b(?:iPad|iOS|iCloud|Siri|Apple|App Store|AlarmKit|Dynamic Island|Isla Dinámica|"
    r"Live Activit\w*|Atividade ao Vivo|Live-Aktivität)\b"
    r"|\biPhone\b(?!\s+(?:o|or|ou|oder)\s+(?:no\s+)?Android)",
    re.IGNORECASE,
)

failed = False
print(f"{'archivo':<34} {'caract.':>8} {'límite':>7}")
for loc in LOCALES:
    for name, limit in LIMITS.items():
        path = HERE / loc / name
        if not path.exists():
            print(f"{loc}/{name:<27} FALTA")
            failed = True
            continue
        text = path.read_text(encoding="utf-8")
        n = len(text)
        flag = "" if n <= limit else "  ← EXCEDE"
        if text != text.strip():
            flag += "  ← espacios o saltos al inicio/fin"
        bad = sorted({m.group(0) for m in IOS_ONLY.finditer(text)})
        if bad:
            flag += f"  ← solo iOS: {', '.join(bad)}"
        if flag:
            failed = True
        print(f"{loc + '/' + name:<34} {n:>8} {limit:>7}{flag}")

block = "\n".join(
    f"<{loc}>\n{(HERE / loc / 'release_notes.txt').read_text(encoding='utf-8').strip()}\n</{loc}>"
    for loc in LOCALES
)
(HERE / "release_notes_play_console.txt").write_text(block + "\n", encoding="utf-8")
print("\n✓ release_notes_play_console.txt actualizado")

sys.exit(1 if failed else 0)
