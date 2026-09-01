package com.polaris.mes.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
public class FlowableSchemaConfiguration {
    @Bean
    ProcessEngineConfigurationConfigurer flowableSchemaStrategy(JdbcTemplate jdbc) {
        String catalog = currentCatalog(jdbc);
        return configuration -> {
            // Without an explicit catalog, MySQL metadata lookup can see ACT_*
            // tables from unrelated databases on the same server.
            configuration.setDatabaseCatalog(catalog);
            configuration.setDatabaseSchemaUpdate(hasFlowableTables(jdbc) ? "true" : "create");
        };
    }

    private String currentCatalog(JdbcTemplate jdbc) {
        DataSource dataSource = jdbc.getDataSource();
        if (dataSource == null) throw new IllegalStateException("数据源未初始化，无法检查 Flowable 表");
        try (Connection connection = dataSource.getConnection()) {
            return connection.getCatalog();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取数据库目录", ex);
        }
    }

    private boolean hasFlowableTables(JdbcTemplate jdbc) {
        DataSource dataSource = jdbc.getDataSource();
        if (dataSource == null) throw new IllegalStateException("数据源未初始化，无法检查 Flowable 表");
        String catalog;
        try (Connection connection = dataSource.getConnection()) {
            catalog = connection.getCatalog();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法检查 Flowable 表", ex);
        }
        Integer count = jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=? and upper(table_name) like 'ACT_%'", Integer.class, catalog);
        return count != null && count > 0;
    }
}
