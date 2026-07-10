use matrix_sdk::{
    authentication::{matrix::MatrixSession, SessionTokens},
    config::SyncSettings,
    encryption::{
        recovery::{RecoveryError, RecoveryState},
        verification::{
            SasVerification, Verification, VerificationRequest, VerificationRequestState,
        },
        EncryptionSettings, VerificationState,
    },
    room::MessagesOptions,
    ruma::{
        api::client::receipt::create_receipt::v3::ReceiptType,
        api::MatrixVersion,
        events::receipt::ReceiptThread,
        events::{
            key::verification::{request::ToDeviceKeyVerificationRequestEvent, VerificationMethod},
            room::message::RoomMessageEventContent,
        },
        OwnedDeviceId, OwnedEventId, OwnedRoomId, OwnedUserId, UInt,
    },
    store::RoomLoadSettings,
    Client, SessionMeta,
};
use reqwest::header::{HeaderMap, HeaderName, HeaderValue};
use serde_json::{json, Value};
use std::{
    collections::HashMap,
    path::Path,
    sync::{Mutex, OnceLock},
    time::Duration,
};

const DEVICE_ID_HEADER: &str = "x-weave-matrix-device-id";

struct ManagedClient {
    client: Client,
    homeserver_url: String,
    user_id: String,
    device_id: String,
    access_token: String,
    verification_request: Option<VerificationRequest>,
    sas_verification: Option<SasVerification>,
}

static CLIENTS: OnceLock<Mutex<HashMap<String, ManagedClient>>> = OnceLock::new();

fn clients() -> &'static Mutex<HashMap<String, ManagedClient>> {
    CLIENTS.get_or_init(|| Mutex::new(HashMap::new()))
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

    {
        let guard = clients()
            .lock()
            .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?;
        if let Some(existing) = guard.get(&profile_key) {
            if existing.homeserver_url == homeserver_url
                && existing.user_id == user_id
                && existing.device_id == device_id
                && existing.access_token == access_token
            {
                return Ok(json!({
                    "initialized": true,
                    "restored": true,
                    "deviceId": device_id,
                }));
            }
        }
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
    let client = client_for(profile_key)?;
    let first = client
        .sync_once(SyncSettings::new().timeout(Duration::from_secs(0)))
        .await
        .map_err(|_| "M_WEAVE_E2EE_SYNC".to_string())?;

    let mut enabled_rooms = 0_u64;
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
        }
    }
    if enabled_rooms > 0 {
        client
            .sync_once(SyncSettings::new().timeout(Duration::from_secs(0)))
            .await
            .map_err(|_| "M_WEAVE_E2EE_SYNC".to_string())?;
    }

    Ok(json!({
        "nextBatch": first.next_batch,
        "enabledRooms": enabled_rooms,
    }))
}

pub async fn rooms(profile_key: String) -> String {
    json_result(rooms_inner(&profile_key).await)
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
    let response = room
        .send(RoomMessageEventContent::text_plain(body))
        .await
        .map_err(|_| "M_WEAVE_E2EE_SEND".to_string())?;
    Ok(json!({ "eventId": response.response.event_id.to_string() }))
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
        client_for(&profile_key)?
            .encryption()
            .recovery()
            .recover(recovery_key_or_passphrase.trim())
            .await
            .map_err(|_| "M_WEAVE_E2EE_RECOVERY".to_string())?;
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
        let devices = client
            .encryption()
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

pub fn dispose(profile_key: String) -> String {
    let result = clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())
        .map(|mut guard| {
            guard.remove(&profile_key);
            json!({ "disposed": true })
        });
    json_result(result)
}

fn client_for(profile_key: &str) -> Result<Client, String> {
    clients()
        .lock()
        .map_err(|_| "M_WEAVE_E2EE_UNAVAILABLE".to_string())?
        .get(profile_key)
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

fn project_timeline_event(
    event: &matrix_sdk::deserialized_responses::TimelineEvent,
) -> Option<Value> {
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
}
