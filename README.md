# Fate: Unlimited Blade Works

Mod de Fabric para Minecraft 1.21.1 con los servants de Fate/stay night UBW: armas, armaduras y Noble Phantasms.

![Capturas dentro del juego](docs/preview.png)

## Contenido

**Saber (Artoria)**
- **Excalibur** (modelo 3D): mantén click derecho 3 s y suelta para lanzar el haz de luz (48 bloques, ignora
  armadura). Mientras cargas, la hoja se vuelve dorada y la alzas con las dos manos.
  Agachado + click derecho: **Strike Air**, ráfaga de viento en cono.
- **Armadura** (coraza, faldar, escarpes) con modelo 3D de GeckoLib y falda animada.
  Conjunto completo: **Avalon** (Regeneración I) y **Resistencia Mágica** (inmune al daño mágico y al Wither).

![Saber](docs/saber.png)

**Archer (EMIYA)**
- **Kanshō y Bakuya**: click derecho los lanza y vuelven a la mano. Con uno en cada mano salen los dos y se cruzan.
  Agachado + click derecho: **Rho Aias**, escudo de siete pétalos que destruye proyectiles.
- **Arco de Archer**: dispara espadas proyectadas sin gastar flechas. Agachado y tensado a tope: **Caladbolg II**,
  que explota al impactar (Broken Phantasm).
- **Armadura** (peto, grebas, botas) con modelo 3D de GeckoLib y faldón animado.
  Conjunto completo: visión nocturna y los monstruos cercanos brillan.
- **Unlimited Blade Works** (Reality Marble): mantén 3 s y suelta. Un anillo de fuego se extiende y tú y todos los seres
  vivos a 16 bloques sois llevados a la dimensión Unlimited Blade Works: un páramo rojizo bajo un cielo de atardecer,
  con cientos de espadas clavadas y engranajes gigantes girando en el cielo. Dentro, click derecho hace surgir espadas del
  suelo que vuelan hacia el enemigo; agachado + click derecho lo deshace. A los 60 s todos vuelven a donde estaban.
  Si el servidor se cierra durante el Marble, quien siga dentro vuelve a su punto de respawn.

![Unlimited Blade Works](docs/ubw.png)

**Lancer (Cú Chulainn)**
- **Gáe Bolg**: lanza carmesí con más alcance. Mantén 1 s y suelta: **la lanza que atraviesa con la muerte**,
  embiste al objetivo que miras (12 bloques) y le atraviesa el corazón sin fallar (ignora armadura, deja Wither).
  Agachado, mantén 2 s y suelta: **la lanza que vuela con la muerte**, saltas y la lanzas; persigue al objetivo
  y estalla en un área de 6 bloques.
- **Armadura** (coraza, grebas, botas) con modelo 3D de GeckoLib y coleta animada.
  Conjunto completo: Velocidad I, **Protección contra Proyectiles** (el 75% no te hiere) y
  **Continuación de Batalla** (sobrevives a un golpe mortal, cada 5 minutos).

![Lancer](docs/lancer.png)

**Rider (Medusa)**
- **Daga de Rider** con cadena: click derecho la lanza; si engancha a un enemigo lo hiere y lo atrae, si se clava en un
  bloque te impulsa hacia él. Agachado + click derecho: **Ojos Místicos**, quien te mira de frente queda casi
  petrificado (lentitud extrema, fatiga y debilidad).
- **Bellerophon** (bridas): click derecho invoca a **Pegaso** (modelo 3D animado) y lo montas. Mira hacia arriba y avanza
  para despegar; vuela hacia donde miras. Montado, click derecho: **la embestida de Bellerophon**, un cometa de luz que
  arrolla todo a su paso. Pegaso desaparece si se queda sin jinete.
- **Armadura** de 4 piezas: Breaker Gorgon (venda), vestido con melena animada, medias y botas.
  Conjunto completo: Velocidad I y Salto II.

![Rider](docs/rider.png)

**Gilgamesh**
- **Llave de Babilonia**: click derecho abre el **Gate of Babylon**, ocho portales dorados detrás de ti que disparan una
  lluvia de armas del tesoro (prototipos de los Noble Phantasm y armas de oro, diamante y netherita) hacia lo que miras.
- **Ea**: mantén click derecho y suelta para **Enuma Elish**, un vórtice en espiral de 64 bloques que arrastra hacia su eje
  lo que pasa cerca y desgarra lo que toca (ignora armadura).
- **Armadura dorada** (coraza, grebas, escarpes) con escarcelas animadas. Conjunto completo: **Regla de Oro**
  (Suerte II y Resistencia I).

![Gilgamesh](docs/gilgamesh.png)

Todo está en la pestaña **Fate: Unlimited Blade Works** del creativo, y tiene recetas de crafteo.

## Gamerule

```
/gamerule fateAbilitiesBreakBlocks true
```

Desactivada por defecto. Activada: Excalibur y Enuma Elish abren un túnel por donde pasan, y Caladbolg II y la
Gáe Bolg lanzada explotan como TNT.
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

- `java tools/ServantAssets.java src/main/resources/assets/fate_ubw`: regenera los modelos 3D de los servants
  (ítems JSON, geo de GeckoLib) y sus texturas, incluidas las animadas de Excalibur.
- `gradlew runShowcase`: abre un cliente de desarrollo que crea un mundo, usa cada arma y habilidad,
  guarda capturas en `run-showcase/screenshots` y se cierra solo. No entra en el jar publicado.
  Con la variable de entorno `FATE_SHOWCASE=saber` (o `archer`, `lancer`, `rider`, `gilgamesh`, `ubw`, o varios separados por comas)
  solo prueba a esos servants.

---

Mod de fans sin ánimo de lucro. Fate/stay night y sus personajes pertenecen a TYPE-MOON.
