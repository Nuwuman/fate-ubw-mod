# Textura de las cadenas de Enkidu (mismo reparto que la cadena vanilla: u 0-3 eslabón de frente, u 3-6 eslabón de canto)
import sys
from PIL import Image
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
px = img.load()
LIGHT, GOLD, DARK, SILVER = (255, 238, 170, 255), (226, 182, 72, 255), (150, 108, 34, 255), (236, 232, 220, 255)
for start in (0, 8):  # dos eslabones de frente por textura
    for y in range(start, start + 8):
        top, bottom = y == start, y == start + 7
        for x in range(3):
            if (top or bottom) and x != 1: continue
            if not (top or bottom) and x == 1: continue  # hueco del eslabón
            px[x, y] = LIGHT if x == 0 else (DARK if x == 2 else GOLD)
    px[1, start] = SILVER
for y in range(4, 12):  # eslabón de canto entre los dos
    px[3, y], px[4, y], px[5, y] = GOLD, LIGHT, DARK
for y in (0, 1, 2, 13, 14, 15):
    px[3, y], px[4, y], px[5, y] = GOLD, LIGHT, DARK
img.save(sys.argv[1])
