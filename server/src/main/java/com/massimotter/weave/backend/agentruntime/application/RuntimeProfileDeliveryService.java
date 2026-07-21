package com.massimotter.weave.backend.agentruntime.application;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadPrincipal;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.InvalidRuntimeProfileException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileVerifier;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

public final class RuntimeProfileDeliveryService {
    private static final Pattern PROFILE_HASH = Pattern.compile("sha256:[a-f0-9]{64}");

    private final RuntimeProfileRepository profiles;
    private final RuntimeProfileVerifier verifier;
    private final RuntimeGovernanceRepository governance;
    private final Clock clock;

    public RuntimeProfileDeliveryService(
            RuntimeProfileRepository profiles,
            RuntimeProfileVerifier verifier,
            RuntimeGovernanceRepository governance,
            Clock clock) {
        if (profiles == null || verifier == null || governance == null || clock == null) {
            throw new IllegalArgumentException("RuntimeProfile delivery dependencies are required");
        }
        this.profiles = profiles;
        this.verifier = verifier;
        this.governance = governance;
        this.clock = clock;
    }

    public Optional<SignedRuntimeProfile> findCurrent(
            String profileHash,
            RuntimeWorkloadPrincipal principal) {
        if (profileHash == null || !PROFILE_HASH.matcher(profileHash).matches() || principal == null) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        return profiles.findCurrentForWorkload(
                        profileHash,
                        principal.issuer(),
                        principal.subject(),
                        principal.clientId(),
                        now)
                .filter(envelope -> profileHash.equals(envelope.profileHash()))
                .filter(envelope -> verifiesFor(envelope, principal, now));
    }

    private boolean verifiesFor(
            SignedRuntimeProfile envelope,
            RuntimeWorkloadPrincipal principal,
            Instant now) {
        try {
            RuntimeProfile profile = verifier.verify(envelope, now);
            RuntimeProfile.WorkloadIdentity workload = profile.workloadIdentity();
            return workload.issuer().equals(principal.issuer())
                    && workload.subject().equals(principal.subject())
                    && workload.clientId().equals(principal.clientId())
                    && governance.findEffectiveRevision(
                                    profile.organizationRef(), profile.personRef(),
                                    profile.entitlementRevision(), now)
                            .filter(entitlement -> entitlement.memberBinding().equals(profile.memberBinding()))
                            .isPresent();
        } catch (InvalidRuntimeProfileException | IllegalArgumentException exception) {
            return false;
        }
    }

}
