package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Arrays;

/**
 * X509CrlData - X.509 CRL (Certificate Revocation List) 데이터 Value Object
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 필수 필드 검증</li>
 *   <li>Value equality: 모든 필드 값으로 동등성 판단</li>
 * </ul>
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>X.509 CRL의 이진 데이터 (DER 인코딩)</li>
 *   <li>CRL의 다음 업데이트 시간</li>
 *   <li>포함된 폐기된 인증서 개수</li>
 * </ul>
 *
 * <p><b>LDIF 추출 예시</b>:</p>
 * <pre>{@code
 * // LDIF 파일에서:
 * certificateRevocationList;binary:: MIICrDCBlQIBATANBgkqhkiG9w0BAQUFADF...
 *
 * // Base64 디코딩 후:
 * byte[] crlBytes = Base64.getDecoder().decode(ldifData);
 * X509CrlData crlData = X509CrlData.of(crlBytes);
 * }</pre>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // CRL 데이터 저장
 * X509CrlData crlData = X509CrlData.of(crlBinaryData);
 *
 * // 폐기된 인증서 개수
 * int revokedCount = crlData.getRevokedCount();
 *
 * // CRL 이진 데이터
 * byte[] derEncoding = crlData.getCrlBinary();
 * }</pre>
 *
 * @see CertificateRevocationList
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Embeddable
@Getter
@EqualsAndHashCode
public class X509CrlData implements ValueObject {

    /**
     * X.509 CRL 이진 데이터 (DER 인코딩)
     *
     * <p>X.509 CRL의 완전한 DER-encoded 바이너리 데이터입니다.
     * LDIF 파일에서 certificateRevocationList;binary 필드로부터 추출된 Base64 데이터를 디코딩합니다.</p>
     * <p>최대 크기: 약 10MB</p>
     */
    @Lob
    @Column(name = "crl_binary", nullable = false, columnDefinition = "BYTEA")
    private byte[] crlBinary;

    /**
     * 폐기된 인증서 개수
     *
     * <p>CRL에 포함된 폐기된 인증서의 개수입니다.
     * 통계 및 성능 최적화 목적으로 저장됩니다.</p>
     */
    @Column(name = "revoked_count", nullable = false, columnDefinition = "INT DEFAULT 0")
    private int revokedCount;

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected X509CrlData() {
    }

    /**
     * X509CrlData 생성 (Static Factory Method)
     *
     * @param crlBinary DER-encoded CRL 바이너리 데이터
     * @return X509CrlData
     * @throws DomainException CRL 데이터가 null이거나 비어있는 경우
     */
    public static X509CrlData of(byte[] crlBinary) {
        if (crlBinary == null || crlBinary.length == 0) {
            throw new DomainException(
                "INVALID_CRL_DATA",
                "CRL binary data cannot be null or empty"
            );
        }

        X509CrlData crlData = new X509CrlData();
        crlData.crlBinary = Arrays.copyOf(crlBinary, crlBinary.length);
        crlData.revokedCount = 0;  // TODO: 실제 CRL 파싱 후 설정
        return crlData;
    }

    /**
     * X509CrlData 생성 (폐기 개수 포함)
     *
     * @param crlBinary DER-encoded CRL 바이너리 데이터
     * @param revokedCount 폐기된 인증서 개수
     * @return X509CrlData
     * @throws DomainException CRL 데이터가 null이거나 revokedCount가 음수인 경우
     */
    public static X509CrlData of(byte[] crlBinary, int revokedCount) {
        if (crlBinary == null || crlBinary.length == 0) {
            throw new DomainException(
                "INVALID_CRL_DATA",
                "CRL binary data cannot be null or empty"
            );
        }

        if (revokedCount < 0) {
            throw new DomainException(
                "INVALID_REVOKED_COUNT",
                "Revoked count cannot be negative. Got: " + revokedCount
            );
        }

        X509CrlData crlData = new X509CrlData();
        crlData.crlBinary = Arrays.copyOf(crlBinary, crlBinary.length);
        crlData.revokedCount = revokedCount;
        return crlData;
    }

    /**
     * CRL 바이너리 데이터 (복사본)
     *
     * @return DER-encoded CRL 바이너리 데이터의 복사본
     */
    public byte[] getCrlBinary() {
        return crlBinary != null ? Arrays.copyOf(crlBinary, crlBinary.length) : null;
    }

    /**
     * CRL 데이터 크기
     *
     * @return 바이트 단위 크기
     */
    public int getSize() {
        return crlBinary != null ? crlBinary.length : 0;
    }

    /**
     * 비어있는지 확인
     *
     * @return CRL 데이터가 없으면 true
     */
    public boolean isEmpty() {
        return crlBinary == null || crlBinary.length == 0;
    }

    /**
     * 폐기된 인증서가 있는지 확인
     *
     * @return revokedCount > 0이면 true
     */
    public boolean hasRevokedCertificates() {
        return revokedCount > 0;
    }

    /**
     * 문자열 표현
     *
     * @return CRL 정보 문자열
     */
    @Override
    public String toString() {
        return String.format("X509CrlData[size=%d bytes, revokedCount=%d]", getSize(), revokedCount);
    }
}
