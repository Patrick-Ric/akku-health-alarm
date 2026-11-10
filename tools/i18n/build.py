"""Builds res/values-<lang>/strings.xml from tools/i18n/<lang>.txt.

Format of <lang>.txt: one line per source text, "number|translation" (numbers from source.json).
Plurals: the "one" line uses the number of the first item, "other" the second; extra forms
are written as "94:few|...", "94:many|...", "94:two|...", "94:zero|...".
Checks that every placeholder of the English text appears in the translation.
"""
import json
import re
import sys
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
SRC = json.loads((ROOT / "tools/i18n/source.json").read_text())
PH = re.compile(r"%(\d\$)?[ds%]")
NBSP = " "


def android_escape(text: str) -> str:
    text = escape(text)
    text = re.sub(r"(?<!\\)'", r"\\'", text)
    text = re.sub(r'(?<!\\)"', r'\\"', text)
    # Keep number and unit together.
    text = re.sub(r"(%\d?\$?[ds]) (%%|°C)", lambda m: m.group(1) + NBSP + m.group(2), text)
    return text


def build(lang: str, folder: str) -> list[str]:
    lines = (ROOT / f"tools/i18n/{lang}.txt").read_text().splitlines()
    tr, extra = {}, {}
    for line in lines:
        if not line.strip() or line.startswith("#"):
            continue
        key, _, text = line.partition("|")
        if ":" in key:
            n, q = key.split(":")
            extra.setdefault(int(n), {})[q] = text.strip()
        else:
            tr[int(key)] = text.strip()
    problems = []
    out = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    i = 0
    while i < len(SRC):
        kind, name, q, en = SRC[i]
        if kind == "s":
            t = tr.get(i)
            if t is None:
                problems.append(f"{lang}: {i} fehlt")
            else:
                if sorted(PH.findall(en)) != sorted(PH.findall(t)):
                    problems.append(f"{lang}: {i} Platzhalter {PH.findall(en)} != {PH.findall(t)}")
                # A literal "%" in a text without placeholders must not be read as one.
                plain = "%" in en and not PH.findall(en)
                attr = ' formatted="false"' if plain else ""
                out.append(f'    <string name="{name}"{attr}>{android_escape(t)}</string>')
            i += 1
        elif kind == "p":
            one, other = tr.get(i), tr.get(i + 1)
            forms = {"one": one, "other": other, **extra.get(i, {})}
            # Romance languages also have "many" for very large numbers; same text as "other".
            if folder.split("-")[0] in ("es", "fr", "it", "pt") and "many" not in forms:
                forms["many"] = other
            out.append(f'    <plurals name="{name}">')
            for qty in ("zero", "one", "two", "few", "many", "other"):
                if forms.get(qty):
                    if sorted(set(PH.findall(SRC[i + 1][3]))) != sorted(set(PH.findall(forms[qty]))) and qty != "one":
                        problems.append(f"{lang}: {i} {qty} Platzhalter")
                    out.append(f'        <item quantity="{qty}">{android_escape(forms[qty])}</item>')
            out.append("    </plurals>")
            if other is None:
                problems.append(f"{lang}: {i + 1} fehlt")
            i += 2
        else:
            out.append(f'    <string-array name="{name}">')
            while i < len(SRC) and SRC[i][0] == "a" and SRC[i][1] == name:
                t = tr.get(i)
                if t is None:
                    problems.append(f"{lang}: {i} fehlt")
                out.append(f"        <item>{android_escape(t or SRC[i][3])}</item>")
                i += 1
            out.append("    </string-array>")
    out.append("</resources>")
    d = ROOT / f"app/src/main/res/values-{folder}"
    d.mkdir(exist_ok=True)
    (d / "strings.xml").write_text("\n".join(out) + "\n")
    return problems


if __name__ == "__main__":
    folders = {"zh-CN": "zh-rCN", "zh-TW": "zh-rTW", "pt-BR": "pt-rBR"}
    all_problems = []
    for lang in sys.argv[1:]:
        all_problems += build(lang, folders.get(lang, lang))
    print("\n".join(all_problems) or f"ok: {', '.join(sys.argv[1:])}")
