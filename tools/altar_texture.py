# Texturas del altar de ritual: piedra oscura con el círculo rojo, la estrella de cinco puntas y el centro dorado.
# Uso: python tools/altar_texture.py   (escribe textures/block/summoning_altar_top.png y _side.png)
import math, os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'fate_ubw', 'textures', 'block')
N = 64
img = Image.new('RGBA', (N, N), (28, 24, 34, 255))
d = ImageDraw.Draw(img)
for y in range(N):                                   # veta sutil de la piedra
    for x in range(N):
        if (x * 7 + y * 13) % 11 == 0:
            d.point((x, y), fill=(36, 31, 44, 255))
c = N / 2
RED, GOLD = (200, 28, 40, 255), (235, 190, 70, 255)
d.ellipse([c - 28, c - 28, c + 28, c + 28], outline=RED, width=2)
d.ellipse([c - 24, c - 24, c + 24, c + 24], outline=(120, 16, 28, 255), width=1)
pts = [(c + 22 * math.sin(math.radians(a)), c - 22 * math.cos(math.radians(a))) for a in range(0, 360, 72)]
for i in range(5):
    d.line([pts[i], pts[(i + 2) % 5]], fill=RED, width=1)
for a in range(0, 360, 30):                          # runas en el borde
    x, y = c + 30 * math.sin(math.radians(a)), c - 30 * math.cos(math.radians(a))
    d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=GOLD)
d.ellipse([c - 3, c - 3, c + 3, c + 3], fill=GOLD)
img.save(os.path.join(OUT, 'summoning_altar_top.png'))
side = Image.new('RGBA', (16, 16), (28, 24, 34, 255))
ImageDraw.Draw(side).rectangle([0, 15, 15, 15], fill=(200, 28, 40, 255))
side.save(os.path.join(OUT, 'summoning_altar_side.png'))
