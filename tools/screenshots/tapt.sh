#!/bin/bash
# tapt.sh "texto exacto" [n]  — toca el n-ésimo nodo (1 por defecto) cuyo text o content-desc coincide.
ADB=$HOME/Library/Android/sdk/platform-tools/adb
$ADB exec-out uiautomator dump /sdcard/u.xml >/dev/null 2>&1
xml=$($ADB shell cat /sdcard/u.xml)
n=${2:-1}
b=$(echo "$xml" | python3 -c "
import sys,re
x=sys.stdin.read(); t=sys.argv[1]; n=int(sys.argv[2])
hits=[m for m in re.finditer(r'<node [^>]*>', x) if ('text=\"'+t+'\"') in m.group(0) or ('content-desc=\"'+t+'\"') in m.group(0)]
if len(hits)<n: sys.exit(1)
b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', hits[n-1].group(0)).groups()
print((int(b[0])+int(b[2]))//2, (int(b[1])+int(b[3]))//2)
" "$1" "$n") || { echo "NO ENCONTRADO: $1"; exit 1; }
$ADB shell input tap $b
