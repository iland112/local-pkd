package com.smartcoreinc.localpkd.ldif.service.verification;

// 인증서 검증 전략 인터페이스
public interface CertificateVerificationStrategy<T, R> {
    R verify(T data, VerificationContext context);
    boolean supports(Class<?> dataType);
}
