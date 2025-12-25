package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RevokedCertificates - CRL에 포함된 폐기된 인증서 일련번호 집합 Value Object
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 일련번호 검증</li>
 *   <li>Value equality: 포함된 일련번호 집합으로 동등성 판단</li>
 * </ul>
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>CRL에 포함된 폐기된 인증서 일련번호 관리</li>
 *   <li>폐기 여부 빠른 조회 (HashSet)</li>
 *   <li>일련번호 형식 검증</li>
 * </ul>
 *
 * <p><b>일련번호 형식</b>:</p>
 * <ul>
 *   <li>16진수 문자열 (0-9, A-F)</li>
 *   <li>예: "01234567890ABCDEF"</li>
 *   <li>대소문자 구분 없음 (대문자로 정규화)</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // CRL 파싱 후 생성
 * Set<String> revokedSerialNumbers = new HashSet<>();
 * revokedSerialNumbers.add("01234567890ABCDEF");
 * revokedSerialNumbers.add("FEDCBA0987654321");
 *
 * RevokedCertificates revoked = RevokedCertificates.of(revokedSerialNumbers);
 *
 * // 폐기 여부 확인
 * boolean isRevoked = revoked.contains("01234567890ABCDEF");  // true
 * int count = revoked.calculateCount();  // 2
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
public class RevokedCertificates implements ValueObject, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 폐기된 인증서 일련번호 집합 (세미콜론 구분)
     *
     * <p>데이터베이스 저장을 위해 세미콜론으로 구분된 문자열로 저장됩니다.
     * 예: "01234567890ABCDEF;FEDCBA0987654321"</p>
     */
    @Column(name = "revoked_serial_numbers", columnDefinition = "TEXT", nullable = false)
    private String serialNumbers;

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected RevokedCertificates() {
    }

    /**
     * RevokedCertificates 생성 (Static Factory Method)
     *
     * @param serialNumbers 폐기된 인증서 일련번호 집합
     * @return RevokedCertificates
     * @throws DomainException 입력이 null이거나 빈 집합인 경우
     */
    public static RevokedCertificates of(Set<String> serialNumbers) {
        if (serialNumbers == null || serialNumbers.isEmpty()) {
            throw new DomainException(
                "INVALID_REVOKED_CERTIFICATES",
                "Revoked certificates cannot be null or empty"
            );
        }

        // 일련번호 검증
        for (String serialNumber : serialNumbers) {
            validateSerialNumber(serialNumber);
        }

        // 대문자로 정규화 및 정렬
        String normalized = serialNumbers.stream()
            .map(String::toUpperCase)
            .sorted()
            .collect(Collectors.joining(";"));

        RevokedCertificates revoked = new RevokedCertificates();
        revoked.serialNumbers = normalized;
        return revoked;
    }

    /**
     * 빈 RevokedCertificates 생성 (CRL에 폐기된 인증서가 없는 경우)
     *
     * @return 빈 RevokedCertificates
     */
    public static RevokedCertificates empty() {
        RevokedCertificates revoked = new RevokedCertificates();
        revoked.serialNumbers = "";
        return revoked;
    }

    /**
     * 일련번호 검증
     *
     * @param serialNumber 16진수 일련번호
     * @throws DomainException 형식이 유효하지 않은 경우
     */
    private static void validateSerialNumber(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new DomainException(
                "INVALID_SERIAL_NUMBER",
                "Serial number cannot be null or blank"
            );
        }

        // 16진수 형식 검증 (대소문자 모두 허용)
        if (!serialNumber.toUpperCase().matches("^[0-9A-F]+$")) {
            throw new DomainException(
                "INVALID_SERIAL_NUMBER_FORMAT",
                "Serial number must be hexadecimal string. Got: " + serialNumber
            );
        }
    }

    /**
     * 특정 일련번호가 폐기 목록에 있는지 확인
     *
     * @param serialNumber 확인할 인증서 일련번호
     * @return 폐기되었으면 true
     */
    public boolean contains(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return false;
        }

        if (serialNumbers == null || serialNumbers.isEmpty()) {
            return false;
        }

        String normalized = serialNumber.toUpperCase();
        String[] numbers = serialNumbers.split(";");

        for (String number : numbers) {
            if (number.equals(normalized)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 폐기된 인증서 개수
     *
     * <p>Note: 메서드명을 'getCount' 대신 'calculateCount'로 사용하여
     * Hibernate가 JavaBeans 프로퍼티로 인식하지 않도록 함</p>
     *
     * @return 폐기된 인증서 수
     */
    public int calculateCount() {
        if (serialNumbers == null || serialNumbers.isEmpty()) {
            return 0;
        }
        return serialNumbers.split(";").length;
    }

    /**
     * 폐기된 인증서 일련번호 집합 (읽기 전용)
     *
     * @return 불변 일련번호 집합
     */
    public Set<String> getSerialNumbers() {
        if (serialNumbers == null || serialNumbers.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        for (String number : serialNumbers.split(";")) {
            if (!number.isEmpty()) {
                result.add(number);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * 비어있는지 확인
     *
     * @return 폐기된 인증서가 없으면 true
     */
    public boolean isEmpty() {
        return serialNumbers == null || serialNumbers.isEmpty();
    }

    /**
     * 문자열 표현
     *
     * @return 폐기 정보 문자열
     */
    @Override
    public String toString() {
        return String.format("RevokedCertificates[count=%d]", calculateCount());
    }
}
