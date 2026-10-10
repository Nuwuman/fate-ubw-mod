package com.nuwuman.fateubw.saber;

import com.nuwuman.fateubw.archer.UnlimitedBladeWorks;
import com.nuwuman.fateubw.FateUBW;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * El haz de luz de Excalibur: crece hacia delante, daña lo que toca y se desvanece.
 * Es también la base de otros Noble Phantasm en línea recta (Enuma Elish), que cambian
 * longitud, radio, daño y efectos sobrescribiendo los métodos protegidos.
 */
public class ExcaliburBeamEntity extends Entity {
    /** Lo que tarda la punta del haz en llegar al final: avanza, no aparece entero (y así hay tiempo para un choque). */
    public static final int GROW_TICKS = 30;
    /** Lo que tarda el haz en alcanzar su grosor. */
    public static final int WIDEN_TICKS = 8;
    public static final int DAMAGE_TICKS = 46;
    public static final int LIFETIME = 58;

    // Los primeros bloques del haz no se rompen, para no cavar bajo los pies del que dispara
    private static final float CARVE_START = 2.0F;
    private static final int CARVE_BUDGET = 250;
    // Obsidiana y más duros resisten el haz
    private static final float MAX_BLAST_RESISTANCE = 1200.0F;

    @Nullable
    protected Entity owner;
    protected final Set<Integer> hit = new HashSet<>();
    private float carved = CARVE_START;
    // Choque de Noble Phantasms: el largo al que se frena (sincronizado con el cliente; -1 = sin choque)
    private static final TrackedData<Float> CLASH = DataTracker.registerData(ExcaliburBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final int CLASH_TICKS = 14;
    private static final int ERUPTION_TICKS = 10;
    @Nullable
    private ExcaliburBeamEntity rival;
    private Vec3d clashPoint = Vec3d.ZERO;
    private int clashTicks;
    private boolean clashed;
    private int bonusDamageTicks;

    public ExcaliburBeamEntity(EntityType<? extends ExcaliburBeamEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public static void fire(ServerWorld world, PlayerEntity owner) {
        new ExcaliburBeamEntity(FateUBW.BEAM, world).launch(world, owner);
    }

    protected void launch(ServerWorld world, PlayerEntity owner) {
        Vec3d dir = owner.getRotationVec(1.0F);
        Vec3d start = owner.getEyePos().add(0.0, -0.3, 0.0).add(dir.multiply(1.2));
        refreshPositionAndAngles(start.x, start.y, start.z, owner.getYaw(), owner.getPitch());
        this.owner = owner;
        world.spawnEntity(this);
    }

    public float maxLength() {
        return 48.0F;
    }

    public float radius() {
        return 2.5F;
    }

    protected float damage() {
        return 40.0F;
    }

    protected RegistryKey<DamageType> damageType() {
        return FateUBW.EXCALIBUR_DAMAGE;
    }

    /** Efecto extra sobre cada objetivo alcanzado (además del daño). */
    protected void onHit(ServerWorld world, LivingEntity target, Vec3d dir) {
        target.setOnFireFor(5);
        target.takeKnockback(1.5, -dir.x, -dir.z);
    }

    /** Efecto sobre lo que está cerca del haz pero aún no ha sido alcanzado. */
    protected void affectNearby(ServerWorld world, Vec3d start, Vec3d end) {
    }

    public float length(float age) {
        float length = maxLength() * Math.min(1.0F, age / GROW_TICKS);
        float clash = dataTracker.get(CLASH);
        return clash >= 0.0F ? Math.min(length, clash) : length;
    }

    public float fade(float age) {
        return age < LIFETIME - 10 ? 1.0F : Math.max(0.0F, (LIFETIME - age) / 10.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld world) {
            if (!clashed && age <= DAMAGE_TICKS) findRival(world);
            if (rival != null) clash(world);
            if (age <= DAMAGE_TICKS + bonusDamageTicks) sweep(world);
            else if (carving(world)) carve(world, getPos(), getRotationVector(), maxLength());
            if (age > LIFETIME + bonusDamageTicks && !carving(world)) discard();
        }
    }

    /** Estallido a lo largo del haz: luz dorada en Excalibur; los demás lo cambian (Enuma Elish, explosiones). */
    protected void burst(ServerWorld world, Vec3d at, double spread) {
        world.spawnParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, spread, spread, spread, 0.25);
    }

    // Busca el haz de otro jugador que se cruce con este
    private void findRival(ServerWorld world) {
        Vec3d start = getPos(), end = start.add(getRotationVector().multiply(length(age)));
        for (ExcaliburBeamEntity other : world.getEntitiesByClass(ExcaliburBeamEntity.class, new Box(start, end).expand(maxLength()),
                e -> e != this && !e.clashed && e.rival == null && (owner == null || e.owner != owner))) {
            Vec3d oStart = other.getPos(), oEnd = oStart.add(other.getRotationVector().multiply(other.length(other.age)));
            Vec3d point = meeting(start, end, oStart, oEnd, radius() + other.radius());
            if (point == null) continue;
            for (ExcaliburBeamEntity beam : new ExcaliburBeamEntity[]{this, other}) {
                beam.clashed = true;
                beam.clashPoint = point;
                beam.clashTicks = 0;
                beam.dataTracker.set(CLASH, (float) beam.getPos().distanceTo(point));
            }
            rival = other;
            other.rival = this;
            world.playSound(null, point.x, point.y, point.z, net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                    net.minecraft.sound.SoundCategory.PLAYERS, 3.0F, 0.6F);
            return;
        }
    }

    // Los dos haces empujan en el punto de choque; gana el dueño con más maná y el otro estalla
    private void clash(ServerWorld world) {
        ExcaliburBeamEntity other = rival;
        if (other == null || other.isRemoved()) {
            win();
            return;
        }
        Vec3d p = clashPoint;
        world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 12, 0.8, 0.8, 0.8, 0.35);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 10, 1.0, 1.0, 1.0, 0.4);
        if (clashTicks % 4 == 0) {
            world.spawnParticles(ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            world.playSound(null, p.x, p.y, p.z, net.minecraft.sound.SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                    net.minecraft.sound.SoundCategory.PLAYERS, 2.0F, 0.5F + clashTicks * 0.04F);
        }
        if (++clashTicks < CLASH_TICKS || getId() > other.getId()) return; // lo resuelve uno de los dos
        ExcaliburBeamEntity winner = power(this) >= power(other) ? this : other;
        ExcaliburBeamEntity loser = winner == this ? other : this;
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 2, 1.0, 1.0, 1.0, 0.0);
        world.playSound(null, p.x, p.y, p.z, net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                net.minecraft.sound.SoundCategory.PLAYERS, 4.0F, 0.8F);
        loser.discard();
        winner.win();
        if (winner.owner instanceof net.minecraft.server.network.ServerPlayerEntity player)
            com.nuwuman.fateubw.Achievements.grant(player, "clash", "done");
    }

    private void win() {
        rival = null;
        dataTracker.set(CLASH, -1.0F);
        bonusDamageTicks = Math.max(0, age + 8 - DAMAGE_TICKS); // le queda tiempo para atravesar lo que había detrás
    }

    // Maná que le queda al dueño (y, a igualdad, el daño del Noble Phantasm)
    private static float power(ExcaliburBeamEntity beam) {
        float mana = beam.owner instanceof PlayerEntity player ? com.nuwuman.fateubw.ability.Mana.get(player) : 0.0F;
        return mana * 1000.0F + beam.damage();
    }

    /**
     * Dónde chocan los haces ab y cd (de grosor conjunto {@code reach}), o null si no se tocan. Si van casi paralelos
     * (de frente), chocan entre las dos puntas; si se cruzan, en el punto donde pasan más cerca.
     */
    @Nullable
    private static Vec3d meeting(Vec3d a, Vec3d b, Vec3d c, Vec3d d, double reach) {
        Vec3d u = b.subtract(a), v = d.subtract(c), w = a.subtract(c);
        double uu = u.dotProduct(u), uv = u.dotProduct(v), vv = v.dotProduct(v), uw = u.dotProduct(w), vw = v.dotProduct(w);
        if (uu < 1.0E-6 || vv < 1.0E-6) return null;
        double den = uu * vv - uv * uv;
        if (den < 1.0E-3 * uu * vv) {
            double lateral = c.subtract(a).crossProduct(u).length() / Math.sqrt(uu);
            double sc = c.subtract(a).dotProduct(u) / uu, sd = d.subtract(a).dotProduct(u) / uu;
            boolean overlap = Math.max(sc, sd) >= 0.0 && Math.min(sc, sd) <= 1.0;
            return lateral <= reach && overlap ? b.lerp(d, 0.5) : null;
        }
        double s = MathHelper.clamp((uv * vw - vv * uw) / den, 0.0, 1.0);
        double t = MathHelper.clamp((uv * s + vw) / vv, 0.0, 1.0);
        s = MathHelper.clamp((uv * t - uw) / uu, 0.0, 1.0);
        Vec3d p = a.add(u.multiply(s)), q = c.add(v.multiply(t));
        return p.distanceTo(q) <= reach ? p.lerp(q, 0.5) : null;
    }

    private void sweep(ServerWorld world) {
        float len = length(age);
        if (len < 0.1F) return;
        Vec3d start = getPos();
        Vec3d dir = getRotationVector();
        Vec3d end = start.add(dir.multiply(len));

        // Frente del haz mientras avanza
        if (age <= GROW_TICKS) {
            world.spawnParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 15, 1.0, 1.0, 1.0, 0.15);
            burst(world, end, 1.0);
        } else if (age <= GROW_TICKS + ERUPTION_TICKS && rival == null) {
            // Ya entero, una cadena de explosiones corre de la base a la punta
            double at = len * (age - GROW_TICKS) / (double) ERUPTION_TICKS;
            Vec3d p = start.add(dir.multiply(at));
            burst(world, p, radius() * 0.5);
            if ((age - GROW_TICKS) % 3 == 0) {
                world.playSound(null, p.x, p.y, p.z, net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.2F, 0.8F + world.random.nextFloat() * 0.3F);
            }
        }
        if (FateUBW.breaksBlocks(world, start)) carve(world, start, dir, len);
        affectNearby(world, start, end);
        if (damageType() == FateUBW.EXCALIBUR_DAMAGE) afterglow(world, start, dir, len);

        float radius = radius();
        DamageSource source = world.getDamageSources().create(damageType(), this, owner);
        Box area = new Box(start, end).expand(radius + 1.0);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != owner && e.isAlive() && !hit.contains(e.getId()) && sameSide(world, start, e) && com.nuwuman.fateubw.Rules.canAffect(owner, e))) {
            Vec3d center = target.getBoundingBox().getCenter();
            if (distanceToSegment(center, start, end) > radius + target.getWidth() / 2) continue;
            hit.add(target.getId());
            target.damage(source, damage());
            onHit(world, target, dir);
            world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // Estela de luz a lo largo del haz y, con fateAbilitiesBreakBlocks, el suelo queda ardiendo por donde pasó
    private void afterglow(ServerWorld world, Vec3d start, Vec3d dir, float len) {
        for (int i = 0; i < 10; i++) {
            Vec3d p = start.add(dir.multiply(random.nextFloat() * len));
            world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, radius() * 0.6, radius() * 0.6, radius() * 0.6, 0.03);
        }
        if (age != GROW_TICKS || !FateUBW.breaksBlocks(world, start)) return;
        for (float d = 2.0F; d <= len; d += 2.0F) {
            BlockPos pos = BlockPos.ofFloored(start.add(dir.multiply(d)));
            for (int down = 0; down < 5; down++, pos = pos.down()) {
                if (world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), net.minecraft.util.math.Direction.UP)) {
                    if (random.nextFloat() < 0.6F) world.setBlockState(pos, net.minecraft.block.AbstractFireBlock.getState(world, pos));
                    break;
                }
            }
        }
    }

    // Rompe (sin soltar objetos) una esfera del radio del haz en cada paso del tramo que avanzó este tick
    // Como mucho CARVE_BUDGET bloques por tick: si hay más, sigue en los siguientes donde lo dejó
    private void carve(ServerWorld world, Vec3d start, Vec3d dir, float len) {
        float radius = radius() * 0.8F; // el túnel, algo más estrecho que la luz del haz
        int r = MathHelper.ceil(radius), broken = 0;
        for (float d = carved; d <= len; d += 1.0F) {
            if (broken >= CARVE_BUDGET) {
                carved = d;
                return;
            }
            Vec3d point = start.add(dir.multiply(d));
            BlockPos center = BlockPos.ofFloored(point);
            for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
                if (Vec3d.ofCenter(pos).squaredDistanceTo(point) > radius * radius) continue;
                BlockState state = world.getBlockState(pos);
                if (state.isAir() || state.getHardness(world, pos) < 0
                        || state.getBlock().getBlastResistance() >= MAX_BLAST_RESISTANCE) continue;
                breakCarved(world, pos.toImmutable(), state);
                broken++;
            }
        }
        carved = Math.max(carved, len);
    }

    /** ¿Le queda túnel por abrir? Mientras tanto el haz sigue vivo en el servidor. */
    private boolean carving(ServerWorld world) {
        return carved < maxLength() && FateUBW.breaksBlocks(world, getPos());
    }

    /**
     * Quita un bloque del túnel sin soltar objetos. Sin partículas, sonido ni avisar a los vecinos: con miles de bloques
     * por disparo, cada una de esas cosas por bloque es lo que congelaba el juego (los efectos del haz ya lo tapan).
     */
    protected void breakCarved(ServerWorld world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    /** El haz atraviesa paredes, pero no la frontera de un Reality Marble: dentro y fuera son mundos distintos. */
    protected static boolean sameSide(ServerWorld world, Vec3d start, Entity e) {
        return UnlimitedBladeWorks.protects(world, BlockPos.ofFloored(start)) == UnlimitedBladeWorks.protects(world, e.getBlockPos());
    }

    /** Punto del segmento a-b más cercano a p. */
    protected static Vec3d closestOnSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double t = MathHelper.clamp(p.subtract(a).dotProduct(ab) / ab.lengthSquared(), 0.0, 1.0);
        return a.add(ab.multiply(t));
    }

    private static double distanceToSegment(Vec3d p, Vec3d a, Vec3d b) {
        return p.distanceTo(closestOnSegment(p, a, b));
    }

    @Override
    public boolean shouldRender(double distance) {
        return true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(CLASH, -1.0F);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
