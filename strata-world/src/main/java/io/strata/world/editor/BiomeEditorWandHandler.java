package io.strata.world.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.strata.core.util.StrataLogger;
import io.strata.core.wand.WandHandler;
import io.strata.world.network.BiomeSamplePayload;
import io.strata.world.network.OpenBiomeEditorPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.util.collection.Weighted;
import net.minecraft.world.biome.BiomeEffects;
import net.minecraft.world.biome.SpawnSettings;

/**
 * Wand handler for the Biome Editor.
 *
 * <p>Matches when the player right-clicks in open air or points at terrain.
 * Opens the {@link BiomeEditorScreen} with the current draft loaded, or
 * samples the biome at the player's position as a starting template.
 */
public class BiomeEditorWandHandler implements WandHandler {

    private static final Gson GSON = new GsonBuilder().create();

    public static final String HANDLER_ID = "biome_editor";

    @Override
    public String getId() {
        return HANDLER_ID;
    }

    @Override
    public String getDisplayName() {
        return "Biome Editor";
    }

    @Override
    public boolean matches(World world, PlayerEntity player, HitResult hit) {
        return true;
    }

    @Override
    public void handle(World world, PlayerEntity player, HitResult hit) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        // If pointing at a block, sample the biome at that position
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            String biomeJson = sampleBiomeAt(world, pos);
            if (biomeJson != null) {
                StrataLogger.debug("Biome editor: sampling biome at {} for {}",
                        pos, player.getName().getString());
                ServerPlayNetworking.send(serverPlayer, new BiomeSamplePayload(biomeJson));
                return;
            }
        }

        // No block hit — just open the editor with current draft
        StrataLogger.debug("Biome editor wand handler: sending OpenBiomeEditor to {}",
                player.getName().getString());
        ServerPlayNetworking.send(serverPlayer, new OpenBiomeEditorPayload());
    }

    private String sampleBiomeAt(World world, BlockPos pos) {
        RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
        Biome biome = biomeEntry.value();

        String biomeName = biomeEntry.getKey()
                .map(key -> key.getValue().toString())
                .orElse("unknown");

        JsonObject json = new JsonObject();

        // Identity
        String displayPart = biomeName.contains(":") ? biomeName.split(":", 2)[1] : biomeName;
        String displayName = capitalizeWords(displayPart.replace("_", " "));
        json.addProperty("displayName", displayName);
        json.addProperty("biomeId", biomeName);
        json.addProperty("templateSource", biomeName);

        // Layer 1: Colors from Biome convenience methods
        json.addProperty("waterColor", biome.getWaterColor());
        json.addProperty("foliageColor", biome.getFoliageColor());
        json.addProperty("grassColor", biome.getGrassColorAt(pos.getX(), pos.getZ()));

        // BiomeEffects in 1.21.11: waterColor, foliageColor(Optional), grassColor(Optional)
        // Sky, fog, waterFog are now in EnvironmentAttributes — use sensible defaults
        json.addProperty("skyColor", 7907327);
        json.addProperty("fogColor", 12638463);
        json.addProperty("waterFogColor", 329011);

        // Precipitation
        boolean hasPrecip = biome.hasPrecipitation();
        float temp = biome.getTemperature();
        json.addProperty("hasRain", hasPrecip && temp >= 0.15f);
        json.addProperty("hasSnow", hasPrecip && temp < 0.15f);

        // Layer 2
        json.addProperty("temperature", temp);
        json.addProperty("humidity", 0.5f);

        // Spawn entries (creature group)
        JsonArray spawns = new JsonArray();
        SpawnSettings spawnSettings = biome.getSpawnSettings();
        for (Weighted<SpawnSettings.SpawnEntry> weighted :
                spawnSettings.getSpawnEntries(SpawnGroup.CREATURE).getEntries()) {
            SpawnSettings.SpawnEntry entry = weighted.value();
            JsonObject spawn = new JsonObject();
            String entityId = Registries.ENTITY_TYPE.getId(entry.type()).toString();
            spawn.addProperty("entityId", entityId);
            spawn.addProperty("weight", weighted.weight());
            spawn.addProperty("minGroupSize", entry.minGroupSize());
            spawn.addProperty("maxGroupSize", entry.maxGroupSize());
            spawns.add(spawn);
        }
        json.add("spawnEntries", spawns);

        return GSON.toJson(json);
    }

    private static String capitalizeWords(String input) {
        StringBuilder sb = new StringBuilder();
        for (String word : input.split(" ")) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
