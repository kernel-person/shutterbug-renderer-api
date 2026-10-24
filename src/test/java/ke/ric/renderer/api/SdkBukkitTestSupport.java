package ke.ric.renderer.api;

import org.bukkit.Server;
import org.bukkit.Registry;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;

final class SdkBukkitTestSupport {
    final Thread serverThread = Thread.currentThread();
    final Queue<Runnable> queued = new ConcurrentLinkedQueue<>();
    final List<Listener> listeners = new ArrayList<>();
    volatile boolean enabled = true;
    volatile boolean rejectScheduling;
    RendererService service;
    org.bukkit.map.MapView createdMap;
    org.bukkit.inventory.ItemFactory itemFactory;
    java.util.function.Function<Class<?>, Registry<?>> registries = type -> null;
    int mapsCreated;
    final BukkitScheduler scheduler = proxy(BukkitScheduler.class, (p, m, a) -> {
        if (m.getName().equals("runTask")) {
            if (rejectScheduling) throw new RejectedExecutionException("scheduler unavailable");
            queued.add((Runnable) a[1]);
            return proxy(BukkitTask.class, (task, method, args) -> defaultValue(method.getReturnType()));
        }
        return defaultValue(m.getReturnType());
    });
    final ServicesManager services = proxy(ServicesManager.class, (p, m, a) ->
            m.getName().equals("load") ? service : defaultValue(m.getReturnType()));
    final PluginManager plugins = proxy(PluginManager.class, (p, m, a) -> {
        if (m.getName().equals("registerEvents")) listeners.add((Listener) a[0]);
        return defaultValue(m.getReturnType());
    });
    final Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
        case "isPrimaryThread" -> Thread.currentThread() == serverThread;
        case "getScheduler" -> scheduler;
        case "getServicesManager" -> services;
        case "getPluginManager" -> plugins;
        case "getItemFactory" -> itemFactory;
        case "getRegistry" -> registries.apply((Class<?>) a[0]);
        case "createMap" -> { mapsCreated++; yield createdMap; }
        default -> defaultValue(m.getReturnType());
    });
    final Plugin owner = proxy(Plugin.class, (p, m, a) -> switch (m.getName()) {
        case "isEnabled" -> enabled;
        case "getServer" -> server;
        case "getName" -> "sdk-test";
        default -> defaultValue(m.getReturnType());
    });

    void drain() { for (Runnable next; (next = queued.poll()) != null;) next.run(); }

    void fire(Object event) throws Exception {
        record Handler(Listener listener, java.lang.reflect.Method method) {}
        List<Handler> handlers = new ArrayList<>();
        for (Listener listener : List.copyOf(listeners)) {
            for (var method : listener.getClass().getDeclaredMethods()) {
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == event.getClass()) {
                    handlers.add(new Handler(listener, method));
                }
            }
        }
        handlers.sort(java.util.Comparator.comparingInt(handler -> {
            var annotation=handler.method().getAnnotation(org.bukkit.event.EventHandler.class);
            return annotation==null?org.bukkit.event.EventPriority.NORMAL.ordinal():annotation.priority().ordinal();
        }));
        for (Handler handler : handlers) {
            var annotation=handler.method().getAnnotation(org.bukkit.event.EventHandler.class);
            if(annotation!=null && annotation.ignoreCancelled() && event instanceof org.bukkit.event.Cancellable cancellable && cancellable.isCancelled())continue;
            handler.method().setAccessible(true);
            handler.method().invoke(handler.listener(), event);
        }
    }

    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("toString")) return type.getSimpleName() + "-fixture";
            return handler.invoke(p, m, a);
        }));
    }

    static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return (char) 0;
        return 0;
    }
}
