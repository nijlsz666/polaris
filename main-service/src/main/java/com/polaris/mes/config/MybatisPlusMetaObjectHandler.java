package com.polaris.mes.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.polaris.mes.common.TenantContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Defensive fill policy: tenant and audit timestamps are never trusted from a request body. */
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        TenantContext.Identity identity = TenantContext.current();
        if (identity != null) strictInsertFill(metaObject, "tenantId", Long.class, identity.tenantId());
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
