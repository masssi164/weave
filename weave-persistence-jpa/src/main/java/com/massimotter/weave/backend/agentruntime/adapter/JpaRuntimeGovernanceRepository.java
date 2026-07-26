package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.*;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.*;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public class JpaRuntimeGovernanceRepository implements RuntimeGovernanceRepository {
  private static final String DOMAIN = "weave.agent-runtime.entitlement/v1";
  private final RuntimeEntitlementJpaRepository entitlements;
  private final RuntimeRevocationJpaRepository revocations;
  private final RuntimeAuditCorrelationJpaRepository correlations;

  public JpaRuntimeGovernanceRepository(
      RuntimeEntitlementJpaRepository e,
      RuntimeRevocationJpaRepository r,
      RuntimeAuditCorrelationJpaRepository c) {
    entitlements = e;
    revocations = r;
    correlations = c;
  }

  @Override
  @Transactional
  public RuntimeEntitlementRef activate(
      RuntimeEntitlementObservation o, String activation, String audit, Instant now) {
    Objects.requireNonNull(o);
    text(activation);
    text(audit);
    Objects.requireNonNull(now);
    if (o.observedAt().isAfter(now.plusSeconds(1)))
      throw new IllegalArgumentException("future entitlement observations are rejected");
    var reusable =
        entitlements
            .findFirstByOrganizationRefAndPersonRefAndMemberIssuerAndMemberSubjectAndSourceProviderAndSourceGroupRefAndCapabilityRevisionAndEntitlementStateOrderByLastObservedAtDescCreatedAtDesc(
                o.organizationRef(),
                o.personRef(),
                o.memberBinding().issuer(),
                o.memberBinding().subject(),
                o.sourceProvider(),
                o.sourceGroupRef(),
                o.capabilityRevision(),
                "ENTITLED");
    if (reusable.isPresent()) {
      var e = reusable.orElseThrow();
      e.observe(o.observedAt(), o.expiresAt(), audit, now);
      return map(entitlements.saveAndFlush(e));
    }
    RuntimeEntitlementRef proposed = proposed(o, activation, audit, now);
    var existing = entitlements.findByEntitlementRef(proposed.entitlementRef());
    if (existing.isPresent()) return same(proposed, map(existing.orElseThrow()));
    try {
      return map(entitlements.saveAndFlush(entity(proposed)));
    } catch (DataIntegrityViolationException conflict) {
      return same(
          proposed,
          map(
              entitlements
                  .findByEntitlementRef(proposed.entitlementRef())
                  .orElseThrow(() -> conflict)));
    }
  }

  @Override
  public Optional<RuntimeEntitlementRef> findCurrent(String org, String person) {
    text(org);
    text(person);
    return entitlements
        .findFirstByOrganizationRefAndPersonRefAndEntitlementStateOrderByLastObservedAtDescCreatedAtDescEntitlementRefDesc(
            org, person, "ENTITLED")
        .or(
            () ->
                entitlements
                    .findFirstByOrganizationRefAndPersonRefOrderByLastObservedAtDescCreatedAtDescEntitlementRefDesc(
                        org, person))
        .map(JpaRuntimeGovernanceRepository::map);
  }

  @Override
  public Optional<RuntimeEntitlementRef> findRevision(String org, String person, String revision) {
    text(org);
    text(person);
    text(revision);
    return entitlements
        .findByOrganizationRefAndPersonRefAndEntitlementRevision(org, person, revision)
        .map(JpaRuntimeGovernanceRepository::map);
  }

  @Override
  public Optional<RuntimeEntitlementRef> findEffectiveRevision(
      String org, String person, String revision, Instant now) {
    text(org);
    text(person);
    text(revision);
    return entitlements
        .findEffective(org, person, revision, now)
        .map(JpaRuntimeGovernanceRepository::map);
  }

  @Override
  @Transactional
  public RuntimeRevocation revoke(
      RuntimeEntitlementRef entitlement,
      String cell,
      String profile,
      String workload,
      String code,
      String reason,
      String actor,
      String ref,
      String audit,
      Instant now) {
    RuntimeRevocation p =
        new RuntimeRevocation(
            UUID.randomUUID(),
            ref,
            entitlement.organizationRef(),
            entitlement.personRef(),
            code,
            reason,
            actor,
            now,
            entitlement.entitlementRef(),
            entitlement.entitlementRevision(),
            cell,
            profile,
            workload,
            audit,
            now);
    var existing = revocations.findByRevocationRef(ref);
    if (existing.isPresent()) return same(p, map(existing.orElseThrow()));
    try {
      var stored = entitlements.lockByRef(entitlement.entitlementRef()).orElseThrow();
      var saved = revocations.saveAndFlush(entity(p));
      stored.revoke(ref, now, audit, now);
      entitlements.saveAndFlush(stored);
      return map(saved);
    } catch (DataIntegrityViolationException conflict) {
      return same(p, map(revocations.findByRevocationRef(ref).orElseThrow(() -> conflict)));
    }
  }

  @Override
  @Transactional
  public RuntimeAuditCorrelation appendCorrelation(RuntimeAuditCorrelation c) {
    var existing = correlations.findByCorrelationRef(c.correlationRef());
    if (existing.isPresent()) return same(c, map(existing.orElseThrow()));
    try {
      return map(correlations.saveAndFlush(entity(c)));
    } catch (DataIntegrityViolationException conflict) {
      return same(
          c,
          map(correlations.findByCorrelationRef(c.correlationRef()).orElseThrow(() -> conflict)));
    }
  }

  private static RuntimeEntitlementRef proposed(
      RuntimeEntitlementObservation o, String activation, String audit, Instant now) {
    String material =
        o.organizationRef()
            + "\0"
            + o.personRef()
            + "\0"
            + o.memberBinding().issuer()
            + "\0"
            + o.memberBinding().subject()
            + "\0"
            + o.sourceProvider()
            + "\0"
            + o.sourceGroupRef()
            + "\0"
            + o.capabilityRevision()
            + "\0"
            + activation;
    String ref = "entitlement:" + RuntimeWorkloadOwnership.fingerprint(material).substring(7);
    String rev = RuntimeWorkloadOwnership.fingerprint(DOMAIN + "\0" + material);
    return new RuntimeEntitlementRef(
        UUID.randomUUID(),
        ref,
        rev,
        o.organizationRef(),
        o.personRef(),
        o.memberBinding(),
        o.sourceProvider(),
        o.sourceGroupRef(),
        o.capabilityRevision(),
        RuntimeEntitlementState.ENTITLED,
        o.observedAt(),
        o.observedAt(),
        o.expiresAt(),
        null,
        null,
        audit,
        now,
        now);
  }

  private static RuntimeEntitlementEntity entity(RuntimeEntitlementRef e) {
    return new RuntimeEntitlementEntity(
        e.recordId(),
        e.entitlementRef(),
        e.entitlementRevision(),
        e.organizationRef(),
        e.personRef(),
        e.memberBinding().issuer(),
        e.memberBinding().subject(),
        e.sourceProvider(),
        e.sourceGroupRef(),
        e.capabilityRevision(),
        e.state().name(),
        e.effectiveAt(),
        e.lastObservedAt(),
        e.expiresAt(),
        e.revocationRef(),
        e.revokedAt(),
        e.auditRef(),
        e.createdAt(),
        e.updatedAt());
  }

  private static RuntimeEntitlementRef map(RuntimeEntitlementEntity e) {
    return new RuntimeEntitlementRef(
        e.recordId(),
        e.entitlementRef(),
        e.entitlementRevision(),
        e.organizationRef(),
        e.personRef(),
        new RuntimeMemberBinding(e.memberIssuer(), e.memberSubject()),
        e.sourceProvider(),
        e.sourceGroupRef(),
        e.capabilityRevision(),
        RuntimeEntitlementState.valueOf(e.entitlementState()),
        e.effectiveAt(),
        e.lastObservedAt(),
        e.expiresAt(),
        e.revocationRef(),
        e.revokedAt(),
        e.auditRef(),
        e.createdAt(),
        e.updatedAt());
  }

  private static RuntimeRevocationEntity entity(RuntimeRevocation r) {
    return new RuntimeRevocationEntity(
        r.recordId(),
        r.revocationRef(),
        r.organizationRef(),
        r.personRef(),
        r.reasonCode(),
        r.reasonRefHash(),
        r.actorRefHash(),
        r.effectiveAt(),
        r.entitlementRef(),
        r.entitlementRevision(),
        r.cellRef(),
        r.profileHash(),
        r.workloadRefHash(),
        r.auditCorrelationRef(),
        r.createdAt());
  }

  private static RuntimeRevocation map(RuntimeRevocationEntity r) {
    return new RuntimeRevocation(
        r.recordId(),
        r.revocationRef(),
        r.organizationRef(),
        r.personRef(),
        r.reasonCode(),
        r.reasonRefHash(),
        r.actorRefHash(),
        r.effectiveAt(),
        r.entitlementRef(),
        r.entitlementRevision(),
        r.cellRef(),
        r.profileHash(),
        r.workloadRefHash(),
        r.auditCorrelationRef(),
        r.createdAt());
  }

  private static RuntimeAuditCorrelationEntity entity(RuntimeAuditCorrelation c) {
    return new RuntimeAuditCorrelationEntity(
        c.recordId(),
        c.correlationRef(),
        c.organizationRefHash(),
        c.personRefHash(),
        c.keycloakRefHash(),
        c.orchestratorRefHash(),
        c.openClawRefHash(),
        c.matrixRefHash(),
        c.mcpRefHash(),
        c.domainAuditRefHash(),
        c.occurredAt(),
        c.createdAt());
  }

  private static RuntimeAuditCorrelation map(RuntimeAuditCorrelationEntity c) {
    return new RuntimeAuditCorrelation(
        c.recordId(),
        c.correlationRef(),
        c.organizationRefHash(),
        c.personRefHash(),
        c.keycloakRefHash(),
        c.orchestratorRefHash(),
        c.openclawRefHash(),
        c.matrixRefHash(),
        c.mcpRefHash(),
        c.domainAuditRefHash(),
        c.occurredAt(),
        c.createdAt());
  }

  private static RuntimeEntitlementRef same(RuntimeEntitlementRef a, RuntimeEntitlementRef b) {
    if (!a.entitlementRef().equals(b.entitlementRef())
        || !a.entitlementRevision().equals(b.entitlementRevision())
        || b.state() != RuntimeEntitlementState.ENTITLED)
      throw new IllegalStateException(
          "entitlement reference is bound to different authority evidence");
    return b;
  }

  private static RuntimeRevocation same(RuntimeRevocation a, RuntimeRevocation b) {
    if (!a.revocationRef().equals(b.revocationRef())
        || !a.entitlementRevision().equals(b.entitlementRevision())
        || !a.auditCorrelationRef().equals(b.auditCorrelationRef()))
      throw new IllegalStateException("revocation reference is bound to different evidence");
    return b;
  }

  private static RuntimeAuditCorrelation same(
      RuntimeAuditCorrelation a, RuntimeAuditCorrelation b) {
    if (!a.correlationRef().equals(b.correlationRef())
        || !a.organizationRefHash().equals(b.organizationRefHash())
        || !a.personRefHash().equals(b.personRefHash())
        || !Objects.equals(a.keycloakRefHash(), b.keycloakRefHash())
        || !Objects.equals(a.orchestratorRefHash(), b.orchestratorRefHash())
        || !Objects.equals(a.openClawRefHash(), b.openClawRefHash())
        || !Objects.equals(a.matrixRefHash(), b.matrixRefHash())
        || !Objects.equals(a.mcpRefHash(), b.mcpRefHash())
        || !Objects.equals(a.domainAuditRefHash(), b.domainAuditRefHash()))
      throw new IllegalStateException("audit correlation reference is bound to different evidence");
    return b;
  }

  private static void text(String s) {
    if (s == null || s.isBlank() || s.length() > 255)
      throw new IllegalArgumentException("required bounded value");
  }
}
