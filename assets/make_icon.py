# -*- coding: utf-8 -*-
"""从 icon.svg 渲染的 PNG 打包多分辨率 .ico（Windows 标准格式）"""
from PIL import Image
import subprocess, os

ASSETS = os.path.dirname(os.path.abspath(__file__))
MAGICK = r"C:\Program Files\ImageMagick-7.1.2-Q16\magick.exe"
SIZES = [256, 128, 64, 48, 32, 16]

pngs = []
for size in SIZES:
    out = os.path.join(ASSETS, f"icon-{size}.png")
    subprocess.run([MAGICK, "-background", "none",
                    os.path.join(ASSETS, "icon.svg"),
                    "-resize", f"{size}x{size}", out], check=True)
    img = Image.open(out).convert("RGBA")
    assert img.size == (size, size), f"{out} size mismatch: {img.size}"
    pngs.append(img)

ico_path = os.path.join(ASSETS, "icon.ico")
pngs[0].save(ico_path, format="ICO",
             append_images=pngs[1:],
             sizes=[(s, s) for s in SIZES])
print("OK ->", ico_path)

check = Image.open(ico_path)
print("ico info:", check.info.get("sizes"))
