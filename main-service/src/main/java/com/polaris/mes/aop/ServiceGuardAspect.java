package com.polaris.mes.aop;

import com.polaris.mes.annotation.RequireRole;
import com.polaris.mes.common.RequestContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class ServiceGuardAspect {
    @Around("@annotation(com.polaris.mes.annotation.RequireRole) || @within(com.polaris.mes.annotation.RequireRole)")
    public Object checkRole(ProceedingJoinPoint point) throws Throwable {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        RequireRole annotation = method.getAnnotation(RequireRole.class);
        if (annotation == null) annotation = point.getTarget().getClass().getAnnotation(RequireRole.class);
        if (annotation != null) RequestContext.requireRole(annotation.value());
        return point.proceed();
    }
}
