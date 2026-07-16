use matrix_sdk::{
    authentication::{matrix::MatrixSession, SessionTokens},
    config::SyncSettings,
    deserialized_responses::{
        ProcessedToDeviceEvent, TimelineEvent, TimelineEventKind, ToDeviceUnableToDecryptReason,
        UnableToDecryptReason,
    },
    encryption::{
        recovery::{RecoveryError, RecoveryState},
        verification::{
            SasVerification, Verification, VerificationRequest, VerificationRequestState,
        },
        BackupDownloadStrategy, EncryptionSettings, VerificationState,
    },
    room::{MessagesOptions, RoomMember},
    ruma::{
        api::client::{
            receipt::create_receipt::v3::ReceiptType,
            room::create_room::v3::{Request as CreateRoomRequest, RoomPreset},
        },
        api::error::ErrorKind,
        api::MatrixVersion,
        events::receipt::ReceiptThread,
        events::{
            key::verification::{request::ToDeviceKeyVerificationRequestEvent, VerificationMethod},
            room::encryption::RoomEncryptionEventContent,
            room::message::RoomMessageEventContent,
            InitialStateEvent,
        },
        OwnedDeviceId, OwnedEventId, OwnedRoomId, OwnedUserId, UInt,
    },
    store::RoomLoadSettings,
    Client, Room, RoomMemberships, SessionMeta,
};
use reqwest::header::{HeaderMap, HeaderName, HeaderValue};
use serde_json::{json, Value};
use std::{
    collections::{BTreeMap, HashMap},
    path::Path,
    sync::{Arc, Mutex, OnceLock},
    time::Duration,
};
use tokio::{
    sync::{watch, Mutex as AsyncMutex},
    task::JoinHandle,
};

const DEVICE_ID_HEADER: &str = "x-weave-matrix-device-id";
const BACKGROUND_SYNC_POLL_INTERVAL: Duration = Duration::from_secs(1);
const BACKGROUND_SYNC_LONG_POLL_TIMEOUT: Duration = Duration::from_secs(10);
const BACKGROUND_SYNC_MAX_BACKOFF: Duration = Duration::from_secs(30);
const BACKGROUND_SYNC_BARRIER_TIMEOUT: Duration = Duration::from_secs(15);
const PRE_SEND_DEVICE_QUERY_ATTEMPTS: usize = 3;
const PRE_SEND_DEVICE_QUERY_DELAY: Duration = Duration::from_millis(150);

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
    sync_start_gate: Arc<AsyncMutex<()>>,
    background_sync_progress: watch::Sender<BackgroundSyncProgress>,
    background_sync_stop: Option<watch::Sender<bool>>,
    background_sync: Option<JoinHandle<()>>,
    to_device_diagnostics: ToDeviceDiagnostics,
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
    Background,
    PreSend,
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
struct BackgroundSyncProgress {
    generation: u64,
    next_batch: String,
    enabled_rooms: u64,
    converged_rooms: u64,
    terminal_error: Option<String>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct CompletedSyncCycle {
    next_batch: String,
    enabled_rooms: u64,
    converged_rooms: u64,
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
            // Reject new operations, let the continuous-sync owner finish its
            // current response, and reuse the same I/O gate for the replacement.
            existing.accepting_operations = false;
            (existing.matrix_io_gate.clone(), true)
        } else {
            (Arc::new(AsyncMutex::new(())), false)
        }
    };
    if replacing_existing {
        stop_background_sync(&profile_key).await?;
    }
    let _matrix_io_guard = matrix_io_gate.lock().await;
    if replacing_existing {
        let replaced = clients()
            .lock()
            .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
            .remove(&profile_key);
        drop(replaced);
    }

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

    let handler_client = client.clone();
    let handler_profile_key = profile_key.clone();
    client.add_event_handler(move |event: ToDeviceKeyVerificationRequestEvent| {
        let client = handler_client.clone();
        let profile_key = handler_profile_key.clone();
        async move {
            if let Some(request) = client
                .encryption()
                .get_verification_request(&event.sender, event.content.transaction_id.as_str())
                .await
            {
                let _ = update_verification(&profile_key, Some(request), None);
            }
        }
    });

    let (background_sync_progress, _) = watch::channel(BackgroundSyncProgress::default());

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
                room_security_fingerprints: HashMap::new(),
                pre_send_security_fingerprints: HashMap::new(),
                accepting_operations: true,
                matrix_io_gate: matrix_io_gate.clone(),
                room_security_gate: Arc::new(AsyncMutex::new(())),
                sync_start_gate: Arc::new(AsyncMutex::new(())),
                background_sync_progress,
                background_sync_stop: None,
                background_sync: None,
                to_device_diagnostics: ToDeviceDiagnostics::default(),
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
    let mut builder = reqwest::Client::builder().default_headers(default_headers);
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
    let sync_start_gate = sync_start_gate_for(profile_key)?;
    let _sync_start_guard = sync_start_gate.lock().await;

    // Once continuous sync owns the Matrix cursor, an explicit readiness call
    // observes its latest fully processed cycle. Cancelling an in-flight sync
    // after the facade has returned a to-device room key can advance the
    // persisted cursor before the crypto store commits that key. That produces
    // the exact cold-device failure where a later sync can no longer recover
    // the first Megolm envelope. One owner processes the response atomically;
    // foreground callers read its committed progress instead of racing it or
    // waiting for an unrelated long-poll after the needed event already landed.
    if let Some(progress) = running_background_sync_observer(profile_key)? {
        let completed = observe_completed_background_sync(progress).await?;
        return Ok(json!({
            "nextBatch": completed.next_batch,
            "enabledRooms": completed.enabled_rooms,
            "convergedRooms": completed.converged_rooms,
            "backgroundSync": "observed",
        }));
    }

    let completed = complete_sync_cycle(
        profile_key,
        Duration::from_secs(0),
        None,
        "M_WEAVE_E2EE_SYNC",
    )
    .await?;
    let progress = background_sync_progress_for(profile_key)?;
    publish_completed_sync(&progress, &completed);
    start_background_sync(profile_key, completed.next_batch.clone()).await?;

    Ok(json!({
        "nextBatch": completed.next_batch,
        "enabledRooms": completed.enabled_rooms,
        "convergedRooms": completed.converged_rooms,
        "backgroundSync": "started",
    }))
}

async fn complete_sync_cycle(
    profile_key: &str,
    timeout: Duration,
    since: Option<&str>,
    error_code: &str,
) -> Result<CompletedSyncCycle, String> {
    // Sync processing, out-of-band device queries, and Megolm send setup all
    // mutate one SDK crypto store. Keep the whole processed sync cycle under
    // the same profile gate used by send so the pre-send recipient snapshot
    // cannot race a device-list update or an ephemeral to-device delivery.
    let matrix_io_gate = matrix_io_gate_for(profile_key)?;
    let _matrix_io_guard = matrix_io_gate.lock().await;
    let client = client_for(profile_key)?;
    let settings = sync_settings(timeout, since);
    let mut response = client
        .sync_once(settings)
        .await
        .map_err(|error| matrix_sdk_error_code(&error, error_code))?;
    record_to_device_diagnostics(profile_key, &response.to_device)?;
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
        let (newly_enabled_rooms, newly_converged_rooms) =
            converge_joined_room_security(profile_key, &client).await?;
        enabled_rooms = enabled_rooms.saturating_add(newly_enabled_rooms);
        converged_rooms = converged_rooms.max(newly_converged_rooms);
    }
    Ok(CompletedSyncCycle {
        next_batch: response.next_batch,
        enabled_rooms,
        converged_rooms,
    })
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

fn to_device_diagnostics(profile_key: &str) -> Result<ToDeviceDiagnostics, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .map(|managed| managed.to_device_diagnostics.clone())
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
        refresh_active_member_device_keys(
            profile_key,
            client,
            &room,
            RoomSecurityRefresh::Background,
        )
        .await?;
        converged_rooms += 1;
    }
    Ok((enabled_rooms, converged_rooms))
}

fn sync_start_gate_for(profile_key: &str) -> Result<Arc<AsyncMutex<()>>, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .filter(|managed| managed.accepting_operations)
        .map(|managed| managed.sync_start_gate.clone())
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())
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

fn running_background_sync_observer(
    profile_key: &str,
) -> Result<Option<watch::Receiver<BackgroundSyncProgress>>, String> {
    let mut guard = clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?;
    let managed = guard
        .get_mut(profile_key)
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())?;
    let Some(task) = managed.background_sync.as_ref() else {
        return Ok(None);
    };
    if !task.is_finished() {
        return Ok(Some(managed.background_sync_progress.subscribe()));
    }

    managed.background_sync.take();
    managed.background_sync_stop.take();
    if let Some(error_code) = managed
        .background_sync_progress
        .borrow()
        .terminal_error
        .clone()
    {
        return Err(error_code);
    }
    Err("M_WEAVE_E2EE_BACKGROUND_SYNC".to_string())
}

async fn observe_completed_background_sync(
    mut progress: watch::Receiver<BackgroundSyncProgress>,
) -> Result<BackgroundSyncProgress, String> {
    let baseline_generation = progress.borrow().generation;
    tokio::time::timeout(BACKGROUND_SYNC_BARRIER_TIMEOUT, async move {
        loop {
            let observed = progress.borrow().clone();
            if let Some(error_code) = observed.terminal_error {
                return Err(error_code);
            }
            // A foreground refresh is a freshness barrier, not a cache read.
            // Returning an older completed generation lets the caller fetch a
            // newly encrypted timeline before the background owner has
            // committed the matching to-device room key. Matrix to-device
            // events are ephemeral, so wait for the single sync owner to
            // publish a cycle newer than the one visible at subscription time.
            if observed.generation > baseline_generation {
                return Ok(observed);
            }
            progress
                .changed()
                .await
                .map_err(|_| "M_WEAVE_E2EE_BACKGROUND_SYNC".to_string())?;
        }
    })
    .await
    .map_err(|_| "M_WEAVE_E2EE_SYNC_TIMEOUT".to_string())?
}

fn background_sync_progress_for(
    profile_key: &str,
) -> Result<watch::Sender<BackgroundSyncProgress>, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
        .map(|managed| managed.background_sync_progress.clone())
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())
}

fn publish_completed_sync(
    progress: &watch::Sender<BackgroundSyncProgress>,
    completed: &CompletedSyncCycle,
) {
    progress.send_modify(|state| {
        state.generation = state.generation.saturating_add(1);
        state.next_batch = completed.next_batch.clone();
        state.enabled_rooms = completed.enabled_rooms;
        state.converged_rooms = completed.converged_rooms;
        state.terminal_error = None;
    });
}

async fn start_background_sync(profile_key: &str, initial_cursor: String) -> Result<(), String> {
    // Starting a cursor owner is a handoff, not a replacement. Always await
    // termination of the previous owner before the next task can issue /sync.
    stop_background_sync(profile_key).await?;
    let progress = background_sync_progress_for(profile_key)?;
    progress.send_modify(|state| state.terminal_error = None);
    let sync_profile_key = profile_key.to_string();
    let sync_progress = progress.clone();
    let (stop_sender, stop_receiver) = watch::channel(false);
    let mut guard = clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?;
    let managed = guard
        .get_mut(profile_key)
        .filter(|managed| managed.accepting_operations)
        .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())?;
    debug_assert!(managed.background_sync.is_none());
    debug_assert!(managed.background_sync_stop.is_none());
    // Publish the task and its cooperative stop handle while the client
    // registry is locked. The spawned loop must read that registry before its
    // first /sync, so lifecycle replacement can never observe an unowned task.
    let task = tokio::spawn(async move {
        run_background_sync(
            sync_profile_key,
            sync_progress,
            stop_receiver,
            initial_cursor,
        )
        .await;
    });
    managed.background_sync_stop = Some(stop_sender);
    managed.background_sync = Some(task);
    Ok(())
}

async fn run_background_sync(
    profile_key: String,
    progress: watch::Sender<BackgroundSyncProgress>,
    mut stop: watch::Receiver<bool>,
    initial_cursor: String,
) {
    let mut consecutive_failures = 0_u32;
    let mut cursor = initial_cursor;
    loop {
        if background_sync_stop_requested(&stop) {
            return;
        }
        let delay = match complete_sync_cycle(
            &profile_key,
            BACKGROUND_SYNC_LONG_POLL_TIMEOUT,
            Some(cursor.as_str()),
            "M_WEAVE_E2EE_BACKGROUND_SYNC",
        )
        .await
        {
            Ok(completed) => {
                consecutive_failures = 0;
                cursor = completed.next_batch.clone();
                publish_completed_sync(&progress, &completed);
                BACKGROUND_SYNC_POLL_INTERVAL
            }
            Err(error_code) => {
                consecutive_failures = consecutive_failures.saturating_add(1);
                if is_terminal_matrix_session_error(&error_code) {
                    progress.send_modify(|state| {
                        state.terminal_error = Some(error_code.clone());
                    });
                    tracing::warn!(
                        error_code,
                        "Matrix background sync stopped after terminal session rejection"
                    );
                    return;
                }
                tracing::warn!(
                    error_code,
                    consecutive_failures,
                    "Matrix background sync will retry with bounded backoff"
                );
                background_sync_retry_delay(consecutive_failures)
            }
        };
        if background_sync_stop_requested(&stop)
            || wait_for_background_sync_delay_or_stop(&mut stop, delay).await
        {
            return;
        }
    }
}

fn background_sync_stop_requested(stop: &watch::Receiver<bool>) -> bool {
    *stop.borrow()
}

async fn wait_for_background_sync_delay_or_stop(
    stop: &mut watch::Receiver<bool>,
    delay: Duration,
) -> bool {
    tokio::select! {
        _ = tokio::time::sleep(delay) => false,
        changed = stop.changed() => changed.is_err() || background_sync_stop_requested(stop),
    }
}

fn background_sync_retry_delay(consecutive_failures: u32) -> Duration {
    let exponent = consecutive_failures.clamp(1, 5);
    Duration::from_secs((1_u64 << exponent).min(BACKGROUND_SYNC_MAX_BACKOFF.as_secs()))
}

fn is_terminal_matrix_session_error(error_code: &str) -> bool {
    matches!(error_code, "M_MISSING_TOKEN" | "M_UNKNOWN_TOKEN")
}

async fn stop_background_sync(profile_key: &str) -> Result<(), String> {
    let (stop, task) = {
        let mut guard = clients()
            .lock()
            .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?;
        let managed = guard
            .get_mut(profile_key)
            .ok_or_else(|| "M_WEAVE_E2EE_NOT_INITIALIZED".to_string())?;
        (
            managed.background_sync_stop.take(),
            managed.background_sync.take(),
        )
    };
    if let Some(stop) = stop {
        let _ = stop.send(true);
    }
    if let Some(task) = task {
        if let Err(error) = task.await {
            tracing::warn!(?error, "Matrix background sync task stopped unexpectedly");
        }
    }
    Ok(())
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
    let client = client_for(profile_key)?;
    let mut request = CreateRoomRequest::new();
    request.name = Some(title.to_owned());
    request.preset = Some(RoomPreset::PrivateChat);
    request.initial_state = vec![InitialStateEvent::with_empty_state_key(
        RoomEncryptionEventContent::with_recommended_defaults(),
    )
    .to_raw_any()];
    let room = client
        .create_room(request)
        .await
        .map_err(|error| matrix_sdk_error_code(&error, "M_WEAVE_E2EE_CREATE_ROOM"))?;

    // The facade commits room creation before it appears in the client's
    // joined-room cache. Cross the same single-owner sync barrier used by
    // normal Chat refreshes so the returned conversation is immediately
    // navigable and its encryption state is available before the first send.
    sync_inner(profile_key).await?;

    Ok(json!({
        "roomId": room.room_id().to_string(),
        "title": title,
        "encrypted": true,
    }))
}

async fn rooms_inner(profile_key: &str) -> Result<Value, String> {
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
    let decryption = decryption_diagnostics(&response.chunk, &to_device_diagnostics(profile_key)?);
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
    let response = room
        .send(RoomMessageEventContent::text_plain(body))
        .await
        .map_err(|_| "M_WEAVE_E2EE_SEND".to_string())?;
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
        .members_no_sync(RoomMemberships::ACTIVE)
        .await
        .map_err(|_| "M_WEAVE_E2EE_ROOM_MEMBERS".to_string())?;
    let observed_member_ids = active_member_ids(&members);
    let cached_fingerprint =
        cached_room_security_fingerprint(profile_key, room.room_id().as_str())?;
    let cached_pre_send_fingerprint =
        cached_pre_send_security_fingerprint(profile_key, room.room_id().as_str())?;

    let member_set_changed = active_member_set_changed(
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
        // projection before it decides which devices receive the Megolm session.
        // Background convergence may warm an incomplete first-room snapshot, but
        // it cannot satisfy the first security-sensitive send barrier. A separate
        // pre-send fingerprint keeps subsequent sends fast until a later
        // member/device change invalidates that barrier.
        room.mark_members_missing();
        members = room
            .members(RoomMemberships::ACTIVE)
            .await
            .map_err(|_| "M_WEAVE_E2EE_ROOM_MEMBERS".to_string())?;
    }
    let encryption = client.encryption();
    let mut member_device_ids = Vec::new();

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
        let mut eligible_device_ids = cached_devices
            .devices()
            .filter(|device| {
                is_eligible_peer_device(device.is_blacklisted(), device.curve25519_key().is_some())
            })
            .map(|device| format!("{user_id}|{}", device.device_id()))
            .collect::<Vec<_>>();

        // A first room sync can mark a member as tracked while the local crypto
        // store still contains only an older device snapshot. A non-empty
        // cache therefore does not prove that the member's current app device
        // is known. Query every peer before a send; background convergence and
        // cached keys are observations, not permission to cross the barrier.
        if query_required {
            let attempts = if refresh == RoomSecurityRefresh::PreSend {
                PRE_SEND_DEVICE_QUERY_ATTEMPTS
            } else {
                1
            };
            for attempt in 0..attempts {
                encryption
                    .request_user_identity(user_id)
                    .await
                    .map_err(|_| "M_WEAVE_E2EE_MEMBER_KEYS".to_string())?;
                let refreshed_devices = encryption
                    .get_user_devices(user_id)
                    .await
                    .map_err(|_| "M_WEAVE_E2EE_MEMBER_KEYS".to_string())?;
                eligible_device_ids = refreshed_devices
                    .devices()
                    .filter(|device| {
                        is_eligible_peer_device(
                            device.is_blacklisted(),
                            device.curve25519_key().is_some(),
                        )
                    })
                    .map(|device| format!("{user_id}|{}", device.device_id()))
                    .collect::<Vec<_>>();
                if !should_retry_peer_device_query(refresh, attempt, &eligible_device_ids) {
                    break;
                }
                tokio::time::sleep(PRE_SEND_DEVICE_QUERY_DELAY).await;
            }
        }
        if refresh == RoomSecurityRefresh::PreSend && eligible_device_ids.is_empty() {
            // An active peer with no usable device cannot receive the next
            // Megolm room key. Fail before the SDK timeline send so the event
            // is never committed with an incomplete recipient set.
            return Err("M_WEAVE_E2EE_PEER_DEVICE_PENDING".to_string());
        }
        member_device_ids.extend(eligible_device_ids);
    }
    member_device_ids.sort_unstable();
    member_device_ids.dedup();

    let observed_fingerprint = RoomSecurityFingerprint {
        member_ids: active_member_ids(&members),
        member_device_ids,
    };
    if room_security_fingerprint_changed(cached_fingerprint.as_ref(), &observed_fingerprint) {
        // The canonical Matrix facade can expose a newly registered device for
        // an unchanged room member between app sessions. A current `/keys/query`
        // updates the SDK crypto store, but an already persisted outbound Megolm
        // session may still exclude that device. Rotate only when the effective
        // active member/device set changes so the next send shares a fresh room
        // key with every active device without rotating on every message.
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

fn active_member_ids(members: &[RoomMember]) -> Vec<String> {
    let mut member_ids = members
        .iter()
        .map(|member| member.user_id().to_string())
        .collect::<Vec<_>>();
    member_ids.sort_unstable();
    member_ids.dedup();
    member_ids
}

fn active_member_set_changed(cached: Option<&[String]>, observed: &[String]) -> bool {
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
    eligible_device_ids: &[String],
) -> bool {
    refresh == RoomSecurityRefresh::PreSend
        && eligible_device_ids.is_empty()
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

    Ok(json!({
        "signedIn": client.matrix_auth().logged_in(),
        "recoveryState": recovery_state_name(recovery_state),
        "crossSigningReady": cross_signing.as_ref().is_some_and(|state| state.is_complete()),
        "deviceVerified": own_device.as_ref().is_some_and(|device| device.is_verified()),
        "accountVerified": matches!(verification_state, VerificationState::Verified),
        "encryptedRoomCount": encrypted_rooms,
        "verification": verification,
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
        // sync response. Refresh the current user's device keys explicitly
        // before selecting a verification target; get_user_devices() reads
        // only the local crypto store and is not a network freshness barrier.
        encryption
            .request_user_identity(own_user_id)
            .await
            .map_err(|_| "M_WEAVE_E2EE_DEVICE".to_string())?;
        let devices = encryption
            .get_user_devices(own_user_id)
            .await
            .map_err(|_| "M_WEAVE_E2EE_DEVICE".to_string())?;
        let other_device = devices
            .devices()
            .find(|device| device.device_id() != own_device_id && !device.is_blacklisted())
            .ok_or_else(|| "M_WEAVE_E2EE_NO_OTHER_DEVICE".to_string())?;
        let request = other_device
            .request_verification_with_methods(vec![VerificationMethod::SasV1])
            .await
            .map_err(|_| "M_WEAVE_E2EE_VERIFICATION".to_string())?;
        update_verification(&profile_key, Some(request), None)?;
        verification_json(&profile_key)
    }
    .await;
    json_result(result)
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
        stop_background_sync(&profile_key).await?;
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

fn decryption_diagnostics(events: &[TimelineEvent], to_device: &ToDeviceDiagnostics) -> Value {
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

        assert!(active_member_set_changed(None, &initial));
        assert!(!active_member_set_changed(Some(&initial), &initial));
        assert!(active_member_set_changed(Some(&initial), &shared));
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
    fn background_cache_cannot_satisfy_the_first_encrypted_send_barrier() {
        let warmed = RoomSecurityFingerprint {
            member_ids: vec!["@author:api.weave.test".to_string()],
            member_device_ids: Vec::new(),
        };

        assert!(!requires_pre_send_member_reload(
            RoomSecurityRefresh::Background,
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
    fn pre_send_device_query_retries_only_until_an_eligible_device_converges() {
        let no_devices = Vec::<String>::new();
        let eligible = vec!["@peer:api.weave.test|DEVICE_A".to_string()];

        assert!(should_retry_peer_device_query(
            RoomSecurityRefresh::PreSend,
            0,
            &no_devices,
        ));
        assert!(should_retry_peer_device_query(
            RoomSecurityRefresh::PreSend,
            1,
            &no_devices,
        ));
        assert!(!should_retry_peer_device_query(
            RoomSecurityRefresh::PreSend,
            2,
            &no_devices,
        ));
        assert!(!should_retry_peer_device_query(
            RoomSecurityRefresh::PreSend,
            0,
            &eligible,
        ));
        assert!(!should_retry_peer_device_query(
            RoomSecurityRefresh::Background,
            0,
            &no_devices,
        ));
    }

    #[test]
    fn graceful_background_stop_interrupts_only_the_inter_cycle_delay() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_time()
            .build()
            .unwrap();
        runtime.block_on(async {
            let (stop, mut observer) = watch::channel(false);
            let waiter = tokio::spawn(async move {
                wait_for_background_sync_delay_or_stop(&mut observer, BACKGROUND_SYNC_MAX_BACKOFF)
                    .await
            });
            tokio::task::yield_now().await;
            assert!(!waiter.is_finished());

            stop.send(true).unwrap();
            assert!(waiter.await.unwrap());
        });
    }

    #[test]
    fn foreground_barrier_outlives_the_background_long_poll() {
        assert!(BACKGROUND_SYNC_BARRIER_TIMEOUT > BACKGROUND_SYNC_LONG_POLL_TIMEOUT);
    }

    #[test]
    fn background_sync_retry_backoff_is_exponential_and_bounded() {
        assert_eq!(background_sync_retry_delay(1), Duration::from_secs(2));
        assert_eq!(background_sync_retry_delay(2), Duration::from_secs(4));
        assert_eq!(background_sync_retry_delay(4), Duration::from_secs(16));
        assert_eq!(background_sync_retry_delay(5), BACKGROUND_SYNC_MAX_BACKOFF);
        assert_eq!(background_sync_retry_delay(50), BACKGROUND_SYNC_MAX_BACKOFF);
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

    #[test]
    fn foreground_sync_waits_until_a_fully_processed_background_cycle_is_published() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_time()
            .build()
            .unwrap();
        runtime.block_on(async {
            let (progress, observer) = watch::channel(BackgroundSyncProgress::default());
            let waiter = tokio::spawn(observe_completed_background_sync(observer));
            tokio::task::yield_now().await;

            progress.send_modify(|state| state.next_batch = "not-complete".to_string());
            tokio::task::yield_now().await;
            assert!(!waiter.is_finished());

            progress.send_modify(|state| {
                state.generation = 1;
                state.next_batch = "complete".to_string();
                state.converged_rooms = 1;
            });
            let observed = waiter.await.unwrap().unwrap();
            assert_eq!(observed.generation, 1);
            assert_eq!(observed.next_batch, "complete");
            assert_eq!(observed.converged_rooms, 1);
        });
    }

    #[test]
    fn foreground_sync_requires_a_new_fully_processed_background_cycle() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_time()
            .build()
            .unwrap();
        runtime.block_on(async {
            let (progress, observer) = watch::channel(BackgroundSyncProgress::default());
            publish_completed_sync(
                &progress,
                &CompletedSyncCycle {
                    next_batch: "complete".to_string(),
                    enabled_rooms: 0,
                    converged_rooms: 1,
                },
            );

            let waiter = tokio::spawn(observe_completed_background_sync(observer));
            tokio::task::yield_now().await;
            assert!(!waiter.is_finished());

            publish_completed_sync(
                &progress,
                &CompletedSyncCycle {
                    next_batch: "fresh".to_string(),
                    enabled_rooms: 0,
                    converged_rooms: 2,
                },
            );

            let observed = waiter.await.unwrap().unwrap();
            assert_eq!(observed.generation, 2);
            assert_eq!(observed.next_batch, "fresh");
            assert_eq!(observed.converged_rooms, 2);
        });
    }

    #[test]
    fn foreground_sync_surfaces_a_terminal_background_session_rejection() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_time()
            .build()
            .unwrap();
        runtime.block_on(async {
            let (progress, observer) = watch::channel(BackgroundSyncProgress::default());
            let waiter = tokio::spawn(observe_completed_background_sync(observer));
            tokio::task::yield_now().await;

            progress.send_modify(|state| {
                state.terminal_error = Some("M_UNKNOWN_TOKEN".to_string());
            });
            assert_eq!(waiter.await.unwrap(), Err("M_UNKNOWN_TOKEN".to_string()));
        });
    }

    #[test]
    fn terminal_session_rejections_stop_background_sync() {
        assert!(is_terminal_matrix_session_error("M_MISSING_TOKEN"));
        assert!(is_terminal_matrix_session_error("M_UNKNOWN_TOKEN"));
        assert!(!is_terminal_matrix_session_error("M_LIMIT_EXCEEDED"));
        assert!(!is_terminal_matrix_session_error(
            "M_WEAVE_E2EE_BACKGROUND_SYNC"
        ));
    }
}
