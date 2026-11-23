#!/usr/bin/env python3
import argparse
import filecmp
import json
import shutil
import struct
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
INFO = {"info": {"author": "xcode", "version": 1}}


def color(value):
    value = value.lstrip("#")
    if len(value) == 8:
        alpha = int(value[:2], 16) / 255
        value = value[2:]
    else:
        alpha = 1
    return "#" + value.upper(), alpha


def transform(node):
    def attr(name, default=0):
        return float(node.get(ANDROID + name, default))

    tx, ty = attr("translateX"), attr("translateY")
    sx, sy = attr("scaleX", 1), attr("scaleY", 1)
    rotation = attr("rotation")
    px, py = attr("pivotX"), attr("pivotY")
    parts = []
    if tx or ty:
        parts.append(f"translate({tx:g} {ty:g})")
    if rotation:
        parts.append(f"translate({px:g} {py:g}) rotate({rotation:g}) translate({-px:g} {-py:g})")
    if sx != 1 or sy != 1:
        parts.append(f"translate({px:g} {py:g}) scale({sx:g} {sy:g}) translate({-px:g} {-py:g})")
    return " ".join(parts)


def convert_node(node):
    tag = node.tag.rsplit("}", 1)[-1]
    if tag == "group":
        result = ["<g"]
        group_transform = transform(node)
        if group_transform:
            result.append(f' transform="{group_transform}"')
        result.append(">")
        result.extend(convert_node(child) for child in node)
        result.append("</g>")
        return "".join(result)
    if tag != "path":
        raise ValueError(f"Неподдерживаемый элемент Android Vector: {tag}")
    attrs = [f'd="{node.get(ANDROID + "pathData", "")}"']
    fill = node.get(ANDROID + "fillColor")
    stroke = node.get(ANDROID + "strokeColor")
    if fill:
        value, alpha = color(fill)
        attrs += [f'fill="{value}"']
        if alpha != 1:
            attrs.append(f'fill-opacity="{alpha:.6f}"')
    else:
        attrs.append('fill="none"')
    if stroke:
        value, alpha = color(stroke)
        attrs += [f'stroke="{value}"', f'stroke-width="{node.get(ANDROID + "strokeWidth", "1")}"']
        if alpha != 1:
            attrs.append(f'stroke-opacity="{alpha:.6f}"')
    mapping = {
        "strokeLineCap": "stroke-linecap",
        "strokeLineJoin": "stroke-linejoin",
        "strokeMiterLimit": "stroke-miterlimit",
    }
    for source, target in mapping.items():
        value = node.get(ANDROID + source)
        if value:
            attrs.append(f'{target}="{value}"')
    if node.get(ANDROID + "fillType") == "evenOdd":
        attrs += ['fill-rule="evenodd"', 'clip-rule="evenodd"']
    for source, target in (("fillAlpha", "fill-opacity"), ("strokeAlpha", "stroke-opacity")):
        value = node.get(ANDROID + source)
        if value:
            attrs.append(f'{target}="{value}"')
    return "<path " + " ".join(attrs) + "/>"


def convert_vector(source):
    root = ET.parse(source).getroot()
    width = root.get(ANDROID + "viewportWidth")
    height = root.get(ANDROID + "viewportHeight")
    body = "".join(convert_node(node) for node in root)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">{body}</svg>\n'
    )


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def generate_svg_icons(source, destination):
    for xml in sorted(source.glob("ic_*.xml")):
        name = xml.stem
        svg = convert_vector(xml)
        directory = destination / f"{name}.imageset"
        directory.mkdir(parents=True, exist_ok=True)
        (directory / f"{name}.svg").write_text(svg)
        write_json(
            directory / "Contents.json",
            {
                **INFO,
                "images": [{"filename": f"{name}.svg", "idiom": "universal"}],
                "properties": {
                    "preserves-vector-representation": True,
                    "template-rendering-intent": "template",
                },
            },
        )


def res_colors(path):
    root = ET.parse(path).getroot()
    result = {}
    for node in root.findall("color"):
        value, _ = color(node.text.strip())
        result[node.get("name")] = value
    return result


def components(value):
    channels = [int(value[i : i + 2], 16) / 255 for i in (1, 3, 5)]
    return {"red": f"{channels[0]:.6f}", "green": f"{channels[1]:.6f}", "blue": f"{channels[2]:.6f}", "alpha": "1.000"}


def generate_launch_colors(root, destination):
    light = res_colors(root / "app/src/main/res/values/colors.xml")["window_background"]
    dark = res_colors(root / "app/src/main/res/values-night/colors.xml")["window_background"]
    write_json(
        destination / "LaunchBackground.colorset/Contents.json",
        {
            **INFO,
            "colors": [
                {"color": {"color-space": "srgb", "components": components(light)}, "idiom": "universal"},
                {
                    "appearances": [{"appearance": "luminosity", "value": "dark"}],
                    "color": {"color-space": "srgb", "components": components(dark)},
                    "idiom": "universal",
                },
            ],
        },
    )


def png_chunk(kind, data):
    crc = zlib.crc32(kind + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", crc)


def remove_png_alpha(path):
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("Некорректный PNG иконки")
    offset = 8
    header = None
    compressed = []
    ancillary = []
    while offset < len(data):
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        kind = data[offset + 4 : offset + 8]
        body = data[offset + 8 : offset + 8 + length]
        offset += length + 12
        if kind == b"IHDR":
            header = body
        elif kind == b"IDAT":
            compressed.append(body)
        elif kind not in (b"IEND", b"tRNS"):
            ancillary.append((kind, body))
    width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", header)
    if depth != 8 or color != 6 or compression != 0 or filtering != 0 or interlace != 0:
        raise ValueError("Иконка должна быть непересекающимся RGBA PNG с 8 битами на канал")
    raw = zlib.decompress(b"".join(compressed))
    stride = width * 4
    previous = bytearray(stride)
    rows = []
    for row in range(height):
        start = row * (stride + 1)
        mode = raw[start]
        current = bytearray(raw[start + 1 : start + stride + 1])
        for index in range(stride):
            left = current[index - 4] if index >= 4 else 0
            up = previous[index]
            upper_left = previous[index - 4] if index >= 4 else 0
            if mode == 1:
                predictor = left
            elif mode == 2:
                predictor = up
            elif mode == 3:
                predictor = (left + up) // 2
            elif mode == 4:
                estimate = left + up - upper_left
                left_distance = abs(estimate - left)
                up_distance = abs(estimate - up)
                corner_distance = abs(estimate - upper_left)
                if left_distance <= up_distance and left_distance <= corner_distance:
                    predictor = left
                elif up_distance <= corner_distance:
                    predictor = up
                else:
                    predictor = upper_left
            elif mode == 0:
                predictor = 0
            else:
                raise ValueError("Неизвестный PNG-фильтр")
            current[index] = (current[index] + predictor) & 0xFF
        rgb = bytearray(width * 3)
        rgb[0::3] = current[0::4]
        rgb[1::3] = current[1::4]
        rgb[2::3] = current[2::4]
        rows.append(b"\x00" + bytes(rgb))
        previous = current
    header = bytearray(header)
    header[9] = 2
    output = [b"\x89PNG\r\n\x1a\n", png_chunk(b"IHDR", bytes(header))]
    output.extend(png_chunk(kind, body) for kind, body in ancillary)
    output.append(png_chunk(b"IDAT", zlib.compress(b"".join(rows), 9)))
    output.append(png_chunk(b"IEND", b""))
    path.write_bytes(b"".join(output))


def generate_app_icon(root, destination, work):
    foreground = convert_vector(root / "app/src/main/res/drawable/ic_launcher_foreground.xml")
    background = res_colors(root / "app/src/main/res/values/colors.xml")["ic_launcher_background"]
    opening, body = foreground.split(">", 1)
    opening = opening.replace('width="108" height="108"', 'width="1024" height="1024"', 1)
    body = body.rsplit("</svg>", 1)[0]
    svg = opening + f'><rect width="108" height="108" fill="{background}"/>' + body + "</svg>\n"
    source = work / "AppIcon.svg"
    source.write_text(svg)
    subprocess.run(
        ["qlmanage", "-t", "-s", "1024", "-o", str(work), str(source)],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    rendered = work / "AppIcon.svg.png"
    if not rendered.exists():
        raise RuntimeError("qlmanage не создал AppIcon.png")
    target = destination / "AppIcon.appiconset/AppIcon.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(rendered), target)
    subprocess.run(["sips", "-z", "1024", "1024", str(target)], check=True, stdout=subprocess.DEVNULL)
    remove_png_alpha(target)
    write_json(
        destination / "AppIcon.appiconset/Contents.json",
        {
            **INFO,
            "images": [{"filename": "AppIcon.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}],
        },
    )


def generate(root, destination):
    destination.mkdir(parents=True, exist_ok=True)
    write_json(destination / "Contents.json", INFO)
    generate_svg_icons(root / "app/src/main/res/drawable", destination)
    generate_launch_colors(root, destination)
    with tempfile.TemporaryDirectory(prefix="babylog-icon-") as temporary:
        generate_app_icon(root, destination, Path(temporary))


def files(directory):
    return {
        path.relative_to(directory)
        for path in directory.rglob("*")
        if path.is_file() and path.name != ".DS_Store"
    }


def check(expected, actual):
    expected_files = files(expected)
    actual_files = files(actual)
    stale = []
    for relative in sorted(expected_files | actual_files):
        if relative not in actual_files or relative not in expected_files:
            stale.append(str(relative))
        elif not filecmp.cmp(expected / relative, actual / relative, shallow=False):
            stale.append(str(relative))
    for path in stale:
        print(path)
    return not stale


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    actual = args.root / "ios/BabyLog/Assets.xcassets"
    if args.check:
        with tempfile.TemporaryDirectory(prefix="babylog-icons-") as temporary:
            expected = Path(temporary) / "Assets.xcassets"
            generate(args.root, expected)
            if not check(expected, actual):
                print("Сгенерированные iOS-иконки устарели: запустите scripts/ios-icons.sh", file=sys.stderr)
                return 1
    else:
        generate(args.root, actual)
    return 0


if __name__ == "__main__":
    sys.exit(main())
