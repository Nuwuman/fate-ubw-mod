# Fate: Unlimited Blade Works

Mod de Fabric para Minecraft 1.21.1 con los servants de Fate/stay night UBW: armas, armaduras y Noble Phantasms.

![Capturas dentro del juego](docs/preview.png)

## Contenido

**Saber**
- **Excalibur**: mantén click derecho 3 s y suelta para lanzar el haz de luz (48 bloques, ignora armadura).
  Agachado + click derecho: **Strike Air**, ráfaga de viento en cono.

**Archer (EMIYA)**
- **Kanshō y Bakuya**: click derecho los lanza y vuelven a la mano. Con uno en cada mano salen los dos y se cruzan.
  Agachado + click derecho: **Rho Aias**, escudo de siete pétalos que destruye proyectiles.
- **Arco de Archer**: dispara espadas proyectadas sin gastar flechas. Agachado y tensado a tope: **Caladbolg II**,
  que explota al impactar (Broken Phantasm).
- **Armadura** (peto, grebas, botas) con modelo 3D de GeckoLib y faldón animado.
  Conjunto completo: visión nocturna y los monstruos cercanos brillan.

Todo está en la pestaña **Fate: Unlimited Blade Works** del creativo, y tiene recetas de crafteo.

## Gamerule

```
/gamerule fateAbilitiesBreakBlocks true
```

Desactivada por defecto. Activada: Excalibur abre un túnel por donde pasa y Caladbolg II explota como TNT.
Bedrock, obsidiana y bloques igual de resistentes no se rompen.

## Instalar

En la carpeta `mods` de una instancia **Fabric 1.21.1** (Fabric Loader 0.16 o superior):

1. `fate_ubw-<versión>.jar` (de la pestaña **Releases** de este repo)
2. [Fabric API](https://modrinth.com/mod/fabric-api) para 1.21.1
3. [GeckoLib](https://modrinth.com/mod/geckolib) 4.7.x para Fabric 1.21.1

## Compilar

Necesita JDK 21.

```
gradlew build
```

El jar sale en `build/libs/`.

## Herramientas de desarrollo

- `java tools/TextureGen.java src/main/resources/assets/fate_ubw/textures/item`: regenera las texturas animadas de Excalibur.
- `java tools/ArcherAssets.java src/main/resources/assets/fate_ubw`: regenera los modelos 3D de Archer
  (ítems JSON, geo de GeckoLib) y sus texturas.
- `gradlew runShowcase`: abre un cliente de desarrollo que crea un mundo, usa cada arma y habilidad,
  guarda capturas en `run-showcase/screenshots` y se cierra solo. No entra en el jar publicado.

---

Mod de fans sin ánimo de lucro. Fate/stay night y sus personajes pertenecen a TYPE-MOON.
