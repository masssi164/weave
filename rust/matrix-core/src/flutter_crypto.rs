use matrix_sdk::{
    authentication::{matrix::MatrixSession, SessionTokens},
    config::SyncSettings,
    deserialized_responses::{
        ProcessedToDeviceEvent, TimelineEvent, TimelineEventKind, ToDeviceUnableToDecryptReason,
        UnableToDecryptReason,
    },
    encryption::{
        identities::UserDevices,
        recovery::{RecoveryError, RecoveryState},
        verification::{
            SasVerification, Verification, VerificationRequest, VerificationRequestState,
        },
        BackupDownloadStrategy, EncryptionSettings, VerificationState,
    },
    room::{MessagesOptions, RoomMember},
    ruma::{
        api::client::{
            keys::get_keys::v3 as get_keys,
            receipt::create_receipt::v3::ReceiptType,
            room::create_room::v3::{Request as CreateRoomRequest, RoomPreset},
        },
        api::error::ErrorKind,
        api::MatrixVersion,
        events::receipt::ReceiptThread,
        events::{
            key::verification::VerificationMethod, room::encryption::RoomEncryptionEventContent,
            room::message::RoomMessageEventContent, AnyToDeviceEvent, InitialStateEvent,
        },
        serde::Raw,
        OwnedDeviceId, OwnedEventId, OwnedRoomId, OwnedUserId, UInt,
    },
    store::RoomLoadSettings,
    Client, Room, RoomMemberships, SessionMeta,
};
use reqwest::header::{HeaderMap, HeaderName, HeaderValue};
use serde_json::{json, Value};
use std::{
    cmp::Ordering,
    collections::{BTreeMap, BTreeSet, HashMap},
    path::Path,
    sync::{Arc, Mutex, OnceLock},
    time::Duration,
};
use tokio::sync::Mutex as AsyncMutex;

const DEVICE_ID_HEADER: &str = "x-weave-matrix-device-id";
const PRE_SEND_DEVICE_QUERY_ATTEMPTS: usize = 10;
const PRE_SEND_DEVICE_QUERY_DELAY: Duration = Duration::from_millis(500);
const MATRIX_CONNECT_TIMEOUT: Duration = Duration::from_secs(5);
const MATRIX_REQUEST_TIMEOUT: Duration = Duration::from_secs(15);
const OLM_UNWEDGE_ROTATION_PENDING_KEY: &[u8] = b"weave.olm-unwedge-rotation-pending.v1";

struct ManagedClient {
    client: Client,
    homeserver_url: String,
    user_id: String,
    device_id: String,
    access_token: String,
    room_security_fingerprints: HashMap<String, RoomSecurityFingerprint>,
    pre_send_security_fingerprints: HashMap<String, RoomSecurityFingerprint>,
    accepting_operations: bool,
    matrix_io_gate: Arc<AsyncMutex<()>>,
    room_security_gate: Arc<AsyncMutex<()>>,
    sync_cursor: Option<String>,
    to_device_diagnostics: ToDeviceDiagnostics,
    peer_device_diagnostics: PeerDeviceConvergenceDiagnostics,
    verification_request: Option<VerificationRequest>,
    sas_verification: Option<SasVerification>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct RoomSecurityFingerprint {
    member_ids: Vec<String>,
    member_device_ids: Vec<String>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum RoomSecurityRefresh {
    Sync,
    PreSend,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct CompletedSyncCycle {
    next_batch: String,
    enabled_rooms: u64,
    converged_rooms: u64,
}

#[derive(Default)]
struct ClientContinuityState {
    room_security_fingerprints: HashMap<String, RoomSecurityFingerprint>,
    pre_send_security_fingerprints: HashMap<String, RoomSecurityFingerprint>,
    sync_cursor: Option<String>,
    to_device_diagnostics: ToDeviceDiagnostics,
    peer_device_diagnostics: PeerDeviceConvergenceDiagnostics,
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
struct ToDeviceDiagnostics {
    decrypted: u64,
    decrypted_room_key: u64,
    decrypted_forwarded_room_key: u64,
    decrypted_other: u64,
    decrypted_unknown_type: u64,
    decryption_failure: u64,
    unverified_sender_device: u64,
    no_olm_machine: u64,
    encryption_disabled: u64,
    plaintext: u64,
    invalid: u64,
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
enum PeerDeviceConvergenceState {
    #[default]
    NotObserved,
    Converged,
    Pending,
    Rejected,
    Blocked,
    Invalid,
}

impl PeerDeviceConvergenceState {
    fn errcode(self) -> Option<&'static str> {
        match self {
            Self::NotObserved | Self::Converged => None,
            Self::Pending => Some("M_WEAVE_E2EE_PEER_DEVICE_PENDING"),
            Self::Rejected => Some("M_WEAVE_E2EE_PEER_DEVICE_REJECTED"),
            Self::Blocked => Some("M_WEAVE_E2EE_PEER_DEVICE_BLOCKED"),
            Self::Invalid => Some("M_WEAVE_E2EE_PEER_DEVICE_INVALID"),
        }
    }
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
struct PeerDeviceConvergenceDiagnostics {
    joined_peer_count: u64,
    authoritative_device_count: u64,
    sdk_device_count: u64,
    sdk_usable_device_count: u64,
    sdk_deleted_device_count: u64,
    sdk_blacklisted_device_count: u64,
    sdk_missing_curve25519_count: u64,
    sdk_missing_authoritative_device_count: u64,
    sdk_unexpected_device_count: u64,
    query_attempt_count: u64,
    converged_peer_count: u64,
    pending_peer_count: u64,
    rejected_peer_count: u64,
    blocked_peer_count: u64,
    invalid_peer_count: u64,
}

impl PeerDeviceConvergenceDiagnostics {
    fn record(
        &mut self,
        authoritative_device_ids: &BTreeSet<String>,
        sdk_devices: &PeerSdkDeviceSet,
        state: PeerDeviceConvergenceState,
    ) {
        self.authoritative_device_count = self
            .authoritative_device_count
            .saturating_add(authoritative_device_ids.len() as u64);
        self.sdk_device_count = self
            .sdk_device_count
            .saturating_add(sdk_devices.all.len() as u64);
        self.sdk_usable_device_count = self
            .sdk_usable_device_count
            .saturating_add(sdk_devices.usable.len() as u64);
        self.sdk_deleted_device_count = self
            .sdk_deleted_device_count
            .saturating_add(sdk_devices.deleted.len() as u64);
        self.sdk_blacklisted_device_count = self
            .sdk_blacklisted_device_count
            .saturating_add(sdk_devices.blacklisted.len() as u64);
        self.sdk_missing_curve25519_count = self
            .sdk_missing_curve25519_count
            .saturating_add(sdk_devices.missing_curve25519.len() as u64);
        self.sdk_missing_authoritative_device_count =
            self.sdk_missing_authoritative_device_count.saturating_add(
                authoritative_device_ids
                    .difference(&sdk_devices.all)
                    .count() as u64,
            );
        self.sdk_unexpected_device_count = self
            .sdk_unexpected_device_count
            .saturating_add(sdk_devices.all.difference(authoritative_device_ids).count() as u64);
        match state {
            PeerDeviceConvergenceState::NotObserved => {}
            PeerDeviceConvergenceState::Converged => {
                self.converged_peer_count = self.converged_peer_count.saturating_add(1);
            }
            PeerDeviceConvergenceState::Pending => {
                self.pending_peer_count = self.pending_peer_count.saturating_add(1);
            }
            PeerDeviceConvergenceState::Rejected => {
                self.rejected_peer_count = self.rejected_peer_count.saturating_add(1);
            }
            PeerDeviceConvergenceState::Blocked => {
                self.blocked_peer_count = self.blocked_peer_count.saturating_add(1);
            }
            PeerDeviceConvergenceState::Invalid => {
                self.invalid_peer_count = self.invalid_peer_count.saturating_add(1);
            }
        }
    }
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
struct PeerSdkDeviceSet {
    all: BTreeSet<String>,
    usable: BTreeSet<String>,
    deleted: BTreeSet<String>,
    blacklisted: BTreeSet<String>,
    missing_curve25519: BTreeSet<String>,
}

impl PeerSdkDeviceSet {
    fn from_user_devices(devices: &UserDevices) -> Self {
        let mut observed = Self::default();
        for device in devices.devices() {
            let device_id = device.device_id().to_string();
            if device.is_deleted() {
                observed.deleted.insert(device_id);
                continue;
            }
            observed.all.insert(device_id.clone());
            if device.is_blacklisted() {
                observed.blacklisted.insert(device_id.clone());
            }
            if device.curve25519_key().is_none() {
                observed.missing_curve25519.insert(device_id.clone());
            }
            if is_eligible_peer_device(device.is_blacklisted(), device.curve25519_key().is_some()) {
                observed.usable.insert(device_id);
            }
        }
        observed
    }
}

impl ToDeviceDiagnostics {
    fn record(&mut self, events: &[ProcessedToDeviceEvent]) {
        for event in events {
            match event {
                ProcessedToDeviceEvent::Decrypted { raw, .. } => {
                    self.decrypted = self.decrypted.saturating_add(1);
                    match raw.get_field::<String>("type").ok().flatten().as_deref() {
                        Some("m.room_key") => {
                            self.decrypted_room_key = self.decrypted_room_key.saturating_add(1);
                        }
                        Some("m.forwarded_room_key") => {
                            self.decrypted_forwarded_room_key =
                                self.decrypted_forwarded_room_key.saturating_add(1);
                        }
                        Some(_) => {
                            self.decrypted_other = self.decrypted_other.saturating_add(1);
                        }
                        None => {
                            self.decrypted_unknown_type =
                                self.decrypted_unknown_type.saturating_add(1);
                        }
                    }
                }
                ProcessedToDeviceEvent::UnableToDecrypt { utd_info, .. } => {
                    match &utd_info.reason {
                        ToDeviceUnableToDecryptReason::DecryptionFailure => {
                            self.decryption_failure = self.decryption_failure.saturating_add(1);
                        }
                        ToDeviceUnableToDecryptReason::UnverifiedSenderDevice => {
                            self.unverified_sender_device =
                                self.unverified_sender_device.saturating_add(1);
                        }
                        ToDeviceUnableToDecryptReason::NoOlmMachine => {
                            self.no_olm_machine = self.no_olm_machine.saturating_add(1);
                        }
                        ToDeviceUnableToDecryptReason::EncryptionIsDisabled => {
                            self.encryption_disabled = self.encryption_disabled.saturating_add(1);
                        }
                    }
                }
                ProcessedToDeviceEvent::PlainText(_) => {
                    self.plaintext = self.plaintext.saturating_add(1);
                }
                ProcessedToDeviceEvent::Invalid(_) => {
                    self.invalid = self.invalid.saturating_add(1);
                }
            }
        }
    }

    fn unable_to_decrypt_count(&self) -> u64 {
        self.decryption_failure
            .saturating_add(self.unverified_sender_device)
            .saturating_add(self.no_olm_machine)
            .saturating_add(self.encryption_disabled)
    }

    fn reason_counts(&self) -> BTreeMap<&'static str, u64> {
        let mut reasons = BTreeMap::new();
        for (reason, count) in [
            ("decryptionFailure", self.decryption_failure),
            ("unverifiedSenderDevice", self.unverified_sender_device),
            ("noOlmMachine", self.no_olm_machine),
            ("encryptionDisabled", self.encryption_disabled),
        ] {
            if count > 0 {
                reasons.insert(reason, count);
            }
        }
        reasons
    }
}

static CLIENTS: OnceLock<Mutex<HashMap<String, ManagedClient>>> = OnceLock::new();
static CLIENT_LIFECYCLE_GATES: OnceLock<Mutex<HashMap<String, Arc<AsyncMutex<()>>>>> =
    OnceLock::new();

fn clients() -> &'static Mutex<HashMap<String, ManagedClient>> {
    CLIENTS.get_or_init(|| Mutex::new(HashMap::new()))
}

fn client_lifecycle_gate_for(profile_key: &str) -> Result<Arc<AsyncMutex<()>>, String> {
    let mut gates = CLIENT_LIFECYCLE_GATES
        .get_or_init(|| Mutex::new(HashMap::new()))
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?;
    Ok(gates
        .entry(profile_key.to_string())
        .or_insert_with(|| Arc::new(AsyncMutex::new(())))
        .clone())
}

pub async fn initialize(
    profile_key: String,
    homeserver_url: String,
    user_id: String,
    device_id: String,
    access_token: String,
    store_path: String,
    store_passphrase: String,
    extra_root_certificate_pem: String,
) -> String {
    json_result(
        initialize_inner(
            profile_key,
            homeserver_url,
            user_id,
            device_id,
            access_token,
            store_path,
            store_passphrase,
            extra_root_certificate_pem,
        )
        .await,
    )
}

async fn initialize_inner(
    profile_key: String,
    homeserver_url: String,
    user_id: String,
    device_id: String,
    access_token: String,
    store_path: String,
    store_passphrase: String,
    extra_root_certificate_pem: String,
) -> Result<Value, String> {
    validate_identifier(&profile_key, "profile")?;
    if homeserver_url.trim().is_empty()
        || access_token.is_empty()
        || store_path.trim().is_empty()
        || store_passphrase.len() < 32
    {
        return Err("M_WEAVE_E2EE_CONFIGURATION".to_string());
    }
    let matrix_user_id =
        OwnedUserId::try_from(user_id.as_str()).map_err(|_| "M_WEAVE_E2EE_IDENTITY".to_string())?;
    let matrix_device_id = OwnedDeviceId::from(device_id.as_str());
    validate_identifier(&device_id, "device")?;

    let lifecycle_gate = client_lifecycle_gate_for(&profile_key)?;
    let _lifecycle_guard = lifecycle_gate.lock().await;
    let (matrix_io_gate, replacing_existing) = {
        let mut guard = clients()
            .lock()
            .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?;
        if let Some(existing) = guard.get_mut(&profile_key) {
            if existing.homeserver_url == homeserver_url
                && existing.user_id == user_id
                && existing.device_id == device_id
                && existing.access_token == access_token
                && existing.accepting_operations
            {
                return Ok(json!({
                    "initialized": true,
                    "restored": true,
                    "deviceId": device_id,
                }));
            }
            // Token renewal cannot create a second Matrix SDK/store owner.
            // Reject new operations, drain the single explicit sync/send gate,
            // and reuse that gate for the replacement client.
            existing.accepting_operations = false;
            (existing.matrix_io_gate.clone(), true)
        } else {
            (Arc::new(AsyncMutex::new(())), false)
        }
    };
    let _matrix_io_guard = matrix_io_gate.lock().await;
    let continuity = if replacing_existing {
        clients()
            .lock()
            .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
            .remove(&profile_key)
            .map(|replaced| ClientContinuityState {
                room_security_fingerprints: replaced.room_security_fingerprints,
                pre_send_security_fingerprints: replaced.pre_send_security_fingerprints,
                sync_cursor: replaced.sync_cursor,
                to_device_diagnostics: replaced.to_device_diagnostics,
                peer_device_diagnostics: replaced.peer_device_diagnostics,
            })
            .unwrap_or_default()
    } else {
        ClientContinuityState::default()
    };

    let mut default_headers = HeaderMap::new();
    default_headers.insert(
        HeaderName::from_static(DEVICE_ID_HEADER),
        HeaderValue::from_str(&device_id).map_err(|_| "M_WEAVE_E2EE_IDENTITY".to_string())?,
    );
    let http_client = build_http_client(default_headers, &extra_root_certificate_pem)?;

    let client = Client::builder()
        .homeserver_url(&homeserver_url)
        .server_versions([MatrixVersion::V1_18])
        .http_client(http_client)
        .sqlite_store(Path::new(&store_path), Some(store_passphrase.as_str()))
        .with_encryption_settings(EncryptionSettings {
            auto_enable_cross_signing: true,
            auto_enable_backups: true,
            backup_download_strategy: BackupDownloadStrategy::AfterDecryptionFailure,
            ..Default::default()
        })
        .build()
        .await
        .map_err(|_| "M_WEAVE_E2EE_STORE".to_string())?;

    client
        .matrix_auth()
        .restore_session(
            MatrixSession {
                meta: SessionMeta {
                    user_id: matrix_user_id,
                    device_id: matrix_device_id,
                },
                tokens: SessionTokens {
                    access_token: access_token.clone(),
                    refresh_token: None,
                },
            },
            RoomLoadSettings::default(),
        )
        .await
        .map_err(|_| "M_WEAVE_E2EE_SESSION".to_string())?;

    client
        .encryption()
        .wait_for_e2ee_initialization_tasks()
        .await;

    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .insert(
            profile_key,
            ManagedClient {
                client,
                homeserver_url,
                user_id,
                device_id: device_id.clone(),
                access_token,
                room_security_fingerprints: continuity.room_security_fingerprints,
                pre_send_security_fingerprints: continuity.pre_send_security_fingerprints,
                accepting_operations: true,
                matrix_io_gate: matrix_io_gate.clone(),
                room_security_gate: Arc::new(AsyncMutex::new(())),
                sync_cursor: continuity.sync_cursor,
                to_device_diagnostics: continuity.to_device_diagnostics,
                peer_device_diagnostics: continuity.peer_device_diagnostics,
                verification_request: None,
                sas_verification: None,
            },
        );

    Ok(json!({
        "initialized": true,
        "restored": false,
        "deviceId": device_id,
    }))
}

fn build_http_client(
    default_headers: HeaderMap,
    extra_root_certificate_pem: &str,
) -> Result<reqwest::Client, String> {
    // A stalled Matrix request otherwise retains the single explicit
    // sync/send/store gate forever. Bound both connection establishment and
    // the complete request so foreground retry, sign-out, and token renewal
    // can always regain the sole crypto-store owner.
    let mut builder = reqwest::Client::builder()
        .default_headers(default_headers)
        .connect_timeout(MATRIX_CONNECT_TIMEOUT)
        .timeout(MATRIX_REQUEST_TIMEOUT);
    if !extra_root_certificate_pem.trim().is_empty() {
        let certificates =
            reqwest::Certificate::from_pem_bundle(extra_root_certificate_pem.as_bytes())
                .map_err(|_| "M_WEAVE_E2EE_TLS_ROOT".to_string())?;
        if certificates.is_empty() {
            return Err("M_WEAVE_E2EE_TLS_ROOT".to_string());
        }
        builder = builder.tls_certs_merge(certificates);
    }
    builder
        .build()
        .map_err(|_| "M_WEAVE_E2EE_CONFIGURATION".to_string())
}

pub async fn sync(profile_key: String) -> String {
    json_result(sync_inner(&profile_key).await)
}

async fn sync_inner(profile_key: &str) -> Result<Value, String> {
    let completed =
        complete_sync_cycle(profile_key, Duration::from_secs(0), "M_WEAVE_E2EE_SYNC").await?;

    Ok(json!({
        "nextBatch": completed.next_batch,
        "enabledRooms": completed.enabled_rooms,
        "convergedRooms": completed.converged_rooms,
        "syncOwner": "explicit",
    }))
}

async fn complete_sync_cycle(
    profile_key: &str,
    timeout: Duration,
    error_code: &str,
) -> Result<CompletedSyncCycle, String> {
    // Matrix acknowledges ephemeral to-device events when the next `/sync`
    // presents the previous `next_batch`. Keep cursor read, response processing,
    // crypto-store mutation, and cursor advance under the same gate used by
    // encrypted send. There is deliberately no second background cursor owner.
    let matrix_io_gate = matrix_io_gate_for(profile_key)?;
    let _matrix_io_guard = matrix_io_gate.lock().await;
    complete_sync_cycle_under_gate(profile_key, timeout, error_code).await
}

async fn complete_sync_cycle_under_gate(
    profile_key: &str,
    timeout: Duration,
    error_code: &str,
) -> Result<CompletedSyncCycle, String> {
    let (client, since) = client_and_sync_cursor(profile_key)?;
    let settings = sync_settings(timeout, since.as_deref());
    let mut response = client
        .sync_once(settings)
        .await
        .map_err(|error| matrix_sdk_error_code(&error, error_code))?;
    record_to_device_diagnostics(profile_key, &response.to_device)?;
    remember_olm_unwedge_rotation(&client, &response.to_device).await?;
    reconcile_verification_requests(profile_key, &client, &response.to_device).await?;
    let (mut enabled_rooms, mut converged_rooms) =
        converge_joined_room_security(profile_key, &client).await?;
    if enabled_rooms > 0 {
        let next_batch = response.next_batch.clone();
        response = client
            .sync_once(sync_settings(
                Duration::from_secs(0),
                Some(next_batch.as_str()),
            ))
            .await
            .map_err(|error| matrix_sdk_error_code(&error, error_code))?;
        record_to_device_diagnostics(profile_key, &response.to_device)?;
        remember_olm_unwedge_rotation(&client, &response.to_device).await?;
        reconcile_verification_requests(profile_key, &client, &response.to_device).await?;
        let (newly_enabled_rooms, newly_converged_rooms) =
            converge_joined_room_security(profile_key, &client).await?;
        enabled_rooms = enabled_rooms.saturating_add(newly_enabled_rooms);
        converged_rooms = converged_rooms.max(newly_converged_rooms);
    }
    let completed = CompletedSyncCycle {
        next_batch: response.next_batch,
        enabled_rooms,
        converged_rooms,
    };
    remember_sync_cursor(profile_key, completed.next_batch.clone())?;
    Ok(completed)
}

async fn reconcile_verification_requests(
    profile_key: &str,
    client: &Client,
    events: &[ProcessedToDeviceEvent],
) -> Result<(), String> {
    // Verification requests are ephemeral to-device events. Reconcile the
    // SDK-owned request into the app projection from the processed response
    // before its cursor is acknowledged. This keeps verification state in the
    // same transaction as the crypto-store mutation instead of relying on a
    // separate event-handler side effect.
    for event in events {
        let Some((sender, transaction_id)) = verification_request_identity(event) else {
            continue;
        };
        if let Some(request) = client
            .encryption()
            .get_verification_request(&sender, &transaction_id)
            .await
        {
            update_verification(profile_key, Some(request), None)?;
        }
    }
    Ok(())
}

fn verification_request_identity(event: &ProcessedToDeviceEvent) -> Option<(OwnedUserId, String)> {
    let raw = event.as_raw();
    if raw.get_field::<String>("type").ok().flatten().as_deref()
        != Some("m.key.verification.request")
    {
        return None;
    }
    let sender = raw.get_field::<OwnedUserId>("sender").ok().flatten()?;
    let content = raw.get_field::<Value>("content").ok().flatten()?;
    let transaction_id = content
        .get("transaction_id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())?
        .to_owned();
    Some((sender, transaction_id))
}

fn client_and_sync_cursor(profile_key: &str) -> Result<(Client, Option<String>), String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .filter(|managed| managed.accepting_operations)
        .map(|managed| (managed.client.clone(), managed.sync_cursor.clone()))
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())
}

fn remember_sync_cursor(profile_key: &str, next_batch: String) -> Result<(), String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get_mut(profile_key)
        .filter(|managed| managed.accepting_operations)
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())?
        .sync_cursor = Some(next_batch);
    Ok(())
}

fn record_to_device_diagnostics(
    profile_key: &str,
    events: &[ProcessedToDeviceEvent],
) -> Result<(), String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get_mut(profile_key)
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())?
        .to_device_diagnostics
        .record(events);
    Ok(())
}

fn is_decrypted_olm_dummy(raw: &Raw<AnyToDeviceEvent>) -> bool {
    raw.get_field::<String>("type").ok().flatten().as_deref() == Some("m.dummy")
}

fn contains_decrypted_olm_dummy(events: &[ProcessedToDeviceEvent]) -> bool {
    events.iter().any(|event| {
        matches!(
            event,
            ProcessedToDeviceEvent::Decrypted { raw, .. } if is_decrypted_olm_dummy(raw)
        )
    })
}

async fn remember_olm_unwedge_rotation(
    client: &Client,
    events: &[ProcessedToDeviceEvent],
) -> Result<(), String> {
    if contains_decrypted_olm_dummy(events) {
        // matrix-sdk sends an encrypted `m.dummy` after it detects a wedged
        // Olm session. Receiving that event proves the peer has established a
        // fresh Olm channel, but an existing outbound Megolm session can still
        // remember that the old channel already received its room key. Persist
        // a one-shot rotation latch so the next encrypted send re-shares the
        // room key even when the app is relaunched between sync and send.
        client
            .state_store()
            .set_custom_value(OLM_UNWEDGE_ROTATION_PENDING_KEY, vec![1])
            .await
            .map_err(|_| "M_WEAVE_E2EE_STORE".to_string())?;
    }
    Ok(())
}

fn olm_unwedge_rotation_is_pending(value: Option<&[u8]>) -> bool {
    !matches!(value, None | Some([]) | Some([0]))
}

async fn olm_unwedge_rotation_pending(client: &Client) -> Result<bool, String> {
    let value = client
        .state_store()
        .get_custom_value(OLM_UNWEDGE_ROTATION_PENDING_KEY)
        .await
        .map_err(|_| "M_WEAVE_E2EE_STORE".to_string())?;
    Ok(olm_unwedge_rotation_is_pending(value.as_deref()))
}

async fn clear_olm_unwedge_rotation(client: &Client) -> Result<(), String> {
    client
        .state_store()
        .set_custom_value(OLM_UNWEDGE_ROTATION_PENDING_KEY, vec![0])
        .await
        .map_err(|_| "M_WEAVE_E2EE_STORE".to_string())?;
    Ok(())
}

fn to_device_diagnostics(profile_key: &str) -> Result<ToDeviceDiagnostics, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .map(|managed| managed.to_device_diagnostics.clone())
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())
}

fn remember_peer_device_diagnostics(
    profile_key: &str,
    diagnostics: PeerDeviceConvergenceDiagnostics,
) -> Result<(), String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get_mut(profile_key)
        .filter(|managed| managed.accepting_operations)
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())?
        .peer_device_diagnostics = diagnostics;
    Ok(())
}

fn peer_device_diagnostics(profile_key: &str) -> Result<PeerDeviceConvergenceDiagnostics, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .map(|managed| managed.peer_device_diagnostics.clone())
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())
}

fn sync_settings(timeout: Duration, since: Option<&str>) -> SyncSettings {
    let settings = SyncSettings::new().timeout(timeout);
    match since.filter(|value| !value.is_empty()) {
        Some(cursor) => settings.token(cursor.to_owned()),
        None => settings,
    }
}

async fn converge_joined_room_security(
    profile_key: &str,
    client: &Client,
) -> Result<(u64, u64), String> {
    let mut enabled_rooms = 0_u64;
    let mut converged_rooms = 0_u64;
    for room in client.joined_rooms() {
        let encryption = room
            .latest_encryption_state()
            .await
            .map_err(|_| "M_WEAVE_E2EE_ROOM_STATE".to_string())?;
        if !encryption.is_encrypted() {
            room.enable_encryption()
                .await
                .map_err(|_| "M_WEAVE_E2EE_ENABLE_ROOM".to_string())?;
            enabled_rooms += 1;
            continue;
        }

        // A room can become shared after both app-owned crypto clients have
        // opened. Converge the active member and device lists on every fresh
        // membership set before either participant needs to receive the first
        // Olm-wrapped Megolm room key. A send-time check alone protects only
        // the sender and leaves a cold collaborator unable to authenticate or
        // decrypt the first to-device key delivery.
        refresh_active_member_device_keys(profile_key, client, &room, RoomSecurityRefresh::Sync)
            .await?;
        converged_rooms += 1;
    }
    Ok((enabled_rooms, converged_rooms))
}

fn matrix_io_gate_for(profile_key: &str) -> Result<Arc<AsyncMutex<()>>, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .filter(|managed| managed.accepting_operations)
        .map(|managed| managed.matrix_io_gate.clone())
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())
}

pub async fn rooms(profile_key: String) -> String {
    json_result(rooms_inner(&profile_key).await)
}

pub async fn create_encrypted_room(profile_key: String, title: String) -> String {
    json_result(create_encrypted_room_inner(&profile_key, &title).await)
}

async fn create_encrypted_room_inner(profile_key: &str, title: &str) -> Result<Value, String> {
    let title = title.trim();
    if title.is_empty() || title.chars().count() > 200 {
        return Err("M_INVALID_PARAM".to_string());
    }
    let room_id = {
        let matrix_io_gate = matrix_io_gate_for(profile_key)?;
        let _matrix_io_guard = matrix_io_gate.lock().await;
        let client = client_for(profile_key)?;
        let mut request = CreateRoomRequest::new();
        request.name = Some(title.to_owned());
        request.preset = Some(RoomPreset::PrivateChat);
        request.initial_state = vec![InitialStateEvent::with_empty_state_key(
            RoomEncryptionEventContent::with_recommended_defaults(),
        )
        .to_raw_any()];
        client
            .create_room(request)
            .await
            .map_err(|error| matrix_sdk_error_code(&error, "M_WEAVE_E2EE_CREATE_ROOM"))?
            .room_id()
            .to_owned()
    };

    // The facade commits room creation before it appears in the client's
    // joined-room cache. Cross the same single-owner sync barrier used by
    // normal Chat refreshes so the returned conversation is immediately
    // navigable and its encryption state is available before the first send.
    sync_inner(profile_key).await?;

    Ok(json!({
        "roomId": room_id.to_string(),
        "title": title,
        "encrypted": true,
    }))
}

async fn rooms_inner(profile_key: &str) -> Result<Value, String> {
    let matrix_io_gate = matrix_io_gate_for(profile_key)?;
    let _matrix_io_guard = matrix_io_gate.lock().await;
    let client = client_for(profile_key)?;
    let mut rooms = Vec::new();
    for room in client.joined_rooms() {
        let encrypted = room
            .latest_encryption_state()
            .await
            .map_err(|_| "M_WEAVE_E2EE_ROOM_STATE".to_string())?
            .is_encrypted();
        let display_name = room
            .display_name()
            .await
            .map(|name| name.to_string())
            .unwrap_or_else(|_| room.room_id().to_string());
        rooms.push(json!({
            "roomId": room.room_id().to_string(),
            "title": display_name,
            "unreadCount": room.num_unread_messages(),
            "encrypted": encrypted,
        }));
    }
    Ok(json!({ "rooms": rooms }))
}

pub async fn room_messages(profile_key: String, room_id: String, limit: u32) -> String {
    json_result(room_messages_inner(&profile_key, &room_id, limit).await)
}

async fn room_messages_inner(
    profile_key: &str,
    room_id: &str,
    limit: u32,
) -> Result<Value, String> {
    let matrix_io_gate = matrix_io_gate_for(profile_key)?;
    let _matrix_io_guard = matrix_io_gate.lock().await;
    // Treat to-device key consumption, cursor acknowledgement, and timeline
    // decryption as one native receive transaction. No foreground/background
    // handoff can interleave a second cursor owner between the room key and the
    // message that depends on it.
    complete_sync_cycle_under_gate(profile_key, Duration::from_secs(0), "M_WEAVE_E2EE_SYNC")
        .await?;
    let client = client_for(profile_key)?;
    let room_id = OwnedRoomId::try_from(room_id).map_err(|_| "M_INVALID_PARAM".to_string())?;
    let room = client
        .get_room(&room_id)
        .ok_or_else(|| "M_NOT_FOUND".to_string())?;
    if !room
        .latest_encryption_state()
        .await
        .map_err(|_| "M_WEAVE_E2EE_ROOM_STATE".to_string())?
        .is_encrypted()
    {
        return Err("M_WEAVE_E2EE_REQUIRED".to_string());
    }

    let mut options = MessagesOptions::backward();
    options.limit =
        UInt::new(u64::from(limit.clamp(1, 100))).expect("bounded Matrix message limit");
    let response = room
        .messages(options)
        .await
        .map_err(|_| "M_WEAVE_E2EE_TIMELINE".to_string())?;
    let decryption = decryption_diagnostics(
        &response.chunk,
        &to_device_diagnostics(profile_key)?,
        &peer_device_diagnostics(profile_key)?,
    );
    let mut messages = response
        .chunk
        .iter()
        .filter_map(project_timeline_event)
        .collect::<Vec<_>>();
    messages.reverse();
    Ok(json!({
        "roomId": room_id.to_string(),
        "messages": messages,
        "end": response.end,
        "decryption": decryption,
    }))
}

pub async fn send_text(profile_key: String, room_id: String, body: String) -> String {
    json_result(send_text_inner(&profile_key, &room_id, &body).await)
}

pub async fn mark_read(profile_key: String, room_id: String, event_id: String) -> String {
    let result = async {
        let matrix_io_gate = matrix_io_gate_for(&profile_key)?;
        let _matrix_io_guard = matrix_io_gate.lock().await;
        let client = client_for(&profile_key)?;
        let room_id = OwnedRoomId::try_from(room_id).map_err(|_| "M_INVALID_PARAM".to_string())?;
        let event_id =
            OwnedEventId::try_from(event_id).map_err(|_| "M_INVALID_PARAM".to_string())?;
        let room = client
            .get_room(&room_id)
            .ok_or_else(|| "M_NOT_FOUND".to_string())?;
        room.send_single_receipt(ReceiptType::Read, ReceiptThread::Unthreaded, event_id)
            .await
            .map_err(|_| "M_WEAVE_E2EE_RECEIPT".to_string())?;
        Ok(json!({ "read": true }))
    }
    .await;
    json_result(result)
}

async fn send_text_inner(profile_key: &str, room_id: &str, body: &str) -> Result<Value, String> {
    if body.trim().is_empty() || body.len() > 100_000 {
        return Err("M_INVALID_PARAM".to_string());
    }
    let room_id = OwnedRoomId::try_from(room_id).map_err(|_| "M_INVALID_PARAM".to_string())?;
    let matrix_io_gate = matrix_io_gate_for(profile_key)?;
    let _matrix_io_guard = matrix_io_gate.lock().await;
    let client = client_for(profile_key)?;
    let room = client
        .get_room(&room_id)
        .ok_or_else(|| "M_NOT_FOUND".to_string())?;
    if !room
        .latest_encryption_state()
        .await
        .map_err(|_| "M_WEAVE_E2EE_ROOM_STATE".to_string())?
        .is_encrypted()
    {
        return Err("M_WEAVE_E2EE_REQUIRED".to_string());
    }
    refresh_active_member_device_keys(profile_key, &client, &room, RoomSecurityRefresh::PreSend)
        .await?;
    let rotate_after_olm_unwedge = olm_unwedge_rotation_pending(&client).await?;
    if rotate_after_olm_unwedge {
        // A newly-established Olm channel does not invalidate the SDK's
        // existing outbound Megolm sharing record. Rotate exactly once after
        // the unwedge signal so the next message distributes a fresh room key
        // over the repaired channel instead of committing another event that
        // the peer cannot decrypt.
        room.discard_room_key()
            .await
            .map_err(|_| "M_WEAVE_E2EE_ROOM_KEY_ROTATION".to_string())?;
        // The discarded outbound Megolm state is itself the durable repair.
        // Clear the auxiliary latch before committing the timeline event so a
        // local store failure can never report a failed send after the server
        // has already accepted it. If the later send fails, the next send must
        // still create or reuse the replacement outbound session and share its
        // key before committing.
        clear_olm_unwedge_rotation(&client).await?;
    }
    let response = room
        .send(RoomMessageEventContent::text_plain(body))
        .await
        .map_err(|error| matrix_sdk_error_code(&error, "M_WEAVE_E2EE_SEND"))?;
    Ok(json!({ "eventId": response.response.event_id.to_string() }))
}

async fn refresh_active_member_device_keys(
    profile_key: &str,
    client: &Client,
    room: &Room,
    refresh: RoomSecurityRefresh,
) -> Result<(), String> {
    let security_gate = room_security_gate_for(profile_key)?;
    let _security_guard = security_gate.lock().await;
    let own_user_id = client
        .user_id()
        .ok_or_else(|| "M_WEAVE_E2EE_SESSION".to_string())?;

    let mut members = room
        .members_no_sync(RoomMemberships::JOIN)
        .await
        .map_err(|_| "M_WEAVE_E2EE_ROOM_MEMBERS".to_string())?;
    let observed_member_ids = joined_member_ids(&members);
    let cached_fingerprint =
        cached_room_security_fingerprint(profile_key, room.room_id().as_str())?;
    let cached_pre_send_fingerprint =
        cached_pre_send_security_fingerprint(profile_key, room.room_id().as_str())?;

    let member_set_changed = joined_member_set_changed(
        cached_fingerprint
            .as_ref()
            .map(|fingerprint| fingerprint.member_ids.as_slice()),
        &observed_member_ids,
    );
    let pre_send_reload_required = requires_pre_send_member_reload(
        refresh,
        cached_pre_send_fingerprint.as_ref(),
        cached_fingerprint.as_ref(),
    );
    let full_member_reload_required = member_set_changed || pre_send_reload_required;
    if full_member_reload_required {
        // The canonical facade can create and join a room between two bounded
        // syncs. Force the SDK to consume the facade's complete `/members`
        // joined projection before it decides which devices receive the Megolm
        // session. Invited users are not yet active recipients and must not turn
        // the sender's fail-closed barrier into a publication wait.
        // Sync convergence may warm an incomplete first-room snapshot, but
        // it cannot satisfy the first security-sensitive send barrier. A separate
        // pre-send fingerprint keeps subsequent sends fast until a later
        // member/device change invalidates that barrier.
        room.mark_members_missing();
        members = room
            .members(RoomMemberships::JOIN)
            .await
            .map_err(|_| "M_WEAVE_E2EE_ROOM_MEMBERS".to_string())?;
    }
    let encryption = client.encryption();
    let mut member_device_ids = Vec::new();
    let mut convergence_diagnostics = PeerDeviceConvergenceDiagnostics {
        joined_peer_count: members
            .iter()
            .filter(|member| member.user_id() != own_user_id)
            .count() as u64,
        ..Default::default()
    };
    let mut convergence_observed = false;

    for member in &members {
        let user_id = member.user_id();
        if user_id == own_user_id {
            continue;
        }
        let cached_devices = encryption
            .get_user_devices(user_id)
            .await
            .map_err(|_| "M_WEAVE_E2EE_MEMBER_KEYS".to_string())?;
        let has_cached_device = cached_devices.devices().next().is_some();
        let query_required = refresh == RoomSecurityRefresh::PreSend
            || requires_explicit_device_query(full_member_reload_required, has_cached_device);
        let mut sdk_devices = PeerSdkDeviceSet::from_user_devices(&cached_devices);
        let mut convergence_state = PeerDeviceConvergenceState::NotObserved;

        // A first room sync can mark a member as tracked while the local crypto
        // store still contains only an older device snapshot. Compare a standard
        // `/keys/query` projection with the SDK's validated device store on every
        // security-sensitive send. The raw response is evidence only: it never
        // becomes a recipient until the SDK has accepted its self-signature and
        // exposed a usable Curve25519 key.
        if query_required {
            convergence_observed = true;
            let attempts = if refresh == RoomSecurityRefresh::PreSend {
                PRE_SEND_DEVICE_QUERY_ATTEMPTS
            } else {
                1
            };
            for attempt in 0..attempts {
                convergence_diagnostics.query_attempt_count = convergence_diagnostics
                    .query_attempt_count
                    .saturating_add(1);
                let authoritative_device_ids =
                    authoritative_peer_device_ids(client, user_id).await?;
                encryption
                    .request_user_identity(user_id)
                    .await
                    .map_err(|error| matrix_sdk_error_code(&error, "M_WEAVE_E2EE_MEMBER_KEYS"))?;
                let refreshed_devices = encryption
                    .get_user_devices(user_id)
                    .await
                    .map_err(|_| "M_WEAVE_E2EE_MEMBER_KEYS".to_string())?;
                sdk_devices = PeerSdkDeviceSet::from_user_devices(&refreshed_devices);
                convergence_state =
                    classify_peer_device_convergence(&authoritative_device_ids, &sdk_devices);
                if !should_retry_peer_device_query(refresh, attempt, convergence_state) {
                    convergence_diagnostics.record(
                        &authoritative_device_ids,
                        &sdk_devices,
                        convergence_state,
                    );
                    break;
                }
                tokio::time::sleep(PRE_SEND_DEVICE_QUERY_DELAY).await;
            }
        }
        if refresh == RoomSecurityRefresh::PreSend {
            remember_peer_device_diagnostics(profile_key, convergence_diagnostics.clone())?;
            if let Some(errcode) = convergence_state.errcode() {
                // A joined peer whose authoritative device set has not become
                // the SDK's exact usable set cannot receive the next Megolm
                // room key reliably. Fail before the timeline event is committed.
                return Err(errcode.to_string());
            }
        }
        member_device_ids.extend(
            sdk_devices
                .usable
                .iter()
                .map(|device_id| format!("{user_id}|{device_id}")),
        );
    }
    if convergence_observed {
        remember_peer_device_diagnostics(profile_key, convergence_diagnostics)?;
    }
    member_device_ids.sort_unstable();
    member_device_ids.dedup();

    let observed_fingerprint = RoomSecurityFingerprint {
        member_ids: joined_member_ids(&members),
        member_device_ids,
    };
    if room_security_fingerprint_changed(cached_fingerprint.as_ref(), &observed_fingerprint) {
        // The canonical Matrix facade can expose a newly registered device for
        // an unchanged room member between app sessions. A current `/keys/query`
        // updates the SDK crypto store, but an already persisted outbound Megolm
        // session may still exclude that device. Rotate only when the effective
        // joined member/device set changes so the next send shares a fresh room
        // key with every joined device without rotating on every message.
        room.discard_room_key()
            .await
            .map_err(|_| "M_WEAVE_E2EE_ROOM_KEY_ROTATION".to_string())?;
    }

    remember_room_security_fingerprint(
        profile_key,
        room.room_id().as_str(),
        observed_fingerprint.clone(),
    )?;
    if refresh == RoomSecurityRefresh::PreSend {
        remember_pre_send_security_fingerprint(
            profile_key,
            room.room_id().as_str(),
            observed_fingerprint,
        )?;
    }

    Ok(())
}

async fn authoritative_peer_device_ids(
    client: &Client,
    user_id: &matrix_sdk::ruma::UserId,
) -> Result<BTreeSet<String>, String> {
    let mut request = get_keys::Request::new();
    request
        .device_keys
        .insert(user_id.to_owned(), Vec::<OwnedDeviceId>::new());
    let response = client.send(request).await.map_err(|error| {
        matrix_error_kind_code(error.client_api_error_kind(), "M_WEAVE_E2EE_MEMBER_KEYS")
    })?;
    Ok(response
        .device_keys
        .get(user_id)
        .map(|devices| devices.keys().map(ToString::to_string).collect())
        .unwrap_or_default())
}

fn classify_peer_device_convergence(
    authoritative_device_ids: &BTreeSet<String>,
    sdk_devices: &PeerSdkDeviceSet,
) -> PeerDeviceConvergenceState {
    if authoritative_device_ids.is_empty() {
        return PeerDeviceConvergenceState::Pending;
    }
    if sdk_devices.all != *authoritative_device_ids {
        return PeerDeviceConvergenceState::Rejected;
    }
    if !sdk_devices.blacklisted.is_empty() {
        return PeerDeviceConvergenceState::Blocked;
    }
    if !sdk_devices.missing_curve25519.is_empty() {
        return PeerDeviceConvergenceState::Invalid;
    }
    if sdk_devices.usable == *authoritative_device_ids {
        PeerDeviceConvergenceState::Converged
    } else {
        PeerDeviceConvergenceState::Rejected
    }
}

fn joined_member_ids(members: &[RoomMember]) -> Vec<String> {
    let mut member_ids = members
        .iter()
        .map(|member| member.user_id().to_string())
        .collect::<Vec<_>>();
    member_ids.sort_unstable();
    member_ids.dedup();
    member_ids
}

fn joined_member_set_changed(cached: Option<&[String]>, observed: &[String]) -> bool {
    cached != Some(observed)
}

fn room_security_fingerprint_changed(
    cached: Option<&RoomSecurityFingerprint>,
    observed: &RoomSecurityFingerprint,
) -> bool {
    cached != Some(observed)
}

fn requires_explicit_device_query(member_set_changed: bool, has_cached_device: bool) -> bool {
    member_set_changed || !has_cached_device
}

fn requires_pre_send_member_reload(
    refresh: RoomSecurityRefresh,
    _cached_pre_send_fingerprint: Option<&RoomSecurityFingerprint>,
    _cached_fingerprint: Option<&RoomSecurityFingerprint>,
) -> bool {
    // Membership and key publication can change without a prior local sync.
    // Every security-sensitive send therefore refreshes the authoritative
    // active-members projection; the fingerprint still prevents needless key
    // rotation when the effective eligible device set is unchanged.
    refresh == RoomSecurityRefresh::PreSend
}

fn is_eligible_peer_device(is_blacklisted: bool, has_curve25519_key: bool) -> bool {
    !is_blacklisted && has_curve25519_key
}

fn should_retry_peer_device_query(
    refresh: RoomSecurityRefresh,
    attempt: usize,
    state: PeerDeviceConvergenceState,
) -> bool {
    refresh == RoomSecurityRefresh::PreSend
        && state != PeerDeviceConvergenceState::Converged
        && attempt + 1 < PRE_SEND_DEVICE_QUERY_ATTEMPTS
}

fn room_security_gate_for(profile_key: &str) -> Result<Arc<AsyncMutex<()>>, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .map(|managed| managed.room_security_gate.clone())
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())
}

fn cached_room_security_fingerprint(
    profile_key: &str,
    room_id: &str,
) -> Result<Option<RoomSecurityFingerprint>, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .map(|managed| managed.room_security_fingerprints.get(room_id).cloned())
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())
}

fn cached_pre_send_security_fingerprint(
    profile_key: &str,
    room_id: &str,
) -> Result<Option<RoomSecurityFingerprint>, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .map(|managed| managed.pre_send_security_fingerprints.get(room_id).cloned())
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())
}

fn remember_room_security_fingerprint(
    profile_key: &str,
    room_id: &str,
    fingerprint: RoomSecurityFingerprint,
) -> Result<(), String> {
    let mut guard = clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?;
    let managed = guard
        .get_mut(profile_key)
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())?;
    let changed = managed
        .room_security_fingerprints
        .get(room_id)
        .map_or(true, |cached| cached != &fingerprint);
    managed
        .room_security_fingerprints
        .insert(room_id.to_string(), fingerprint);
    if changed {
        managed.pre_send_security_fingerprints.remove(room_id);
    }
    Ok(())
}

fn remember_pre_send_security_fingerprint(
    profile_key: &str,
    room_id: &str,
    fingerprint: RoomSecurityFingerprint,
) -> Result<(), String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get_mut(profile_key)
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())?
        .pre_send_security_fingerprints
        .insert(room_id.to_string(), fingerprint);
    Ok(())
}

pub async fn security_state(profile_key: String) -> String {
    json_result(security_state_inner(&profile_key).await)
}

async fn security_state_inner(profile_key: &str) -> Result<Value, String> {
    let client = client_for(profile_key)?;
    let recovery_state = client.encryption().recovery().state();
    let cross_signing = client.encryption().cross_signing_status().await;
    let own_device = client
        .encryption()
        .get_own_device()
        .await
        .map_err(|_| "M_WEAVE_E2EE_DEVICE".to_string())?;
    let verification_state = client.encryption().verification_state().get();
    let encrypted_rooms = client
        .joined_rooms()
        .into_iter()
        .filter(|room| room.encryption_state().is_encrypted())
        .count();
    let verification = verification_json(profile_key)?;
    // Expose the already-recorded receive state without issuing another
    // `/sync` or `/messages` request. Failure diagnostics must not become a
    // second cursor owner or obscure the original receive-path failure behind
    // a network timeout.
    let receive_diagnostics = decryption_diagnostics(
        &[],
        &to_device_diagnostics(profile_key)?,
        &peer_device_diagnostics(profile_key)?,
    );

    Ok(json!({
        "signedIn": client.matrix_auth().logged_in(),
        "recoveryState": recovery_state_name(recovery_state),
        "crossSigningReady": cross_signing.as_ref().is_some_and(|state| state.is_complete()),
        "deviceVerified": own_device.as_ref().is_some_and(|device| device.is_verified()),
        "accountVerified": matches!(verification_state, VerificationState::Verified),
        "encryptedRoomCount": encrypted_rooms,
        "verification": verification,
        "receiveDiagnostics": receive_diagnostics,
    }))
}

pub async fn bootstrap_recovery(profile_key: String, passphrase: String) -> String {
    let result = async {
        let client = client_for(&profile_key)?;
        let recovery = client.encryption().recovery();
        let enable = recovery.enable().wait_for_backups_to_upload();
        let recovery_key = if passphrase.trim().is_empty() {
            enable.await
        } else {
            enable.with_passphrase(passphrase.trim()).await
        }
        .map_err(|error| bootstrap_recovery_error_code(&error).to_string())?;
        Ok(json!({ "recoveryKey": recovery_key }))
    }
    .await;
    json_result(result)
}

pub async fn recover(profile_key: String, recovery_key_or_passphrase: String) -> String {
    let result = async {
        if recovery_key_or_passphrase.trim().is_empty() {
            return Err("M_INVALID_PARAM".to_string());
        }
        let client = client_for(&profile_key)?;
        client
            .encryption()
            .recovery()
            .recover(recovery_key_or_passphrase.trim())
            .await
            .map_err(|_| "M_WEAVE_E2EE_RECOVERY".to_string())?;
        for room in client
            .joined_rooms()
            .into_iter()
            .filter(|room| room.encryption_state().is_encrypted())
        {
            client
                .encryption()
                .backups()
                .download_room_keys_for_room(room.room_id())
                .await
                .map_err(|_| "M_WEAVE_E2EE_RECOVERY_ROOM_KEYS".to_string())?;
        }
        Ok(json!({ "recovered": true }))
    }
    .await;
    json_result(result)
}

pub async fn start_verification(profile_key: String) -> String {
    let result = async {
        let client = client_for(&profile_key)?;
        let own_user_id = client
            .user_id()
            .ok_or_else(|| "M_WEAVE_E2EE_SESSION".to_string())?;
        let own_device_id = client
            .device_id()
            .ok_or_else(|| "M_WEAVE_E2EE_SESSION".to_string())?;
        let encryption = client.encryption();
        // A second device can publish its keys after this client's latest
        // sync response. Refresh the current user's device data before
        // selecting a concrete sibling verification target.
        encryption
            .request_user_identity(own_user_id)
            .await
            .map_err(|_| "M_WEAVE_E2EE_DEVICE".to_string())?;
        let devices = encryption
            .get_user_devices(own_user_id)
            .await
            .map_err(|_| "M_WEAVE_E2EE_DEVICE".to_string())?;
        let mut candidates = devices
            .devices()
            .filter(|device| {
                device.device_id() != own_device_id
                    && !device.is_deleted()
                    && !device.is_blacklisted()
                    && device.curve25519_key().is_some()
            })
            .collect::<Vec<_>>();
        // UserDevices is backed by a hash map, so iterator order is not a
        // product decision. Prefer the newest unverified sibling: that is the
        // device the user has just added and needs to approve. A stable ID
        // tie-breaker keeps retries on the same target. Do not use
        // OwnUserIdentity::request_verification here: matrix-sdk deliberately
        // filters unsigned new devices from that broadcast and can therefore
        // route the request only to an older, already-signed sibling.
        candidates.sort_by(|left, right| {
            verification_target_order(
                left.is_verified(),
                left.first_time_seen_ts().get().into(),
                left.device_id().as_str(),
                right.is_verified(),
                right.first_time_seen_ts().get().into(),
                right.device_id().as_str(),
            )
        });
        let target = candidates
            .into_iter()
            .next()
            .ok_or_else(|| "M_WEAVE_E2EE_NO_OTHER_DEVICE".to_string())?;
        let request = target
            .request_verification_with_methods(vec![VerificationMethod::SasV1])
            .await
            .map_err(|_| "M_WEAVE_E2EE_VERIFICATION".to_string())?;
        update_verification(&profile_key, Some(request), None)?;
        verification_json(&profile_key)
    }
    .await;
    json_result(result)
}

fn verification_target_order(
    left_verified: bool,
    left_first_seen_ms: u64,
    left_device_id: &str,
    right_verified: bool,
    right_first_seen_ms: u64,
    right_device_id: &str,
) -> Ordering {
    left_verified
        .cmp(&right_verified)
        .then_with(|| right_first_seen_ms.cmp(&left_first_seen_ms))
        .then_with(|| left_device_id.cmp(right_device_id))
}

pub async fn accept_verification(profile_key: String) -> String {
    let result = async {
        let request = verification_request_for(&profile_key)?;
        request
            .accept_with_methods(vec![VerificationMethod::SasV1])
            .await
            .map_err(|_| "M_WEAVE_E2EE_VERIFICATION".to_string())?;
        verification_json(&profile_key)
    }
    .await;
    json_result(result)
}

pub async fn start_sas(profile_key: String) -> String {
    let result = async {
        let request = verification_request_for(&profile_key)?;
        let transitioned = match request.state() {
            VerificationRequestState::Transitioned { verification } => verification.sas(),
            _ => None,
        };
        let sas = if let Some(sas) = transitioned {
            sas.accept()
                .await
                .map_err(|_| "M_WEAVE_E2EE_VERIFICATION".to_string())?;
            sas
        } else {
            request
                .start_sas()
                .await
                .map_err(|_| "M_WEAVE_E2EE_VERIFICATION".to_string())?
                .ok_or_else(|| "M_WEAVE_E2EE_VERIFICATION_NOT_READY".to_string())?
        };
        update_verification(&profile_key, Some(request), Some(sas))?;
        verification_json(&profile_key)
    }
    .await;
    json_result(result)
}

pub async fn confirm_sas(profile_key: String, matches: bool) -> String {
    let result = async {
        let sas = sas_for(&profile_key)?;
        if matches {
            sas.confirm()
                .await
                .map_err(|_| "M_WEAVE_E2EE_VERIFICATION".to_string())?;
        } else {
            sas.mismatch()
                .await
                .map_err(|_| "M_WEAVE_E2EE_VERIFICATION".to_string())?;
        }
        verification_json(&profile_key)
    }
    .await;
    json_result(result)
}

pub async fn cancel_verification(profile_key: String) -> String {
    let result = async {
        if let Ok(sas) = sas_for(&profile_key) {
            sas.cancel()
                .await
                .map_err(|_| "M_WEAVE_E2EE_VERIFICATION".to_string())?;
        } else if let Ok(request) = verification_request_for(&profile_key) {
            request
                .cancel()
                .await
                .map_err(|_| "M_WEAVE_E2EE_VERIFICATION".to_string())?;
        }
        verification_json(&profile_key)
    }
    .await;
    json_result(result)
}

pub fn dismiss_verification(profile_key: String) -> String {
    json_result(update_verification(&profile_key, None, None).map(|_| json!({ "dismissed": true })))
}

pub async fn dispose(profile_key: String) -> String {
    let result = async {
        let lifecycle_gate = client_lifecycle_gate_for(&profile_key)?;
        let _lifecycle_guard = lifecycle_gate.lock().await;
        let matrix_io_gate = {
            let mut guard = clients()
                .lock()
                .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?;
            let Some(managed) = guard.get_mut(&profile_key) else {
                return Ok(json!({ "disposed": true }));
            };
            managed.accepting_operations = false;
            managed.matrix_io_gate.clone()
        };
        let _matrix_io_guard = matrix_io_gate.lock().await;
        let removed = clients()
            .lock()
            .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
            .remove(&profile_key);
        drop(removed);
        Ok(json!({ "disposed": true }))
    }
    .await;
    json_result(result)
}

fn client_for(profile_key: &str) -> Result<Client, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .filter(|managed| managed.accepting_operations)
        .map(|managed| managed.client.clone())
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())
}

fn verification_request_for(profile_key: &str) -> Result<VerificationRequest, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .and_then(|managed| managed.verification_request.clone())
        .ok_or_else(|| "M_WEAVE_E2EE_VERIFICATION_NOT_FOUND".to_string())
}

fn sas_for(profile_key: &str) -> Result<SasVerification, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .and_then(|managed| managed.sas_verification.clone())
        .ok_or_else(|| "M_WEAVE_E2EE_VERIFICATION_NOT_FOUND".to_string())
}

fn update_verification(
    profile_key: &str,
    request: Option<VerificationRequest>,
    sas: Option<SasVerification>,
) -> Result<(), String> {
    let mut guard = clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?;
    let managed = guard
        .get_mut(profile_key)
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())?;
    managed.verification_request = request;
    managed.sas_verification = sas;
    Ok(())
}

fn verification_json(profile_key: &str) -> Result<Value, String> {
    let request = verification_request_for(profile_key).ok();
    let mut sas = sas_for(profile_key).ok();
    if sas.is_none() {
        if let Some(VerificationRequestState::Transitioned { verification }) =
            request.as_ref().map(VerificationRequest::state)
        {
            sas = match verification {
                Verification::SasV1(value) => Some(value),
                _ => None,
            };
            if let Some(value) = sas.clone() {
                update_verification(profile_key, request.clone(), Some(value))?;
            }
        }
    }
    if let Some(sas) = sas {
        let emojis = sas
            .emoji()
            .map(|values| {
                values
                    .into_iter()
                    .map(|emoji| json!({ "symbol": emoji.symbol, "label": emoji.description }))
                    .collect::<Vec<_>>()
            })
            .unwrap_or_default();
        let numbers = sas
            .decimals()
            .map(|(one, two, three)| vec![one, two, three])
            .unwrap_or_default();
        let phase = if sas.is_done() {
            "done"
        } else if sas.is_cancelled() {
            "cancelled"
        } else if sas.can_be_presented() {
            "compareSas"
        } else {
            "waitingForOtherDevice"
        };
        return Ok(json!({ "phase": phase, "sasNumbers": numbers, "sasEmojis": emojis }));
    }
    if let Some(request) = request {
        let phase = match request.state() {
            VerificationRequestState::Created { .. } => "waitingForOtherDevice",
            VerificationRequestState::Requested { .. } => "incomingRequest",
            VerificationRequestState::Ready { .. } => "chooseMethod",
            VerificationRequestState::Transitioned { .. } => "waitingForOtherDevice",
            VerificationRequestState::Done => "done",
            VerificationRequestState::Cancelled(_) => "cancelled",
        };
        return Ok(json!({ "phase": phase }));
    }
    Ok(json!({ "phase": "none" }))
}

fn project_timeline_event(event: &TimelineEvent) -> Option<Value> {
    if event.encryption_info().is_none() {
        return None;
    }
    let raw = serde_json::to_value(event.raw()).ok()?;
    if raw.get("type").and_then(Value::as_str) != Some("m.room.message") {
        return None;
    }
    let body = raw.pointer("/content/body").and_then(Value::as_str)?;
    Some(json!({
        "eventId": event.event_id().map(|value| value.to_string()).unwrap_or_default(),
        "sender": event.sender().map(|value| value.to_string()).unwrap_or_default(),
        "originServerTs": raw.get("origin_server_ts").and_then(Value::as_u64).unwrap_or_default(),
        "body": body,
        "contentType": "encryptedText",
    }))
}

fn decryption_diagnostics(
    events: &[TimelineEvent],
    to_device: &ToDeviceDiagnostics,
    peer_device: &PeerDeviceConvergenceDiagnostics,
) -> Value {
    let mut decrypted = 0_u64;
    let mut unable_to_decrypt = 0_u64;
    let mut plaintext = 0_u64;
    let mut reasons = BTreeMap::<&'static str, u64>::new();

    for event in events {
        match &event.kind {
            TimelineEventKind::Decrypted(_) => decrypted += 1,
            TimelineEventKind::UnableToDecrypt { utd_info, .. } => {
                unable_to_decrypt += 1;
                *reasons
                    .entry(unable_to_decrypt_reason(utd_info.reason.clone()))
                    .or_default() += 1;
            }
            TimelineEventKind::PlainText { .. } => plaintext += 1,
        }
    }

    json!({
        "eventCount": events.len(),
        "decryptedCount": decrypted,
        "unableToDecryptCount": unable_to_decrypt,
        "plaintextCount": plaintext,
        "reasonCounts": reasons,
        "toDeviceDecryptedCount": to_device.decrypted,
        "toDeviceDecryptedRoomKeyCount": to_device.decrypted_room_key,
        "toDeviceDecryptedForwardedRoomKeyCount": to_device.decrypted_forwarded_room_key,
        "toDeviceDecryptedOtherCount": to_device.decrypted_other,
        "toDeviceDecryptedUnknownTypeCount": to_device.decrypted_unknown_type,
        "toDeviceUnableToDecryptCount": to_device.unable_to_decrypt_count(),
        "toDevicePlaintextCount": to_device.plaintext,
        "toDeviceInvalidCount": to_device.invalid,
        "toDeviceReasonCounts": to_device.reason_counts(),
        "joinedPeerCount": peer_device.joined_peer_count,
        "authoritativeDeviceCount": peer_device.authoritative_device_count,
        "sdkDeviceCount": peer_device.sdk_device_count,
        "sdkUsableDeviceCount": peer_device.sdk_usable_device_count,
        "sdkDeletedDeviceCount": peer_device.sdk_deleted_device_count,
        "sdkBlacklistedDeviceCount": peer_device.sdk_blacklisted_device_count,
        "sdkMissingCurve25519Count": peer_device.sdk_missing_curve25519_count,
        "sdkMissingAuthoritativeDeviceCount": peer_device.sdk_missing_authoritative_device_count,
        "sdkUnexpectedDeviceCount": peer_device.sdk_unexpected_device_count,
        "deviceQueryAttemptCount": peer_device.query_attempt_count,
        "convergedPeerCount": peer_device.converged_peer_count,
        "pendingPeerCount": peer_device.pending_peer_count,
        "rejectedPeerCount": peer_device.rejected_peer_count,
        "blockedPeerCount": peer_device.blocked_peer_count,
        "invalidPeerCount": peer_device.invalid_peer_count,
    })
}

fn unable_to_decrypt_reason(reason: UnableToDecryptReason) -> &'static str {
    match reason {
        UnableToDecryptReason::MissingMegolmSession { .. } => "missingMegolmSession",
        UnableToDecryptReason::MalformedEncryptedEvent => "malformedEncryptedEvent",
        UnableToDecryptReason::UnknownMegolmMessageIndex => "unknownMegolmMessageIndex",
        UnableToDecryptReason::MegolmDecryptionFailure => "megolmDecryptionFailure",
        UnableToDecryptReason::PayloadDeserializationFailure => "payloadDeserializationFailure",
        UnableToDecryptReason::MismatchedIdentityKeys => "mismatchedIdentityKeys",
        UnableToDecryptReason::SenderIdentityNotTrusted(_) => "senderIdentityNotTrusted",
        _ => "unknown",
    }
}

fn recovery_state_name(state: RecoveryState) -> &'static str {
    match state {
        RecoveryState::Unknown => "unknown",
        RecoveryState::Enabled => "enabled",
        RecoveryState::Disabled => "disabled",
        RecoveryState::Incomplete => "incomplete",
    }
}

fn bootstrap_recovery_error_code(error: &RecoveryError) -> &'static str {
    match error {
        RecoveryError::BackupExistsOnServer => "M_WEAVE_E2EE_RECOVERY_BACKUP_EXISTS",
        RecoveryError::Sdk(_) => "M_WEAVE_E2EE_RECOVERY_BACKUP_SETUP",
        RecoveryError::SecretStorage(_) => "M_WEAVE_E2EE_RECOVERY_SECRET_STORAGE",
    }
}

fn matrix_sdk_error_code(error: &matrix_sdk::Error, fallback: &str) -> String {
    if let matrix_sdk::Error::Http(http_error) = error {
        return matrix_error_kind_code(http_error.client_api_error_kind(), fallback);
    }
    fallback.to_owned()
}

fn matrix_error_kind_code(kind: Option<&ErrorKind>, fallback: &str) -> String {
    kind.map(|value| value.errcode().as_str().to_owned())
        .unwrap_or_else(|| fallback.to_owned())
}

fn validate_identifier(value: &str, kind: &str) -> Result<(), String> {
    if value.len() < 8
        || value.len() > 512
        || !value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"._=:@/-".contains(&byte))
    {
        return Err(format!(
            "M_WEAVE_E2EE_{}_INVALID",
            kind.to_ascii_uppercase()
        ));
    }
    Ok(())
}

fn json_result(result: Result<Value, String>) -> String {
    serde_json::to_string(&result.unwrap_or_else(|errcode| {
        json!({
            "errcode": errcode,
            "error": "The Matrix E2EE operation could not be completed.",
        })
    }))
    .unwrap_or_else(|_| "{\"errcode\":\"M_WEAVE_E2EE_SERIALIZATION\"}".to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use matrix_sdk::encryption::secret_storage::SecretStorageError;

    #[test]
    fn platform_roots_remain_the_default() {
        assert!(build_http_client(HeaderMap::new(), "").is_ok());
    }

    #[test]
    fn matrix_request_deadlines_are_shorter_than_the_foreground_retry_window() {
        assert!(MATRIX_CONNECT_TIMEOUT < MATRIX_REQUEST_TIMEOUT);
        assert!(MATRIX_REQUEST_TIMEOUT <= Duration::from_secs(15));
    }

    #[test]
    fn verification_target_prefers_newest_unverified_sibling_stably() {
        assert_eq!(
            verification_target_order(false, 2, "DEVICE_B", false, 1, "DEVICE_A"),
            Ordering::Less
        );
        assert_eq!(
            verification_target_order(false, 1, "DEVICE_A", true, 2, "DEVICE_B"),
            Ordering::Less
        );
        assert_eq!(
            verification_target_order(false, 2, "DEVICE_A", false, 2, "DEVICE_B"),
            Ordering::Less
        );
    }

    #[test]
    fn invalid_extra_root_fails_closed() {
        let result = build_http_client(HeaderMap::new(), "not a PEM certificate");

        assert_eq!(result.err().as_deref(), Some("M_WEAVE_E2EE_TLS_ROOT"));
    }

    #[test]
    fn recovery_bootstrap_errors_are_support_safe_and_phase_specific() {
        assert_eq!(
            bootstrap_recovery_error_code(&RecoveryError::BackupExistsOnServer),
            "M_WEAVE_E2EE_RECOVERY_BACKUP_EXISTS"
        );
        assert_eq!(
            bootstrap_recovery_error_code(&RecoveryError::SecretStorage(
                SecretStorageError::MissingKeyInfo { key_id: None }
            )),
            "M_WEAVE_E2EE_RECOVERY_SECRET_STORAGE"
        );
    }

    #[test]
    fn matrix_server_errcodes_survive_the_native_sync_boundary() {
        assert_eq!(
            matrix_error_kind_code(Some(&ErrorKind::MissingToken), "M_WEAVE_E2EE_SYNC"),
            "M_MISSING_TOKEN"
        );
        assert_eq!(
            matrix_error_kind_code(None, "M_WEAVE_E2EE_SYNC"),
            "M_WEAVE_E2EE_SYNC"
        );
    }

    #[test]
    fn encrypted_send_detects_new_or_changed_member_sets() {
        let initial = vec!["@author:api.weave.test".to_string()];
        let shared = vec![
            "@author:api.weave.test".to_string(),
            "@collaborator:api.weave.test".to_string(),
        ];

        assert!(joined_member_set_changed(None, &initial));
        assert!(!joined_member_set_changed(Some(&initial), &initial));
        assert!(joined_member_set_changed(Some(&initial), &shared));
    }

    #[test]
    fn changed_membership_forces_a_current_query_despite_cached_devices() {
        assert!(requires_explicit_device_query(true, true));
        assert!(requires_explicit_device_query(true, false));
        assert!(requires_explicit_device_query(false, false));
        assert!(!requires_explicit_device_query(false, true));
    }

    #[test]
    fn new_member_device_rotates_a_persisted_outbound_room_key_once() {
        let initial = RoomSecurityFingerprint {
            member_ids: vec![
                "@author:api.weave.test".to_string(),
                "@collaborator:api.weave.test".to_string(),
            ],
            member_device_ids: vec!["@collaborator:api.weave.test|DEVICE_A".to_string()],
        };
        let expanded = RoomSecurityFingerprint {
            member_ids: initial.member_ids.clone(),
            member_device_ids: vec![
                "@collaborator:api.weave.test|DEVICE_A".to_string(),
                "@collaborator:api.weave.test|DEVICE_B".to_string(),
            ],
        };

        assert!(room_security_fingerprint_changed(None, &initial));
        assert!(!room_security_fingerprint_changed(Some(&initial), &initial));
        assert!(room_security_fingerprint_changed(Some(&initial), &expanded));
    }

    #[test]
    fn sync_cache_cannot_satisfy_the_first_encrypted_send_barrier() {
        let warmed = RoomSecurityFingerprint {
            member_ids: vec!["@author:api.weave.test".to_string()],
            member_device_ids: Vec::new(),
        };

        assert!(!requires_pre_send_member_reload(
            RoomSecurityRefresh::Sync,
            None,
            None,
        ));
        assert!(requires_pre_send_member_reload(
            RoomSecurityRefresh::PreSend,
            None,
            None,
        ));
        assert!(requires_pre_send_member_reload(
            RoomSecurityRefresh::PreSend,
            None,
            Some(&warmed),
        ));
        assert!(requires_pre_send_member_reload(
            RoomSecurityRefresh::PreSend,
            Some(&warmed),
            Some(&warmed),
        ));
        let expanded = RoomSecurityFingerprint {
            member_ids: vec![
                "@author:api.weave.test".to_string(),
                "@collaborator:api.weave.test".to_string(),
            ],
            member_device_ids: vec!["@collaborator:api.weave.test|DEVICE_A".to_string()],
        };
        assert!(requires_pre_send_member_reload(
            RoomSecurityRefresh::PreSend,
            Some(&warmed),
            Some(&expanded),
        ));
    }

    #[test]
    fn encrypted_send_rejects_blacklisted_or_keyless_peer_devices() {
        assert!(is_eligible_peer_device(false, true));
        assert!(!is_eligible_peer_device(true, true));
        assert!(!is_eligible_peer_device(false, false));
        assert!(!is_eligible_peer_device(true, false));
    }

    #[test]
    fn pre_send_device_query_retries_only_until_the_exact_device_set_converges() {
        assert!(should_retry_peer_device_query(
            RoomSecurityRefresh::PreSend,
            0,
            PeerDeviceConvergenceState::Pending,
        ));
        assert!(should_retry_peer_device_query(
            RoomSecurityRefresh::PreSend,
            PRE_SEND_DEVICE_QUERY_ATTEMPTS - 2,
            PeerDeviceConvergenceState::Rejected,
        ));
        assert!(!should_retry_peer_device_query(
            RoomSecurityRefresh::PreSend,
            PRE_SEND_DEVICE_QUERY_ATTEMPTS - 1,
            PeerDeviceConvergenceState::Invalid,
        ));
        assert!(!should_retry_peer_device_query(
            RoomSecurityRefresh::PreSend,
            0,
            PeerDeviceConvergenceState::Converged,
        ));
        assert!(!should_retry_peer_device_query(
            RoomSecurityRefresh::Sync,
            0,
            PeerDeviceConvergenceState::Pending,
        ));
    }

    #[test]
    fn peer_device_convergence_distinguishes_publication_validation_and_policy() {
        let current = BTreeSet::from(["DEVICE_A".to_string()]);
        let accepted = PeerSdkDeviceSet {
            all: current.clone(),
            usable: current.clone(),
            ..Default::default()
        };
        assert_eq!(
            classify_peer_device_convergence(&current, &accepted),
            PeerDeviceConvergenceState::Converged
        );
        assert_eq!(
            classify_peer_device_convergence(&BTreeSet::new(), &accepted),
            PeerDeviceConvergenceState::Pending
        );
        assert_eq!(
            classify_peer_device_convergence(&current, &PeerSdkDeviceSet::default()),
            PeerDeviceConvergenceState::Rejected
        );
        assert_eq!(
            classify_peer_device_convergence(
                &current,
                &PeerSdkDeviceSet {
                    deleted: current.clone(),
                    ..Default::default()
                },
            ),
            PeerDeviceConvergenceState::Rejected
        );
        assert_eq!(
            classify_peer_device_convergence(
                &current,
                &PeerSdkDeviceSet {
                    all: current.clone(),
                    blacklisted: current.clone(),
                    ..Default::default()
                },
            ),
            PeerDeviceConvergenceState::Blocked
        );
        assert_eq!(
            classify_peer_device_convergence(
                &current,
                &PeerSdkDeviceSet {
                    all: current.clone(),
                    missing_curve25519: current.clone(),
                    ..Default::default()
                },
            ),
            PeerDeviceConvergenceState::Invalid
        );
    }

    #[test]
    fn peer_device_diagnostics_expose_counts_without_identifiers() {
        let authoritative = BTreeSet::from(["DEVICE_A".to_string(), "DEVICE_B".to_string()]);
        let sdk_devices = PeerSdkDeviceSet {
            all: BTreeSet::from(["DEVICE_A".to_string(), "STALE".to_string()]),
            usable: BTreeSet::from(["DEVICE_A".to_string()]),
            blacklisted: BTreeSet::from(["STALE".to_string()]),
            ..Default::default()
        };
        let mut diagnostics = PeerDeviceConvergenceDiagnostics {
            joined_peer_count: 1,
            query_attempt_count: 3,
            ..Default::default()
        };
        diagnostics.record(
            &authoritative,
            &sdk_devices,
            PeerDeviceConvergenceState::Rejected,
        );

        assert_eq!(diagnostics.authoritative_device_count, 2);
        assert_eq!(diagnostics.sdk_device_count, 2);
        assert_eq!(diagnostics.sdk_usable_device_count, 1);
        assert_eq!(diagnostics.sdk_missing_authoritative_device_count, 1);
        assert_eq!(diagnostics.sdk_unexpected_device_count, 1);
        assert_eq!(diagnostics.rejected_peer_count, 1);
    }

    #[test]
    fn pre_send_device_query_window_is_bounded_and_user_visible() {
        let maximum_wait =
            PRE_SEND_DEVICE_QUERY_DELAY.saturating_mul((PRE_SEND_DEVICE_QUERY_ATTEMPTS - 1) as u32);

        assert_eq!(maximum_wait, Duration::from_millis(4_500));
        assert!(maximum_wait < Duration::from_secs(5));
    }

    #[test]
    fn decryption_failure_reasons_are_support_safe_and_specific() {
        assert_eq!(
            unable_to_decrypt_reason(UnableToDecryptReason::MissingMegolmSession {
                withheld_code: None,
            }),
            "missingMegolmSession"
        );
        assert_eq!(
            unable_to_decrypt_reason(UnableToDecryptReason::MismatchedIdentityKeys),
            "mismatchedIdentityKeys"
        );
        assert_eq!(
            unable_to_decrypt_reason(UnableToDecryptReason::MalformedEncryptedEvent),
            "malformedEncryptedEvent"
        );
    }

    #[test]
    fn verification_request_identity_is_taken_from_the_processed_event() {
        let request = ProcessedToDeviceEvent::PlainText(
            serde_json::from_value(json!({
                "sender": "@member:api.weave.test",
                "type": "m.key.verification.request",
                "content": {
                    "transaction_id": "verification-transaction",
                    "from_device": "WEAVE_DEVICE",
                    "methods": ["m.sas.v1"],
                    "timestamp": 1,
                },
            }))
            .expect("verification request should be valid raw JSON"),
        );
        let unrelated = ProcessedToDeviceEvent::PlainText(
            serde_json::from_value(json!({
                "sender": "@member:api.weave.test",
                "type": "m.room_key",
                "content": { "transaction_id": "not-verification" },
            }))
            .expect("room key should be valid raw JSON"),
        );

        let (sender, transaction_id) =
            verification_request_identity(&request).expect("request identity should be found");
        assert_eq!(sender.as_str(), "@member:api.weave.test");
        assert_eq!(transaction_id, "verification-transaction");
        assert!(verification_request_identity(&unrelated).is_none());
    }

    #[test]
    fn olm_unwedge_dummy_arms_a_single_fail_closed_rotation_latch() {
        let dummy: Raw<AnyToDeviceEvent> = serde_json::from_value(json!({
            "sender": "@member:api.weave.test",
            "type": "m.dummy",
            "content": {},
        }))
        .expect("dummy event should be valid raw JSON");
        let room_key: Raw<AnyToDeviceEvent> = serde_json::from_value(json!({
            "sender": "@member:api.weave.test",
            "type": "m.room_key",
            "content": {},
        }))
        .expect("room key should be valid raw JSON");

        assert!(is_decrypted_olm_dummy(&dummy));
        assert!(!is_decrypted_olm_dummy(&room_key));
        assert!(!olm_unwedge_rotation_is_pending(None));
        assert!(!olm_unwedge_rotation_is_pending(Some(&[])));
        assert!(!olm_unwedge_rotation_is_pending(Some(&[0])));
        assert!(olm_unwedge_rotation_is_pending(Some(&[1])));
        // Unknown persisted values rotate once instead of silently accepting
        // a potentially stale Megolm sharing record.
        assert!(olm_unwedge_rotation_is_pending(Some(&[2, 3])));
    }

    #[test]
    fn to_device_diagnostics_expose_only_bounded_reason_counts() {
        let diagnostics = ToDeviceDiagnostics {
            decrypted: 2,
            decrypted_room_key: 1,
            decrypted_forwarded_room_key: 0,
            decrypted_other: 1,
            decrypted_unknown_type: 0,
            decryption_failure: 1,
            unverified_sender_device: 1,
            no_olm_machine: 0,
            encryption_disabled: 0,
            plaintext: 3,
            invalid: 0,
        };

        assert_eq!(diagnostics.unable_to_decrypt_count(), 2);
        assert_eq!(
            diagnostics.decrypted_room_key
                + diagnostics.decrypted_forwarded_room_key
                + diagnostics.decrypted_other
                + diagnostics.decrypted_unknown_type,
            diagnostics.decrypted
        );
        assert_eq!(
            diagnostics.reason_counts(),
            BTreeMap::from([("decryptionFailure", 1), ("unverifiedSenderDevice", 1),])
        );
    }
}
