import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Genera las texturas animadas de Excalibur: tiras verticales de frames 32x32 + su .mcmeta.
 * Uso: java tools/TextureGen.java src/main/resources/assets/fate_ubw/textures/item
 */
public class TextureGen {
    static final int S = 32;
    // Eje de la espada: pomo abajo a la izquierda, punta arriba a la derecha
    static final double PX = 3.5, PY = 28.5, TX = 29.5, TY = 2.5;
    static final double LEN = Math.hypot(TX - PX, TY - PY);
    static final double DX = (TX - PX) / LEN, DY = (TY - PY) / LEN;
    static final double NX = -DY, NY = DX;
    static final double BLADE_START = 12.0, BLADE_W = 2.3;

    static final int GOLD = 0xd4a017, GOLD_L = 0xf7d774;
    static final int BLUE = 0x1d3f8f, BLUE_L = 0x3567c4, GEM = 0x7fd3ff;
    static final int EDGE_L = 0xffffff, STEEL_L = 0xe3ebf7, FULLER = 0xb4c4e4, STEEL_D = 0x9aa8c4, EDGE_D = 0x76839f;

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : ".");
        Files.createDirectories(out);
        // Normal: un destello recorre la hoja cada ~3 s
        write(out, "excalibur", 0, 16, "{\"animation\":{\"frames\":[{\"index\":0,\"time\":60}," + range(1, 16) + "]}}");
        // Cargando: hoja dorada con destello continuo
        write(out, "excalibur_charging", 1, 12, "{\"animation\":{\"frametime\":1}}");
        // Cargada: hoja de luz que pulsa con aura dorada
        write(out, "excalibur_charged", 2, 8, "{\"animation\":{\"frametime\":2,\"interpolate\":true}}");
    }

    static String range(int from, int to) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) b.append(',');
            b.append(i);
        }
        return b.toString();
    }

    static void write(Path out, String name, int mode, int frames, String mcmeta) throws IOException {
        BufferedImage img = new BufferedImage(S, S * frames, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < frames; f++) {
            img.setRGB(0, f * S, S, S, frame(mode, f, frames), 0, S);
        }
        ImageIO.write(img, "png", out.resolve(name + ".png").toFile());
        Files.writeString(out.resolve(name + ".png.mcmeta"), mcmeta + "\n");
    }

    static double bladeHalfWidth(double s) {
        double tipStart = LEN - 6.5;
        return s < tipStart ? BLADE_W : BLADE_W * Math.max(0, (LEN - 0.3 - s) / (LEN - 0.3 - tipStart));
    }

    static int[] frame(int mode, int f, int frames) {
        int[] px = new int[S * S];
        double phase = mode == 0 ? (f == 0 ? -1 : (f - 1) / (double) (frames - 2)) : f / (double) frames;
        double shine = phase < 0 ? -100 : BLADE_START + (LEN - BLADE_START) * phase;
        double pulse = 0.5 + 0.5 * Math.sin(2 * Math.PI * f / frames);

        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                double vx = x + 0.5 - PX, vy = y + 0.5 - PY;
                double s = vx * DX + vy * DY, r = vx * NX + vy * NY;
                int c = -1;
                if (s < 3.4) { // pomo con gema
                    double d = Math.hypot(s - 1.4, r);
                    if (d <= 2.0) c = d <= 0.9 ? GEM : (r < 0 ? GOLD_L : GOLD);
                } else if (s < 9.5) { // empuñadura azul con anillos dorados
                    if (Math.abs(r) <= 1.15) {
                        boolean band = Math.abs(s - 5.4) < 0.55 || Math.abs(s - 7.6) < 0.55;
                        c = band ? GOLD : (r < 0 ? BLUE_L : BLUE);
                    }
                } else if (s < BLADE_START) { // guarda
                    if (Math.abs(r) <= 5.4) {
                        boolean rim = Math.abs(r) > 4.3 || s < 10.1 || s > 11.4;
                        c = rim ? (r < 0 ? GOLD_L : GOLD) : Math.abs(r) < 1.0 ? GEM : (r < 0 ? BLUE_L : BLUE);
                    }
                } else { // hoja
                    double w = bladeHalfWidth(s);
                    if (w > 0 && Math.abs(r) <= w + 0.15) {
                        double t = r / Math.max(w, 0.6);
                        c = t < -0.75 ? EDGE_L : t < -0.25 ? STEEL_L : t <= 0.25 ? FULLER : t <= 0.75 ? STEEL_D : EDGE_D;
                        if (s < 18.0 && Math.abs(t) <= 0.3) c = ((int) (s * 1.5) % 2 == 0) ? GOLD_L : GOLD; // grabados
                        if (mode == 1) c = lerp(c, 0xffd86b, 0.35);
                        if (mode == 2) c = lerp(lerp(c, 0xffd95a, 0.6), 0xffffff, 0.35 * pulse);
                        if (Math.abs(s - shine) < 1.3) c = lerp(c, 0xffffff, 0.65);
                    }
                }
                if (c >= 0) px[y * S + x] = 0xff000000 | c;
            }
        }
        outline(px);
        if (mode == 2) aura(px, pulse);
        return px;
    }

    // Contorno oscuro de 1 px, como las espadas vanilla
    static void outline(int[] px) {
        int[] src = px.clone();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                if (src[y * S + x] != 0) continue;
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx < 0 || ny < 0 || nx >= S || ny >= S || src[ny * S + nx] == 0) continue;
                    px[y * S + x] = 0xff000000 | lerp(src[ny * S + nx] & 0xffffff, 0x10131c, 0.75);
                    break;
                }
            }
        }
    }

    // Halo dorado semitransparente alrededor de la hoja
    static void aura(int[] px, double pulse) {
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                if (px[y * S + x] != 0) continue;
                double vx = x + 0.5 - PX, vy = y + 0.5 - PY;
                double s = vx * DX + vy * DY, r = vx * NX + vy * NY;
                if (s < BLADE_START - 0.5 || s > LEN) continue;
                double d = Math.abs(r) - Math.max(bladeHalfWidth(Math.min(s, LEN - 1)), 0.8);
                if (d > 2.6) continue;
                int a = (int) ((90 + 70 * pulse) * (1 - Math.max(0, d - 0.6) / 2.0));
                if (a > 0) px[y * S + x] = (Math.min(a, 255) << 24) | 0xffcc33;
            }
        }
    }

    static int lerp(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xff) + (((b >> 16) & 0xff) - ((a >> 16) & 0xff)) * t);
        int g = (int) Math.round(((a >> 8) & 0xff) + (((b >> 8) & 0xff) - ((a >> 8) & 0xff)) * t);
        int bl = (int) Math.round((a & 0xff) + ((b & 0xff) - (a & 0xff)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
