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
 * CrlTestFixture - CRL(인증서 폐기 목록) 도메인 객체 테스트 픽스처
 *
 * <p><b>목적</b>: 테스트에서 필요한 CertificateRevocationList 객체를 쉽게 생성</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // 여러 CRL 생성 (50개 valid, 10개 invalid)
 * List<CertificateRevocationList> crls = CrlTestFixture.buildList(
 *     50, true,    // 50개의 valid CRL
 *     10, false    // 10개의 invalid CRL
 * );
 *
 * // 단일 CRL 생성 (valid)
 * CertificateRevocationList crl = CrlTestFixture.createValid();
 * </pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CrlTestFixture {

    /**
     * Mock CertificateRevocationList 객체 생성
     *
     * @param isValid CRL 유효 여부
     * @return Mock 된 CertificateRevocationList 객체
     */
    private static CertificateRevocationList createMock(boolean isValid) {
        UUID crlId = UUID.randomUUID();

        // Mock 객체 생성
        CertificateRevocationList crl = mock(CertificateRevocationList.class);

        when(crl.getId()).thenReturn(new CrlId(crlId));
        when(crl.isValid()).thenReturn(isValid);
        when(crl.isExpired()).thenReturn(!isValid);
        when(crl.isNotYetValid()).thenReturn(false);
        when(crl.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(crl.getUpdatedAt()).thenReturn(LocalDateTime.now());

        // IssuerName mock
        IssuerName issuerName = mock(IssuerName.class);
        when(issuerName.getValue()).thenReturn("CN=Test CRL Issuer");
        when(crl.getIssuerName()).thenReturn(issuerName);

        return crl;
    }

    /**
     * 여러 CertificateRevocationList 객체를 한 번에 생성
     *
     * <p>예: buildList(50, true, 10, false)
     * - 50개의 valid CRL + 10개의 invalid CRL
     *
     * @param counts CRL 개수와 valid 여부를 번갈아가며 지정
     * @return CertificateRevocationList 목록
     */
    public static List<CertificateRevocationList> buildList(Object... counts) {
        List<CertificateRevocationList> crls = new ArrayList<>();

        for (int i = 0; i < counts.length; i += 2) {
            int count = (int) counts[i];
            boolean isValid = (boolean) counts[i + 1];

            for (int j = 0; j < count; j++) {
                crls.add(createMock(isValid));
            }
        }

        return crls;
    }

    /**
     * 유효한 테스트용 CRL 객체 생성
     *
     * @return Mock 된 유효한 CertificateRevocationList 객체
     */
    public static CertificateRevocationList createValid() {
        return createMock(true);
    }

    /**
     * 무효한 테스트용 CRL 객체 생성
     *
     * @return Mock 된 무효한 CertificateRevocationList 객체
     */
    public static CertificateRevocationList createInvalid() {
        return createMock(false);
    }

    /**
     * 테스트용 CRL 배열 생성
     *
     * @param count 생성할 CRL 개수
     * @param isValid 모든 CRL의 유효 여부
     * @return CertificateRevocationList 배열
     */
    public static List<CertificateRevocationList> createList(int count, boolean isValid) {
        List<CertificateRevocationList> crls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            crls.add(createMock(isValid));
        }
        return crls;
    }
}
