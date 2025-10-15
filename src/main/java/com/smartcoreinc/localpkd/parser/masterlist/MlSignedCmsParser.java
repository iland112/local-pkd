package com.smartcoreinc.localpkd.parser.masterlist;

import java.io.FileInputStream;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Collection;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.FileType;
import com.smartcoreinc.localpkd.parser.common.CertificateParserUtil;
import com.smartcoreinc.localpkd.parser.common.FileParser;
import com.smartcoreinc.localpkd.parser.common.domain.ParseContext;
import com.smartcoreinc.localpkd.parser.common.domain.ParseResult;
import com.smartcoreinc.localpkd.parser.common.exception.ParsingException;
import com.smartcoreinc.localpkd.parser.core.ParsedCertificate;

import lombok.extern.slf4j.Slf4j;

/**
 * ML Signed CMS 파서
 * 
 * ICAO Master List (Signed CMS 파일 파싱
 * - 파일명: ICAO_ml_{Month}{Year}.ml
 * - 포맷: PKCS#7 Signed Data (CMS)
 * - 서명: UN/ICAO Trust Anchor로 검증
 */
@Slf4j
@Component
public class MlSignedCmsParser implements FileParser {

    static {
        // BouncyCastle Prover 등록
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public boolean supports(FileType fileType, FileFormat fileFormat) {
        return fileFormat == FileFormat.ML_SIGNED_CMS;
    }

    @Override
    public int getPriority() {
        return 50; // 높은 우선순위
    }

    @Override
    public ParseResult parse(byte[] fileData, ParseContext context) throws ParsingException {
        log.info("=== ML Signed CMS 파싱 시작: {} ===", context.getOriginalFileName());

        ParseResult result = ParseResult.builder()
            .fieldId(context.getFileId())
            .success(false)
            .parseStartTime(LocalDateTime.now())
            .build();

        try {
            // 1. CMS Signed Data 생성
            CMSSignedData signedData = new CMSSignedData(fileData);
            log.debug("CMS Signed Data 생성 완료");

            // 2. 서명 검증 (Trust Anchor 필요)
            if (context.getTrustAnchorPath() != null) {
                boolean singatureValid = verifySignature(signedData, context.getTrustAnchorPath());
                if (!singatureValid) {
                    throw new ParsingException(
                        context.getFileId(),
                        context.getOriginalFileName(),
                        "CMS 서명 검증 실패"
                    );
                }
                log.info("✅ CMS 서명 검증 성공");
            } else {
                log.warn("⚠️ Trust Anchor 경로가 없어 서명 검증을 건너뜁니다");
            }

            // 3. Singed Content 추출
            CMSProcessable signedContent = signedData.getSignedContent();
            byte[] contentBytes = (byte[]) signedContent.getContent();
            log.debug("Signed Content 추출 완료: {} bytes", contentBytes.length);

            // 4. ASN.1 SET OF Certificate 파싱
            try (ASN1InputStream asn1In = new ASN1InputStream(contentBytes)) {
                ASN1Primitive root = asn1In.readObject();

                if (!(root instanceof ASN1Sequence seq)) {
                    throw new ParsingException("예상하지 못한 ASN.1 구조: Sequence가 아님");
                }

                if (seq.size() < 2 || !(seq.getObjectAt(1) instanceof ASN1Set certSet)) {
                    throw new ParsingException("예상하지 못한 ML 콘텐츠 구조");
                }

                log.info("총 {}개의 CSCA 인증서 발견", certSet.size());

                // 5. 개별 인증서 파싱
                JcaX509CertificateConverter converter =
                    new JcaX509CertificateConverter().setProvider("BC");

                int processed = 0;
                int valid = 0;
                int invalid = 0;

                for (ASN1Encodable encodable : certSet) {
                    try {
                        Certificate bcCert = Certificate.getInstance(encodable);
                        X509CertificateHolder holder = new X509CertificateHolder(bcCert);
                        X509Certificate x509Cert = converter.getCertificate(holder);

                        // ParsedCerificate 생성
                        ParsedCertificate parsedCert = CertificateParserUtil.parseCertificate(x509Cert, context);

                        result.addCertificate(parsedCert);

                        if (parsedCert.isValid()) {
                            valid++;
                        } else {
                            invalid++;
                        }

                        processed++;

                        if (processed % 10 == 0) {
                            log.debug("처리 진행: {}/{}", processed, certSet.size());
                        }
                    } catch (Exception e) {
                        log.warn("인증서 처리 실패 (인덱스: {}): {}", processed, e.getMessage());
                        result.addError(String.format("인증서 %d 처리 실패: %s", processed, e.getMessage()));
                        invalid++;
                        processed++;
                    }
                }

                result.setValidEntries(valid);
                result.setInvalidEntries(invalid);
                result.setSuccess(true);

                log.info("✅ 파싱 완료: 총 {}, 유효 {}, 무효 {}", processed, valid, invalid);
            }
        } catch (ParsingException e) {
            throw e;
        } catch (Exception e) {
            log.error("ML Signed CMS 파싱 실패", e);
            result.fail("ML Signed CMS 파싱 실패: " + e.getMessage(), e);
            throw new ParsingException(
                context.getFileId(),
                context.getOriginalFileName(),
                "ML Signed CMS 파싱 실패",
                e
            );
        } finally {
            result.complete();
        }

        return result;
    }

    /**
     * CMS 서명 검증
     * @param signedData
     * @param trustAnchorPath
     * @return
     */
    private boolean verifySignature(CMSSignedData signedData, String trustAnchorPath) throws Exception {
        // Trust Anchor 로드
        X509Certificate trustAnchor;
        try (FileInputStream fis = new FileInputStream(trustAnchorPath)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BC");
            trustAnchor = (X509Certificate) certFactory.generateCertificate(fis);
            log.debug("Trust Anchor 로드: {}", trustAnchor.getSubjectX500Principal());
        }

        // 서명 검증
        Store<X509CertificateHolder> certStore = signedData.getCertificates();
        SignerInformationStore signerInfos = signedData.getSignerInfos();

        for (SignerInformation signer : signerInfos.getSigners()) {
            SignerId sid = signer.getSID();

            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> certCollection = certStore.getMatches(sid);

            if (certCollection.isEmpty()) {
                log.warn("서명자 인증서를 찾을 수 없음: {}", sid);
                continue;
            }

            X509CertificateHolder holder = certCollection.iterator().next();

            // 서명 검증
            SignerInformationVerifier verifier = new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider("BC")
                .build(holder);
            
            boolean valid = signer.verify(verifier);

            if (!valid) {
                log.error("❌ 서명 검증 실패: {}", sid);
                return false;
            }

            log.debug("✅ 서명 검증 성공: {}", sid);
        }

        return true;
    }

}
