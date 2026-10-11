# Pinta la textura del escudo de Mash (Lord Chaldeas) sobre el .bbmodel del usuario, pieza por pieza, siguiendo la
# referencia: cruz azul pizarra con bordes, brazos con los extremos en V, rueda dentada clara con un anillo de puntos y
# la inscripción, disco redondo detrás con el borde claro rasgado, adornos de plata con gema azul y los extremos de
# arriba y abajo abiertos en horquilla. Lo que queda fuera de la forma es transparente (el disco se ve redondo).
# Uso: python tools/mash_shield_texture.py <entrada.bbmodel> <salida.bbmodel>
import sys, json, math, base64, io, random
from PIL import Image

D = 4                       # píxeles por unidad del modelo
SIZE = 512                  # textura de 512x512; en el .bbmodel la UV va de 0 a 16
UV = SIZE / 16.0
T = (0, 0, 0, 0)
NAVY, NAVY_HI, NAVY_LO = (58, 62, 84, 255), (84, 90, 116, 255), (32, 34, 48, 255)
BACK = (44, 47, 64, 255)
LIGHT, LIGHT_HI, LIGHT_LO = (160, 167, 186, 255), (196, 202, 216, 255), (120, 126, 146, 255)
SILVER, SILVER_HI = (206, 210, 222, 255), (238, 241, 248, 255)
GEM, GEM_HI = (38, 56, 168, 255), (110, 140, 240, 255)
rnd = random.Random(3)


def edge(c, d):
    """Bisel: más claro en el borde de la forma, oscuro justo en el contorno."""
    if d < 0.3:
        return NAVY_LO
    if d < 0.8:
        return NAVY_HI
    return c


# ---------- Formas de cada pieza (coordenadas del modelo, en la cara de delante) ----------
def bar(x, y):
    # Palo vertical [4..12]x[-8..30]: se estrecha hacia la cruz; detrás de los brazos, transparente (evita parpadeos)
    if arms(x, y) != T:
        return T                 # lo tapan los brazos (mismo plano): así no parpadea, sin dejar huecos
    if y < 11:
        half = 4.0 - 1.1 * min(1.0, max(0.0, (y + 8) / 15.0))      # ancho abajo, estrecho junto a la cruz
    else:
        half = 2.9 + 1.1 * min(1.0, max(0.0, (y - 21) / 9.0))       # estrecho junto a la cruz, ancho arriba
    dx = abs(x - 8)
    if dx > half:
        return T
    # Horquillas: ranura en el centro de las puntas
    if (y > 27.2 or y < -4.5) and dx < 0.45:
        return T
    c = NAVY
    if y > 28.6 or y < -6.6:
        c = LIGHT_HI if dx > half - 1.2 else LIGHT                 # remate claro de las puntas
    return edge(c, half - dx)


def cap(x, y):
    # Remate de arriba [5..11]x[30..31.5]
    if abs(x - 8) < 0.45:
        return T
    return LIGHT_HI if y > 31 else LIGHT


def arms(x, y):
    # Brazos [-3..19]x[11..21]: más altos en los extremos, con la muesca en V
    dx = abs(x - 8)
    half = 3.6 + 1.4 * min(1.0, max(0.0, (dx - 6) / 4.0))
    dy = abs(y - 16)
    if dy > half:
        return T
    # Muesca en V en el extremo
    if dx > 9.6 and dy < (dx - 9.6) * 1.6 + 0.6 and dy < 1.6:
        return T
    # Remaches claros cerca de los extremos
    if (x - 1.2) ** 2 + (y - 16) ** 2 < 0.5 or (x - 14.8) ** 2 + (y - 16) ** 2 < 0.5:
        return LIGHT_HI if (y - 16) > 0 else LIGHT
    return edge(NAVY, min(half - dy, 11 - dx + 0.9))


def arm_end(x, y):
    # Topes de los brazos [-4..-3] y [19..20]x[12..20]: con la muesca
    if abs(y - 16) < 1.6:
        return T
    return NAVY_LO if abs(y - 16) > 3.6 else NAVY


def disk(x, y):
    # La rueda clara del centro [4..12]x[12..20]: anillo de 12 puntos oscuros, centro claro y la inscripción
    dx, dy = x - 8, y - 16
    r = math.hypot(dx, dy)
    for k in range(12):
        a = k * math.pi / 6
        if math.hypot(dx - 3.0 * math.sin(a), dy - 3.0 * math.cos(a)) < 0.42:
            return NAVY_LO
    if r < 0.8:
        return LIGHT_HI
    if abs(dx) < 0.13 and 0.9 < abs(dy) < 2.4 and int((dy + 3) * 3) % 3 != 0:
        return NAVY_LO                                                # inscripción vertical
    return LIGHT_LO if r > 3.7 else LIGHT


def gear_strip(x, y, axis):
    # Dientes de la rueda alrededor del disco: alternan diente claro y hueco
    t = x if axis == 'x' else y
    return LIGHT_LO if int(math.floor(t * 2)) % 2 == 0 else LIGHT


def circle(x, y):
    # Disco de detrás [0..16]x[9..23]: redondo, oscuro, con el borde claro rasgado
    ex, ey = (x - 8) / 8.0, (y - 16) / 7.0
    r = math.hypot(ex, ey)
    if r > 1.0:
        return T
    ang = math.atan2(ey, ex)
    jag = 0.06 * math.sin(ang * 23) + 0.04 * math.sin(ang * 41 + 1.3)
    if r > 0.93:
        return NAVY_LO
    if r > 0.66 + jag:
        return LIGHT_LO if r > 0.84 else LIGHT
    return BACK


def ornament(x, y, top):
    # Adornos de plata con la gema azul
    cy = 25.5 if top else 6.2
    if math.hypot((x - 8) * 1.6, y - cy) < 0.55:
        return GEM_HI if y > cy else GEM
    return SILVER_HI if (int((x + y) * 3) % 4 == 0) else SILVER


def silver(x, y):
    return SILVER


def tip(x, y):
    return LIGHT_HI if y > -9 else LIGHT


def handle(x, y):
    return NAVY_LO


PIECES = [bar, cap, arms, arm_end, arm_end, disk,
          lambda x, y: gear_strip(x, y, 'x'), lambda x, y: gear_strip(x, y, 'x'),
          lambda x, y: gear_strip(x, y, 'y'), lambda x, y: gear_strip(x, y, 'y'),
          lambda x, y: ornament(x, y, True), lambda x, y: ornament(x, y, False), silver, tip, tip, handle, circle]


def side_colour(piece):
    if piece in (5, 6, 7, 8, 9):
        return LIGHT_LO
    if piece in (10, 11, 12):
        return SILVER
    if piece in (13, 14):
        return LIGHT
    return NAVY_LO


# ---------- Pintar y repartir en la textura ----------
def face_pixels(e, face, painter, piece):
    (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
    if face in ('north', 'south'):
        w, h = x1 - x0, y1 - y0
    elif face in ('east', 'west'):
        w, h = z1 - z0, y1 - y0
    else:
        w, h = x1 - x0, z1 - z0
    pw, ph = max(1, round(w * D)), max(1, round(h * D))
    img = Image.new('RGBA', (pw, ph))
    px = img.load()
    for j in range(ph):
        for i in range(pw):
            fu, fv = (i + 0.5) / pw, (j + 0.5) / ph
            if face == 'north':      # vista desde delante: a la izquierda queda la x alta
                c = painter(x1 - fu * w, y1 - fv * h)
            elif face == 'south':    # la cara de atrás: oscura, con la misma silueta
                c = painter(x0 + fu * w, y1 - fv * h)
                if c[3]:
                    c = BACK if piece == 16 else (NAVY_LO if c in (NAVY, NAVY_HI) else c)
                bx, by = x0 + fu * w, y1 - fv * h
                if piece == 16 and (bar(bx, by) != T or arms(bx, by) != T):
                    c = T            # justo detrás de la cruz (mismo plano): así no parpadea, sin dejar huecos
            else:
                c = side_colour(piece)
            px[i, j] = c
    return img


src, dst = sys.argv[1], sys.argv[2]
d = json.load(open(src, encoding='utf-8'))
elements = d['elements']
# Asignación de pintores por forma, no por posición en la lista (el usuario puede reordenar)
def painter_for(e):
    f, t = e['from'], e['to']
    key = (tuple(f), tuple(t))
    table = {((4, -8, 7.2), (12, 30, 8.8)): 0, ((5, 30, 7.3), (11, 31.5, 8.7)): 1, ((-3, 11, 7.2), (19, 21, 8.8)): 2,
             ((-4, 12, 7.3), (-3, 20, 8.7)): 3, ((19, 12, 7.3), (20, 20, 8.7)): 4, ((4, 12, 6.8), (12, 20, 7.2)): 5,
             ((5, 21, 6.95), (11, 22, 7.2)): 6, ((5, 10, 6.95), (11, 11, 7.2)): 7, ((2, 13, 6.95), (3, 19, 7.2)): 8,
             ((13, 13, 6.95), (14, 19, 7.2)): 9, ((7.3, 24, 6.9), (8.7, 27, 7.2)): 10, ((7.3, 3, 6.9), (8.7, 7, 7.2)): 11,
             ((6.3, 6, 6.9), (9.7, 6.6, 7.2)): 12, ((5.5, -10, 7.3), (10.5, -8, 8.7)): 13, ((6.8, -12, 7.4), (9.2, -10, 8.6)): 14,
             ((7.1, 8, 8.8), (8.9, 15, 10)): 15, ((0, 9, 8.2), (16, 23, 8.8)): 16}
    return table.get(key, 15)

faces = []
for e in elements:
    piece = painter_for(e)
    for name in e['faces']:
        img = face_pixels(e, name, PIECES[piece], piece)
        faces.append((img.height, img.width, e, name, img))
# Reparto en estantes, de las más altas a las más bajas
faces.sort(key=lambda f: (-f[0], -f[1]))
atlas = Image.new('RGBA', (SIZE, SIZE), T)
x = y = shelf = 0
for h, w, e, name, img in faces:
    if x + w > SIZE:
        x, y, shelf = 0, y + shelf + 1, 0
    assert y + h <= SIZE, 'no cabe la textura'
    atlas.paste(img, (x, y))
    e['faces'][name]['uv'] = [x / UV, y / UV, (x + w) / UV, (y + h) / UV]
    e['faces'][name].pop('rotation', None)
    x += w + 1
    shelf = max(shelf, h)

buf = io.BytesIO()
atlas.save(buf, 'PNG')
tex = d['textures'][0]
tex.update({"width": SIZE, "height": SIZE, "uv_width": 16, "uv_height": 16,
            "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()})
json.dump(d, open(dst, 'w', encoding='utf-8'))
print('caras:', len(faces), 'alto usado:', y + shelf)
