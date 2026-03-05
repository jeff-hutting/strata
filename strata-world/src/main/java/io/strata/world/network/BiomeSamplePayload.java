package io.strata.world.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client packet carrying sampled biome properties for the Biome Editor.
 *
 * <p>Sent by {@link io.strata.world.editor.BiomeEditorWandHandler} when the player
 * right-clicks terrain with the Strata Wand. The biome at the player's position is
 * sampled and its properties are serialized as a JSON string.
 */
public record BiomeSamplePayload(String biomeJson) implements CustomPayload {

    public static final Id<BiomeSamplePayload> ID =
            new CustomPayload.Id<>(Identifier.of("strata_world", "biome_sample"));

    public static final PacketCodec<RegistryByteBuf, BiomeSamplePayload> CODEC =
            PacketCodec.ofStatic(
                    (buf, payload) -> buf.writeString(payload.biomeJson()),
                    buf -> new BiomeSamplePayload(buf.readString())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
