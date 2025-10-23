package com.smartcoreinc.localpkd.fileupload.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CollectionNumber - Collection 번호 Value Object
 *
 * <p>ICAO PKD Collection 번호를 나타내는 도메인 객체입니다.
 * 3자리 숫자 형식(001, 002, 003)으로 구성됩니다.</p>
 *
 * <h3>Collection 번호 규칙</h3>
 * <ul>
 *   <li>001 - CSCA (Country Signing Certificate Authority)</li>
 *   <li>002 - eMRTD (electronic Machine Readable Travel Documents)</li>
 *   <li>003 - (향후 확장)</li>
 * </ul>
 *
 * <h3>검증 규칙</h3>
 * <ul>
 *   <li>정확히 3자리 숫자여야 함</li>
 *   <li>001, 002, 003 등의 형식</li>
 *   <li>null이나 빈 문자열 불가</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 직접 생성
 * CollectionNumber collection = CollectionNumber.of("002");
 *
 * // 파일명에서 추출
 * FileName fileName = FileName.of("icaopkd-002-complete-009410.ldif");
 * CollectionNumber collection = CollectionNumber.extractFromFileName(fileName);
 *
 * // 비즈니스 로직
 * boolean isCsca = collection.isCsca();      // false
 * boolean isEmrtd = collection.isEmrtd();    // true
 * String value = collection.getValue();      // "002"
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionNumber {

    /**
     * Collection 번호 (3자리 숫자 문자열)
     */
    private String value;

    /**
     * Collection 001 상수 (CSCA)
     */
    public static final CollectionNumber CSCA = new CollectionNumber("001");

    /**
     * Collection 002 상수 (eMRTD)
     */
    public static final CollectionNumber EMRTD = new CollectionNumber("002");

    /**
     * 파일명 패턴: icaopkd-{collection}-...
     */
    private static final Pattern FILENAME_PATTERN = Pattern.compile("icaopkd-(\\d{3})-", Pattern.CASE_INSENSITIVE);

    /**
     * Private 생성자 - 정적 팩토리 메서드를 통해서만 생성
     *
     * @param value Collection 번호
     */
    private CollectionNumber(String value) {
        validate(value);
        this.value = value;
    }

    /**
     * 검증 로직
     *
     * @param value 검증할 값
     * @throws DomainException 검증 실패 시
     */
    private void validate(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new DomainException(
                    "INVALID_COLLECTION_NUMBER",
                    "Collection number cannot be null or empty"
            );
        }

        if (!value.matches("^\\d{3}$")) {
            throw new DomainException(
                    "INVALID_COLLECTION_NUMBER",
                    String.format("Collection number must be exactly 3 digits (e.g., 001, 002), but got: %s", value)
            );
        }

        // 추가 검증: 001~999 범위 확인
        int number = Integer.parseInt(value);
        if (number < 1 || number > 999) {
            throw new DomainException(
                    "INVALID_COLLECTION_NUMBER",
                    String.format("Collection number must be between 001 and 999, but got: %s", value)
            );
        }
    }

    /**
     * CollectionNumber 생성 (정적 팩토리 메서드)
     *
     * @param value Collection 번호 (3자리 숫자 문자열)
     * @return CollectionNumber 인스턴스
     * @throws DomainException 검증 실패 시
     */
    public static CollectionNumber of(String value) {
        return new CollectionNumber(value);
    }

    /**
     * 파일명으로부터 Collection 번호 추출
     *
     * <p>파일명 패턴을 분석하여 Collection 번호를 추출합니다.
     * 패턴: icaopkd-{collection}-{type}-{version}.ldif</p>
     *
     * <h4>추출 예시</h4>
     * <ul>
     *   <li>"icaopkd-001-complete-009410.ldif" → "001"</li>
     *   <li>"icaopkd-002-delta-009411.ldif" → "002"</li>
     *   <li>"masterlist-July2025.ml" → "002" (기본값)</li>
     * </ul>
     *
     * @param fileName 파일명 Value Object
     * @return 추출된 CollectionNumber
     * @throws DomainException 파일명이 null인 경우
     */
    public static CollectionNumber extractFromFileName(FileName fileName) {
        if (fileName == null) {
            throw new DomainException(
                    "INVALID_FILE_NAME",
                    "FileName cannot be null for collection number extraction"
            );
        }

        Matcher matcher = FILENAME_PATTERN.matcher(fileName.getValue());

        if (matcher.find()) {
            String collectionStr = matcher.group(1);
            return new CollectionNumber(collectionStr);
        }

        // 기본값: Master List는 Collection 002
        // LDIF 파일이면서 패턴이 없으면 001로 간주
        if (fileName.getValue().toLowerCase().endsWith(".ml")) {
            return EMRTD;  // Collection 002
        } else {
            return CSCA;   // Collection 001 (기본값)
        }
    }

    /**
     * FileFormat으로부터 기본 Collection 번호 반환
     *
     * @param fileFormat 파일 포맷
     * @return 기본 CollectionNumber
     * @throws DomainException FileFormat이 null인 경우
     */
    public static CollectionNumber fromFileFormat(FileFormat fileFormat) {
        if (fileFormat == null) {
            throw new DomainException(
                    "INVALID_FILE_FORMAT",
                    "FileFormat cannot be null"
            );
        }

        String defaultCollection = fileFormat.getDefaultCollectionNumber();
        return new CollectionNumber(defaultCollection);
    }

    // ===== 비즈니스 메서드 =====

    /**
     * CSCA Collection 여부 확인
     *
     * @return Collection 001이면 true
     */
    public boolean isCsca() {
        return "001".equals(value);
    }

    /**
     * eMRTD Collection 여부 확인
     *
     * @return Collection 002이면 true
     */
    public boolean isEmrtd() {
        return "002".equals(value);
    }

    /**
     * 숫자 값 반환
     *
     * @return Collection 번호의 정수 값 (1, 2, 3, ...)
     */
    public int toInt() {
        return Integer.parseInt(value);
    }

    /**
     * 문자열 표현
     *
     * @return "CollectionNumber[value=002]"
     */
    @Override
    public String toString() {
        return String.format("CollectionNumber[value=%s]", value);
    }

    // ===== JPA Persistence 지원 =====

    /**
     * JPA Persistence용 문자열 변환
     *
     * @return Collection 번호 문자열
     */
    public String toStorageValue() {
        return value;
    }

    /**
     * JPA Persistence용 문자열로부터 복원
     *
     * @param value Collection 번호 문자열
     * @return CollectionNumber 인스턴스
     */
    public static CollectionNumber fromStorageValue(String value) {
        return new CollectionNumber(value);
    }
}
