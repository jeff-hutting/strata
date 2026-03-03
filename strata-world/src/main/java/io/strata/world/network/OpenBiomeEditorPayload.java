package io.strata.world.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client packet that instructs the client to open the Biome Editor screen.
 *
 * <p>Sent by {@link io.strata.world.editor.BiomeEditorWandHandler} when the player
 * right-clicks the Strata Wand. Carries no data — the client always opens the editor
 * with the current local draft state.
 *
 * <p>Registered via {@code PayloadTypeRegistry.playS2C()} in
 * {@link io.strata.world.StrataWorld#onInitialize()}.
 * Received via {@code ClientPlayNetworking} in
 * {@link io.strata.world.StrataWorldClient#onInitializeClient()}.
 */
public record OpenBiomeEditorPayload() implements CustomPayload {

    public static final Id<OpenBiomeEditorPayload> ID =
            new CustomPayload.Id<>(Identifier.of("strata_world", "open_biome_editor"));

    /** Zero-data codec — this packet carries no payload bytes. */
    public static final PacketCodec<RegistryByteBuf, OpenBiomeEditorPayload> CODEC =
            PacketCodec.unit(new OpenBiomeEditorPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
