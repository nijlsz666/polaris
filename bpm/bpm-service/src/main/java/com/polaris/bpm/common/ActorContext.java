package com.polaris.bpm.common;

/** Request-scoped actor holder used by service-level cross-cutting concerns. */
public final class ActorContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ActorContext() { }

    public static void set(String actor) { CURRENT.set(actor); }
    public static String get() { return CURRENT.get(); }
    public static String getOrDefault(String fallback) { return get() == null ? fallback : get(); }
    public static void clear() { CURRENT.remove(); }
}
