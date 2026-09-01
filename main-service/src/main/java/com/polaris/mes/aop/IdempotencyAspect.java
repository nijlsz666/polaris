package com.polaris.mes.aop;

import com.polaris.mes.annotation.Idempotent;
import com.polaris.mes.common.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Lightweight local idempotency guard; durable keys remain enforced by unique DB indexes. */
@Aspect
@Component
public class IdempotencyAspect {
    private final Map<String, Object> completed = new ConcurrentHashMap<>();

    @Around("@annotation(com.polaris.mes.annotation.Idempotent)")
    public Object guard(ProceedingJoinPoint point) throws Throwable {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        Idempotent annotation = method.getAnnotation(Idempotent.class);
        String requestKey = findKey(point.getArgs(), annotation.keyField());
        if (requestKey == null || requestKey.isBlank() || TenantContext.current() == null) return point.proceed();
        String key = TenantContext.require().tenantId() + ":" + point.getSignature().toShortString() + ":" + requestKey;
        Object cached = completed.get(key);
        if (cached != null) return cached;
        synchronized (completed) {
            cached = completed.get(key);
            if (cached != null) return cached;
            Object result = point.proceed();
            completed.put(key, result);
            return result;
        }
    }

    private String findKey(Object[] args, String field) {
        for (Object arg : args) {
            if (arg instanceof Map<?, ?> map && map.get(field) != null) return String.valueOf(map.get(field));
        }
        return null;
    }
}
