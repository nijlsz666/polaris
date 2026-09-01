package com.polaris.bpm.aop;

import com.polaris.bpm.annotation.AuditOperation;
import com.polaris.bpm.common.ActorContext;
import com.polaris.bpm.mapper.BpmAuditLogMapper;
import com.polaris.bpm.model.entity.BpmAuditLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** Durable audit trail for successful BPM commands. */
@Aspect
@Component
@Order(20)
public class AuditOperationAspect {
    private final BpmAuditLogMapper auditLogMapper;

    public AuditOperationAspect(BpmAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Around("@annotation(com.polaris.bpm.annotation.AuditOperation)")
    public Object record(ProceedingJoinPoint point) throws Throwable {
        Object result = point.proceed();
        MethodSignature signature = (MethodSignature) point.getSignature();
        AuditOperation operation = signature.getMethod().getAnnotation(AuditOperation.class);
        BpmAuditLog log = new BpmAuditLog();
        log.setActor(resolveActor(signature.getName(), point.getArgs()));
        log.setActionCode(operation.action());
        log.setResourceType(operation.resource());
        log.setOperation(signature.toShortString());
        log.setRequestSummary(summarize(point.getArgs()));
        log.setSuccess(Boolean.TRUE);
        auditLogMapper.insert(log);
        return result;
    }

    private String resolveActor(String methodName, Object[] args) {
        String contextualActor = ActorContext.get();
        if (contextualActor != null && !contextualActor.isBlank()) return contextualActor;
        if ("saveDesign".equals(methodName) && args.length > 2 && args[2] instanceof String actor) return actor;
        for (Object arg : args) if ("admin".equals(arg)) return "admin";
        for (int i = args.length - 1; i >= 0; i--) if (args[i] instanceof String value && !value.isBlank()) return value;
        return "system";
    }

    private String summarize(Object[] args) {
        String value = Arrays.deepToString(args);
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
