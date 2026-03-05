package io.strata.world.mixin;

import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.gen.carver.ConfiguredCarver;
import net.minecraft.world.gen.feature.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Exposes the package-private members of {@link GenerationSettings} needed to
 * construct dynamic generation settings for the editor preview biome.
 *
 * <p>{@link #strata$getCarvers()} allows the existing carvers to be preserved
 * when rebuilding settings with a new feature list.
 *
 * <p>{@link #strata$create(RegistryEntryList, List)} invokes the package-private
 * constructor so a new {@link GenerationSettings} instance can be built from
 * the editor's current feature list without going through the codec.
 */
@Mixin(GenerationSettings.class)
public interface GenerationSettingsAccessor {

    /** Returns the carvers entry list stored on this settings instance. */
    @Accessor("carvers")
    RegistryEntryList<ConfiguredCarver<?>> strata$getCarvers();

    /**
     * Invokes the package-private {@link GenerationSettings} constructor.
     * The resulting instance correctly initialises the derived {@code flowerFeatures}
     * and {@code allowedFeatures} caches from the supplied {@code features} list.
     */
    @Invoker("<init>")
    static GenerationSettings strata$create(
            RegistryEntryList<ConfiguredCarver<?>> carvers,
            List<RegistryEntryList<PlacedFeature>> features) {
        throw new AssertionError("Mixin invoker not applied");
    }
}
