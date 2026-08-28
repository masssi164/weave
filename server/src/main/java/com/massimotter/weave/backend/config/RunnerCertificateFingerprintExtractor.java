package com.massimotter.weave.backend.config;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.web.authentication.preauth.x509.X509PrincipalExtractor;

/** Derives the persisted Runner identity coordinate from the actual client certificate bytes. */
public final class RunnerCertificateFingerprintExtractor implements X509PrincipalExtractor {

    @Override
    public Object extractPrincipal(X509Certificate certificate) {
        X509Certificate value = Objects.requireNonNull(certificate, "certificate");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getEncoded());
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (CertificateEncodingException failure) {
            throw new AuthenticationServiceException(
                    "Runner client certificate could not be fingerprinted.",
                    failure);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
