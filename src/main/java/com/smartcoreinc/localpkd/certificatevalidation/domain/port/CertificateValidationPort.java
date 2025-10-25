package com.smartcoreinc.localpkd.certificatevalidation.domain.port;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidationError;

import java.util.List;

/**
 * CertificateValidationPort - X.509 인증서 검증 Port (Domain Layer)
 *
 * <p><b>Hexagonal Architecture Port Pattern</b>:</p>
 * <ul>
 *   <li>Domain Layer에 인터페이스 정의</li>
 *   <li>Infrastructure Layer에서 Adapter로 구현</li>
 *   <li>외부 라이브러리(BouncyCastle)와의 결합도를 낮춤</li>
 * </ul>
 *
 * <p><b>책임 (Responsibilities)</b>:</p>
 * <ul>
 *   <li>X.509 인증서 서명 검증</li>
 *   <li>인증서 유효기간 검증</li>
 *   <li>Basic Constraints 검증 (CA 플래그, Path Length 등)</li>
 *   <li>Key Usage 검증</li>
 *   <li>Trust Chain 구축 (CSCA → DSC)</li>
 *   <li>CRL/OCSP 기반 폐기 확인</li>
 * </ul>
 *
 * <p><b>설계 원칙</b>:</p>
 * <ul>
 *   <li>Dependency Inversion Principle: Domain이 외부 라이브러리에 의존하지 않음</li>
 *   <li>Single Responsibility: 각 메서드는 단일 검증 책임만 수행</li>
 *   <li>Testability: Mock 객체로 테스트 가능</li>
 * </ul>
 *
 * <p><b>구현체 예시</b>:</p>
 * <ul>
 *   <li>BouncyCastleValidationAdapter: BouncyCastle 라이브러리 사용</li>
 *   <li>JavaSecurityValidationAdapter: Java Security API 사용 (향후)</li>
 * </ul>
 *
 * <p><b>사용 예시 - Use Case에서 주입</b>:</p>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class ValidateCertificateUseCase {
 *
 *     // Domain Port 주입 (구현체는 Spring이 자동 연결)
 *     private final CertificateValidationPort validationPort;
 *     private final CertificateRepository certificateRepository;
 *
 *     @Transactional
 *     public ValidateCertificateResponse execute(ValidateCertificateCommand command) {
 *         Certificate certificate = certificateRepository.findById(certId).orElseThrow();
 *
 *         // 서명 검증
 *         ValidationResult signatureResult = validationPort.validateSignature(certificate);
 *
 *         // 유효기간 검증
 *         ValidationResult validityResult = validationPort.validateValidity(certificate);
 *
 *         // 검증 결과 기록
 *         certificate.recordValidation(signatureResult);
 *
 *         return ValidateCertificateResponse.success(...);
 *     }
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
public interface CertificateValidationPort {

    /**
     * 인증서 서명 검증
     *
     * <p>발급자(Issuer)의 공개 키로 인증서 서명을 검증합니다.</p>
     *
     * <p><b>검증 프로세스</b>:</p>
     * <ol>
     *   <li>발급자 인증서 조회 (Issuer DN 기반)</li>
     *   <li>발급자 공개 키 추출</li>
     *   <li>서명 알고리즘 확인 (SHA256WithRSA, SHA256WithEC 등)</li>
     *   <li>서명 검증 수행</li>
     * </ol>
     *
     * @param certificate 검증할 인증서
     * @param issuerCertificate 발급자 인증서 (null이면 self-signed 검증)
     * @return 검증 성공 시 true, 실패 시 false
     * @throws IllegalArgumentException certificate가 null인 경우
     */
    boolean validateSignature(Certificate certificate, Certificate issuerCertificate);

    /**
     * 인증서 유효기간 검증
     *
     * <p>현재 시간이 notBefore와 notAfter 사이에 있는지 확인합니다.</p>
     *
     * <p><b>검증 규칙</b>:</p>
     * <ul>
     *   <li>notBefore <= 현재 시간 < notAfter: 유효</li>
     *   <li>현재 시간 < notBefore: 아직 유효하지 않음</li>
     *   <li>현재 시간 >= notAfter: 만료됨</li>
     * </ul>
     *
     * @param certificate 검증할 인증서
     * @return 유효기간 내이면 true, 아니면 false
     * @throws IllegalArgumentException certificate가 null인 경우
     */
    boolean validateValidity(Certificate certificate);

    /**
     * Basic Constraints 검증
     *
     * <p>X.509 v3 Basic Constraints Extension을 검증합니다.</p>
     *
     * <p><b>검증 항목</b>:</p>
     * <ul>
     *   <li>CA 플래그: CSCA, DSC_NC는 CA=true, DSC, DS는 CA=false</li>
     *   <li>Path Length: CA 인증서의 경우 인증 경로 깊이 제한 확인</li>
     *   <li>Critical Flag: Basic Constraints가 critical로 표시되었는지 확인</li>
     * </ul>
     *
     * @param certificate 검증할 인증서
     * @return Basic Constraints가 유효하면 true, 아니면 false
     * @throws IllegalArgumentException certificate가 null인 경우
     */
    boolean validateBasicConstraints(Certificate certificate);

    /**
     * Key Usage 검증
     *
     * <p>X.509 v3 Key Usage Extension을 검증합니다.</p>
     *
     * <p><b>검증 항목</b>:</p>
     * <ul>
     *   <li>CSCA: keyCertSign, cRLSign 필수</li>
     *   <li>DSC: digitalSignature 필수</li>
     *   <li>DS: digitalSignature 필수</li>
     * </ul>
     *
     * @param certificate 검증할 인증서
     * @return Key Usage가 유효하면 true, 아니면 false
     * @throws IllegalArgumentException certificate가 null인 경우
     */
    boolean validateKeyUsage(Certificate certificate);

    /**
     * Trust Chain 구축
     *
     * <p>End Entity 인증서부터 Trust Anchor(CSCA)까지의 인증 경로를 구축합니다.</p>
     *
     * <p><b>Trust Chain 구축 프로세스</b>:</p>
     * <ol>
     *   <li>End Entity 인증서의 Issuer DN 확인</li>
     *   <li>Issuer DN과 일치하는 발급자 인증서 검색</li>
     *   <li>발급자 인증서의 서명 검증</li>
     *   <li>발급자가 Trust Anchor(CSCA)인지 확인</li>
     *   <li>Trust Anchor가 아니면 재귀적으로 반복 (최대 깊이 제한)</li>
     * </ol>
     *
     * <p><b>Trust Anchor 판단 기준</b>:</p>
     * <ul>
     *   <li>CertificateType = CSCA</li>
     *   <li>Self-signed (Subject DN = Issuer DN)</li>
     *   <li>CA 플래그 = true</li>
     * </ul>
     *
     * @param certificate End Entity 인증서
     * @param trustAnchor Trust Anchor 인증서 (null이면 자동 검색)
     * @param maxDepth 최대 인증 경로 깊이 (기본값: 5)
     * @return Trust Chain (End Entity → ... → Trust Anchor)
     * @throws IllegalArgumentException certificate가 null인 경우
     */
    List<Certificate> buildTrustChain(Certificate certificate, Certificate trustAnchor, int maxDepth);

    /**
     * 인증서 폐기 확인 (CRL/OCSP)
     *
     * <p>CRL(Certificate Revocation List) 또는 OCSP(Online Certificate Status Protocol)를 통해
     * 인증서가 폐기되었는지 확인합니다.</p>
     *
     * <p><b>검증 프로세스</b>:</p>
     * <ol>
     *   <li>인증서에서 CRL Distribution Points Extension 추출</li>
     *   <li>CRL 다운로드 및 파싱</li>
     *   <li>CRL에서 인증서 일련 번호 검색</li>
     *   <li>OCSP URL이 있으면 OCSP 요청 (선택)</li>
     * </ol>
     *
     * <p><b>결과</b>:</p>
     * <ul>
     *   <li>폐기되지 않음: true</li>
     *   <li>폐기됨: false</li>
     *   <li>CRL/OCSP 접근 불가: true (보수적 접근)</li>
     * </ul>
     *
     * @param certificate 검증할 인증서
     * @return 폐기되지 않았으면 true, 폐기되었으면 false
     * @throws IllegalArgumentException certificate가 null인 경우
     */
    boolean checkRevocation(Certificate certificate);

    /**
     * 완전한 인증서 검증 수행
     *
     * <p>모든 검증 항목을 수행하고 검증 오류 목록을 반환합니다.</p>
     *
     * <p><b>검증 항목</b>:</p>
     * <ul>
     *   <li>서명 검증</li>
     *   <li>유효기간 검증</li>
     *   <li>Basic Constraints 검증</li>
     *   <li>Key Usage 검증</li>
     *   <li>Trust Chain 검증</li>
     *   <li>폐기 확인 (옵션)</li>
     * </ul>
     *
     * @param certificate 검증할 인증서
     * @param trustAnchor Trust Anchor 인증서 (null이면 자동 검색)
     * @param checkRevocation 폐기 확인 수행 여부
     * @return 검증 오류 목록 (빈 리스트이면 모든 검증 성공)
     * @throws IllegalArgumentException certificate가 null인 경우
     */
    List<ValidationError> performFullValidation(
        Certificate certificate,
        Certificate trustAnchor,
        boolean checkRevocation
    );
}
