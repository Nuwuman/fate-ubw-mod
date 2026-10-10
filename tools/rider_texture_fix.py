# Venda de Rider en el mismo morado que las bandas, y hebillas de plata con relieve.
# Uso: python rider_fix.py <textura entrada> <textura salida> <rider_armor.geo.json>
import sys, json, colorsys
from PIL import Image

src, dst, geo = sys.argv[1], sys.argv[2], sys.argv[3]
img = Image.open(src).convert('RGBA')
px = img.load()
W, H = img.size

# Morado de referencia: las bandas de brazos y piernas (uv 36,56 de 10x10)
ref = [px[x, y] for x in range(36, 46) for y in range(56, 66)]
rh, rl, rs = colorsys.rgb_to_hls(*[sum(c[i] for c in ref) / len(ref) / 255 for i in range(3)])
pinks = 0
for y in range(H):
    for x in range(W):
        r, g, b, a = px[x, y]
        if a == 0:
            continue
        h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        if s > 0.4 and (h > 0.88 or h < 0.02):  # el magenta de la venda: mismo tono y saturación que las bandas, conservando las luces
            nr, ng, nb = colorsys.hls_to_rgb(rh, min(1.0, l * rl / 0.39), rs)
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
            pinks += 1

HI, MID, SH = (238, 240, 247, 255), (176, 180, 192, 255), (112, 116, 130, 255)
HOLE = (52, 20, 70, 255)


def buckle(x0, y0, w, h):
    """Una cara de hebilla: marco de plata con brillo arriba a la izquierda, sombra abajo a la derecha, y si cabe,
    el hueco con el pasador en medio."""
    for dy in range(h):
        for dx in range(w):
            c = MID
            if dy == 0 or dx == 0:
                c = HI
            elif dy == h - 1 or dx == w - 1:
                c = SH
            if w >= 3 and h >= 2 and dy == h - 1 and 0 < dx < w - 1:
                c = HOLE if dx != w // 2 else MID
            if w >= 3 and h >= 3 and 0 < dy < h - 1 and 0 < dx < w - 1:
                c = HOLE if dx != w // 2 else MID
            px[x0 + dx, y0 + dy] = c


silver = lambda c: c[3] > 0 and min(c[:3]) > 130 and max(c[:3]) - min(c[:3]) < 30
# Cada cara del modelo pintada sobre todo de blanco/gris es una cara de hebilla
faces = []
for bone in json.load(open(geo, encoding='utf-8'))['minecraft:geometry'][0]['bones']:
    for cube in bone.get('cubes', []):
        for f in cube['uv'].values():
            (u, v), (w, h) = f['uv'], f['uv_size']
            cells = [px[x, y] for x in range(u, u + w) for y in range(v, v + h)]
            if cells and sum(map(silver, cells)) * 2 >= len(cells):
                faces.append((u, v, w, h))
# Manchas sueltas de plata dentro de caras moradas (las hebillas pintadas en las bandas): se repintan igual
covered = {(x, y) for (u, v, w, h) in faces for x in range(u, u + w) for y in range(v, v + h)}
seen = set()
for y in range(H):
    for x in range(W):
        if (x, y) in seen or (x, y) in covered or not silver(px[x, y]):
            continue
        stack, cells = [(x, y)], []
        seen.add((x, y))
        while stack:
            cx, cy = stack.pop()
            cells.append((cx, cy))
            for n in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                if 0 <= n[0] < W and 0 <= n[1] < H and n not in seen and n not in covered and silver(px[n]):
                    seen.add(n)
                    stack.append(n)
        xs, ys = [c[0] for c in cells], [c[1] for c in cells]
        faces.append((min(xs), min(ys), max(xs) - min(xs) + 1, max(ys) - min(ys) + 1))
for f in faces:
    buckle(*f)
img.save(dst)
print('rosa recoloreado:', pinks, 'px; caras de hebilla:', faces)
