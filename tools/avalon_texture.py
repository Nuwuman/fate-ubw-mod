# Textura de Avalon (cuña de oro con esmalte azul), según la referencia del usuario
import sys, math, random
from PIL import Image
W, H = 48, 128
img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
px = img.load()
GOLD = (236, 210, 122); GOLD_HI = (250, 236, 170); BLUE = (29, 60, 140); BLUE_HI = (52, 92, 180); INK = (43, 36, 20); LINE = (246, 226, 150)
MOUTH = 7
def half(y):  # media anchura de la cuña en la fila y
    if y < MOUTH: return W / 2
    t = (y - MOUTH) / (H - 1 - MOUTH)
    return (W / 2 - 2) * (1 - t) + 1.0
def dist_line(x, y, x0, y0, x1, y1):
    dx, dy = x1 - x0, y1 - y0
    return abs(dy * (x - x0) - dx * (y - y0)) / math.hypot(dx, dy)
random.seed(7)
for y in range(H):
    h = half(y)
    for x in range(W):
        cx = x + 0.5 - W / 2
        if abs(cx) > h: continue
        shade = 1.0 - 0.18 * (abs(cx) / max(h, 1))
        c = tuple(int(v * shade) for v in GOLD)
        if y < MOUTH:  # boca: oro claro con ribete azul
            c = BLUE if y in (0, MOUTH - 1) or x in (0, W - 1) else GOLD_HI
        else:
            edge = h - abs(cx)
            if edge < 4.5: c = BLUE_HI if edge > 3.2 else BLUE
            elif edge < 5.6: c = LINE
            # X cerca de la boca: bandas de esquina a esquina
            for (x0, x1) in ((3, W - 3), (W - 3, 3)):
                d = dist_line(x + 0.5, y + 0.5, x0, MOUTH + 1, x1, MOUTH + 26)
                if MOUTH < y < MOUTH + 28 and abs(cx) < h - 4:
                    if d < 2.2: c = BLUE
                    elif d < 3.0: c = LINE
        px[x, y] = c + (255,)
# Letras de hada en vertical por el centro
y = 42
while y < 74:
    gw = random.choice((3, 4, 5)); gh = random.choice((3, 4))
    x0 = W // 2 - gw // 2
    for _ in range(6):
        gx = x0 + random.randrange(gw); gy = y + random.randrange(gh)
        px[gx, gy] = INK + (255,)
    for gx in range(x0, x0 + gw):
        if random.random() < 0.6: px[gx, y + gh // 2] = INK + (255,)
    y += gh + 1
img.save(sys.argv[1])
