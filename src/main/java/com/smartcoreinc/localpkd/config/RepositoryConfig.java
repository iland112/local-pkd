package com.smartcoreinc.localpkd.config;

import com.smartcoreinc.localpkd.fileparsing.infrastructure.repository.ParsedCertificateQueryRepository;
import com.smartcoreinc.localpkd.fileparsing.infrastructure.repository.impl.ParsedCertificateQueryRepositoryImpl;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RepositoryConfig {

    @Bean
    public ParsedCertificateQueryRepository parsedCertificateQueryRepository(EntityManager entityManager) {
        return new ParsedCertificateQueryRepositoryImpl(entityManager);
    }
}
