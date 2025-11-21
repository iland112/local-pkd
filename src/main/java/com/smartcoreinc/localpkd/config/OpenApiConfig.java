package com.smartcoreinc.localpkd.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger Configuration
 *
 * <p>REST API 문서화를 위한 Swagger UI 설정</p>
 *
 * <h3>Swagger UI 접속</h3>
 * <ul>
 *   <li>Swagger UI: <a href="http://localhost:8081/swagger-ui.html">http://localhost:8081/swagger-ui.html</a></li>
 *   <li>API Docs (JSON): <a href="http://localhost:8081/v3/api-docs">http://localhost:8081/v3/api-docs</a></li>
 *   <li>API Docs (YAML): <a href="http://localhost:8081/v3/api-docs.yaml">http://localhost:8081/v3/api-docs.yaml</a></li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-19
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8081}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Local PKD Evaluation API")
                        .description("""
                                # ICAO PKD Local Evaluation System API

                                ## 개요
                                ICAO Public Key Directory (PKD) 로컬 평가 및 관리 시스템의 REST API 문서입니다.

                                ## 주요 기능
                                - **파일 업로드**: LDIF 및 Master List 파일 업로드 (자동/수동 모드)
                                - **파싱**: 파일 파싱 및 인증서/CRL 추출
                                - **검증**: 인증서 신뢰 체인 검증
                                - **LDAP 연동**: OpenLDAP 서버 연동 (PostgreSQL + OpenLDAP 이중 저장)
                                - **이력 조회**: 업로드 이력 검색 및 필터링

                                ## 외부 시스템
                                - **PostgreSQL**: 데이터 저장 및 분석
                                - **OpenLDAP**: 192.168.100.10:389 (LDAP 디렉토리 서비스)

                                ## 파일 형식
                                - **LDIF**: ICAO PKD LDIF 파일 (.ldif)
                                  - CSCA Complete/Delta LDIF
                                  - eMRTD Complete/Delta LDIF
                                - **Master List**: CMS Signed Master List 파일 (.ml)

                                ## 처리 모드
                                - **AUTO**: 파일 업로드 후 자동 파싱 → 검증 → LDAP 등록
                                - **MANUAL**: 각 단계를 사용자가 수동으로 트리거

                                ## 중복 검사
                                파일 업로드 전 SHA-256 해시 기반 중복 검사 지원
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SmartCore Inc.")
                                .email("support@smartcoreinc.com")
                                .url("https://www.smartcoreinc.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://www.smartcoreinc.com/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("http://192.168.100.11:" + serverPort)
                                .description("Internal LAN Server")
                ));
    }
}
