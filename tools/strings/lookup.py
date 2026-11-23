#!/usr/bin/env python3
"""Busca cómo tradujo la app de iOS un texto: lookup.py "Posponer" [más palabras].
Imprime: clave · es · en · pt-BR (de ios_reference.tsv, exportado de Localizable.xcstrings)."""
import os
import sys

ref = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ios_reference.tsv")
needle = " ".join(sys.argv[1:]).lower()
with open(ref, encoding="utf-8") as fh:
    for line in fh:
        if needle in line.lower():
            print(" · ".join(line.rstrip("\n").split("\t")))
