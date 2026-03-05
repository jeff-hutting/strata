package io.strata.world.mixin;

import io.strata.world.editor.BiomeDesignWorldPreset;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents "Open to LAN" in Biome Design Worlds.
 *
 * <p>Biome Design Worlds are singleplayer-only per SPEC §7.0. This mixin
 * intercepts {@link IntegratedServer#openToLan(GameMode, boolean, int)} and
 * returns {@code false} (LAN not opened) when the world is a Biome Design World.
 */
@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    @Inject(method = "openToLan", at = @At("HEAD"), cancellable = true)
    private void strata$blockLanInDesignWorld(GameMode gameMode, boolean cheatsAllowed, int port,
                                               CallbackInfoReturnable<Boolean> cir) {
        IntegratedServer self = (IntegratedServer) (Object) this;
        if (BiomeDesignWorldPreset.isCurrentWorldBiomeDesignWorld(self)) {
            cir.setReturnValue(false);
        }
    }
}
