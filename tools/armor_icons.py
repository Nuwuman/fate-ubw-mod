# Iconos de inventario de las armaduras: los mismos cubos que la armadura puesta, sacados de su .geo.bbmodel y con su
# textura. Así el icono siempre coincide con el modelo, también con los que edita el usuario en Blockbench.
# Uso: python tools/armor_icons.py   (rehace los 31 modelos de models/item)
import json, math, os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'fate_ubw')
CHEST = ('armorBody', 'armorRightArm', 'armorLeftArm')
LEGS = ('armorRightLeg', 'armorLeftLeg')
BOOTS = ('armorRightBoot', 'armorLeftBoot')
# servant: [(ítem, huesos, desplazamiento en Y)]; los hijos de cada hueso van con él
SETS = {
    'saber': [('saber_chestplate', CHEST, -4), ('saber_leggings', LEGS, 2), ('saber_boots', BOOTS, 6)],
    'archer': [('archer_chestplate', CHEST + ('coatBack', 'coatLeft', 'coatRight', 'coatFrontL', 'coatFrontR'), -4),
               ('archer_leggings', LEGS, 2), ('archer_boots', BOOTS, 6)],
    'lancer': [('lancer_chestplate', CHEST + ('ponytail',), -4), ('lancer_leggings', LEGS, 2),
               ('lancer_boots', BOOTS, 6)],
    'rider': [('rider_helmet', ('armorHead',), -20), ('rider_chestplate', CHEST, -4),
              ('rider_leggings', LEGS, 2), ('rider_boots', BOOTS, 6)],
    'gilgamesh': [('gilgamesh_chestplate', CHEST + ('tassets',), -4), ('gilgamesh_leggings', LEGS, 2),
                  ('gilgamesh_boots', BOOTS, 6)],
    'caster': [('caster_hood', ('armorHead',), -20),
               ('caster_chestplate', CHEST + ('capeBack', 'capeLeft', 'capeRight'), -4),
               ('caster_leggings', LEGS, 2), ('caster_boots', BOOTS, 6)],
    'assassin': [('assassin_chestplate', CHEST + ('hair', 'haoriBack', 'haoriFrontL', 'haoriFrontR'), -4),
                 ('assassin_leggings', LEGS, 2), ('assassin_boots', BOOTS, 6)],
    'berserker': [('berserker_chestplate', CHEST, -4), ('berserker_leggings', LEGS, 2),
                  ('berserker_boots', BOOTS, 6)],
    'ishtar': [('ishtar_tiara', ('armorHead',), -20), ('ishtar_chestplate', CHEST, -4),
               ('ishtar_leggings', LEGS, 2), ('ishtar_boots', BOOTS, 6)],
    'mash': [('mash_chestplate', CHEST, -4), ('mash_leggings', LEGS, 2), ('mash_boots', BOOTS, 6)],
}

AXES = 'xyz'
# Qué cara pasa a cuál al girar +90° sobre cada eje (como en bbmodel_to_item.py)
TURN = {'z': {'east': 'up', 'up': 'west', 'west': 'down', 'down': 'east'},
        'x': {'up': 'south', 'south': 'down', 'down': 'north', 'north': 'up'},
        'y': {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}}


def turn_point(p, axis, o):
    a, b = [(1, 2), (2, 0), (0, 1)][AXES.index(axis)]
    q = list(p)
    q[a], q[b] = o[a] - (p[b] - o[b]), o[b] + (p[a] - o[a])
    return q


def turn(e, axis, times, o):
    """Gira la caja (y su giro pequeño pendiente) 90° 'times' veces sobre 'o'."""
    for _ in range(times % 4):
        p, q = turn_point(e['from'], axis, o), turn_point(e['to'], axis, o)
        e['from'], e['to'] = [min(u, v) for u, v in zip(p, q)], [max(u, v) for u, v in zip(p, q)]
        faces = {}
        for n, f in e['faces'].items():
            if n in TURN[axis]:
                faces[TURN[axis][n]] = f
            else:
                f['rotation'] = (f.get('rotation', 0) + 90) % 360
                faces[n] = f
        e['faces'] = faces
        if e['rest']:
            ax, ang, org = e['rest']
            v = turn_point([1 if c == ax else 0 for c in AXES], axis, [0, 0, 0])
            i = max(range(3), key=lambda k: abs(v[k]))
            e['rest'] = (AXES[i], ang * (1 if v[i] > 0 else -1), turn_point(org, axis, o))


def apply(e, rot, origin):
    """Hornea los giros de 90° y guarda el resto (solo uno: los modelos JSON no admiten más)."""
    for i in (2, 1):
        ang = (rot or [0, 0, 0])[i]
        if not ang:
            continue
        if ang % 90 == 0:
            turn(e, AXES[i], int(ang // 90), origin)
        elif not e['rest'] or abs(ang) > abs(e['rest'][1]):
            e['rest'] = (AXES[i], ang, list(origin))


FIT = 15  # lo que ocupa el icono en la casilla (16 = de borde a borde)


def gui_view(v, rx, ry):
    """Gira un punto como el "display" de Minecraft (rotationXYZ: primero Y y luego X sobre el resultado)."""
    x, y, z = v
    a, b = math.radians(ry), math.radians(rx)
    x, z = x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)
    y, z = y * math.cos(b) - z * math.sin(b), y * math.sin(b) + z * math.cos(b)
    return [x, y, z]


def icon(d, bones, dy, tex):
    groups = {g['uuid']: g for g in d.get('groups', [])}
    elements = {e['uuid']: e for e in d['elements']}
    # Las UV van en píxeles de la textura que usan las caras (el proyecto puede tener otra resolución, como Mash)
    used = {str(f['texture']) for e in d['elements'] for f in e['faces'].values() if f.get('texture') is not None}
    t = next(t for k, t in enumerate(d['textures']) if str(t.get('id', k)) in used)
    res_w, res_h = t.get('uv_width') or d['resolution']['width'], t.get('uv_height') or d['resolution']['height']
    out = []

    def walk(children, chain, take):
        for c in children:
            if isinstance(c, str):
                if take and c in elements:
                    out.append((elements[c], chain))
                continue
            g = groups.get(c['uuid'], c)
            walk(c['children'], [g] + chain, take or g['name'] in bones)

    walk(d['outliner'], [], False)
    els = []
    for src, chain in out:
        if not src.get('export', True):
            continue
        g = src.get('inflate', 0)
        e = {'from': [v - g for v in src['from']], 'to': [v + g for v in src['to']], 'rest': None,
             'faces': {n: dict(f) for n, f in src['faces'].items() if f.get('texture') is not None}}
        apply(e, src.get('rotation'), src.get('origin', [0, 0, 0]))
        for grp in chain:
            apply(e, grp.get('rotation'), grp.get('origin', [0, 0, 0]))
        shift = lambda p: [round(p[0] + 8, 4) + 0, round(p[1] + dy, 4) + 0, round(p[2] + 8, 4) + 0]
        el = {'from': shift(e['from']), 'to': shift(e['to'])}
        if e['rest']:
            ax, ang, org = e['rest']
            ang = max(-45, min(45, round(ang / 22.5) * 22.5))
            if ang:
                el['rotation'] = {'angle': ang, 'axis': ax, 'origin': shift(org)}
        el['faces'] = {}
        for n, f in e['faces'].items():
            u0, v0, u1, v1 = f['uv']
            face = {'uv': [round(u0 * 16 / res_w, 4), round(v0 * 16 / res_h, 4),
                           round(u1 * 16 / res_w, 4), round(v1 * 16 / res_h, 4)], 'texture': '#0'}
            if f.get('rotation'):
                face['rotation'] = f['rotation']
            el['faces'][n] = face
        els.append(el)
    # Encaja el icono en la casilla: lo centra y lo escala para que ocupe lo mismo sea grande o pequeño
    lo = [min(min(e['from'][i], e['to'][i]) for e in els) for i in range(3)]
    hi = [max(max(e['from'][i], e['to'][i]) for e in els) for i in range(3)]
    view = lambda v: gui_view(v, 30, 225)
    pts = [view([(lo, hi)[k >> i & 1][i] - 8 for i in range(3)]) for k in range(8)]
    size = max(max(q[i] for q in pts) - min(q[i] for q in pts) for i in (0, 1))
    s = round(min(2.0, FIT / size), 3)
    c = view([(lo[i] + hi[i]) / 2 - 8 for i in range(3)])
    display = {
        'gui': {'rotation': [30, 225, 0], 'translation': [round(-s * c[0], 2), round(-s * c[1], 2), 0], 'scale': [s] * 3},
        'ground': {'translation': [0, 3, 0], 'scale': [0.25] * 3},
        'fixed': {'scale': [0.5] * 3},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375] * 3},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [0.4] * 3},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'scale': [0.4] * 3},
    }
    return ('{\n  "gui_light": "front",\n  "textures": %s,\n  "elements": [\n%s\n  ],\n  "display": %s\n}\n'
            % (json.dumps({'0': tex, 'particle': tex}), ',\n'.join('    ' + json.dumps(el) for el in els),
               json.dumps(display, indent=2).replace('\n', '\n  ')))


if __name__ == '__main__':
    for servant, items in SETS.items():
        path = os.path.join(ROOT, 'geo', 'item', 'armor', servant + '_armor.geo.bbmodel')
        d = json.load(open(path, encoding='utf-8'))
        for name, bones, dy in items:
            with open(os.path.join(ROOT, 'models', 'item', name + '.json'), 'w', encoding='utf-8', newline='\n') as fh:
                fh.write(icon(d, bones, dy, 'fate_ubw:item/armor/%s_armor' % servant))
            print(name)
