package com.smartcoreinc.localpkd.fileparsing.fixture;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import com.smartcoreinc.localpkd.fileparsing.domain.model.*;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MasterListTestFixture - Master List 도메인 객체 테스트 픽스처
 *
 * <p><b>목적</b>: 테스트에서 필요한 MasterList 및 관련 객체를 쉽게 생성</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // 단일 Master List 생성 (한국)
 * MasterList ml = MasterListTestFixture.createKorea();
 *
 * // 단일 Master List 생성 (미국)
 * MasterList ml = MasterListTestFixture.createUsa();
 *
 * // 여러 Master List 생성
 * List<MasterList> masterLists = MasterListTestFixture.createMultiple(5);
 * </pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MasterListTestFixture {

    /**
     * 기본 Master List 생성 (한국, KOR)
     *
     * @return MasterList (Korea)
     */
    public static MasterList createKorea() {
        return createWithCountry(CountryCode.of("KR"), 10);
    }

    /**
     * 기본 Master List 생성 (미국, USA)
     *
     * @return MasterList (USA)
     */
    public static MasterList createUsa() {
        return createWithCountry(CountryCode.of("US"), 15);
    }

    /**
     * 기본 Master List 생성 (일본, JPN)
     *
     * @return MasterList (Japan)
     */
    public static MasterList createJapan() {
        return createWithCountry(CountryCode.of("JP"), 8);
    }

    /**
     * 특정 국가 코드로 Master List 생성
     *
     * @param countryCode 국가 코드
     * @param cscaCount CSCA 인증서 개수
     * @return MasterList
     */
    public static MasterList createWithCountry(CountryCode countryCode, int cscaCount) {
        return MasterList.create(
            MasterListId.newId(),
            UploadId.newId(),
            countryCode,
            MasterListVersion.of("1.0"),
            createCmsBinaryData(5000),
            createSignerInfo(countryCode),
            cscaCount
        );
    }

    /**
     * 특정 UploadId로 Master List 생성
     *
     * @param uploadId UploadId
     * @return MasterList (Korea)
     */
    public static MasterList createWithUploadId(UploadId uploadId) {
        return MasterList.create(
            MasterListId.newId(),
            uploadId,
            CountryCode.of("KR"),
            MasterListVersion.of("1.0"),
            createCmsBinaryData(5000),
            createSignerInfo(CountryCode.of("KR")),
            10
        );
    }

    /**
     * 여러 Master List 생성 (다양한 국가)
     *
     * @param count 생성할 Master List 개수
     * @return List of MasterList
     */
    public static List<MasterList> createMultiple(int count) {
        List<MasterList> masterLists = new ArrayList<>();
        String[] countries = {"KR", "US", "JP", "GB", "DE", "FR", "CN", "IN", "BR", "CA"};

        for (int i = 0; i < count; i++) {
            String countryCode = countries[i % countries.length];
            masterLists.add(createWithCountry(CountryCode.of(countryCode), 10 + i));
        }

        return masterLists;
    }

    /**
     * 동일 국가의 여러 버전 Master List 생성
     *
     * @param countryCode 국가 코드
     * @param versionCount 버전 개수
     * @return List of MasterList (same country, different versions)
     */
    public static List<MasterList> createMultipleVersions(CountryCode countryCode, int versionCount) {
        List<MasterList> masterLists = new ArrayList<>();

        for (int i = 0; i < versionCount; i++) {
            MasterList ml = MasterList.create(
                MasterListId.newId(),
                UploadId.newId(),
                countryCode,
                MasterListVersion.of("1." + i),
                createCmsBinaryData(5000 + (i * 100)),
                createSignerInfo(countryCode),
                10 + i
            );
            masterLists.add(ml);

            // Simulate time delay between versions
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return masterLists;
    }

    /**
     * CMS Binary Data 생성 (테스트용 더미 데이터)
     *
     * <p>CMS 형식은 ASN.1 SEQUENCE (0x30)로 시작해야 합니다.
     * 테스트용으로 유효한 ASN.1 구조의 더미 데이터를 생성합니다.</p>
     *
     * @param size 바이트 크기
     * @return CmsBinaryData
     */
    private static CmsBinaryData createCmsBinaryData(int size) {
        if (size < 100) {
            size = 100; // Minimum size for valid CMS
        }

        byte[] dummyData = new byte[size];

        // ASN.1 SEQUENCE tag (0x30) - CMS starts with this
        dummyData[0] = 0x30;

        // ASN.1 long-form length encoding
        // 0x82 means 2 octets follow for length
        dummyData[1] = (byte) 0x82;
        int contentLength = size - 4; // minus header bytes
        dummyData[2] = (byte) ((contentLength >> 8) & 0xFF);
        dummyData[3] = (byte) (contentLength & 0xFF);

        // Fill remaining bytes with test data
        for (int i = 4; i < size; i++) {
            dummyData[i] = (byte) (i % 256);
        }

        return CmsBinaryData.of(dummyData);
    }

    /**
     * SignerInfo 생성 (테스트용)
     *
     * @param countryCode 국가 코드
     * @return SignerInfo
     */
    private static SignerInfo createSignerInfo(CountryCode countryCode) {
        String signerDn = String.format("CN=CSCA %s, O=%s Government, C=%s",
            countryCode.getValue(),
            countryCode.getValue(),
            countryCode.getValue()
        );
        Map<String, Object> signerData = new HashMap<>();
        signerData.put("signerDN", signerDn);
        signerData.put("signatureAlgorithm", "SHA256withRSA");
        return SignerInfo.of(signerData);
    }
}
