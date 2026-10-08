# Fate: Unlimited Blade Works

Mod de Fabric para Minecraft 1.21.1 con los servants de Fate/stay night UBW: armas, armaduras y Noble Phantasms.

![Capturas dentro del juego](docs/preview.png)

## Habilidades del conjunto

Las habilidades que no son de un arma salen de la ropa: con el conjunto completo de un servant puesto aparecen en la
esquina inferior derecha. **R** usa la seleccionada y **G** pasa a la siguiente (se cambian en Opciones → Controles →
Fate: Unlimited Blade Works). Cada habilidad muestra su recarga, y cada habilidad de un arma tiene la suya propia: usar
una no bloquea a las demás.

## Contenido

**Saber (Artoria)**
- **Excalibur** (modelo 3D): mantén click derecho 3 s y suelta para lanzar el haz de luz (48 bloques, ignora
  armadura). Mientras cargas, la hoja se vuelve dorada y la alzas con las dos manos.
  Agachado + click derecho: **Strike Air**, ráfaga de viento en cono.
- **Armadura** (coraza, faldar, escarpes) con modelo 3D de GeckoLib y falda animada.
  Conjunto completo: Regeneración I y **Resistencia Mágica** (inmune al daño mágico y al Wither).
  Habilidades: **Avalon** (5 s invulnerable) y **Mana Burst** (embestida que arrolla a quien esté en medio).

![Saber](docs/saber.png)

**Archer (EMIYA)**
- **Kanshō y Bakuya**: click derecho los lanza y vuelven a la mano. Con uno en cada mano salen los dos y se cruzan.
  Agachado + click derecho: **Rho Aias**, escudo de siete pétalos que destruye proyectiles.
- **Arco de Archer**: dispara espadas proyectadas sin gastar flechas. Agachado y tensado a tope: **Caladbolg II**,
  que explota al impactar (Broken Phantasm).
- **Armadura** (peto, grebas, botas) con modelo 3D de GeckoLib y faldón animado.
  Conjunto completo: visión nocturna y los monstruos cercanos brillan. Habilidades:
  - **Trace On**: agachado analiza el arma de quien miras (o la de tu mano) y la guarda en tu memoria; normal, proyecta
    una copia (encantamientos incluidos) en un hueco libre de la barra. Sin nada analizado proyecta a Kanshō y Bakuya.
    Las proyecciones se desvanecen al minuto y no se pueden meter en cofres ni usar para craftear. Si tiras una (Q),
    sale volando y estalla al chocar: **Broken Phantasm**.
  - **Unlimited Blade Works** (Reality Marble): recitas el aria 3 s y el mundo a tu alrededor se convierte en Unlimited
    Blade Works: una cúpula de 30 bloques de radio con un páramo rojizo, espadas clavadas y un cielo de atardecer con
    engranajes gigantes que tapa el mundo de fuera, así que parece no tener fin. Quien esté dentro queda atrapado contigo.
    Dentro eres más fuerte (Fuerza II, Resistencia, Velocidad, Regeneración), sobre los demás llueven espadas sin parar
    y la habilidad lanza ráfagas de 16 espadas; agachado lo deshace. No se pueden romper ni poner bloques dentro.
    A los 60 s el Marble se deshace y cada bloque vuelve a como estaba, cofres y su contenido incluidos. Si el servidor
    se cae durante el Marble, los bloques se restauran al arrancar.

![Trace On](docs/trace_on.png)

![Unlimited Blade Works](docs/ubw.png)

**Lancer (Cú Chulainn)**
- **Gáe Bolg**: lanza carmesí con más alcance. Mantén 1 s y suelta: **la lanza que atraviesa con la muerte**,
  embiste al objetivo que miras (12 bloques) y le atraviesa el corazón sin fallar (ignora armadura, deja Wither).
  Agachado, mantén 2 s y suelta: **la lanza que vuela con la muerte**, saltas y la lanzas; persigue al objetivo
  y estalla en un área de 6 bloques.
- **Armadura** (coraza, grebas, botas) con modelo 3D de GeckoLib y coleta animada.
  Conjunto completo: Velocidad I, **Protección contra Proyectiles** (el 75% no te hiere) y
  **Continuación de Batalla** (sobrevives a un golpe mortal, cada 5 minutos). Habilidad: **Runa Ansuz**, tres bolas
  de fuego en abanico.

![Lancer](docs/lancer.png)

**Rider (Medusa)**
- **Daga de Rider** con cadena: click derecho la lanza; si engancha a un enemigo lo hiere y lo atrae, si se clava en un
  bloque te impulsa hacia él.
- **Armadura** de 4 piezas: Breaker Gorgon (venda), vestido con melena animada, medias y botas.
  Conjunto completo: Velocidad I y Salto II. Habilidades:
  - **Ojos Místicos**: quien te mira de frente queda casi petrificado (lentitud extrema, fatiga y debilidad).
  - **Bellerophon**: invoca a **Pegaso** (modelo 3D animado) y lo montas. Mira hacia arriba y avanza para despegar;
    vuela hacia donde miras. Montado, la habilidad lanza **la embestida de Bellerophon**, un cometa de luz que arrolla
    todo a su paso. Pegaso desaparece si se queda sin jinete.

![Rider](docs/rider.png)

**Gilgamesh**
- **Ea**: mantén click derecho y suelta para **Enuma Elish**, un vórtice en espiral de 64 bloques que arrastra hacia su eje
  lo que pasa cerca y desgarra lo que toca (ignora armadura).
- **Armadura dorada** (coraza, grebas, escarpes) con escarcelas animadas. Conjunto completo: **Regla de Oro**
  (Suerte II y Resistencia I). Habilidades:
  - **Gate of Babylon**: ocho portales dorados detrás de ti que disparan una lluvia de armas del tesoro hacia lo que miras.
  - **Enkidu**: las Cadenas del Cielo atan a quien miras (32 bloques) y no le dejan moverse durante 5 s.

![Gilgamesh](docs/gilgamesh.png)

**Caster (Medea)**
- **Rule Breaker**: daga en zigzag de colores. Click derecho a quien tienes delante: la puñalada que rompe todo contrato
  mágico. Le quita todos los efectos, rompe la doma (el animal pasa a ser tuyo), disipa a Pegaso y deshace su Reality
  Marble.
- **Túnica** (túnica, faldones, botas) con capa animada. Conjunto completo: Regeneración I y sin daño por caída.
  Habilidades: **Palabras Divinas Rápidas** (rayos de maná a los seis enemigos más cercanos frente a ti) y
  **Transferencia Espacial** (apareces donde miras, hasta 32 bloques).

**Assassin (Sasaki Kojirō)**
- **Monohoshizao**: nodachi larguísima con más alcance. Mantén 1 s y suelta: **Tsubame Gaeshi**, tres cortes en el mismo
  instante que no se pueden esquivar.
- **Haori y hakama** con faldones y coleta animados. Conjunto completo: Velocidad I y **Ojo de la Mente** (esquiva uno de
  cada cuatro golpes cuerpo a cuerpo). Habilidad: **Ocultación de Presencia** (invisible 10 s; los monstruos te pierden).

**Berserker (Heracles)**
- **Hacha-espada**: una losa de piedra con mango. Mantén 1,5 s y suelta: **Nine Lives**, nueve golpes en un instante a todo
  lo que tengas delante.
- **Brazales, taparrabos y grebas** con faldones animados. Conjunto completo: **God Hand**, once vidas de más (resucitas
  con la vida llena y recuperas una cada 2 minutos; el HUD muestra cuántas te quedan) y los golpes de menos de 4 de daño
  no te hacen nada. Habilidad: **Locura Mejorada** (Fuerza III, Velocidad II y Resistencia durante 15 s).

![Caster, Assassin y Berserker](docs/new_servants.png)

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
  Con la variable de entorno `FATE_SHOWCASE=saber` (o `archer`, `lancer`, `rider`, `gilgamesh`, `ubw`, `trace`, `hud`,
  `caster`, `assassin`, `berserker`, o varios separados por comas) solo prueba esas secciones.

---

Mod de fans sin ánimo de lucro. Fate/stay night y sus personajes pertenecen a TYPE-MOON.
