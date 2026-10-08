package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.archer.UbwCoreEntity;
import com.nuwuman.fateubw.archer.UnlimitedBladeWorks;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * El cielo de Unlimited Blade Works: una cúpula opaca justo por dentro de la pared de barrera que tapa el mundo
 * de fuera (atardecer rojizo, horizonte encendido y espadas en la lejanía) y los engranajes que giran dentro.
 * Visto desde el centro parece infinito: los engranajes están más cerca pero a escala, así que se ven igual de grandes.
 */
public class UbwCoreRenderer extends EntityRenderer<UbwCoreEntity> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/beacon_beam.png");
    private static final float DOME = UnlimitedBladeWorks.RADIUS - 1.0F;
    // Latitudes de los anillos de la cúpula, más juntas en el horizonte para que el resplandor sea nítido
    private static final float[] LATITUDES = {-90, -60, -30, -15, -8, -4, -2, 0, 2, 4, 8, 15, 25, 35, 50, 65, 80, 90};
    private static final int SEGMENTS = 64;
    // Color según la altura (seno de la latitud): suelo lejano, horizonte encendido, cielo rojo que se oscurece
    private static final float[][] SKY = {
            {-1.0F, 110, 45, 28}, {-0.03F, 165, 75, 40}, {0.0F, 250, 175, 95},
            {0.1F, 230, 115, 55}, {0.4F, 150, 45, 30}, {1.0F, 45, 10, 12}};
    // x, y, z (respecto al centro), radio, velocidad de giro, inclinación
    private static final float[][] GEARS = sky(22);
    // ángulo, altura, inclinación de cada espada del horizonte
    private static final float[][] SWORDS = horizon(260);

    // Cúpula de engranajes con semilla fija: los grandes lejos y altos, los pequeños más cerca;
    // los más grandes giran más despacio y la mitad gira al revés. Luego se acercan dentro de la cúpula
    // conservando su tamaño aparente desde el centro.
    private static float[][] sky(int count) {
        java.util.Random random = new java.util.Random(1234);
        float[][] gears = new float[count][];
        for (int i = 0; i < count; i++) {
            boolean big = i < count * 2 / 3;
            double angle = (i + random.nextDouble() * 0.6) * Math.PI * 2 / count * (big ? 1.5 : 1.0);
            double dist = big ? 70 + random.nextDouble() * 50 : 35 + random.nextDouble() * 25;
            float radius = big ? 16 + random.nextFloat() * 22 : 5 + random.nextFloat() * 8;
            float y = big ? 45 + random.nextFloat() * 60 : 25 + random.nextFloat() * 25;
            float speed = (0.6F / (radius / 10.0F)) * (random.nextBoolean() ? 1 : -1);
            float tilt = -40 + random.nextFloat() * 80;
            double x = Math.cos(angle) * dist, z = Math.sin(angle) * dist;
            double far = Math.sqrt(x * x + y * y + z * z);
            float scale = (float) (DOME * (0.55 + 0.38 * MathHelper.clamp((far - 40) / 125, 0, 1)) / far);
            gears[i] = new float[]{(float) x * scale, y * scale, (float) z * scale, radius * scale, speed, tilt};
        }
        return gears;
    }

    private static float[][] horizon(int count) {
        java.util.Random random = new java.util.Random(5678);
        float[][] swords = new float[count][];
        for (int i = 0; i < count; i++) {
            swords[i] = new float[]{(float) (random.nextDouble() * Math.PI * 2), 0.25F + random.nextFloat() * 0.7F, -0.35F + random.nextFloat() * 0.7F};
        }
        return swords;
    }

    public UbwCoreRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(UbwCoreEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(UbwCoreEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float age = entity.age + tickDelta;
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getDebugQuads());
        // La cúpula crece con la onda que transforma el terreno
        float r = DOME * Math.min(1.0F, age / UnlimitedBladeWorks.SPREAD_TICKS);
        Matrix4f m = matrices.peek().getPositionMatrix();
        dome(vc, m, r);
        for (float[] s : SWORDS) sword(vc, m, r * 0.97F, s);

        float appear = MathHelper.clamp((age - UnlimitedBladeWorks.SPREAD_TICKS) / 30.0F, 0.0F, 1.0F);
        if (appear <= 0.0F) return;
        for (float[] g : GEARS) {
            matrices.push();
            matrices.translate(g[0], g[1], g[2]);
            // De cara al centro, algo inclinado
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.toDegrees(Math.atan2(-g[0], -g[2]))));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g[5]));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(age * g[4]));
            gear(vc, matrices.peek().getPositionMatrix(), g[3], (int) (220 * appear));
            matrices.pop();
        }
    }

    private static void dome(VertexConsumer vc, Matrix4f m, float r) {
        for (int j = 0; j < LATITUDES.length - 1; j++) {
            float lat0 = LATITUDES[j] * MathHelper.RADIANS_PER_DEGREE, lat1 = LATITUDES[j + 1] * MathHelper.RADIANS_PER_DEGREE;
            float y0 = MathHelper.sin(lat0), y1 = MathHelper.sin(lat1), c0 = MathHelper.cos(lat0), c1 = MathHelper.cos(lat1);
            int col0 = skyColor(y0), col1 = skyColor(y1);
            for (int i = 0; i < SEGMENTS; i++) {
                float a0 = (float) (i * Math.PI * 2 / SEGMENTS), a1 = (float) ((i + 1) * Math.PI * 2 / SEGMENTS);
                float x0 = MathHelper.cos(a0), z0 = MathHelper.sin(a0), x1 = MathHelper.cos(a1), z1 = MathHelper.sin(a1);
                vertex(vc, m, x0 * c0 * r, y0 * r, z0 * c0 * r, col0);
                vertex(vc, m, x1 * c0 * r, y0 * r, z1 * c0 * r, col0);
                vertex(vc, m, x1 * c1 * r, y1 * r, z1 * c1 * r, col1);
                vertex(vc, m, x0 * c1 * r, y1 * r, z0 * c1 * r, col1);
            }
        }
    }

    private static int skyColor(float height) {
        for (int k = 1; k < SKY.length; k++) {
            if (height <= SKY[k][0]) {
                float t = (height - SKY[k - 1][0]) / (SKY[k][0] - SKY[k - 1][0]);
                return rgb(MathHelper.lerp(t, SKY[k - 1][1], SKY[k][1]), MathHelper.lerp(t, SKY[k - 1][2], SKY[k][2]),
                        MathHelper.lerp(t, SKY[k - 1][3], SKY[k][3]));
            }
        }
        return rgb(SKY[SKY.length - 1][1], SKY[SKY.length - 1][2], SKY[SKY.length - 1][3]);
    }

    private static int rgb(float r, float g, float b) {
        return ((int) r << 16) | ((int) g << 8) | (int) b;
    }

    // Silueta de una espada clavada en el horizonte: hoja afilada y algo inclinada
    private static void sword(VertexConsumer vc, Matrix4f m, float r, float[] s) {
        float scale = r / DOME;
        float cx = MathHelper.cos(s[0]), cz = MathHelper.sin(s[0]);
        float tx = -cz, tz = cx; // tangente a la cúpula
        float base = -0.25F * scale, height = s[1] * scale, w = 0.07F * scale, lean = s[2] * height;
        float bx = cx * r, bz = cz * r;
        float topX = bx + tx * lean, topZ = bz + tz * lean, top = base + height;
        int color = rgb(40, 20, 16);
        vertex(vc, m, bx - tx * w, base, bz - tz * w, color);
        vertex(vc, m, bx + tx * w, base, bz + tz * w, color);
        vertex(vc, m, topX + tx * w * 0.2F, top, topZ + tz * w * 0.2F, color);
        vertex(vc, m, topX - tx * w * 0.2F, top, topZ - tz * w * 0.2F, color);
        // Guarda
        float gy = base + height * 0.22F, gx = bx + tx * lean * 0.22F, gz = bz + tz * lean * 0.22F, gw = w * 3.0F, gh = w * 0.6F;
        vertex(vc, m, gx - tx * gw, gy - gh, gz - tz * gw, color);
        vertex(vc, m, gx + tx * gw, gy - gh, gz + tz * gw, color);
        vertex(vc, m, gx + tx * gw, gy + gh, gz + tz * gw, color);
        vertex(vc, m, gx - tx * gw, gy + gh, gz - tz * gw, color);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb) {
        vc.vertex(m, x, y, z).color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255);
    }

    // Engranaje en el plano XY: aro, dientes, cubo y radios
    private static void gear(VertexConsumer vc, Matrix4f m, float radius, int alpha) {
        int teeth = 16;
        float inner = radius * 0.78F, hubIn = radius * 0.12F, hubOut = radius * 0.25F;
        for (int i = 0; i < teeth * 2; i++) {
            float a0 = (float) (i * Math.PI / teeth), a1 = (float) ((i + 1) * Math.PI / teeth);
            float c0 = MathHelper.cos(a0), s0 = MathHelper.sin(a0), c1 = MathHelper.cos(a1), s1 = MathHelper.sin(a1);
            float outer = i % 2 == 0 ? radius * 1.12F : radius;
            quad(vc, m, c0 * inner, s0 * inner, c1 * inner, s1 * inner, c1 * outer, s1 * outer, c0 * outer, s0 * outer, alpha);
            quad(vc, m, c0 * hubIn, s0 * hubIn, c1 * hubIn, s1 * hubIn, c1 * hubOut, s1 * hubOut, c0 * hubOut, s0 * hubOut, alpha);
        }
        for (int k = 0; k < 6; k++) {
            float a = (float) (k * Math.PI / 3);
            float c = MathHelper.cos(a), s = MathHelper.sin(a), w = radius * 0.05F;
            quad(vc, m, c * hubOut - s * w, s * hubOut + c * w, c * hubOut + s * w, s * hubOut - c * w,
                    c * inner + s * w, s * inner - c * w, c * inner - s * w, s * inner + c * w, alpha);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by,
                             float cx, float cy, float dx, float dy, int alpha) {
        int r = 58, g = 36, b = 24;
        vc.vertex(m, ax, ay, 0).color(r, g, b, alpha);
        vc.vertex(m, bx, by, 0).color(r, g, b, alpha);
        vc.vertex(m, cx, cy, 0).color(r, g, b, alpha);
        vc.vertex(m, dx, dy, 0).color(r, g, b, alpha);
        vc.vertex(m, dx, dy, 0).color(r, g, b, alpha);
        vc.vertex(m, cx, cy, 0).color(r, g, b, alpha);
        vc.vertex(m, bx, by, 0).color(r, g, b, alpha);
        vc.vertex(m, ax, ay, 0).color(r, g, b, alpha);
    }

    @Override
    public Identifier getTexture(UbwCoreEntity entity) {
        return TEXTURE;
    }
}
