package ke.ric.renderer.sample;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Bukkit-loadable shell that does not symbolically link the optional renderer API. */
public final class RendererSamplePlugin extends JavaPlugin {
    private static final String API_SERVICE = "ke.ric.renderer.api.RendererService";
    private static final String API_RUNTIME =
            "ke.ric.renderer.sample.RendererSampleApiRuntime";

    private RendererSampleRuntime runtime;

    @Override
    public void onEnable() {
        try {
            ClassLoader loader = getClassLoader();
            Class.forName(API_SERVICE, false, loader);
            Class<?> runtimeType = Class.forName(API_RUNTIME, true, loader);
            Method factory = runtimeType.getDeclaredMethod("enable", JavaPlugin.class);
            factory.setAccessible(true);
            runtime = (RendererSampleRuntime) factory.invoke(null, this);
        } catch (ClassNotFoundException failure) {
            getLogger().warning("RENDERER_SAMPLE_UNAVAILABLE renderer API unavailable; "
                    + "sample remains enabled");
        } catch (ReflectiveOperationException | LinkageError failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation
                    && invocation.getCause() != null ? invocation.getCause() : failure;
            getLogger().warning("RENDERER_SAMPLE_UNAVAILABLE renderer activation failed ("
                    + cause.getClass().getSimpleName() + "); sample remains enabled");
        }
    }

    @Override
    public void onDisable() {
        RendererSampleRuntime active = runtime;
        runtime = null;
        if (active != null) active.close();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        RendererSampleRuntime active = runtime;
        if (active != null) return active.onCommand(sender, command, label, args);
        if (!"renderer-sample".equalsIgnoreCase(command.getName()) || args.length != 1) {
            return false;
        }
        getLogger().warning(
                "RENDERER_SAMPLE_UNAVAILABLE renderer API/provider is unavailable");
        return true;
    }
}
