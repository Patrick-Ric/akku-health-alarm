"""Writes tools/i18n/source.json and tools/i18n/<lang>.txt from the strings.xml files.

Run it after texts were changed directly in res/values*/strings.xml, so that build.py
(the other direction) does not bring back old texts. Usage: python3 tools/i18n/export.py
"""
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
FOLDERS = {"pt-rBR": "pt-BR", "zh-rCN": "zh-CN", "zh-rTW": "zh-TW"}
NBSP = " "


def text(el) -> str:
    t = "".join(el.itertext())
    t = t.replace("\\'", "'").replace('\\"', '"')
    # build.py puts the no-break space between number and unit back itself.
    return re.sub(r"(%\d?\$?[ds])" + NBSP + r"(%%|°C)", r"\1 \2", t)


def entries(path: Path) -> list:
    """[kind, name, quantity, text] in file order, like source.json."""
    out = []
    for el in ET.parse(path).getroot():
        name = el.get("name")
        if el.get("translatable") == "false":
            continue
        if el.tag == "string":
            out.append(["s", name, None, text(el)])
        elif el.tag == "plurals":
            forms = {i.get("quantity"): text(i) for i in el}
            out.append(["p", name, "one", forms.get("one", forms.get("other"))])
            out.append(["p", name, "other", forms["other"]])
            out.append(["px", name, forms])
        elif el.tag == "string-array":
            out += [["a", name, None, text(i)] for i in el]
    return out


def main():
    source = [e for e in entries(RES / "values/strings.xml") if e[0] != "px"]
    (ROOT / "tools/i18n/source.json").write_text(json.dumps(source, ensure_ascii=False, indent=0) + "\n")
    index = {}
    for i, (kind, name, q, _) in enumerate(source):
        index.setdefault((kind, name, q), []).append(i)
    for d in sorted(RES.glob("values-*/strings.xml")):
        folder = d.parent.name.removeprefix("values-")
        if folder.startswith(("night", "v")) and not folder.startswith("vi"):
            continue
        lang = FOLDERS.get(folder, folder)
        lines, arrays = [], {}
        for e in entries(d):
            if e[0] == "px":
                first = index[("p", e[1], "one")][0]
                for q, t in e[2].items():
                    if q not in ("one", "other"):
                        lines.append(f"{first}:{q}|{t}")
                continue
            if e[0] == "a":
                n = arrays.get(e[1], 0)
                arrays[e[1]] = n + 1
                i = index[("a", e[1], None)][n]
            else:
                i = index[(e[0], e[1], e[2])][0]
            lines.append(f"{i}|{e[3]}")
        (ROOT / f"tools/i18n/{lang}.txt").write_text("\n".join(lines) + "\n")
        print(lang, len(lines))


if __name__ == "__main__":
    main()
