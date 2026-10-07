package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/** Ropa de EMIYA con modelo 3D de GeckoLib (geo/item/armor/archer_armor.geo.json) y faldón animado. */
public class ArcherArmorItem extends ArmorItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.archer_armor.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ArcherArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.ARCHER_MATERIAL, type, settings.maxDamage(type.getMaxDamage(37)));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<ArcherArmorItem> renderer;

            @Override
            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack,
                                                                                    @Nullable EquipmentSlot slot, @Nullable BipedEntityModel<T> original) {
                if (renderer == null) renderer = new GeoArmorRenderer<>(new DefaultedItemGeoModel<>(FateUBW.id("armor/archer_armor")));
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, 10, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // Conjunto completo (peto, grebas, botas): visión nocturna y los monstruos cercanos brillan (Ojo de Halcón)
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || !(world instanceof ServerWorld server) || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack) return;
        if (!player.getEquippedStack(EquipmentSlot.LEGS).isOf(FateUBW.ARCHER_LEGGINGS)
                || !player.getEquippedStack(EquipmentSlot.FEET).isOf(FateUBW.ARCHER_BOOTS)) return;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false));
        for (LivingEntity mob : server.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(32.0), e -> e instanceof Monster)) {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, true, false));
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.archer_armor.tooltip.set").formatted(Formatting.GOLD));
    }
}
