package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;

class RunnerCertificateFingerprintExtractorTest {

    @Test
    void derivesCanonicalSha256FingerprintFromEncodedCertificate() throws Exception {
        byte[] encoded = "runner-certificate".getBytes(StandardCharsets.UTF_8);
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getEncoded()).thenReturn(encoded);

        Object principal = new RunnerCertificateFingerprintExtractor()
                .extractPrincipal(certificate);

        String expected = "sha256:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(encoded));
        assertThat(principal).isEqualTo(expected);
    }

    @Test
    void encodingFailureFailsClosedWithoutFallingBackToSubjectText() throws Exception {
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getEncoded()).thenThrow(new CertificateEncodingException("broken"));

        assertThatThrownBy(() -> new RunnerCertificateFingerprintExtractor()
                        .extractPrincipal(certificate))
                .isInstanceOf(AuthenticationServiceException.class)
                .hasMessage("Runner client certificate could not be fingerprinted.");
    }
}
