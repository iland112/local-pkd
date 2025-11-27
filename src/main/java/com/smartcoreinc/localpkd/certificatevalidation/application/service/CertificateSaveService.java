package com.smartcoreinc.localpkd.certificatevalidation.application.service;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidationError;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidationResult;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CertificateSaveService - 인증서 저장 전용 서비스
 *
 * <p><b>책임</b>: 각 인증서를 개별 트랜잭션으로 저장하여 부분 실패 방지</p>
 *
 * <p><b>핵심 기능</b>:</p>
 * <ul>
 *   <li>REQUIRES_NEW propagation: 각 인증서를 독립적인 트랜잭션으로 처리</li>
 *   <li>중복 체크: fingerprint 기반 중복 확인 (auto-flush 전에)</li>
 *   <li>예외 처리: DataIntegrityViolationException 잡아서 로깅만</li>
 *   <li>부분 실패 허용: 일부 인증서 저장 실패해도 나머지는 계속 처리</li>
 * </ul>
 *
 * <p><b>트랜잭션 전략</b>:</p>
 * <pre>
 * ValidateCertificatesUseCase (REQUIRED)
 *   └─▶ CertificateSaveService.saveOrUpdate() (REQUIRES_NEW)  // 개별 트랜잭션
 *       - 성공 시: commit
 *       - 실패 시: rollback (이 인증서만, 전체 트랜잭션은 영향 없음)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateSaveService {

    private final CertificateRepository certificateRepository;

    /**
     * 인증서 저장 또는 업데이트 (개별 트랜잭션)
     *
     * <p><b>REQUIRES_NEW 전략</b>: 이 메서드는 항상 새로운 트랜잭션을 시작합니다.
     * 따라서 이 메서드의 성공/실패는 호출자의 트랜잭션에 영향을 주지 않습니다.</p>
     *
     * <p><b>중복 처리 전략</b>:</p>
     * <ol>
     *   <li>먼저 fingerprint로 기존 인증서 조회 (이 시점에 auto-flush 발생 가능)</li>
     *   <li>기존 인증서가 있으면 업데이트</li>
     *   <li>없으면 새로 저장</li>
     *   <li>DataIntegrityViolationException 발생 시 (race condition):
     *       <ul>
     *           <li>WARNING 로그 출력</li>
     *           <li>예외를 삼킴 (호출자의 트랜잭션은 계속 진행)</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * @param certificate 저장할 인증서
     * @param validationResult 검증 결과
     * @param errors 검증 오류 목록
     * @param validCertificateIds 유효 인증서 ID 리스트 (출력 파라미터)
     * @param invalidCertificateIds 무효 인증서 ID 리스트 (출력 파라미터)
     * @param processedFingerprints 처리된 fingerprint 집합 (중복 방지용)
     * @param isCsca CSCA 인증서 여부
     * @param uploadId 원본 업로드 파일 ID (로깅용)
     * @return 저장 성공 여부 (true: 저장/업데이트 성공, false: 중복으로 스킵)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveOrUpdate(
        Certificate certificate,
        ValidationResult validationResult,
        List<ValidationError> errors,
        List<UUID> validCertificateIds,
        List<UUID> invalidCertificateIds,
        Set<String> processedFingerprints,
        boolean isCsca,
        UUID uploadId
    ) {
        String fingerprint = certificate.getX509Data().getFingerprintSha256();
        String certType = isCsca ? "CSCA" : "DSC";

        try {
            // 1. 배치 내 중복 체크 (메모리 기반, 빠름)
            if (processedFingerprints.contains(fingerprint)) {
                log.warn("[{}] Duplicate fingerprint detected in batch (skipped): fingerprint={}, uploadId={}",
                    certType, fingerprint, uploadId);
                return false; // 중복이므로 저장 안 함
            }

            // 2. DB 중복 체크 (findByFingerprint 호출 시 auto-flush 발생 가능)
            Certificate existingCert = certificateRepository
                .findByFingerprint(fingerprint)
                .orElse(null);

            if (existingCert != null) {
                // 2-1. 기존 인증서 업데이트
                log.debug("[{}] Updating existing certificate: fingerprint={}, subject={}",
                    certType, fingerprint, certificate.getSubjectInfo().getCommonName());

                existingCert.recordValidation(validationResult);
                existingCert.addValidationErrors(errors);
                Certificate updated = certificateRepository.save(existingCert);

                // 처리된 fingerprint 기록
                processedFingerprints.add(fingerprint);

                // 유효/무효 리스트에 추가
                if (updated.isValid()) {
                    validCertificateIds.add(updated.getId().getId());
                } else {
                    invalidCertificateIds.add(updated.getId().getId());
                }

                log.debug("[{}] Certificate updated successfully: id={}, status={}",
                    certType, updated.getId().getId(), updated.getStatus());
                return true;

            } else {
                // 2-2. 새 인증서 저장
                log.debug("[{}] Saving new certificate: fingerprint={}, subject={}",
                    certType, fingerprint, certificate.getSubjectInfo().getCommonName());

                Certificate savedCert = certificateRepository.save(certificate);

                // 처리된 fingerprint 기록
                processedFingerprints.add(fingerprint);

                // 유효/무효 리스트에 추가
                if (savedCert.isValid()) {
                    validCertificateIds.add(savedCert.getId().getId());
                } else {
                    invalidCertificateIds.add(savedCert.getId().getId());
                }

                log.debug("[{}] Certificate saved successfully: id={}, status={}",
                    certType, savedCert.getId().getId(), savedCert.getStatus());
                return true;
            }

        } catch (DataIntegrityViolationException dive) {
            // 3. 중복 키 예외 처리 (race condition)
            // 다른 트랜잭션에서 동시에 같은 인증서를 저장한 경우
            log.warn("[{}] Duplicate certificate detected (race condition, skipped): fingerprint={}, subject={}, uploadId={}",
                certType, fingerprint, certificate.getSubjectInfo().getCommonName(), uploadId);
            log.debug("[{}] DataIntegrityViolationException details: {}", certType, dive.getMessage());

            // 예외를 삼킴 - 호출자의 트랜잭션은 계속 진행
            // REQUIRES_NEW이므로 이 트랜잭션만 rollback되고, 호출자 트랜잭션은 영향 없음
            return false;

        } catch (Exception ex) {
            // 4. 기타 예외 처리
            log.error("[{}] Unexpected error while saving certificate: fingerprint={}, subject={}, uploadId={}",
                certType, fingerprint, certificate.getSubjectInfo().getCommonName(), uploadId, ex);

            // 예외를 다시 던지지 않음 - 부분 실패 허용
            // REQUIRES_NEW이므로 이 트랜잭션만 rollback되고, 호출자 트랜잭션은 계속 진행
            return false;
        }
    }
}
