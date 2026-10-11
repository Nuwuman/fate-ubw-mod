# Arco flotante de Ishtar: genera el modelo (a partir de la forma del usuario, tools/blockbench/maanna_base.bbmodel, con
# curvas y detalles de sus referencias), le pone UV y textura azul y oro, y lo parte en cinco ítems para el juego:
#   maanna_hull = empuñadura, abanico y cintas (fijo)
#   maanna_prow / maanna_stern = brazo de fuera de arriba / abajo (giran alrededor de la empuñadura al tensar)
#   maanna_inner_up / maanna_inner_down = creciente de dentro con sus campanas (giran alrededor de su centro)
# Uso: python tools/maanna_bow.py   (escribe tools/blockbench/maanna.bbmodel, la textura y los modelos de ítem)
import base64, io, json, os, uuid
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'fate_ubw')
T = 1024                   # tamaño de la textura; el espacio UV del modelo es 16x16 (como en el escudo de Mash)
PX = T / 16                # píxeles por unidad UV
TEX = 'fate_ubw:item/maanna_bow'

BLUE, BLUE_D, BLUE_L = (36, 42, 200), (24, 26, 150), (70, 80, 235)
GOLD, GOLD_D, GOLD_L = (232, 190, 70), (160, 120, 30), (255, 232, 140)
RED, WHITE = (190, 38, 48), (240, 236, 220)

# ---------- forma ----------
# La silueta es la del usuario (tools/blockbench/maanna_base.bbmodel): empuñadura delante, brazo de fuera que sube y se
# abre hacia atrás, y creciente de dentro con una campana en cada punta. Aquí se redondea con tramos girados de 22,5° en
# 22,5° (lo máximo que admiten los modelos JSON), y se añaden los detalles de las referencias: aro y pestaña de las
# campanas, tachuelas, abanico con cintas y cola de la cinta. Todo se define para el brazo de arriba y se refleja.
import math
CY = 15.0                                   # centro vertical del arco
elements = []                               # cada pieza: name, from, to, rotation, origin, role, part


def add(name, lo, hi, role, part, rot=None, origin=None):
    elements.append({'name': name, 'from': lo, 'to': hi, 'rot': rot, 'origin': origin, 'role': role, 'part': part})


def mirror_y(e):
    lo, hi = e['from'], e['to']
    out = dict(e)
    out['from'] = [lo[0], 2 * CY - hi[1], lo[2]]
    out['to'] = [hi[0], 2 * CY - lo[1], hi[2]]
    if e['rot']:
        out['rot'] = [-e['rot'][0], e['rot'][1], e['rot'][2]]
        out['origin'] = [e['origin'][0], 2 * CY - e['origin'][1], e['origin'][2]]
    out['part'] = e['part'].replace('_up', '_down')
    return out


def seg(name, p0, p1, tx0, tx1, cross0, cross1, role, part, grow=0.5):
    """Tramo entre dos puntos (z, y) del perfil; el grosor (x) y el ancho pasan de un extremo al otro."""
    dz, dy = p1[0] - p0[0], p1[1] - p0[1]
    ang = math.degrees(math.atan2(dy, dz))
    length = math.hypot(dz, dy) + grow
    cz, cy = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2
    tx, cross = (tx0 + tx1) / 2, (cross0 + cross1) / 2
    if abs(ang) <= 45 + 11.25:
        theta = -max(-45, min(45, round(ang / 22.5) * 22.5))
        lo, hi = [8 - tx / 2, cy - cross / 2, cz - length / 2], [8 + tx / 2, cy + cross / 2, cz + length / 2]
    else:
        theta = max(-45, min(45, round((90 - ang) / 22.5) * 22.5))
        lo, hi = [8 - tx / 2, cy - length / 2, cz - cross / 2], [8 + tx / 2, cy + length / 2, cz + cross / 2]
    add(name, lo, hi, role, part, [theta, 0, 0] if theta else None, [8, cy, cz] if theta else None)


# --- brazo de fuera (sale de la empuñadura, sube y se abre hacia atrás con la punta en flecha) ---
EZ, EY, EA, EB = 14.0, 22.0, 13.0, 5.5      # centro y radios de la elipse del brazo
pts = [(EZ - EA * math.cos(math.radians(f)), EY + EB * math.sin(math.radians(f))) for f in (0, 18, 36, 54, 72, 90)]
thick = [2.8, 2.6, 2.4, 2.2, 2.0, 1.8]
width = [3.2, 3.0, 2.8, 2.6, 2.3, 2.0]
for k in range(5):
    seg('externo_%d' % k, pts[k], pts[k + 1], thick[k], thick[k + 1], width[k], width[k + 1], 'outer', 'outer_up')
seg('externo_punta_0', pts[5], (pts[5][0] + 2.8, pts[5][1] + 1.4), 1.8, 1.4, 2.0, 1.5, 'outer', 'outer_up')
seg('externo_punta_1', (pts[5][0] + 2.8, pts[5][1] + 1.4), (pts[5][0] + 4.4, pts[5][1] + 3.6), 1.4, 1.0, 1.5, 1.0, 'tip', 'outer_up')
add('externo_aro', [6.6, EY - 0.4, -0.6], [9.4, EY + 2.6, 2.6], 'ring', 'outer_up')      # aro dorado donde sale de la empuñadura

# --- creciente de dentro (con la campana y la pestaña en la punta) ---
CZ, IA, IB = 28.0, 12.5, 10.5               # centro y radios de su elipse
ang = [0, 17, 35, 52, 70]
ip = [(CZ - IA * math.cos(math.radians(f)), CY + IB * math.sin(math.radians(f))) for f in ang]
for k in range(4):
    seg('interno_%d' % k, ip[k], ip[k + 1], 2.4 - 0.15 * k, 2.4 - 0.15 * (k + 1), 3.2 - 0.25 * k, 3.2 - 0.25 * (k + 1),
        'inner', 'inner_up', grow=0.3)
f = math.radians(70)                          # tangente en la punta: hacia atrás y un poco arriba
tz, ty = IA * math.sin(f), IB * math.cos(f)
tn = math.hypot(tz, ty)
dz, dy = tz / tn, ty / tn
base = ip[4]
BELL = [(1.0, 2.8, 2.6, 'ring'), (2.2, 3.4, 3.2, 'bell'), (1.4, 4.6, 4.4, 'bell')]   # largo, ancho x, ancho y
pos = 0.0
for k, (ln, wx, wy, role) in enumerate(BELL):
    a = (base[0] + dz * pos, base[1] + dy * pos)
    b = (base[0] + dz * (pos + ln), base[1] + dy * (pos + ln))
    seg('campana_%d' % k, a, b, wx, wx, wy, wy, role, 'inner_up', grow=0.2)
    pos += ln
END = (base[0] + dz * pos, base[1] + dy * pos)           # boca de la campana
ANCHOR = (END[0] - dz * 0.6, END[1] - dy * 0.6)           # donde se ata la cuerda
# pestaña en rombo con forma de punta de flecha, más allá de la boca
cz, cy = END[0] + dz * 1.8, END[1] + dy * 1.8
add('pestana', [7.6, cy - 2.0, cz - 2.0], [8.4, cy + 2.0, cz + 2.0], 'tab', 'inner_up', [45, 0, 0], [8, cy, cz])
add('pestana_aro', [7.5, cy - 0.5, cz - 0.5], [8.5, cy + 0.5, cz + 0.5], 'tab', 'inner_up')
# tachuelas en forma de flecha en el borde de dentro del creciente (apuntan al hueco)
for k, fa in enumerate((35, 62)):
    q = (CZ - IA * math.cos(math.radians(fa)), CY + IB * math.sin(math.radians(fa)))
    add('tachuela_%d' % k, [7.4, q[1] - 0.8, q[0] + 0.6], [8.6, q[1] + 0.8, q[0] + 3.2], 'stud', 'inner_up')
add('tachuela_c', [7.2, CY - 1.0, ip[0][0] + 1.0], [8.8, CY + 1.0, ip[0][0] + 4.0], 'stud', 'inner_up')

# --- empuñadura, abanico y cintas (fijos) ---
add('empunadura', [7, 8, 0], [9, 22, 2], 'grip', 'hull')
add('abanico_0', [7.2, 9, 2], [8.8, 21, 4], 'fan', 'hull')
add('abanico_1', [7.2, 10.5, 4], [8.8, 19.5, 6], 'fan', 'hull')
add('abanico_2', [7.2, 12, 6], [8.8, 18, 8], 'fan', 'hull')
add('ala_i', [9.2, 10.5, 0.3], [12.4, 19.5, 1.9], 'fan', 'hull', [0, 22.5, 0], [9.2, 15, 1.1])
add('ala_d', [3.6, 10.5, 0.3], [6.8, 19.5, 1.9], 'fan', 'hull', [0, -22.5, 0], [6.8, 15, 1.1])
for k, y0 in enumerate((10.0, 16.5)):
    add('cinta_%d' % k, [6.6, y0, -0.45], [9.4, y0 + 3.0, 2.45], 'ribbon', 'hull', [-22.5, 0, 0], [8, y0 + 1.5, 1.0])
add('cola_0', [7.1, 2.0, 0.2], [8.0, 10.0, 0.9], 'tail', 'hull', [22.5, 0, 0], [7.5, 10, 0.5])
add('cola_1', [8.0, 3.5, 1.1], [8.9, 10.0, 1.8], 'tail', 'hull', [-22.5, 0, 0], [8.5, 10, 1.5])

# reflejo del brazo de arriba hacia abajo
for e in [e for e in elements if e['part'].endswith('_up')]:
    elements.append(mirror_y(e))
print('anclaje de la cuerda: z=%.2f y=%.2f; pivote interior z=%.2f' % (ANCHOR[0], ANCHOR[1], ip[0][0]))
for e in elements:
    e['faces_n'] = None

# ---------- reparto de la textura: estantes, de mayor a menor ----------
def face_sizes(e):
    dx, dy, dz = [e['to'][k] - e['from'][k] for k in range(3)]
    return {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}


def pack(density):
    items = []
    for i, e in enumerate(elements):
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


density = 0.9
while True:
    layout = pack(density)
    if layout:
        break
    density -= 0.01
print('piezas', len(elements), '| densidad', round(density, 2), '->', round(density * PX, 1), 'px por unidad del modelo')

# ---------- pintado ----------
img = Image.new('RGBA', (T, T), (0, 0, 0, 0))
dr = ImageDraw.Draw(img)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def gold(x0, y0, x1, y1):
    dr.rectangle([x0, y0, x1 - 1, y1 - 1], fill=GOLD)
    step = max(6, min(x1 - x0, y1 - y0) // 3)
    for k in range(step, max(x1 - x0, y1 - y0), step):
        if x1 - x0 >= y1 - y0:
            dr.line([x0 + k, y0, x0 + k, y1 - 1], fill=GOLD_D, width=2)
        else:
            dr.line([x0, y0 + k, x1 - 1, y0 + k], fill=GOLD_D, width=2)
    dr.rectangle([x0, y0, x1 - 1, y1 - 1], outline=GOLD_L, width=2)


def blue(x0, y0, x1, y1):
    h = y1 - y0
    for k in range(h):
        t = k / max(1, h - 1)
        dr.line([x0, y0 + k, x1 - 1, y0 + k], fill=shade(BLUE, 0.85 + 0.3 * (0.5 - abs(t - 0.5))))


def paint(role, n, box):
    x0, y0, x1, y1 = [int(round(v * PX)) for v in box]
    w, h = x1 - x0, y1 - y0
    cap = n in ('up', 'down')
    long_h = w >= h                                   # el lado largo del trozo
    if role in ('bell', 'tip', 'ring', 'tab', 'stud'):
        gold(x0, y0, x1, y1)
        return
    if role == 'ribbon':
        s = max(6, min(w, h) // 2)
        for i in range(0, w, s):
            for j in range(0, h, s):
                dr.rectangle([x0 + i, y0 + j, min(x1, x0 + i + s) - 1, min(y1, y0 + j + s) - 1],
                             fill=RED if (i // s + j // s) % 2 == 0 else WHITE)
        dr.rectangle([x0, y0, x1 - 1, y1 - 1], outline=GOLD_D, width=2)
        return
    if role == 'tail':
        dr.rectangle([x0, y0, x1 - 1, y1 - 1], fill=RED)
        dr.rectangle([x0, y0, x1 - 1, y1 - 1], outline=WHITE, width=max(2, min(w, h) // 8))
        return
    blue(x0, y0, x1, y1)
    if cap:
        gold(x0, y0, x1, y1)
        return
    edge = max(3, int(min(w, h) * 0.09))
    dr.rectangle([x0, y0, x1 - 1, y1 - 1], outline=GOLD, width=edge)
    if role == 'grip':                                # bandas doradas a lo largo
        for f in (0.2, 0.8):
            if long_h:
                dr.rectangle([x0 + int(w * f) - 4, y0, x0 + int(w * f) + 4, y1 - 1], fill=GOLD)
            else:
                dr.rectangle([x0, y0 + int(h * f) - 4, x1 - 1, y0 + int(h * f) + 4], fill=GOLD)
    elif role == 'fan':                               # chevrones dorados
        step = max(10, min(w, h) // 2)
        if long_h:
            for k in range(x0 + step, x1 - step // 2, step):
                dr.line([k - step // 3, y0 + edge, k, (y0 + y1) // 2, k - step // 3, y1 - edge], fill=GOLD, width=3)
        else:
            for k in range(y0 + step, y1 - step // 2, step):
                dr.line([x0 + edge, k - step // 3, (x0 + x1) // 2, k, x1 - edge, k - step // 3], fill=GOLD, width=3)
    elif role == 'outer':
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
    paint(elements[i]['role'], n, box)

buf = io.BytesIO()
img.save(buf, 'PNG')
png = buf.getvalue()

# ---------- .bbmodel con UV y textura (grupos: externo, interno y centro) ----------
tex = {"name": "maanna_bow.png", "path": "", "folder": "item", "namespace": "fate_ubw", "id": "0", "group": "",
       "scope": 0, "width": T, "height": T, "uv_width": 16, "uv_height": 16, "particle": True, "use_as_default": False,
       "layers_enabled": False, "sync_to_project": "", "file_format": "png", "render_mode": "default",
       "render_sides": "auto", "wrap_mode": "limited", "pbr_channel": "color", "fps": 7, "force_translucent": False,
       "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False, "visible": True,
       "internal": True, "saved": False, "uuid": str(uuid.uuid4()),
       "source": "data:image/png;base64," + base64.b64encode(png).decode()}
GROUPS = {'outer': 'externo', 'inner': 'interno', 'hull': 'centro'}
group_ids = {g: str(uuid.uuid4()) for g in GROUPS.values()}
bb_elements, children = [], {g: [] for g in GROUPS.values()}
for i, e in enumerate(elements):
    uid = str(uuid.uuid4())
    lo, hi = e['from'], e['to']
    el = {"name": e['name'], "box_uv": False, "render_order": "default", "rescale": False, "locked": False,
          "shade_direction_override": "", "light_emission": 0, "export": True, "scope": 0, "allow_mirror_modeling": True,
          "from": [round(v, 3) for v in lo], "to": [round(v, 3) for v in hi], "autouv": 0, "color": i % 8,
          "origin": [round(v, 3) for v in (e['origin'] or [(lo[k] + hi[k]) / 2 for k in range(3)])],
          "faces": {n: {"uv": [round(v, 4) for v in layout[(i, n)]], "texture": 0} for n in face_sizes(e)},
          "type": "cube", "uuid": uid}
    if e['rot']:
        el['rotation'] = e['rot']
    bb_elements.append(el)
    children[GROUPS[e['part'].split('_')[0]]].append(uid)
groups = [{"name": g, "uuid": group_ids[g], "export": True, "locked": False, "scope": 0, "selected": False,
           "visibility": True, "_static": {"properties": {}, "temp_data": {}}, "origin": [8, 8, 8], "rotation": [0, 0, 0],
           "color": 0, "children": [], "reset": False, "shade": True, "mirror_uv": False, "autouv": 0, "isOpen": True,
           "primary_selected": False} for g in GROUPS.values()]
out = {"meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False}, "name": "maanna", "parent": "",
       "java_block_version": "1.21.11", "ambientocclusion": True, "front_gui_light": False, "visible_box": [1, 1, 0],
       "variable_placeholders": "", "multi_file_ruleset": "", "variable_placeholder_buttons": [],
       "unhandled_root_fields": {}, "resolution": {"width": 16, "height": 16}, "elements": bb_elements, "groups": groups,
       "outliner": [{"uuid": group_ids[g], "isOpen": True, "children": children[g]} for g in GROUPS.values()],
       "textures": [tex], "display": {}}
json.dump(out, open(os.path.join(HERE, 'blockbench', 'maanna.bbmodel'), 'w', encoding='utf-8'), ensure_ascii=False)

# ---------- ítems del juego: giros de 90° horneados, y partido según la pieza ----------
import subprocess, sys
full_json = os.path.join(HERE, '_maanna_full.json')
full_png = os.path.join(HERE, '_maanna_full.png')
subprocess.check_call([sys.executable, os.path.join(HERE, 'bbmodel_to_item.py'), os.path.join(HERE, 'blockbench', 'maanna.bbmodel'),
                       full_json, full_png, 'maanna_bow'], stdout=subprocess.DEVNULL)
full = json.load(open(full_json, encoding='utf-8'))
ITEM = {'hull': 'maanna_hull', 'outer_up': 'maanna_prow', 'outer_down': 'maanna_stern',
        'inner_up': 'maanna_inner_up', 'inner_down': 'maanna_inner_down'}
parts = {name: [] for name in ITEM.values()}
for e, el in zip(elements, full['elements']):
    parts[ITEM[e['part']]].append(el)
for name, els_ in parts.items():
    with open(os.path.join(ASSETS, 'models', 'item', name + '.json'), 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('{\n  "gui_light": "front",\n  "textures": %s,\n  "elements": [\n%s\n  ],\n  "display": {}\n}\n' % (
            json.dumps({'0': TEX, 'particle': TEX}), ',\n'.join('    ' + json.dumps(el) for el in els_)))
    print(name, len(els_), 'piezas')
open(os.path.join(ASSETS, 'textures', 'item', 'maanna_bow.png'), 'wb').write(png)
os.remove(full_json)
os.remove(full_png)
