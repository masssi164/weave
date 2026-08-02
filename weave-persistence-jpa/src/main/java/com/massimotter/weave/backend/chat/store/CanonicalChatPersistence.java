package com.massimotter.weave.backend.chat.store;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Adapter-private relational model for canonical Chat.
 *
 * <p>The domain model deliberately has no JPA annotations. These entities own the code-first
 * schema and expose only aggregate operations needed by the canonical store.
 */
final class CanonicalChatPersistence {
  private CanonicalChatPersistence() {}

  static OffsetDateTime utc(Instant value) {
    return value == null ? null : value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
  }
}

@Embeddable
class ChatPairId implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "unused_part_1")
  private String part1;

  @Column(name = "unused_part_2")
  private String part2;

  protected ChatPairId() {}

  ChatPairId(String part1, String part2) {
    this.part1 = Objects.requireNonNull(part1, "part1");
    this.part2 = Objects.requireNonNull(part2, "part2");
  }

  String part1() {
    return part1;
  }

  String part2() {
    return part2;
  }

  @Override
  public boolean equals(Object candidate) {
    return this == candidate
        || candidate instanceof ChatPairId other
            && Objects.equals(part1, other.part1)
            && Objects.equals(part2, other.part2);
  }

  @Override
  public int hashCode() {
    return Objects.hash(part1, part2);
  }
}

@Embeddable
class ChatTripleId implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "unused_part_1")
  private String part1;

  @Column(name = "unused_part_2")
  private String part2;

  @Column(name = "unused_part_3")
  private String part3;

  protected ChatTripleId() {}

  ChatTripleId(String part1, String part2, String part3) {
    this.part1 = Objects.requireNonNull(part1, "part1");
    this.part2 = Objects.requireNonNull(part2, "part2");
    this.part3 = Objects.requireNonNull(part3, "part3");
  }

  String part1() {
    return part1;
  }

  String part2() {
    return part2;
  }

  String part3() {
    return part3;
  }

  @Override
  public boolean equals(Object candidate) {
    return this == candidate
        || candidate instanceof ChatTripleId other
            && Objects.equals(part1, other.part1)
            && Objects.equals(part2, other.part2)
            && Objects.equals(part3, other.part3);
  }

  @Override
  public int hashCode() {
    return Objects.hash(part1, part2, part3);
  }
}

@Embeddable
class ChatQuadId implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "unused_part_1")
  private String part1;

  @Column(name = "unused_part_2")
  private String part2;

  @Column(name = "unused_part_3")
  private String part3;

  @Column(name = "unused_part_4")
  private String part4;

  protected ChatQuadId() {}

  ChatQuadId(String part1, String part2, String part3, String part4) {
    this.part1 = Objects.requireNonNull(part1, "part1");
    this.part2 = Objects.requireNonNull(part2, "part2");
    this.part3 = Objects.requireNonNull(part3, "part3");
    this.part4 = Objects.requireNonNull(part4, "part4");
  }

  String part1() {
    return part1;
  }

  String part2() {
    return part2;
  }

  String part3() {
    return part3;
  }

  String part4() {
    return part4;
  }

  @Override
  public boolean equals(Object candidate) {
    return this == candidate
        || candidate instanceof ChatQuadId other
            && Objects.equals(part1, other.part1)
            && Objects.equals(part2, other.part2)
            && Objects.equals(part3, other.part3)
            && Objects.equals(part4, other.part4);
  }

  @Override
  public int hashCode() {
    return Objects.hash(part1, part2, part3, part4);
  }
}

@Entity
@Table(name = "weave_chat_conversations")
class ChatConversationJpaEntity {
  @EmbeddedId
  @AttributeOverrides({
    @AttributeOverride(
        name = "part1",
        column = @Column(name = "tenant_id", nullable = false, length = 160)),
    @AttributeOverride(
        name = "part2",
        column = @Column(name = "conversation_id", nullable = false, length = 160))
  })
  private ChatPairId id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "context_id", nullable = false, length = 160, updatable = false)
  private String contextId;

  @Column(name = "title", nullable = false, length = 512)
  private String title;

  @Column(name = "conversation_kind", nullable = false, length = 64, updatable = false)
  private String kind;

  @Column(name = "open_to_workspace", nullable = false)
  private boolean openToWorkspace;

  @Column(name = "lifecycle_state", nullable = false, length = 32)
  private String lifecycleState;

  @Column(name = "encryption_mode", nullable = false, length = 128)
  private String encryptionMode;

  @Column(name = "created_at_utc", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at_utc", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "next_event_sequence", nullable = false)
  private long nextEventSequence;

  protected ChatConversationJpaEntity() {}

  static ChatConversationJpaEntity pending(
      String tenantId,
      String contextId,
      String conversationId,
      String title,
      String kind,
      String encryptionMode,
      Instant now) {
    ChatConversationJpaEntity entity = new ChatConversationJpaEntity();
    entity.id = new ChatPairId(tenantId, conversationId);
    entity.contextId = contextId;
    entity.title = title;
    entity.kind = kind;
    entity.lifecycleState = "pending";
    entity.encryptionMode = encryptionMode;
    entity.createdAt = CanonicalChatPersistence.utc(now);
    entity.updatedAt = CanonicalChatPersistence.utc(now);
    return entity;
  }

  String tenantId() {
    return id.part1();
  }

  String conversationId() {
    return id.part2();
  }

  String contextId() {
    return contextId;
  }

  String title() {
    return title;
  }

  String kind() {
    return kind;
  }

  boolean openToWorkspace() {
    return openToWorkspace;
  }

  String lifecycleState() {
    return lifecycleState;
  }

  String encryptionMode() {
    return encryptionMode;
  }

  Instant updatedAt() {
    return updatedAt.toInstant();
  }

  void commit(Instant now) {
    lifecycleState = "committed";
    updatedAt = CanonicalChatPersistence.utc(now);
  }

  void enableEncryption(String algorithm, Instant now) {
    if (!"unencrypted".equals(encryptionMode) && !algorithm.equals(encryptionMode)) {
      throw new IllegalStateException("canonical Chat encryption state changed concurrently");
    }
    encryptionMode = algorithm;
    updatedAt = CanonicalChatPersistence.utc(now);
  }

  long allocateEventSequence() {
    nextEventSequence = Math.addExact(nextEventSequence, 1L);
    return nextEventSequence;
  }
}

@Entity
@Table(name = "weave_chat_memberships")
class ChatMembershipJpaEntity {
  @EmbeddedId
  @AttributeOverrides({
    @AttributeOverride(
        name = "part1",
        column = @Column(name = "tenant_id", nullable = false, length = 160)),
    @AttributeOverride(
        name = "part2",
        column = @Column(name = "conversation_id", nullable = false, length = 160)),
    @AttributeOverride(
        name = "part3",
        column = @Column(name = "identity_issuer", nullable = false, length = 512)),
    @AttributeOverride(
        name = "part4",
        column = @Column(name = "actor_ref", nullable = false, length = 255))
  })
  private ChatQuadId id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "member_role", nullable = false, length = 32)
  private String role;

  @Column(name = "membership_state", nullable = false, length = 32)
  private String state;

  @Column(name = "invited_at_utc")
  private OffsetDateTime invitedAt;

  @Column(name = "joined_at_utc")
  private OffsetDateTime joinedAt;

  @Column(name = "updated_at_utc", nullable = false)
  private OffsetDateTime updatedAt;

  protected ChatMembershipJpaEntity() {}

  static ChatMembershipJpaEntity create(
      String tenantId,
      String conversationId,
      String identityIssuer,
      String actorRef,
      String state,
      Instant invitedAt,
      Instant joinedAt) {
    ChatMembershipJpaEntity entity = new ChatMembershipJpaEntity();
    entity.id = new ChatQuadId(tenantId, conversationId, identityIssuer, actorRef);
    entity.role = "member";
    entity.state = state;
    entity.invitedAt = CanonicalChatPersistence.utc(invitedAt);
    entity.joinedAt = CanonicalChatPersistence.utc(joinedAt);
    entity.updatedAt = CanonicalChatPersistence.utc(joinedAt == null ? invitedAt : joinedAt);
    return entity;
  }

  String tenantId() {
    return id.part1();
  }

  String conversationId() {
    return id.part2();
  }

  String identityIssuer() {
    return id.part3();
  }

  String actorRef() {
    return id.part4();
  }

  String role() {
    return role;
  }

  String state() {
    return state;
  }

  Instant joinedAt() {
    return joinedAt == null ? Instant.EPOCH : joinedAt.toInstant();
  }

  void transition(String targetState, Instant now) {
    state = targetState;
    joinedAt = "joined".equals(targetState) ? CanonicalChatPersistence.utc(now) : null;
    updatedAt = CanonicalChatPersistence.utc(now);
  }
}

@Entity
@Table(
    name = "weave_chat_events",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_weave_chat_events_conversation_sequence",
            columnNames = {"tenant_id", "conversation_id", "sequence_value"}))
class ChatEventJpaEntity {
  @EmbeddedId
  @AttributeOverrides({
    @AttributeOverride(
        name = "part1",
        column = @Column(name = "tenant_id", nullable = false, length = 160)),
    @AttributeOverride(
        name = "part2",
        column = @Column(name = "conversation_id", nullable = false, length = 160)),
    @AttributeOverride(
        name = "part3",
        column = @Column(name = "event_id", nullable = false, length = 255))
  })
  private ChatTripleId id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "sequence_value", nullable = false, updatable = false)
  private long sequence;

  @Column(name = "sender_issuer", nullable = false, length = 512, updatable = false)
  private String senderIssuer;

  @Column(name = "sender_ref", nullable = false, length = 255, updatable = false)
  private String senderRef;

  @Column(name = "event_kind", nullable = false, length = 32, updatable = false)
  private String kind;

  @Column(name = "content_json", nullable = false, updatable = false, length = Integer.MAX_VALUE)
  private String contentJson;

  @Column(name = "occurred_at_utc", nullable = false, updatable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "delivery_state", nullable = false, length = 32)
  private String deliveryState;

  @Column(name = "redacted", nullable = false)
  private boolean redacted;

  protected ChatEventJpaEntity() {}

  static ChatEventJpaEntity create(
      String tenantId,
      String conversationId,
      String eventId,
      long sequence,
      String senderIssuer,
      String senderRef,
      String kind,
      String contentJson,
      Instant occurredAt,
      String deliveryState) {
    ChatEventJpaEntity entity = new ChatEventJpaEntity();
    entity.id = new ChatTripleId(tenantId, conversationId, eventId);
    entity.sequence = sequence;
    entity.senderIssuer = senderIssuer;
    entity.senderRef = senderRef;
    entity.kind = kind;
    entity.contentJson = contentJson;
    entity.occurredAt = CanonicalChatPersistence.utc(occurredAt);
    entity.deliveryState = deliveryState;
    return entity;
  }

  long sequence() {
    return sequence;
  }

  String tenantId() {
    return id.part1();
  }

  String conversationId() {
    return id.part2();
  }

  String eventId() {
    return id.part3();
  }

  String senderIssuer() {
    return senderIssuer;
  }

  String senderRef() {
    return senderRef;
  }

  String kind() {
    return kind;
  }

  String contentJson() {
    return contentJson;
  }

  Instant occurredAt() {
    return occurredAt.toInstant();
  }

  String deliveryState() {
    return deliveryState;
  }

  boolean redacted() {
    return redacted;
  }

  void commit() {
    deliveryState = "committed";
  }

  boolean redact() {
    if (redacted) {
      return false;
    }
    redacted = true;
    return true;
  }
}

@Entity
@Table(name = "weave_chat_operations")
class ChatOperationJpaEntity {
  @EmbeddedId
  @AttributeOverrides({
    @AttributeOverride(
        name = "part1",
        column = @Column(name = "tenant_id", nullable = false, length = 160)),
    @AttributeOverride(
        name = "part2",
        column = @Column(name = "operation_id", nullable = false, length = 96))
  })
  private ChatPairId id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "context_id", nullable = false, length = 160, updatable = false)
  private String contextId;

  @Column(name = "operation_type", nullable = false, length = 48, updatable = false)
  private String operationType;

  @Column(name = "identity_issuer", nullable = false, length = 512, updatable = false)
  private String identityIssuer;

  @Column(name = "actor_ref", nullable = false, length = 255, updatable = false)
  private String actorRef;

  @Column(name = "conversation_id", nullable = false, length = 160, updatable = false)
  private String conversationId;

  @Column(name = "canonical_object_id", nullable = false, length = 255, updatable = false)
  private String canonicalObjectId;

  @Column(name = "northbound_transaction_id", nullable = false, length = 160, updatable = false)
  private String northboundTransactionId;

  @Column(name = "provider_transaction_id", nullable = false, length = 160, updatable = false)
  private String providerTransactionId;

  @Column(name = "provider_alias_intent", length = 255, updatable = false)
  private String providerAliasIntent;

  @Column(name = "payload_digest", nullable = false, length = 64, updatable = false)
  private String payloadDigest;

  @Column(name = "operation_state", nullable = false, length = 32)
  private String state;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_error_code", length = 96)
  private String lastErrorCode;

  @Column(name = "created_at_utc", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at_utc", nullable = false)
  private OffsetDateTime updatedAt;

  protected ChatOperationJpaEntity() {}

  static ChatOperationJpaEntity create(
      String tenantId,
      String contextId,
      String operationId,
      String operationType,
      String identityIssuer,
      String actorRef,
      String conversationId,
      String canonicalObjectId,
      String northboundTransactionId,
      String providerTransactionId,
      String providerAliasIntent,
      String payloadDigest,
      Instant now) {
    ChatOperationJpaEntity entity = new ChatOperationJpaEntity();
    entity.id = new ChatPairId(tenantId, operationId);
    entity.contextId = contextId;
    entity.operationType = operationType;
    entity.identityIssuer = identityIssuer;
    entity.actorRef = actorRef;
    entity.conversationId = conversationId;
    entity.canonicalObjectId = canonicalObjectId;
    entity.northboundTransactionId = northboundTransactionId;
    entity.providerTransactionId = providerTransactionId;
    entity.providerAliasIntent = providerAliasIntent;
    entity.payloadDigest = payloadDigest;
    entity.state = "pending";
    entity.createdAt = CanonicalChatPersistence.utc(now);
    entity.updatedAt = CanonicalChatPersistence.utc(now);
    return entity;
  }

  String tenantId() {
    return id.part1();
  }

  String operationId() {
    return id.part2();
  }

  String operationType() {
    return operationType;
  }

  String identityIssuer() {
    return identityIssuer;
  }

  String actorRef() {
    return actorRef;
  }

  String conversationId() {
    return conversationId;
  }

  String canonicalObjectId() {
    return canonicalObjectId;
  }

  String providerTransactionId() {
    return providerTransactionId;
  }

  String providerAliasIntent() {
    return providerAliasIntent;
  }

  String payloadDigest() {
    return payloadDigest;
  }

  String state() {
    return state;
  }

  String lastErrorCode() {
    return lastErrorCode;
  }

  void acknowledge(Instant now) {
    state = "committed";
    lastErrorCode = null;
    updatedAt = CanonicalChatPersistence.utc(now);
  }

  void fail(String supportSafeCode, Instant now) {
    if (!"committed".equals(state)) {
      state = "failed_retryable";
      attemptCount++;
      lastErrorCode = supportSafeCode;
      updatedAt = CanonicalChatPersistence.utc(now);
    }
  }
}

@Entity
@Table(name = "weave_chat_outbox")
class ChatOutboxJpaEntity {
  @EmbeddedId
  @AttributeOverrides({
    @AttributeOverride(
        name = "part1",
        column = @Column(name = "tenant_id", nullable = false, length = 160)),
    @AttributeOverride(
        name = "part2",
        column = @Column(name = "operation_id", nullable = false, length = 96))
  })
  private ChatPairId id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "operation_type", nullable = false, length = 48, updatable = false)
  private String operationType;

  @Column(name = "payload_json", nullable = false, updatable = false, length = Integer.MAX_VALUE)
  private String payloadJson;

  @Column(name = "provider_transaction_id", nullable = false, length = 160, updatable = false)
  private String providerTransactionId;

  @Column(name = "outbox_state", nullable = false, length = 32)
  private String state;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at_utc")
  private OffsetDateTime nextAttemptAt;

  @Column(name = "last_error_code", length = 96)
  private String lastErrorCode;

  @Column(name = "created_at_utc", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at_utc", nullable = false)
  private OffsetDateTime updatedAt;

  protected ChatOutboxJpaEntity() {}

  static ChatOutboxJpaEntity create(
      String tenantId,
      String operationId,
      String operationType,
      String payloadJson,
      String providerTransactionId,
      Instant now) {
    ChatOutboxJpaEntity entity = new ChatOutboxJpaEntity();
    entity.id = new ChatPairId(tenantId, operationId);
    entity.operationType = operationType;
    entity.payloadJson = payloadJson;
    entity.providerTransactionId = providerTransactionId;
    entity.state = "pending";
    entity.createdAt = CanonicalChatPersistence.utc(now);
    entity.updatedAt = CanonicalChatPersistence.utc(now);
    return entity;
  }

  String state() {
    return state;
  }

  Instant nextAttemptAt() {
    return nextAttemptAt == null ? null : nextAttemptAt.toInstant();
  }

  void acknowledge(Instant now) {
    state = "acknowledged";
    nextAttemptAt = null;
    lastErrorCode = null;
    updatedAt = CanonicalChatPersistence.utc(now);
  }

  void fail(String supportSafeCode, Instant retryAt, Instant now) {
    if (!"acknowledged".equals(state)) {
      state = "pending";
      attemptCount++;
      nextAttemptAt = CanonicalChatPersistence.utc(retryAt);
      lastErrorCode = supportSafeCode;
      updatedAt = CanonicalChatPersistence.utc(now);
    }
  }
}

@Entity
@Table(name = "weave_chat_provider_mappings")
class ChatProviderMappingJpaEntity {
  @EmbeddedId
  @AttributeOverrides({
    @AttributeOverride(
        name = "part1",
        column = @Column(name = "tenant_id", nullable = false, length = 160)),
    @AttributeOverride(
        name = "part2",
        column = @Column(name = "provider_key", nullable = false, length = 64)),
    @AttributeOverride(
        name = "part3",
        column = @Column(name = "object_type", nullable = false, length = 32)),
    @AttributeOverride(
        name = "part4",
        column = @Column(name = "canonical_object_id", nullable = false, length = 768))
  })
  private ChatQuadId id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "provider_ref", length = 768)
  private String providerRef;

  @Column(name = "mapping_intent_ref", length = 768, updatable = false)
  private String mappingIntentRef;

  @Column(name = "provider_source_version", length = 255)
  private String providerSourceVersion;

  @Column(name = "mapping_state", nullable = false, length = 32)
  private String state;

  @Column(name = "updated_at_utc", nullable = false)
  private OffsetDateTime updatedAt;

  protected ChatProviderMappingJpaEntity() {}

  static ChatProviderMappingJpaEntity pending(
      String tenantId,
      String providerKey,
      String objectType,
      String canonicalObjectId,
      String providerRef,
      String mappingIntentRef,
      Instant now) {
    ChatProviderMappingJpaEntity entity = new ChatProviderMappingJpaEntity();
    entity.id = new ChatQuadId(tenantId, providerKey, objectType, canonicalObjectId);
    entity.providerRef = providerRef;
    entity.mappingIntentRef = mappingIntentRef;
    entity.state = "pending";
    entity.updatedAt = CanonicalChatPersistence.utc(now);
    return entity;
  }

  String tenantId() {
    return id.part1();
  }

  String providerKey() {
    return id.part2();
  }

  String objectType() {
    return id.part3();
  }

  String canonicalObjectId() {
    return id.part4();
  }

  String providerRef() {
    return providerRef;
  }

  String mappingIntentRef() {
    return mappingIntentRef;
  }

  String providerSourceVersion() {
    return providerSourceVersion;
  }

  String state() {
    return state;
  }

  void acknowledge(String providerRef, String sourceVersion, Instant now) {
    this.providerRef = providerRef;
    providerSourceVersion = sourceVersion;
    state = "acknowledged";
    updatedAt = CanonicalChatPersistence.utc(now);
  }

  void degrade(Instant now) {
    state = "degraded";
    updatedAt = CanonicalChatPersistence.utc(now);
  }

  void heal(Instant now) {
    state = "acknowledged";
    updatedAt = CanonicalChatPersistence.utc(now);
  }
}

@Entity
@Table(name = "weave_chat_bridge_ledger")
class ChatBridgeLedgerJpaEntity {
  @EmbeddedId
  @AttributeOverrides({
    @AttributeOverride(
        name = "part1",
        column = @Column(name = "tenant_id", nullable = false, length = 160)),
    @AttributeOverride(
        name = "part2",
        column = @Column(name = "ledger_id", nullable = false, length = 96))
  })
  private ChatPairId id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "provider_key", nullable = false, length = 64, updatable = false)
  private String providerKey;

  @Column(name = "direction", nullable = false, length = 32, updatable = false)
  private String direction;

  @Column(name = "provider_transaction_id", length = 255, updatable = false)
  private String providerTransactionId;

  @Column(name = "provider_event_ref", length = 768, updatable = false)
  private String providerEventRef;

  @Column(name = "canonical_object_id", length = 255, updatable = false)
  private String canonicalObjectId;

  @Column(name = "source_version", length = 255)
  private String sourceVersion;

  @Column(name = "ledger_state", nullable = false, length = 32)
  private String state;

  @Column(name = "observed_at_utc", nullable = false)
  private OffsetDateTime observedAt;

  protected ChatBridgeLedgerJpaEntity() {}

  static ChatBridgeLedgerJpaEntity create(
      String tenantId,
      String ledgerId,
      String providerKey,
      String direction,
      String providerTransactionId,
      String providerEventRef,
      String canonicalObjectId,
      String sourceVersion,
      String state,
      Instant observedAt) {
    ChatBridgeLedgerJpaEntity entity = new ChatBridgeLedgerJpaEntity();
    entity.id = new ChatPairId(tenantId, ledgerId);
    entity.providerKey = providerKey;
    entity.direction = direction;
    entity.providerTransactionId = providerTransactionId;
    entity.providerEventRef = providerEventRef;
    entity.canonicalObjectId = canonicalObjectId;
    entity.update(state, sourceVersion, observedAt);
    return entity;
  }

  String providerEventRef() {
    return providerEventRef;
  }

  void update(String state, String sourceVersion, Instant observedAt) {
    this.state = state;
    this.sourceVersion = sourceVersion;
    this.observedAt = CanonicalChatPersistence.utc(observedAt);
  }
}

@Entity
@Table(name = "weave_chat_appservice_transactions")
class ChatAppserviceTransactionJpaEntity {
  @EmbeddedId
  @AttributeOverrides({
    @AttributeOverride(
        name = "part1",
        column = @Column(name = "provider_key", nullable = false, length = 64)),
    @AttributeOverride(
        name = "part2",
        column = @Column(name = "homeserver_transaction_id", nullable = false, length = 255))
  })
  private ChatPairId id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "payload_digest", nullable = false, length = 64, updatable = false)
  private String payloadDigest;

  @Column(name = "transaction_state", nullable = false, length = 32)
  private String state;

  @Column(name = "event_count", nullable = false, updatable = false)
  private int eventCount;

  @Column(name = "duplicate_count", nullable = false)
  private int duplicateCount;

  @Column(name = "received_at_utc", nullable = false, updatable = false)
  private OffsetDateTime receivedAt;

  @Column(name = "completed_at_utc")
  private OffsetDateTime completedAt;

  @Column(name = "semantic_fingerprint_version", nullable = false, length = 64, updatable = false)
  private String fingerprintVersion;

  @Column(name = "semantic_mismatch_count", nullable = false)
  private int semanticMismatchCount;

  @Column(name = "semantic_mismatch_hash", length = 64)
  private String semanticMismatchHash;

  protected ChatAppserviceTransactionJpaEntity() {}

  static ChatAppserviceTransactionJpaEntity processing(
      String providerKey,
      String transactionId,
      String digest,
      int eventCount,
      String fingerprintVersion,
      Instant now) {
    ChatAppserviceTransactionJpaEntity entity = new ChatAppserviceTransactionJpaEntity();
    entity.id = new ChatPairId(providerKey, transactionId);
    entity.payloadDigest = digest;
    entity.state = "processing";
    entity.eventCount = eventCount;
    entity.fingerprintVersion = fingerprintVersion;
    entity.receivedAt = CanonicalChatPersistence.utc(now);
    return entity;
  }

  String state() {
    return state;
  }

  String payloadDigest() {
    return payloadDigest;
  }

  int eventCount() {
    return eventCount;
  }

  int duplicateCount() {
    return duplicateCount;
  }

  int semanticMismatchCount() {
    return semanticMismatchCount;
  }

  String fingerprintVersion() {
    return fingerprintVersion;
  }

  void semanticMismatch(String mismatchHash) {
    state = "semantic-mismatch";
    semanticMismatchCount++;
    semanticMismatchHash = mismatchHash;
  }

  void duplicate() {
    duplicateCount++;
  }

  void complete(int duplicates, Instant now) {
    if ("processing".equals(state)) {
      state = "completed";
      duplicateCount += Math.max(0, duplicates);
      completedAt = CanonicalChatPersistence.utc(now);
    }
  }
}

@Entity
@Table(name = "weave_chat_quarantine")
class ChatQuarantineJpaEntity {
  @EmbeddedId
  @AttributeOverrides({
    @AttributeOverride(
        name = "part1",
        column = @Column(name = "tenant_id", nullable = false, length = 160)),
    @AttributeOverride(
        name = "part2",
        column = @Column(name = "quarantine_id", nullable = false, length = 96))
  })
  private ChatPairId id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "provider_key", nullable = false, length = 64, updatable = false)
  private String providerKey;

  @Column(name = "conversation_id", length = 160, updatable = false)
  private String conversationId;

  @Column(name = "correlation_hash", nullable = false, length = 64, updatable = false)
  private String correlationHash;

  @Column(name = "reason_code", nullable = false, length = 96, updatable = false)
  private String reasonCode;

  @Column(name = "category_code", nullable = false, length = 96, updatable = false)
  private String categoryCode;

  @Column(name = "recoverable", nullable = false, updatable = false)
  private boolean recoverable;

  @Column(name = "classifier_version", nullable = false, length = 64)
  private String classifierVersion;

  @Column(name = "lifecycle_state", nullable = false, length = 32)
  private String lifecycleState;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "max_attempts", nullable = false, updatable = false)
  private int maxAttempts;

  @Column(name = "observed_at_utc", nullable = false, updatable = false)
  private OffsetDateTime observedAt;

  @Column(name = "last_attempt_at_utc")
  private OffsetDateTime lastAttemptAt;

  @Column(name = "resolved_at_utc")
  private OffsetDateTime resolvedAt;

  @Column(name = "last_outcome_code", length = 96)
  private String lastOutcomeCode;

  @Column(name = "private_homeserver_transaction_id", length = 255, updatable = false)
  private String privateTransactionId;

  @Column(name = "private_provider_event_ref", length = 768, updatable = false)
  private String privateProviderEventRef;

  @Column(name = "private_provider_room_ref", length = 768, updatable = false)
  private String privateProviderRoomRef;

  @Column(name = "private_normalized_event_json", updatable = false, length = Integer.MAX_VALUE)
  private String normalizedEventJson;

  protected ChatQuarantineJpaEntity() {}

  static ChatQuarantineJpaEntity create(
      String tenantId,
      String quarantineId,
      String providerKey,
      String conversationId,
      String correlationHash,
      String reasonCode,
      String categoryCode,
      boolean recoverable,
      String classifierVersion,
      String lifecycleState,
      int maxAttempts,
      Instant observedAt,
      String privateTransactionId,
      String privateProviderEventRef,
      String privateProviderRoomRef,
      String normalizedEventJson,
      String lastOutcomeCode) {
    ChatQuarantineJpaEntity entity = new ChatQuarantineJpaEntity();
    entity.id = new ChatPairId(tenantId, quarantineId);
    entity.providerKey = providerKey;
    entity.conversationId = conversationId;
    entity.correlationHash = correlationHash;
    entity.reasonCode = reasonCode;
    entity.categoryCode = categoryCode;
    entity.recoverable = recoverable;
    entity.classifierVersion = classifierVersion;
    entity.lifecycleState = lifecycleState;
    entity.maxAttempts = maxAttempts;
    entity.observedAt = CanonicalChatPersistence.utc(observedAt);
    entity.privateTransactionId = privateTransactionId;
    entity.privateProviderEventRef = privateProviderEventRef;
    entity.privateProviderRoomRef = privateProviderRoomRef;
    entity.normalizedEventJson = normalizedEventJson;
    entity.lastOutcomeCode = lastOutcomeCode;
    if (!"pending".equals(lifecycleState)) {
      entity.resolvedAt = CanonicalChatPersistence.utc(observedAt);
    }
    return entity;
  }

  String tenantId() {
    return id.part1();
  }

  String quarantineId() {
    return id.part2();
  }

  String providerKey() {
    return providerKey;
  }

  String conversationId() {
    return conversationId;
  }

  String correlationHash() {
    return correlationHash;
  }

  String reasonCode() {
    return reasonCode;
  }

  boolean recoverable() {
    return recoverable;
  }

  String classifierVersion() {
    return classifierVersion;
  }

  String lifecycleState() {
    return lifecycleState;
  }

  int attemptCount() {
    return attemptCount;
  }

  int maxAttempts() {
    return maxAttempts;
  }

  String normalizedEventJson() {
    return normalizedEventJson;
  }

  void resolve(String state, String outcome, String classifierVersion, Instant now) {
    lifecycleState = state;
    attemptCount++;
    lastAttemptAt = CanonicalChatPersistence.utc(now);
    resolvedAt = CanonicalChatPersistence.utc(now);
    lastOutcomeCode = outcome;
    this.classifierVersion = classifierVersion;
  }

  void reject(String outcome, String classifierVersion, Instant now, boolean incrementAttempt) {
    lifecycleState = "rejected";
    if (incrementAttempt) {
      attemptCount++;
      lastAttemptAt = CanonicalChatPersistence.utc(now);
    }
    resolvedAt = CanonicalChatPersistence.utc(now);
    lastOutcomeCode = outcome;
    this.classifierVersion = classifierVersion;
  }

  void defer(String outcome, String classifierVersion, Instant now) {
    attemptCount++;
    lastAttemptAt = CanonicalChatPersistence.utc(now);
    lastOutcomeCode = outcome;
    this.classifierVersion = classifierVersion;
  }
}

@Entity
@Table(name = "weave_chat_read_receipts")
class ChatReadReceiptJpaEntity {
  @EmbeddedId
  @AttributeOverrides({
    @AttributeOverride(
        name = "part1",
        column = @Column(name = "tenant_id", nullable = false, length = 160)),
    @AttributeOverride(
        name = "part2",
        column = @Column(name = "conversation_id", nullable = false, length = 160)),
    @AttributeOverride(
        name = "part3",
        column = @Column(name = "identity_issuer", nullable = false, length = 512)),
    @AttributeOverride(
        name = "part4",
        column = @Column(name = "actor_ref", nullable = false, length = 255))
  })
  private ChatQuadId id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "event_id", nullable = false, length = 255)
  private String eventId;

  @Column(name = "read_at_utc", nullable = false)
  private OffsetDateTime readAt;

  protected ChatReadReceiptJpaEntity() {}

  static ChatReadReceiptJpaEntity create(
      String tenantId,
      String conversationId,
      String identityIssuer,
      String actorRef,
      String eventId,
      Instant readAt) {
    ChatReadReceiptJpaEntity entity = new ChatReadReceiptJpaEntity();
    entity.id = new ChatQuadId(tenantId, conversationId, identityIssuer, actorRef);
    entity.advance(eventId, readAt);
    return entity;
  }

  void advance(String eventId, Instant readAt) {
    this.eventId = eventId;
    this.readAt = CanonicalChatPersistence.utc(readAt);
  }
}

@Entity
@Table(name = "weave_chat_changes")
class ChatChangeJpaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "sequence_value", nullable = false, updatable = false)
  private Long sequence;

  @Column(name = "tenant_id", nullable = false, length = 160, updatable = false)
  private String tenantId;

  @Column(name = "conversation_id", nullable = false, length = 160, updatable = false)
  private String conversationId;

  @Column(name = "change_kind", nullable = false, length = 64, updatable = false)
  private String kind;

  @Column(name = "canonical_object_id", length = 255, updatable = false)
  private String canonicalObjectId;

  @Column(name = "callback_deduplication_key", length = 96, updatable = false)
  private String callbackDeduplicationKey;

  @Column(name = "occurred_at_utc", nullable = false, updatable = false)
  private OffsetDateTime occurredAt;

  protected ChatChangeJpaEntity() {}

  static ChatChangeJpaEntity create(
      String tenantId,
      String conversationId,
      String kind,
      String canonicalObjectId,
      String callbackDeduplicationKey,
      Instant occurredAt) {
    ChatChangeJpaEntity entity = new ChatChangeJpaEntity();
    entity.tenantId = tenantId;
    entity.conversationId = conversationId;
    entity.kind = kind;
    entity.canonicalObjectId = canonicalObjectId;
    entity.callbackDeduplicationKey = callbackDeduplicationKey;
    entity.occurredAt = CanonicalChatPersistence.utc(occurredAt);
    return entity;
  }

  long sequence() {
    return sequence;
  }

  String conversationId() {
    return conversationId;
  }

  String kind() {
    return kind;
  }

  String canonicalObjectId() {
    return canonicalObjectId;
  }

  Instant occurredAt() {
    return occurredAt.toInstant();
  }
}

interface ChatConversationJpaRepository
    extends JpaRepository<ChatConversationJpaEntity, ChatPairId> {
  @Query(
      """
      select conversation from ChatConversationJpaEntity conversation
      where conversation.id.part1 = :tenantId
        and conversation.contextId = :contextId
        and conversation.lifecycleState = 'committed'
        and exists (
            select membership.id.part1 from ChatMembershipJpaEntity membership
            where membership.id.part1 = conversation.id.part1
              and membership.id.part2 = conversation.id.part2
              and membership.id.part3 = :identityIssuer
              and membership.id.part4 = :actorRef
              and membership.state = 'joined'
        )
        and not exists (
            select mapping.id.part1 from ChatProviderMappingJpaEntity mapping
            where mapping.id.part1 = conversation.id.part1
              and mapping.id.part3 = 'conversation'
              and mapping.id.part4 = conversation.id.part2
              and mapping.state = 'degraded'
        )
      order by conversation.updatedAt desc, conversation.id.part2
      """)
  List<ChatConversationJpaEntity> findJoined(
      @Param("tenantId") String tenantId,
      @Param("contextId") String contextId,
      @Param("identityIssuer") String identityIssuer,
      @Param("actorRef") String actorRef);

  Optional<ChatConversationJpaEntity> findByIdAndLifecycleState(
      ChatPairId id, String lifecycleState);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select conversation from ChatConversationJpaEntity conversation
      where conversation.id.part1 = :tenantId
        and conversation.id.part2 = :conversationId
      """)
  Optional<ChatConversationJpaEntity> lockForEventSequence(
      @Param("tenantId") String tenantId,
      @Param("conversationId") String conversationId);
}

interface ChatMembershipJpaRepository extends JpaRepository<ChatMembershipJpaEntity, ChatQuadId> {
  List<ChatMembershipJpaEntity> findByIdPart1AndIdPart2OrderByIdPart4(
      String tenantId, String conversationId);

  List<ChatMembershipJpaEntity> findByIdPart1AndIdPart2InOrderByIdPart2AscIdPart4Asc(
      String tenantId, List<String> conversationIds);

  long countByIdPart1AndIdPart2AndState(String tenantId, String conversationId, String state);
}

interface ChatEventJpaRepository extends JpaRepository<ChatEventJpaEntity, ChatTripleId> {
  @Query(
      """
      select event from ChatEventJpaEntity event
      where event.id.part1 = :tenantId
        and event.id.part2 = :conversationId
        and event.deliveryState = 'committed'
        and event.sequence < :before
      order by event.sequence desc
      """)
  List<ChatEventJpaEntity> findVisibleBefore(
      @Param("tenantId") String tenantId,
      @Param("conversationId") String conversationId,
      @Param("before") long before,
      Pageable page);

  List<ChatEventJpaEntity> findByIdPart1AndIdPart2AndDeliveryStateOrderBySequence(
      String tenantId, String conversationId, String deliveryState);

  long countByIdPart1AndIdPart2AndDeliveryState(
      String tenantId, String conversationId, String deliveryState);

  long countByIdPart1AndIdPart2AndDeliveryStateAndKind(
      String tenantId, String conversationId, String deliveryState, String kind);

  Optional<ChatEventJpaEntity> findFirstByIdPart1AndIdPart3(String tenantId, String eventId);
}

interface ChatOperationJpaRepository extends JpaRepository<ChatOperationJpaEntity, ChatPairId> {
  Optional<ChatOperationJpaEntity> findFirstByProviderTransactionId(String providerTransactionId);

  List<ChatOperationJpaEntity> findByIdPart1AndConversationIdAndOperationTypeInAndStateIn(
      String tenantId, String conversationId, List<String> operationTypes, List<String> states);

  long countByIdPart1AndConversationIdAndState(
      String tenantId, String conversationId, String state);

  long countByIdPart1AndOperationTypeAndIdentityIssuerAndActorRefAndStateIn(
      String tenantId,
      String operationType,
      String identityIssuer,
      String actorRef,
      List<String> states);
}

interface ChatOutboxJpaRepository extends JpaRepository<ChatOutboxJpaEntity, ChatPairId> {}

interface ChatProviderMappingJpaRepository
    extends JpaRepository<ChatProviderMappingJpaEntity, ChatQuadId> {
  Optional<ChatProviderMappingJpaEntity> findFirstByIdPart2AndIdPart3AndProviderRef(
      String providerKey, String objectType, String providerRef);

  Optional<ChatProviderMappingJpaEntity> findFirstByIdPart2AndIdPart3AndMappingIntentRef(
      String providerKey, String objectType, String mappingIntentRef);

  long countByIdPart1AndIdPart3AndIdPart4AndState(
      String tenantId, String objectType, String canonicalObjectId, String state);

  long countByIdPart1AndIdPart2AndIdPart3AndIdPart4AndState(
      String tenantId,
      String providerKey,
      String objectType,
      String canonicalObjectId,
      String state);

  @Query(
      """
      select mapping.providerRef
      from ChatProviderMappingJpaEntity mapping, ChatEventJpaEntity event
      where mapping.id.part1 = event.id.part1
        and mapping.id.part4 = event.id.part3
        and mapping.id.part2 = :providerKey
        and mapping.id.part3 = 'event'
        and mapping.state = 'acknowledged'
        and event.id.part1 = :tenantId
        and event.id.part2 = :conversationId
        and event.deliveryState = 'committed'
      order by event.sequence
      """)
  List<String> findAcknowledgedEventRefs(
      @Param("providerKey") String providerKey,
      @Param("tenantId") String tenantId,
      @Param("conversationId") String conversationId);
}

interface ChatBridgeLedgerJpaRepository
    extends JpaRepository<ChatBridgeLedgerJpaEntity, ChatPairId> {
  boolean existsByIdPart1AndProviderKeyAndDirectionAndProviderTransactionIdAndProviderEventRef(
      String tenantId,
      String providerKey,
      String direction,
      String providerTransactionId,
      String providerEventRef);

  boolean existsByIdPart1AndProviderKeyAndProviderEventRefAndStateIn(
      String tenantId, String providerKey, String providerEventRef, List<String> states);

  @Query(
      """
      select count(ledger) from ChatBridgeLedgerJpaEntity ledger
      where ledger.id.part1 = :tenantId
        and ledger.providerKey = :providerKey
        and (
            ledger.canonicalObjectId = :conversationId
            or ledger.canonicalObjectId in (
                select event.id.part3 from ChatEventJpaEntity event
                where event.id.part1 = :tenantId
                  and event.id.part2 = :conversationId
            )
            or ledger.providerTransactionId in (
                select operation.providerTransactionId from ChatOperationJpaEntity operation
                where operation.id.part1 = :tenantId
                  and operation.conversationId = :conversationId
            )
        )
      """)
  long countEvidence(
      @Param("tenantId") String tenantId,
      @Param("providerKey") String providerKey,
      @Param("conversationId") String conversationId);
}

interface ChatAppserviceTransactionJpaRepository
    extends JpaRepository<ChatAppserviceTransactionJpaEntity, ChatPairId> {
  long countByIdPart1AndState(String providerKey, String state);

  long countByIdPart1(String providerKey);

  @Query(
      """
      select coalesce(sum(callback.duplicateCount), 0)
      from ChatAppserviceTransactionJpaEntity callback
      where callback.id.part1 = :providerKey
      """)
  long sumDuplicates(@Param("providerKey") String providerKey);

  @Query(
      """
      select coalesce(sum(callback.semanticMismatchCount), 0)
      from ChatAppserviceTransactionJpaEntity callback
      where callback.id.part1 = :providerKey
      """)
  long sumSemanticMismatches(@Param("providerKey") String providerKey);
}

interface ChatQuarantineJpaRepository extends JpaRepository<ChatQuarantineJpaEntity, ChatPairId> {
  @Query(
      """
      select quarantine from ChatQuarantineJpaEntity quarantine
      where quarantine.providerKey = :providerKey
        and quarantine.lifecycleState = 'pending'
        and quarantine.recoverable = true
        and quarantine.classifierVersion <> :classifierVersion
      order by quarantine.observedAt, quarantine.id.part2
      """)
  List<ChatQuarantineJpaEntity> findReconciliationCandidates(
      @Param("providerKey") String providerKey,
      @Param("classifierVersion") String classifierVersion,
      Pageable page);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select quarantine from ChatQuarantineJpaEntity quarantine
      where quarantine.id.part1 = :tenantId
        and quarantine.providerKey = :providerKey
        and quarantine.correlationHash = :correlationHash
      """)
  Optional<ChatQuarantineJpaEntity> lockByCorrelation(
      @Param("tenantId") String tenantId,
      @Param("providerKey") String providerKey,
      @Param("correlationHash") String correlationHash);

  Optional<ChatQuarantineJpaEntity>
      findFirstByIdPart1AndConversationIdAndLifecycleStateInOrderByObservedAtDescIdPart2Desc(
          String tenantId, String conversationId, List<String> lifecycleStates);

  long countByProviderKeyAndIdPart1AndConversationIdAndLifecycleStateIn(
      String providerKey, String tenantId, String conversationId, List<String> lifecycleStates);

  long countByIdPart1AndProviderKeyAndConversationIdAndLifecycleStateIn(
      String tenantId, String providerKey, String conversationId, List<String> lifecycleStates);
}

interface ChatReadReceiptJpaRepository
    extends JpaRepository<ChatReadReceiptJpaEntity, ChatQuadId> {}

interface ChatChangeJpaRepository extends JpaRepository<ChatChangeJpaEntity, Long> {
  @Query(
      """
      select coalesce(max(change.sequence), 0) from ChatChangeJpaEntity change
      where change.tenantId = :tenantId
        and exists (
            select membership.id.part1 from ChatMembershipJpaEntity membership
            where membership.id.part1 = change.tenantId
              and membership.id.part2 = change.conversationId
              and membership.id.part3 = :identityIssuer
              and membership.id.part4 = :actorRef
              and membership.state = 'joined'
        )
        and exists (
            select conversation.id.part1 from ChatConversationJpaEntity conversation
            where conversation.id.part1 = change.tenantId
              and conversation.id.part2 = change.conversationId
              and conversation.contextId = :contextId
        )
      """)
  long currentCursor(
      @Param("tenantId") String tenantId,
      @Param("identityIssuer") String identityIssuer,
      @Param("actorRef") String actorRef,
      @Param("contextId") String contextId);

  @Query(
      """
      select change from ChatChangeJpaEntity change
      where change.tenantId = :tenantId
        and change.sequence > :after
        and exists (
            select membership.id.part1 from ChatMembershipJpaEntity membership
            where membership.id.part1 = change.tenantId
              and membership.id.part2 = change.conversationId
              and membership.id.part3 = :identityIssuer
              and membership.id.part4 = :actorRef
              and membership.state = 'joined'
        )
        and exists (
            select conversation.id.part1 from ChatConversationJpaEntity conversation
            where conversation.id.part1 = change.tenantId
              and conversation.id.part2 = change.conversationId
              and conversation.contextId = :contextId
        )
        and not exists (
            select mapping.id.part1 from ChatProviderMappingJpaEntity mapping
            where mapping.id.part1 = change.tenantId
              and mapping.id.part3 = 'conversation'
              and mapping.id.part4 = change.conversationId
              and mapping.state = 'degraded'
        )
      order by change.sequence
      """)
  List<ChatChangeJpaEntity> findVisibleChanges(
      @Param("tenantId") String tenantId,
      @Param("after") long after,
      @Param("identityIssuer") String identityIssuer,
      @Param("actorRef") String actorRef,
      @Param("contextId") String contextId,
      Pageable page);

  boolean existsByTenantIdAndConversationIdAndCanonicalObjectId(
      String tenantId, String conversationId, String canonicalObjectId);

  boolean existsByTenantIdAndConversationIdAndKindAndCanonicalObjectId(
      String tenantId, String conversationId, String kind, String canonicalObjectId);

  boolean existsByTenantIdAndCallbackDeduplicationKey(
      String tenantId, String callbackDeduplicationKey);
}
