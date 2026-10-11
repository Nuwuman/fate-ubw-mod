# Convierte un .bbmodel de Blockbench (formato bedrock, UV por cara) al .geo.json de GeckoLib, como lo exporta Blockbench:
# X invertida, up/down con la UV volteada, inflate y huesos con su pivote y padre.
# Los grupos de referencia de Blockbench (el jugador "root" que añade la plantilla de armadura) no se exportan.
# El tamaño de la textura sale de la textura que usan las piezas (su uv_width/uv_height), no de la resolución del proyecto.
# Uso: python tools/bbmodel_to_geo.py <entrada.bbmodel> <salida.geo.json> [textura.png de salida]
import sys, json, os

d = json.load(open(sys.argv[1], encoding='utf-8'))
groups = {g['uuid']: g for g in d.get('groups', [])}
elements = {e['uuid']: e for e in d['elements']}
r = lambda v: round(v, 4) + 0  # sin -0.0


def face(name, f):
    u0, v0, u1, v1 = f['uv']
    if name in ('up', 'down'):
        return {"uv": [r(u1), r(v1)], "uv_size": [r(u0 - u1), r(v0 - v1)]}
    return {"uv": [r(u0), r(v0)], "uv_size": [r(u1 - u0), r(v1 - v0)]}


def cube(e):
    c = {"origin": [r(-e['to'][0]), r(e['from'][1]), r(e['from'][2])],
         "size": [r(e['to'][i] - e['from'][i]) for i in range(3)]}
    if e.get('inflate'):
        c["inflate"] = e['inflate']
    if e.get('rotation') and any(e['rotation']):
        c["pivot"] = [r(-e['origin'][0]), r(e['origin'][1]), r(e['origin'][2])]
        c["rotation"] = [r(-e['rotation'][0]), r(-e['rotation'][1]), r(e['rotation'][2])]
    c["uv"] = {n: face(n, f) for n, f in e['faces'].items() if f.get('texture') is not None}
    return c


SKIP = {"root"}
bones = []


def walk(nodes, parent=None):
    for node in nodes:
        if isinstance(node, str):
            continue
        g = groups.get(node['uuid'], node)
        if parent is None and g['name'] in SKIP:
            continue
        bone = {"name": g['name']}
        if parent:
            bone["parent"] = parent
        bone["pivot"] = [r(-g['origin'][0]), r(g['origin'][1]), r(g['origin'][2])]
        if g.get('rotation') and any(g['rotation']):
            bone["rotation"] = [r(-g['rotation'][0]), r(-g['rotation'][1]), r(g['rotation'][2])]
        bone["cubes"] = [cube(elements[c]) for c in node.get('children', []) if isinstance(c, str) and c in elements]
        bones.append(bone)
        walk(node.get('children', []), g['name'])


walk(d['outliner'])
from collections import Counter
tex_counts = Counter()
res = d.get('resolution', {"width": 64, "height": 64})
textures = d.get('textures', [])
kept = set()
def collect(nodes, skip=False):
    for n in nodes:
        if isinstance(n, str):
            if not skip: kept.add(n)
        else:
            g = groups.get(n['uuid'], n)
            collect(n.get('children', []), skip or (g['name'] in SKIP))
collect(d['outliner'])
for u in kept:
    for f in elements[u]['faces'].values():
        if f.get('texture') is not None: tex_counts[f['texture']] += 1
if tex_counts and textures:
    tid = tex_counts.most_common(1)[0][0]
    tex = next((t for t in textures if t.get('id') == str(tid) or textures.index(t) == tid), textures[0])
    res = {"width": tex.get('uv_width', tex.get('width')), "height": tex.get('uv_height', tex.get('height'))}
    if len(sys.argv) > 3:
        import base64
        open(sys.argv[3], 'wb').write(base64.b64decode(tex['source'].split(',', 1)[1]))
geo = {"format_version": "1.12.0", "minecraft:geometry": [{
    # El identificador sale del nombre del archivo de salida (mash_armor.geo.json → geometry.mash_armor)
    "description": {"identifier": "geometry." + os.path.basename(sys.argv[2]).split('.')[0],
                    "texture_width": res['width'], "texture_height": res['height'],
                    "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1.5, 0]},
    "bones": bones}]}
json.dump(geo, open(sys.argv[2], 'w', encoding='utf-8'), indent=2)
print(len(bones), 'huesos,', sum(len(b['cubes']) for b in bones), 'cubos')
