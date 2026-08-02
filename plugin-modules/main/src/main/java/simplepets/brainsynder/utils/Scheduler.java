package simplepets.brainsynder.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import simplepets.brainsynder.PetCore;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Routes work to the thread that owns the affected server state.
 *
 * <p>The Paper region scheduler API is accessed reflectively so the same jar can
 * continue to load on Spigot. Paper implements these schedulers on its main
 * thread, while Folia implements them on the appropriate region thread.</p>
 */
public final class Scheduler {
    private static final Runnable RETIRED_TASK = () -> { };

    private static final Method GET_GLOBAL_SCHEDULER = findMethod(Bukkit.class, "getGlobalRegionScheduler");
    private static final Method GET_REGION_SCHEDULER = findMethod(Bukkit.class, "getRegionScheduler");
    private static final Method GET_ASYNC_SCHEDULER = findMethod(Bukkit.class, "getAsyncScheduler");
    private static final Method IS_OWNED_BY_CURRENT_REGION = findMethod(Bukkit.class, "isOwnedByCurrentRegion", Entity.class);
    private static final Method IS_LOCATION_OWNED_BY_CURRENT_REGION = findMethod(Bukkit.class, "isOwnedByCurrentRegion", Location.class);
    private static final Method GET_ENTITY_SCHEDULER = findMethod(Entity.class, "getScheduler");
    private static final Method TELEPORT_ASYNC = findMethod(Entity.class, "teleportAsync", Location.class);
    private static final Method GET_TRACKED_PLAYERS = firstMethod(Entity.class, "getTrackedBy", "getTrackedPlayers");
    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");
    private static final Method GLOBAL_EXECUTE = findSchedulerMethod(GET_GLOBAL_SCHEDULER,
        "execute", Plugin.class, Runnable.class);
    private static final Method GLOBAL_RUN_DELAYED = findSchedulerMethod(GET_GLOBAL_SCHEDULER,
        "runDelayed", Plugin.class, Consumer.class, long.class);
    private static final Method GLOBAL_CANCEL_TASKS = findSchedulerMethod(GET_GLOBAL_SCHEDULER,
        "cancelTasks", Plugin.class);
    private static final Method REGION_EXECUTE = findSchedulerMethod(GET_REGION_SCHEDULER,
        "execute", Plugin.class, Location.class, Runnable.class);
    private static final Method REGION_RUN_DELAYED = findSchedulerMethod(GET_REGION_SCHEDULER,
        "runDelayed", Plugin.class, Location.class, Consumer.class, long.class);
    private static final Method ASYNC_RUN_NOW = findSchedulerMethod(GET_ASYNC_SCHEDULER,
        "runNow", Plugin.class, Consumer.class);
    private static final Method ASYNC_CANCEL_TASKS = findSchedulerMethod(GET_ASYNC_SCHEDULER,
        "cancelTasks", Plugin.class);
    private static final Method ENTITY_EXECUTE = findSchedulerMethod(GET_ENTITY_SCHEDULER,
        "execute", Plugin.class, Runnable.class, Runnable.class, long.class);

    private Scheduler() {
    }

    public static boolean supportsRegionScheduling() {
        return GET_GLOBAL_SCHEDULER != null
            && GET_REGION_SCHEDULER != null
            && GET_ASYNC_SCHEDULER != null
            && GET_ENTITY_SCHEDULER != null;
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void runGlobal(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!supportsRegionScheduling()) {
            Bukkit.getScheduler().runTask(plugin(), task);
            return;
        }

        Object scheduler = invoke(GET_GLOBAL_SCHEDULER, null);
        invoke(GLOBAL_EXECUTE, scheduler, plugin(), task);
    }

    public static void runGlobalLater(Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        if (!supportsRegionScheduling()) {
            Bukkit.getScheduler().runTaskLater(plugin(), task, delayTicks);
            return;
        }

        Object scheduler = invoke(GET_GLOBAL_SCHEDULER, null);
        Consumer<Object> consumer = ignored -> task.run();
        invoke(GLOBAL_RUN_DELAYED, scheduler, plugin(), consumer, validTickDelay(delayTicks));
    }

    public static void runAt(Location location, Runnable task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");
        if (!supportsRegionScheduling()) {
            Bukkit.getScheduler().runTask(plugin(), task);
            return;
        }

        Object scheduler = invoke(GET_REGION_SCHEDULER, null);
        invoke(REGION_EXECUTE, scheduler, plugin(), location, task);
    }

    public static void runAtLater(Location location, Runnable task, long delayTicks) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");
        if (!supportsRegionScheduling()) {
            Bukkit.getScheduler().runTaskLater(plugin(), task, delayTicks);
            return;
        }

        Object scheduler = invoke(GET_REGION_SCHEDULER, null);
        Consumer<Object> consumer = ignored -> task.run();
        invoke(REGION_RUN_DELAYED, scheduler, plugin(), location, consumer, validTickDelay(delayTicks));
    }

    public static void runEntity(Entity entity, Runnable task) {
        runEntityLater(entity, task, RETIRED_TASK, 1L);
    }

    public static void runEntityLater(Entity entity, Runnable task, long delayTicks) {
        runEntityLater(entity, task, RETIRED_TASK, delayTicks);
    }

    public static void runEntityLater(Entity entity, Runnable task, Runnable retired, long delayTicks) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(retired, "retired");
        if (!supportsRegionScheduling()) {
            Bukkit.getScheduler().runTaskLater(plugin(), task, delayTicks);
            return;
        }

        Object scheduler = invoke(GET_ENTITY_SCHEDULER, entity);
        Object scheduled = invoke(ENTITY_EXECUTE, scheduler, plugin(), task, retired, validTickDelay(delayTicks));
        if (Boolean.FALSE.equals(scheduled)) retired.run();
    }

    public static <T> CompletableFuture<T> callEntity(Entity entity, Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        CompletableFuture<T> future = new CompletableFuture<>();
        runEntityLater(entity, () -> {
            try {
                future.complete(task.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        }, () -> future.completeExceptionally(new IllegalStateException("Entity retired before its task ran")), 1L);
        return future;
    }

    public static void run(CommandSender sender, Runnable task) {
        if (sender instanceof Entity entity) {
            runEntity(entity, task);
        } else {
            runGlobal(task);
        }
    }

    public static void runLater(CommandSender sender, Runnable task, long delayTicks) {
        if (sender instanceof Entity entity) {
            runEntityLater(entity, task, delayTicks);
        } else {
            runGlobalLater(task, delayTicks);
        }
    }

    public static void runAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!supportsRegionScheduling()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin(), task);
            return;
        }

        Object scheduler = invoke(GET_ASYNC_SCHEDULER, null);
        Consumer<Object> consumer = ignored -> task.run();
        invoke(ASYNC_RUN_NOW, scheduler, plugin(), consumer);
    }

    public static boolean isOwnedByCurrentRegion(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        if (!supportsRegionScheduling() || IS_OWNED_BY_CURRENT_REGION == null) return true;
        return (boolean) invoke(IS_OWNED_BY_CURRENT_REGION, null, entity);
    }

    public static boolean isOwnedByCurrentRegion(Location location) {
        Objects.requireNonNull(location, "location");
        if (!supportsRegionScheduling() || IS_LOCATION_OWNED_BY_CURRENT_REGION == null) return true;
        return (boolean) invoke(IS_LOCATION_OWNED_BY_CURRENT_REGION, null, location);
    }

    public static boolean forEachTrackedPlayer(Entity entity, Consumer<Player> action) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(action, "action");
        if (GET_TRACKED_PLAYERS == null) return false;

        Object result = invoke(GET_TRACKED_PLAYERS, entity);
        if (!(result instanceof Iterable<?> players)) return false;
        players.forEach(value -> {
            if (value instanceof Player player) action.accept(player);
        });
        return true;
    }

    public static void teleport(Entity entity, Location destination, Consumer<Boolean> completion) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(completion, "completion");

        runEntityLater(entity, () -> {
            if (!supportsRegionScheduling() || TELEPORT_ASYNC == null) {
                completion.accept(entity.teleport(destination));
                return;
            }

            Object result = invoke(TELEPORT_ASYNC, entity, destination);
            if (!(result instanceof CompletableFuture<?> future)) {
                completion.accept(false);
                return;
            }

            future.whenComplete((success, throwable) -> runEntityLater(entity,
                () -> completion.accept(throwable == null && Boolean.TRUE.equals(success)),
                () -> completion.accept(false), 1L));
        }, () -> completion.accept(false), 1L);
    }

    public static void cancelTasks() {
        if (!supportsRegionScheduling()) {
            Bukkit.getScheduler().cancelTasks(plugin());
            return;
        }

        Object globalScheduler = invoke(GET_GLOBAL_SCHEDULER, null);
        invoke(GLOBAL_CANCEL_TASKS, globalScheduler, plugin());
        Object asyncScheduler = invoke(GET_ASYNC_SCHEDULER, null);
        invoke(ASYNC_CANCEL_TASKS, asyncScheduler, plugin());
    }

    private static long validTickDelay(long delayTicks) {
        return Math.max(1L, delayTicks);
    }

    private static PetCore plugin() {
        return PetCore.getInstance();
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method firstMethod(Class<?> owner, String... names) {
        for (String name : names) {
            Method method = findMethod(owner, name);
            if (method != null) return method;
        }
        return null;
    }

    private static Method findSchedulerMethod(Method schedulerAccessor, String name, Class<?>... parameterTypes) {
        if (schedulerAccessor == null) return null;
        return findMethod(schedulerAccessor.getReturnType(), name, parameterTypes);
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, Scheduler.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static Object invoke(Method method, Object owner, Object... arguments) {
        if (method == null) throw new IllegalStateException("Attempted to invoke an unavailable scheduler method");
        try {
            return method.invoke(owner, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to access scheduler method " + method, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Scheduler method failed " + method, cause);
        }
    }
}
