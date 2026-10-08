package com.nuwuman.fateubw.saber;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.Rules;
import com.nuwuman.fateubw.Voices;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Excalibur. La dibuja GeckoLib: el viento de Invisible Air la tapa y se deshace al cargar o tras Strike Air,
 * y la hoja se vuelve dorada y brilla en la oscuridad mientras carga.
 */
public class ExcaliburItem extends SwordItem implements GeoItem {
    // 3 s de carga para el Noble Phantasm
    public static final int FULL_CHARGE = 60;
    private static final int EXCALIBUR_COOLDOWN = 20 * 20;
    private static final int STRIKE_AIR_COOLDOWN = 20 * 2;
    private static final DustParticleEffect GOLD_DUST = new DustParticleEffect(new Vector3f(1.0F, 0.8F, 0.25F), 1.3F);

    public static final int VEILED = 0, REVEALED = 1, CHARGING = 2, CHARGED = 3;
    /** Estado de Invisible Air de esta Excalibur. Lo rellena el cliente (aquí no se puede tocar código de cliente). */
    public static ToIntFunction<ItemStack> airState = stack -> VEILED;
    private static final RawAnimation VEIL = RawAnimation.begin().thenPlay("animation.excalibur.veil").thenLoop("animation.excalibur.veiled");
    private static final RawAnimation UNVEIL = RawAnimation.begin().thenPlay("animation.excalibur.unveil").thenLoop("animation.excalibur.revealed");
    private static final RawAnimation UNVEIL_CHARGE = RawAnimation.begin().thenPlay("animation.excalibur.unveil").thenLoop("animation.excalibur.charge");
    private static final RawAnimation REVEALED_LOOP = RawAnimation.begin().thenLoop("animation.excalibur.revealed");
    private static final RawAnimation CHARGE_LOOP = RawAnimation.begin().thenLoop("animation.excalibur.charge");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ExcaliburItem(Item.Settings settings) {
        super(ToolMaterials.NETHERITE, settings.attributeModifiers(
                SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 6, -2.4F)));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private com.nuwuman.fateubw.client.ExcaliburRenderer renderer;

            @Override
            public net.minecraft.client.render.item.BuiltinModelItemRenderer getGeoItemRenderer() {
                if (renderer == null) renderer = new com.nuwuman.fateubw.client.ExcaliburRenderer();
                return renderer;
            }
        });
    }

    // El viento se deshace al revelarse y vuelve a formarse al taparla; entre revelada y cargando no hay viento
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation[] current = {null};
        controllers.add(new AnimationController<>(this, "air", 0, state -> {
            ItemStack stack = state.getData(DataTickets.ITEMSTACK);
            int air = stack == null ? VEILED : airState.applyAsInt(stack);
            boolean charging = air >= CHARGING;
            if (air == VEILED) current[0] = VEIL;
            else if (current[0] == null || current[0] == VEIL) current[0] = charging ? UNVEIL_CHARGE : UNVEIL;
            else if (charging != (current[0] == UNVEIL_CHARGE || current[0] == CHARGE_LOOP)) current[0] = charging ? CHARGE_LOOP : REVEALED_LOOP;
            return state.setAndContinue(current[0]);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // Cada Excalibur con su propia animación (si no, todas compartirían el estado del viento)
    @Override
    public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
        if (world instanceof ServerWorld server) GeoItem.getOrAssignId(stack, server);
    }

    // 0..1, lo usan el modelo (cambio de textura) y la pose de brazos en el cliente
    public static float chargeProgress(LivingEntity user) {
        return Math.min(1.0F, user.getItemUseTime() / (float) FULL_CHARGE);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        // Cada habilidad con su propio cooldown, como la Gáe Bolg
        if (user.isSneaking()) {
            if (!Rules.ready(user, FateUBW.STRIKE_AIR, STRIKE_AIR_COOLDOWN, "strike_air")) return TypedActionResult.fail(stack);
            if (world instanceof ServerWorld server) strikeAir(server, user);
            Rules.commit(user, FateUBW.STRIKE_AIR, STRIKE_AIR_COOLDOWN);
            return TypedActionResult.success(stack, world.isClient());
        }
        if (!Rules.ready(user, FateUBW.EXCALIBUR_NP, EXCALIBUR_COOLDOWN, "excalibur")) return TypedActionResult.fail(stack);
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(world instanceof ServerWorld server)) return;
        int charge = getMaxUseTime(stack, user) - remainingUseTicks;
        float progress = Math.min(1.0F, charge / (float) FULL_CHARGE);
        Random random = server.getRandom();

        // Luces que suben del suelo alrededor del jugador (cada 2 ticks, para no tapar la escena)
        int motes = charge % 2 == 0 ? 1 + (int) (progress * 2) : 0;
        for (int i = 0; i < motes; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 1.5 + random.nextDouble() * 2.5;
            server.spawnParticles(ParticleTypes.END_ROD,
                    user.getX() + Math.cos(angle) * radius, user.getY() + 0.1, user.getZ() + Math.sin(angle) * radius,
                    0, 0.0, 1.0, 0.0, 0.05 + progress * 0.12);
        }
        // Brillo dorado alrededor de la espada alzada
        server.spawnParticles(GOLD_DUST, user.getX(), user.getEyeY() + 0.9, user.getZ(),
                2 + (int) (progress * 4), 0.25, 0.5, 0.25, 0.0);

        if (charge == FULL_CHARGE / 3 || charge == FULL_CHARGE * 2 / 3) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 2.0F, 0.6F + progress);
        }
        if (charge == FULL_CHARGE) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 2.0F, 1.2F);
            server.spawnParticles(ParticleTypes.FLASH, user.getX(), user.getEyeY() + 1.0, user.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (charge >= FULL_CHARGE && charge % 3 == 0) {
            server.spawnParticles(ParticleTypes.ELECTRIC_SPARK, user.getX(), user.getEyeY() + 1.0, user.getZ(),
                    4, 0.3, 0.7, 0.3, 0.1);
        }
    }

    // Al soltar con la carga completa: ¡Excalibur!
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        int charge = getMaxUseTime(stack, user) - remainingUseTicks;
        if (charge < FULL_CHARGE || !(user instanceof PlayerEntity player)) return;

        Rules.commit(player, FateUBW.EXCALIBUR_NP, EXCALIBUR_COOLDOWN);
        com.nuwuman.fateubw.PlayerAnims.play(player, "excalibur_swing");
        if (!(world instanceof ServerWorld server)) return;

        ExcaliburBeamEntity.fire(server, player);
        Voices.say(world, player, "excalibur");
        player.swingHand(player.getActiveHand(), true);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 3.0F, 0.8F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 2.0F, 0.6F);

        // Retroceso por la potencia del golpe
        Vec3d dir = player.getRotationVec(1.0F);
        player.addVelocity(-dir.x * 0.5, 0.1, -dir.z * 0.5);
        player.velocityModified = true;
    }

    // Agachado + click derecho: ráfaga de viento en cono
    private void strikeAir(ServerWorld world, PlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F);
        Box area = player.getBoundingBox().stretch(dir.multiply(8.0)).expand(2.0);

        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> e != player && e.isAlive() && com.nuwuman.fateubw.Rules.canAffect(player, e))) {
            Vec3d to = target.getBoundingBox().getCenter().subtract(eye);
            if (to.length() > 8.0 || to.normalize().dotProduct(dir) < 0.5) continue;
            target.damage(world.getDamageSources().playerAttack(player), 6.0F);
            target.takeKnockback(2.0, -dir.x, -dir.z);
            target.addVelocity(0.0, 0.35, 0.0);
            target.velocityModified = true;
        }

        for (int i = 1; i <= 8; i++) {
            Vec3d p = eye.add(dir.multiply(i));
            world.spawnParticles(ParticleTypes.GUST, p.x, p.y - 0.3, p.z, 1, 0.1, 0.1, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.CLOUD, p.x, p.y - 0.3, p.z, 4, 0.3, 0.3, 0.3, 0.05);
        }
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, eye.x + dir.x * 1.5, eye.y - 0.3, eye.z + dir.z * 1.5,
                1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.PLAYERS, 1.5F, 0.8F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.excalibur.tooltip.charge").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.fate_ubw.excalibur.tooltip.strike_air").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("item.fate_ubw.excalibur.tooltip.air").formatted(Formatting.GRAY));
    }
}
