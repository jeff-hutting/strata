package io.strata.core.attachment;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class StrataAttachments {

    private StrataAttachments() {}

    public static final AttachmentType<Boolean> PLAYER_INITIALIZED =
            AttachmentRegistry.create(
                    Identifier.of("strata_core", "player_initialized"),
                    builder -> builder
                            .initializer(() -> false)
                            .persistent(Codec.BOOL)
            );

    public static final AttachmentType<Map<String, String>> PLAYER_TAGS =
            AttachmentRegistry.create(
                    Identifier.of("strata_core", "player_tags"),
                    builder -> builder
                            .initializer(HashMap::new)
                            .persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING))
            );

    public static void init() {
        // Force class loading to register attachment types
    }
}
