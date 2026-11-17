package cn.minerealms.assimpshowcase.rocket.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimpshowcase.rocket.common.ExhaustProfile;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/** Client resource-pack profiles, reloaded by F3+T. Invalid overrides never install partial data. */
public final class ExhaustProfiles implements ResourceManagerReloadListener {
    public static final ResourceLocation DEFAULT = new ResourceLocation(AssimpLoader.MODID, "kerolox");
    private static final ExhaustProfile FALLBACK = ExhaustProfile.fromJson(new com.google.gson.JsonObject());
    private static volatile Map<ResourceLocation, ExhaustProfile> profiles = Map.of();

    public static ExhaustProfile get(ResourceLocation id) { return profiles.getOrDefault(id, FALLBACK); }

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        Map<ResourceLocation, ExhaustProfile> loaded = new HashMap<>();
        resources.listResources("exhaust_profiles", location -> location.getPath().endsWith(".json")).forEach((id, resource) -> {
            String path = id.getPath();
            ResourceLocation key = new ResourceLocation(id.getNamespace(), path.substring("exhaust_profiles/".length(), path.length() - 5));
            try (Reader reader = resource.openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                loaded.put(key, ExhaustProfile.fromJson(json.getAsJsonObject()));
            } catch (Exception failure) {
                AssimpLoader.LOGGER.warn("Rejected exhaust profile {}: {}", id, failure.toString());
            }
        });
        profiles = Map.copyOf(loaded);
        RocketExhausts.invalidateFrame();
        AssimpLoader.LOGGER.info("Loaded {} rocket exhaust profiles", loaded.size());
    }
}
