package cn.minerealms.assimploader.compat;

import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.render.AssimpPbrTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.WeakHashMap;

/** Optional bridge to Oculus/Iris PBR texture loaders, with no shader injection. */
public final class OculusPBRSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(OculusPBRSupport.class);
    private static final Map<AbstractTexture, Boolean> PREPARED = new WeakHashMap<>();
    private static Bridge bridge;
    private static boolean failed;

    private OculusPBRSupport() {}

    public static boolean isPBRAvailable() {
        return RenderSystem.isOnRenderThread() && enabled() && OculusCompat.isShadersActive()
                && ensureLoaderRegistered();
    }

    /** Registration precedes albedo allocation/binding, including when shaders are temporarily disabled. */
    public static boolean ensureLoaderRegistered() {
        if (!RenderSystem.isOnRenderThread() || !OculusCompat.isOculusLoaded() || failed) return false;
        if (bridge != null) return true;
        try {
            ClassLoader loader = OculusPBRSupport.class.getClassLoader();
            Class<?> registryType = pbrClass(loader, "texture.pbr.loader.PBRTextureLoaderRegistry", "pbr.loader.PBRTextureLoaderRegistry");
            Class<?> loaderType = pbrClass(loader, "texture.pbr.loader.PBRTextureLoader", "pbr.loader.PBRTextureLoader");
            Class<?> consumerType = pbrClass(loader, "texture.pbr.loader.PBRTextureLoader$PBRTextureConsumer", "pbr.loader.PBRTextureLoader$PBRTextureConsumer");
            Class<?> managerType = pbrClass(loader, "texture.pbr.PBRTextureManager", "pbr.texture.PBRTextureManager");
            Object registry = registryType.getField("INSTANCE").get(null);
            Object manager = managerType.getField("INSTANCE").get(null);
            Method normal = consumerType.getMethod("acceptNormalTexture", AbstractTexture.class);
            Method specular = consumerType.getMethod("acceptSpecularTexture", AbstractTexture.class);
            Object proxy = Proxy.newProxyInstance(loaderType.getClassLoader(), new Class<?>[]{loaderType}, (self, method, args) -> {
                if (method.getName().equals("load") && args != null && args.length == 3) {
                    load((AssimpPbrTexture) args[0], args[2], normal, specular);
                    return null;
                }
                return switch (method.getName()) {
                    case "toString" -> "AssimpLoader PBR texture loader";
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> args != null && args.length == 1 && self == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                };
            });
            registryType.getMethod("register", Class.class, loaderType).invoke(registry, AssimpPbrTexture.class, proxy);
            bridge = new Bridge(manager, managerType.getMethod("getOrLoadHolder", int.class),
                    managerType.getMethod("onDeleteTexture", int.class), proxy);
            LOGGER.info("Registered Oculus/Iris PBR loader for AssimpLoader material textures ({})", registryType.getPackageName());
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            fail(exception);
            return false;
        }
    }

    private static Class<?> pbrClass(ClassLoader loader, String oculusName, String irisName) throws ClassNotFoundException {
        try {
            return Class.forName("net.irisshaders.iris." + oculusName, false, loader);
        } catch (ClassNotFoundException legacyMissing) {
            return Class.forName("net.irisshaders.iris." + irisName, false, loader);
        }
    }

    private static boolean enabled() {
        return OculusCompat.isIntegrationEnabled() && OculusCompat.pbrConfigured() && !failed;
    }

    private static void load(AssimpPbrTexture base, Object consumer, Method acceptNormal, Method acceptSpecular) {
        if (!enabled() || !base.isReady() || base.maps() == null) return;
        DynamicTexture normal = null;
        DynamicTexture specular = null;
        boolean normalAccepted = false;
        boolean specularAccepted = false;
        try {
            // Allocate both before transfer. After accept, only Oculus may close that texture.
            if (base.maps().normal() != null) normal = child(base.maps().normal());
            if (base.maps().specular() != null) specular = child(base.maps().specular());
            if (normal != null) {
                acceptNormal.invoke(consumer, normal);
                normalAccepted = true;
            }
            if (specular != null) {
                acceptSpecular.invoke(consumer, specular);
                specularAccepted = true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            // Return a partial holder if a preceding accept succeeded; throwing would leak its ownership.
            fail(exception);
        } finally {
            base.recordBindings(normalAccepted ? normal.getId() : 0, specularAccepted ? specular.getId() : 0);
            if (normal != null && !normalAccepted) close(normal);
            if (specular != null && !specularAccepted) close(specular);
        }
    }

    private static DynamicTexture child(cn.minerealms.assimploader.material.LabPbrPacker.Image source) {
        var image = AssimpPbrTexture.nativeImage(source);
        DynamicTexture texture = null;
        try {
            texture = new DynamicTexture(image);
            image = null;
            texture.setFilter(false, false);
            return texture;
        } catch (RuntimeException | Error exception) {
            if (texture != null) close(texture);
            else if (image != null) image.close();
            throw exception;
        }
    }

    private static void close(AbstractTexture texture) {
        texture.close();
        texture.releaseId();
    }

    public static void registerPBRTextures(ResourceLocation albedo, AssimpMaterial material) {
        prepareTexture(albedo);
    }

    /** True only for actual AssimpLoader image channels handed to Oculus; defaults are not successes. */
    public static boolean prepareTexture(ResourceLocation albedo) {
        if (albedo == null || !RenderSystem.isOnRenderThread() || !OculusCompat.isOculusLoaded()) return false;
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(albedo);
        if (!(texture instanceof AssimpPbrTexture base)) return false;
        if (base.updateConfiguration(enabled())) invalidateTexture(base);
        if (!base.isReady() || !isPBRAvailable()) return false;
        Boolean known = PREPARED.get(base);
        if (known != null) return known;
        try {
            bridge.getHolder.invoke(bridge.manager, base.getId());
            boolean prepared = base.normalTextureId() > 0 || base.specularTextureId() > 0;
            PREPARED.put(base, prepared);
            return prepared;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            fail(exception);
            return false;
        }
    }

    /** Clears a cached default holder after asynchronous image preparation or a material change. */
    public static void invalidateTexture(AssimpPbrTexture texture) {
        PREPARED.remove(texture);
        texture.recordBindings(0, 0);
        if (bridge == null) return;
        try {
            bridge.deleteHolder.invoke(bridge.manager, texture.getId());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            fail(exception);
        }
    }

    private static void fail(Throwable exception) {
        if (!failed) LOGGER.warn("Oculus PBR bridge unavailable; keeping base-color rendering until reload", exception);
        failed = true;
        PREPARED.clear();
    }

    public static String getNormalMapPath(String texturePath) { return appendSuffix(texturePath, "_n"); }
    public static String getSpecularMapPath(String texturePath) { return appendSuffix(texturePath, "_s"); }

    private static String appendSuffix(String path, String suffix) {
        if (path == null || path.isBlank() || path.startsWith("*")) return null;
        int dot = path.lastIndexOf('.');
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return dot > slash ? path.substring(0, dot) + suffix + path.substring(dot) : path + suffix;
    }

    public static void reset() {
        failed = false;
        PREPARED.clear();
        // Registry entries survive resource reload; keep the strongly-held proxy and resolved interface methods.
    }

    private record Bridge(Object manager, Method getHolder, Method deleteHolder, Object loaderProxy) {}
}
