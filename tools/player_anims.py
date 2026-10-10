import json, sys, os
out = sys.argv[1]
Z = [0, 0, 0]
def anim(name, length, bones):
    d = {"format_version": "1.8.0", "animations": {name: {"animation_length": length, "loop": False, "bones": {
        b: {"rotation": {str(t): v for t, v in keys}} for b, keys in bones.items()}}}}
    json.dump(d, open(os.path.join(out, name + ".json"), "w"), indent=2)

def hold(pose, start, end, length, attack=0.15):
    return [(0.0, Z), (attack, pose), (end, pose), (length, Z)]

# Avalon: alza la vaina con las dos manos delante, el pecho erguido (como en el anime al desplegarla ante Gilgamesh)
anim("avalon", 1.6, {
    "rightArm": hold([-95, -18, 0], 0, 1.2, 1.6), "leftArm": hold([-95, 18, 0], 0, 1.2, 1.6),
    "torso": hold([-6, 0, 0], 0, 1.2, 1.6), "head": hold([-8, 0, 0], 0, 1.2, 1.6),
    "rightLeg": hold([-12, 0, 4], 0, 1.2, 1.6), "leftLeg": hold([10, 0, -4], 0, 1.2, 1.6)})
# Mana Burst: se lanza hacia delante con los brazos atrás
anim("mana_burst", 0.8, {
    "torso": hold([28, 0, 0], 0, 0.5, 0.8, 0.08), "rightArm": hold([45, 0, 15], 0, 0.5, 0.8, 0.08),
    "leftArm": hold([45, 0, -15], 0, 0.5, 0.8, 0.08), "rightLeg": hold([-45, 0, 0], 0, 0.5, 0.8, 0.08),
    "leftLeg": hold([35, 0, 0], 0, 0.5, 0.8, 0.08), "head": hold([-20, 0, 0], 0, 0.5, 0.8, 0.08)})
# Gate of Babylon: el brazo derecho alzado y abierto hacia delante, mirando por encima del hombro, altivo
anim("gate_of_babylon", 1.4, {
    "rightArm": [(0.0, Z), (0.2, [-150, -10, 25]), (0.3, [-140, -10, 20]), (1.0, [-140, -10, 20]), (1.4, Z)],
    "leftArm": hold([5, 0, -12], 0, 1.0, 1.4), "torso": hold([-8, 10, 0], 0, 1.0, 1.4),
    "head": hold([-12, 0, 0], 0, 1.0, 1.4)})
# Enkidu: barre el brazo de un lado a otro mandando las cadenas
anim("enkidu", 1.0, {
    "rightArm": [(0.0, Z), (0.15, [-90, 50, 0]), (0.45, [-90, -50, 0]), (0.7, [-90, -50, 0]), (1.0, Z)],
    "torso": [(0.0, Z), (0.15, [0, 15, 0]), (0.45, [0, -15, 0]), (0.7, [0, -15, 0]), (1.0, Z)]})
# Hrunting: de frente, el arco al frente, tensa a fondo y suelta (sin girar el cuerpo: dispara hacia donde mira)
anim("hrunting", 1.3, {
    "leftArm": hold([-90, 8, 0], 0, 1.0, 1.3, 0.2),
    "rightArm": [(0.0, Z), (0.2, [-90, -8, 0]), (0.75, [-88, -22, 0]), (0.85, [-75, -10, 35]), (1.0, [-75, -10, 35]), (1.3, Z)],
    "rightLeg": hold([-10, 0, 4], 0, 1.0, 1.3, 0.2), "leftLeg": hold([10, 0, -4], 0, 1.0, 1.3, 0.2)})
# Ansuz: dibuja la runa en el aire con el dedo
anim("ansuz", 0.9, {
    "rightArm": [(0.0, Z), (0.12, [-95, 0, 0]), (0.25, [-120, -15, 0]), (0.4, [-80, 15, 0]), (0.55, [-110, 15, 0]),
                 (0.65, [-90, 0, 0]), (0.9, Z)],
    "leftArm": hold([0, 0, -12], 0, 0.65, 0.9), "torso": hold([0, -8, 0], 0, 0.65, 0.9)})
# Divine Words: las dos manos alzadas lanzando el conjuro
anim("divine_words", 1.2, {
    "rightArm": hold([-145, -15, 15], 0, 0.9, 1.2, 0.25), "leftArm": hold([-145, 15, -15], 0, 0.9, 1.2, 0.25),
    "torso": hold([-10, 0, 0], 0, 0.9, 1.2, 0.25), "head": hold([-15, 0, 0], 0, 0.9, 1.2, 0.25)})
# Jewel Burst: lanza las joyas de arriba abajo, entre los dedos
anim("jewel_burst", 0.7, {
    "rightArm": [(0.0, [-170, 0, 10]), (0.15, [-50, 0, 0]), (0.4, [-50, 0, 0]), (0.7, Z)],
    "torso": [(0.0, [-5, 0, 0]), (0.15, [12, 0, 0]), (0.4, [12, 0, 0]), (0.7, Z)],
    "leftArm": hold([-20, 0, -15], 0, 0.4, 0.7)})
# Mad Enhancement: el rugido, brazos abiertos hacia abajo y la cabeza atrás
anim("mad_enhancement", 1.3, {
    "rightArm": hold([15, 0, 40], 0, 1.0, 1.3, 0.2), "leftArm": hold([15, 0, -40], 0, 1.0, 1.3, 0.2),
    "torso": hold([-15, 0, 0], 0, 1.0, 1.3, 0.2), "head": hold([-35, 0, 0], 0, 1.0, 1.3, 0.2),
    "rightLeg": hold([0, 0, 10], 0, 1.0, 1.3, 0.2), "leftLeg": hold([0, 0, -10], 0, 1.0, 1.3, 0.2)})
# Bellerophon: inclinada sobre Pegaso, tirando de las riendas doradas hacia delante
anim("bellerophon", 1.6, {
    "torso": hold([30, 0, 0], 0, 1.3, 1.6, 0.2), "head": hold([-25, 0, 0], 0, 1.3, 1.6, 0.2),
    "rightArm": [(0.0, Z), (0.2, [-60, -10, 0]), (0.5, [-75, -10, 0]), (0.8, [-55, -10, 0]), (1.3, [-70, -10, 0]), (1.6, Z)],
    "leftArm": [(0.0, Z), (0.2, [-60, 10, 0]), (0.5, [-75, 10, 0]), (0.8, [-55, 10, 0]), (1.3, [-70, 10, 0]), (1.6, Z)]})
# Nine Lives: nueve tajos seguidos, alternando direcciones, con el torso acompañando
cuts = [[-170, 0, 0], [-30, 40, 0], [-150, -40, 20], [-20, -40, 0], [-160, 40, 0], [-40, 0, 0],
        [-140, -50, 30], [-30, 50, 0], [-175, 0, 0], [-10, 0, 0]]
anim("nine_lives", 1.3, {
    "rightArm": [(0.0, Z)] + [(round(0.06 + i * 0.1, 2), c) for i, c in enumerate(cuts)] + [(1.3, Z)],
    "leftArm": [(0.0, Z)] + [(round(0.06 + i * 0.1, 2), [c[0] * 0.6, -c[1], -c[2]]) for i, c in enumerate(cuts)] + [(1.3, Z)],
    "torso": [(0.0, Z)] + [(round(0.06 + i * 0.1, 2), [10 if i % 2 else -5, 18 if i % 2 else -18, 0]) for i in range(len(cuts))] + [(1.3, Z)],
    "rightLeg": hold([-25, 0, 5], 0, 1.0, 1.3, 0.1), "leftLeg": hold([20, 0, -5], 0, 1.0, 1.3, 0.1)})

# ---------- Mash ----------
def pose(name, bones):
    """Postura mantenida mientras se usa el ítem: llega en 0,2 s y se queda (hold_on_last_frame)."""
    d = {"format_version": "1.8.0", "animations": {name: {"animation_length": 0.2, "loop": "hold_on_last_frame", "bones": {
        b: {"rotation": {"0.0": Z, "0.2": v}} for b, v in bones.items()}}}}
    json.dump(d, open(os.path.join(out, name + ".json"), "w"), indent=2)

# Guardia: el escudo en alto delante, el cuerpo un poco agachado tras él
pose("mash_guard", {"leftArm": [-80, 25, 0], "rightArm": [-75, -30, 0], "torso": [8, 0, 0],
                    "rightLeg": [-12, 0, 4], "leftLeg": [10, 0, -4]})
# Cargando Lord Camelot: el escudo plantado en el suelo con las dos manos, el peso hacia delante
pose("mash_plant", {"leftArm": [-45, 20, 0], "rightArm": [-45, -20, 0], "torso": [18, 0, 0], "head": [-15, 0, 0],
                    "rightLeg": [-30, 0, 6], "leftLeg": [20, 0, -6]})
# Lord Camelot: alza el escudo y lo planta con fuerza
anim("lord_camelot", 1.4, {
    "leftArm": [(0.0, [-45, 20, 0]), (0.25, [-150, 15, 0]), (0.5, [-60, 20, 0]), (1.1, [-60, 20, 0]), (1.4, Z)],
    "rightArm": [(0.0, [-45, -20, 0]), (0.25, [-150, -15, 0]), (0.5, [-60, -20, 0]), (1.1, [-60, -20, 0]), (1.4, Z)],
    "torso": [(0.0, [18, 0, 0]), (0.25, [-8, 0, 0]), (0.5, [20, 0, 0]), (1.1, [20, 0, 0]), (1.4, Z)],
    "rightLeg": hold([-30, 0, 6], 0, 1.1, 1.4, 0.1), "leftLeg": hold([20, 0, -6], 0, 1.1, 1.4, 0.1)})
# Bunker Bolt: embestida con el hombro y el escudo por delante
anim("bunker_bolt", 0.8, {
    "torso": hold([30, -20, 0], 0, 0.5, 0.8, 0.08), "leftArm": hold([-90, 30, 0], 0, 0.5, 0.8, 0.08),
    "rightArm": hold([30, 0, 15], 0, 0.5, 0.8, 0.08), "rightLeg": hold([-40, 0, 0], 0, 0.5, 0.8, 0.08),
    "leftLeg": hold([30, 0, 0], 0, 0.5, 0.8, 0.08)})
# Muro de Copos de Nieve: los brazos abiertos hacia los compañeros
anim("wall_of_snowflakes", 1.2, {
    "rightArm": hold([-100, 0, 45], 0, 0.9, 1.2, 0.2), "leftArm": hold([-100, 0, -45], 0, 0.9, 1.2, 0.2),
    "torso": hold([-6, 0, 0], 0, 0.9, 1.2, 0.2)})
# Muro de Tiza: señala con la mano libre a quien protege
anim("wall_of_chalk", 1.0, {
    "rightArm": hold([-95, -10, 0], 0, 0.7, 1.0, 0.15), "torso": hold([0, -10, 0], 0, 0.7, 1.0, 0.15)})
