package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.files.FileNativeProviderSetupResponse;
import com.massimotter.weave.backend.model.files.FileSetupCredentialListResponse;
import com.massimotter.weave.backend.model.files.FileSetupCredentialRequest;
import com.massimotter.weave.backend.model.files.FileSetupCredentialResponse;
import com.massimotter.weave.backend.service.FilesFacadeService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Historical public DAV setup surface for explicitly opted-in compatibility environments. */
@RestController
@Hidden
@Validated
@ConditionalOnProperty(name = "weave.compatibility.public-dav.enabled", havingValue = "true")
public class FilesDavSetupController {

    private final FilesFacadeService filesFacadeService;

    public FilesDavSetupController(FilesFacadeService filesFacadeService) {
        this.filesFacadeService = filesFacadeService;
    }

    @GetMapping("/api/files/native-provider-setup")
    public FileNativeProviderSetupResponse nativeProviderSetup(@AuthenticationPrincipal Jwt jwt) {
        return filesFacadeService.nativeProviderSetup(jwt);
    }

    @GetMapping("/api/files/client-setup/credentials")
    public FileSetupCredentialListResponse setupCredentials() {
        return filesFacadeService.setupCredentials();
    }

    @PostMapping("/api/files/client-setup/credentials")
    public FileSetupCredentialResponse createSetupCredential(@Valid @RequestBody FileSetupCredentialRequest request) {
        return filesFacadeService.createSetupCredential(request);
    }

    @DeleteMapping("/api/files/client-setup/credentials/{credentialId}")
    public FileSetupCredentialResponse revokeSetupCredential(@PathVariable @Size(max = 128) String credentialId) {
        return filesFacadeService.revokeSetupCredential(credentialId);
    }
}
