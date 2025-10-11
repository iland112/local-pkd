package com.smartcoreinc.localpkd.ldif.service.verification;

import java.security.cert.X509CRL;
import java.util.Date;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CRLValidationStrategy implements CertificateVerificationStrategy<X509CRL, Boolean> {

    @Override
    public Boolean verify(X509CRL crl, VerificationContext context) {
        try {
            // 1. CRL 기본 정보 검증
            if (crl.getIssuerX500Principal() == null) {
                log.warn("Record {}: CRL has no issuer", context.getRecordNumber());
                return false;
            }
            
            // 2. CRL 유효 기간 검증
            Date now = new Date();
            Date thisUpdate = crl.getThisUpdate();
            Date nextUpdate = crl.getNextUpdate();
            
            if (thisUpdate != null && thisUpdate.after(now)) {
                log.warn("Record {}: CRL thisUpdate is in the future", context.getRecordNumber());
                return false;
            }
            
            if (nextUpdate != null && nextUpdate.before(now)) {
                log.warn("Record {}: CRL has expired (nextUpdate passed)", context.getRecordNumber());
                // 만료된 CRL이지만 경고만 하고 검증은 통과시킬 수도 있음
                // return false;
            }
            
            // 3. CRL 서명 알고리즘 검증
            String sigAlgName = crl.getSigAlgName();
            if (sigAlgName == null || sigAlgName.trim().isEmpty()) {
                log.warn("Record {}: CRL has no signature algorithm", context.getRecordNumber());
                return false;
            }
            
            // 4. 취약한 서명 알고리즘 체크
            if (isWeakSignatureAlgorithm(sigAlgName)) {
                log.warn("Record {}: CRL uses weak signature algorithm: {}", 
                    context.getRecordNumber(), sigAlgName);
                // 취약한 알고리즘이지만 검증은 통과시킬 수도 있음
                // return false;
            }
            
            log.debug("Record {}: CRL basic validation passed - Issuer: {}, SigAlg: {}", 
                context.getRecordNumber(), 
                crl.getIssuerX500Principal().getName(),
                sigAlgName);
            
            return true;
            
        } catch (Exception e) {
            log.error("Record {}: CRL verification failed with exception: {}", 
                context.getRecordNumber(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean supports(Class<?> dataType) {
        return X509CRL.class.isAssignableFrom(dataType);
    }

    /**
     * 취약한 서명 알고리즘인지 확인
     */
    private boolean isWeakSignatureAlgorithm(String algorithm) {
        String lowerAlg = algorithm.toLowerCase();
        return lowerAlg.contains("md5") || 
               lowerAlg.contains("sha1") ||
               lowerAlg.equals("sha1withrsa") ||
               lowerAlg.equals("md5withrsa");
    }
    
}
