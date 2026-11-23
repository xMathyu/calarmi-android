#!/usr/bin/env python3
"""Genera los strings de Android a partir de tools/strings/*.tsv.

Cada TSV (salvo ios_reference.tsv) es un módulo: `core.tsv` →
`values/strings_core.xml`, `values-es/strings_core.xml`,
`values-pt-rBR/strings_core.xml`. Columnas: name, en, es, pt (con cabecera).

- Formatos posicionales al estilo Android: %1$s, %2$d.
- Un `%` sin formato se escribe tal cual (el script marca formatted="false").
- Plurales: el nombre empieza con `plural:` y cada celda es
  `one=… || other=…` (pt y es usan las mismas cantidades).
- `\\n` en la celda es un salto de línea.

Uso: python3 tools/strings/gen_strings.py   (desde la raíz del proyecto)
"""
import csv
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "tools", "strings")
RES = os.path.join(ROOT, "app", "src", "main", "res")
LOCALES = {"en": "values", "es": "values-es", "pt": "values-pt-rBR"}
POSITIONAL = re.compile(r"%\d+\$[sdf]")


def escape(text: str) -> str:
    text = text.replace("\\n", "\n")
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    text = text.replace("\n", "\\n")
    if text.startswith("@") or text.startswith("?"):
        text = "\\" + text
    return text


def attrs(text: str) -> str:
    return ' formatted="false"' if "%" in text and not POSITIONAL.search(text) else ""


def main() -> int:
    errors = 0
    for file in sorted(os.listdir(SRC)):
        if not file.endswith(".tsv") or file == "ios_reference.tsv":
            continue
        module = file[:-4]
        rows = []
        with open(os.path.join(SRC, file), encoding="utf-8") as fh:
            reader = csv.DictReader(fh, delimiter="\t", quoting=csv.QUOTE_NONE)
            for line, row in enumerate(reader, start=2):
                if not row.get("name") or row["name"].startswith("#"):
                    continue
                for loc in LOCALES:
                    if not (row.get(loc) or "").strip():
                        print(f"{file}:{line}: falta '{loc}' en {row['name']}", file=sys.stderr)
                        errors += 1
                rows.append(row)
        names = [r["name"] for r in rows]
        dupes = {n for n in names if names.count(n) > 1}
        if dupes:
            print(f"{file}: nombres repetidos: {sorted(dupes)}", file=sys.stderr)
            errors += 1
        for loc, folder in LOCALES.items():
            out = ['<?xml version="1.0" encoding="utf-8"?>',
                   f"<!-- Generado por tools/strings/gen_strings.py desde {file}. No editar a mano. -->",
                   "<resources>"]
            for row in rows:
                name, value = row["name"], (row.get(loc) or row["en"]).strip()
                if name.startswith("plural:"):
                    out.append(f'    <plurals name="{name[7:]}">')
                    for part in value.split("||"):
                        qty, _, text = part.strip().partition("=")
                        out.append(f'        <item quantity="{qty.strip()}">{escape(text.strip())}</item>')
                    out.append("    </plurals>")
                else:
                    out.append(f'    <string name="{name}"{attrs(value)}>{escape(value)}</string>')
            out.append("</resources>")
            os.makedirs(os.path.join(RES, folder), exist_ok=True)
            with open(os.path.join(RES, folder, f"strings_{module}.xml"), "w", encoding="utf-8") as fh:
                fh.write("\n".join(out) + "\n")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
