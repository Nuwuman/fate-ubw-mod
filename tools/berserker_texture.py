# Texturiza las caras de color plano de la armadura de Berserker: cinturón de cuero (con hebilla delante),
# grilletes de hierro (remaches y algo de óxido) y el paño azul del taparrabos (pliegues y bajo deshilachado, sin dorado).
# Uso: python tools/berserker_texture.py <textura entrada> <textura salida> <berserker_armor.geo.bbmodel>
import sys, json, random
from collections import Counter
from PIL import Image

src, dst, model = sys.argv[1:4]
img = Image.open(src).convert('RGBA')
px = img.load()
rnd = random.Random(12)
clamp = lambda v: max(0, min(255, int(v)))
shade = lambda c, k: (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), 255)


def leather(x0, y0, w, h, front):
    base = (78, 50, 30)
    for y in range(h):
        for x in range(w):
            k = 0.85 + 0.25 * rnd.random()
            if y == 0 or y == h - 1:
                k *= 0.65                                   # cantos oscuros
            c = shade(base, k)
            if h >= 4 and y in (1, h - 2) and x % 2 == 0:
                c = (150, 110, 70, 255)                     # pespunte
            px[x0 + x, y0 + y] = c
    if front and w >= 6:                                    # hebilla de hierro en el centro
        bx = x0 + w // 2 - 2
        for y in range(h):
            for x in range(4):
                edge = x in (0, 3) or y in (0, h - 1)
                px[bx + x, y0 + y] = (205, 205, 212, 255) if edge and (x == 0 or y == 0) else \
                    (120, 122, 130, 255) if edge else (40, 26, 16, 255)
        px[bx + 1, y0 + h // 2] = (170, 170, 178, 255)      # pasador


def iron(x0, y0, w, h):
    for y in range(h):
        for x in range(w):
            k = 0.9 + 0.15 * rnd.random()
            if y == 0:
                k = 1.25                                    # canto de arriba con brillo
            elif y == h - 1:
                k = 0.6
            c = shade((128, 130, 138), k)
            if rnd.random() < 0.05 and 0 < y < h - 1:
                c = (120, 72, 44, 255)                      # óxido
            px[x0 + x, y0 + y] = c
    if h >= 3:
        for x in range(1, w - 1, 3):                        # remaches
            px[x0 + x, y0 + h // 2] = (215, 217, 224, 255)
            if h // 2 + 1 < h - 1:
                px[x0 + x, y0 + h // 2 + 1] = (70, 72, 80, 255)


def cloth(x0, y0, w, h):
    base = (24, 38, 92)
    for y in range(h):
        for x in range(w):
            k = 0.85 + 0.2 * ((x % 3) == 1) - 0.12 * ((x % 3) == 2) + 0.06 * rnd.random()
            if y == 0:
                k *= 0.6                                    # bajo el cinturón, en sombra
            c = shade(base, k)
            if y == h - 1 and x % 2 == 1:
                c = shade(base, 0.5)                        # bajo deshilachado
            px[x0 + x, y0 + y] = c


d = json.load(open(model, encoding='utf-8'))
painted = []
for e in d['elements']:
    for name, f in e['faces'].items():
        u0, v0, u1, v1 = f['uv']
        x0, y0, w, h = min(u0, u1), min(v0, v1), abs(u1 - u0), abs(v1 - v0)
        cells = Counter(px[x, y][:3] for x in range(x0, x0 + w) for y in range(y0, y0 + h))
        if not cells:
            continue
        (r, g, b), n = cells.most_common(1)[0]
        flat = n * 10 >= w * h * 3                          # un color ocupa al menos el 30 %
        if not flat or name in ('up', 'down'):
            continue
        if r > g > b and r < 60:                            # marrón oscuro: cinturón
            leather(x0, y0, w, h, name == 'north')
        elif b > r + 20 and b < 90:                         # azul marino: paño
            cloth(x0, y0, w, h)
        elif abs(r - g) < 6 and abs(g - b) < 6 and r > 110:  # gris claro: grillete
            iron(x0, y0, w, h)
        else:
            continue
        painted.append((e['name'], name, (x0, y0, w, h)))
img.save(dst)
for p in painted:
    print(p)
