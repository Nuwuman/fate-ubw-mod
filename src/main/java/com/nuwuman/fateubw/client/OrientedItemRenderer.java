package com.nuwuman.fateubw.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Dibuja el modelo 3D de un ítem (construido apuntando a +Y) orientado según el yaw/pitch del proyectil,
 * con la misma convención que las flechas vanilla. Opcionalmente gira sobre su eje como un taladro.
 */
public class OrientedItemRenderer<T extends Entity> extends EntityRenderer<T> {
    private final ItemRenderer itemRenderer;
    private final Function<T, ItemStack> stack;
    private final Predicate<T> spin;

    public OrientedItemRenderer(EntityRendererFactory.Context ctx, Function<T, ItemStack> stack, Predicate<T> spin) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.stack = stack;
        this.spin = spin;
    }

    @Override
    public void render(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        // Deja +X en la dirección del vuelo...
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()) - 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch())));
        // ...y el modelo, que mira hacia +Y, se tumba sobre +X
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90.0F));
        if (spin.test(entity)) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + tickDelta) * 40.0F));
        itemRenderer.renderItem(stack.apply(entity), ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV,
                matrices, vertexConsumers, entity.getWorld(), entity.getId());
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(T entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
