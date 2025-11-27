package com.smartcoreinc.localpkd.fileparsing.domain.model;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

/**
 * MasterListParseResult - Master List 파싱 결과 DTO
 *
 * <p><b>목적</b>: Master List 파싱 후 추출된 모든 데이터를 구조화하여 전달</p>
 *
 * <p>이 DTO는 MasterListParser 포트의 반환 타입으로 사용되며,
 * ParseMasterListFileUseCase에서 MasterList aggregate와 개별 Certificate 엔티티를 생성하는 데 사용됩니다.</p>
 *
 * <h3>포함 데이터</h3>
 * <ul>
 *   <li><b>countryCode</b>: Master List 발행 국가 (첫 번째 CSCA에서 추출)</li>
 *   <li><b>version</b>: Master List 버전</li>
 *   <li><b>cmsBinary</b>: 원본 CMS 바이너리 (LDAP 업로드용)</li>
 *   <li><b>signerInfo</b>: 서명자 정보 (DN, 알고리즘 등)</li>
 *   <li><b>cscaCertificates</b>: 추출된 CSCA 인증서 목록</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MasterListParseResult {

    private final CountryCode countryCode;
    private final MasterListVersion version;
    private final CmsBinaryData cmsBinary;
    private final SignerInfo signerInfo;
    private final List<ParsedCsca> cscaCertificates;

    /**
     * 파싱 성공 결과 생성
     *
     * @param countryCode Master List 발행 국가
     * @param version Master List 버전
     * @param cmsBinary 원본 CMS 바이너리
     * @param signerInfo 서명자 정보
     * @param cscaCertificates 추출된 CSCA 인증서 목록
     * @return MasterListParseResult
     */
    public static MasterListParseResult of(CountryCode countryCode,
                                            MasterListVersion version,
                                            CmsBinaryData cmsBinary,
                                            SignerInfo signerInfo,
                                            List<ParsedCsca> cscaCertificates) {
        if (cmsBinary == null) {
            throw new IllegalArgumentException("CMS binary cannot be null");
        }
        if (cscaCertificates == null) {
            throw new IllegalArgumentException("CSCA certificates list cannot be null");
        }

        return new MasterListParseResult(
            countryCode,
            version != null ? version : MasterListVersion.unknown(),
            cmsBinary,
            signerInfo != null ? signerInfo : SignerInfo.empty(),
            cscaCertificates
        );
    }

    /**
     * CSCA 인증서 개수
     */
    public int getCscaCount() {
        return cscaCertificates.size();
    }

    /**
     * ParsedCsca - 파싱된 CSCA 인증서 데이터
     *
     * <p>Master List에서 추출된 개별 CSCA 인증서 정보를 담습니다.</p>
     * <p>Certificate aggregate 생성 시 사용됩니다.</p>
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ParsedCsca {
        private final X509Certificate x509Certificate;
        private final String fingerprintSha256;
        private final CountryCode countryCode;

        public static ParsedCsca of(X509Certificate x509Certificate,
                                     String fingerprintSha256,
                                     CountryCode countryCode) {
            if (x509Certificate == null) {
                throw new IllegalArgumentException("X509Certificate cannot be null");
            }
            if (fingerprintSha256 == null || fingerprintSha256.isEmpty()) {
                throw new IllegalArgumentException("Fingerprint cannot be null or empty");
            }

            return new ParsedCsca(x509Certificate, fingerprintSha256, countryCode);
        }
    }
}
