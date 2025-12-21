package com.smartcoreinc.localpkd.config;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCount;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.TypeCount;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.hibernate.bytecode.internal.none.BytecodeProviderImpl;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * GraalVM Native Image hints registrar for runtime reflection and resource access.
 *
 * This configuration registers necessary hints for:
 * - Bouncy Castle cryptographic provider classes
 * - LDAP SDK classes
 * - JPA/Hibernate entities
 * - Hibernate BytecodeProvider for Native Image
 * - Static resources (templates, CSS, JS)
 */
@Configuration
@ImportRuntimeHints(NativeImageHintsRegistrar.LocalPkdRuntimeHints.class)
public class NativeImageHintsRegistrar {

    static class LocalPkdRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // Bouncy Castle Provider
            hints.reflection().registerType(BouncyCastleProvider.class,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS);

            // Hibernate BytecodeProvider for Native Image
            hints.reflection().registerType(BytecodeProviderImpl.class,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS);

            // JPQL Projection classes (SELECT new ... queries)
            hints.reflection().registerType(TypeCount.class,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);
            hints.reflection().registerType(CountryCount.class,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);
            hints.reflection().registerType(CertificateType.class,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);

            // Register Flyway migration resources
            hints.resources().registerPattern("db/migration/*");

            // Register resource patterns
            hints.resources().registerPattern("templates/*");
            hints.resources().registerPattern("templates/**/*");
            hints.resources().registerPattern("static/*");
            hints.resources().registerPattern("static/**/*");
            hints.resources().registerPattern("application*.yml");
            hints.resources().registerPattern("application*.yaml");
            hints.resources().registerPattern("application*.properties");
        }
    }
}
