import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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

    static Paint solid(int c) {
        return (x, y, w, h, s) -> shade(c, x, y, w, h, s, 0.05);
    }

    static Paint metal(int c) {
        return (x, y, w, h, s) -> shade(mul(c, 1.18 - 0.36 * y / Math.max(1, h - 1)), x, y, w, h, s, 0.04);
    }

    // Patrón de rombos (caparazón de tortuga de Kanshō y Bakuya)
    static Paint lattice(int base, int line) {
        return (x, y, w, h, s) -> {
            boolean l = Math.floorMod(x + y, 4) == 0 || Math.floorMod(x - y, 4) == 0;
            return shade(l ? line : base, x, y, w, h, s, 0.05);
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
    static final Paint KAN_BLADE = lattice(0x1c1c24, 0x7a1a1a), KAN_EDGE = metal(0x8f95a3),
            KAN_GRIP = wrap(0x5a1414, 0x2e0909), KAN_METAL = metal(0x8c6d1f);
    static final Paint BAK_BLADE = lattice(0xe6e7ec, 0x8597ad), BAK_EDGE = metal(0xf3f5f9),
            BAK_GRIP = wrap(0xd5d6dc, 0x8e8f99), BAK_METAL = metal(0xb5bac4);
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
                Face f = new Face(dirs[i], px(dims[i][0]), px(dims[i][1]), c.paint, seed++);
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
        return String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "");
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
                b.append(", \"rotation\": { \"angle\": ").append(n(c.angle)).append(", \"axis\": \"").append(c.axis)
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
            m.box(left[i], y0, 7.7, left[i] + 1.4, y0 + 2.1, 8.3, metal(colors[i]));
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

    // Medea: túnica morada con capa que cae desde los hombros (animada) y mangas acampanadas
    static List<Bone> casterArmor() {
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, ROBE).inflate(1.0);
        body.model.box(-4, 22, -2, 4, 24, 2, ROBE_DARK).inflate(1.15);
        body.model.box(-4, 11.5, -2, 4, 13, 2, ROBE_GOLD).inflate(1.25);
        body.model.box(-1, 14, -3.4, 1, 22, -3.0, ROBE_GOLD);
        Bone capeBack = new Bone("capeBack", "armorBody", 0, 24, 3.4);
        capeBack.model.box(-5.2, 0.5, 3.4, 5.2, 24.5, 4.0, ROBE_DARK);
        Bone capeLeft = new Bone("capeLeft", "armorBody", 5.4, 24, 0);
        capeLeft.model.box(5.4, 4, -2.6, 6.0, 24, 3.4, ROBE_DARK);
        Bone capeRight = new Bone("capeRight", "armorBody", -5.4, 24, 0);
        capeRight.model.box(-6.0, 4, -2.6, -5.4, 24, 3.4, ROBE_DARK);

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 16, -2, -4, 24, 2, ROBE).inflate(1.0);
        rightArm.model.box(-8.5, 11.5, -2.5, -3.5, 16, 2.5, ROBE).inflate(0.9);
        rightArm.model.box(-8.5, 11.2, -2.5, -3.5, 12, 2.5, ROBE_GOLD).inflate(0.95);
        Bone leftArm = new Bone("armorLeftArm", null, 5, 22, 0);
        leftArm.model.box(4, 16, -2, 8, 24, 2, ROBE).inflate(1.0);
        leftArm.model.box(3.5, 11.5, -2.5, 8.5, 16, 2.5, ROBE).inflate(0.9);
        leftArm.model.box(3.5, 11.2, -2.5, 8.5, 12, 2.5, ROBE_GOLD).inflate(0.95);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 2, -2, 0, 12, 2, ROBE).inflate(0.7);
        Bone leftLeg = new Bone("armorLeftLeg", null, 2, 12, 0);
        leftLeg.model.box(0, 2, -2, 4, 12, 2, ROBE).inflate(0.7);

        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 3, 2, ROBE_DARK).inflate(0.9);
        rightBoot.model.box(-4, 0, -3.3, 0, 1.2, -2.8, ROBE_GOLD);
        Bone leftBoot = new Bone("armorLeftBoot", null, 2, 12, 0);
        leftBoot.model.box(0, 0, -2, 4, 3, 2, ROBE_DARK).inflate(0.9);
        leftBoot.model.box(0, 0, -3.3, 4, 1.2, -2.8, ROBE_GOLD);

        return List.of(head, body, capeBack, capeLeft, capeRight, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    // Sasaki Kojirō: kimono azul, haori morado abierto (faldones animados), coleta larga, hakama ancho y tabi con zori
    static List<Bone> assassinArmor() {
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, KIMONO).inflate(1.0);
        body.model.box(-4, 13, -2, -1, 24, 2, HAORI).inflate(1.15);       // haori, abierto delante
        body.model.box(1, 13, -2, 4, 24, 2, HAORI).inflate(1.15);
        body.model.box(-4, 11.5, -2, 4, 13.5, 2, OBI).inflate(1.25);
        Bone hair = new Bone("hair", "armorBody", 0, 24.5, 3.2);
        hair.model.box(-1.2, 12, 3.2, 1.2, 24.5, 4.4, KOJIRO_HAIR);
        Bone haoriBack = new Bone("haoriBack", "armorBody", 0, 12, 3.3);
        haoriBack.model.box(-4.8, 4, 3.3, 4.8, 12, 3.9, HAORI);
        Bone haoriFrontL = new Bone("haoriFrontL", "armorBody", 2.5, 12, -3.3);
        haoriFrontL.model.box(1.4, 5, -3.9, 4.8, 12, -3.3, HAORI);
        Bone haoriFrontR = new Bone("haoriFrontR", "armorBody", -2.5, 12, -3.3);
        haoriFrontR.model.box(-4.8, 5, -3.9, -1.4, 12, -3.3, HAORI);

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 15, -2, -4, 24, 2, HAORI).inflate(1.1);
        rightArm.model.box(-8.6, 14, -2.8, -3.4, 18, 2.8, HAORI).inflate(0.6);
        Bone leftArm = new Bone("armorLeftArm", null, 5, 22, 0);
        leftArm.model.box(4, 15, -2, 8, 24, 2, HAORI).inflate(1.1);
        leftArm.model.box(3.4, 14, -2.8, 8.6, 18, 2.8, HAORI).inflate(0.6);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 2, -2, 0, 12, 2, HAKAMA).inflate(1.0);
        Bone leftLeg = new Bone("armorLeftLeg", null, 2, 12, 0);
        leftLeg.model.box(0, 2, -2, 4, 12, 2, HAKAMA).inflate(1.0);

        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0.6, -2, 0, 3, 2, TABI).inflate(0.5);
        rightBoot.model.box(-4, 0, -2.6, 0, 0.6, 2.6, ZORI).inflate(0.4);
        Bone leftBoot = new Bone("armorLeftBoot", null, 2, 12, 0);
        leftBoot.model.box(0, 0.6, -2, 4, 3, 2, TABI).inflate(0.5);
        leftBoot.model.box(0, 0, -2.6, 4, 0.6, 2.6, ZORI).inflate(0.4);

        return List.of(head, body, hair, haoriBack, haoriFrontL, haoriFrontR, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    // Heracles: piel oscura, pectorales, brazales de bronce, cinturón con taparrabos (animado) y grebas
    static List<Bone> berserkerArmor() {
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, SKIN).inflate(0.6);
        body.model.box(-3.8, 18, -2.9, -0.2, 22, -2.4, SKIN_DARK);       // pectorales
        body.model.box(0.2, 18, -2.9, 3.8, 22, -2.4, SKIN_DARK);
        body.model.box(-4, 11.5, -2, 4, 13.5, 2, BRONZE).inflate(1.1);
        Bone clothFront = new Bone("loinFront", "armorBody", 0, 12, -3.2);
        clothFront.model.box(-2.5, 4, -3.6, 2.5, 12, -3.1, LOINCLOTH);
        Bone clothBack = new Bone("loinBack", "armorBody", 0, 12, 3.2);
        clothBack.model.box(-3, 4, 3.1, 3, 12, 3.6, LOINCLOTH);

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 12, -2, -4, 24, 2, SKIN).inflate(0.7);
        rightArm.model.box(-8, 12, -2, -4, 16, 2, BRONZE).inflate(1.0);
        rightArm.model.box(-8.6, 21, -2.6, -3.4, 24.6, 2.6, BRONZE).inflate(0.3);
        Bone leftArm = new Bone("armorLeftArm", null, 5, 22, 0);
        leftArm.model.box(4, 12, -2, 8, 24, 2, SKIN).inflate(0.7);
        leftArm.model.box(4, 12, -2, 8, 16, 2, BRONZE).inflate(1.0);
        leftArm.model.box(3.4, 21, -2.6, 8.6, 24.6, 2.6, BRONZE).inflate(0.3);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 4, -2, 0, 12, 2, SKIN).inflate(0.55);
        rightLeg.model.box(-4, 5, -2.9, 0, 7.5, -2.4, BRONZE);
        Bone leftLeg = new Bone("armorLeftLeg", null, 2, 12, 0);
        leftLeg.model.box(0, 4, -2, 4, 12, 2, SKIN).inflate(0.55);
        leftLeg.model.box(0, 5, -2.9, 4, 7.5, -2.4, BRONZE);

        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 5, 2, GREAVE).inflate(0.9);
        Bone leftBoot = new Bone("armorLeftBoot", null, 2, 12, 0);
        leftBoot.model.box(0, 0, -2, 4, 5, 2, GREAVE).inflate(0.9);

        return List.of(head, body, clothFront, clothBack, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
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
            m.box(7.3, y0, 7.75, 8.7, y0 + 2.4, 8.25, i % 2 == 0 ? HRUNT_RED : HRUNT_DARK);
            m.box(6.6, y0 + 0.4, 7.85, 7.3, y0 + 1.2, 8.15, HRUNT_RED);        // púas
            m.box(8.7, y0 + 1.2, 7.85, 9.4, y0 + 2.0, 8.15, HRUNT_RED);
        }
        m.box(7.6, b + 16.8, 7.8, 8.4, b + 18.6, 8.2, HRUNT_RED);
        m.box(7.85, b + 18.6, 7.85, 8.15, b + 19.6, 8.15, HRUNT_RED);
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

    // Invisible Air: el viento que envuelve a Excalibur. Solo se ven la empuñadura y un remolino translúcido
    static Paint wind() {
        return (x, y, w, h, s) -> Math.floorMod(x + y + FRAME * 2, 6) < 2 ? (110 << 24) | 0xe4f2ff : (40 << 24) | 0xbcd6ff;
    }

    static Model excaliburAir() {
        Model m = new Model();
        m.box(7.2, -1, 7.2, 8.8, 0.5, 8.8, GOLD);
        m.box(7.5, 0.5, 7.5, 8.5, 4.5, 8.5, EX_GRIP);
        m.box(6.0, 4.5, 6.9, 10.0, 24.5, 9.1, wind());
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

    static void geoModel(Path root, String name, List<Bone> bones, String kind) throws IOException {
        List<Cube> all = new ArrayList<>();
        for (Bone bone : bones) all.addAll(bone.model.cubes);
        BufferedImage img = atlas(all);
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
        Path tex = root.resolve("textures/" + kind + name + ".png");
        Files.createDirectories(tex.getParent());
        ImageIO.write(img, "png", tex.toFile());
        if (all.stream().anyMatch(c -> c.glow != null)) {
            ImageIO.write(glowmask(all, img), "png", root.resolve("textures/" + kind + name + "_glowmask.png").toFile());
        }
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
    // Kanshō / Bakuya: sables chinos de un solo filo. Empuñadura centrada en y≈2.6
    static Model falchion(Paint blade, Paint edge, Paint grip, Paint metal) {
        Model m = new Model();
        m.box(7.25, -0.5, 7.25, 8.75, 0.75, 8.75, metal);      // pomo
        m.box(7.5, 0.75, 7.5, 8.5, 4.5, 8.5, grip);             // empuñadura
        m.box(6.75, 4.5, 7.0, 9.75, 5.5, 9.0, metal);           // guarda
        m.box(7.0, 5.5, 7.6, 9.25, 13.5, 8.4, blade);           // hoja
        m.box(9.25, 5.5, 7.75, 9.75, 13.5, 8.25, edge);         // filo
        m.box(7.25, 13.5, 7.6, 9.75, 17.5, 8.4, blade);         // hoja ensanchada
        m.box(9.75, 13.5, 7.75, 10.25, 17.5, 8.25, edge);
        m.box(8.0, 17.5, 7.65, 10.25, 19.0, 8.35, blade);       // punta
        m.box(9.0, 19.0, 7.7, 10.25, 20.0, 8.3, edge);
        return m;
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

    // ---------- armadura (coordenadas de jugador: y desde los pies, frente hacia -Z) ----------
    static List<Bone> archerArmor() {
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, BLACK_ARMOR).inflate(1.0);
        body.model.box(-4, 22, -2, 4, 24, 2, SILVER).inflate(1.15);
        body.model.box(-4, 11, -2, 4, 13, 2, BELT).inflate(1.3);
        body.model.box(-1, 11, -3.6, 1, 13, -3.1, SILVER);

        // Faldón rojo del Sudario de Magdalena: atrás, a los lados y dos solapas delante (abierto en el centro)
        Bone coatBack = new Bone("coatBack", "armorBody", 0, 12, 3.3);
        coatBack.model.box(-4.6, 1, 3.3, 4.6, 12, 3.9, RED_CLOTH);
        Bone coatLeft = new Bone("coatLeft", "armorBody", 4.6, 12, 0);
        coatLeft.model.box(4.6, 2, -2.5, 5.2, 12, 3.3, RED_CLOTH);
        Bone coatRight = new Bone("coatRight", "armorBody", -4.6, 12, 0);
        coatRight.model.box(-5.2, 2, -2.5, -4.6, 12, 3.3, RED_CLOTH);
        Bone coatFrontL = new Bone("coatFrontL", "armorBody", 2.5, 12, -3.3);
        coatFrontL.model.box(1.2, 4, -3.9, 4.6, 12, -3.3, RED_CLOTH);
        Bone coatFrontR = new Bone("coatFrontR", "armorBody", -2.5, 12, -3.3);
        coatFrontR.model.box(-4.6, 4, -3.9, -1.2, 12, -3.3, RED_CLOTH);

        // Mangas rojas sueltas y guanteletes negros
        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 14, -2, -4, 23, 2, RED_CLOTH).inflate(1.1);
        rightArm.model.box(-8, 12, -2, -4, 15, 2, BLACK_ARMOR).inflate(0.8);
        Bone leftArm = new Bone("armorLeftArm", null, 5, 22, 0);
        leftArm.model.box(4, 14, -2, 8, 23, 2, RED_CLOTH).inflate(1.1);
        leftArm.model.box(4, 12, -2, 8, 15, 2, BLACK_ARMOR).inflate(0.8);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 3, -2, 0, 12, 2, BLACK_ARMOR).inflate(0.5);
        rightLeg.model.box(-4, 5.5, -3.0, 0, 8, -2.5, SILVER);
        Bone leftLeg = new Bone("armorLeftLeg", null, 2, 12, 0);
        leftLeg.model.box(0, 3, -2, 4, 12, 2, BLACK_ARMOR).inflate(0.5);
        leftLeg.model.box(0, 5.5, -3.0, 4, 8, -2.5, SILVER);

        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 4, 2, BLACK_ARMOR).inflate(0.9);
        rightBoot.model.box(-4, 0, -3.4, 0, 1.5, -2.9, SILVER);
        Bone leftBoot = new Bone("armorLeftBoot", null, 2, 12, 0);
        leftBoot.model.box(0, 0, -2, 4, 4, 2, BLACK_ARMOR).inflate(0.9);
        leftBoot.model.box(0, 0, -3.4, 4, 1.5, -2.9, SILVER);

        return List.of(head, body, coatBack, coatLeft, coatRight, coatFrontL, coatFrontR,
                rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
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

    // Rider: Breaker Gorgon (venda y flequillo) en la cabeza; vestido negro y melena morada en el cuerpo;
    // medias con liga morada; botas altas negras
    static List<Bone> riderArmor() {
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        head.model.box(-4, 28.5, -4, 4, 32.5, 4, PURPLE_HAIR).inflate(0.75);
        head.model.box(-4, 26.5, -4, 4, 28.5, 4, BLINDFOLD).inflate(0.6);

        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, BLACK_DRESS).inflate(1.0);
        body.model.box(-4, 22, -2, 4, 24, 2, DARK_PURPLE).inflate(1.1);
        body.model.box(-4.5, 9, -2.6, 4.5, 12, 2.6, BLACK_DRESS);
        Bone hair = new Bone("hair", "armorBody", 0, 24.5, 3.3);
        hair.model.box(-4, 9, 3.3, 4, 24.5, 4.6, PURPLE_HAIR);

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 12, -2, -4, 18, 2, BLACK_DRESS).inflate(0.9);
        rightArm.model.box(-8, 20, -2, -4, 21.5, 2, DARK_PURPLE).inflate(0.6);
        Bone leftArm = new Bone("armorLeftArm", null, 5, 22, 0);
        leftArm.model.box(4, 12, -2, 8, 18, 2, BLACK_DRESS).inflate(0.9);
        leftArm.model.box(4, 20, -2, 8, 21.5, 2, DARK_PURPLE).inflate(0.6);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 4, -2, 0, 12, 2, BLACK_DRESS).inflate(0.5);
        rightLeg.model.box(-4, 8, -2, 0, 9, 2, DARK_PURPLE).inflate(0.6);
        Bone leftLeg = new Bone("armorLeftLeg", null, 2, 12, 0);
        leftLeg.model.box(0, 4, -2, 4, 12, 2, BLACK_DRESS).inflate(0.5);
        leftLeg.model.box(0, 8, -2, 4, 9, 2, DARK_PURPLE).inflate(0.6);

        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 7, 2, BLACK_BOOT).inflate(0.9);
        rightBoot.model.box(-4, 0, -3.2, 0, 1.2, -2.7, DARK_PURPLE);
        Bone leftBoot = new Bone("armorLeftBoot", null, 2, 12, 0);
        leftBoot.model.box(0, 0, -2, 4, 7, 2, BLACK_BOOT).inflate(0.9);
        leftBoot.model.box(0, 0, -3.2, 4, 1.2, -2.7, DARK_PURPLE);

        return List.of(head, body, hair, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

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

    // Gilgamesh: coraza dorada con gema azul, cinturón rojo, escarcelas (animadas), grandes hombreras, grebas y escarpes de oro
    static List<Bone> gilgameshArmor() {
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, GOLD_ARMOR).inflate(1.0);
        body.model.box(-4, 11.5, -2, 4, 13, 2, RED_TRIM).inflate(1.25);
        body.model.box(-1, 19, -3.4, 1, 21, -3.0, EX_GEM);
        Bone tassets = new Bone("tassets", "armorBody", 0, 12, 0);
        tassets.model.box(-4.4, 5, -3.7, -0.3, 12, -3.2, GOLD_ARMOR);
        tassets.model.box(0.3, 5, -3.7, 4.4, 12, -3.2, GOLD_ARMOR);
        tassets.model.box(-4.4, 5, 3.2, 4.4, 12, 3.7, GOLD_ARMOR);

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 12, -2, -4, 20, 2, GOLD_ARMOR).inflate(0.9);
        rightArm.model.box(-9.5, 20, -3, -3.5, 25, 3, GOLD_ARMOR).inflate(0.3);
        Bone leftArm = new Bone("armorLeftArm", null, 5, 22, 0);
        leftArm.model.box(4, 12, -2, 8, 20, 2, GOLD_ARMOR).inflate(0.9);
        leftArm.model.box(3.5, 20, -3, 9.5, 25, 3, GOLD_ARMOR).inflate(0.3);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 3, -2, 0, 12, 2, GOLD_ARMOR).inflate(0.5);
        rightLeg.model.box(-3, 6, -3.0, -1, 8, -2.5, RED_TRIM);
        Bone leftLeg = new Bone("armorLeftLeg", null, 2, 12, 0);
        leftLeg.model.box(0, 3, -2, 4, 12, 2, GOLD_ARMOR).inflate(0.5);
        leftLeg.model.box(1, 6, -3.0, 3, 8, -2.5, RED_TRIM);

        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 5, 2, GOLD_ARMOR).inflate(0.9);
        rightBoot.model.box(-3.5, 0, -3.6, -0.5, 1.5, -2.9, GOLD);
        Bone leftBoot = new Bone("armorLeftBoot", null, 2, 12, 0);
        leftBoot.model.box(0, 0, -2, 4, 5, 2, GOLD_ARMOR).inflate(0.9);
        leftBoot.model.box(0.5, 0, -3.6, 3.5, 1.5, -2.9, GOLD);

        return List.of(head, body, tassets, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
    }

    // Gáe Bolg: lanza carmesí con púas. Agarre en y≈2.5, igual que las espadas; total de -12 a 30.5
    static Model gaeBolg() {
        Model m = new Model();
        m.box(7.4, -12, 7.4, 8.6, -10.5, 8.6, SPEAR_METAL);     // regatón
        m.box(7.6, -10.5, 7.6, 8.4, 21, 8.4, SPEAR_SHAFT);      // asta
        m.box(7.35, 0.5, 7.35, 8.65, 4.5, 8.65, SPEAR_GRIP);    // agarre
        m.box(7.2, 21, 7.2, 8.8, 22, 8.8, SPEAR_METAL);         // anillo
        m.box(7.0, 22, 7.6, 9.0, 26, 8.4, SPEAR_HEAD);          // hoja
        m.box(7.4, 26, 7.65, 8.6, 29, 8.35, SPEAR_HEAD);
        m.box(7.75, 29, 7.7, 8.25, 30.5, 8.3, SPEAR_EDGE);      // punta
        // Púas que apuntan hacia atrás, a ambos lados de la hoja
        for (double y : new double[]{22.5, 25.0}) {
            m.box(5.6, y, 7.75, 7.0, y + 0.8, 8.25, SPEAR_EDGE).rot("z", 45, 7.0, y + 0.4, 8);
            m.box(9.0, y, 7.75, 10.4, y + 0.8, 8.25, SPEAR_EDGE).rot("z", -45, 9.0, y + 0.4, 8);
        }
        return m;
    }

    // Lancer: traje azul ceñido, coraza, hombreras y brazales de plata, y su coleta azul
    static List<Bone> lancerArmor() {
        Bone head = new Bone("armorHead", null, 0, 24, 0);
        Bone body = new Bone("armorBody", null, 0, 24, 0);
        body.model.box(-4, 12, -2, 4, 24, 2, BLUE_SUIT).inflate(1.0);
        body.model.box(-4, 18, -2, 4, 23, 2, LANCER_SILVER).inflate(1.15);
        body.model.box(-4, 11.5, -2, 4, 13, 2, LANCER_DARK).inflate(1.25);

        Bone ponytail = new Bone("ponytail", "armorBody", 0, 25, 3.2);
        ponytail.model.box(-0.75, 14, 3.2, 0.75, 25, 4.2, HAIR);
        ponytail.model.box(-0.5, 12, 3.3, 0.5, 14, 4.0, HAIR);
        ponytail.model.box(-1.0, 22.5, 3.1, 1.0, 23.5, 4.4, LANCER_SILVER);

        Bone rightArm = new Bone("armorRightArm", null, -5, 22, 0);
        rightArm.model.box(-8, 12, -2, -4, 24, 2, BLUE_SUIT).inflate(0.8);
        rightArm.model.box(-8, 12, -2, -4, 15.5, 2, LANCER_SILVER).inflate(1.0);
        rightArm.model.box(-8.75, 20.5, -2.75, -3.25, 24.75, 2.75, LANCER_SILVER).inflate(0.5);
        Bone leftArm = new Bone("armorLeftArm", null, 5, 22, 0);
        leftArm.model.box(4, 12, -2, 8, 24, 2, BLUE_SUIT).inflate(0.8);
        leftArm.model.box(4, 12, -2, 8, 15.5, 2, LANCER_SILVER).inflate(1.0);
        leftArm.model.box(3.25, 20.5, -2.75, 8.75, 24.75, 2.75, LANCER_SILVER).inflate(0.5);

        Bone rightLeg = new Bone("armorRightLeg", null, -2, 12, 0);
        rightLeg.model.box(-4, 3, -2, 0, 12, 2, BLUE_SUIT).inflate(0.5);
        rightLeg.model.box(-4, 5.5, -3.0, 0, 8, -2.5, LANCER_SILVER);
        Bone leftLeg = new Bone("armorLeftLeg", null, 2, 12, 0);
        leftLeg.model.box(0, 3, -2, 4, 12, 2, BLUE_SUIT).inflate(0.5);
        leftLeg.model.box(0, 5.5, -3.0, 4, 8, -2.5, LANCER_SILVER);

        Bone rightBoot = new Bone("armorRightBoot", null, -2, 12, 0);
        rightBoot.model.box(-4, 0, -2, 0, 4, 2, LANCER_SILVER).inflate(0.9);
        rightBoot.model.box(-4, 0, -3.4, 0, 1.5, -2.9, LANCER_DARK);
        Bone leftBoot = new Bone("armorLeftBoot", null, 2, 12, 0);
        leftBoot.model.box(0, 0, -2, 4, 4, 2, LANCER_SILVER).inflate(0.9);
        leftBoot.model.box(0, 0, -3.4, 4, 1.5, -2.9, LANCER_DARK);

        return List.of(head, body, ponytail, rightArm, leftArm, rightLeg, leftLeg, rightBoot, leftBoot);
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
        // Gana la última que encaje: el viento primero, y cargar la revela
        String exOverrides = "[\n"
                + "    { \"predicate\": { \"fate_ubw:air\": 1.0 }, \"model\": \"fate_ubw:item/excalibur_air\" },\n"
                + "    { \"predicate\": { \"fate_ubw:charge\": 0.01 }, \"model\": \"fate_ubw:item/excalibur_charging\" },\n"
                + "    { \"predicate\": { \"fate_ubw:charge\": 1.0 }, \"model\": \"fate_ubw:item/excalibur_charged\" }\n"
                + "  ]";
        itemModel(root, "excalibur", excalibur(0), handheld(0.7), exOverrides,
                16, "{\"animation\":{\"frames\":[{\"index\":0,\"time\":60}," + range(1, 16) + "]}}");
        itemModel(root, "excalibur_air", excaliburAir(), handheld(0.7), null, 8, "{\"animation\":{\"frametime\":2}}");
        itemModel(root, "excalibur_charging", excalibur(1), raised(0.7, -35, 5.0, 0.0, 0.75), null,
                12, "{\"animation\":{\"frametime\":1}}");
        itemModel(root, "excalibur_charged", excalibur(2), raised(0.7, -50, 6.5, -0.5, 0.8), null,
                8, "{\"animation\":{\"frametime\":2,\"interpolate\":true}}");
        List<Bone> saber = saberArmor();
        geoModel(root, "saber_armor", saber);
        itemModel(root, "saber_chestplate", bonesToModel(saber, 8, -4, 8, "armorBody", "skirtFront", "skirtBack", "skirtLeft",
                "skirtRight", "armorRightArm", "armorLeftArm"), armorIcon(0.5), null);
        itemModel(root, "saber_leggings", bonesToModel(saber, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "saber_boots", bonesToModel(saber, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);

        itemModel(root, "kanshou", falchion(KAN_BLADE, KAN_EDGE, KAN_GRIP, KAN_METAL), handheld(0.9), null);
        itemModel(root, "bakuya", falchion(BAK_BLADE, BAK_EDGE, BAK_GRIP, BAK_METAL), handheld(0.9), null);
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
        geoModel(root, "archer_armor", armor);
        // Iconos 3D de inventario con los mismos cubos que la armadura puesta
        itemModel(root, "archer_chestplate", bonesToModel(armor, 8, -4, 8, "armorBody", "coatBack", "coatLeft", "coatRight",
                "coatFrontL", "coatFrontR", "armorRightArm", "armorLeftArm"), armorIcon(0.5), null);
        itemModel(root, "archer_leggings", bonesToModel(armor, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "archer_boots", bonesToModel(armor, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);

        // ---------- Lancer ----------
        itemModel(root, "gae_bolg", gaeBolg(), handheld(0.5, 0.5), null);
        List<Bone> lancer = lancerArmor();
        geoModel(root, "lancer_armor", lancer);
        itemModel(root, "lancer_chestplate", bonesToModel(lancer, 8, -4, 8, "armorBody", "ponytail", "armorRightArm", "armorLeftArm"),
                armorIcon(0.5), null);
        itemModel(root, "lancer_leggings", bonesToModel(lancer, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "lancer_boots", bonesToModel(lancer, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);

        // ---------- Rider ----------
        itemModel(root, "rider_dagger", chainDagger(), handheld(0.7), null);
        itemModel(root, "bellerophon", bridle(), HELD_OBJECT, null);
        List<Bone> rider = riderArmor();
        geoModel(root, "rider_armor", rider);
        itemModel(root, "rider_helmet", bonesToModel(rider, 8, -20, 8, "armorHead"), armorIcon(0.7), null);
        itemModel(root, "rider_chestplate", bonesToModel(rider, 8, -4, 8, "armorBody", "hair", "armorRightArm", "armorLeftArm"),
                armorIcon(0.5), null);
        itemModel(root, "rider_leggings", bonesToModel(rider, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "rider_boots", bonesToModel(rider, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);
        geoModel(root, "pegasus", pegasus());

        // ---------- Gilgamesh ----------
        // Ea la dibuja GeckoLib (cilindros que giran y líneas que brillan); el JSON solo da las posiciones en mano
        geoModel(root, "ea", eaBones(), "item/");
        Files.writeString(root.resolve("models/item/ea.json"),
                "{\n  \"parent\": \"builtin/entity\",\n  \"textures\": { \"particle\": \"fate_ubw:item/ea\" },\n  \"display\": " + handheld(0.7) + "\n}\n");
        itemModel(root, "gate_of_babylon", babylonKey(), handheld(0.9), null);
        List<Bone> gilgamesh = gilgameshArmor();
        geoModel(root, "gilgamesh_armor", gilgamesh);
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
        itemModel(root, "hrunting", hrunting(-0.5), handheld(0.8), null);
        itemModel(root, "rin_jewel", rinJewel(), HELD_OBJECT, null);
        itemModel(root, "shirou_poster", shirouPoster(), handheld(0.8), null);
        itemModel(root, "zelzeriz", zelzeriz(), HELD_OBJECT, null);
        skin(root, "berserker", 0x4a4b52, 0x141418, 0xd01020);
        skin(root, "lancer", 0xe8c4a8, 0x1f3f9a, 0xc01020);
        skin(root, "assassin", 0xecd0b4, 0x4b3a8f, 0x3a5fd0);

        // ---------- Caster ----------
        itemModel(root, "rule_breaker", ruleBreaker(), handheld(1.0), null);
        List<Bone> caster = casterArmor();
        geoModel(root, "caster_armor", caster);
        itemModel(root, "caster_chestplate", bonesToModel(caster, 8, -4, 8, "armorBody", "capeBack", "capeLeft", "capeRight",
                "armorRightArm", "armorLeftArm"), armorIcon(0.5), null);
        itemModel(root, "caster_leggings", bonesToModel(caster, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "caster_boots", bonesToModel(caster, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);

        // ---------- Assassin ----------
        itemModel(root, "monohoshizao", monohoshizao(), handheld(0.55, 0.6), null);
        List<Bone> assassin = assassinArmor();
        geoModel(root, "assassin_armor", assassin);
        itemModel(root, "assassin_chestplate", bonesToModel(assassin, 8, -4, 8, "armorBody", "hair", "haoriBack", "haoriFrontL",
                "haoriFrontR", "armorRightArm", "armorLeftArm"), armorIcon(0.5), null);
        itemModel(root, "assassin_leggings", bonesToModel(assassin, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "assassin_boots", bonesToModel(assassin, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);

        // ---------- Berserker ----------
        itemModel(root, "berserker_axe_sword", axeSword(), handheld(0.7), null);
        List<Bone> berserker = berserkerArmor();
        geoModel(root, "berserker_armor", berserker);
        itemModel(root, "berserker_chestplate", bonesToModel(berserker, 8, -4, 8, "armorBody", "loinFront", "loinBack",
                "armorRightArm", "armorLeftArm"), armorIcon(0.5), null);
        itemModel(root, "berserker_leggings", bonesToModel(berserker, 8, 2, 8, "armorRightLeg", "armorLeftLeg"), armorIcon(0.6), null);
        itemModel(root, "berserker_boots", bonesToModel(berserker, 8, 6, 8, "armorRightBoot", "armorLeftBoot"), armorIcon(0.7), null);
        // Espadas clavadas: cada modelo del mod, boca abajo y enterrado; el blockstate elige uno y un giro al azar
        Map<String, Model> graves = new LinkedHashMap<>();
        graves.put("kanshou", buried(falchion(KAN_BLADE, KAN_EDGE, KAN_GRIP, KAN_METAL), 20, 5));
        graves.put("bakuya", buried(falchion(BAK_BLADE, BAK_EDGE, BAK_GRIP, BAK_METAL), 20, 5));
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
        variants.append("\n    ]\n  }\n}\n");
        Files.createDirectories(root.resolve("blockstates"));
        Files.writeString(root.resolve("blockstates/ubw_sword.json"), variants);
    }
}
