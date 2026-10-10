import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Genera los modelos 3D de los servants y sus texturas:
 * ítems como modelos JSON con cubos, y las armaduras como geo de GeckoLib.
 * Cada cara de cada cubo recibe su propia región en la textura y se pinta con su material.
 * Uso: java tools/ServantAssets.java src/main/resources/assets/fate_ubw
 */
public class ServantAssets {
    static final int DENSITY = 2; // píxeles de textura por unidad de modelo

    // ---------- materiales ----------
    interface Paint {
        int at(int x, int y, int w, int h, int seed);
    }

    static double noise(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xffff) / 65535.0;
    }

    static int mul(int c, double f) {
        int r = (int) Math.min(255, Math.max(0, ((c >> 16) & 0xff) * f));
        int g = (int) Math.min(255, Math.max(0, ((c >> 8) & 0xff) * f));
        int b = (int) Math.min(255, Math.max(0, (c & 0xff) * f));
        return (r << 16) | (g << 8) | b;
    }

    // Borde de cada cara un poco más oscuro + ruido suave: da volumen al estilo Minecraft
    static int shade(int c, int x, int y, int w, int h, int seed, double grain) {
        double f = 1 + (noise(x, y, seed) - 0.5) * 2 * grain;
        if (w > 2 && h > 2 && (x == 0 || y == 0 || x == w - 1 || y == h - 1)) f *= 0.8;
        return mul(c, f);
    }

    // Bisel: arriba y a la izquierda la luz, abajo y a la derecha la sombra, como la ropa pintada a mano
    static int bevel(int c, int x, int y, int w, int h, int seed, double grain) {
        double f = 1 + (noise(x, y, seed) - 0.5) * 2 * grain;
        if (w > 2 && h > 2) {
            if (y == 0 || x == 0) f *= 1.2;
            else if (y == h - 1 || x == w - 1) f *= 0.68;
        }
        return mul(c, f);
    }

    // Placa de metal: degradado vertical y bisel
    static Paint plate(int c) {
        return (x, y, w, h, s) -> bevel(mul(c, 1.15 - 0.3 * y / Math.max(1, h - 1)), x, y, w, h, s, 0.035);
    }

    // Tela: pliegues verticales suaves y bisel
    static Paint fabric(int c) {
        return (x, y, w, h, s) -> bevel(mul(c, 0.9 + 0.1 * Math.sin(x * 1.3 + s)), x, y, w, h, s, 0.05);
    }

    // Una pintura con un ribete de otro color alrededor (t píxeles)
    static Paint edged(Paint inner, int edge, int t) {
        return (x, y, w, h, s) -> x < t || y < t || x >= w - t || y >= h - t ? bevel(edge, x, y, w, h, s, 0.03) : inner.at(x, y, w, h, s);
    }

    // Una pintura con dibujo encima: donde 'mark' es cierto se pinta 'color'
    interface Mark {
        boolean at(int x, int y, int w, int h);
    }

    static Paint marked(Paint base, int color, Mark mark) {
        return (x, y, w, h, s) -> mark.at(x, y, w, h) ? bevel(color, x, y, w, h, s, 0.03) : base.at(x, y, w, h, s);
    }

    static Paint solid(int c) {
        return (x, y, w, h, s) -> shade(c, x, y, w, h, s, 0.05);
    }

    static Paint metal(int c) {
        return (x, y, w, h, s) -> shade(mul(c, 1.18 - 0.36 * y / Math.max(1, h - 1)), x, y, w, h, s, 0.04);
    }

    // Caparazón de tortuga: celdas hexagonales (filas desplazadas, como un panal) de Kanshō y Bakuya
    static Paint hexes(int base, int line) {
        return (x, y, w, h, s) -> {
            int row = Math.floorMod(y, 6) / 3, cy = Math.floorMod(y, 3), cx = Math.floorMod(x + row * 2, 4);
            boolean l = cy == 0 || cx == 0;
            return shade(l ? line : mul(base, 1.0 + 0.08 * (row == 0 ? 1 : -1)), x, y, w, h, s, 0.04);
        };
    }

    // Cuero enrollado de empuñadura
    static Paint wrap(int base, int dark) {
        return (x, y, w, h, s) -> shade(Math.floorMod(y + x / 2, 2) == 0 ? dark : base, x, y, w, h, s, 0.04);
    }

    // Tela con pliegues verticales
    static Paint cloth(int base) {
        return (x, y, w, h, s) -> {
            double fold = 0.88 + 0.12 * Math.sin(x * 1.4 + s);
            return shade(mul(base, fold), x, y, w, h, s, 0.07);
        };
    }

    // Placas de armadura con juntas horizontales
    static Paint plates(int base, int line) {
        return (x, y, w, h, s) -> shade(Math.floorMod(y, 6) == 5 ? line : base, x, y, w, h, s, 0.05);
    }

    // Hoja retorcida de Caladbolg II
    static Paint spiral(int base, int dark) {
        return (x, y, w, h, s) -> shade(Math.floorMod(x + y, 3) == 0 ? dark : base, x, y, w, h, s, 0.04);
    }

    // Traje ceñido con costuras verticales (Lancer)
    static Paint seams(int base, int line) {
        return (x, y, w, h, s) -> shade(Math.floorMod(x, 5) == 4 ? line : base, x, y, w, h, s, 0.05);
    }

    static int lerp(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xff) + (((b >> 16) & 0xff) - ((a >> 16) & 0xff)) * t);
        int g = (int) Math.round(((a >> 8) & 0xff) + (((b >> 8) & 0xff) - ((a >> 8) & 0xff)) * t);
        int bl = (int) Math.round((a & 0xff) + ((b & 0xff) - (a & 0xff)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // Hoja de Excalibur. mode 0: acero con un destello que la recorre; 1: cargando, dorada;
    // 2: cargada, luz dorada que pulsa. El destello baja por la cara según el frame
    static Paint exBlade(int base, int mode) {
        return (x, y, w, h, s) -> {
            int c = mul(base, 1.12 - 0.25 * y / Math.max(1, h - 1));
            double pulse = 0.5 + 0.5 * Math.sin(2 * Math.PI * FRAME / FRAMES);
            if (mode == 1) c = lerp(c, 0xffd86b, 0.4);
            if (mode == 2) c = lerp(lerp(c, 0xffe48a, 0.7), 0xffffff, 0.35 * pulse);
            // mode 0: el frame 0 es la pausa sin destello
            double phase = mode == 0 ? (FRAME == 0 ? -1 : (FRAME - 1) / (double) Math.max(1, FRAMES - 2)) : FRAME / (double) FRAMES;
            double along = h >= w ? (double) y / Math.max(1, h - 1) : (double) x / Math.max(1, w - 1);
            if (phase >= 0 && Math.abs(along - (1 - phase)) < 0.12) c = lerp(c, 0xffffff, 0.6);
            return shade(c, x, y, w, h, s, 0.03);
        };
    }

    // Grabados dorados de la base de la hoja
    static Paint engrave(int mode) {
        return (x, y, w, h, s) -> {
            int c = Math.floorMod(x + y, 3) == 0 ? 0xf7d774 : 0xc9952b;
            if (mode == 2) c = lerp(c, 0xffffff, 0.3);
            return shade(c, x, y, w, h, s, 0.04);
        };
    }

    // Halo dorado translúcido alrededor de la hoja cargada (alfa que pulsa)
    static Paint aura() {
        return (x, y, w, h, s) -> {
            double pulse = 0.5 + 0.5 * Math.sin(2 * Math.PI * FRAME / FRAMES);
            int alpha = (int) (60 + 50 * pulse);
            return (alpha << 24) | 0xffcc33;
        };
    }

    // Asta con vetas a lo largo
    static Paint grain(int base, int dark) {
        return (x, y, w, h, s) -> shade(noise(x, y / 4, s) > 0.7 ? dark : base, x, y, w, h, s, 0.06);
    }

    // ---------- paletas ----------
    static final Paint KAN_BLADE = hexes(0x17171d, 0x9e1d1d), KAN_EDGE = metal(0x9aa0ad),
            KAN_GRIP = wrap(0x5a1414, 0x2e0909), KAN_METAL = metal(0x8c6d1f), KAN_PIP = solid(0xeeeef2);
    static final Paint BAK_BLADE = hexes(0xe9eaef, 0x5f6f86), BAK_EDGE = metal(0xf5f7fb),
            BAK_GRIP = wrap(0xd5d6dc, 0x8e8f99), BAK_METAL = metal(0xb5bac4), BAK_PIP = solid(0x17171d);
    static final Paint BOW_BODY = solid(0x17171d), BOW_TRIM = metal(0x6c707b), BOW_GRIP = wrap(0x2b2b33, 0x15151a),
            STRING = solid(0xdedede);
    static final Paint ARROW_SHAFT = solid(0x26262e), ARROW_FIN = solid(0x101015), STEEL = metal(0xc6cbd5);
    static final Paint CAL_BLADE = spiral(0xb7c2d6, 0x5f6b88), CAL_GRIP = wrap(0x1d1d24, 0x0d0d11), GOLD = metal(0xd2a537);
    static final Paint RED_CLOTH = cloth(0xa3161c), BLACK_ARMOR = plates(0x1f1f27, 0x3a3c47), SILVER = metal(0x9ea4af),
            BELT = solid(0x2f2b2b);
    static final Paint SPEAR_SHAFT = grain(0x8b0f1a, 0x5a0810), SPEAR_HEAD = metal(0xc0182a), SPEAR_EDGE = metal(0xff5560),
            SPEAR_METAL = solid(0x3a0a0e), SPEAR_GRIP = wrap(0x5a0a10, 0x2a0508);
    static final Paint BLUE_SUIT = seams(0x22337a, 0x5d78b8), LANCER_SILVER = metal(0xb8bfcc), LANCER_DARK = solid(0x141a33),
            HAIR = cloth(0x2b4cc2);
    static final Paint EX_GRIP = wrap(0x1d3f8f, 0x112a66), EX_BLUE = metal(0x2a56c6), EX_GEM = metal(0x7fd3ff);
    static final Paint BLUE_DRESS = cloth(0x2a4cb0), SABER_SILVER = metal(0xc8ced9);
    static final Paint BLACK_DRESS = seams(0x1a1520, 0x4a2a5e), DARK_PURPLE = solid(0x4a1f66), PURPLE_HAIR = cloth(0x8a4fc4),
            BLACK_BOOT = metal(0x26222c), BLINDFOLD = plates(0x2a1a36, 0x7a3fa6), DAGGER_GRIP = wrap(0x3a1f4f, 0x1c0f27),
            DARK_STEEL = metal(0x5a5f6b), LEATHER = wrap(0x8a6420, 0x5c4214);
    static final Paint WHITE_COAT = solid(0xf3f3f6), MANE = cloth(0xf0e6c8), EYE_BLUE = metal(0x5aa0ff);

    static final Paint GOLD_ARMOR = plates(0xd9a520, 0x9c6f0c), RED_TRIM = solid(0x9c1420), EA_GRIP = wrap(0x2a0d0d, 0x120505);

    // Cilindros de Ea: negro con líneas rojas que se desplazan con el frame, como si giraran.
    // mode 0: lentas; 1: cargando, rápidas y anaranjadas; 2: cargada, gruesas y brillantes
    static Paint eaSegment(int mode) {
        return (x, y, w, h, s) -> {
            int speed = mode == 0 ? 1 : 3;
            int period = mode == 2 ? 3 : 4;
            boolean line = Math.floorMod(y + x / 2 + FRAME * speed, period) == 0;
            int glow = mode == 0 ? 0xc81a14 : mode == 1 ? 0xff5a1e : 0xffb050;
            return shade(line ? glow : 0x141016, x, y, w, h, s, 0.05);
        };
    }

    // Halo rojo translúcido de Ea cargada
    static Paint redAura() {
        return (x, y, w, h, s) -> {
            double pulse = 0.5 + 0.5 * Math.sin(2 * Math.PI * FRAME / FRAMES);
            return ((int) (50 + 50 * pulse) << 24) | 0xff3020;
        };
    }

    // Plumas de las alas de Pegaso
    static Paint feathers() {
        return (x, y, w, h, s) -> shade(Math.floorMod(x + y / 3, 3) == 0 ? 0xd7dce6 : 0xffffff, x, y, w, h, s, 0.04);
    }

    // ---------- geometría ----------
    static class Face {
        final String dir;
        final int w, h, seed;
        final Paint paint;
        int u, v;

        Face(String dir, int w, int h, Paint paint, int seed) {
            this.dir = dir;
            this.w = w;
            this.h = h;
            this.paint = paint;
            this.seed = seed;
        }
    }

    static class Cube {
        double[] from, to;
        Paint paint;
        String axis;
        double angle;
        double[] origin;
        double inflate;
        final Map<String, Face> faces = new LinkedHashMap<>();

        Cube rot(String axis, double angle, double ox, double oy, double oz) {
            this.axis = axis;
            this.angle = angle;
            this.origin = new double[]{ox, oy, oz};
            return this;
        }

        Cube inflate(double amount) {
            this.inflate = amount;
            return this;
        }

        // Pintura distinta en una cara concreta (p. ej. un emblema solo delante)
        final Map<String, Paint> facePaints = new HashMap<>();

        Cube face(String dir, Paint p) {
            facePaints.put(dir, p);
            return this;
        }

        // Partes que brillan en la oscuridad (GeckoLib: textura _glowmask). 0 = no brilla
        Paint glow;

        Cube glow(Paint p) {
            this.glow = p;
            return this;
        }
    }

    static class Model {
        final List<Cube> cubes = new ArrayList<>();

        Cube box(double x1, double y1, double z1, double x2, double y2, double z2, Paint p) {
            Cube c = new Cube();
            c.from = new double[]{x1, y1, z1};
            c.to = new double[]{x2, y2, z2};
            c.paint = p;
            cubes.add(c);
            return c;
        }

        Model shifted(double dx, double dy, double dz) {
            Model m = new Model();
            for (Cube c : cubes) {
                Cube n = m.box(c.from[0] + dx, c.from[1] + dy, c.from[2] + dz, c.to[0] + dx, c.to[1] + dy, c.to[2] + dz, c.paint);
                n.inflate = c.inflate;
                if (c.axis != null) n.rot(c.axis, c.angle, c.origin[0] + dx, c.origin[1] + dy, c.origin[2] + dz);
            }
            return m;
        }

        Model add(Model other) {
            cubes.addAll(other.cubes);
            return this;
        }

        // Reflejo en X (de brazo/pierna derecha a izquierda). Las caras este y oeste se intercambian
        Model mirrored() {
            Model m = new Model();
            for (Cube c : cubes) {
                Cube n = m.box(-c.to[0], c.from[1], c.from[2], -c.from[0], c.to[1], c.to[2], c.paint);
                n.inflate = c.inflate;
                n.glow = c.glow;
                c.facePaints.forEach((dir, paint) -> n.facePaints.put(dir.equals("east") ? "west" : dir.equals("west") ? "east" : dir, paint));
                if (c.axis != null) n.rot(c.axis, c.axis.equals("x") ? c.angle : -c.angle, -c.origin[0], c.origin[1], c.origin[2]);
            }
            return m;
        }
    }

    static class Bone {
        final String name, parent;
        final double[] pivot;
        final Model model = new Model();

        Bone(String name, String parent, double... pivot) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
        }

        // El hueso del otro lado: mismo contenido reflejado en X
        Bone mirror(String name, String parent) {
            Bone b = new Bone(name, parent, -pivot[0], pivot[1], pivot[2]);
            b.model.add(model.mirrored());
            return b;
        }
    }

    // Frame que se está pintando, para materiales animados (destellos, pulsos)
    static int FRAME = 0, FRAMES = 1;

    static BufferedImage atlas(List<Cube> cubes) {
        return atlas(cubes, 1);
    }

    // Asigna a cada cara su región en la textura (empaquetado por estantes) y la pinta.
    // Con frames > 1 devuelve una tira vertical de frames para una textura animada (.mcmeta).
    // Un material puede devolver ARGB (alfa distinto de 0) para zonas translúcidas.
    static BufferedImage atlas(List<Cube> cubes, int frames) {
        List<Face> faces = new ArrayList<>();
        int seed = 0;
        for (Cube c : cubes) {
            double dx = c.to[0] - c.from[0], dy = c.to[1] - c.from[1], dz = c.to[2] - c.from[2];
            String[] dirs = {"north", "south", "east", "west", "up", "down"};
            double[][] dims = {{dx, dy}, {dx, dy}, {dz, dy}, {dz, dy}, {dx, dz}, {dx, dz}};
            for (int i = 0; i < 6; i++) {
                Face f = new Face(dirs[i], px(dims[i][0]), px(dims[i][1]), c.facePaints.getOrDefault(dirs[i], c.paint), seed++);
                c.faces.put(dirs[i], f);
                faces.add(f);
            }
        }
        List<Face> sorted = new ArrayList<>(faces);
        sorted.sort(Comparator.comparingInt((Face f) -> f.h).reversed());
        for (int size = 16; ; size *= 2) {
            if (pack(sorted, size)) {
                BufferedImage img = new BufferedImage(size, size * frames, BufferedImage.TYPE_INT_ARGB);
                FRAMES = frames;
                for (FRAME = 0; FRAME < frames; FRAME++) {
                    for (Face f : faces) {
                        for (int y = 0; y < f.h; y++) {
                            for (int x = 0; x < f.w; x++) {
                                int c = f.paint.at(x, y, f.w, f.h, f.seed);
                                img.setRGB(f.u + x, FRAME * size + f.v + y, (c >>> 24) == 0 ? 0xff000000 | c : c);
                            }
                        }
                    }
                }
                FRAME = 0;
                FRAMES = 1;
                return img;
            }
        }
    }

    static int px(double units) {
        return Math.max(1, (int) Math.round(units * DENSITY));
    }

    static boolean pack(List<Face> faces, int size) {
        int x = 0, y = 0, shelf = 0;
        for (Face f : faces) {
            if (f.w > size) return false;
            if (x + f.w > size) {
                x = 0;
                y += shelf;
                shelf = 0;
            }
            if (y + f.h > size) return false;
            f.u = x;
            f.v = y;
            x += f.w;
            shelf = Math.max(shelf, f.h);
        }
        return true;
    }

    // ---------- salida ----------
    static String n(double v) {
        if (v == Math.rint(v)) return Long.toString((long) v);
        String t = String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        return t.equals("-0") ? "0" : t;
    }

    static String arr(double... v) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) b.append(", ");
            b.append(n(v[i]));
        }
        return b.append("]").toString();
    }

    // "1,2,...,to-1" para la lista de frames de un .mcmeta
    static String range(int from, int to) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) b.append(',');
            b.append(i);
        }
        return b.toString();
    }

    static void itemModel(Path root, String name, Model model, String display, String overrides) throws IOException {
        itemModel(root, name, model, display, overrides, 1, null);
    }

    static void itemModel(Path root, String name, Model model, String display, String overrides,
                          int frames, String mcmeta) throws IOException {
        writeModel(root, "item", name, model, display, overrides, frames, mcmeta);
    }

    // Modelo de bloque con los mismos cubos (sin transformaciones de vista)
    static void blockModel(Path root, String name, Model model) throws IOException {
        Files.createDirectories(root.resolve("models/block"));
        Files.createDirectories(root.resolve("textures/block"));
        writeModel(root, "block", name, model, null, null, 1, null);
    }

    static void writeModel(Path root, String folder, String name, Model model, String display, String overrides,
                           int frames, String mcmeta) throws IOException {
        BufferedImage img = atlas(model.cubes, frames);
        if (mcmeta != null) Files.writeString(root.resolve("textures/" + folder + "/" + name + ".png.mcmeta"), mcmeta + "\n");
        double k = 16.0 / img.getWidth();
        String tex = "fate_ubw:" + folder + "/" + name;
        StringBuilder b = new StringBuilder();
        b.append(folder.equals("item") ? "{\n  \"gui_light\": \"front\",\n" : "{\n  \"ambientocclusion\": false,\n");
        b.append("  \"textures\": { \"0\": \"").append(tex).append("\", \"particle\": \"").append(tex).append("\" },\n");
        b.append("  \"elements\": [\n");
        for (int i = 0; i < model.cubes.size(); i++) {
            Cube c = model.cubes.get(i);
            double g = c.inflate;
            b.append("    { \"from\": ").append(arr(c.from[0] - g, c.from[1] - g, c.from[2] - g))
                    .append(", \"to\": ").append(arr(c.to[0] + g, c.to[1] + g, c.to[2] + g));
            if (c.axis != null) {
                // Los modelos JSON solo admiten giros de 22,5° en 22,5°: los iconos redondean al más cercano
                b.append(", \"rotation\": { \"angle\": ").append(n(Math.round(c.angle / 22.5) * 22.5)).append(", \"axis\": \"").append(c.axis)
                        .append("\", \"origin\": ").append(arr(c.origin)).append(" }");
            }
            b.append(", \"faces\": {");
            int j = 0;
            for (Face f : c.faces.values()) {
                if (j++ > 0) b.append(",");
                b.append(" \"").append(f.dir).append("\": { \"uv\": ")
                        .append(arr(f.u * k, f.v * k, (f.u + f.w) * k, (f.v + f.h) * k)).append(", \"texture\": \"#0\" }");
            }
            b.append(" } }").append(i < model.cubes.size() - 1 ? "," : "").append("\n");
        }
        b.append("  ]");
        if (display != null) b.append(",\n  \"display\": ").append(display);
        if (overrides != null) b.append(",\n  \"overrides\": ").append(overrides);
        b.append("\n}\n");
        Files.writeString(root.resolve("models/" + folder + "/" + name + ".json"), b);
        ImageIO.write(img, "png", root.resolve("textures/" + folder + "/" + name + ".png").toFile());
    }

    // Arma vanilla clavada. El dibujo va de la empuñadura (abajo a la izquierda) a la punta (arriba a la derecha):
    // girado 180° y luego 45°, la punta apunta al suelo; el centro a 8.3 deja unos 3 píxeles enterrados
    static void vanillaGrave(Path root, String item) throws IOException {
        String tex = "minecraft:item/" + item;
        // Cada cara muestra el dibujo sin reflejar y el giro se ve al revés desde cada lado: las caras sur y este llevan
        // el dibujo tal cual y las norte y oeste reflejado, así la espada queda vertical se mire por donde se mire
        String face = "{ \"uv\": [0, 0, 16, 16], \"rotation\": 180, \"texture\": \"#0\" }";
        String back = "{ \"uv\": [16, 0, 0, 16], \"rotation\": 180, \"texture\": \"#0\" }";
        String json = "{\n  \"ambientocclusion\": false,\n"
                + "  \"textures\": { \"0\": \"" + tex + "\", \"particle\": \"" + tex + "\" },\n"
                + "  \"elements\": [\n"
                + "    { \"from\": [0, 0.3, 8], \"to\": [16, 16.3, 8], \"shade\": false,"
                + " \"rotation\": { \"angle\": 45, \"axis\": \"z\", \"origin\": [8, 8.3, 8] },"
                + " \"faces\": { \"north\": " + back + ", \"south\": " + face + " } },\n"
                + "    { \"from\": [8, 0.3, 0], \"to\": [8, 16.3, 16], \"shade\": false,"
                + " \"rotation\": { \"angle\": 45, \"axis\": \"x\", \"origin\": [8, 8.3, 8] },"
                + " \"faces\": { \"east\": " + face + ", \"west\": " + back + " } }\n"
                + "  ]\n}\n";
        Files.writeString(root.resolve("models/block/ubw_sword_" + item + ".json"), json);
    }

    // Da la vuelta a un arma (punta abajo) y la hunde 'bury' unidades en el suelo: una espada clavada
    static Model buried(Model m, double top, double bury) {
        Model out = new Model();
        for (Cube c : m.cubes) {
            Cube n = out.box(c.from[0], top - c.to[1] - bury, c.from[2], c.to[0], top - c.from[1] - bury, c.to[2], c.paint);
            n.inflate = c.inflate;
            if (c.axis != null) {
                double angle = c.axis.equals("y") ? c.angle : -c.angle; // al reflejar en Y, los giros en X/Z cambian de sentido
                n.rot(c.axis, angle, c.origin[0], top - c.origin[1] - bury, c.origin[2]);
            }
        }
        return out;
    }

    // Unlimited Blade Works: espada de hoja gris con runas rojas que laten
    static Paint runes() {
        return (x, y, w, h, s) -> {
            double pulse = 0.5 + 0.5 * Math.sin(2 * Math.PI * FRAME / FRAMES + y * 0.3);
            boolean rune = Math.floorMod(x * 3 + y, 5) == 0;
            int c = rune ? lerp(0x7a1010, 0xff4020, pulse) : mul(0xb8bcc6, 1.1 - 0.25 * y / Math.max(1, h - 1));
            return shade(c, x, y, w, h, s, 0.04);
        };
    }

    static Model ubwSword() {
        Model m = new Model();
        m.box(7.4, -0.5, 7.4, 8.6, 0.5, 8.6, solid(0x7a1010));     // pomo
        m.box(7.5, 0.5, 7.5, 8.5, 4.5, 8.5, wrap(0x1a1214, 0x0a0607));
        m.box(6.3, 4.5, 7.4, 9.7, 5.3, 8.6, solid(0x7a1010));      // guarda
        Paint blade = runes();
        m.box(7.2, 5.3, 7.65, 8.8, 20, 8.35, blade);
        m.box(7.6, 20, 7.75, 8.4, 22, 8.25, blade);
        return m;
    }

    // Trace On: contorno azul brillante que late a lo largo de la hoja
    static Paint traceLine() {
        return (x, y, w, h, s) -> 0xff000000 | lerp(0x1f6fd6, 0xc8f6ff, 0.5 + 0.5 * Math.sin(2 * Math.PI * FRAME / FRAMES - y * 0.35));
    }

    // Relleno casi transparente con una franja de escaneo que recorre la hoja
    static Paint traceFill() {
        return (x, y, w, h, s) -> {
            double scan = Math.abs((double) y / Math.max(1, h - 1) - (1.0 - (double) FRAME / FRAMES));
            return ((scan < 0.12 ? 150 : 40) << 24) | 0x6fd8ff;
        };
    }

    // Una espada a medio proyectar: solo las aristas y un relleno translúcido
    static Model traceOn() {
        Model m = new Model();
        Paint line = traceLine(), fill = traceFill();
        m.box(7.4, -0.5, 7.4, 8.6, 0.5, 8.6, line);                 // pomo
        m.box(7.5, 0.5, 7.85, 7.75, 4.5, 8.15, line);               // empuñadura
        m.box(8.25, 0.5, 7.85, 8.5, 4.5, 8.15, line);
        m.box(7.75, 0.5, 7.95, 8.25, 4.5, 8.05, fill);
        m.box(6.3, 4.5, 7.7, 9.7, 5.0, 8.3, line);                  // guarda
        m.box(7.1, 5.0, 7.85, 7.35, 19.5, 8.15, line);              // filos
        m.box(8.65, 5.0, 7.85, 8.9, 19.5, 8.15, line);
        m.box(7.35, 5.0, 7.95, 8.65, 19.5, 8.05, fill);
        m.box(7.35, 19.5, 7.85, 7.75, 20.5, 8.15, line);            // punta
        m.box(8.25, 19.5, 7.85, 8.65, 20.5, 8.15, line);
        m.box(7.75, 19.5, 7.95, 8.25, 20.5, 8.05, fill);
        m.box(7.75, 20.5, 7.85, 8.25, 22, 8.15, line);
        return m;
    }

    // ---------- Caster, Assassin, Berserker ----------
    static Paint stone(int base, int dark) {
        return (x, y, w, h, s) -> shade(noise(x / 2, y / 2, s) > 0.62 ? dark : base, x, y, w, h, s, 0.09);
    }

    static final Paint RB_GOLD = metal(0xd9b84a), RB_GRIP = wrap(0x3a1f5c, 0x231236);
    static final Paint ROBE = cloth(0x4b2a7a), ROBE_DARK = solid(0x231536), ROBE_GOLD = metal(0xc9a43c);
    static final Paint HAORI = cloth(0x5a3d8a), KIMONO = cloth(0x2d2f6b), HAKAMA = cloth(0x1f2350), OBI = solid(0xd8c9a0),
            TABI = solid(0xeeeeee), ZORI = solid(0xa08050), KOJIRO_HAIR = cloth(0x4b3a8f);
    static final Paint KATANA_GRIP = wrap(0x1b1b30, 0xd8d0b0), TSUBA = metal(0x3a3a40), KATANA = metal(0xd8dee8), HAMON = metal(0xf6f8fc);
    static final Paint SKIN = solid(0x3c3d42), SKIN_DARK = solid(0x2b2c30), BRONZE = metal(0x7a5530), LOINCLOTH = cloth(0x3b2a1e),
            GREAVE = plates(0x2e2f33, 0x4a4c52);
    static final Paint AXE_STONE = stone(0x6a6b70, 0x45464b), AXE_GRIP = wrap(0x3a2a1c, 0x1f150d);

    // Rule Breaker: daga en zigzag con la hoja de colores
    static Model ruleBreaker() {
        Model m = new Model();
        m.box(7.4, -0.5, 7.4, 8.6, 0.5, 8.6, RB_GOLD);
        m.box(7.5, 0.5, 7.5, 8.5, 4.5, 8.5, RB_GRIP);
        m.box(6.4, 4.5, 7.5, 9.6, 5.3, 8.5, RB_GOLD);
        double[] left = {7.2, 7.9, 6.9, 7.8, 7.2};
        int[] colors = {0xb84de0, 0xf06fb4, 0xf5d74a, 0x5ad6a6, 0x5aa8f0};
        for (int i = 0; i < 5; i++) {
            double y0 = 5.3 + i * 2.0;
            m.box(left[i], y0, 7.7, left[i] + 1.4, y0 + 2.1, 8.3, metal(colors[i])).glow(solid(colors[i]));
        }
        m.box(7.6, 15.3, 7.8, 8.3, 17, 8.2, metal(0x5aa8f0));
        return m;
    }

    // Monohoshizao: nodachi de hoja larguísima, ligeramente curvada
    static Model monohoshizao() {
        Model m = new Model();
        m.box(7.5, -1, 7.6, 8.5, 0, 8.4, TSUBA);                  // kashira
        m.box(7.6, 0, 7.7, 8.4, 7, 8.3, KATANA_GRIP);             // tsuka larga
        m.box(6.6, 7, 7.3, 9.4, 7.6, 8.7, TSUBA);                 // tsuba
        m.box(7.3, 6.8, 6.6, 8.7, 7.8, 9.4, TSUBA);
        m.box(7.5, 7.6, 7.75, 8.5, 8.6, 8.25, RB_GOLD);           // habaki
        double[][] blade = {{7.55, 8.6, 15}, {7.65, 15, 22}, {7.8, 22, 28.5}};
        for (double[] b : blade) {
            m.box(b[0], b[1], 7.85, b[0] + 0.75, b[2], 8.15, KATANA);
            m.box(b[0] + 0.75, b[1], 7.88, b[0] + 0.95, b[2], 8.12, HAMON);   // filo
        }
        m.box(8.0, 28.5, 7.87, 8.7, 30.3, 8.13, KATANA);
        m.box(8.3, 30.3, 7.9, 8.65, 31, 8.1, HAMON);
        return m;
    }

    // La hacha-espada de Heracles: una losa de piedra tosca con mango
    static Model axeSword() {
        Model m = new Model();
        m.box(7.3, -1, 7.3, 8.7, 0, 8.7, AXE_STONE);
        m.box(7.4, 0, 7.4, 8.6, 5, 8.6, AXE_GRIP);
        m.box(5.5, 5, 7.1, 10.5, 9, 8.9, AXE_STONE);
        m.box(4.5, 9, 7.2, 11.5, 20, 8.8, AXE_STONE);
        m.box(3.8, 12, 7.3, 4.5, 17, 8.7, AXE_STONE);              // filo irregular
        m.box(11.5, 10, 7.3, 12.2, 15, 8.7, AXE_STONE);
        m.box(5, 20, 7.25, 11, 24, 8.75, AXE_STONE);
        m.box(6, 24, 7.35, 10, 26, 8.65, AXE_STONE);
        m.box(7, 26, 7.45, 9.2, 27, 8.55, AXE_STONE);
        return m;
    }

    // ---------- Guerra del Santo Grial, Noble Phantasm nuevos y objetos de los Masters ----------
    static final Paint GRAIL_GOLD = metal(0xe2b13c), GRAIL_RIM = metal(0xffe08a), RUBY = metal(0xd0162a);

    // Luz dorada que late dentro de la copa
    static Paint grailLight() {
        return (x, y, w, h, s) -> lerp(0xffd86b, 0xffffff, 0.5 + 0.5 * Math.sin(2 * Math.PI * FRAME / FRAMES)) | 0xff000000;
    }

    static Model holyGrail() {
        Model m = new Model();
        m.box(5, 0, 5, 11, 0.8, 11, GRAIL_GOLD);               // pie
        m.box(6.5, 0.8, 6.5, 9.5, 1.6, 9.5, GRAIL_GOLD);
        m.box(7.4, 1.6, 7.4, 8.6, 5, 8.6, GRAIL_GOLD);         // tallo
        m.box(6.8, 3, 6.8, 9.2, 3.8, 9.2, RUBY);               // nudo con rubíes
        m.box(6.2, 5, 6.2, 9.8, 6, 9.8, GRAIL_GOLD);           // copa
        m.box(5.2, 6, 5.2, 10.8, 8, 10.8, GRAIL_GOLD);
        m.box(4.6, 8, 4.6, 11.4, 10, 11.4, GRAIL_GOLD);
        m.box(4.4, 10, 4.4, 11.6, 10.6, 11.6, GRAIL_RIM);      // borde
        m.box(5.0, 9.6, 5.0, 11.0, 10.4, 11.0, grailLight());  // la luz que llena la copa
        return m;
    }

    // Pergamino con el círculo de invocación: anillo rojo y pentagrama
    static Paint summoningPaint() {
        return (x, y, w, h, s) -> {
            if (w < 6 || h < 6) return shade(0xd8c8a0, x, y, w, h, s, 0.05);
            double u = (x + 0.5) / w * 2 - 1, v = (y + 0.5) / h * 2 - 1, r = Math.sqrt(u * u + v * v);
            boolean ink = Math.abs(r - 0.82) < 0.09;
            for (int p = 0; p < 5 && !ink; p++) {
                double a0 = p * Math.PI * 2 / 5 - Math.PI / 2, a1 = (p + 2) * Math.PI * 2 / 5 - Math.PI / 2;
                double ax = Math.cos(a0) * 0.75, ay = Math.sin(a0) * 0.75, bx = Math.cos(a1) * 0.75, by = Math.sin(a1) * 0.75;
                double t = Math.max(0, Math.min(1, ((u - ax) * (bx - ax) + (v - ay) * (by - ay)) / ((bx - ax) * (bx - ax) + (by - ay) * (by - ay))));
                double dx = u - (ax + t * (bx - ax)), dy = v - (ay + t * (by - ay));
                ink = Math.sqrt(dx * dx + dy * dy) < 0.07;
            }
            return shade(ink ? 0xb3121c : 0xe8dcc0, x, y, w, h, s, 0.04);
        };
    }

    static Model summoningCircle() {
        Model m = new Model();
        m.box(1, 0, 1, 15, 0.4, 15, summoningPaint());
        return m;
    }

    static final String FLAT_ICON = "{\n"
            + "    \"thirdperson_righthand\": { \"rotation\": [0, 0, 0], \"translation\": [0, 3, 1], \"scale\": [0.5, 0.5, 0.5] },\n"
            + "    \"thirdperson_lefthand\": { \"rotation\": [0, 0, 0], \"translation\": [0, 3, 1], \"scale\": [0.5, 0.5, 0.5] },\n"
            + "    \"firstperson_righthand\": { \"rotation\": [-60, 0, 0], \"translation\": [1.13, 3.2, 1.13], \"scale\": [0.5, 0.5, 0.5] },\n"
            + "    \"firstperson_lefthand\": { \"rotation\": [-60, 0, 0], \"translation\": [1.13, 3.2, 1.13], \"scale\": [0.5, 0.5, 0.5] },\n"
            + "    \"gui\": { \"rotation\": [90, 0, 0], \"scale\": [1, 1, 1] },\n"
            + "    \"ground\": { \"translation\": [0, 2, 0], \"scale\": [0.5, 0.5, 0.5] },\n"
            + "    \"fixed\": { \"rotation\": [90, 0, 0], \"scale\": [1, 1, 1] }\n"
            + "  }";

    // Hrunting: la espada-flecha roja y negra que persigue a su presa
    static final Paint HRUNT_RED = metal(0xb01024), HRUNT_DARK = solid(0x1a0a0e);

    static Model hrunting(double b) {
        Model m = new Model();
        m.box(7.6, b, 7.6, 8.4, b + 4, 8.4, wrap(0x1a0a0e, 0x3a0a12));
        m.box(6.8, b + 4, 7.5, 9.2, b + 4.8, 8.5, HRUNT_RED);
        for (int i = 0; i < 5; i++) {
            double y0 = b + 4.8 + i * 2.4;
            Cube seg = m.box(7.3, y0, 7.75, 8.7, y0 + 2.4, 8.25, i % 2 == 0 ? HRUNT_RED : HRUNT_DARK);
            if (i % 2 == 0) seg.glow((x, y, w, h, s) -> Math.floorMod(y, 3) == 0 ? 0xff2a40 : 0);
            m.box(6.6, y0 + 0.4, 7.85, 7.3, y0 + 1.2, 8.15, HRUNT_RED).glow(solid(0xff3050));        // púas
            m.box(8.7, y0 + 1.2, 7.85, 9.4, y0 + 2.0, 8.15, HRUNT_RED).glow(solid(0xff3050));
        }
        m.box(7.6, b + 16.8, 7.8, 8.4, b + 18.6, 8.2, HRUNT_RED).glow(solid(0xff3050));
        m.box(7.85, b + 18.6, 7.85, 8.15, b + 19.6, 8.15, HRUNT_RED).glow(solid(0xff6070));
        return m;
    }

    // Joya de Tohsaka: un rubí tallado que guarda maná
    static Model rinJewel() {
        Model m = new Model();
        m.box(6.5, 6.5, 6.5, 9.5, 9.5, 9.5, RUBY).rot("y", 45, 8, 8, 8);
        m.box(7, 9.5, 7, 9, 10.5, 9, metal(0xff5a6a)).rot("y", 45, 8, 8, 8);
        m.box(7, 5.5, 7, 9, 6.5, 9, metal(0x8a0a18)).rot("y", 45, 8, 8, 8);
        return m;
    }

    // El póster enrollado que Shirou refuerza para pelear
    static Paint posterPaint() {
        return (x, y, w, h, s) -> {
            int[] bands = {0xf2ead2, 0x3d7fd6, 0xf2ead2, 0xe0b23a, 0xf2ead2, 0xd03a3a};
            return shade(bands[Math.floorMod(y / 2, bands.length)], x, y, w, h, s, 0.05);
        };
    }

    static Model shirouPoster() {
        Model m = new Model();
        m.box(7.1, 0, 7.1, 8.9, 18, 8.9, posterPaint());
        m.box(6.9, 0, 6.9, 9.1, 1, 9.1, solid(0xd8ccb0));
        m.box(6.9, 17, 6.9, 9.1, 18, 9.1, solid(0xd8ccb0));
        return m;
    }

    // Zelzeriz: un pájaro de alambre de plata de Illya
    static final Paint WIRE = metal(0xe6ebf2);

    static Model zelzeriz() {
        Model m = new Model();
        m.box(6.5, 7, 7, 10.5, 9, 9, WIRE);                                      // cuerpo
        m.box(10.5, 8, 7.5, 12, 9.5, 8.5, WIRE);                                 // cabeza
        m.box(12, 8.5, 7.8, 13, 9, 8.2, metal(0xd08a3a));                        // pico
        m.box(7, 8.6, 9, 10, 9, 13, WIRE).rot("x", 22.5, 8.5, 8.8, 9);           // alas
        m.box(7, 8.6, 3, 10, 9, 7, WIRE).rot("x", -22.5, 8.5, 8.8, 7);
        m.box(4.5, 7.6, 7.6, 6.5, 8.4, 8.4, WIRE);                               // cola
        return m;
    }

    // Piel de jugador 64x64 para los servants enemigos (casi todo lo tapa su ropa)
    static void skin(Path root, String name, int skin, int hair, int eye) throws IOException {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 16; y < 64; y++) for (int x = 0; x < 64; x++) img.setRGB(x, y, 0xff000000 | mul(skin, 0.95 + 0.05 * ((x + y) % 2)));
        for (int y = 0; y < 16; y++) for (int x = 0; x < 32; x++) img.setRGB(x, y, 0xff000000 | (y < 8 || y < 11 && x != 9 && x != 14 ? hair : skin));
        for (int x = 8; x < 16; x++) for (int y = 8; y < 16; y++) img.setRGB(x, y, 0xff000000 | (y < 10 ? hair : skin)); // cara
        for (int x = 24; x < 32; x++) for (int y = 8; y < 16; y++) img.setRGB(x, y, 0xff000000 | hair);              // nuca
        for (int[] e : new int[][]{{9, 12}, {14, 12}}) {
            img.setRGB(e[0], e[1], 0xffffffff);
            img.setRGB(e[0] == 9 ? 10 : 13, e[1], 0xff000000 | eye);
        }
        img.setRGB(11, 14, 0xff000000 | mul(skin, 0.7));
        img.setRGB(12, 14, 0xff000000 | mul(skin, 0.7));
        Path out = root.resolve("textures/entity/servant/" + name + ".png");
        Files.createDirectories(out.getParent());
        javax.imageio.ImageIO.write(img, "png", out.toFile());
    }

    static void geoModel(Path root, String name, List<Bone> bones) throws IOException {
        // Armaduras en item/armor/, entidades (Pegaso) en entity/: las rutas que espera GeckoLib
        geoModel(root, name, bones, name.endsWith("_armor") ? "item/armor/" : "entity/");
    }

    // Misma textura que el atlas, pero solo con los píxeles de las pintas "glow" (el resto transparente)
    static BufferedImage glowmask(List<Cube> cubes, BufferedImage base) {
        BufferedImage img = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (Cube c : cubes) {
            if (c.glow == null) continue;
            for (Face f : c.faces.values()) {
                for (int y = 0; y < f.h; y++) {
                    for (int x = 0; x < f.w; x++) {
                        int px = c.glow.at(x, y, f.w, f.h, f.seed);
                        if (px != 0) img.setRGB(f.u + x, f.v + y, (px >>> 24) == 0 ? 0xff000000 | px : px);
                    }
                }
            }
        }
        return img;
    }

    // Textura (y _glowmask) de unos huesos. Con los mismos cubos sale la misma distribución: sirve para variantes
    static BufferedImage geoTexture(Path root, String kind, String name, List<Bone> bones) throws IOException {
        List<Cube> all = new ArrayList<>();
        for (Bone bone : bones) all.addAll(bone.model.cubes);
        BufferedImage img = atlas(all);
        Path tex = root.resolve("textures/" + kind + name + ".png");
        Files.createDirectories(tex.getParent());
        ImageIO.write(img, "png", tex.toFile());
        if (all.stream().anyMatch(c -> c.glow != null)) {
            ImageIO.write(glowmask(all, img), "png", root.resolve("textures/" + kind + name + "_glowmask.png").toFile());
        }
        return img;
    }

    static String uuid(String seed) {
        return java.util.UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    // Proyecto de Blockbench (formato Bedrock) con la textura dentro, para retocar la armadura a mano.
    // Blockbench guarda el Bedrock con X invertido y los giros en X e Y cambiados de signo
    static void bbmodel(Path file, String name, List<Bone> bones, BufferedImage img) throws IOException {
        StringBuilder els = new StringBuilder();
        Map<String, StringBuilder> children = new LinkedHashMap<>();
        for (Bone bone : bones) children.put(bone.name, new StringBuilder());
        int count = 0;
        for (Bone bone : bones) {
            for (int j = 0; j < bone.model.cubes.size(); j++) {
                Cube c = bone.model.cubes.get(j);
                String id = uuid(name + "/" + bone.name + "/" + j);
                double rx = "x".equals(c.axis) ? -c.angle : 0, ry = "y".equals(c.axis) ? -c.angle : 0, rz = "z".equals(c.axis) ? c.angle : 0;
                double[] o = c.axis != null ? new double[]{-c.origin[0], c.origin[1], c.origin[2]} : new double[]{0, 0, 0};
                if (count++ > 0) els.append(",\n");
                els.append("    { \"name\": \"").append(bone.name).append("\", \"type\": \"cube\", \"uuid\": \"").append(id)
                        .append("\", \"box_uv\": false, \"from\": ").append(arr(-c.to[0], c.from[1], c.from[2]))
                        .append(", \"to\": ").append(arr(-c.from[0], c.to[1], c.to[2]))
                        .append(", \"inflate\": ").append(n(c.inflate)).append(", \"origin\": ").append(arr(o))
                        .append(", \"rotation\": ").append(arr(rx, ry, rz)).append(", \"faces\": {");
                int k = 0;
                for (Face f : c.faces.values()) {
                    boolean flip = f.dir.equals("up") || f.dir.equals("down");
                    double[] uv = flip ? new double[]{f.u + f.w, f.v + f.h, f.u, f.v} : new double[]{f.u, f.v, f.u + f.w, f.v + f.h};
                    if (k++ > 0) els.append(",");
                    els.append(" \"").append(f.dir).append("\": { \"uv\": ").append(arr(uv)).append(", \"texture\": 0 }");
                }
                els.append(" } }");
                StringBuilder kids = children.get(bone.name);
                if (kids.length() > 0) kids.append(", ");
                kids.append("\"").append(id).append("\"");
            }
        }
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        StringBuilder b = new StringBuilder("{\n  \"meta\": { \"format_version\": \"4.10\", \"model_format\": \"bedrock\", \"box_uv\": false },\n");
        b.append("  \"name\": \"").append(name).append("\", \"model_identifier\": \"").append(name).append("\", \"visible_box\": [3, 3, 1.5],\n");
        b.append("  \"resolution\": { \"width\": ").append(img.getWidth()).append(", \"height\": ").append(img.getHeight()).append(" },\n");
        b.append("  \"elements\": [\n").append(els).append("\n  ],\n  \"outliner\": [\n");
        int r = 0;
        for (Bone bone : bones) {
            if (bone.parent != null) continue;
            if (r++ > 0) b.append(",\n");
            b.append("    ").append(group(name, bone, bones, children));
        }
        b.append("\n  ],\n  \"textures\": [ { \"name\": \"").append(name).append(".png\", \"id\": \"0\", \"uuid\": \"").append(uuid(name + "/texture"))
                .append("\", \"width\": ").append(img.getWidth()).append(", \"height\": ").append(img.getHeight())
                .append(", \"uv_width\": ").append(img.getWidth()).append(", \"uv_height\": ").append(img.getHeight())
                .append(", \"source\": \"data:image/png;base64,").append(java.util.Base64.getEncoder().encodeToString(png.toByteArray()))
                .append("\" } ]\n}\n");
        Files.writeString(file, b);
    }

    static String group(String name, Bone bone, List<Bone> bones, Map<String, StringBuilder> children) {
        StringBuilder kids = new StringBuilder(children.get(bone.name));
        for (Bone child : bones) {
            if (!bone.name.equals(child.parent)) continue;
            if (kids.length() > 0) kids.append(", ");
            kids.append(group(name, child, bones, children));
        }
        return "{ \"name\": \"" + bone.name + "\", \"uuid\": \"" + uuid(name + "/group/" + bone.name) + "\", \"origin\": "
                + arr(-bone.pivot[0], bone.pivot[1], bone.pivot[2]) + ", \"rotation\": [0, 0, 0], \"isOpen\": true, \"children\": [" + kids + "] }";
    }

    // Huellas de lo último que escribió el generador. Si un archivo ya no coincide, lo ha retocado alguien a mano
    static final Path HASHES = Path.of("tools/generated-hashes.txt");
    static final Map<String, String> hashes = new java.util.TreeMap<>();

    static String sha(Path file) throws IOException {
        try {
            // Los de texto sin \r: git puede cambiar los finales de línea al sacar los archivos
            byte[] bytes = file.toString().endsWith(".png") ? Files.readAllBytes(file)
                    : Files.readString(file).replace("\r", "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(d);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    static boolean editedByHand(Path... files) throws IOException {
        if (hashes.isEmpty() && Files.exists(HASHES)) {
            for (String line : Files.readAllLines(HASHES)) {
                String[] kv = line.split(" ", 2);
                if (kv.length == 2) hashes.put(kv[1], kv[0]);
            }
        }
        for (Path f : files) {
            String known = hashes.get(f.toString().replace('\\', '/'));
            if (known != null && Files.exists(f) && !known.equals(sha(f))) return true;
        }
        return false;
    }

    static void remember(Path... files) throws IOException {
        for (Path f : files) hashes.put(f.toString().replace('\\', '/'), sha(f));
        StringBuilder b = new StringBuilder();
        hashes.forEach((file, hash) -> b.append(hash).append(' ').append(file).append('\n'));
        Files.writeString(HASHES, b);
    }

    // Armadura: geo + textura + proyecto de Blockbench. Si alguien la ha retocado a mano, no se toca
    static void armorModel(Path root, String name, List<Bone> bones) throws IOException {
        Path geo = root.resolve("geo/item/armor/" + name + ".geo.json");
        Path tex = root.resolve("textures/item/armor/" + name + ".png");
        Path bb = root.resolve("geo/item/armor/" + name + ".geo.bbmodel");
        if (editedByHand(geo, tex, bb)) {
            System.out.println(name + ": retocada a mano, no se regenera");
            return;
        }
        geoModel(root, name, bones, "item/armor/");
        bbmodel(bb, name, bones, ImageIO.read(tex.toFile()));
        remember(geo, tex, bb);
    }

    static void geoModel(Path root, String name, List<Bone> bones, String kind) throws IOException {
        BufferedImage img = geoTexture(root, kind, name, bones);
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"format_version\": \"1.12.0\",\n  \"minecraft:geometry\": [{\n");
        b.append("    \"description\": { \"identifier\": \"geometry.").append(name).append("\", \"texture_width\": ").append(img.getWidth())
                .append(", \"texture_height\": ").append(img.getHeight())
                .append(", \"visible_bounds_width\": 3, \"visible_bounds_height\": 3, \"visible_bounds_offset\": [0, 1.5, 0] },\n");
        b.append("    \"bones\": [\n");
        for (int i = 0; i < bones.size(); i++) {
            Bone bone = bones.get(i);
            b.append("      { \"name\": \"").append(bone.name).append("\"");
            if (bone.parent != null) b.append(", \"parent\": \"").append(bone.parent).append("\"");
            b.append(", \"pivot\": ").append(arr(bone.pivot)).append(", \"cubes\": [");
            for (int j = 0; j < bone.model.cubes.size(); j++) {
                Cube c = bone.model.cubes.get(j);
                b.append(j > 0 ? ",\n" : "\n").append("        { \"origin\": ").append(arr(c.from))
                        .append(", \"size\": ").append(arr(c.to[0] - c.from[0], c.to[1] - c.from[1], c.to[2] - c.from[2]));
                if (c.inflate != 0) b.append(", \"inflate\": ").append(n(c.inflate));
                if (c.axis != null) {
                    double rx = c.axis.equals("x") ? c.angle : 0, ry = c.axis.equals("y") ? c.angle : 0, rz = c.axis.equals("z") ? c.angle : 0;
                    b.append(", \"pivot\": ").append(arr(c.origin)).append(", \"rotation\": ").append(arr(rx, ry, rz));
                }
                b.append(", \"uv\": {");
                int k = 0;
                for (Face f : c.faces.values()) {
                    if (k++ > 0) b.append(",");
                    b.append(" \"").append(f.dir).append("\": { \"uv\": ").append(arr(f.u, f.v))
                            .append(", \"uv_size\": ").append(arr(f.w, f.h)).append(" }");
                }
                b.append(" } }");
            }
            b.append(bone.model.cubes.isEmpty() ? "] }" : "\n      ] }").append(i < bones.size() - 1 ? "," : "").append("\n");
        }
        b.append("    ]\n  }]\n}\n");
        Path geo = root.resolve("geo/" + kind + name + ".geo.json");
        Files.createDirectories(geo.getParent());
        Files.writeString(geo, b);
    }

    // Ítem dibujado por GeckoLib: el JSON solo da las posiciones en mano (y los overrides, si los hay)
    static void builtinModel(Path root, String name, String texture, String display, String overrides) throws IOException {
        Files.writeString(root.resolve("models/item/" + name + ".json"), "{\n  \"parent\": \"builtin/entity\",\n  \"textures\": { \"particle\": \"fate_ubw:item/"
                + texture + "\" },\n  \"display\": " + display + (overrides != null ? ",\n  \"overrides\": " + overrides : "") + "\n}\n");
    }

    // Un modelo de ítem de una pieza como geo de GeckoLib (coordenadas del JSON menos 8)
    static List<Bone> oneBone(Model m) {
        Bone root = new Bone("root", null, 0, 0, 0);
        for (Cube c : m.cubes) {
            Cube n = root.model.box(c.from[0] - 8, c.from[1] - 8, c.from[2] - 8, c.to[0] - 8, c.to[1] - 8, c.to[2] - 8, c.paint);
            n.inflate = c.inflate;
            n.glow = c.glow;
            if (c.axis != null) n.rot(c.axis, c.angle, c.origin[0] - 8, c.origin[1] - 8, c.origin[2] - 8);
        }
        return List.of(root);
    }

    // Ea para GeckoLib: empuñadura fija y tres cilindros (y la punta) que giran cada uno a su ritmo.
    // Coordenadas de GeckoLib = las del modelo JSON menos (8, 8, 8); las líneas rojas brillan en la oscuridad
    static Paint eaGlow() {
        return (x, y, w, h, s) -> Math.floorMod(y + x / 2, 4) == 0 ? 0xff3a2a : 0;
    }

    static List<Bone> eaBones() {
        Bone handle = new Bone("handle", null, 0, 0, 0);
        handle.model.box(-0.7, -9, -0.7, 0.7, -7.5, 0.7, GOLD);
        handle.model.box(-0.5, -7.5, -0.5, 0.5, -3.5, 0.5, EA_GRIP);
        handle.model.box(-1.8, -3.5, -1.8, 1.8, -2.5, 1.8, GOLD);
        handle.model.box(-1.8, -3.5, -1.8, 1.8, -2.5, 1.8, GOLD).rot("y", 45, 0, -3, 0);
        handle.model.box(-1.2, 3, -1.2, 1.2, 3.2, 1.2, GOLD);       // anillos entre cilindros
        handle.model.box(-1.0, 8.5, -1.0, 1.0, 8.7, 1.0, GOLD);
        Paint seg = eaSegment(0);
        double[][] segments = {{1.3, -2.5, 3}, {1.1, 3.2, 8.5}, {0.9, 8.7, 13.5}, {0.5, 13.5, 16}};
        String[] names = {"cylinder1", "cylinder2", "cylinder3", "tip"};
        List<Bone> bones = new ArrayList<>(List.of(handle));
        for (int i = 0; i < segments.length; i++) {
            double h = segments[i][0];
            Bone bone = new Bone(names[i], "handle", 0, segments[i][1], 0);
            bone.model.box(-h, segments[i][1], -h, h, segments[i][2], h, seg).glow(eaGlow());
            bone.model.box(-h, segments[i][1], -h, h, segments[i][2], h, seg).rot("y", 45, 0, segments[i][1], 0).glow(eaGlow());
            bones.add(bone);
        }
        return bones;
    }

    // ---------- transformaciones de vista ----------
    // Los modelos se construyen en vertical (+Y). Las transformaciones son las de los sprites vanilla
    // con -45° extra en Z, así la espada queda en la diagonal de siempre en mano e inventario.
    static String handheld(double guiScale) {
        return handheld(guiScale, 0.68);
    }

    static String handheld(double guiScale, double firstPersonScale) {
        String fp = arr(firstPersonScale, firstPersonScale, firstPersonScale);
        return "{\n"
                + "    \"thirdperson_righthand\": { \"rotation\": [0, -90, 10], \"translation\": [0, 4, 0.5], \"scale\": [0.85, 0.85, 0.85] },\n"
                + "    \"thirdperson_lefthand\": { \"rotation\": [0, 90, -10], \"translation\": [0, 4, 0.5], \"scale\": [0.85, 0.85, 0.85] },\n"
                + "    \"firstperson_righthand\": { \"rotation\": [0, -90, -20], \"translation\": [1.13, 3.2, 1.13], \"scale\": " + fp + " },\n"
                + "    \"firstperson_lefthand\": { \"rotation\": [0, 90, 20], \"translation\": [1.13, 3.2, 1.13], \"scale\": " + fp + " },\n"
                + "    \"gui\": { \"rotation\": [0, 0, -45], \"translation\": [-1, -1, 0], \"scale\": " + arr(guiScale, guiScale, guiScale) + " },\n"
                + "    \"ground\": { \"rotation\": [0, 0, -45], \"translation\": [0, 2, 0], \"scale\": [0.5, 0.5, 0.5] },\n"
                + "    \"fixed\": { \"rotation\": [0, 180, -45], \"scale\": [0.8, 0.8, 0.8] }\n"
                + "  }";
    }

    // El arco se construye en horizontal con la flecha hacia +Y. En el sprite vanilla la flecha apunta
    // arriba a la izquierda, así que aquí se suman +45° en Z a las transformaciones del arco vanilla
    static final String BOW_DISPLAY = "{\n"
            + "    \"thirdperson_righthand\": { \"rotation\": [-80, 260, 5], \"translation\": [-1, -2, 2.5], \"scale\": [0.9, 0.9, 0.9] },\n"
            + "    \"thirdperson_lefthand\": { \"rotation\": [-80, -280, -5], \"translation\": [-1, -2, 2.5], \"scale\": [0.9, 0.9, 0.9] },\n"
            + "    \"firstperson_righthand\": { \"rotation\": [0, -90, 70], \"translation\": [1.13, 3.2, 1.13], \"scale\": [0.68, 0.68, 0.68] },\n"
            + "    \"firstperson_lefthand\": { \"rotation\": [0, 90, -70], \"translation\": [1.13, 3.2, 1.13], \"scale\": [0.68, 0.68, 0.68] },\n"
            + "    \"gui\": { \"rotation\": [0, 0, 45], \"translation\": [1.4, -1.4, 0], \"scale\": [0.8, 0.8, 0.8] },\n"
            + "    \"ground\": { \"rotation\": [0, 0, 45], \"translation\": [0, 2, 0], \"scale\": [0.5, 0.5, 0.5] },\n"
            + "    \"fixed\": { \"rotation\": [0, 180, 45], \"scale\": [0.8, 0.8, 0.8] }\n"
            + "  }";

    static String armorIcon(double scale) {
        return "{\n"
                + "    \"gui\": { \"rotation\": [30, 225, 0], \"scale\": " + arr(scale, scale, scale) + " },\n"
                + "    \"ground\": { \"translation\": [0, 3, 0], \"scale\": [0.25, 0.25, 0.25] },\n"
                + "    \"fixed\": { \"scale\": [0.5, 0.5, 0.5] },\n"
                + "    \"thirdperson_righthand\": { \"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.375, 0.375, 0.375] },\n"
                + "    \"firstperson_righthand\": { \"rotation\": [0, 45, 0], \"scale\": [0.4, 0.4, 0.4] },\n"
                + "    \"firstperson_lefthand\": { \"rotation\": [0, 225, 0], \"scale\": [0.4, 0.4, 0.4] }\n"
                + "  }";
    }

    // ---------- piezas ----------
    // Kanshō / Bakuya: sables chinos de un solo filo, hoja ancha con caparazón de tortuga (hexágonos) que se curva
    // hacia la punta, recortada en diagonal, con el gancho del lomo junto a la guarda. Guarda redonda con un punto del
    // color de la pareja (yin y yang). Empuñadura centrada en y≈2.6
    static Model falchion(Paint blade, Paint edge, Paint grip, Paint metal, Paint pip) {
        Model m = new Model();
        m.box(7.2, -0.6, 7.2, 8.8, 0.7, 8.8, metal);            // pomo
        m.box(7.5, 0.7, 7.5, 8.5, 4.4, 8.5, grip);              // empuñadura
        m.box(6.3, 4.3, 7.25, 9.7, 6.1, 8.75, metal);           // guarda redonda
        m.box(7.6, 4.8, 7.1, 8.4, 5.6, 8.9, pip);               // punto de la pareja
        m.box(6.9, 6.1, 7.6, 9.3, 10.6, 8.4, blade);            // hoja
        m.box(9.3, 6.1, 7.75, 9.8, 10.6, 8.25, edge);           // filo
        m.box(6.6, 6.1, 7.55, 7.0, 13.6, 8.45, metal);          // lomo
        m.box(5.9, 6.4, 7.65, 6.7, 7.8, 8.35, metal);           // gancho del lomo
        m.box(6.9, 10.6, 7.6, 10.0, 14.2, 8.4, blade);          // la hoja se ensancha
        m.box(10.0, 10.6, 7.75, 10.5, 14.2, 8.25, edge);
        m.box(7.4, 13.6, 7.62, 10.4, 17.2, 8.38, blade).rot("z", 22.5, 7.4, 13.6, 8);   // se curva hacia la punta
        m.box(10.4, 13.6, 7.77, 10.9, 17.2, 8.23, edge).rot("z", 22.5, 7.4, 13.6, 8);
        m.box(6.6, 16.0, 7.65, 9.2, 18.4, 8.35, blade).rot("z", -45, 6.6, 16.0, 8);     // punta recortada
        // Reflejado sobre x=8: en la mano el filo queda abajo y el lomo arriba
        Model flipped = m.mirrored();
        for (Cube c : flipped.cubes) {
            c.from[0] += 16;
            c.to[0] += 16;
            if (c.axis != null) c.origin[0] += 16;
        }
        return flipped;
    }

    // Espada-flecha que dispara el arco, de base en y=b hacia arriba
    static Model swordArrow(double b) {
        Model m = new Model();
        m.box(7.0, b + 1, 7.75, 9.0, b + 3, 8.25, ARROW_FIN);
        m.box(7.75, b, 7.75, 8.25, b + 13, 8.25, ARROW_SHAFT);
        m.box(7.25, b + 13, 7.8, 8.75, b + 15, 8.2, STEEL);
        m.box(7.6, b + 15, 7.85, 8.4, b + 16.5, 8.15, STEEL);
        return m;
    }

    // Caladbolg II: espada retorcida en espiral (cada tramo girado en Y)
    static Model caladbolg(double b) {
        Model m = new Model();
        m.box(7.4, b, 7.4, 8.6, b + 1, 8.6, GOLD);
        m.box(7.6, b + 1, 7.6, 8.4, b + 4, 8.4, CAL_GRIP);
        m.box(6.6, b + 4, 7.4, 9.4, b + 5, 8.6, GOLD);
        // Largo total 24.8: montada en el arco no puede pasar de y=32 (límite de los modelos JSON)
        double[] twist = {-45, -22.5, 0, 22.5, 45, -45};
        for (int i = 0; i < 6; i++) {
            double y0 = b + 5 + 2.8 * i, half = 0.9 - 0.07 * i;
            m.box(8 - half, y0, 7.7, 8 + half, y0 + 2.8, 8.3, CAL_BLADE).rot("y", twist[i], 8, y0, 8);
        }
        m.box(7.6, b + 21.8, 7.85, 8.4, b + 23.8, 8.15, CAL_BLADE);
        m.box(7.85, b + 23.8, 7.9, 8.15, b + 24.8, 8.1, STEEL);
        return m;
    }

    // Arco negro de EMIYA, horizontal, la flecha sale hacia +Y. pull = cuánto se tensa la cuerda (0 = reposo)
    static Model bow(double pull) {
        Model m = new Model();
        m.box(7, 8.6, 7.25, 9, 12, 8.75, BOW_GRIP);
        m.box(6.75, 11.6, 7.4, 9.25, 12.4, 8.6, BOW_TRIM);
        m.box(9, 9.6, 7.5, 16, 10.6, 8.5, BOW_BODY).rot("z", -22.5, 9, 10.1, 8);
        m.box(9.5, 10.6, 7.8, 15.5, 10.9, 8.2, BOW_TRIM).rot("z", -22.5, 9, 10.1, 8);
        m.box(15, 6.8, 7.6, 19, 7.8, 8.4, BOW_BODY).rot("z", 22.5, 15.5, 7.3, 8);
        m.box(0, 9.6, 7.5, 7, 10.6, 8.5, BOW_BODY).rot("z", 22.5, 7, 10.1, 8);
        m.box(0.5, 10.6, 7.8, 6.5, 10.9, 8.2, BOW_TRIM).rot("z", 22.5, 7, 10.1, 8);
        m.box(-3, 6.8, 7.6, 1, 7.8, 8.4, BOW_BODY).rot("z", -22.5, 0.5, 7.3, 8);
        if (pull <= 0) {
            m.box(-2.5, 8.45, 7.95, 18.5, 8.55, 8.05, STRING);
        } else {
            // Cuerda en V: tramos rectos desde las puntas y tramos a 22.5° hasta el culatín
            double dx = pull / Math.tan(Math.toRadians(22.5)), len = pull / Math.sin(Math.toRadians(22.5)), ny = 8.5 - pull;
            if (8 - dx > -2.5) {
                m.box(-2.5, 8.45, 7.95, 8 - dx, 8.55, 8.05, STRING);
                m.box(8 + dx, 8.45, 7.95, 18.5, 8.55, 8.05, STRING);
            }
            m.box(8 - len, ny - 0.05, 7.95, 8, ny + 0.05, 8.05, STRING).rot("z", -22.5, 8, ny, 8);
            m.box(8, ny - 0.05, 7.95, 8 + len, ny + 0.05, 8.05, STRING).rot("z", 22.5, 8, ny, 8);
        }
        return m;
    }

    // Excalibur: pomo dorado con gema, guarda azul y oro con puntas curvadas, hoja plateada con grabados.
    // Agarre en y≈2.5 como las demás espadas; total de -1 a 24. mode como en exBlade; el 2 añade el halo
    static Model excalibur(int mode) {
        Model m = new Model();
        m.box(7.2, -1, 7.2, 8.8, 0.5, 8.8, GOLD);                // pomo
        m.box(7.6, -0.6, 7.0, 8.4, 0.2, 9.0, EX_GEM);             // gema
        m.box(7.5, 0.5, 7.5, 8.5, 4.5, 8.5, EX_GRIP);             // empuñadura
        m.box(6.5, 4.5, 7.2, 9.5, 5.7, 8.8, GOLD);                // centro de la guarda
        m.box(7.0, 4.7, 7.05, 9.0, 5.5, 8.95, EX_BLUE);           // incrustación azul
        m.box(3.5, 4.6, 7.4, 6.5, 5.5, 8.6, GOLD);                // brazos de la guarda
        m.box(9.5, 4.6, 7.4, 12.5, 5.5, 8.6, GOLD);
        m.box(2.6, 4.7, 7.5, 3.8, 5.5, 8.5, GOLD).rot("z", -22.5, 3.8, 5.1, 8);   // puntas curvadas hacia arriba
        m.box(12.2, 4.7, 7.5, 13.4, 5.5, 8.5, GOLD).rot("z", 22.5, 12.2, 5.1, 8);

        Paint steel = exBlade(0xdde5f3, mode), fuller = exBlade(0xb4c4e4, mode);
        m.box(6.9, 5.7, 7.6, 9.1, 9, 8.4, steel);                 // base ancha de la hoja
        m.box(7.6, 6, 7.5, 8.4, 11, 8.5, engrave(mode));          // grabados
        m.box(7.0, 9, 7.65, 9.0, 19, 8.35, steel);                // hoja
        m.box(7.75, 11, 7.6, 8.25, 19, 8.4, fuller);              // acanaladura
        m.box(7.3, 19, 7.7, 8.7, 21.5, 8.3, steel);               // punta
        m.box(7.65, 21.5, 7.75, 8.35, 23, 8.25, steel);
        m.box(7.85, 23, 7.8, 8.15, 24, 8.2, steel);
        if (mode == 2) m.box(6.4, 5.7, 7.1, 9.6, 24.5, 8.9, aura());
        return m;
    }

    // Lo que brilla de Excalibur cargada: la hoja dorada y la gema
    static Paint exGlow(int color) {
        return (x, y, w, h, s) -> lerp(color, 0xffffff, 0.25 * noise(x, y, s));
    }

    // Vetas del viento de Invisible Air, translúcidas
    static Paint windStreaks() {
        return (x, y, w, h, s) -> Math.floorMod(x * 2 + y, 7) < 2 ? (150 << 24) | 0xeef6ff : (45 << 24) | 0xc4dcff;
    }

    // Excalibur para GeckoLib: empuñadura, hoja (brilla al cargar), halo y el viento de Invisible Air.
    // mode como en exBlade: cambia solo la pintura, la distribución de la textura es la misma
    static List<Bone> excaliburBones(int mode) {
        Model m = excalibur(mode);
        if (mode != 2) m.box(6.4, 5.7, 7.1, 9.6, 24.5, 8.9, aura());
        List<Cube> cubes = oneBone(m).get(0).model.cubes;
        Bone hilt = new Bone("hilt", null, 0, -5.5, 0);
        Bone blade = new Bone("blade", "hilt", 0, -2.3, 0);
        Bone halo = new Bone("aura", "blade", 0, -2.3, 0);
        Bone wind = new Bone("wind", "hilt", 0, 6, 0);
        for (int i = 0; i < cubes.size(); i++) {
            Cube c = cubes.get(i);
            if (i == 1) c.glow(exGlow(0x9fe4ff));                     // gema
            if (i < 9) hilt.model.cubes.add(c);
            else if (i == cubes.size() - 1) halo.model.cubes.add(c);
            else blade.model.cubes.add(c.glow(exGlow(i == 10 ? 0xffe9a0 : 0xffd060)));
        }
        Paint air = windStreaks();
        for (int a = 0; a < 180; a += 60) wind.model.box(-2.4, -3, -0.03, 2.4, 17, 0.03, air).rot("y", a, 0, 6, 0);
        wind.model.box(-1.6, -3, -1.6, 1.6, 17, 1.6, air).rot("y", 45, 0, 6, 0);
        return List.of(hilt, blade, halo, wind);
    }

    // Igual que handheld, pero en primera persona la espada se alza (zRot ya incluye los -45° del modelo vertical)
    static String raised(double guiScale, double zRot, double ty, double tz, double scale) {
        String s = arr(scale, scale, scale);
        return "{\n"
                + "    \"thirdperson_righthand\": { \"rotation\": [0, -90, 10], \"translation\": [0, 4, 0.5], \"scale\": [0.85, 0.85, 0.85] },\n"
                + "    \"thirdperson_lefthand\": { \"rotation\": [0, 90, -10], \"translation\": [0, 4, 0.5], \"scale\": [0.85, 0.85, 0.85] },\n"
                + "    \"firstperson_righthand\": { \"rotation\": " + arr(0, -90, zRot) + ", \"translation\": " + arr(1.13, ty, tz) + ", \"scale\": " + s + " },\n"
                + "    \"firstperson_lefthand\": { \"rotation\": " + arr(0, 90, -zRot) + ", \"translation\": " + arr(1.13, ty, tz) + ", \"scale\": " + s + " },\n"
                + "    \"gui\": { \"rotation\": [0, 0, -45], \"translation\": [-1, -1, 0], \"scale\": " + arr(guiScale, guiScale, guiScale) + " },\n"
                + "    \"ground\": { \"rotation\": [0, 0, -45], \"translation\": [0, 2, 0], \"scale\": [0.5, 0.5, 0.5] },\n"
                + "    \"fixed\": { \"rotation\": [0, 180, -45], \"scale\": [0.8, 0.8, 0.8] }\n"
                + "  }";
    }

    // Saber: vestido azul con falda (animada), coraza y hombreras de plata, guanteletes y escarpes
    static List<Bone> saberArmor() {
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, BLUE_DRESS).inflate(1.0);
        body.model.box(-4, 17, -2, 4, 23, 2, SABER_SILVER).inflate(1.2);
        body.model.box(-4, 22.5, -2, 4, 23.5, 2, GOLD).inflate(1.3);
        body.model.box(-4, 11.5, -2, 4, 13, 2, GOLD).inflate(1.3);

        Bone skirtFront = new Bone("skirtFront", "armorBody", 0, 12, -3.3);
        skirtFront.model.box(-4.8, 3, -3.9, 4.8, 12, -3.3, BLUE_DRESS);
        skirtFront.model.box(-4.4, 8, -4.3, -0.4, 12, -3.9, SABER_SILVER);   // escarcelas
        skirtFront.model.box(0.4, 8, -4.3, 4.4, 12, -3.9, SABER_SILVER);
        Bone skirtBack = new Bone("skirtBack", "armorBody", 0, 12, 3.3);
        skirtBack.model.box(-4.8, 3, 3.3, 4.8, 12, 3.9, BLUE_DRESS);
        Bone skirtLeft = new Bone("skirtLeft", "armorBody", 4.6, 12, 0);
        skirtLeft.model.box(4.6, 3, -3.3, 5.2, 12, 3.3, BLUE_DRESS);
        Bone skirtRight = new Bone("skirtRight", "armorBody", -4.6, 12, 0);
        skirtRight.model.box(-5.2, 3, -3.3, -4.6, 12, 3.3, BLUE_DRESS);

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 15, -2, -4, 24, 2, BLUE_DRESS).inflate(0.9);
        rightArm.model.box(-8, 12, -2, -4, 16, 2, SABER_SILVER).inflate(1.05);
        rightArm.model.box(-8.5, 21, -2.5, -3.5, 24.5, 2.5, SABER_SILVER).inflate(0.4);
        Bone leftArm = new Bone("armorLeftArm", null, 5, 22, 0);
        leftArm.model.box(4, 15, -2, 8, 24, 2, BLUE_DRESS).inflate(0.9);
        leftArm.model.box(4, 12, -2, 8, 16, 2, SABER_SILVER).inflate(1.05);
        leftArm.model.box(3.5, 21, -2.5, 8.5, 24.5, 2.5, SABER_SILVER).inflate(0.4);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 7, -2, 0, 12, 2, BLUE_DRESS).inflate(0.5);
        rightLeg.model.box(-4, 3, -2, 0, 7, 2, SABER_SILVER).inflate(0.55);
        Bone leftLeg = new Bone("armorLeftLeg", null, 2, 12, 0);
        leftLeg.model.box(0, 7, -2, 4, 12, 2, BLUE_DRESS).inflate(0.5);
        leftLeg.model.box(0, 3, -2, 4, 7, 2, SABER_SILVER).inflate(0.55);

        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 4, 2, SABER_SILVER).inflate(0.9);
        rightBoot.model.box(-4, 0, -3.4, 0, 1.5, -2.9, GOLD);
        Bone leftBoot = new Bone("armorLeftBoot", null, 2, 12, 0);
        leftBoot.model.box(0, 0, -2, 4, 4, 2, SABER_SILVER).inflate(0.9);
        leftBoot.model.box(0, 0, -3.4, 4, 1.5, -2.9, GOLD);

        return List.of(head, body, skirtFront, skirtBack, skirtLeft, skirtRight,
                rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    // Daga de Rider: un clavo largo con una anilla en el pomo de la que cuelga la cadena
    static Model chainDagger() {
        Model m = new Model();
        m.box(7.75, -9.5, 7.6, 8.25, -7, 8.4, DARK_STEEL);       // eslabones
        m.box(7.6, -7, 7.75, 8.4, -4.5, 8.25, DARK_STEEL);
        m.box(7.0, -4.5, 7.75, 9.0, -4, 8.25, DARK_STEEL);       // anilla
        m.box(7.0, -4, 7.75, 7.5, -1.5, 8.25, DARK_STEEL);
        m.box(8.5, -4, 7.75, 9.0, -1.5, 8.25, DARK_STEEL);
        m.box(7.0, -1.5, 7.75, 9.0, -0.5, 8.25, DARK_STEEL);
        m.box(7.4, -0.5, 7.4, 8.6, 4, 8.6, DAGGER_GRIP);         // empuñadura
        m.box(6.8, 4, 7.6, 9.2, 4.8, 8.4, DARK_STEEL);           // guarda
        m.box(7.4, 4.8, 7.5, 8.6, 10, 8.5, STEEL);               // hoja en forma de clavo
        m.box(7.6, 10, 7.65, 8.4, 14, 8.35, STEEL);
        m.box(7.8, 14, 7.8, 8.2, 16.5, 8.2, STEEL);
        return m;
    }

    // Bellerophon: bridas doradas con una gema
    static Model bridle() {
        Model m = new Model();
        m.box(4, 12, 7.5, 12, 13, 8.5, GOLD);
        m.box(4, 4, 7.5, 12, 5, 8.5, GOLD);
        m.box(4, 5, 7.5, 5, 12, 8.5, GOLD);
        m.box(11, 5, 7.5, 12, 12, 8.5, GOLD);
        m.box(7.4, 12.5, 7.3, 8.6, 13.7, 8.7, EX_GEM);
        m.box(2, 4.2, 7.75, 14, 4.8, 8.25, STEEL);               // bocado
        m.box(7.6, 0, 7.75, 8.4, 4.2, 8.25, LEATHER);            // riendas
        m.box(3, 0, 7.75, 3.8, 4.2, 8.25, LEATHER);
        m.box(12.2, 0, 7.75, 13, 4.2, 8.25, LEATHER);
        return m;
    }

    static final String HELD_OBJECT = "{\n"
            + "    \"thirdperson_righthand\": { \"rotation\": [0, -90, 0], \"translation\": [0, 2, 1], \"scale\": [0.55, 0.55, 0.55] },\n"
            + "    \"thirdperson_lefthand\": { \"rotation\": [0, 90, 0], \"translation\": [0, 2, 1], \"scale\": [0.55, 0.55, 0.55] },\n"
            + "    \"firstperson_righthand\": { \"rotation\": [0, -90, 10], \"translation\": [1.13, 3.2, 1.13], \"scale\": [0.6, 0.6, 0.6] },\n"
            + "    \"firstperson_lefthand\": { \"rotation\": [0, 90, -10], \"translation\": [1.13, 3.2, 1.13], \"scale\": [0.6, 0.6, 0.6] },\n"
            + "    \"gui\": { \"rotation\": [15, -30, 0], \"scale\": [0.9, 0.9, 0.9] },\n"
            + "    \"ground\": { \"translation\": [0, 2, 0], \"scale\": [0.5, 0.5, 0.5] },\n"
            + "    \"fixed\": { \"scale\": [0.8, 0.8, 0.8] }\n"
            + "  }";

    // Pegaso: caballo blanco con alas, crin dorada y cascos de oro. Mira hacia -Z; patas y alas giran en su bone
    static List<Bone> pegasus() {
        Bone body = new Bone("body", null, 0, 16, 0);
        body.model.box(-5, 11, -11, 5, 21, 11, WHITE_COAT);
        Bone neck = new Bone("neck", "body", 0, 19, -9);
        neck.model.box(-2.5, 17, -14, 2.5, 27, -8, WHITE_COAT);
        neck.model.box(-0.75, 20, -9, 0.75, 28, -7.5, MANE);
        Bone head = new Bone("head", "neck", 0, 26, -11);
        head.model.box(-2.5, 24, -19, 2.5, 29, -11, WHITE_COAT);
        head.model.box(-2, 24, -21, 2, 27.5, -19, WHITE_COAT);
        head.model.box(2.5, 26.5, -16.5, 2.7, 27.3, -15.5, EYE_BLUE);
        head.model.box(-2.7, 26.5, -16.5, -2.5, 27.3, -15.5, EYE_BLUE);
        head.model.box(-2, 29, -13, -1, 31, -12, WHITE_COAT);
        head.model.box(1, 29, -13, 2, 31, -12, WHITE_COAT);

        Bone legFL = new Bone("legFL", null, 3.5, 12, -8);
        legFL.model.box(2, 2, -9.5, 5, 12, -6.5, WHITE_COAT);
        legFL.model.box(2, 0, -9.5, 5, 2, -6.5, GOLD);
        Bone legFR = new Bone("legFR", null, -3.5, 12, -8);
        legFR.model.box(-5, 2, -9.5, -2, 12, -6.5, WHITE_COAT);
        legFR.model.box(-5, 0, -9.5, -2, 2, -6.5, GOLD);
        Bone legBL = new Bone("legBL", null, 3.5, 12, 8);
        legBL.model.box(2, 2, 6.5, 5, 12, 9.5, WHITE_COAT);
        legBL.model.box(2, 0, 6.5, 5, 2, 9.5, GOLD);
        Bone legBR = new Bone("legBR", null, -3.5, 12, 8);
        legBR.model.box(-5, 2, 6.5, -2, 12, 9.5, WHITE_COAT);
        legBR.model.box(-5, 0, 6.5, -2, 2, 9.5, GOLD);

        Bone wingL = new Bone("wingL", "body", 5, 20, -4);
        wingL.model.box(5, 19.5, -7, 19, 20.5, 3, feathers());
        wingL.model.box(19, 19.7, -5, 26, 20.3, 2, feathers());
        Bone wingR = new Bone("wingR", "body", -5, 20, -4);
        wingR.model.box(-19, 19.5, -7, -5, 20.5, 3, feathers());
        wingR.model.box(-26, 19.7, -5, -19, 20.3, 2, feathers());

        Bone tail = new Bone("tail", "body", 0, 19, 11);
        tail.model.box(-1.5, 9, 11, 1.5, 19, 13.5, MANE);

        return List.of(body, neck, head, legFL, legFR, legBL, legBR, wingL, wingR, tail);
    }

    // Ea: tres cilindros (octogonales: un cubo y otro girado 45°) cada vez más finos, sobre una empuñadura dorada
    static Model ea(int mode) {
        Model m = new Model();
        m.box(7.3, -1, 7.3, 8.7, 0.5, 8.7, GOLD);                  // pomo
        m.box(7.5, 0.5, 7.5, 8.5, 4.5, 8.5, EA_GRIP);              // empuñadura
        m.box(6.2, 4.5, 6.2, 9.8, 5.5, 9.8, GOLD);                 // guarda redonda
        m.box(6.2, 4.5, 6.2, 9.8, 5.5, 9.8, GOLD).rot("y", 45, 8, 5, 8);
        Paint seg = eaSegment(mode);
        double[][] segments = {{1.3, 5.5, 11}, {1.1, 11.2, 16.5}, {0.9, 16.7, 21.5}, {0.5, 21.5, 24}};
        for (double[] s : segments) {
            double h = s[0];
            m.box(8 - h, s[1], 8 - h, 8 + h, s[2], 8 + h, seg);
            m.box(8 - h, s[1], 8 - h, 8 + h, s[2], 8 + h, seg).rot("y", 45, 8, s[1], 8);
        }
        m.box(6.8, 11, 6.8, 9.2, 11.2, 9.2, GOLD);                 // anillos entre cilindros
        m.box(7.0, 16.5, 7.0, 9.0, 16.7, 9.0, GOLD);
        if (mode == 2) m.box(5.8, 5.5, 5.8, 10.2, 24.5, 10.2, redAura());
        return m;
    }

    // Llave de la Sala del Tesoro: anilla con gema roja, caña y paletón dorados
    static Model babylonKey() {
        Model m = new Model();
        m.box(6, -1, 7.5, 10, 0, 8.5, GOLD);
        m.box(6, 0, 7.5, 7, 3, 8.5, GOLD);
        m.box(9, 0, 7.5, 10, 3, 8.5, GOLD);
        m.box(6, 3, 7.5, 10, 4, 8.5, GOLD);
        m.box(7.5, 0.8, 7.4, 8.5, 2.2, 8.6, solid(0xc8102e));     // gema
        m.box(7, 4, 7, 9, 5, 9, GOLD);                             // collar
        m.box(7.5, 5, 7.5, 8.5, 15, 8.5, GOLD);                    // caña
        m.box(8.5, 11, 7.6, 10.5, 12, 8.4, GOLD);                  // paletón
        m.box(8.5, 13, 7.6, 10, 14, 8.4, GOLD);
        m.box(7.3, 15, 7.3, 8.7, 16, 8.7, GOLD);
        return m;
    }

    // Runas de Gáe Bolg: marcas cortas cada pocos píxeles en la parte alta del asta
    static Paint gaeRunes() {
        return (x, y, w, h, s) -> y < h * 0.45 && Math.floorMod(y, 5) < 2 && Math.floorMod(x + y / 5, 2) == 0 ? 0xff2a3a : 0;
    }

    // Filo de la hoja: brillan los bordes y una línea central
    static Paint gaeEdge() {
        return (x, y, w, h, s) -> x == 0 || x == w - 1 || x == w / 2 ? 0xff4050 : 0;
    }

    // Gáe Bolg: lanza carmesí con púas. Agarre en y≈2.5, igual que las espadas; total de -12 a 30.5
    static Model gaeBolg() {
        Model m = new Model();
        m.box(7.4, -12, 7.4, 8.6, -10.5, 8.6, SPEAR_METAL);     // regatón
        m.box(7.6, -10.5, 7.6, 8.4, 21, 8.4, SPEAR_SHAFT).glow(gaeRunes());   // asta con runas
        m.box(7.35, 0.5, 7.35, 8.65, 4.5, 8.65, SPEAR_GRIP);    // agarre
        m.box(7.2, 21, 7.2, 8.8, 22, 8.8, SPEAR_METAL);         // anillo
        m.box(7.0, 22, 7.6, 9.0, 26, 8.4, SPEAR_HEAD).glow(gaeEdge());          // hoja
        m.box(7.4, 26, 7.65, 8.6, 29, 8.35, SPEAR_HEAD).glow(gaeEdge());
        m.box(7.75, 29, 7.7, 8.25, 30.5, 8.3, SPEAR_EDGE).glow(solid(0xff6070));      // punta
        // Púas que apuntan hacia atrás, a ambos lados de la hoja
        for (double y : new double[]{22.5, 25.0}) {
            m.box(5.6, y, 7.75, 7.0, y + 0.8, 8.25, SPEAR_EDGE).rot("z", 45, 7.0, y + 0.4, 8);
            m.box(9.0, y, 7.75, 10.4, y + 0.8, 8.25, SPEAR_EDGE).rot("z", -45, 9.0, y + 0.4, 8);
        }
        return m;
    }

    // ---------- Armaduras rehechas a partir de las ilustraciones de cada servant ----------
    // Cada pieza es una capa (traje, placa, ribete) y los dibujos van solo en la cara que toca (normalmente delante)
    static boolean centerLine(int x, int w) {
        return Math.abs(x - (w - 1) / 2.0) < 0.6;
    }

    // EMIYA: armadura negra segmentada con líneas grises, abrigo rojo abierto de cuello alto, cinturón de plata,
    // mangas rojas con puños negros, pantalón negro y botas con anillas de plata
    static final int ARCH_RED = 0xa3161c, ARCH_BLACK = 0x1f1f26, ARCH_LINE = 0x6c707c, ARCH_SILVER = 0xbcc1cb;

    static List<Bone> archerArmor() {
        Paint red = fabric(ARCH_RED), black = plate(ARCH_BLACK), suit = fabric(0x18181d), silver = plate(ARCH_SILVER);
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, suit).inflate(0.4);
        body.model.box(-4.2, 15.4, -2.6, 4.2, 23.4, 2.4, black).face("north", marked(black, ARCH_LINE,
                (x, y, w, h) -> centerLine(x, w) || (y % 4 == 3 && y > h / 3) || (y == h / 3 && x > 1 && x < w - 2)));
        body.model.box(-3.4, 23.2, -2.7, 3.4, 25.4, 2.6, red);                 // cuello alto del abrigo
        body.model.box(-4.6, 12.4, 2.3, 4.6, 24.2, 2.9, red);                  // espalda del abrigo
        body.model.box(4.3, 12.4, -2.7, 4.8, 24.2, 2.7, red);                  // costados
        body.model.box(-4.8, 12.4, -2.7, -4.3, 24.2, 2.7, red);
        body.model.box(2.3, 12.4, -2.9, 4.6, 24.2, -2.4, red);                 // solapas, abierto en el centro
        body.model.box(-4.6, 12.4, -2.9, -2.3, 24.2, -2.4, red);
        body.model.box(-4.4, 11.8, -2.8, 4.4, 12.9, 2.8, silver);              // cinturón
        body.model.box(-1, 11.6, -3.1, 1, 13.1, -2.7, silver);

        Bone coatBack = new Bone("coatBack", "armorBody", 0, 12.4, 2.9);
        coatBack.model.box(-4.6, 1.5, 2.4, 4.6, 12.4, 3.0, red);
        Bone coatRight = new Bone("coatRight", "armorBody", -4.6, 12.4, 0);
        coatRight.model.box(-4.9, 2.5, -2.7, -4.3, 12.4, 2.6, red);
        Bone coatLeft = coatRight.mirror("coatLeft", "armorBody");
        Bone coatFrontR = new Bone("coatFrontR", "armorBody", -3.4, 12.4, -2.9);
        coatFrontR.model.box(-4.6, 3.5, -3.1, -2.2, 12.4, -2.6, red);
        Bone coatFrontL = coatFrontR.mirror("coatFrontL", "armorBody");

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 13.6, -2, -4, 24, 2, red).inflate(0.5);
        rightArm.model.box(-8.4, 12, -2.4, -3.6, 14, 2.4, black);              // puño negro
        Bone leftArm = rightArm.mirror("armorLeftArm", null);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 3, -2, 0, 12, 2, suit).inflate(0.35);
        Bone leftLeg = rightLeg.mirror("armorLeftLeg", null);
        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 5, 2, black).inflate(0.55);
        rightBoot.model.box(-4.5, 3.2, -2.5, 0.5, 4.0, 2.5, silver);           // anillas
        rightBoot.model.box(-4.5, 1.5, -2.5, 0.5, 2.1, 2.5, silver);
        rightBoot.model.box(-3.7, 0, -3.3, -0.3, 1.5, -2.4, silver);           // puntera
        Bone leftBoot = rightBoot.mirror("armorLeftBoot", null);

        return List.of(head, body, coatBack, coatLeft, coatRight, coatFrontL, coatFrontR,
                rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    // Cú Chulainn: traje azul ceñido con líneas plateadas que siguen los músculos, hombreras y cinturón de plata,
    // grebas plateadas y su coleta
    static final int LAN_BLUE = 0x2443b0, LAN_LINE = 0xc9d2e4, LAN_SILVER = 0xc0c8d6;

    static Paint lancerSuit(boolean front) {
        Paint base = fabric(LAN_BLUE);
        return marked(base, LAN_LINE, (x, y, w, h) -> {
            double d = Math.abs(x - (w - 1) / 2.0);
            if (!front) return d < 0.6 && y > 1;
            int pec = (int) Math.round(h * 0.34 - d / (w / 2.0) * h * 0.1);
            return (d < 0.6 && y > h * 0.36) || y == pec || ((y == (int) (h * 0.58) || y == (int) (h * 0.76)) && d < w * 0.3);
        });
    }

    static List<Bone> lancerArmor() {
        Paint suit = lancerSuit(false), silver = plate(LAN_SILVER), deep = plate(0x8f98aa);
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, suit).inflate(0.4).face("north", lancerSuit(true));
        body.model.box(-2.4, 23.5, -2.5, 2.4, 24.6, 2.5, suit);                // cuello del traje
        body.model.box(-4.3, 11.7, -2.6, 4.3, 12.7, 2.6, silver);              // cinturón
        Bone ponytail = new Bone("ponytail", "armorBody", 0, 25, 2.6);
        ponytail.model.box(-0.75, 14, 2.6, 0.75, 25, 3.6, HAIR);
        ponytail.model.box(-0.5, 12, 2.7, 0.5, 14, 3.4, HAIR);
        ponytail.model.box(-1.0, 22.5, 2.5, 1.0, 23.5, 3.8, silver);

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 12, -2, -4, 24, 2, suit).inflate(0.35);
        rightArm.model.box(-9.2, 21.3, -2.9, -3.6, 24.9, 2.9, silver).rot("z", -10, -4, 24, 0);   // hombrera
        rightArm.model.box(-9.4, 20.0, -2.7, -4.4, 21.6, 2.7, deep).rot("z", -10, -4, 24, 0);
        rightArm.model.box(-8.4, 12, -2.4, -3.6, 13, 2.4, silver);              // muñequera
        Bone leftArm = rightArm.mirror("armorLeftArm", null);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 3, -2, 0, 12, 2, suit).inflate(0.35);
        Bone leftLeg = rightLeg.mirror("armorLeftLeg", null);
        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 5, 2, silver).inflate(0.5).face("north",
                marked(silver, LAN_BLUE, (x, y, w, h) -> centerLine(x, w) && y < h - 2));
        rightBoot.model.box(-3.6, 0, -3.2, -0.4, 1.4, -2.4, silver);
        Bone leftBoot = rightBoot.mirror("armorLeftBoot", null);

        return List.of(head, body, ponytail, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    // Medusa: venda Breaker Gorgon, pelo morado larguísimo, vestido negro corto sin tirantes con gargantilla,
    // mangas largas negras con bandas moradas y botas negras hasta el muslo con banda y puntera morada
    static final int RID_BLACK = 0x17131c, RID_LEATHER = 0x1c1722, RID_PURPLE = 0x7a2f9e;

    static Paint riderHair() {
        return (x, y, w, h, s) -> bevel(Math.floorMod(x, 3) == 0 ? 0x6a3fa6 : 0x8a52c8, x, y, w, h, s, 0.06);
    }

    // Venda de Medusa: tela magenta enrollada, con pliegues en diagonal (sombra y brillo) y los bordes más oscuros
    static Paint gorgonBand() {
        return (x, y, w, h, s) -> {
            if (y == 0 || y == h - 1) return bevel(0x7a1840, x, y, w, h, s, 0.03);
            int fold = Math.floorMod(x + y * 2, 6);
            int c = fold == 0 ? 0x6e1236 : fold == 1 ? 0xd8508a : 0xb3285e;
            return bevel(c, x, y, w, h, s, 0.04);
        };
    }

    static List<Bone> riderArmor() {
        Paint dress = fabric(RID_BLACK), leather = plate(RID_LEATHER), purple = plate(RID_PURPLE), hairPaint = riderHair();
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        // Breaker Gorgon: solo la venda, tela magenta sobre los ojos con dos hebillas de metal entre los ojos
        Paint band = gorgonBand(), buckle = plate(0xb8bcc6);
        head.model.box(-4, 26.3, -4, 4, 29.3, 4, band).inflate(0.45);
        head.model.box(-0.8, 27.9, -4.75, 0.8, 28.8, -4.4, buckle);           // hebillas, entre los ojos
        head.model.box(-0.8, 26.8, -4.75, 0.8, 27.7, -4.4, buckle);

        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 22.6, 2, dress).inflate(0.45);
        body.model.box(-2.3, 23.5, -2.3, 2.3, 24.3, 2.3, purple);              // gargantilla
        body.model.box(-4.5, 9.0, -2.6, 4.5, 12.4, 2.6, dress);                // falda corta
        Bone hair = new Bone("hair", "armorBody", 0, 24.5, 2.6);
        hair.model.box(-4.3, 4, 2.6, 4.3, 24.5, 4.0, hairPaint);

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 12, -2, -4, 19.5, 2, fabric(0x141018)).inflate(0.4);
        rightArm.model.box(-8.5, 12.8, -2.5, -3.5, 13.8, 2.5, purple);          // bandas moradas
        rightArm.model.box(-8.5, 18.6, -2.5, -3.5, 19.7, 2.5, purple);
        rightArm.model.box(-8.5, 21.6, -2.5, -3.5, 22.8, 2.5, purple);
        Bone leftArm = rightArm.mirror("armorLeftArm", null);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 4, -2, 0, 10.5, 2, leather).inflate(0.4);       // bota alta
        rightLeg.model.box(-4.5, 10.0, -2.5, 0.5, 11.2, 2.5, purple);
        Bone leftLeg = rightLeg.mirror("armorLeftLeg", null);
        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 4.2, 2, leather).inflate(0.5);
        rightBoot.model.box(-3.7, 0, -3.2, -0.3, 1.6, -2.3, purple);           // puntera
        rightBoot.model.box(-3.4, 0, 2.3, -0.6, 2.2, 3.0, purple);              // tacón
        Bone leftBoot = rightBoot.mirror("armorLeftBoot", null);

        return List.of(head, body, hair, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    // Gilgamesh: armadura dorada sobre un traje azul oscuro, hombreras grandes en láminas, peto con líneas oscuras,
    // panel dorado con triángulos delante y falda roja hasta los tobillos, rodilleras y escarpes en punta
    static final int GIL_GOLD = 0xe2b437, GIL_DEEP = 0xa77a1c, GIL_NAVY = 0x1c2350, GIL_RED = 0x9c1622;

    static List<Bone> gilgameshArmor() {
        Paint gold = plate(GIL_GOLD), deep = plate(GIL_DEEP), navy = fabric(GIL_NAVY), red = fabric(GIL_RED);
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, navy).inflate(0.45);
        body.model.box(-4.2, 16.5, -2.6, 4.2, 23.6, 2.5, gold).face("north", marked(gold, GIL_NAVY, (x, y, w, h) -> {
            double d = Math.abs(x - (w - 1) / 2.0), half = h / 2.0;
            return y < half ? Math.abs(d - (half - y) * (w / 2.0) / half) < 0.7 : d < 0.6;
        }));
        body.model.box(-3.9, 14.4, -2.5, 3.9, 16.6, 2.4, gold);                // placas del vientre
        body.model.box(-3.7, 12.6, -2.4, 3.7, 14.5, 2.3, gold);
        body.model.box(-4.3, 11.6, -2.7, 4.3, 12.8, 2.7, deep).face("north",
                marked(deep, GIL_NAVY, (x, y, w, h) -> x % 4 == 1 && y == h / 2));
        body.model.box(-2.6, 23.4, -2.5, 2.6, 24.8, 2.3, gold);                // gorjal

        Bone tassets = new Bone("tassets", "armorBody", 0, 12, 0);
        tassets.model.box(-1.8, 2, -3.0, 1.8, 12, -2.6, gold).face("north", marked(gold, GIL_NAVY,
                (x, y, w, h) -> (y >= 1 && y <= 4 && Math.abs(Math.floorMod(x, 4) - 1.5) < (4.5 - y) / 2) || (centerLine(x, w) && y > 7 && y % 4 != 0)));
        tassets.model.box(-4.6, 0.5, 2.5, 4.6, 12, 3.0, red);                  // falda roja: detrás y a los lados
        tassets.model.box(4.4, 0.5, -2.2, 4.9, 12, 2.6, red);
        tassets.model.box(-4.9, 0.5, -2.2, -4.4, 12, 2.6, red);
        tassets.model.box(1.8, 9.5, -3.0, 4.7, 12, -2.4, gold);                // escarcelas
        tassets.model.box(-4.7, 9.5, -3.0, -1.8, 12, -2.4, gold);

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 12, -2, -4, 22, 2, navy).inflate(0.35);
        rightArm.model.box(-9.9, 21.4, -3.3, -3.4, 25.4, 3.3, gold).rot("z", -12, -4, 24, 0);   // hombrera
        rightArm.model.box(-10.3, 19.8, -3.1, -4.3, 21.8, 3.1, deep).rot("z", -12, -4, 24, 0);
        rightArm.model.box(-8.5, 17.4, -2.5, -3.5, 19.4, 2.5, gold);           // brazal
        rightArm.model.box(-8.6, 11.6, -2.6, -3.4, 16.4, 2.6, gold).face("north",
                marked(gold, GIL_NAVY, (x, y, w, h) -> y == h / 2 && x > 0 && x < w - 1));      // guantelete
        Bone leftArm = rightArm.mirror("armorLeftArm", null);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 3, -2, 0, 12, 2, navy).inflate(0.3);
        rightLeg.model.box(-4.3, 7.4, -2.5, 0.3, 11.8, 2.5, gold);             // muslera
        rightLeg.model.box(-3.6, 4.6, -3.2, -0.4, 7.6, -2.0, gold);            // rodillera
        rightLeg.model.box(-4.2, 3.0, -2.4, 0.2, 5.0, 2.4, deep);
        Bone leftLeg = rightLeg.mirror("armorLeftLeg", null);
        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4.3, 0, -2.5, 0.3, 4.8, 2.5, gold).face("north",
                marked(gold, GIL_NAVY, (x, y, w, h) -> centerLine(x, w) && y < h - 2));
        rightBoot.model.box(-3.5, 0, -4.2, -0.5, 1.4, -2.5, gold);             // punta
        rightBoot.model.box(-4.5, 3.8, -2.7, 0.5, 4.8, 2.7, deep);
        Bone leftBoot = rightBoot.mirror("armorLeftBoot", null);

        return List.of(head, body, tassets, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    // Medea: capucha y capa verde oscuro con ribete dorado y adorno en la frente, túnica morada, broche dorado,
    // mangas anchas con puños dorados y la túnica hasta el suelo
    static final int CAS_CLOAK = 0x223a2c, CAS_ROBE = 0x5a3488, CAS_GOLD = 0xd2ad44;

    static List<Bone> casterArmor() {
        Paint cloak = fabric(CAS_CLOAK), robe = fabric(CAS_ROBE), gold = plate(CAS_GOLD), trimmed = edged(cloak, CAS_GOLD, 1);
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        head.model.box(-4.7, 31.6, -4.9, 4.7, 33.0, 4.7, cloak);               // capucha
        head.model.box(-5.1, 24.2, -4.9, -4.4, 32.2, 4.7, cloak);
        head.model.box(4.4, 24.2, -4.9, 5.1, 32.2, 4.7, cloak);
        head.model.box(-4.7, 24.2, 4.2, 4.7, 32.2, 4.9, cloak);
        head.model.box(-4.8, 30.4, -5.3, 4.8, 31.8, -4.5, trimmed);            // borde de la capucha
        head.model.box(-1.1, 30.6, -5.7, 1.1, 32.6, -5.2, gold);               // adorno de la frente

        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, robe).inflate(0.45).face("north", marked(robe, CAS_GOLD,
                (x, y, w, h) -> y > h * 0.55 && y < h * 0.85 && Math.abs(Math.abs(x - (w - 1) / 2.0) - (h * 0.85 - y) * 0.6) < 0.6));
        body.model.box(-4.8, 20.5, -2.8, 4.8, 24.6, 2.8, cloak);               // manto sobre los hombros
        body.model.box(-1.2, 21.6, -3.2, 1.2, 23.6, -2.7, gold).face("north",
                marked(gold, 0xc0182a, (x, y, w, h) -> Math.abs(x - (w - 1) / 2.0) < 1 && Math.abs(y - (h - 1) / 2.0) < 1));  // broche
        Bone capeBack = new Bone("capeBack", "armorBody", 0, 24, 3);
        capeBack.model.box(-5.0, 0.5, 2.6, 5.0, 24.4, 3.2, trimmed);
        Bone capeRight = new Bone("capeRight", "armorBody", -5, 24, 0);
        capeRight.model.box(-5.2, 0.5, -2.9, -4.6, 24.4, 3.0, cloak);
        capeRight.model.box(-5.2, 0.5, -3.1, -3.2, 20.5, -2.6, trimmed);        // capa abierta por delante
        Bone capeLeft = capeRight.mirror("capeLeft", "armorBody");

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 16, -2, -4, 24, 2, robe).inflate(0.5);
        rightArm.model.box(-8.8, 11.6, -2.8, -3.2, 16.2, 2.8, robe);            // manga ancha
        rightArm.model.box(-8.9, 11.3, -2.9, -3.1, 12.1, 2.9, gold);
        rightArm.model.box(-8.7, 20.6, -2.8, -3.4, 24.7, 2.8, cloak);
        Bone leftArm = rightArm.mirror("armorLeftArm", null);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4.3, 0.6, -2.4, 0, 12, 2.4, robe).face("north",
                marked(robe, CAS_GOLD, (x, y, w, h) -> y >= h - 2));            // túnica hasta el suelo
        Bone leftLeg = rightLeg.mirror("armorLeftLeg", null);
        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 1.6, 2, plate(0x1f3326)).inflate(0.4);
        rightBoot.model.box(-3.7, 0.4, -2.7, -0.3, 1.0, -2.3, gold);
        Bone leftBoot = rightBoot.mirror("armorLeftBoot", null);

        return List.of(head, body, capeBack, capeLeft, capeRight, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    // Ishtar (primera ascensión): top blanco con ribetes dorados, collar de oro con gema negra, braguita negra con
    // cinturón dorado cruzado. Asimétrica: brazo derecho al aire con brazalete; el izquierdo con guante negro largo,
    // aros dorados y adorno en el hombro; pierna derecha con media negra, liga dorada en forma de corona y espinillera;
    // pierna izquierda al aire con tobillera. Lo que va al aire deja ver la skin del jugador. La tiara
    // (opcional) es solo la corona dorada con picos
    static final int ISH_BLACK = 0x1d1b24, ISH_GOLD = 0xdcae3e, ISH_WHITE = 0xffffff, ISH_RED = 0xc0182a;

    static List<Bone> ishtarArmor() {
        Paint black = fabric(ISH_BLACK), gold = plate(ISH_GOLD), white = fabric(ISH_WHITE),
                gem = plate(0x101018);
        // Liga y adornos dorados con picos, como una corona
        Paint crown = marked(gold, 0x8a6418, (x, y, w, h) -> y == h - 1 && x % 3 == 1);

        Bone head = new Bone("armorHead", null, 0, 24, 0);
        head.model.box(-2.6, 32.0, -1.3, 2.6, 32.6, 0.3, gold);               // corona dorada con picos
        head.model.box(-0.45, 32.6, -1.0, 0.45, 35.0, 0.0, gold);
        head.model.box(-2.2, 32.6, -0.9, -1.5, 33.9, -0.1, gold);
        head.model.box(1.5, 32.6, -0.9, 2.2, 33.9, -0.1, gold);

        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 19, -2, 4, 22, 2, white).inflate(0.6);              // top
        body.model.box(-4.2, 21.6, -2.75, 4.2, 22.3, 2.75, gold);
        body.model.box(-4.2, 18.7, -2.75, 4.2, 19.4, 2.75, gold);
        body.model.box(-0.5, 19.2, -2.85, 0.5, 21.8, -2.6, gold);
        body.model.box(-2.3, 23.2, -2.7, 2.3, 24.5, 2.7, gold);                // collar con gema negra
        body.model.box(-0.7, 22.2, -2.9, 0.7, 23.4, -2.6, gem);
        body.model.box(-4, 12, -2, 4, 13.6, 2, black).inflate(0.6);            // braguita y cinturón cruzado
        body.model.box(-4.2, 13.2, -2.8, 4.2, 13.8, 2.8, gold);
        body.model.box(-1.8, 12.3, -2.85, 1.8, 12.9, -2.6, gold);

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8.6, 19.4, -2.6, -3.4, 20.6, 2.6, gold);           // brazalete
        Bone leftArm = new Bone("armorLeftArm", null, 5, 22, 0);
        leftArm.model.box(4, 12, -2, 8, 20.6, 2, black).inflate(0.6);          // guante largo
        leftArm.model.box(3.3, 13.4, -2.7, 8.7, 14.2, 2.7, gold);
        leftArm.model.box(3.3, 17.6, -2.7, 8.7, 18.4, 2.7, gold);
        leftArm.model.box(3.3, 20.2, -2.7, 8.7, 21.2, 2.7, crown);
        leftArm.model.box(7.6, 21.0, -1.6, 8.9, 23.4, 1.6, gold);              // adorno del hombro

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 9.6, -2, 0, 12, 2, black).inflate(0.55);
        rightLeg.model.box(-4, 2.4, -2, 0, 9.6, 2, black).inflate(0.45);       // media negra
        rightLeg.model.box(-4.7, 8.4, -2.7, 0.7, 9.8, 2.7, crown);             // liga en forma de corona
        rightLeg.model.box(-3.6, 9.8, -2.75, -2.9, 10.7, -2.5, gold);
        rightLeg.model.box(-1.1, 9.8, -2.75, -0.4, 10.7, -2.5, gold);
        Bone leftLeg = new Bone("armorLeftLeg", null, 2, 12, 0);
        leftLeg.model.box(0, 9.6, -2, 4, 12, 2, black).inflate(0.55);

        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 2.6, 2, black).inflate(0.5);
        rightBoot.model.box(-3.6, 0.6, -2.95, -0.4, 6.0, -2.55, gold);         // espinillera
        Bone leftBoot = new Bone("armorLeftBoot", null, 2, 12, 0);
        leftBoot.model.box(-0.6, 2.0, -2.6, 4.6, 2.9, 2.6, gold);              // tobillera

        return List.of(head, body, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    // Mash Kyrielight (Shielder), primera ascensión (referencia del usuario): armadura azul marino muy oscuro con líneas
    // moradas que cubre el torso entero, escarcelas en capas sobre la cadera y una capa morada que cae por detrás y por
    // los lados; guanteletes hasta el codo y medias altas. Los hombros, los brazos por encima del codo y lo alto del
    // muslo van al aire (se ve la skin del jugador). Sin casco ni pelo
    static final int MASH_BLACK = 0x20203a, MASH_PLATE = 0x2c2c48, MASH_PURPLE = 0x7a55d6, MASH_CAPE = 0x5a3caa, MASH_LIGHT = 0xd9dbe6;

    static List<Bone> mashArmor() {
        Paint suit = fabric(MASH_BLACK), plateP = edged(metal(MASH_PLATE), MASH_PURPLE, 1), purple = plate(MASH_PURPLE);
        Paint lined = marked(metal(MASH_PLATE), MASH_PURPLE, (x, y, w, h) -> x == w / 2);
        Paint faulds = marked(metal(MASH_PLATE), 0x15152a, (x, y, w, h) -> y % 3 == 2);      // placas en capas
        Paint cape = marked(fabric(MASH_CAPE), 0x3d2878, (x, y, w, h) -> x % 3 == 0);       // pliegues de la capa

        Bone head = new Bone("armorHead", null, 0, 24, 0);

        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, suit).inflate(0.5);                   // traje: el torso entero
        body.model.box(-3.4, 14.5, -2.75, 3.4, 23.0, -2.3, lined);                 // peto con la línea morada en el centro
        body.model.box(-3.6, 19.2, -2.85, 3.6, 19.8, -2.6, purple);                // bajo el pecho
        body.model.box(-2.4, 23.2, -2.6, 2.4, 24.6, 2.6, plateP);                  // cuello alto
        body.model.box(-4.6, 9.6, -2.8, 4.6, 12.8, -2.3, faulds);                  // escarcelas delante
        body.model.box(-4.6, 12.4, -2.8, 4.6, 13.1, 2.8, purple);                  // cinturón morado
        body.model.box(-4.7, 3.0, 2.3, 4.7, 12.8, 2.8, cape);                      // capa por detrás
        body.model.box(-5.2, 4.0, -1.2, -4.7, 12.8, 2.8, cape);                    // y por los lados
        body.model.box(4.7, 4.0, -1.2, 5.2, 12.8, 2.8, cape);
        body.model.box(-5.4, 9.0, -2.4, -4.6, 12.8, -1.0, plateP);                 // escarcelas laterales
        body.model.box(4.6, 9.0, -2.4, 5.4, 12.8, -1.0, plateP);

        // Guanteletes hasta el codo: cubren el brazo entero (4x4) con margen, para que no asome
        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 12, -2, -4, 19, 2, suit).inflate(0.35);
        rightArm.model.box(-8.7, 13, -1.8, -8.2, 18, 1.8, lined);
        rightArm.model.box(-8.5, 18.6, -2.5, -3.5, 19.3, 2.5, purple);
        Bone leftArm = new Bone("armorLeftArm", null, 5, 22, 0);
        leftArm.model.box(4, 12, -2, 8, 19, 2, suit).inflate(0.35);
        leftArm.model.box(8.2, 13, -1.8, 8.7, 18, 1.8, lined);
        leftArm.model.box(3.5, 18.6, -2.5, 8.5, 19.3, 2.5, purple);

        // Medias altas casi hasta la ingle, con la rodillera; arriba del todo, el muslo al aire
        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 4.4, -2, 0, 10.6, 2, suit).inflate(0.4);
        rightLeg.model.box(-4.5, 10.2, -2.5, 0.5, 10.8, 2.5, purple);              // liga morada
        rightLeg.model.box(-3.3, 4.8, -2.75, -0.7, 7.6, -2.35, plateP);            // rodillera
        Bone leftLeg = new Bone("armorLeftLeg", null, 2, 12, 0);
        leftLeg.model.box(0, 4.4, -2, 4, 10.6, 2, suit).inflate(0.4);
        leftLeg.model.box(-0.5, 10.2, -2.5, 4.5, 10.8, 2.5, purple);
        leftLeg.model.box(0.7, 4.8, -2.75, 3.3, 7.6, -2.35, plateP);

        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 4.6, 2, metal(MASH_PLATE)).inflate(0.45);
        rightBoot.model.box(-4.5, 0, -2.6, 0.5, 0.6, 2.6, purple);                 // suela morada
        rightBoot.model.box(-3.2, 0.8, -2.9, -0.8, 4.0, -2.45, lined);             // espinillera
        Bone leftBoot = new Bone("armorLeftBoot", null, 2, 12, 0);
        leftBoot.model.box(0, 0, -2, 4, 4.6, 2, metal(MASH_PLATE)).inflate(0.45);
        leftBoot.model.box(-0.5, 0, -2.6, 4.5, 0.6, 2.6, purple);
        leftBoot.model.box(0.8, 0.8, -2.9, 3.2, 4.0, -2.45, lined);

        return List.of(head, body, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    // El escudo de Mash (referencia del usuario): una cruz enorme azul marino; en el centro un disco claro con puntos
    // oscuros alrededor, una pequeña figura blanca arriba y otra abajo, y la punta de abajo blanca. El asa detrás
    static Model mashShield() {
        Paint navy = edged(metal(0x2b2c4c), 0x17182c, 1), white = metal(0xe2e4ee);
        Paint disk = marked(metal(MASH_LIGHT), 0x2b2c4c, (x, y, w, h) -> (x == 1 || x == w - 2 || y == 1 || y == h - 2) && (x + y) % 2 == 0);
        Model m = new Model();
        m.box(4, -8, 7.2, 12, 30, 8.8, navy);                     // palo vertical de la cruz
        m.box(5, 30, 7.3, 11, 31.5, 8.7, navy);                   // remate redondeado arriba
        m.box(-2, 11, 7.2, 18, 21, 8.8, navy);                    // brazos de la cruz
        m.box(-3, 12, 7.3, -2, 20, 8.7, navy);
        m.box(18, 12, 7.3, 19, 20, 8.7, navy);
        m.box(3, 11, 6.8, 13, 21, 7.2, disk);                      // el disco claro del centro (por delante), con su anillo de puntos
        Paint diskEdge = metal(MASH_LIGHT);                        // y cuatro remates un poco detrás que lo redondean
        m.box(5, 21, 6.95, 11, 22, 7.2, diskEdge);
        m.box(5, 10, 6.95, 11, 11, 7.2, diskEdge);
        m.box(2, 13, 6.95, 3, 19, 7.2, diskEdge);
        m.box(13, 13, 6.95, 14, 19, 7.2, diskEdge);
        m.box(7.3, 24, 6.9, 8.7, 27, 7.2, white);                 // figura de arriba
        m.box(7.3, 3, 6.9, 8.7, 7, 7.2, white);                   // y de abajo, como una espada
        m.box(6.3, 6, 6.9, 9.7, 6.6, 7.2, white);
        m.box(5.5, -10, 7.3, 10.5, -8, 8.7, white);               // punta blanca abajo
        m.box(6.8, -12, 7.4, 9.2, -10, 8.6, white);
        m.box(7.1, 8, 8.8, 8.9, 15, 10, metal(0x3a3c4a));         // asa, por detrás
        return m;
    }
    // En tercera persona va sujeto al antebrazo, al costado, con la cara hacia fuera, como el del Capitán América (también al cubrirse)

    static final String SHIELD_DISPLAY = "{\n"
            + "    \"thirdperson_righthand\": { \"rotation\": [90, 90, 0], \"translation\": [0, 0, 5.5], \"scale\": [0.34, 0.34, 0.34] },\n"
            + "    \"thirdperson_lefthand\": { \"rotation\": [90, 90, 0], \"translation\": [0, 0, 5.5], \"scale\": [0.34, 0.34, 0.34] },\n"
            + "    \"firstperson_righthand\": { \"rotation\": [0, 180, 5], \"translation\": [0, -2, -4], \"scale\": [0.32, 0.32, 0.32] },\n"
            + "    \"firstperson_lefthand\": { \"rotation\": [0, 180, 5], \"translation\": [0, -2, -4], \"scale\": [0.32, 0.32, 0.32] },\n"
            + "    \"gui\": { \"rotation\": [15, -25, -5], \"translation\": [0, 0, 0], \"scale\": [0.42, 0.42, 0.42] },\n"
            + "    \"ground\": { \"translation\": [0, 2, 0], \"scale\": [0.35, 0.35, 0.35] },\n"
            + "    \"fixed\": { \"rotation\": [0, 180, 0], \"scale\": [0.5, 0.5, 0.5] }\n"
            + "  }";

    // Cubriéndose: el escudo más delante y girado, como el vanilla al bloquear
    static final String SHIELD_BLOCKING_DISPLAY = "{\n"
            + "    \"thirdperson_righthand\": { \"rotation\": [90, 90, 0], \"translation\": [0, 0, 5.5], \"scale\": [0.34, 0.34, 0.34] },\n"
            + "    \"thirdperson_lefthand\": { \"rotation\": [90, 90, 0], \"translation\": [0, 0, 5.5], \"scale\": [0.34, 0.34, 0.34] },\n"
            + "    \"firstperson_righthand\": { \"rotation\": [0, 180, -5], \"translation\": [-2, -3, -5], \"scale\": [0.36, 0.36, 0.36] },\n"
            + "    \"firstperson_lefthand\": { \"rotation\": [0, 180, -5], \"translation\": [-2, -3, -5], \"scale\": [0.36, 0.36, 0.36] },\n"
            + "    \"gui\": { \"rotation\": [15, -25, -5], \"translation\": [0, 0, 0], \"scale\": [0.42, 0.42, 0.42] }\n"
            + "  }";

    // Maanna, la Barca del Cielo como arco: dorada, con alas que se abren hacia las puntas y una gema azul en el centro.
    // Horizontal y con la flecha hacia +Y, como el arco de EMIYA
    static final Paint MAANNA_GOLD = metal(0xe3b545), MAANNA_WHITE = metal(0xf2ead2), MAANNA_GEM = metal(0x3a7bd8),
            MAANNA_STRING = solid(0xfff1b8), VENUS = metal(0xffd36a);

    // Maanna flotante: el mod la monta en código con muchos tramos a lo largo de un arco. Un tramo es una barra azul
    // con ribetes y volutas doradas, a lo largo de Z, que ocupa el bloque entero de largo
    static Model maannaHull() {
        int blue = 0x1d3a8f, gold = 0xe0b245;
        Paint side = marked(fabric(blue), gold, (x, y, w, h) -> y == 0 || y == h - 1
                || Math.abs(Math.floorMod(x, 10) - 5 - (y - h / 2.0) * 0.8) < 0.6);
        Paint edge = edged(plate(blue), gold, 1);
        Model m = new Model();
        m.box(6.5, 4, 0, 9.5, 12, 16, edge).face("east", side).face("west", side);
        m.box(6.2, 11.6, 0, 9.8, 12.4, 16, plate(gold));        // filo dorado por fuera del arco
        return m;
    }

    // Remate de las puntas: adorno dorado con una cinta roja
    static Model maannaProw() {
        Model m = new Model();
        m.box(6.5, 3, 4, 9.5, 13, 12, plate(0xe0b245));
        m.box(7, 13, 6, 9, 15, 10, plate(0xf2d27a));
        m.box(6.2, 6, 5, 9.8, 7.5, 11, plate(0xc0182a));
        return m;
    }

    static Model maanna(double pull) {
        Model m = new Model();
        m.box(7, 8.6, 7.25, 9, 12, 8.75, MAANNA_WHITE);                        // empuñadura
        m.box(6.6, 11.6, 7.1, 9.4, 12.6, 8.9, MAANNA_GOLD);
        m.box(7.3, 12.6, 7.3, 8.7, 13.8, 8.7, MAANNA_GEM);                      // gema
        m.box(9, 9.4, 7.4, 16, 10.8, 8.6, MAANNA_GOLD).rot("z", -22.5, 9, 10.1, 8);
        m.box(9.5, 10.8, 7.7, 15.5, 11.4, 8.3, MAANNA_WHITE).rot("z", -22.5, 9, 10.1, 8);
        m.box(15, 6.6, 7.5, 19.5, 8.0, 8.5, MAANNA_GOLD).rot("z", 22.5, 15.5, 7.3, 8);
        m.box(0, 9.4, 7.4, 7, 10.8, 8.6, MAANNA_GOLD).rot("z", 22.5, 7, 10.1, 8);
        m.box(0.5, 10.8, 7.7, 6.5, 11.4, 8.3, MAANNA_WHITE).rot("z", 22.5, 7, 10.1, 8);
        m.box(-3.5, 6.6, 7.5, 1, 8.0, 8.5, MAANNA_GOLD).rot("z", -22.5, 0.5, 7.3, 8);
        if (pull <= 0) {
            m.box(-2.5, 8.45, 7.95, 18.5, 8.55, 8.05, MAANNA_STRING);
        } else {
            double dx = pull / Math.tan(Math.toRadians(22.5)), len = pull / Math.sin(Math.toRadians(22.5)), ny = 8.5 - pull;
            if (8 - dx > -2.5) {
                m.box(-2.5, 8.45, 7.95, 8 - dx, 8.55, 8.05, MAANNA_STRING);
                m.box(8 + dx, 8.45, 7.95, 18.5, 8.55, 8.05, MAANNA_STRING);
            }
            m.box(8 - len, ny - 0.05, 7.95, 8, ny + 0.05, 8.05, MAANNA_STRING).rot("z", -22.5, 8, ny, 8);
            m.box(8, ny - 0.05, 7.95, 8 + len, ny + 0.05, 8.05, MAANNA_STRING).rot("z", 22.5, 8, ny, 8);
        }
        return m;
    }

    // Flecha de luz de Ishtar: varilla dorada con una estrella roja en la punta, de base en y=b
    static Model gemArrow(double b) {
        Model m = new Model();
        m.box(7.8, b, 7.8, 8.2, b + 13, 8.2, VENUS);
        m.box(7.3, b + 13, 7.3, 8.7, b + 14.4, 8.7, metal(ISH_RED));
        m.box(7.6, b + 14.4, 7.6, 8.4, b + 15.4, 8.4, VENUS);
        return m;
    }

    // Sasaki Kojirō: kimono azul, haori morado con forro claro (abierto, faldones animados), obi, coleta larga,
    // hakama con pliegues y tabi con zori
    static final int ASN_HAORI = 0x5a3d8a, ASN_LINING = 0xb9a6d9, ASN_KIMONO = 0x2d2f6b, ASN_HAKAMA = 0x1f2350;

    static Paint hakama() {
        Paint base = fabric(ASN_HAKAMA);
        return marked(base, 0x141838, (x, y, w, h) -> x % 3 == 2);
    }

    static List<Bone> assassinArmor() {
        Paint haori = fabric(ASN_HAORI), lined = edged(haori, ASN_LINING, 1), kimono = fabric(ASN_KIMONO), obi = plate(0xd8c9a0);
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, kimono).inflate(0.45);
        body.model.box(-4.6, 13, -2.7, -1.0, 24.4, 2.7, haori).face("north", lined);   // haori abierto delante
        body.model.box(1.0, 13, -2.7, 4.6, 24.4, 2.7, haori).face("north", lined);
        body.model.box(-1.0, 13, 2.2, 1.0, 24.4, 2.7, haori);
        body.model.box(-4.6, 11.6, -2.8, 4.6, 13.6, 2.8, obi);
        body.model.box(-1.2, 11.4, -3.2, 1.2, 13.8, -2.8, obi);                // nudo del obi
        Bone hair = new Bone("hair", "armorBody", 0, 24.5, 2.8);
        hair.model.box(-1.2, 12, 2.8, 1.2, 24.5, 4.0, KOJIRO_HAIR);
        Bone haoriBack = new Bone("haoriBack", "armorBody", 0, 13, 2.7);
        haoriBack.model.box(-4.6, 4, 2.2, 4.6, 13, 2.8, lined);
        Bone haoriFrontR = new Bone("haoriFrontR", "armorBody", -2.8, 13, -2.7);
        haoriFrontR.model.box(-4.6, 5, -2.8, -1.0, 13, -2.2, lined);
        Bone haoriFrontL = haoriFrontR.mirror("haoriFrontL", "armorBody");

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 15, -2, -4, 24, 2, haori).inflate(0.55);
        rightArm.model.box(-8.8, 13.6, -2.9, -3.2, 18.2, 2.9, lined);            // manga ancha
        Bone leftArm = rightArm.mirror("armorLeftArm", null);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4.6, 1.6, -2.6, 0.2, 12, 2.6, hakama());
        Bone leftLeg = rightLeg.mirror("armorLeftLeg", null);
        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0.6, -2, 0, 2.4, 2, fabric(0xeeeeee)).inflate(0.4);
        rightBoot.model.box(-4.3, 0, -2.6, 0.3, 0.6, 2.6, plate(0xa08050));
        Bone leftBoot = rightBoot.mirror("armorLeftBoot", null);

        return List.of(head, body, hair, haoriBack, haoriFrontL, haoriFrontR, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    // Heracles: piel gris oscura con músculos marcados, cinturón de hierro con remaches, falda de placas de hierro
    // (delante y detrás animadas) y anillas de hierro en muñecas y tobillos
    static final int BER_SKIN = 0x4a4b52, BER_IRON = 0x5d5f66;

    static Paint ironStuds() {
        Paint base = plate(BER_IRON);
        return marked(base, 0x9a9da6, (x, y, w, h) -> x % 3 == 1 && y == h / 2);
    }

    static List<Bone> berserkerArmor() {
        Paint skin = fabric(BER_SKIN), iron = plate(BER_IRON), studs = ironStuds();
        Paint muscles = marked(skin, 0x33343a, (x, y, w, h) -> {
            double d = Math.abs(x - (w - 1) / 2.0);
            return (d < 0.6 && y > h * 0.2) || y == (int) (h * 0.38) || ((y == (int) (h * 0.58) || y == (int) (h * 0.76)) && d < w * 0.3);
        });
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, skin).inflate(0.5).face("north", muscles);
        body.model.box(-4.5, 11.4, -2.7, 4.5, 13.2, 2.7, studs);               // cinturón
        body.model.box(4.3, 5.5, -2.4, 4.9, 11.6, 2.4, iron);                  // placas de los costados
        body.model.box(-4.9, 5.5, -2.4, -4.3, 11.6, 2.4, iron);
        Bone loinFront = new Bone("loinFront", "armorBody", 0, 11.6, -2.7);
        Bone loinBack = new Bone("loinBack", "armorBody", 0, 11.6, 2.7);
        for (int i = 0; i < 3; i++) {
            double x0 = -4.3 + i * 2.9;
            loinFront.model.box(x0, 4.5, -3.1, x0 + 2.7, 11.6, -2.6, iron);
            loinBack.model.box(x0, 4.5, 2.6, x0 + 2.7, 11.6, 3.1, iron);
        }

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 12, -2, -4, 24, 2, skin).inflate(0.5);
        rightArm.model.box(-8.6, 12.4, -2.6, -3.4, 15.4, 2.6, studs);           // muñequera de hierro
        Bone leftArm = rightArm.mirror("armorLeftArm", null);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 4, -2, 0, 12, 2, skin).inflate(0.4);
        Bone leftLeg = rightLeg.mirror("armorLeftLeg", null);
        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 4, 2, skin).inflate(0.45);
        rightBoot.model.box(-4.6, 2.0, -2.6, 0.6, 4.4, 2.6, studs);             // tobillera de hierro
        Bone leftBoot = rightBoot.mirror("armorLeftBoot", null);

        return List.of(head, body, loinFront, loinBack, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    static Model bonesToModel(List<Bone> bones, double dx, double dy, double dz, String... names) {
        Model m = new Model();
        for (Bone b : bones) {
            for (String name : names) {
                if (b.name.equals(name)) m.add(b.model.shifted(dx, dy, dz));
            }
        }
        return m;
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        Files.createDirectories(root.resolve("models/item"));
        Files.createDirectories(root.resolve("textures/item"));

        // ---------- Saber ----------
        // Excalibur la dibuja GeckoLib: el viento de Invisible Air, la hoja que brilla al cargar y su halo.
        // Las texturas de carga son variantes con la misma distribución; los JSON alzan la espada en primera persona
        geoModel(root, "excalibur", excaliburBones(0), "item/");
        geoTexture(root, "item/", "excalibur_charging", excaliburBones(1));
        geoTexture(root, "item/", "excalibur_charged", excaliburBones(2));
        String exOverrides = "[\n"
                + "    { \"predicate\": { \"fate_ubw:charge\": 0.01 }, \"model\": \"fate_ubw:item/excalibur_charging\" },\n"
                + "    { \"predicate\": { \"fate_ubw:charge\": 1.0 }, \"model\": \"fate_ubw:item/excalibur_charged\" }\n"
                + "  ]";
        builtinModel(root, "excalibur", "excalibur", handheld(0.7), exOverrides);
        builtinModel(root, "excalibur_charging", "excalibur", raised(0.7, -35, 5.0, 0.0, 0.75), null);
        builtinModel(root, "excalibur_charged", "excalibur", raised(0.7, -50, 6.5, -0.5, 0.8), null);
        List<Bone> saber = saberArmor();
        // saber_armor.geo.json y su textura se editan a mano en Blockbench (saber_armor.geo.bbmodel): no se regeneran
        itemModel(root, "saber_chestplate", bonesToModel(saber, 8, -4, 8, "armorBody", "skirtFront", "skirtBack", "skirtLeft",
                "skirtRight", "armorRightArm", "armorLeftArm"), armorIcon(0.5), null);
        itemModel(root, "saber_leggings", bonesToModel(saber, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "saber_boots", bonesToModel(saber, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);

        itemModel(root, "kanshou", falchion(KAN_BLADE, KAN_EDGE, KAN_GRIP, KAN_METAL, KAN_PIP), handheld(0.9), null);
        itemModel(root, "bakuya", falchion(BAK_BLADE, BAK_EDGE, BAK_GRIP, BAK_METAL, BAK_PIP), handheld(0.9), null);
        itemModel(root, "sword_arrow", swordArrow(-0.5), handheld(0.9), null);
        itemModel(root, "caladbolg", caladbolg(-0.5), handheld(0.7), null);

        String overrides = "[\n"
                + "    { \"predicate\": { \"pulling\": 1 }, \"model\": \"fate_ubw:item/archer_bow_pulling_0\" },\n"
                + "    { \"predicate\": { \"pulling\": 1, \"pull\": 0.65 }, \"model\": \"fate_ubw:item/archer_bow_pulling_1\" },\n"
                + "    { \"predicate\": { \"pulling\": 1, \"pull\": 0.9 }, \"model\": \"fate_ubw:item/archer_bow_pulling_2\" },\n"
                + "    { \"predicate\": { \"pulling\": 1, \"fate_ubw:caladbolg\": 1 }, \"model\": \"fate_ubw:item/archer_bow_caladbolg_0\" },\n"
                + "    { \"predicate\": { \"pulling\": 1, \"pull\": 0.65, \"fate_ubw:caladbolg\": 1 }, \"model\": \"fate_ubw:item/archer_bow_caladbolg_1\" },\n"
                + "    { \"predicate\": { \"pulling\": 1, \"pull\": 0.9, \"fate_ubw:caladbolg\": 1 }, \"model\": \"fate_ubw:item/archer_bow_caladbolg_2\" }\n"
                + "  ]";
        itemModel(root, "archer_bow", bow(0), BOW_DISPLAY, overrides);
        double[] pulls = {1.5, 3.0, 4.2};
        for (int i = 0; i < 3; i++) {
            double nock = 8.5 - pulls[i];
            itemModel(root, "archer_bow_pulling_" + i, bow(pulls[i]).add(swordArrow(nock)), BOW_DISPLAY, null);
            itemModel(root, "archer_bow_caladbolg_" + i, bow(pulls[i]).add(caladbolg(nock)), BOW_DISPLAY, null);
        }

        List<Bone> armor = archerArmor();
        armorModel(root, "archer_armor", armor);
        // Iconos 3D de inventario con los mismos cubos que la armadura puesta
        itemModel(root, "archer_chestplate", bonesToModel(armor, 8, -4, 8, "armorBody", "coatBack", "coatLeft", "coatRight",
                "coatFrontL", "coatFrontR", "armorRightArm", "armorLeftArm"), armorIcon(0.5), null);
        itemModel(root, "archer_leggings", bonesToModel(armor, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "archer_boots", bonesToModel(armor, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);

        // ---------- Lancer ----------
        geoModel(root, "gae_bolg", oneBone(gaeBolg()), "item/");
        builtinModel(root, "gae_bolg", "gae_bolg", handheld(0.5, 0.5), null);
        List<Bone> lancer = lancerArmor();
        armorModel(root, "lancer_armor", lancer);
        itemModel(root, "lancer_chestplate", bonesToModel(lancer, 8, -4, 8, "armorBody", "ponytail", "armorRightArm", "armorLeftArm"),
                armorIcon(0.5), null);
        itemModel(root, "lancer_leggings", bonesToModel(lancer, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "lancer_boots", bonesToModel(lancer, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);

        // ---------- Rider ----------
        itemModel(root, "rider_dagger", chainDagger(), handheld(0.7), null);
        itemModel(root, "bellerophon", bridle(), HELD_OBJECT, null);
        List<Bone> rider = riderArmor();
        armorModel(root, "rider_armor", rider);
        itemModel(root, "rider_helmet", bonesToModel(rider, 8, -20, 8, "armorHead"), armorIcon(0.7), null);
        itemModel(root, "rider_chestplate", bonesToModel(rider, 8, -4, 8, "armorBody", "hair", "armorRightArm", "armorLeftArm"),
                armorIcon(0.5), null);
        itemModel(root, "rider_leggings", bonesToModel(rider, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "rider_boots", bonesToModel(rider, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);
        geoModel(root, "pegasus", pegasus());

        // ---------- Gilgamesh ----------
        // Ea la dibuja GeckoLib (cilindros que giran y líneas que brillan); el JSON solo da las posiciones en mano
        geoModel(root, "ea", eaBones(), "item/");
        builtinModel(root, "ea", "ea", handheld(0.7), null);
        itemModel(root, "gate_of_babylon", babylonKey(), handheld(0.9), null);
        List<Bone> gilgamesh = gilgameshArmor();
        armorModel(root, "gilgamesh_armor", gilgamesh);
        itemModel(root, "gilgamesh_chestplate", bonesToModel(gilgamesh, 8, -4, 8, "armorBody", "tassets", "armorRightArm", "armorLeftArm"),
                armorIcon(0.5), null);
        itemModel(root, "gilgamesh_leggings", bonesToModel(gilgamesh, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "gilgamesh_boots", bonesToModel(gilgamesh, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);

        // ---------- Unlimited Blade Works ----------
        itemModel(root, "unlimited_blade_works", ubwSword(), handheld(0.8), null, 8, "{\"animation\":{\"frametime\":2,\"interpolate\":true}}");
        itemModel(root, "trace_on", traceOn(), handheld(0.8), null, 8, "{\"animation\":{\"frametime\":2,\"interpolate\":true}}");

        // ---------- Guerra del Santo Grial y Masters ----------
        itemModel(root, "holy_grail", holyGrail(), HELD_OBJECT, null, 8, "{\"animation\":{\"frametime\":3,\"interpolate\":true}}");
        itemModel(root, "summoning_circle", summoningCircle(), FLAT_ICON, null);
        geoModel(root, "hrunting", oneBone(hrunting(-0.5)), "item/");
        builtinModel(root, "hrunting", "hrunting", handheld(0.8), null);
        itemModel(root, "rin_jewel", rinJewel(), HELD_OBJECT, null);
        itemModel(root, "shirou_poster", shirouPoster(), handheld(0.8), null);
        itemModel(root, "zelzeriz", zelzeriz(), HELD_OBJECT, null);
        skin(root, "berserker", 0x4a4b52, 0x141418, 0xd01020);
        skin(root, "lancer", 0xe8c4a8, 0x1f3f9a, 0xc01020);
        skin(root, "assassin", 0xecd0b4, 0x4b3a8f, 0x3a5fd0);

        // ---------- Caster ----------
        geoModel(root, "rule_breaker", oneBone(ruleBreaker()), "item/");
        builtinModel(root, "rule_breaker", "rule_breaker", handheld(1.0), null);
        List<Bone> caster = casterArmor();
        armorModel(root, "caster_armor", caster);
        itemModel(root, "caster_hood", bonesToModel(caster, 8, -20, 8, "armorHead"), armorIcon(0.6), null);
        itemModel(root, "caster_chestplate", bonesToModel(caster, 8, -4, 8, "armorBody", "capeBack", "capeLeft", "capeRight",
                "armorRightArm", "armorLeftArm"), armorIcon(0.5), null);
        itemModel(root, "caster_leggings", bonesToModel(caster, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "caster_boots", bonesToModel(caster, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);

        // ---------- Ishtar ----------
        String maannaOverrides = "[\n"
                + "    { \"predicate\": { \"pulling\": 1 }, \"model\": \"fate_ubw:item/maanna_pulling_0\" },\n"
                + "    { \"predicate\": { \"pulling\": 1, \"pull\": 0.65 }, \"model\": \"fate_ubw:item/maanna_pulling_1\" },\n"
                + "    { \"predicate\": { \"pulling\": 1, \"pull\": 0.9 }, \"model\": \"fate_ubw:item/maanna_pulling_2\" }\n"
                + "  ]";
        itemModel(root, "maanna", maanna(0), BOW_DISPLAY, maannaOverrides);
        itemModel(root, "maanna_hull", maannaHull(), handheld(1.0), null);
        itemModel(root, "maanna_prow", maannaProw(), handheld(1.0), null);
        for (int i = 0; i < 3; i++) {
            itemModel(root, "maanna_pulling_" + i, maanna(pulls[i]).add(gemArrow(8.5 - pulls[i])), BOW_DISPLAY, null);
        }
        List<Bone> ishtar = ishtarArmor();
        armorModel(root, "ishtar_armor", ishtar);
        itemModel(root, "ishtar_tiara", bonesToModel(ishtar, 8, -20, 8, "armorHead"), armorIcon(0.6), null);
        itemModel(root, "ishtar_chestplate", bonesToModel(ishtar, 8, -4, 8, "armorBody", "armorRightArm", "armorLeftArm"),
                armorIcon(0.5), null);
        itemModel(root, "ishtar_leggings", bonesToModel(ishtar, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "ishtar_boots", bonesToModel(ishtar, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);

        // ---------- Mash Kyrielight ----------
        List<Bone> mash = mashArmor();
        armorModel(root, "mash_armor", mash);
        itemModel(root, "mash_chestplate", bonesToModel(mash, 8, -4, 8, "armorBody", "armorRightArm", "armorLeftArm"), armorIcon(0.5), null);
        itemModel(root, "mash_leggings", bonesToModel(mash, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "mash_boots", bonesToModel(mash, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);
        itemModel(root, "mash_shield", mashShield(), SHIELD_DISPLAY,
                "[\n    { \"predicate\": { \"blocking\": 1 }, \"model\": \"fate_ubw:item/mash_shield_blocking\" }\n  ]");
        itemModel(root, "mash_shield_blocking", mashShield(), SHIELD_BLOCKING_DISPLAY, null);

        // ---------- Assassin ----------
        itemModel(root, "monohoshizao", monohoshizao(), handheld(0.55, 0.6), null);
        List<Bone> assassin = assassinArmor();
        armorModel(root, "assassin_armor", assassin);
        itemModel(root, "assassin_chestplate", bonesToModel(assassin, 8, -4, 8, "armorBody", "hair", "haoriBack", "haoriFrontL",
                "haoriFrontR", "armorRightArm", "armorLeftArm"), armorIcon(0.5), null);
        itemModel(root, "assassin_leggings", bonesToModel(assassin, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "assassin_boots", bonesToModel(assassin, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);

        // ---------- Berserker ----------
        // berserker_axe_sword: modelo del usuario (tools/blockbench/berserker_axe_sword.bbmodel), no se genera
        List<Bone> berserker = berserkerArmor();
        armorModel(root, "berserker_armor", berserker);
        itemModel(root, "berserker_chestplate", bonesToModel(berserker, 8, -4, 8, "armorBody", "loinFront", "loinBack",
                "armorRightArm", "armorLeftArm"), armorIcon(0.5), null);
        itemModel(root, "berserker_leggings", bonesToModel(berserker, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "berserker_boots", bonesToModel(berserker, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);
        // Espadas clavadas: cada modelo del mod, boca abajo y enterrado; el blockstate elige uno y un giro al azar
        Map<String, Model> graves = new LinkedHashMap<>();
        graves.put("kanshou", buried(falchion(KAN_BLADE, KAN_EDGE, KAN_GRIP, KAN_METAL, KAN_PIP), 20, 5));
        graves.put("bakuya", buried(falchion(BAK_BLADE, BAK_EDGE, BAK_GRIP, BAK_METAL, BAK_PIP), 20, 5));
        graves.put("arrow", buried(swordArrow(-0.5), 16, 4));
        graves.put("caladbolg", buried(caladbolg(-0.5), 24.3, 6));
        graves.put("excalibur", buried(excalibur(0), 24.5, 6));
        graves.put("gae_bolg", buried(gaeBolg(), 30.5, 12));
        graves.put("ubw", buried(ubwSword(), 22, 5));
        StringBuilder variants = new StringBuilder("{\n  \"variants\": {\n    \"\": [\n");
        int v = 0;
        for (Map.Entry<String, Model> grave : graves.entrySet()) {
            blockModel(root, "ubw_sword_" + grave.getKey(), grave.getValue());
            for (int rot = 0; rot < 360; rot += 90) {
                if (v++ > 0) variants.append(",\n");
                variants.append("      { \"model\": \"fate_ubw:block/ubw_sword_").append(grave.getKey()).append("\", \"y\": ").append(rot).append(" }");
            }
        }
        // Y armas vanilla: su dibujo plano en dos planos cruzados, girado para que la diagonal quede vertical, punta abajo
        for (String weapon : new String[]{"wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword",
                "netherite_sword", "trident"}) {
            vanillaGrave(root, weapon);
            for (int rot = 0; rot < 360; rot += 90) {
                variants.append(",\n      { \"model\": \"fate_ubw:block/ubw_sword_").append(weapon).append("\", \"y\": ").append(rot).append(" }");
            }
        }
        variants.append("\n    ]\n  }\n}\n");
        Files.createDirectories(root.resolve("blockstates"));
        Files.writeString(root.resolve("blockstates/ubw_sword.json"), variants);
    }
}
