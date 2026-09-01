package com.polaris.bpm.common;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestContext {
    private RequestContext() {}

    public static String actor(HttpServletRequest request) {
        return actor(request, "admin");
    }

    public static String actor(HttpServletRequest request, String defaultActor) {
        String actor = request.getHeader("X-User");
        return actor == null || actor.isBlank() ? defaultActor : actor;
    }
}
