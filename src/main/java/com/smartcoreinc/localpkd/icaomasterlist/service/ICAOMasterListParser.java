package com.smartcoreinc.localpkd.icaomasterlist.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.smartcoreinc.localpkd.enums.X509ValidationResult;
import com.smartcoreinc.localpkd.icaomasterlist.entity.CscaCertificate;
import com.smartcoreinc.localpkd.sse.Progress;
import com.smartcoreinc.localpkd.sse.ProgressEvent;
import com.smartcoreinc.localpkd.sse.ProgressPublisher;
import com.smartcoreinc.localpkd.validator.X509CertificateValidator;

import lombok.extern.slf4j.Slf4j;

/**
 * ICAO Master List (회원국들의 CSCA Master List) 파서
 * 개선된 버전: 통계 정보 수집 및 에러 처리 강화
 */
@Slf4j
@Service
public class ICAOMasterListParser {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    // Dependencies
    private final X509CertificateValidator certificateValidator;
    private final CscaLdapAddService cscaCertificateService;
    // SSE publisher(파싱 진행 상태 정보 생성자)
    private final ProgressPublisher progressPublisher;

    // 파싱 결과 데이터
    private int numberOfCertsTotal;
    private final AtomicInteger numberOfCertsParsed = new AtomicInteger(0);
    private final Map<String, Integer> cscaCountByCountry = new HashMap<>();
    private final List<X509Certificate> validCerts = new ArrayList<>();
    private final List<X509Certificate> invalidCerts = new ArrayList<>();
    private final List<String> errorMessages = new ArrayList<>();
    
    // 분석 메타데이터
    private String currentFileName;
    private long currentFileSize;
    private LocalDateTime analysisStartTime;
    private LocalDateTime analysisEndTime;


    public ICAOMasterListParser(X509CertificateValidator cscaCertificateValidator,
                                CscaLdapAddService cscaCertificateService,
                                ProgressPublisher progressPublisher) {
        this.certificateValidator = cscaCertificateValidator;
        this.cscaCertificateService = cscaCertificateService;
        this.progressPublisher = progressPublisher;
    }

    // Getters for statistics
    public Map<String, Integer> getCscaCountByCountry() {
        return new HashMap<>(cscaCountByCountry);
    }

    public List<X509Certificate> getValidCertificates() {
        return new ArrayList<>(validCerts);
    }

    public List<X509Certificate> getInvalidCertificates() {
        return new ArrayList<>(invalidCerts);
    }

    public List<String> getErrorMessages() {
        return new ArrayList<>(errorMessages);
    }

    public int getTotalCertificates() {
        return numberOfCertsTotal;
    }

    public int getProcessedCertificates() {
        return numberOfCertsParsed.get();
    }

    public String getCurrentFileName() {
        return currentFileName;
    }

    public long getCurrentFileSize() {
        return currentFileSize;
    }

    public LocalDateTime getAnalysisStartTime() {
        return analysisStartTime;
    }

    public LocalDateTime getAnalysisEndTime() {
        return analysisEndTime;
    }

    /**
     * ICAO Master List 파일의 내용(byte array)을 분석하고 개별 CSCA 인증서를
     * CscaLdapAddService에 전달하여 LDAP(local PKD)등록한다.
     * 
     * @param data Master List 파일 데이터
     * @param fileName 파일명 (통계용)
     * @param isAddLdap LDAP 저장 여부 플래그
     * @throws Exception 파싱 중 발생한 예외
     */
    public void parseMasterList(byte[] data, String fileName, boolean isAddLdap) throws Exception {
        parseMasterList(data, fileName, data.length, isAddLdap);
    }

    /**
     * ICAO Master List 파일 분석 (기존 호환성 유지)
     */
    public void parseMasterList(byte[] data, boolean isAddLdap) throws Exception {
        parseMasterList(data, "unknown.ml", data.length, isAddLdap);
    }

    /**
     * ICAO Master List 파일 분석 - 완전한 매개변수 버전
     * 
     * @param data Master List 파일 데이터
     * @param fileName 파일명
     * @param fileSize 파일 크기
     * @param isAddLdap LDAP 저장 여부 플래그
     * @throws Exception 파싱 중 발생한 예외
     */
    public void parseMasterList(byte[] data, String fileName, long fileSize, boolean isAddLdap) throws Exception {
        // 초기화
        initializeParsingSession(fileName, fileSize);

        try {
            // PKCS7-signature message 처리 클래스 생성
            CMSSignedData signedData = new CMSSignedData(data);
            
            // ICAO/UN CSCA root 인증서로 Master List Trust Anchor 검증
            // TODO: ICAO/UN ROOT 인증서 파일 경로를 외부에서 받을 수 있게 개선 필요
            ClassPathResource resource = new ClassPathResource("data/UN_CSCA_2.pem");
            Path path = Paths.get(resource.getURI());
            String filePath = path.toAbsolutePath().toString();
            ICAOMasterListVerifier mlVerifier = new ICAOMasterListVerifier(filePath);
            boolean isOk = mlVerifier.verify(signedData);
            if (!isOk) {
                log.error("Master List 신뢰 체인 검증 실패.");
                throw new RuntimeException("Master List 신뢰 체인 검증 실패.");
            }
            log.debug("Master List 신뢰 체인 검증 성공!!.");
            publishProgress("Master List 신뢰 체인 검증 성공", 0);
                        
            // CSCA Master list 데이터 추출
            CMSProcessable signedContent = signedData.getSignedContent();
            byte[] contentBytes = (byte[]) signedContent.getContent();
            
            // ASN.1 SET OF Certificate 추출
            try (ASN1InputStream asn1In = new ASN1InputStream(contentBytes)) {
                ASN1Primitive root = asn1In.readObject();
                if (!(root instanceof ASN1Sequence seq) || seq.size() < 2 || !(seq.getObjectAt(1) instanceof ASN1Set certSet)) {
                    throw new IllegalStateException("Unexpected ML content structure");
                }
                numberOfCertsTotal = certSet.size();
                
                log.info("총 {}개의 CSCA 인증서 발견", numberOfCertsTotal);
                publishProgress("총 %d 개의 CSCA 인증서 분석 시작".formatted(numberOfCertsTotal), 0);
                
                // X.509 인증서 Converter Provider를 Bouncy Castle로 지정 
                JcaX509CertificateConverter converter = 
                    new JcaX509CertificateConverter().setProvider("BC");
                // 각 인증서 처리
                processCertificates(certSet, converter, isAddLdap);
                
                analysisEndTime = LocalDateTime.now();
                log.info("=== Master List 분석 완료 ===");
                String result = "유효 인증서: %d개, 무효 인증서: %d개, 총 국가: %d개".formatted(validCerts.size(), invalidCerts.size(), cscaCountByCountry.size());
                log.info(result);
                
                publishProgress("=== Master List 분석 완료 ===", 1.0);
                publishProgress(result, 1.0);
            }
        } catch (Exception e) {
            analysisEndTime = LocalDateTime.now();
            String errorMsg = "Master List 분석 실패: " + e.getMessage();
            log.error(errorMsg, e);
            errorMessages.add(errorMsg);
            publishProgress("분석 실패: " + e.getMessage(), 0);
            throw e;
        }
    }

    /**
     * 파싱 세션 초기화
     */
    private void initializeParsingSession(String fileName, long fileSize) {
        this.currentFileName = fileName;
        this.currentFileSize = fileSize;
        this.analysisStartTime = LocalDateTime.now();
        this.analysisEndTime = null;
        
        // 이전 결과 초기화
        numberOfCertsTotal = 0;
        numberOfCertsParsed.set(0);
        cscaCountByCountry.clear();
        validCerts.clear();
        invalidCerts.clear();
        errorMessages.clear();
    }

    /**
     * 인증서 배치 처리
     */
    private void processCertificates(ASN1Set certSet, JcaX509CertificateConverter converter, boolean isAddLdap) throws Exception {
        int processed = 0;
        
        for (ASN1Encodable encodable : certSet) {
            try {
                Certificate bcCert = Certificate.getInstance(encodable);
                X509CertificateHolder holder = new X509CertificateHolder(bcCert);
                X509Certificate x509Cert = converter.getCertificate(holder);

                // 인증서 처리
                processSingleCertificate(x509Cert, isAddLdap);
                
                processed++;
                numberOfCertsParsed.set(processed);

                // 진행률 업데이트 (10개마다)
                if (processed % 10 == 0 || processed == numberOfCertsTotal) {
                    double progress = (double) processed / numberOfCertsTotal;
                    String message = String.format("처리 중: %d/%d (%s)", 
                                                  processed, numberOfCertsTotal,
                                                  extractCountryCode(x509Cert.getSubjectX500Principal().getName()));
                    publishProgress(message, progress);
                }

                // 짧은 대기 시간 (UI 업데이트용)
                sleepQuietly(5);
                
            } catch (Exception e) {
                processed++;
                String errorMsg = String.format("인증서 처리 실패 (인덱스: %d): %s", processed, e.getMessage());
                log.warn(errorMsg, e);
                errorMessages.add(errorMsg);
                
                // 개별 인증서 오류는 전체 프로세스를 중단하지 않음
                continue;
            }
        }
    }

    /**
     * 단일 인증서 처리
     */
    private void processSingleCertificate(X509Certificate x509Cert, boolean isAddLdap) {
        try {
            // CSCA(X.509) 인증서 검증 후 클래스 내부 멤버 변수에 저장
            X509ValidationResult validationResult = certificateValidator.validateCertificate(x509Cert);
            if (validationResult == X509ValidationResult.SUCCESS) {
                validCerts.add(x509Cert);
            } else {
                invalidCerts.add(x509Cert);
            }

            // 국가별 통계 업데이트
            String subject = x509Cert.getSubjectX500Principal().getName();
            String country = extractCountryCode(subject);
            cscaCountByCountry.merge(country, 1, Integer::sum);
            
            // LDAP 저장 (필요한 경우)
            if (isAddLdap) {
                saveCertificateToLdap(x509Cert, validationResult);
            }
            
        } catch (Exception e) {
            String errorMsg = String.format("인증서 처리 중 오류 (Subject: %s): %s", 
                                          x509Cert.getSubjectX500Principal().getName(), e.getMessage());
            log.warn(errorMsg, e);
            errorMessages.add(errorMsg);
            
            // 실패한 인증서는 무효 목록에 추가
            invalidCerts.add(x509Cert);
        }
    }

    /**
     * LDAP에 인증서 저장
     */
    private void saveCertificateToLdap(X509Certificate x509Cert, X509ValidationResult validationResult) {
        try {
            String validityStatus = validationResult.name();
            CscaCertificate cscaCertificate = cscaCertificateService.save(x509Cert, validityStatus);
            log.debug("LDAP 저장 완료: {}", cscaCertificate.getDn().toString());
        } catch (Exception e) {
            String errorMsg = String.format("LDAP 저장 실패 (Subject: %s): %s", 
                                          x509Cert.getSubjectX500Principal().getName(), e.getMessage());
            log.error(errorMsg, e);
            errorMessages.add(errorMsg);
        }
    }

    /**
     * 진행 상황 발행
     */
    private void publishProgress(String message, double progress) {
        try {
            Progress progressObj = new Progress(progress, "ML");
            ProgressEvent progressEvent = new ProgressEvent(
                progressObj, 
                numberOfCertsParsed.get(), 
                numberOfCertsTotal, 
                message
            );
            progressPublisher.notifyProgressListeners(progressEvent);
        } catch (Exception e) {
            log.warn("진행 상황 발행 실패: {}", e.getMessage());
        }
    }

    /**
     * 국가 코드 추출
     */
    private String extractCountryCode(String dn) {
        if (dn == null) return "UNKNOWN";
        
        for (String part : dn.split(",")) {
            String trimmedPart = part.trim();
            if (trimmedPart.startsWith("C=")) {
                String countryCode = trimmedPart.substring(2).toUpperCase();
                return countryCode.isEmpty() ? "UNKNOWN" : countryCode;
            }
        }
        return "UNKNOWN";
    }

    /**
     * 조용히 대기
     */
    private static void sleepQuietly(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sleep interrupted: {}", e.getMessage());
        }
    }

    /**
     * 분석 통계 요약 정보 반환
     */
    public String getAnalysisSummary() {
        if (analysisStartTime == null) {
            return "분석이 아직 시작되지 않았습니다.";
        }
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        StringBuilder summary = new StringBuilder();
        
        summary.append(String.format("파일명: %s%n", currentFileName));
        summary.append(String.format("파일크기: %s%n", formatFileSize(currentFileSize)));
        summary.append(String.format("분석시작: %s%n", analysisStartTime.format(formatter)));
        
        if (analysisEndTime != null) {
            summary.append(String.format("분석 완료: %s%n", analysisEndTime.format(formatter)));
            long durationSeconds = java.time.Duration.between(analysisStartTime, analysisEndTime).getSeconds();
            summary.append(String.format("소요 시간: %d초%n", durationSeconds));
        } else {
            summary.append("분석 상태: 진행 중\n");
        }
        
        summary.append(String.format("총 인증서: %d개%n", numberOfCertsTotal));
        summary.append(String.format("처리 완료: %d개%n", numberOfCertsParsed.get()));
        summary.append(String.format("유효 인증서: %d개%n", validCerts.size()));
        summary.append(String.format("무효 인증서: %d개%n", invalidCerts.size()));
        summary.append(String.format("참여 국가: %d개%n", cscaCountByCountry.size()));
        
        if (!errorMessages.isEmpty()) {
            summary.append(String.format("오류 발생: %d건%n", errorMessages.size()));
        }
        
        return summary.toString();
    }

    /**
     * 파일 크기 포맷팅
     */
    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        
        return String.format("%.1f %s", 
                           bytes / Math.pow(1024, digitGroups), 
                           units[digitGroups]);
    }

    /**
     * 현재 진행률 반환 (백분율)
     */
    public double getCurrentProgress() {
        if (numberOfCertsTotal == 0) return 0.0;
        return (double) numberOfCertsParsed.get() / numberOfCertsTotal;
    }

    /**
     * 분석 상태 반환
     */
    public String getAnalysisStatus() {
        if (analysisStartTime == null) {
            return "READY";
        } else if (analysisEndTime == null) {
            return "IN_PROGRESS";
        } else if (!errorMessages.isEmpty()) {
            return "COMPLETED_WITH_ERRORS";
        } else {
            return "COMPLETED";
        }
    }

    /**
     * 국가별 인증서 통계를 정렬된 형태로 반환
     */
    public List<Map.Entry<String, Integer>> getCountryStatisticsSorted() {
        return cscaCountByCountry.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())) // 내림차순
                .toList();
    }

    /**
     * 유효성 비율 계산 (백분율)
     */
    public double getValidityRate() {
        int total = validCerts.size() + invalidCerts.size();
        if (total == 0) return 0.0;
        return (double) validCerts.size() / total * 100.0;
    }

    /**
     * 평균 국가당 인증서 수 계산
     */
    public double getAverageCertificatesPerCountry() {
        if (cscaCountByCountry.isEmpty()) return 0.0;
        return (double) (validCerts.size() + invalidCerts.size()) / cscaCountByCountry.size();
    }

    /**
     * 분석 결과 리셋 (새로운 분석 전)
     */
    public void resetAnalysisResults() {
        numberOfCertsTotal = 0;
        numberOfCertsParsed.set(0);
        cscaCountByCountry.clear();
        validCerts.clear();
        invalidCerts.clear();
        errorMessages.clear();
        
        currentFileName = null;
        currentFileSize = 0;
        analysisStartTime = null;
        analysisEndTime = null;
        
        log.debug("분석 결과가 초기화되었습니다.");
    }
}
