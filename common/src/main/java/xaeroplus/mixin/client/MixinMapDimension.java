package xaeroplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.map.world.MapDimension;
import xaeroplus.settings.Settings;

@Mixin(value = MapDimension.class, remap = false)
public class MixinMapDimension {

    @WrapOperation(method = "getSkyDarken", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSkyDarken(F)F"
    ))
    public float setNightMode(final ClientLevel instance, final float f, final Operation<Float> original) {
        if (Settings.REGISTRY.nightModeSetting.get()) {
            // matches ow midnight
            // you could move this to a setting to adjust if you want
            return 0.2f;
        }
        return original.call(instance, f);
    }
}
