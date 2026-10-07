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

    // Asigna a cada cara su región en la textura (empaquetado por estantes) y la pinta
    static BufferedImage atlas(List<Cube> cubes) {
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
                BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                for (Face f : faces) {
                    for (int y = 0; y < f.h; y++) {
                        for (int x = 0; x < f.w; x++) {
                            img.setRGB(f.u + x, f.v + y, 0xff000000 | f.paint.at(x, y, f.w, f.h, f.seed));
                        }
                    }
                }
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

    static void itemModel(Path root, String name, Model model, String display, String overrides) throws IOException {
        BufferedImage img = atlas(model.cubes);
        double k = 16.0 / img.getWidth();
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"gui_light\": \"front\",\n");
        b.append("  \"textures\": { \"0\": \"fate_ubw:item/").append(name).append("\", \"particle\": \"fate_ubw:item/").append(name).append("\" },\n");
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
        b.append("  ],\n  \"display\": ").append(display);
        if (overrides != null) b.append(",\n  \"overrides\": ").append(overrides);
        b.append("\n}\n");
        Files.writeString(root.resolve("models/item/" + name + ".json"), b);
        ImageIO.write(img, "png", root.resolve("textures/item/" + name + ".png").toFile());
    }

    static void geoModel(Path root, String name, List<Bone> bones) throws IOException {
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
        Path geo = root.resolve("geo/item/armor/" + name + ".geo.json");
        Files.createDirectories(geo.getParent());
        Files.writeString(geo, b);
        Path tex = root.resolve("textures/item/armor/" + name + ".png");
        Files.createDirectories(tex.getParent());
        ImageIO.write(img, "png", tex.toFile());
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
    }
}
