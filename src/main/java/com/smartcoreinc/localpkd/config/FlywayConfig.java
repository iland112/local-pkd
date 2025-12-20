package com.smartcoreinc.localpkd.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import lombok.extern.slf4j.Slf4j;

/**
 * Flyway Database Migration 설정
 */
@Slf4j
@Configuration
public class FlywayConfig {
    
    /**
     * 개발 환경용 Flyway 전략
     * 실패 시 repair 후 재시도
     */
    @Bean
    @Profile("dev")
    public FlywayMigrationStrategy flywayDevStrategy() {
        return flyway -> {
            log.info("=== Flyway Migration Started (Development Mode) ===");
            try {
                flyway.migrate();
                log.info("=== Flyway Migration Completed Successfully ===");
            } catch (Exception e) {
                log.error("Flyway migration failed, attemping repair...", e);
                try {
                    flyway.repair();
                    log.info("Flyway repair completed, retrying migration...");
                    flyway.migrate();
                    log.info("=== Flyway Migration Completed After Repair ===");
                } catch (Exception repairException) {
                    log.error("Flyway repair and migration failed", repairException);
                    throw repairException;
                }
            }
        };
    }

    /**
     * 운영 환경용 Flyway 전략
     * 엄격한 검증
     */
    @Bean
    @Profile("prod")
    public FlywayMigrationStrategy flywayProdStrategy() {
        return flyway -> {
            log.info("=== Flyway Migration Started (Production Mode) ===");

            // 마이그레이션 전 검증
            try {
                flyway.validate();
                log.info("Flyway validation passed");
            } catch (Exception e) {
                log.error("Flyway validation failed", e);
                throw new IllegalStateException("Cannot proceed with migration - validation failed", e);
            }

            // 마이그레이션 실행
            try {
                flyway.migrate();
                log.info("=== Flyway Migration Completed Successfully ===");
            } catch (Exception e) {
                log.error("Flyway migration failed in production", e);
                throw e;
            }
        };
    }

    /**
     * 기본 Flyway 전략 (로컬 및 테스트)
     */
    @Bean
    @Profile({"local", "test"})
    public FlywayMigrationStrategy flywayDefaultStrategy() {
        return flyway -> {
            log.info("=== Flyway Migration Started (Default Mode) ===");

            // 기존 스키마가 있는 경우 baseline 설정
            if (hasExistingSchema(flyway)) {
                log.info("Existing schema detected, setting baseline...");
                flyway.baseline();
            }

            flyway.migrate();
            log.info("=== Flyway Migration Completed Successfully ===");
        };
    }

    /**
     * 기존 스키마 존재 여부 확인
     */
    private boolean hasExistingSchema(Flyway flyway) {
        try {
            flyway.info();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Bean
    public Callback flywayCallback() {
        return new Callback() {

            @Override
            public boolean supports(Event event, Context context) {
                return event == Event.AFTER_MIGRATE;
            }

            @Override
            public boolean canHandleInTransaction(Event event, Context context) {
                return true;
            }

            @Override
            public void handle(Event event, Context context) {
                log.info("=== Flyway Migration Callback ===");
                log.info("Applied migration count: {}", context.getMigrationInfo() != null ? 1 : 0);

                // 마이그레이션 후 추가 작업이 필요한 경우 여기에 구현
                // 예: 초기 데이터 로드, 캐시 초기화 등
            }

            @Override
            public String getCallbackName() {
                return "PKD Migration Callback";
            }
        };
    }
}
