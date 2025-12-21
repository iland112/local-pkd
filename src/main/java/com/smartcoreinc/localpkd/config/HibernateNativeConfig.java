package com.smartcoreinc.localpkd.config;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NativeDetector;

/**
 * Hibernate configuration for GraalVM Native Image compatibility.
 *
 * Forces the use of 'none' BytecodeProvider when running in native image mode,
 * since ByteBuddy is not compatible with GraalVM native images.
 */
@Configuration
public class HibernateNativeConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernateNativePropertiesCustomizer() {
        return hibernateProperties -> {
            // Always use 'none' BytecodeProvider for Native Image compatibility
            // This is required because ByteBuddy cannot generate classes at runtime in native images
            if (NativeDetector.inNativeImage()) {
                hibernateProperties.put(AvailableSettings.BYTECODE_PROVIDER, "none");
                hibernateProperties.put("hibernate.bytecode.use_reflection_optimizer", "false");
            }
        };
    }
}
