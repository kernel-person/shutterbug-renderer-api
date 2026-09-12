package docs.examples;

import org.bukkit.plugin.java.JavaPlugin;

/** API-free entry point for a soft-dependent consumer. */
public final class OptionalProviderEntrypoint extends JavaPlugin {
    @Override public void onEnable() {
        try {
            Class.forName("ke.ric.renderer.api.RendererService", false, getClassLoader());
            Class.forName("docs.examples.OptionalRendererRuntime", true, getClassLoader())
                    .getMethod("enable", JavaPlugin.class).invoke(null, this);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            getLogger().warning("Renderer API/provider is unavailable; continuing without rendering");
        }
    }
    @Override public void onDisable() {
        try { Class.forName("docs.examples.OptionalRendererRuntime", false, getClassLoader())
                .getMethod("disable").invoke(null); }
        catch (ReflectiveOperationException | LinkageError ignored) { }
    }
}
