package com.smartcoreinc.localpkd.testutil;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Collection;

/**
 * Utility to extract DSC certificate from SOD file for test data.
 */
public class ExtractDscFromSod {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java ExtractDscFromSod <sod-file-path>");
            System.exit(1);
        }

        String sodPath = args[0];
        System.out.println("📄 Extracting DSC from SOD: " + sodPath);

        // Read SOD file
        byte[] sodBytes = Files.readAllBytes(Paths.get(sodPath));
        System.out.println("✅ Read " + sodBytes.length + " bytes from SOD");

        // Unwrap ICAO 9303 Tag 0x77 if present
        byte[] cmsBytes = unwrapIcaoSod(sodBytes);
        System.out.println("✅ Unwrapped ICAO Tag 0x77, CMS content: " + cmsBytes.length + " bytes");

        // Parse CMS SignedData
        CMSSignedData cmsSignedData = new CMSSignedData(cmsBytes);
        System.out.println("✅ Parsed CMS SignedData");

        // Extract certificates
        Store<X509CertificateHolder> certStore = cmsSignedData.getCertificates();
        Collection<X509CertificateHolder> certHolders = certStore.getMatches(null);

        if (certHolders.isEmpty()) {
            System.err.println("❌ No certificates found in SOD");
            System.exit(1);
        }

        // Get first certificate (DSC)
        X509CertificateHolder certHolder = certHolders.iterator().next();
        X509Certificate dscCert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);

        System.out.println("\n📜 DSC Certificate Details:");
        System.out.println("Subject: " + dscCert.getSubjectX500Principal());
        System.out.println("Issuer: " + dscCert.getIssuerX500Principal());
        System.out.println("Serial: " + dscCert.getSerialNumber().toString(16).toUpperCase());
        System.out.println("Valid From: " + dscCert.getNotBefore());
        System.out.println("Valid To: " + dscCert.getNotAfter());

        // Save to PEM file
        String outputPath = "src/test/resources/test-data/certificates/korean-dsc.pem";
        try (PemWriter pemWriter = new PemWriter(new FileWriter(outputPath))) {
            pemWriter.writeObject(new PemObject("CERTIFICATE", dscCert.getEncoded()));
        }
        System.out.println("\n✅ DSC saved to: " + outputPath);

        // Verify DSC issuer matches CSCA subject
        System.out.println("\n🔍 Verification:");
        System.out.println("DSC Issuer DN: " + dscCert.getIssuerX500Principal());
        System.out.println("Expected CSCA Subject DN: C=KR,O=Government,OU=MOFA,CN=CSCA003");

        String issuerDN = dscCert.getIssuerX500Principal().getName();
        if (issuerDN.contains("CN=CSCA003") && issuerDN.contains("C=KR")) {
            System.out.println("✅ DSC is signed by CSCA003!");
        } else {
            System.out.println("⚠️ DSC issuer does not match expected CSCA");
        }
    }

    /**
     * Unwraps ICAO 9303 Tag 0x77 wrapper from SOD if present.
     */
    private static byte[] unwrapIcaoSod(byte[] sodBytes) {
        if ((sodBytes[0] & 0xFF) != 0x77) {
            return sodBytes; // No wrapper
        }

        int offset = 1; // Skip tag
        int lengthByte = sodBytes[offset++] & 0xFF;

        if ((lengthByte & 0x80) != 0) {
            // Long form: skip additional octets
            int numOctets = lengthByte & 0x7F;
            offset += numOctets;
        }

        // Extract CMS content
        byte[] cmsBytes = new byte[sodBytes.length - offset];
        System.arraycopy(sodBytes, offset, cmsBytes, 0, cmsBytes.length);
        return cmsBytes;
    }
}
