package com.polaris.bpm.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.bpm.annotation.Idempotent;
import com.polaris.bpm.mapper.BpmIdempotencyRecordMapper;
import com.polaris.bpm.model.entity.BpmIdempotencyRecord;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Database-backed idempotent replay of command results. */
@Aspect
@Component
@Order(10)
public class IdempotencyAspect {
    private final BpmIdempotencyRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    public IdempotencyAspect(BpmIdempotencyRecordMapper recordMapper, ObjectMapper objectMapper) {
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(com.polaris.bpm.annotation.Idempotent)")
    public Object replay(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Idempotent annotation = signature.getMethod().getAnnotation(Idempotent.class);
        String requestKey = key(annotation, signature, point.getArgs());
        BpmIdempotencyRecord existing = recordMapper.selectOne(new LambdaQueryWrapper<BpmIdempotencyRecord>()
                .eq(BpmIdempotencyRecord::getRequestKey, requestKey));
        if (existing != null && "SUCCESS".equals(existing.getStatus()) && existing.getResponseJson() != null) {
            return objectMapper.readValue(existing.getResponseJson(), Object.class);
        }

        Object result = point.proceed();
        BpmIdempotencyRecord record = new BpmIdempotencyRecord();
        record.setRequestKey(requestKey);
        record.setOperationName(signature.toShortString());
        record.setResponseJson(objectMapper.writeValueAsString(result));
        record.setStatus("SUCCESS");
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException duplicate) {
            // Another request won the race; the successful business transaction remains authoritative.
        }
        return result;
    }

    private String key(Idempotent annotation, MethodSignature signature, Object[] args) {
        String source = annotation.key().isBlank()
                ? signature.toShortString() + "|" + java.util.Arrays.deepToString(args)
                : annotation.key() + "|" + java.util.Arrays.deepToString(args);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成幂等请求键", ex);
        }
    }
}
