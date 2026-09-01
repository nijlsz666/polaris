package com.polaris.bpm.config;

import com.polaris.bpm.common.ActorContext;
import com.polaris.bpm.common.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;

/** Copies the authenticated actor into a request-local context for AOP and audit. */
@Component
public class BpmRequestInterceptor implements HandlerInterceptor {
    private final String defaultActor;

    public BpmRequestInterceptor(@Value("${polaris.bpm.default-actor:admin}") String defaultActor) {
        this.defaultActor = defaultActor;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        ActorContext.set(RequestContext.actor(request, defaultActor));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ActorContext.clear();
    }
}
