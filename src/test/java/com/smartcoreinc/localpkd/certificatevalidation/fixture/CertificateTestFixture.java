package com.smartcoreinc.localpkd.certificatevalidation.fixture;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * CertificateTestFixture - 인증서 도메인 객체 테스트 픽스처
 *
 * <p><b>목적</b>: 테스트에서 필요한 Certificate 및 관련 객체를 쉽게 생성</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // 여러 인증서 생성 (100개 valid, 20개 invalid)
 * List<Certificate> certificates = CertificateTestFixture.buildList(
 *     100, true,   // 100개의 valid 인증서
 *     20, false    // 20개의 invalid 인증서
 * );
 *
 * // 단일 인증서 생성 (valid)
 * Certificate cert = CertificateTestFixture.createValid();
 *
 * // 단일 인증서 생성 (invalid)
 * Certificate cert = CertificateTestFixture.createInvalid();
 * </pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CertificateTestFixture {

    /**
     * Mock Certificate 객체 생성
     *
     * @param isValid 인증서 유효 여부
     * @return Mock 된 Certificate 객체 (모든 메서드 설정됨)
     */
    private static Certificate createMock(boolean isValid) {
        UUID certId = UUID.randomUUID();

        // Mock 객체 생성
        Certificate cert = mock(Certificate.class);

        when(cert.getId()).thenReturn(new CertificateId(certId));
        when(cert.isValid()).thenReturn(isValid);
        when(cert.isExpired()).thenReturn(!isValid);
        when(cert.isCurrentlyValid()).thenReturn(isValid);
        when(cert.getStatus()).thenReturn(isValid ? CertificateStatus.VALID : CertificateStatus.INVALID);
        when(cert.getCertificateType()).thenReturn(CertificateType.DSC);
        when(cert.getCreatedAt()).thenReturn(LocalDateTime.now());

        // SubjectInfo 및 IssuerInfo mock
        SubjectInfo subjectInfo = mock(SubjectInfo.class);
        when(subjectInfo.getDistinguishedName()).thenReturn("CN=Test-" + certId);
        when(cert.getSubjectInfo()).thenReturn(subjectInfo);

        IssuerInfo issuerInfo = mock(IssuerInfo.class);
        when(issuerInfo.getDistinguishedName()).thenReturn("CN=Test Issuer");
        when(cert.getIssuerInfo()).thenReturn(issuerInfo);

        return cert;
    }

    /**
     * 여러 Certificate 객체를 한 번에 생성
     *
     * <p>예: buildList(100, true, 20, false)
     * - 100개의 valid 인증서 + 20개의 invalid 인증서
     *
     * @param counts 인증서 개수와 valid 여부를 번갈아가며 지정
     *               예: (100, true, 20, false) → 100개 valid + 20개 invalid
     * @return Certificate 목록
     */
    public static List<Certificate> buildList(Object... counts) {
        List<Certificate> certificates = new ArrayList<>();

        for (int i = 0; i < counts.length; i += 2) {
            int count = (int) counts[i];
            boolean isValid = (boolean) counts[i + 1];

            for (int j = 0; j < count; j++) {
                certificates.add(createMock(isValid));
            }
        }

        return certificates;
    }

    /**
     * 유효한 테스트용 Certificate 객체 생성
     *
     * @return Mock 된 유효한 Certificate 객체
     */
    public static Certificate createValid() {
        return createMock(true);
    }

    /**
     * 무효한 테스트용 Certificate 객체 생성
     *
     * @return Mock 된 무효한 Certificate 객체
     */
    public static Certificate createInvalid() {
        return createMock(false);
    }

    /**
     * 테스트용 Certificate 배열 생성
     *
     * @param count 생성할 인증서 개수
     * @param isValid 모든 인증서의 유효 여부
     * @return Certificate 배열
     */
    public static List<Certificate> createList(int count, boolean isValid) {
        List<Certificate> certificates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            certificates.add(createMock(isValid));
        }
        return certificates;
    }
}
