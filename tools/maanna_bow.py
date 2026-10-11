# Arco flotante de Ishtar: pone UV y textura (azul y oro, de las referencias del usuario) al modelo que él hizo
# (tools/blockbench/maanna_base.bbmodel) sin mover ni cambiar ninguna pieza, y lo parte en tres ítems para el juego:
#   maanna_hull  = empuñadura y centro del arco interior (fijo)
#   maanna_prow  = brazos de arriba   (giran al tensar)
#   maanna_stern = brazos de abajo    (giran al tensar)
# Uso: python tools/maanna_bow.py   (escribe tools/blockbench/maanna.bbmodel, textura y modelos de ítem)
import base64, io, json, os, uuid
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'fate_ubw')
T = 512                    # tamaño de la textura; el espacio UV del modelo es 16x16 (como en el escudo de Mash)
PX = T / 16                # píxeles por unidad UV
TEX = 'fate_ubw:item/maanna_bow'

BLUE, BLUE_D, BLUE_L = (36, 42, 200), (24, 26, 150), (70, 80, 235)
GOLD, GOLD_D, GOLD_L = (232, 190, 70), (160, 120, 30), (255, 232, 140)
RED, WHITE = (190, 38, 48), (240, 236, 220)

base = json.load(open(os.path.join(HERE, 'blockbench', 'maanna_base.bbmodel'), encoding='utf-8'))
els = base['elements']
# Qué es cada pieza (por su sitio en el modelo del usuario)
ROLE = {0: 'grip', 12: 'bell', 13: 'bell', 5: 'tip', 6: 'tip'}
for i in (1, 2, 3, 4): ROLE[i] = 'outer'
for i in (7, 8, 9, 10, 11): ROLE[i] = 'inner'


def face_sizes(e):
    dx, dy, dz = [e['to'][k] - e['from'][k] for k in range(3)]
    return {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}


# ---------- reparto de la textura: estantes, de mayor a menor ----------
def pack(density):
    items = []
    for i, e in enumerate(els):
        for n, (w, h) in face_sizes(e).items():
            items.append((h * density, w * density, i, n))
    items.sort(reverse=True)
    x = y = row = 0.0
    out = {}
    for h, w, i, n in items:
        if x + w > 16:
            x, y, row = 0.0, y + row, 0.0
        if y + h > 16:
            return None
        out[(i, n)] = (x, y, x + w, y + h)
        x += w
        row = max(row, h)
    return out


density = 0.62
while True:
    layout = pack(density)
    if layout:
        break
    density -= 0.01
print('densidad', round(density, 2), '->', round(density * PX, 1), 'px por unidad del modelo')

# ---------- pintado ----------
img = Image.new('RGBA', (T, T), (0, 0, 0, 0))
dr = ImageDraw.Draw(img)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def paint(role, n, box):
    x0, y0, x1, y1 = [int(round(v * PX)) for v in box]
    w, h = x1 - x0, y1 - y0
    cap = n in ('up', 'down')
    long_h = w >= h                                   # el lado largo del trozo
    side = 'east' if n in ('east', 'west') else 'front'
    if role in ('bell', 'tip'):
        dr.rectangle([x0, y0, x1 - 1, y1 - 1], fill=GOLD)
        for k in range(0, (h if True else w), max(6, h // 5)):
            dr.line([x0, y0 + k, x1 - 1, y0 + k], fill=GOLD_D, width=2)
        dr.rectangle([x0, y0, x1 - 1, y1 - 1], outline=GOLD_L, width=2)
        return
    if role == 'grip' and not cap:
        dr.rectangle([x0, y0, x1 - 1, y1 - 1], fill=GOLD)
        # cinta roja y blanca en rombos, en diagonal
        if n in ('north', 'south'):
            step = max(8, w // 3)
            for k in range(-h, h + w, step):
                for r in range(0, h, step):
                    cx, cy = x0 + k + r - (r // step) * 0, y0 + r
                    cx = x0 + (k + r) % (w + step)
                    col = RED if (k // step + r // step) % 2 == 0 else WHITE
                    dr.polygon([(cx, cy), (cx + step // 2, cy + step // 2), (cx, cy + step), (cx - step // 2, cy + step // 2)],
                               fill=col)
            dr.rectangle([x0, y0, x1 - 1, y1 - 1], outline=GOLD_D, width=3)
        else:
            dr.rectangle([x0, y0, x1 - 1, y1 - 1], outline=GOLD_D, width=2)
        return
    # cuerpo azul con degradado y filos dorados
    for k in range(h):
        t = k / max(1, h - 1)
        dr.line([x0, y0 + k, x1 - 1, y0 + k], fill=shade(BLUE, 0.85 + 0.3 * (0.5 - abs(t - 0.5))))
    if cap:
        dr.rectangle([x0, y0, x1 - 1, y1 - 1], fill=GOLD)
        dr.rectangle([x0, y0, x1 - 1, y1 - 1], outline=GOLD_D, width=2)
        return
    edge = max(3, int(min(w, h) * 0.09))
    dr.rectangle([x0, y0, x1 - 1, y1 - 1], outline=GOLD, width=edge)
    # línea dorada central, siguiendo el lado largo, con adornos
    if role == 'outer':
        if long_h:
            cy = (y0 + y1) // 2
            dr.line([x0, cy, x1 - 1, cy], fill=GOLD, width=max(3, h // 7))
            for k in range(x0 + 10, x1 - 10, max(18, h)):
                dr.polygon([(k, cy), (k + h // 3, y0 + edge), (k + h // 2, cy), (k + h // 3, y1 - edge)], outline=GOLD, width=2)
        else:
            cx = (x0 + x1) // 2
            dr.line([cx, y0, cx, y1 - 1], fill=GOLD, width=max(3, w // 7))
            for k in range(y0 + 10, y1 - 10, max(18, w)):
                dr.polygon([(cx, k), (x0 + edge, k + w // 3), (cx, k + w // 2), (x1 - edge, k + w // 3)], outline=GOLD, width=2)
    else:  # inner: arcos dorados concéntricos, como el creciente de las referencias
        step = max(14, min(w, h) // 2)
        if long_h:
            for k in range(x0 + step // 2, x1, step):
                dr.arc([k - step // 2, y0 + edge, k + step // 2, y1 - edge], 180, 360, fill=GOLD, width=3)
        else:
            for k in range(y0 + step // 2, y1, step):
                dr.arc([x0 + edge, k - step // 2, x1 - edge, k + step // 2], 90, 270, fill=GOLD, width=3)


for (i, n), box in layout.items():
    paint(ROLE[i], n, box)

buf = io.BytesIO()
img.save(buf, 'PNG')
png = buf.getvalue()

# ---------- .bbmodel con UV y textura (piezas y grupos del usuario intactos) ----------
tex = {"name": "maanna_bow.png", "path": "", "folder": "item", "namespace": "fate_ubw", "id": "0", "group": "",
       "scope": 0, "width": T, "height": T, "uv_width": 16, "uv_height": 16, "particle": True, "use_as_default": False,
       "layers_enabled": False, "sync_to_project": "", "file_format": "png", "render_mode": "default",
       "render_sides": "auto", "wrap_mode": "limited", "pbr_channel": "color", "fps": 7, "force_translucent": False,
       "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False, "visible": True,
       "internal": True, "saved": False, "uuid": str(uuid.uuid4()),
       "source": "data:image/png;base64," + base64.b64encode(png).decode()}
out = json.loads(json.dumps(base))
out['resolution'] = {'width': 16, 'height': 16}
out['name'] = 'maanna'
for i, e in enumerate(out['elements']):
    e['autouv'] = 0
    for n in e['faces']:
        e['faces'][n] = {'uv': [round(v, 4) for v in layout[(i, n)]], 'texture': 0}
out['textures'] = [tex]
json.dump(out, open(os.path.join(HERE, 'blockbench', 'maanna.bbmodel'), 'w', encoding='utf-8'), ensure_ascii=False)

# ---------- ítems del juego: modelo con los giros de 90° horneados y partido por zonas ----------
import subprocess, sys
full_json = os.path.join(HERE, '_maanna_full.json')
full_png = os.path.join(HERE, '_maanna_full.png')
subprocess.check_call([sys.executable, os.path.join(HERE, 'bbmodel_to_item.py'), os.path.join(HERE, 'blockbench', 'maanna.bbmodel'),
                       full_json, full_png, 'maanna_bow'], stdout=subprocess.DEVNULL)
full = json.load(open(full_json, encoding='utf-8'))
PIVOT_Y = 15.0
parts = {'maanna_hull': [], 'maanna_prow': [], 'maanna_stern': []}
for e in full['elements']:
    lo, hi = e['from'][1], e['to'][1]
    if lo < PIVOT_Y < hi:
        parts['maanna_hull'].append(e)             # cruza el centro: no gira
    elif lo >= PIVOT_Y:
        parts['maanna_prow'].append(e)
    else:
        parts['maanna_stern'].append(e)
for name, elements in parts.items():
    with open(os.path.join(ASSETS, 'models', 'item', name + '.json'), 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('{\n  "gui_light": "front",\n  "textures": %s,\n  "elements": [\n%s\n  ],\n  "display": {}\n}\n' % (
            json.dumps({'0': TEX, 'particle': TEX}), ',\n'.join('    ' + json.dumps(el) for el in elements)))
    print(name, len(elements), 'piezas')
open(os.path.join(ASSETS, 'textures', 'item', 'maanna_bow.png'), 'wb').write(png)
os.remove(full_json)
os.remove(full_png)
