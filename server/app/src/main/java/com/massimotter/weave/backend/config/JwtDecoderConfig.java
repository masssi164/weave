package com.massimotter.weave.backend.config;

import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.configuredDecoder;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.exactAudienceValidator;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.requiredAuthorizedPartyValidator;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.requiredHumanSubjectValidator;

import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;

import com.massimotter.weave.backend.security.MemberSessionRevocationService;
import com.massimotter.weave.backend.security.MemberSessionCutoffService;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.util.StringUtils;

@Configuration
public class JwtDecoderConfig {

    @Bean("jwtDecoder")
    @Primary
    JwtDecoder memberJwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            ObjectProvider<MemberSessionRevocationService> memberSessions,
            ObjectProvider<MemberSessionCutoffService> memberCutoffs) {
        return withMemberSessionRevocation(
                withMemberCutoff(jwtDecoder(resourceServerProperties, weaveSecurityProperties),
                        memberCutoffs::getObject), memberSessions::getObject);
    }

    // Direct decoder contract tests exercise the existing per-session boundary.
    JwtDecoder memberJwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            ObjectProvider<MemberSessionRevocationService> memberSessions) {
        return withMemberSessionRevocation(
                jwtDecoder(resourceServerProperties, weaveSecurityProperties), memberSessions::getObject);
    }

    static JwtDecoder withMemberCutoff(
            JwtDecoder decoder, Supplier<MemberSessionCutoffService> memberCutoffs) {
        return token -> {
            Jwt jwt = decoder.decode(token);
            if (memberCutoffs.get().revoked(jwt)) {
                throw new BadJwtException("The Weave member session was revoked.");
            }
            return jwt;
        };
    }

    static JwtDecoder withMemberSessionRevocation(
            JwtDecoder decoder, Supplier<MemberSessionRevocationService> memberSessions) {
        return token -> {
            Jwt jwt = decoder.decode(token);
            // Resolve only after signature/claim validation, so contract export
            // loads no optional domain runtime through authentication.
            MemberSessionRevocationService sessions = memberSessions.get();
            if (sessions.revoked(jwt)) {
                // Retain the longest expiry when another already-issued bearer
                // from the same revoked OIDC session is presented.
                sessions.revoke(jwt);
                throw new BadJwtException("The Weave member session was revoked.");
            }
            return jwt;
        };
    }

    JwtDecoder jwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties) {
        String issuerUri = resourceServerProperties.getJwt().getIssuerUri();
        if (!StringUtils.hasText(issuerUri)) {
            return configuredDecoder(resourceServerProperties, jwt -> OAuth2TokenValidatorResult.success());
        }
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                requiredHumanSubjectValidator());
        if (weaveSecurityProperties.hasRequiredAudience()) {
            validator = new DelegatingOAuth2TokenValidator<>(
                    validator,
                    exactAudienceValidator(Set.of(weaveSecurityProperties.requiredAudience())));
        }
        if (weaveSecurityProperties.hasRequiredAuthorizedParty()) {
            validator = new DelegatingOAuth2TokenValidator<>(
                    validator,
                    requiredAuthorizedPartyValidator(weaveSecurityProperties.requiredAuthorizedParty()));
        }
        return configuredDecoder(resourceServerProperties, validator);
    }

    @Bean("adminApiJwtDecoder")
    JwtDecoder adminApiJwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            ObjectProvider<MemberSessionCutoffService> memberCutoffs) {
        return withMemberCutoff(adminApiValidatedDecoder(resourceServerProperties, weaveSecurityProperties),
                memberCutoffs::getObject);
    }

    JwtDecoder adminApiJwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties) {
        return adminApiValidatedDecoder(resourceServerProperties, weaveSecurityProperties);
    }

    private JwtDecoder adminApiValidatedDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties) {
        String issuerUri = resourceServerProperties.getJwt().getIssuerUri();
        if (!StringUtils.hasText(issuerUri)) {
            return configuredDecoder(resourceServerProperties, jwt -> OAuth2TokenValidatorResult.success());
        }
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                requiredHumanSubjectValidator(),
                exactAudienceValidator(Set.of(weaveSecurityProperties.requiredAudience())),
                requiredAuthorizedPartyValidator("weave-admin-console"));
        return configuredDecoder(resourceServerProperties, validator);
    }





}
