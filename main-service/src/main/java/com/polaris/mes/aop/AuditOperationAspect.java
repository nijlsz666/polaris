package com.polaris.mes.aop;

import com.polaris.mes.annotation.AuditOperation;
import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.AuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditOperationAspect {
    private final AuditService auditService;

    public AuditOperationAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(com.polaris.mes.annotation.AuditOperation)")
    public Object record(ProceedingJoinPoint point) throws Throwable {
        Object result = point.proceed();
        TenantContext.Identity identity = TenantContext.current();
        if (identity != null) {
            AuditOperation operation = ((MethodSignature) point.getSignature()).getMethod().getAnnotation(AuditOperation.class);
            auditService.record(identity.username(), operation.action(), operation.resource(), point.getSignature().toShortString());
        }
        return result;
    }
}
