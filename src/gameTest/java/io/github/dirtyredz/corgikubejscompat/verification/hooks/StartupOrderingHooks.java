package io.github.dirtyredz.corgikubejscompat.verification.hooks;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bootstrap-visible hooks: JDK-only, no classloading of Minecraft from the agent. */
public final class StartupOrderingHooks {
    private static final String MODE = System.getProperty("compat.scenario", "invalid");
    private static final CountDownLatch WRITER_HELD = new CountDownLatch(1);
    private static final CountDownLatch RELEASE_WRITER = new CountDownLatch(1);
    private static final CountDownLatch INITIALIZER_END = new CountDownLatch(1);
    private static final CountDownLatch CONSTRUCTOR_HELD = new CountDownLatch(1);
    private static final CountDownLatch HOOK_ENTERED = new CountDownLatch(1);
    private static final CountDownLatch HOOK_RETURNED = new CountDownLatch(1);
    private static final CountDownLatch CONSTRUCTOR_DONE = new CountDownLatch(1);
    private static final AtomicInteger ENUMERATIONS = new AtomicInteger();
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final AtomicInteger OVERLAPS = new AtomicInteger();
    private static final Set<Mutation> WRITING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> CORGI_WRITES = new ConcurrentHashMap<>();
    private static final AtomicBoolean FIRST_HOOK = new AtomicBoolean(true);
    private static volatile boolean cme;
    private static volatile boolean reloadComplete;
    private static volatile Thread constructorThread;
    private static volatile Thread firstHookThread;
    private static final AtomicInteger SNAPSHOTS = new AtomicInteger();
    private static PrintWriter trace;

    private StartupOrderingHooks() {}

    public static void start() {
        try {
            trace = new PrintWriter(Files.newBufferedWriter(Path.of("startup-ordering-trace.log")));
        } catch (IOException e) { throw new UncheckedIOException(e); }
        require(Set.of("control", "writer-first", "reader-first", "baseline").contains(MODE), "Unknown scenario");
        event("SCENARIO " + MODE);
        if (MODE.equals("writer-first")) {
            Thread coordinator = new Thread(() -> {
                await(WRITER_HELD, "writer held");
                await(HOOK_ENTERED, "hook entered while writer held");
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
                boolean atBarrier = false;
                while (!atBarrier && System.nanoTime() < deadline) {
                    StackTraceElement[] stack = firstHookThread.getStackTrace();
                    atBarrier = stack.length > 0 && stack[0].getClassName().equals(
                            "io.github.dirtyredz.corgikubejscompat.CorgiRegistryPlugin")
                            && stack[0].getMethodName().equals("registerTypeWrappers");
                    if (!atBarrier) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
                }
                require(atBarrier, "Hook did not reach the held class-initialization barrier");
                event("HOOK_WAITING_ON_INITIALIZATION");
                require(HOOK_RETURNED.getCount() == 1, "Hook returned while initializer held");
                event("INDEPENDENT_WRITER_RELEASE");
                RELEASE_WRITER.countDown();
            }, "compat-latch-coordinator");
            coordinator.setDaemon(true);
            coordinator.start();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(StartupOrderingHooks::finish, "compat-result"));
    }

    public static synchronized void event(String message) {
        Thread thread = Thread.currentThread();
        trace.println(System.nanoTime() + " [" + thread.getName() + "#" + thread.getId() + "] " + message);
        trace.flush();
    }
    public static void invalid(String message, Throwable failure) {
        event("INVALID " + message);
        if (failure != null) { synchronized (StartupOrderingHooks.class) { failure.printStackTrace(trace); trace.flush(); } }
        System.err.println("COMPAT INVALID: " + message);
        Runtime.getRuntime().halt(86);
    }
    private static void require(boolean condition, String message) {
        if (!condition) invalid(message, null);
    }
    private static void await(CountDownLatch latch, String milestone) {
        try { require(latch.await(60, TimeUnit.SECONDS), "Watchdog expired: " + milestone); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); invalid(milestone, e); }
    }

    public static void constructorEnter() {
        constructorThread = Thread.currentThread();
        event("CONSTRUCTOR_BEFORE_INIT");
        CONSTRUCTOR_HELD.countDown();
        if (MODE.equals("reader-first")) await(HOOK_RETURNED, "reader initializes before constructor");
    }
    public static void constructorInitialized() {
        event("CONSTRUCTOR_INIT_DONE");
        CONSTRUCTOR_DONE.countDown();
    }
    public static void initializerEnter() {
        if (MODE.equals("reader-first")) {
            require(Thread.currentThread() == firstHookThread, "Reader-first initializer was not run by the hook");
            require(CONSTRUCTOR_DONE.getCount() == 1, "Reader-first constructor was not held");
        } else if (MODE.equals("writer-first") || MODE.equals("control")) {
            require(Thread.currentThread() == constructorThread, "Writer initializer was not run by the constructor");
        }
        event("INITIALIZER_ENTER");
        WRITER_HELD.countDown();
        if (MODE.equals("control") || MODE.equals("writer-first")) await(RELEASE_WRITER, "release initializer");
    }
    public static void initializerEnd() {
        event("INITIALIZER_END");
        INITIALIZER_END.countDown();
    }
    public static void hookEnter() {
        if (FIRST_HOOK.getAndSet(false)) {
            firstHookThread = Thread.currentThread();
            if (MODE.equals("writer-first")) await(WRITER_HELD, "writer initializes first");
            if (MODE.equals("reader-first")) await(CONSTRUCTOR_HELD, "constructor held before init");
        }
        event("HOOK_ENTER");
        HOOK_ENTERED.countDown();
    }
    public static void hookReturn() {
        require(INITIALIZER_END.getCount() == 0, "Hook returned before initializer end");
        event("HOOK_RETURN");
        HOOK_RETURNED.countDown();
    }

    public static Iterator<?> rootIterator(Set<?> keys) {
        if (MODE.equals("control")) await(WRITER_HELD, "writer held before iterator");
        if (!MODE.equals("control") && !MODE.equals("baseline")) {
            require(HOOK_RETURNED.getCount() == 0, "Iterator before hook return");
            require(INITIALIZER_END.getCount() == 0, "Iterator before initializer end");
        }
        ACTIVE.incrementAndGet();
        WRITING.forEach(Mutation::overlap);
        Iterator<?> original = keys.iterator(); // Must precede the control's root writes.
        event("ITERATOR_CREATED " + ENUMERATIONS.incrementAndGet());
        if (MODE.equals("control")) {
            RELEASE_WRITER.countDown();
            await(CONSTRUCTOR_DONE, "writer finished before resuming control iterator");
        }
        return new Iterator<Object>() {
            private boolean ended;
            private void end() {
                if (!ended) { ended = true; ACTIVE.decrementAndGet(); event("ENUMERATION_END"); }
            }
            @Override public boolean hasNext() {
                boolean next = original.hasNext();
                if (!next) end();
                return next;
            }
            @Override public Object next() {
                try { return original.next(); }
                catch (ConcurrentModificationException expected) {
                    cme = true;
                    event("ORIGINAL_CME");
                    synchronized (StartupOrderingHooks.class) { expected.printStackTrace(trace); trace.flush(); }
                    end();
                    if (MODE.equals("control")) finish();
                    throw expected;
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static Map<?, ?> traceMap(Map<?, ?> map, Object registryKey) {
        if (!registryKey.toString().equals("ResourceKey[minecraft:root / minecraft:root]")) return map;
        event("ROOT_MAP_ATTACHED");
        return new TracedRootMap((Map<Object, Object>) map);
    }
    static Mutation mutation(String operation, Object key) {
        if (operation.equals("put-unchanged")) {
            event("ROOT_PUT_UNCHANGED " + key);
            return null;
        }
        Mutation mutation = new Mutation();
        WRITING.add(mutation);
        if (ACTIVE.get() != 0) mutation.overlap();
        String text = String.valueOf(key);
        event("ROOT_MUTATION " + operation + " " + text + " active=" + ACTIVE.get());
        synchronized (StartupOrderingHooks.class) {
            new Exception("Root mutation stack").printStackTrace(trace);
            trace.flush();
        }
        if (text.contains(" / corgilib:")) CORGI_WRITES.merge(text, 1, Integer::sum);
        return mutation;
    }
    static final class Mutation implements AutoCloseable {
        private final AtomicBoolean overlapped = new AtomicBoolean();
        void overlap() { if (overlapped.compareAndSet(false, true)) OVERLAPS.incrementAndGet(); }
        @Override public void close() {
            if (ACTIVE.get() != 0) overlap();
            WRITING.remove(this);
        }
    }
    public static void scriptsLoaded(Object manager) {
        try {
            ClassLoader loader = manager.getClass().getClassLoader();
            Class<?> builtin = Class.forName("net.minecraft.core.registries.BuiltInRegistries", false, loader);
            Object root = builtin.getField("f_257047_").get(null);
            Class<?> registry = Class.forName("net.minecraft.core.Registry", false, loader);
            List<String> contents = new ArrayList<>();
            for (Object value : (Iterable<?>) root) {
                String key = registry.getMethod("m_123023_").invoke(value).toString();
                List<String> entries = new ArrayList<>();
                for (Object entry : (Set<?>) registry.getMethod("m_6566_").invoke(value)) entries.add(entry.toString());
                Collections.sort(entries);
                contents.add(key + "=" + entries);
            }
            Collections.sort(contents);
            int number = SNAPSHOTS.incrementAndGet();
            Files.write(Path.of("registry-snapshot-" + number + ".txt"), contents);
            event("SCRIPTS_LOADED snapshot=" + number);
        } catch (Throwable e) { invalid("Registry snapshot failed", e); }
    }
    public static CompletableFuture<?> observeReload(CompletableFuture<?> future) {
        return future.whenComplete((result, failure) -> {
            if (failure != null) invalid("Resource reload failed", failure);
            reloadComplete = true;
            event("RELOAD_COMPLETE");
            System.out.println("COMPAT_RELOAD_COMPLETE");
        });
    }
    private static void finish() {
        Set<String> expected = Set.of(
                "ResourceKey[minecraft:root / corgilib:blending_function]",
                "ResourceKey[minecraft:root / corgilib:villager_trades_item_listing]",
                "ResourceKey[minecraft:root / corgilib:condition]");
        boolean valid = CORGI_WRITES.keySet().equals(expected)
                && CORGI_WRITES.values().stream().allMatch(n -> n == 1)
                && CONSTRUCTOR_DONE.getCount() == 0 && ENUMERATIONS.get() > 0 && ACTIVE.get() == 0 && WRITING.isEmpty();
        valid &= MODE.equals("control") ? cme && OVERLAPS.get() == 3
                : !cme && OVERLAPS.get() == 0 && ENUMERATIONS.get() >= 3 && SNAPSHOTS.get() >= 3 && reloadComplete;
        if (!MODE.equals("control") && !MODE.equals("baseline")) valid &= HOOK_RETURNED.getCount() == 0;
        event((valid ? "PASS " : "INVALID ") + MODE + " corgi=" + CORGI_WRITES
                + " enumerations=" + ENUMERATIONS + " overlap=" + OVERLAPS);
        if (!valid) Runtime.getRuntime().halt(86);
    }
}
