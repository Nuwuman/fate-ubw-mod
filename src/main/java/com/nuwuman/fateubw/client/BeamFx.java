package com.nuwuman.fateubw.client;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

/**
 * Piezas de luz para los haces de los Noble Phantasm, en coordenadas del haz (+Y a lo largo de él). Todo va en la capa
 * del rayo (aditiva): cada cara se dibuja con las dos orientaciones porque esa capa descarta las de espaldas.
 */
final class BeamFx {
    private BeamFx() {
    }

    /** Estallido de rayos de luz en el plano perpendicular al haz, a la altura y: anchos en el centro y finos en la punta. */
    static void rays(VertexConsumer vc, Matrix4f m, float y, float inner, float outer, int count, float spin,
                     int r, int g, int b, int a) {
        if (a <= 0) return;
        for (int i = 0; i < count; i++) {
            float angle = spin + i * MathHelper.TAU / count;
            float len = outer * (i % 2 == 0 ? 1.0F : 0.6F);
            float half = MathHelper.TAU / count * 0.22F;
            float c0 = MathHelper.cos(angle - half), s0 = MathHelper.sin(angle - half);
            float c1 = MathHelper.cos(angle + half), s1 = MathHelper.sin(angle + half);
            float ct = MathHelper.cos(angle), st = MathHelper.sin(angle);
            quad(vc, m, c0 * inner, y, s0 * inner, c1 * inner, y, s1 * inner, ct * len, y, st * len, ct * len, y, st * len,
                    r, g, b, a, 0);
        }
    }

    /** Estelas finas de luz paralelas al haz que corren hacia la punta. */
    static void streaks(VertexConsumer vc, Matrix4f m, float len, float w, float age, int count, long seed,
                        int r, int g, int b, int a) {
        if (a <= 0 || len < 2.0F) return;
        Random random = Random.create(seed);
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * MathHelper.TAU;
            float radius = w * (0.5F + random.nextFloat() * 0.9F);
            float size = 3.0F + random.nextFloat() * 5.0F;
            float speed = 2.5F + random.nextFloat() * 2.5F;
            float y0 = (random.nextFloat() * len + age * speed) % len, y1 = Math.min(len, y0 + size);
            float x = MathHelper.cos(angle) * radius, z = MathHelper.sin(angle) * radius, t = 0.06F + w * 0.02F;
            // Dos tiras cruzadas para que se vean desde cualquier lado
            quad(vc, m, x - t, y0, z, x + t, y0, z, x + t, y1, z, x - t, y1, z, r, g, b, a, a);
            quad(vc, m, x, y0, z - t, x, y0, z + t, x, y1, z + t, x, y1, z - t, r, g, b, a, a);
        }
    }

    /** Un rayo quebrado que recorre el haz por fuera; con otra semilla cambia de forma. */
    static void bolt(VertexConsumer vc, Matrix4f m, float len, float radius, long seed, int r, int g, int b, int a) {
        if (a <= 0 || len < 2.0F) return;
        Random random = Random.create(seed);
        float step = 2.5F, base = random.nextFloat() * MathHelper.TAU, t = 0.12F;
        float px = MathHelper.cos(base) * radius, pz = MathHelper.sin(base) * radius, py = 0.0F;
        while (py < len) {
            float ny = Math.min(len, py + step * (0.6F + random.nextFloat() * 0.8F));
            float angle = base + (random.nextFloat() - 0.5F) * 1.6F, rr = radius * (0.7F + random.nextFloat() * 0.7F);
            float nx = MathHelper.cos(angle) * rr, nz = MathHelper.sin(angle) * rr;
            quad(vc, m, px - t, py, pz, px + t, py, pz, nx + t, ny, nz, nx - t, ny, nz, r, g, b, a, a);
            quad(vc, m, px, py, pz - t, px, py, pz + t, nx, ny, nz + t, nx, ny, nz - t, r, g, b, a, a);
            px = nx;
            pz = nz;
            py = ny;
        }
    }

    // Cuadrilátero con las dos caras; aStart para los dos primeros vértices y aEnd para los dos últimos
    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int r, int g, int b, int aStart, int aEnd) {
        vc.vertex(m, ax, ay, az).color(r, g, b, aStart);
        vc.vertex(m, bx, by, bz).color(r, g, b, aStart);
        vc.vertex(m, cx, cy, cz).color(r, g, b, aEnd);
        vc.vertex(m, dx, dy, dz).color(r, g, b, aEnd);
        vc.vertex(m, dx, dy, dz).color(r, g, b, aEnd);
        vc.vertex(m, cx, cy, cz).color(r, g, b, aEnd);
        vc.vertex(m, bx, by, bz).color(r, g, b, aStart);
        vc.vertex(m, ax, ay, az).color(r, g, b, aStart);
    }
}
