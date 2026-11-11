#!/usr/bin/env python3
"""Собирает иконку iOS из Android-вектора; нужен rsvg-convert (ADR-0049)."""

from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from xml.sax.saxutils import quoteattr

ROOT = Path(__file__).resolve().parent.parent
ANDROID = "http://schemas.android.com/apk/res/android"
VECTOR = ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml"
COLORS = ROOT / "app/src/main/res/values/colors.xml"
OUTPUT = ROOT / "ios/BabyLog/Assets.xcassets/AppIcon.appiconset/AppIcon.png"


def attribute(node, name):
    return node.get(f"{{{ANDROID}}}{name}")


def main():
    background = re.search(
        r'<color name="ic_launcher_background">(#[0-9A-Fa-f]{6})</color>',
        COLORS.read_text(),
    ).group(1)
    vector = ET.parse(VECTOR).getroot()
    viewport = attribute(vector, "viewportWidth")
    paths = []
    for node in vector.iter():
        if not node.tag.endswith("path"):
            continue
        attrs = [f"d={quoteattr(attribute(node, 'pathData'))}"]
        fill = attribute(node, "fillColor")
        stroke = attribute(node, "strokeColor")
        attrs.append(f"fill={quoteattr(fill or 'none')}")
        if attribute(node, "fillType") == "evenOdd":
            attrs.append('fill-rule="evenodd"')
        if stroke:
            attrs.extend([
                f"stroke={quoteattr(stroke)}",
                f"stroke-width={quoteattr(attribute(node, 'strokeWidth'))}",
                f"stroke-linecap={quoteattr(attribute(node, 'strokeLineCap') or 'butt')}",
            ])
        paths.append("<path " + " ".join(attrs) + "/>")

    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" '
        f'viewBox="0 0 {viewport} {attribute(vector, "viewportHeight")}">'
        f'<rect width="100%" height="100%" fill="{background}"/>'
        + "".join(paths)
        + "</svg>"
    )
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".svg") as source:
        source.write(svg.encode())
        source.flush()
        subprocess.run(
            ["rsvg-convert", "-w", "1024", "-h", "1024", "-o", str(OUTPUT), source.name],
            check=True,
        )
    print(OUTPUT)


if __name__ == "__main__":
    main()
