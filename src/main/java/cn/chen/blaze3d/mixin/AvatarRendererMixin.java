package cn.chen.blaze3d.mixin;
import cn.chen.blaze3d.player.PlayerReplacementRuntime;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Z", at = @At("HEAD"), cancellable = true)
    private void blaze3d$hideLayers(AvatarRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (state.isInvisible) cir.setReturnValue(false);
    }
    @Inject(method = "isEntityUpsideDown(Lnet/minecraft/world/entity/Avatar;)Z", at = @At("HEAD"))
    private void blaze3d$markHidden(Avatar entity, CallbackInfoReturnable<Boolean> cir) {
        if (PlayerReplacementRuntime.INSTANCE.shouldHideVanilla(entity)) entity.setInvisible(true);
    }
}
