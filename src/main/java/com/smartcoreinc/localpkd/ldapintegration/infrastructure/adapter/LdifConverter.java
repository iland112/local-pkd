package com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * LdifConverter - Certificate and CRL to LDIF format converter
 *
 * <p><b>Purpose</b>: Convert domain Certificate and CRL objects to LDIF text format
 * for uploading to OpenLDAP server</p>
 *
 * <h3>ICAO PKD LDIF Format (analyzed from real files)</h3>
 * <pre>
 * DN Structure:
 * cn={ESCAPED-SUBJECT-DN}+sn={SERIAL},o={ml|dsc|crl},c={COUNTRY},{baseDN}
 *
 * Example (DSC):
 * dn: cn=OU\=Identity Services Passport CA\,OU\=Passports\,O\=Government of New Zealand\,C\=NZ+sn=42E575AF,o=dsc,c=NZ,dc=ldap,dc=smartcoreinc,dc=com
 * pkdVersion: 1150
 * userCertificate;binary:: MIIFGTCCBAmg...
 * sn: 42E575AF
 * cn: OU=Identity Services Passport CA,OU=Passports,O=Government of New Zealand,C=NZ
 * objectClass: inetOrgPerson
 * objectClass: pkdDownload
 * objectClass: organizationalPerson
 * objectClass: top
 * objectClass: person
 *
 * Example (ML - Master List):
 * dn: cn=CN\=CSCA-FRANCE\,O\=Gouv\,C\=FR,o=ml,c=FR,dc=ldap,dc=smartcoreinc,dc=com
 * pkdVersion: 70
 * sn: 1
 * cn: CN=CSCA-FRANCE,O=Gouv,C=FR
 * objectClass: top
 * objectClass: person
 * objectClass: pkdMasterList
 * objectClass: pkdDownload
 * pkdMasterListContent:: ...
 *
 * CRL (inferred):
 * dn: cn={ISSUER-DN}+sn={SERIAL},o=crl,c={COUNTRY},dc=ldap,dc=smartcoreinc,dc=com
 * certificateRevocationList;binary:: ...
 * </pre>
 *
 * <h3>Organization Types</h3>
 * <ul>
 *   <li>o=ml - Master List (CSCA certificates from Master List)</li>
 *   <li>o=dsc - Document Signer Certificates</li>
 *   <li>o=crl - Certificate Revocation Lists</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 2.0
 * @since 2025-11-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LdifConverter {

    private final com.smartcoreinc.localpkd.ldapintegration.infrastructure.config.LdapProperties ldapProperties;

    /**
     * Convert Certificate to LDIF format following ICAO PKD structure
     *
     * @param certificate Certificate aggregate to convert
     * @return LDIF formatted text
     * @throws IllegalArgumentException if certificate data is invalid
     */
    public String certificateToLdif(Certificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("Certificate cannot be null");
        }

        try {
            // Extract certificate data
            String countryCode = certificate.getSubjectInfo().getCountryCode();
            String serialNumber = certificate.getX509Data().getSerialNumber();
            String subjectDn = certificate.getSubjectInfo().getDistinguishedName();
            byte[] certBinary = certificate.getX509Data().getCertificateBinary();
            CertificateType certType = certificate.getCertificateType();

            // Determine organization type: ml for CSCA, dsc for DSC/DSC_NC
            String orgType = (certType == CertificateType.CSCA) ? "ml" : "dsc";

            // Determine data hierarchy: nc-data for non-conformant (DSC_NC), data for conformant
            String dataHierarchy = (certType == CertificateType.DSC_NC) ? "nc-data" : "data";

            // Build DN following ICAO PKD structure:
            // cn={ESCAPED-SUBJECT-DN}+sn={SERIAL},o={ml|dsc},c={COUNTRY},dc={data|nc-data},dc=download,dc=pkd,{baseDN}
            String escapedSubjectDn = escapeLdapDn(subjectDn);
            String dn = String.format("cn=%s+sn=%s,o=%s,c=%s,dc=%s,dc=download,dc=pkd,%s",
                    escapedSubjectDn,
                    serialNumber,
                    orgType,
                    countryCode,
                    dataHierarchy,
                    ldapProperties.getBase());

            // Base64 encode certificate binary
            String base64Cert = Base64.getEncoder().encodeToString(certBinary);

            // Build LDIF entry following ICAO PKD format
            StringBuilder ldif = new StringBuilder();
            ldif.append("dn: ").append(dn).append("\n");
            ldif.append("pkdVersion: 1150").append("\n");  // ICAO PKD version
            ldif.append("userCertificate;binary:: ").append(base64Cert).append("\n");
            ldif.append("sn: ").append(serialNumber).append("\n");
            ldif.append("cn: ").append(subjectDn).append("\n");
            ldif.append("objectClass: inetOrgPerson").append("\n");
            ldif.append("objectClass: pkdDownload").append("\n");
            ldif.append("objectClass: organizationalPerson").append("\n");
            ldif.append("objectClass: top").append("\n");
            ldif.append("objectClass: person").append("\n");

            // Add pkdMasterList objectClass for CSCA
            if (certType == CertificateType.CSCA) {
                ldif.append("objectClass: pkdMasterList").append("\n");
            }

            log.debug("Converted certificate to LDIF: dn={}, type={}, size={} bytes",
                    dn, certType, certBinary.length);

            return ldif.toString();

        } catch (Exception e) {
            log.error("Failed to convert certificate to LDIF: id={}", certificate.getId(), e);
            throw new IllegalArgumentException("Failed to convert certificate to LDIF: " + e.getMessage(), e);
        }
    }

    /**
     * Convert CertificateRevocationList to LDIF format
     *
     * @param crl CRL aggregate to convert
     * @return LDIF formatted text
     * @throws IllegalArgumentException if CRL data is invalid
     */
    public String crlToLdif(CertificateRevocationList crl) {
        if (crl == null) {
            throw new IllegalArgumentException("CRL cannot be null");
        }

        try {
            // Extract CRL data
            String countryCode = crl.getCountryCode().getValue();
            String issuerName = crl.getIssuerName().getValue();
            byte[] crlBinary = crl.getX509CrlData().getCrlBinary();

            // Build DN following ICAO PKD structure:
            // cn={ISSUER-NAME},o=crl,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}
            String escapedIssuerName = escapeLdapDn(issuerName);
            String dn = String.format("cn=%s,o=crl,c=%s,dc=data,dc=download,dc=pkd,%s",
                    escapedIssuerName,
                    countryCode,
                    ldapProperties.getBase());

            // Base64 encode CRL binary
            String base64Crl = Base64.getEncoder().encodeToString(crlBinary);

            // Build LDIF entry
            StringBuilder ldif = new StringBuilder();
            ldif.append("dn: ").append(dn).append("\n");
            ldif.append("objectClass: top").append("\n");
            ldif.append("objectClass: cRLDistributionPoint").append("\n");
            ldif.append("cn: ").append(issuerName).append("\n");
            ldif.append("certificateRevocationList;binary:: ").append(base64Crl).append("\n");

            log.debug("Converted CRL to LDIF: dn={}, size={} bytes",
                    dn, crlBinary.length);

            return ldif.toString();

        } catch (Exception e) {
            log.error("Failed to convert CRL to LDIF: id={}", crl.getId(), e);
            throw new IllegalArgumentException("Failed to convert CRL to LDIF: " + e.getMessage(), e);
        }
    }

    /**
     * Convert MasterList to LDIF format following ICAO PKD structure
     *
     * <p>Master Lists are uploaded as complete CMS-signed binaries (not individual certificates).</p>
     * <p>This ensures ICAO PKD compliance for Master List storage in LDAP.</p>
     *
     * <h3>LDIF Format</h3>
     * <pre>
     * dn: cn={ESCAPED-SIGNER-DN},o=ml,c={COUNTRY},dc=ldap,dc=smartcoreinc,dc=com
     * pkdVersion: 70
     * sn: 1
     * cn: {SIGNER-DN}
     * objectClass: top
     * objectClass: person
     * objectClass: pkdMasterList
     * objectClass: pkdDownload
     * pkdMasterListContent:: {BASE64-ENCODED-CMS-BINARY}
     * </pre>
     *
     * @param masterList MasterList aggregate to convert
     * @return LDIF formatted text
     * @throws IllegalArgumentException if Master List data is invalid
     */
    public String masterListToLdif(MasterList masterList) {
        if (masterList == null) {
            throw new IllegalArgumentException("MasterList cannot be null");
        }

        try {
            // Extract Master List data
            String countryCode = masterList.getCountryCode().getValue();
            byte[] cmsBinary = masterList.getCmsBinary().getValue();
            String signerDn = masterList.getSignerDn();  // Primary CSCA DN from signer info

            // If signer DN is not available, use country code as fallback
            if (signerDn == null || signerDn.isEmpty()) {
                signerDn = String.format("CN=CSCA-%s,C=%s", countryCode, countryCode);
                log.warn("Signer DN not available for Master List id={}, using fallback: {}",
                        masterList.getId().getId(), signerDn);
            }

            // Build DN following ICAO PKD structure:
            // cn={ESCAPED-SIGNER-DN},o=ml,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}
            String escapedSignerDn = escapeLdapDn(signerDn);
            String dn = String.format("cn=%s,o=ml,c=%s,dc=data,dc=download,dc=pkd,%s",
                    escapedSignerDn,
                    countryCode,
                    ldapProperties.getBase());

            // Base64 encode CMS binary
            String base64Cms = Base64.getEncoder().encodeToString(cmsBinary);

            // Build LDIF entry following ICAO PKD Master List format
            StringBuilder ldif = new StringBuilder();
            ldif.append("dn: ").append(dn).append("\n");
            ldif.append("pkdVersion: 70").append("\n");  // Master List PKD version
            ldif.append("sn: 1").append("\n");  // Serial number (always 1 for Master List)
            ldif.append("cn: ").append(signerDn).append("\n");
            ldif.append("objectClass: top").append("\n");
            ldif.append("objectClass: person").append("\n");
            ldif.append("objectClass: pkdMasterList").append("\n");
            ldif.append("objectClass: pkdDownload").append("\n");
            ldif.append("pkdMasterListContent:: ").append(base64Cms).append("\n");

            log.debug("Converted Master List to LDIF: dn={}, country={}, size={} bytes, cscaCount={}",
                    dn, countryCode, cmsBinary.length, masterList.getCscaCount());

            return ldif.toString();

        } catch (Exception e) {
            log.error("Failed to convert Master List to LDIF: id={}", masterList.getId(), e);
            throw new IllegalArgumentException("Failed to convert Master List to LDIF: " + e.getMessage(), e);
        }
    }

    /**
     * Escape LDAP DN special characters
     *
     * <p>LDAP DN special characters that need escaping: , = + < > # ; \ "</p>
     * <p>Example: "OU=Test,O=Org" → "OU\=Test\,O\=Org"</p>
     *
     * @param dn Original DN string
     * @return Escaped DN string
     */
    private String escapeLdapDn(String dn) {
        if (dn == null || dn.isEmpty()) {
            return dn;
        }

        return dn
                .replace("\\", "\\\\")  // Backslash must be escaped first
                .replace(",", "\\,")
                .replace("=", "\\=")
                .replace("+", "\\+")
                .replace("<", "\\<")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace(";", "\\;")
                .replace("\"", "\\\"");
    }
}
