#!/usr/bin/env python3
"""Sube un AAB a una pista de Google Play con la API de publicación (v3).

Sin dependencias: el token de acceso llega en ACCESS_TOKEN (en GitHub Actions lo
da google-github-actions/auth con Workload Identity Federation). Una edición de
Play es una transacción: se abre, se sube el bundle, se asigna a la pista y se
confirma; si algo falla antes del commit, Play la descarta y no cambia nada.

    ACCESS_TOKEN=… publicar_play.py --package com.mathyusolutions.calarmi \\
        --aab app-release.aab --track alpha --status completed --notes store/listing

Las notas de la versión salen de store/listing/<locale>/release_notes.txt. Las tres
fichas en español comparten las de es-419.
"""
import argparse
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
# idioma de Play → carpeta de store/listing
NOTE_LOCALES = {
    "en-US": "en-US",
    "es-419": "es-419",
    "es-ES": "es-419",
    "es-US": "es-419",
    "pt-BR": "pt-BR",
    "de-DE": "de-DE",
}


def call(method, url, token, body=None, data=None, content_type="application/json", timeout=120):
    if body is not None:
        data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": f"Bearer {token}",
        "Content-Type": content_type,
    })
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")
        sys.exit(f"{method} {url.split('/applications/')[-1]} → {e.code}\n{detail}")


def release_notes(folder: pathlib.Path):
    notes = []
    for language, sub in NOTE_LOCALES.items():
        f = folder / sub / "release_notes.txt"
        if f.exists():
            text = f.read_text().strip()
            if text:
                notes.append({"language": language, "text": text[:500]})
    return notes


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--package", required=True)
    p.add_argument("--aab", required=True)
    p.add_argument("--track", default="alpha")
    p.add_argument("--status", default="completed", choices=["completed", "draft", "halted", "inProgress"])
    p.add_argument("--notes", default="store/listing")
    p.add_argument("--name", default=None, help="nombre del lanzamiento (por defecto, versionCode (versionName))")
    args = p.parse_args()

    token = os.environ.get("ACCESS_TOKEN")
    if not token:
        sys.exit("Falta ACCESS_TOKEN.")
    base = f"{API}/{args.package}"

    edit = call("POST", f"{base}/edits", token, body={})["id"]
    print(f"edición {edit}")

    aab = pathlib.Path(args.aab).read_bytes()
    bundle = call("POST", f"{UPLOAD}/{args.package}/edits/{edit}/bundles?uploadType=media", token,
                  data=aab, content_type="application/octet-stream", timeout=600)
    version_code = str(bundle["versionCode"])
    print(f"bundle subido: versionCode {version_code} ({len(aab) / 1e6:.1f} MB)")

    release = {
        "name": args.name or version_code,
        "versionCodes": [version_code],
        "status": args.status,
    }
    notes = release_notes(pathlib.Path(args.notes))
    if notes:
        release["releaseNotes"] = notes
    call("PUT", f"{base}/edits/{edit}/tracks/{args.track}", token,
         body={"track": args.track, "releases": [release]})
    print(f"pista {args.track}: {version_code} ({args.status}), notas en {len(notes)} idiomas")

    call("POST", f"{base}/edits/{edit}:commit", token, body={})
    print("edición confirmada: Play la envía a revisión si hace falta")


if __name__ == "__main__":
    main()
