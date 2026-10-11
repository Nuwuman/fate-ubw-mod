# Convierte un .bbmodel de Blockbench en formato "Java Block/Item" al .json de modelo de ítem y su textura.
# Los giros de 90/180/270 (que Minecraft no admite en los modelos JSON) se hornean en la caja, porque siguen
# alineados con los ejes. Uso: python tools/bbmodel_to_item.py <entrada.bbmodel> <modelo.json> <textura.png> <id textura>
import sys, json, base64

src, out_json, out_png, tex_id = sys.argv[1:5]
d = json.load(open(src, encoding='utf-8'))
# Una textura por cada una del proyecto: la primera en <textura.png>, las demás en <textura>_1.png, _2.png...
tex_ids = {}
for k, t in enumerate(d['textures']):
    path = out_png if k == 0 else out_png[:-4] + '_%d.png' % k
    open(path, 'wb').write(base64.b64decode(t['source'].split(',', 1)[1]))
    tex_ids[str(t.get('id', k))] = (k, tex_id if k == 0 else tex_id + '_%d' % k)
r = lambda v: round(v, 4) + 0
AXES = 'xyz'
# Qué cara pasa a cuál al girar +90° sobre cada eje (regla de la mano derecha, como Minecraft)
TURN = {'z': {'east': 'up', 'up': 'west', 'west': 'down', 'down': 'east'},
        'x': {'up': 'south', 'south': 'down', 'down': 'north', 'north': 'up'},
        'y': {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}}


def rot90(e, axis, times):
    i = AXES.index(axis)
    a, b = [(1, 2), (2, 0), (0, 1)][i]                      # el plano que gira
    o = e['origin']
    for _ in range(times % 4):
        lo, hi = e['from'][:], e['to'][:]
        na0, na1 = sorted([o[a] - (lo[b] - o[b]), o[a] - (hi[b] - o[b])])
        nb0, nb1 = sorted([o[b] + (lo[a] - o[a]), o[b] + (hi[a] - o[a])])
        e['from'][a], e['to'][a], e['from'][b], e['to'][b] = na0, na1, nb0, nb1
        faces = {}
        for n, f in e['faces'].items():
            f = dict(f)
            if n in TURN[axis]:
                faces[TURN[axis][n]] = f
            else:                                           # las caras del eje giran su textura
                f['rotation'] = (f.get('rotation', 0) + 90) % 360
                faces[n] = f
        e['faces'] = faces


elements = []
for e in d['elements']:
    if not e.get('export', True):
        continue
    e = json.loads(json.dumps(e))
    rot = e.get('rotation') or [0, 0, 0]
    out = {}
    for i, ang in enumerate(rot):
        if ang and ang % 90 == 0:
            rot90(e, AXES[i], int(ang // 90))
            rot[i] = 0
    el = {"from": [r(v) for v in e['from']], "to": [r(v) for v in e['to']]}
    left = [(AXES[i], a) for i, a in enumerate(rot) if a]
    if left:
        assert len(left) == 1 and left[0][1] in (-45, -22.5, 22.5, 45), ('giro no admitido', e['name'], rot)
        el["rotation"] = {"angle": left[0][1], "axis": left[0][0], "origin": [r(v) for v in e['origin']]}
    el["faces"] = {}
    for n, f in e['faces'].items():
        if f.get('texture') is None:
            continue
        face = {"uv": [r(v) for v in f['uv']], "texture": "#%d" % tex_ids[str(f['texture'])][0]}
        if f.get('rotation'):
            face["rotation"] = f['rotation']
        el["faces"][n] = face
    elements.append(el)

model = {"gui_light": "front" if d.get('front_gui_light', True) else "side",
         "textures": {**{str(k): name for k, name in tex_ids.values()}, "particle": tex_id},
         "elements": elements,
         "display": d.get('display', {})}
with open(out_json, 'w', encoding='utf-8') as fh:
    fh.write('{\n  "gui_light": %s,\n  "textures": %s,\n  "elements": [\n' % (json.dumps(model['gui_light']), json.dumps(model['textures'])))
    fh.write(',\n'.join('    ' + json.dumps(el) for el in elements))
    fh.write('\n  ],\n  "display": %s\n}\n' % json.dumps(model['display'], indent=2).replace('\n', '\n  '))
print(len(elements), 'piezas')
