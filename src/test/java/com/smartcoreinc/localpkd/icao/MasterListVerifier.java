package com.smartcoreinc.localpkd.icao;

import java.io.FileInputStream;
import java.security.Security;
import java.util.Collection;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;

public class MasterListVerifier {
    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // Locad MasterList file
        FileInputStream fis = new FileInputStream("ICAO_ml_01April2025.ml");
        CMSSignedData signedData = new CMSSignedData(fis);

        // Extract signer infos
        // 서명자 정보를 가져옴
        SignerInformationStore signers = signedData.getSignerInfos();
        Collection<SignerInformation> signerInfos = signers.getSigners();

        // Extract all certs in CMS (including singer certs)
        Store<X509CertificateHolder> certStore = signedData.getCertificates();

        for (SignerInformation signer: signerInfos) {
            // 서명자 식별자
            SignerId signerId = signer.getSID();
            // 해당 SID와 일치하는 인증서 찾기.
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> certCollection = certStore.getMatches(signerId);
            if (certCollection.isEmpty()) {
                System.out.println("⚠️ 서명자 인증서를 찾을 수 없습니다.");
                continue;
            }

            X509CertificateHolder signerCertHolder = certCollection.iterator().next();

            // Verifiy signature(실제 서명 검증)
            boolean signatureValid = signer.verify(new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider("BC")
                .build(signerCertHolder));
            
            System.out.println("🔏 서명자 Subject: " + signerCertHolder.getSubject());
            System.out.println("✔️ 서명 유효 여부: " + signatureValid);    
        }

    }
}
