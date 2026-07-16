use ruma::{OwnedEventId, OwnedRoomId, OwnedServerName, OwnedUserId};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::BTreeMap;
use thiserror::Error;
use tracing::instrument;

pub const MATRIX_PROTOCOL_SURFACE: &str = "matrix-client-server-facade";
pub const OIDC_GATEKEEPER: &str = "spring-boot-resource-server";
pub const SERVER_JNI_BOUNDARY: &str = "server-jni-wrapper";
pub const FLUTTER_BRIDGE_BOUNDARY: &str = "flutter-rust-bridge";
pub const SUPPORTED_MATRIX_VERSIONS: &[&str] = &["v1.18"];
pub const NATIVE_LIBRARY: &str = "weave_matrix_core";
pub const NATIVE_METHOD: &str = "projectJson";

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MatrixFacadeDescriptor {
    pub protocol_surface: String,
    pub oidc_gatekeeper: String,
    pub northbound_homeserver_dependency: bool,
    pub rust_protocol_core: String,
    pub server_jni_boundary: String,
    pub flutter_bridge_boundary: String,
    pub native_library: String,
    pub native_method: String,
    pub native_linked: bool,
    pub server_name: String,
    pub supported_matrix_versions: Vec<String>,
    pub supported_endpoints: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MatrixIdProjection {
    pub user_id: String,
    pub room_id: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "code", content = "detail")]
pub enum MatrixCoreFailure {
    EmptySubject,
    EmptyServerName,
    InvalidMatrixId { kind: String },
    InvalidSyncToken,
    InvalidOperation,
    InvalidRequest,
    UnsupportedMessageType,
    Serialization,
}

#[derive(Debug, Error)]
pub enum MatrixCoreError {
    #[error("subject must not be empty")]
    EmptySubject,
    #[error("server name must not be empty")]
    EmptyServerName,
    #[error("invalid Matrix {kind}")]
    InvalidMatrixId { kind: &'static str },
    #[error("invalid Matrix sync token")]
    InvalidSyncToken,
    #[error("unsupported Matrix core operation")]
    InvalidOperation,
    #[error("invalid Matrix request")]
    InvalidRequest,
    #[error("only m.text messages are supported by this facade profile")]
    UnsupportedMessageType,
    #[error("failed to serialize Matrix core payload")]
    Serialization(#[from] serde_json::Error),
}

impl MatrixCoreError {
    pub fn support_safe_failure(&self) -> MatrixCoreFailure {
        match self {
            MatrixCoreError::EmptySubject => MatrixCoreFailure::EmptySubject,
            MatrixCoreError::EmptyServerName => MatrixCoreFailure::EmptyServerName,
            MatrixCoreError::InvalidMatrixId { kind } => MatrixCoreFailure::InvalidMatrixId {
                kind: (*kind).to_string(),
            },
            MatrixCoreError::InvalidSyncToken => MatrixCoreFailure::InvalidSyncToken,
            MatrixCoreError::InvalidOperation => MatrixCoreFailure::InvalidOperation,
            MatrixCoreError::InvalidRequest => MatrixCoreFailure::InvalidRequest,
            MatrixCoreError::UnsupportedMessageType => MatrixCoreFailure::UnsupportedMessageType,
            MatrixCoreError::Serialization(_) => MatrixCoreFailure::Serialization,
        }
    }

    fn errcode(&self) -> &'static str {
        match self {
            MatrixCoreError::EmptySubject => "M_MISSING_TOKEN",
            MatrixCoreError::InvalidSyncToken
            | MatrixCoreError::InvalidRequest
            | MatrixCoreError::Serialization(_) => "M_BAD_JSON",
            MatrixCoreError::UnsupportedMessageType => "M_UNSUPPORTED",
            MatrixCoreError::InvalidOperation
            | MatrixCoreError::EmptyServerName
            | MatrixCoreError::InvalidMatrixId { .. } => "M_WEAVE_MATRIX_CORE_ERROR",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProjectionInput {
    #[serde(default)]
    subject: String,
    #[serde(default)]
    device_id: String,
    #[serde(default)]
    cursor: String,
    #[serde(default)]
    since: Option<String>,
    #[serde(default)]
    conversations: Vec<CanonicalConversationInput>,
    #[serde(default)]
    account_data: BTreeMap<String, Value>,
    #[serde(default)]
    to_device_events: Vec<Value>,
    #[serde(default)]
    device_lists_changed: Vec<String>,
    #[serde(default)]
    device_lists_left: Vec<String>,
    #[serde(default)]
    device_one_time_keys_count: BTreeMap<String, u64>,
    #[serde(default)]
    device_unused_fallback_key_types: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CanonicalConversationInput {
    conversation_id: String,
    title: String,
    #[serde(default)]
    updated_at_epoch_millis: i64,
    #[serde(default)]
    unread_count: u64,
    #[serde(default)]
    encryption_algorithm: Option<String>,
    #[serde(default)]
    memberships: Vec<CanonicalMembershipInput>,
    #[serde(default)]
    messages: Vec<CanonicalMessageInput>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CanonicalMembershipInput {
    member_ref: String,
    state: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CanonicalMessageInput {
    message_id: String,
    sender_ref: String,
    sent_at_epoch_millis: i64,
    #[serde(default = "default_event_kind")]
    kind: String,
    #[serde(default)]
    message_type: Option<String>,
    #[serde(default)]
    body: Option<String>,
    #[serde(default)]
    format: Option<String>,
    #[serde(default)]
    formatted_body: Option<String>,
    #[serde(default)]
    relation_kind: Option<String>,
    #[serde(default)]
    relation_target_event_id: Option<String>,
    #[serde(default)]
    reply_to_event_id: Option<String>,
    #[serde(default)]
    reaction_key: Option<String>,
    #[serde(default)]
    presentation_extensions: BTreeMap<String, Value>,
    #[serde(default = "default_delivery_state")]
    delivery_state: String,
    #[serde(default)]
    encrypted_content: Option<Value>,
    #[serde(default)]
    redacted: bool,
}

fn default_event_kind() -> String {
    "message".to_string()
}

fn default_delivery_state() -> String {
    "sent".to_string()
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct MatrixEvent {
    #[serde(rename = "type")]
    event_type: String,
    sender: String,
    event_id: String,
    origin_server_ts: i64,
    content: Value,
    #[serde(skip_serializing_if = "Option::is_none")]
    state_key: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
struct MatrixRoomEvent {
    #[serde(flatten)]
    event: MatrixEvent,
    room_id: String,
    unsigned: BTreeMap<String, Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct MatrixTimeline {
    #[serde(default)]
    limited: bool,
    #[serde(default)]
    prev_batch: String,
    #[serde(default)]
    events: Vec<MatrixEvent>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct MatrixState {
    #[serde(default)]
    events: Vec<MatrixEvent>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct MatrixUnreadNotifications {
    #[serde(default)]
    notification_count: u64,
    #[serde(default)]
    highlight_count: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct MatrixJoinedRoom {
    #[serde(default)]
    state: MatrixState,
    #[serde(default)]
    timeline: MatrixTimeline,
    #[serde(default)]
    unread_notifications: MatrixUnreadNotifications,
}

impl Default for MatrixState {
    fn default() -> Self {
        Self { events: Vec::new() }
    }
}

impl Default for MatrixTimeline {
    fn default() -> Self {
        Self {
            limited: false,
            prev_batch: String::new(),
            events: Vec::new(),
        }
    }
}

impl Default for MatrixUnreadNotifications {
    fn default() -> Self {
        Self {
            notification_count: 0,
            highlight_count: 0,
        }
    }
}

#[derive(Debug, Clone, Deserialize)]
struct SendMessageRequest {
    msgtype: String,
    body: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SendEventRequest {
    event_type: String,
    content: Value,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ClientSyncProjection {
    next_batch: String,
    rooms: Vec<ClientRoomProjection>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ClientRoomProjection {
    room_id: String,
    title: String,
    unread_count: u64,
    messages: Vec<ClientMessageProjection>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ClientMessageProjection {
    event_id: String,
    sender: String,
    origin_server_ts: i64,
    body: Option<String>,
    content_type: String,
}

#[instrument(skip(server_name))]
pub fn matrix_facade_descriptor(
    server_name: String,
) -> Result<MatrixFacadeDescriptor, MatrixCoreError> {
    let server_name = validate_server_name(&server_name)?.to_string();
    Ok(MatrixFacadeDescriptor {
        protocol_surface: MATRIX_PROTOCOL_SURFACE.to_string(),
        oidc_gatekeeper: OIDC_GATEKEEPER.to_string(),
        northbound_homeserver_dependency: false,
        rust_protocol_core: "ruma-serde-serde_json-thiserror-tracing".to_string(),
        server_jni_boundary: SERVER_JNI_BOUNDARY.to_string(),
        flutter_bridge_boundary: FLUTTER_BRIDGE_BOUNDARY.to_string(),
        native_library: NATIVE_LIBRARY.to_string(),
        native_method: NATIVE_METHOD.to_string(),
        native_linked: true,
        server_name,
        supported_matrix_versions: SUPPORTED_MATRIX_VERSIONS
            .iter()
            .map(|version| (*version).to_string())
            .collect(),
        supported_endpoints: vec![
            "GET /_matrix/client/versions".to_string(),
            "GET /_matrix/client/v3/account/whoami".to_string(),
            "GET /_matrix/client/v3/sync".to_string(),
            "GET /_matrix/client/v3/joined_rooms".to_string(),
            "GET /_matrix/client/v3/rooms/{roomId}/messages".to_string(),
            "GET /_matrix/client/v3/rooms/{roomId}/members".to_string(),
            "PUT /_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}".to_string(),
            "PUT /_matrix/client/v3/rooms/{roomId}/send/m.reaction/{txnId}".to_string(),
            "PUT /_matrix/client/v3/rooms/{roomId}/redact/{eventId}/{txnId}".to_string(),
            "GET /_matrix/client/v3/rooms/{roomId}/joined_members".to_string(),
            "POST /_matrix/client/v3/rooms/{roomId}/receipt/m.read/{eventId}".to_string(),
            "PUT /_matrix/client/v3/rooms/{roomId}/typing/{userId}".to_string(),
            "POST /_matrix/client/v3/createRoom".to_string(),
            "POST /_matrix/client/v3/join/{roomId}".to_string(),
            "POST /_matrix/client/v3/rooms/{roomId}/leave".to_string(),
            "GET /_matrix/client/v3/rooms/{roomId}/state".to_string(),
            "GET /_matrix/client/v3/profile/{userId}".to_string(),
            "GET /_matrix/client/v3/pushrules/".to_string(),
            "POST /_matrix/client/v3/user/{userId}/filter".to_string(),
            "GET|PUT /_matrix/client/v3/user/{userId}/account_data/{type}".to_string(),
            "POST /_matrix/client/v3/keys/upload".to_string(),
            "POST /_matrix/client/v3/keys/query".to_string(),
            "POST /_matrix/client/v3/keys/claim".to_string(),
            "GET /_matrix/client/v3/keys/changes".to_string(),
            "POST /_matrix/client/v3/keys/device_signing/upload".to_string(),
            "POST /_matrix/client/v3/keys/signatures/upload".to_string(),
            "PUT /_matrix/client/v3/sendToDevice/{eventType}/{txnId}".to_string(),
            "PUT /_matrix/client/v3/rooms/{roomId}/state/m.room.encryption".to_string(),
            "PUT /_matrix/client/v3/rooms/{roomId}/send/m.room.encrypted/{txnId}".to_string(),
            "DELETE /_matrix/client/v3/devices/{deviceId}".to_string(),
            "GET|POST /_matrix/client/v3/room_keys/version".to_string(),
            "GET|PUT|DELETE /_matrix/client/v3/room_keys/version/{version}".to_string(),
            "GET|PUT|DELETE /_matrix/client/v3/room_keys/keys".to_string(),
            "GET|PUT|DELETE /_matrix/client/v3/room_keys/keys/{roomId}".to_string(),
            "GET|PUT|DELETE /_matrix/client/v3/room_keys/keys/{roomId}/{sessionId}".to_string(),
        ],
    })
}

#[instrument(skip(server_name))]
pub fn matrix_facade_descriptor_json(server_name: String) -> Result<String, MatrixCoreError> {
    Ok(serde_json::to_string(&matrix_facade_descriptor(
        server_name,
    )?)?)
}

#[instrument(skip(subject, conversation_id, server_name))]
pub fn project_weave_matrix_ids(
    subject: String,
    conversation_id: String,
    server_name: String,
) -> Result<MatrixIdProjection, MatrixCoreError> {
    let server_name = validate_server_name(&server_name)?;
    let user_id = matrix_user_id(&subject, &server_name)?.to_string();
    let room_id = matrix_room_id(&conversation_id, &server_name)?.to_string();
    Ok(MatrixIdProjection { user_id, room_id })
}

#[instrument(skip(input_json, server_name))]
pub fn project_json(
    operation: String,
    input_json: String,
    server_name: String,
) -> Result<String, MatrixCoreError> {
    let server_name = validate_server_name(&server_name)?;
    let value = match operation.as_str() {
        "descriptor" => serde_json::to_value(matrix_facade_descriptor(server_name.to_string())?)?,
        "versions" => versions_value(server_name.as_str())?,
        "whoami" => whoami_value(&parse(&input_json)?, &server_name)?,
        "sync" => sync_value(&parse(&input_json)?, &server_name)?,
        "validate-sync-token" => validate_sync_token_value(&parse(&input_json)?)?,
        "decode-sync-token" => decode_sync_token_value(&parse(&input_json)?)?,
        "joined-rooms" => joined_rooms_value(&parse(&input_json)?, &server_name)?,
        "messages" => messages_value(&parse(&input_json)?, &server_name)?,
        "members" => members_value(&parse(&input_json)?, &server_name)?,
        "parse-object" => parse_object_value(&input_json)?,
        "parse-send" => parse_send_value(&input_json)?,
        "parse-event" => parse_event_value(&input_json, &server_name)?,
        "serialize-send" => serialize_send_value(&input_json)?,
        "send-response" => send_response_value(&input_json, &server_name)?,
        "decode-room" => decode_room_value(&input_json, &server_name)?,
        "decode-event" => decode_event_value(&input_json, &server_name)?,
        "room-id" => room_id_value(&input_json, &server_name)?,
        "user-id" => user_id_value(&input_json, &server_name)?,
        "error" => matrix_error_value(&input_json)?,
        "parse-sync" => parse_sync_value(&input_json, &server_name)?,
        "parse-messages" => parse_messages_value(&input_json, &server_name)?,
        "parse-versions" => parse_versions_value(&input_json)?,
        "parse-whoami" => parse_whoami_value(&input_json, &server_name)?,
        _ => return Err(MatrixCoreError::InvalidOperation),
    };
    Ok(serde_json::to_string(&value)?)
}

pub fn project_json_or_error(operation: String, input_json: String, server_name: String) -> String {
    project_json(operation, input_json, server_name).unwrap_or_else(|error| {
        json!({
            "errcode": error.errcode(),
            "error": error.to_string(),
            "supportSafe": true,
            "matrixCoreFailure": error.support_safe_failure(),
        })
        .to_string()
    })
}

fn versions_value(server_name: &str) -> Result<Value, MatrixCoreError> {
    Ok(json!({
        "versions": SUPPORTED_MATRIX_VERSIONS,
        "unstable_features": {},
        "weaveBoundary": "northbound-matrix-client-server",
        "canonicalDomain": "chat",
        "providerDataPlaneExposed": false,
        "matrixCore": matrix_facade_descriptor(server_name.to_string())?,
    }))
}

fn whoami_value(
    input: &ProjectionInput,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    let device_id = validate_device_id(&input.device_id)?;
    Ok(json!({
        "user_id": matrix_user_id(&input.subject, server_name)?.to_string(),
        "device_id": device_id,
        "is_guest": false,
        "weaveBoundary": "northbound-matrix-client-server",
        "canonicalDomain": "chat",
        "providerDataPlaneExposed": false,
        "matrixCore": matrix_facade_descriptor(server_name.to_string())?,
    }))
}

fn validate_device_id(value: &str) -> Result<&str, MatrixCoreError> {
    if value.len() < 8
        || value.len() > 128
        || !value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'=' | b'-'))
    {
        return Err(MatrixCoreError::InvalidMatrixId { kind: "device" });
    }
    Ok(value)
}

fn parse_whoami_value(
    input_json: &str,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    let input: Value = parse(input_json)?;
    let user_id = input
        .get("user_id")
        .and_then(Value::as_str)
        .ok_or(MatrixCoreError::InvalidRequest)?;
    validate_user_for_server(user_id, server_name)?;
    Ok(json!({ "userId": user_id }))
}

fn sync_value(
    input: &ProjectionInput,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    if let Some(since) = &input.since {
        decode_sync_token(since)?;
    }
    let mut joined = BTreeMap::<String, MatrixJoinedRoom>::new();
    for conversation in &input.conversations {
        let room_id = matrix_room_id(&conversation.conversation_id, server_name)?.to_string();
        joined.insert(
            room_id,
            MatrixJoinedRoom {
                state: MatrixState {
                    events: room_state_events(conversation, server_name)?,
                },
                timeline: MatrixTimeline {
                    limited: false,
                    prev_batch: encode_sync_token("start"),
                    events: conversation
                        .messages
                        .iter()
                        .map(|message| message_event(message, server_name))
                        .collect::<Result<Vec<_>, _>>()?,
                },
                unread_notifications: MatrixUnreadNotifications {
                    notification_count: conversation.unread_count,
                    highlight_count: 0,
                },
            },
        );
    }
    Ok(json!({
        "next_batch": encode_sync_token(&input.cursor),
        "rooms": { "join": joined },
        "account_data": {
            "events": input.account_data.iter().map(|(event_type, content)| json!({
                "type": event_type,
                "content": content,
            })).collect::<Vec<_>>()
        },
        "to_device": { "events": input.to_device_events },
        "device_lists": {
            "changed": input.device_lists_changed,
            "left": input.device_lists_left,
        },
        "device_one_time_keys_count": input.device_one_time_keys_count,
        "device_unused_fallback_key_types": input.device_unused_fallback_key_types,
        "weaveBoundary": "northbound-matrix-client-server",
        "canonicalDomain": "chat",
        "providerDataPlaneExposed": false,
        "matrixCore": matrix_facade_descriptor(server_name.to_string())?,
    }))
}

fn validate_sync_token_value(input: &ProjectionInput) -> Result<Value, MatrixCoreError> {
    if let Some(since) = &input.since {
        decode_sync_token(since)?;
    }
    Ok(json!({ "valid": true }))
}

fn decode_sync_token_value(input: &ProjectionInput) -> Result<Value, MatrixCoreError> {
    let cursor = input
        .since
        .as_deref()
        .map(decode_sync_token)
        .transpose()?
        .unwrap_or_default();
    Ok(json!({ "cursor": cursor }))
}

fn joined_rooms_value(
    input: &ProjectionInput,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    let rooms = input
        .conversations
        .iter()
        .map(|conversation| {
            matrix_room_id(&conversation.conversation_id, server_name).map(|id| id.to_string())
        })
        .collect::<Result<Vec<_>, MatrixCoreError>>()?;
    Ok(json!({ "joined_rooms": rooms }))
}

fn messages_value(
    input: &ProjectionInput,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    let conversation = input
        .conversations
        .first()
        .ok_or(MatrixCoreError::InvalidRequest)?;
    let room_id = matrix_room_id(&conversation.conversation_id, server_name)?.to_string();
    let events = conversation
        .messages
        .iter()
        .map(|message| {
            Ok(MatrixRoomEvent {
                event: message_event(message, server_name)?,
                room_id: room_id.clone(),
                unsigned: BTreeMap::new(),
            })
        })
        .collect::<Result<Vec<_>, MatrixCoreError>>()?;
    Ok(json!({
        "start": input.since.clone().unwrap_or_else(|| encode_sync_token("start")),
        "end": encode_sync_token(&input.cursor),
        "chunk": events,
    }))
}

fn members_value(
    input: &ProjectionInput,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    let conversation = input
        .conversations
        .first()
        .ok_or(MatrixCoreError::InvalidRequest)?;
    let room_id = matrix_room_id(&conversation.conversation_id, server_name)?.to_string();
    let chunk = conversation
        .memberships
        .iter()
        .map(|membership| {
            Ok(MatrixRoomEvent {
                event: membership_event(conversation, membership, server_name)?,
                room_id: room_id.clone(),
                unsigned: BTreeMap::new(),
            })
        })
        .collect::<Result<Vec<_>, MatrixCoreError>>()?;
    Ok(json!({ "chunk": chunk }))
}

fn parse_send_value(input_json: &str) -> Result<Value, MatrixCoreError> {
    let request: SendMessageRequest =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    if request.msgtype != "m.text" {
        return Err(MatrixCoreError::UnsupportedMessageType);
    }
    let body = request.body.trim();
    if body.is_empty() || body.len() > 65_536 {
        return Err(MatrixCoreError::InvalidRequest);
    }
    Ok(json!({ "body": body, "msgtype": "m.text" }))
}

fn parse_object_value(input_json: &str) -> Result<Value, MatrixCoreError> {
    let value: Value =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    if !value.is_object() {
        return Err(MatrixCoreError::InvalidRequest);
    }
    Ok(json!({ "value": value }))
}

fn parse_event_value(
    input_json: &str,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    let request: SendEventRequest =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    match request.event_type.as_str() {
        "m.room.message" => parse_message_content(&request.content, server_name),
        "m.reaction" => parse_reaction_content(&request.content, server_name),
        "m.room.encrypted" => parse_encrypted_content(&request.content),
        _ => Err(MatrixCoreError::UnsupportedMessageType),
    }
}

fn parse_encrypted_content(content: &Value) -> Result<Value, MatrixCoreError> {
    let object = content.as_object().ok_or(MatrixCoreError::InvalidRequest)?;
    if object.get("algorithm").and_then(Value::as_str) != Some("m.megolm.v1.aes-sha2") {
        return Err(MatrixCoreError::UnsupportedMessageType);
    }
    for (field, max_length) in [
        ("ciphertext", 262_144),
        ("sender_key", 512),
        ("session_id", 512),
        ("device_id", 128),
    ] {
        let value = object
            .get(field)
            .and_then(Value::as_str)
            .filter(|value| !value.is_empty() && value.len() <= max_length)
            .ok_or(MatrixCoreError::InvalidRequest)?;
        if value.chars().any(char::is_control) {
            return Err(MatrixCoreError::InvalidRequest);
        }
    }
    if serde_json::to_vec(content)?.len() > 393_216 {
        return Err(MatrixCoreError::InvalidRequest);
    }
    Ok(json!({
        "kind": "encrypted",
        "messageType": Value::Null,
        "body": Value::Null,
        "format": Value::Null,
        "formattedBody": Value::Null,
        "relationKind": Value::Null,
        "relationTargetEventId": Value::Null,
        "replyToEventId": Value::Null,
        "reactionKey": Value::Null,
        "presentationExtensions": {},
        "encryptedContent": content,
    }))
}

fn parse_message_content(
    content: &Value,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    let object = content.as_object().ok_or(MatrixCoreError::InvalidRequest)?;
    let message_type = object
        .get("msgtype")
        .and_then(Value::as_str)
        .unwrap_or("m.text");
    if !matches!(message_type, "m.text" | "m.notice" | "m.emote") {
        return Err(MatrixCoreError::UnsupportedMessageType);
    }
    let body = object
        .get("body")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty() && value.len() <= 65_536)
        .ok_or(MatrixCoreError::InvalidRequest)?;
    let (relation_kind, relation_target_event_id, reply_to_event_id) =
        parse_relation(object.get("m.relates_to"), server_name)?;
    let extensions = object
        .iter()
        .filter(|(key, _)| {
            matches!(
                key.as_str(),
                "com.openclaw.approval" | "com.openclaw.presentation"
            )
        })
        .map(|(key, value)| (key.clone(), value.clone()))
        .collect::<BTreeMap<_, _>>();
    Ok(json!({
        "kind": "message",
        "messageType": message_type,
        "body": body,
        "format": object.get("format").and_then(Value::as_str),
        "formattedBody": object.get("formatted_body").and_then(Value::as_str),
        "relationKind": relation_kind,
        "relationTargetEventId": relation_target_event_id,
        "replyToEventId": reply_to_event_id,
        "reactionKey": Value::Null,
        "presentationExtensions": extensions,
    }))
}

fn parse_reaction_content(
    content: &Value,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    let relation = content
        .get("m.relates_to")
        .and_then(Value::as_object)
        .ok_or(MatrixCoreError::InvalidRequest)?;
    if relation.get("rel_type").and_then(Value::as_str) != Some("m.annotation") {
        return Err(MatrixCoreError::InvalidRequest);
    }
    let target = relation
        .get("event_id")
        .and_then(Value::as_str)
        .ok_or(MatrixCoreError::InvalidRequest)?;
    let key = relation
        .get("key")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty() && value.len() <= 128)
        .ok_or(MatrixCoreError::InvalidRequest)?;
    Ok(json!({
        "kind": "reaction",
        "messageType": Value::Null,
        "body": Value::Null,
        "format": Value::Null,
        "formattedBody": Value::Null,
        "relationKind": "reaction",
        "relationTargetEventId": decode_event_id(target, server_name)?,
        "replyToEventId": Value::Null,
        "reactionKey": key,
        "presentationExtensions": {},
    }))
}

fn parse_relation(
    relation: Option<&Value>,
    server_name: &OwnedServerName,
) -> Result<(Option<String>, Option<String>, Option<String>), MatrixCoreError> {
    let Some(relation) = relation.and_then(Value::as_object) else {
        return Ok((None, None, None));
    };
    let reply_to = relation
        .get("m.in_reply_to")
        .and_then(Value::as_object)
        .and_then(|reply| reply.get("event_id"))
        .and_then(Value::as_str)
        .map(|event_id| decode_event_id(event_id, server_name))
        .transpose()?;
    let relation_type = relation.get("rel_type").and_then(Value::as_str);
    let target = relation
        .get("event_id")
        .and_then(Value::as_str)
        .map(|event_id| decode_event_id(event_id, server_name))
        .transpose()?;
    let kind = match relation_type {
        Some("m.thread") => Some("thread".to_string()),
        Some("m.replace") => Some("replace".to_string()),
        Some(_) => return Err(MatrixCoreError::InvalidRequest),
        None if reply_to.is_some() => Some("reply".to_string()),
        None => None,
    };
    let canonical_target = target.or_else(|| reply_to.clone());
    Ok((kind, canonical_target, reply_to))
}

fn serialize_send_value(input_json: &str) -> Result<Value, MatrixCoreError> {
    #[derive(Deserialize)]
    struct Input {
        body: String,
    }
    let input: Input =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    let body = input.body.trim();
    if body.is_empty() || body.len() > 65_536 {
        return Err(MatrixCoreError::InvalidRequest);
    }
    Ok(json!({ "msgtype": "m.text", "body": body }))
}

fn send_response_value(
    input_json: &str,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Input {
        message_id: String,
    }
    let input: Input =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    Ok(json!({ "event_id": matrix_event_id(&input.message_id, server_name)?.to_string() }))
}

fn decode_room_value(
    input_json: &str,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Input {
        room_id: String,
    }
    let input: Input =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    let room_id = OwnedRoomId::try_from(input.room_id)
        .map_err(|_| MatrixCoreError::InvalidMatrixId { kind: "room_id" })?;
    let value = room_id.as_str();
    let separator = value
        .rfind(':')
        .ok_or(MatrixCoreError::InvalidMatrixId { kind: "room_id" })?;
    if &value[separator + 1..] != server_name.as_str() {
        return Err(MatrixCoreError::InvalidMatrixId { kind: "room_id" });
    }
    Ok(json!({ "conversationId": &value[1..separator] }))
}

fn decode_event_value(
    input_json: &str,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Input {
        event_id: String,
    }
    let input: Input =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    Ok(json!({ "eventId": decode_event_id(&input.event_id, server_name)? }))
}

fn room_id_value(
    input_json: &str,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Input {
        conversation_id: String,
    }
    let input: Input =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    Ok(json!({
        "roomId": matrix_room_id(&input.conversation_id, server_name)?.to_string()
    }))
}

fn user_id_value(
    input_json: &str,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct UserIdRequest {
        member_ref: String,
    }
    let request: UserIdRequest =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    Ok(json!({
        "userId": matrix_user_id(&request.member_ref, server_name)?.to_string()
    }))
}

fn matrix_error_value(input_json: &str) -> Result<Value, MatrixCoreError> {
    #[derive(Deserialize)]
    struct Input {
        errcode: String,
        error: String,
    }
    let input: Input =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    if !input.errcode.starts_with("M_") || input.error.trim().is_empty() {
        return Err(MatrixCoreError::InvalidRequest);
    }
    Ok(json!({
        "errcode": input.errcode,
        "error": input.error,
        "weaveBoundary": "northbound-matrix-client-server",
        "canonicalDomain": "chat",
        "supportSafe": true,
        "providerDataPlaneExposed": false,
    }))
}

fn parse_sync_value(
    input_json: &str,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    #[derive(Deserialize)]
    struct Rooms {
        #[serde(default)]
        join: BTreeMap<String, MatrixJoinedRoom>,
    }
    #[derive(Deserialize)]
    struct Response {
        next_batch: String,
        #[serde(default)]
        rooms: Option<Rooms>,
    }
    let response: Response =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    decode_sync_token(&response.next_batch)?;
    let mut rooms = Vec::new();
    for (room_id, room) in response.rooms.map(|rooms| rooms.join).unwrap_or_default() {
        validate_room_for_server(&room_id, server_name)?;
        let title = room
            .state
            .events
            .iter()
            .find(|event| event.event_type == "m.room.name")
            .and_then(|event| event.content.get("name"))
            .and_then(Value::as_str)
            .unwrap_or("Weave Chat")
            .to_string();
        let messages = room
            .timeline
            .events
            .iter()
            .filter_map(client_message_projection)
            .collect();
        rooms.push(ClientRoomProjection {
            room_id,
            title,
            unread_count: room.unread_notifications.notification_count,
            messages,
        });
    }
    Ok(serde_json::to_value(ClientSyncProjection {
        next_batch: response.next_batch,
        rooms,
    })?)
}

fn parse_messages_value(
    input_json: &str,
    server_name: &OwnedServerName,
) -> Result<Value, MatrixCoreError> {
    #[derive(Deserialize)]
    struct Response {
        #[serde(default)]
        chunk: Vec<MatrixEvent>,
    }
    let response: Response =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    let messages: Vec<_> = response
        .chunk
        .iter()
        .filter_map(client_message_projection)
        .collect();
    for message in &messages {
        validate_event_for_server(&message.event_id, server_name)?;
        validate_user_for_server(&message.sender, server_name)?;
    }
    Ok(json!({ "messages": messages }))
}

fn parse_versions_value(input_json: &str) -> Result<Value, MatrixCoreError> {
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct CoreDescriptor {
        protocol_surface: String,
        oidc_gatekeeper: String,
        northbound_homeserver_dependency: bool,
        native_linked: bool,
        server_name: String,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Response {
        #[serde(default)]
        versions: Vec<String>,
        matrix_core: CoreDescriptor,
    }
    let response: Response =
        serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    let supported = response
        .versions
        .iter()
        .any(|version| SUPPORTED_MATRIX_VERSIONS.contains(&version.as_str()));
    if !supported
        || response.matrix_core.protocol_surface != MATRIX_PROTOCOL_SURFACE
        || response.matrix_core.oidc_gatekeeper != OIDC_GATEKEEPER
        || response.matrix_core.northbound_homeserver_dependency
        || !response.matrix_core.native_linked
    {
        return Err(MatrixCoreError::InvalidRequest);
    }
    Ok(json!({
        "compatible": true,
        "serverName": response.matrix_core.server_name,
        "versions": response.versions,
    }))
}

fn client_message_projection(event: &MatrixEvent) -> Option<ClientMessageProjection> {
    let content_type = match event.event_type.as_str() {
        "m.room.message"
            if event.content.get("msgtype").and_then(Value::as_str) == Some("m.text") =>
        {
            "text"
        }
        "m.room.encrypted" => "encrypted",
        _ => return None,
    };
    Some(ClientMessageProjection {
        event_id: event.event_id.clone(),
        sender: event.sender.clone(),
        origin_server_ts: event.origin_server_ts,
        body: if content_type == "text" {
            event
                .content
                .get("body")
                .and_then(Value::as_str)
                .map(str::to_string)
        } else {
            None
        },
        content_type: content_type.to_string(),
    })
}

fn room_name_event(
    conversation: &CanonicalConversationInput,
    server_name: &OwnedServerName,
) -> Result<MatrixEvent, MatrixCoreError> {
    Ok(MatrixEvent {
        event_type: "m.room.name".to_string(),
        sender: matrix_user_id("weave", server_name)?.to_string(),
        event_id: matrix_event_id(
            &format!("state-{}", conversation.conversation_id),
            server_name,
        )?
        .to_string(),
        origin_server_ts: conversation.updated_at_epoch_millis,
        content: json!({ "name": conversation.title }),
        state_key: Some(String::new()),
    })
}

fn room_state_events(
    conversation: &CanonicalConversationInput,
    server_name: &OwnedServerName,
) -> Result<Vec<MatrixEvent>, MatrixCoreError> {
    let mut events = vec![room_name_event(conversation, server_name)?];
    if let Some(algorithm) = conversation.encryption_algorithm.as_deref() {
        if algorithm != "m.megolm.v1.aes-sha2" {
            return Err(MatrixCoreError::InvalidRequest);
        }
        events.push(MatrixEvent {
            event_type: "m.room.encryption".to_string(),
            sender: matrix_user_id("weave", server_name)?.to_string(),
            event_id: matrix_event_id(
                &format!("state-encryption-{}", conversation.conversation_id),
                server_name,
            )?
            .to_string(),
            origin_server_ts: conversation.updated_at_epoch_millis,
            content: json!({ "algorithm": algorithm }),
            state_key: Some(String::new()),
        });
    }
    events.extend(
        conversation
            .memberships
            .iter()
            .map(|membership| membership_event(conversation, membership, server_name))
            .collect::<Result<Vec<_>, _>>()?,
    );
    Ok(events)
}

fn membership_event(
    conversation: &CanonicalConversationInput,
    membership: &CanonicalMembershipInput,
    server_name: &OwnedServerName,
) -> Result<MatrixEvent, MatrixCoreError> {
    let state = match membership.state.as_str() {
        "joined" | "join" => "join",
        "invited" | "invite" => "invite",
        "left" | "leave" => "leave",
        "banned" | "ban" => "ban",
        _ => return Err(MatrixCoreError::InvalidRequest),
    };
    let user_id = matrix_user_id(&membership.member_ref, server_name)?.to_string();
    Ok(MatrixEvent {
        event_type: "m.room.member".to_string(),
        sender: user_id.clone(),
        event_id: matrix_event_id(
            &format!(
                "membership-{}-{}",
                conversation.conversation_id, membership.member_ref
            ),
            server_name,
        )?
        .to_string(),
        origin_server_ts: conversation.updated_at_epoch_millis,
        content: json!({ "membership": state }),
        state_key: Some(user_id),
    })
}

fn message_event(
    message: &CanonicalMessageInput,
    server_name: &OwnedServerName,
) -> Result<MatrixEvent, MatrixCoreError> {
    let (event_type, content) = if message.redacted {
        // Matrix redaction strips event content but retains the event type.
        // Keeping encrypted events typed as encrypted lets clients distinguish
        // a deliberately redacted ciphertext event from a plaintext message.
        let redacted_event_type =
            if message.encrypted_content.is_some() || message.kind == "encrypted" {
                "m.room.encrypted"
            } else if message.kind == "reaction" {
                "m.reaction"
            } else {
                "m.room.message"
            };
        (redacted_event_type, json!({}))
    } else if let Some(encrypted_content) = &message.encrypted_content {
        ("m.room.encrypted", encrypted_content.clone())
    } else if message.kind == "reaction" {
        let target = message
            .relation_target_event_id
            .as_deref()
            .ok_or(MatrixCoreError::InvalidRequest)?;
        let key = message
            .reaction_key
            .as_deref()
            .ok_or(MatrixCoreError::InvalidRequest)?;
        (
            "m.reaction",
            json!({
                "m.relates_to": {
                    "rel_type": "m.annotation",
                    "event_id": matrix_event_id(target, server_name)?.to_string(),
                    "key": key,
                },
            }),
        )
    } else {
        let message_type = message.message_type.as_deref().unwrap_or("m.text");
        if !matches!(message_type, "m.text" | "m.notice" | "m.emote") {
            return Err(MatrixCoreError::UnsupportedMessageType);
        }
        let body = message
            .body
            .as_deref()
            .map(str::trim)
            .filter(|body| !body.is_empty() && body.len() <= 65_536)
            .ok_or(MatrixCoreError::InvalidRequest)?;
        let mut content = serde_json::Map::new();
        content.insert("msgtype".to_string(), json!(message_type));
        content.insert("body".to_string(), json!(body));
        if let Some(format) = &message.format {
            content.insert("format".to_string(), json!(format));
        }
        if let Some(formatted_body) = &message.formatted_body {
            content.insert("formatted_body".to_string(), json!(formatted_body));
        }
        if let Some(relation) = matrix_relation(message, server_name)? {
            content.insert("m.relates_to".to_string(), relation);
        }
        for (key, value) in &message.presentation_extensions {
            if matches!(
                key.as_str(),
                "com.openclaw.approval" | "com.openclaw.presentation"
            ) {
                content.insert(key.clone(), value.clone());
            }
        }
        content.insert("weaveMessageId".to_string(), json!(message.message_id));
        content.insert(
            "weaveDeliveryState".to_string(),
            json!(message.delivery_state),
        );
        content.insert("weaveCanonicalDomain".to_string(), json!("chat"));
        content.insert("providerDataPlaneExposed".to_string(), json!(false));
        ("m.room.message", Value::Object(content))
    };
    Ok(MatrixEvent {
        event_type: event_type.to_string(),
        sender: matrix_user_id(&message.sender_ref, server_name)?.to_string(),
        event_id: matrix_event_id(&message.message_id, server_name)?.to_string(),
        origin_server_ts: message.sent_at_epoch_millis,
        content,
        state_key: None,
    })
}

fn matrix_relation(
    message: &CanonicalMessageInput,
    server_name: &OwnedServerName,
) -> Result<Option<Value>, MatrixCoreError> {
    let Some(kind) = message.relation_kind.as_deref() else {
        return Ok(None);
    };
    let target = message
        .relation_target_event_id
        .as_deref()
        .ok_or(MatrixCoreError::InvalidRequest)?;
    let target = matrix_event_id(target, server_name)?.to_string();
    let relation = match kind {
        "reply" => json!({ "m.in_reply_to": { "event_id": target } }),
        "thread" => json!({
            "rel_type": "m.thread",
            "event_id": target,
            "m.in_reply_to": {
                "event_id": matrix_event_id(
                    message.reply_to_event_id.as_deref().unwrap_or(
                        message.relation_target_event_id.as_deref().unwrap_or_default()
                    ),
                    server_name,
                )?.to_string(),
            },
        }),
        "replace" => json!({ "rel_type": "m.replace", "event_id": target }),
        _ => return Err(MatrixCoreError::InvalidRequest),
    };
    Ok(Some(relation))
}

fn decode_event_id(value: &str, server_name: &OwnedServerName) -> Result<String, MatrixCoreError> {
    validate_event_for_server(value, server_name)?;
    let separator = value
        .rfind(':')
        .ok_or(MatrixCoreError::InvalidMatrixId { kind: "event_id" })?;
    Ok(value[1..separator].to_string())
}

fn parse<T: for<'de> Deserialize<'de>>(input_json: &str) -> Result<T, MatrixCoreError> {
    serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)
}

fn validate_server_name(server_name: &str) -> Result<OwnedServerName, MatrixCoreError> {
    let server_name = server_name.trim();
    if server_name.is_empty() {
        return Err(MatrixCoreError::EmptyServerName);
    }
    OwnedServerName::try_from(server_name.to_string()).map_err(|_| {
        MatrixCoreError::InvalidMatrixId {
            kind: "server_name",
        }
    })
}

fn matrix_user_id(
    value: &str,
    server_name: &OwnedServerName,
) -> Result<OwnedUserId, MatrixCoreError> {
    let source = value.rsplit(':').next().unwrap_or(value);
    let localpart = canonical_localpart(source.trim_start_matches('@'), "subject")?;
    OwnedUserId::try_from(format!("@{}:{}", localpart, server_name))
        .map_err(|_| MatrixCoreError::InvalidMatrixId { kind: "user_id" })
}

fn matrix_room_id(
    value: &str,
    server_name: &OwnedServerName,
) -> Result<OwnedRoomId, MatrixCoreError> {
    let localpart = canonical_localpart(value.trim_start_matches('!'), "conversation")?;
    OwnedRoomId::try_from(format!("!{}:{}", localpart, server_name))
        .map_err(|_| MatrixCoreError::InvalidMatrixId { kind: "room_id" })
}

fn matrix_event_id(
    value: &str,
    server_name: &OwnedServerName,
) -> Result<OwnedEventId, MatrixCoreError> {
    let localpart = canonical_localpart(value.trim_start_matches('$'), "event")?;
    OwnedEventId::try_from(format!("${}:{}", localpart, server_name))
        .map_err(|_| MatrixCoreError::InvalidMatrixId { kind: "event_id" })
}

fn validate_room_for_server(
    value: &str,
    server_name: &OwnedServerName,
) -> Result<(), MatrixCoreError> {
    let room_id = OwnedRoomId::try_from(value.to_string())
        .map_err(|_| MatrixCoreError::InvalidMatrixId { kind: "room_id" })?;
    if room_id.server_name() != Some(server_name.as_ref()) {
        return Err(MatrixCoreError::InvalidMatrixId { kind: "room_id" });
    }
    Ok(())
}

fn validate_event_for_server(
    value: &str,
    server_name: &OwnedServerName,
) -> Result<(), MatrixCoreError> {
    let event_id = OwnedEventId::try_from(value.to_string())
        .map_err(|_| MatrixCoreError::InvalidMatrixId { kind: "event_id" })?;
    if event_id.server_name() != Some(server_name.as_ref()) {
        return Err(MatrixCoreError::InvalidMatrixId { kind: "event_id" });
    }
    Ok(())
}

fn validate_user_for_server(
    value: &str,
    server_name: &OwnedServerName,
) -> Result<(), MatrixCoreError> {
    let user_id = OwnedUserId::try_from(value.to_string())
        .map_err(|_| MatrixCoreError::InvalidMatrixId { kind: "user_id" })?;
    if user_id.server_name().as_str() != server_name.as_str() {
        return Err(MatrixCoreError::InvalidMatrixId { kind: "user_id" });
    }
    Ok(())
}

fn canonical_localpart(value: &str, field: &'static str) -> Result<String, MatrixCoreError> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return Err(if field == "subject" {
            MatrixCoreError::EmptySubject
        } else {
            MatrixCoreError::InvalidMatrixId { kind: field }
        });
    }
    let mut output = String::with_capacity(trimmed.len());
    for character in trimmed.chars() {
        let lower = character.to_ascii_lowercase();
        if lower.is_ascii_alphanumeric() || matches!(lower, '.' | '_' | '-' | '=' | '/') {
            output.push(lower);
        } else {
            output.push('_');
        }
    }
    let output = output.trim_matches('_').to_string();
    if output.is_empty() {
        return Err(MatrixCoreError::InvalidMatrixId { kind: field });
    }
    Ok(output)
}

fn encode_sync_token(cursor: &str) -> String {
    let cursor = if cursor.trim().is_empty() {
        "0"
    } else {
        cursor.trim()
    };
    let encoded = cursor
        .as_bytes()
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect::<String>();
    format!("weave.s1.{encoded}")
}

fn decode_sync_token(token: &str) -> Result<String, MatrixCoreError> {
    let encoded = token
        .strip_prefix("weave.s1.")
        .ok_or(MatrixCoreError::InvalidSyncToken)?;
    if encoded.is_empty()
        || encoded.len() % 2 != 0
        || !encoded.bytes().all(|byte| byte.is_ascii_hexdigit())
    {
        return Err(MatrixCoreError::InvalidSyncToken);
    }
    let bytes = (0..encoded.len())
        .step_by(2)
        .map(|index| u8::from_str_radix(&encoded[index..index + 2], 16))
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| MatrixCoreError::InvalidSyncToken)?;
    String::from_utf8(bytes).map_err(|_| MatrixCoreError::InvalidSyncToken)
}

pub mod frb_api {
    pub fn project_matrix_json(
        operation: String,
        input_json: String,
        server_name: String,
    ) -> String {
        crate::project_json_or_error(operation, input_json, server_name)
    }

    #[cfg(feature = "flutter")]
    pub async fn initialize_matrix_client(
        profile_key: String,
        homeserver_url: String,
        user_id: String,
        device_id: String,
        access_token: String,
        store_path: String,
        store_passphrase: String,
        extra_root_certificate_pem: String,
    ) -> String {
        crate::flutter_crypto::initialize(
            profile_key,
            homeserver_url,
            user_id,
            device_id,
            access_token,
            store_path,
            store_passphrase,
            extra_root_certificate_pem,
        )
        .await
    }

    #[cfg(feature = "flutter")]
    pub async fn sync_matrix_client(profile_key: String) -> String {
        crate::flutter_crypto::sync(profile_key).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_rooms(profile_key: String) -> String {
        crate::flutter_crypto::rooms(profile_key).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_create_encrypted_room(profile_key: String, title: String) -> String {
        crate::flutter_crypto::create_encrypted_room(profile_key, title).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_room_messages(profile_key: String, room_id: String, limit: u32) -> String {
        crate::flutter_crypto::room_messages(profile_key, room_id, limit).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_send_text(profile_key: String, room_id: String, body: String) -> String {
        crate::flutter_crypto::send_text(profile_key, room_id, body).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_mark_read(
        profile_key: String,
        room_id: String,
        event_id: String,
    ) -> String {
        crate::flutter_crypto::mark_read(profile_key, room_id, event_id).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_security_state(profile_key: String) -> String {
        crate::flutter_crypto::security_state(profile_key).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_bootstrap_recovery(profile_key: String, passphrase: String) -> String {
        crate::flutter_crypto::bootstrap_recovery(profile_key, passphrase).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_recover(profile_key: String, recovery_key_or_passphrase: String) -> String {
        crate::flutter_crypto::recover(profile_key, recovery_key_or_passphrase).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_start_verification(profile_key: String) -> String {
        crate::flutter_crypto::start_verification(profile_key).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_accept_verification(profile_key: String) -> String {
        crate::flutter_crypto::accept_verification(profile_key).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_start_sas(profile_key: String) -> String {
        crate::flutter_crypto::start_sas(profile_key).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_confirm_sas(profile_key: String, matches: bool) -> String {
        crate::flutter_crypto::confirm_sas(profile_key, matches).await
    }

    #[cfg(feature = "flutter")]
    pub async fn matrix_cancel_verification(profile_key: String) -> String {
        crate::flutter_crypto::cancel_verification(profile_key).await
    }

    #[cfg(feature = "flutter")]
    pub fn matrix_dismiss_verification(profile_key: String) -> String {
        crate::flutter_crypto::dismiss_verification(profile_key)
    }

    #[cfg(feature = "flutter")]
    pub async fn dispose_matrix_client(profile_key: String) -> String {
        crate::flutter_crypto::dispose(profile_key).await
    }
}

#[cfg(feature = "flutter")]
mod flutter_crypto;

#[cfg(feature = "flutter")]
mod frb_generated;

#[cfg(feature = "jni")]
pub mod jni_bridge {
    use jni::errors::ThrowRuntimeExAndDefault;
    use jni::objects::{JClass, JString};
    use jni::sys::jstring;
    use jni::EnvUnowned;

    use crate::project_json_or_error;

    #[no_mangle]
    pub extern "system" fn Java_com_massimotter_weave_backend_matrix_NativeMatrixCore_projectJson<
        'local,
    >(
        mut unowned_env: EnvUnowned<'local>,
        _class: JClass<'local>,
        operation: JString<'local>,
        input_json: JString<'local>,
        server_name: JString<'local>,
    ) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let operation = operation.try_to_string(env).unwrap_or_default();
                let input_json = input_json.try_to_string(env).unwrap_or_default();
                let server_name = server_name.try_to_string(env).unwrap_or_default();
                let payload = project_json_or_error(operation, input_json, server_name);
                JString::from_str(env, payload).map(JString::into_raw)
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn canonical_input() -> String {
        json!({
            "subject": "User 123@example.com",
            "cursor": "revision-7",
            "conversations": [{
                "conversationId": "channel-general",
                "title": "General",
                "updatedAtEpochMillis": 1_720_432_800_000_i64,
                "unreadCount": 2,
                "encryptionAlgorithm": "m.megolm.v1.aes-sha2",
                "memberships": [{
                    "memberRef": "user:alice",
                    "state": "joined"
                }],
                "messages": [{
                    "messageId": "msg-1",
                    "senderRef": "user:alice",
                    "sentAtEpochMillis": 1_720_432_800_000_i64,
                    "body": "Hello from Weave Chat",
                    "deliveryState": "sent",
                    "encrypted": false
                }]
            }]
        })
        .to_string()
    }

    #[test]
    fn descriptor_marks_spring_oidc_as_gatekeeper_and_native_core_as_required() {
        let descriptor = matrix_facade_descriptor("matrix.weave.test".to_string()).unwrap();
        assert_eq!(descriptor.protocol_surface, MATRIX_PROTOCOL_SURFACE);
        assert_eq!(descriptor.oidc_gatekeeper, OIDC_GATEKEEPER);
        assert!(!descriptor.northbound_homeserver_dependency);
        assert!(descriptor.native_linked);
        assert!(descriptor
            .supported_endpoints
            .iter()
            .any(|endpoint| endpoint.contains("/sync")));
    }

    #[test]
    fn sync_projection_uses_ruma_validated_ids_and_stable_cursor() {
        let json = project_json(
            "sync".to_string(),
            canonical_input(),
            "matrix.weave.test".to_string(),
        )
        .unwrap();
        let parsed: Value = serde_json::from_str(&json).unwrap();
        assert_eq!(parsed["next_batch"], encode_sync_token("revision-7"));
        let state = parsed["rooms"]["join"]["!channel-general:matrix.weave.test"]["state"]
            ["events"]
            .as_array()
            .unwrap();
        assert!(state.iter().any(|event| {
            event["type"] == "m.room.encryption"
                && event["state_key"] == ""
                && event["content"]["algorithm"] == "m.megolm.v1.aes-sha2"
        }));
        assert_eq!(
            parsed["rooms"]["join"]["!channel-general:matrix.weave.test"]["timeline"]["events"][0]
                ["content"]["body"],
            "Hello from Weave Chat"
        );
    }

    #[test]
    fn sync_projection_accepts_ciphertext_only_canonical_events() {
        use ruma::{api::client::sync::sync_events::v3::Response, api::IncomingResponse};

        let mut input: Value = serde_json::from_str(&canonical_input()).unwrap();
        input["conversations"][0]["messages"] = json!([{
            "messageId": "encrypted-1",
            "senderRef": "user:alice",
            "sentAtEpochMillis": 1_720_432_800_000_i64,
            "kind": "encrypted",
            "messageType": Value::Null,
            "body": Value::Null,
            "deliveryState": "sent",
            "encryptedContent": {
                "algorithm": "m.megolm.v1.aes-sha2",
                "ciphertext": "opaque-ciphertext",
                "sender_key": "curve25519:alice",
                "session_id": "megolm-session-1",
                "device_id": "WEAVEDEVICEALICE"
            }
        }]);

        let projected = project_json(
            "sync".to_string(),
            input.to_string(),
            "matrix.weave.test".to_string(),
        )
        .unwrap();
        let _: Response = Response::try_from_http_response(ruma::exports::http::Response::new(
            projected.as_bytes(),
        ))
        .unwrap();
        let projected: Value = serde_json::from_str(&projected).unwrap();
        let event = &projected["rooms"]["join"]["!channel-general:matrix.weave.test"]["timeline"]
            ["events"][0];

        assert_eq!(event["type"], "m.room.encrypted");
        assert_eq!(event["content"]["ciphertext"], "opaque-ciphertext");
        assert!(event["content"].get("body").is_none());
    }

    #[test]
    fn members_projection_is_a_typed_ruma_response() {
        use ruma::{events::room::member::RoomMemberEvent, serde::Raw};

        #[derive(Deserialize)]
        struct MembersResponse {
            chunk: Vec<Raw<RoomMemberEvent>>,
        }

        let json = project_json(
            "members".to_string(),
            canonical_input(),
            "matrix.weave.test".to_string(),
        )
        .unwrap();
        let response: MembersResponse = serde_json::from_str(&json).unwrap();
        response.chunk[0].deserialize().unwrap();
        let member: Value = serde_json::from_str(response.chunk[0].json().get()).unwrap();

        assert_eq!(member["type"], "m.room.member");
        assert_eq!(member["state_key"], "@alice:matrix.weave.test");
        assert_eq!(member["room_id"], "!channel-general:matrix.weave.test");
        assert_eq!(member["content"]["membership"], "join");
    }

    #[test]
    fn messages_projection_is_a_typed_ruma_response() {
        use ruma::{events::AnyTimelineEvent, serde::Raw};

        #[derive(Deserialize)]
        struct MessagesResponse {
            chunk: Vec<Raw<AnyTimelineEvent>>,
        }

        let json = project_json(
            "messages".to_string(),
            canonical_input(),
            "matrix.weave.test".to_string(),
        )
        .unwrap();
        let response: MessagesResponse = serde_json::from_str(&json).unwrap();
        response.chunk[0].deserialize().unwrap();
    }

    #[test]
    fn client_projection_parses_matrix_in_rust_instead_of_dart() {
        let sync = project_json(
            "sync".to_string(),
            canonical_input(),
            "matrix.weave.test".to_string(),
        )
        .unwrap();
        let client = project_json(
            "parse-sync".to_string(),
            sync,
            "matrix.weave.test".to_string(),
        )
        .unwrap();
        let parsed: Value = serde_json::from_str(&client).unwrap();
        assert_eq!(parsed["rooms"][0]["title"], "General");
        assert_eq!(parsed["rooms"][0]["messages"][0]["contentType"], "text");
    }

    #[test]
    fn client_whoami_projection_validates_the_facade_server_name() {
        let valid = project_json(
            "parse-whoami".to_string(),
            json!({"user_id": "@user_alice:matrix.weave.test"}).to_string(),
            "matrix.weave.test".to_string(),
        )
        .unwrap();
        assert_eq!(
            serde_json::from_str::<Value>(&valid).unwrap()["userId"],
            "@user_alice:matrix.weave.test"
        );

        let invalid = project_json_or_error(
            "parse-whoami".to_string(),
            json!({"user_id": "@user_alice:provider.invalid"}).to_string(),
            "matrix.weave.test".to_string(),
        );
        assert_eq!(
            serde_json::from_str::<Value>(&invalid).unwrap()["errcode"],
            "M_WEAVE_MATRIX_CORE_ERROR"
        );
    }

    #[test]
    fn malformed_send_and_sync_tokens_return_matrix_safe_errors() {
        let send = project_json_or_error(
            "parse-send".to_string(),
            json!({"msgtype": "m.image", "body": "x"}).to_string(),
            "matrix.weave.test".to_string(),
        );
        assert_eq!(
            serde_json::from_str::<Value>(&send).unwrap()["errcode"],
            "M_UNSUPPORTED"
        );

        let mut input: Value = serde_json::from_str(&canonical_input()).unwrap();
        input["since"] = Value::String("provider-token".to_string());
        let sync = project_json_or_error(
            "sync".to_string(),
            input.to_string(),
            "matrix.weave.test".to_string(),
        );
        assert_eq!(
            serde_json::from_str::<Value>(&sync).unwrap()["errcode"],
            "M_BAD_JSON"
        );
    }

    #[test]
    fn generic_matrix_request_objects_are_parsed_by_the_rust_core() {
        let object = project_json(
            "parse-object".to_string(),
            json!({"name": "General", "is_direct": false, "nested": {"enabled": true}}).to_string(),
            "matrix.weave.test".to_string(),
        )
        .unwrap();
        let parsed: Value = serde_json::from_str(&object).unwrap();
        assert_eq!(parsed["value"]["name"], "General");
        assert_eq!(parsed["value"]["nested"]["enabled"], true);

        let invalid = project_json_or_error(
            "parse-object".to_string(),
            json!(["not", "an", "object"]).to_string(),
            "matrix.weave.test".to_string(),
        );
        assert_eq!(
            serde_json::from_str::<Value>(&invalid).unwrap()["errcode"],
            "M_BAD_JSON"
        );
    }

    #[test]
    fn approval_messages_and_reactions_round_trip_through_canonical_events() {
        let approval = project_json(
            "parse-event".to_string(),
            json!({
                "eventType": "m.room.message",
                "content": {
                    "msgtype": "m.text",
                    "body": "Approve calendar creation",
                    "com.openclaw.approval": {
                        "version": 1,
                        "kind": "plugin",
                        "state": "pending"
                    },
                    "providerSecret": "must-not-cross"
                }
            })
            .to_string(),
            "matrix.weave.test".to_string(),
        )
        .unwrap();
        let approval: Value = serde_json::from_str(&approval).unwrap();
        assert_eq!(approval["kind"], "message");
        assert_eq!(
            approval["presentationExtensions"]["com.openclaw.approval"]["kind"],
            "plugin"
        );
        assert!(approval["presentationExtensions"]
            .get("providerSecret")
            .is_none());

        let reaction = project_json(
            "parse-event".to_string(),
            json!({
                "eventType": "m.reaction",
                "content": {
                    "m.relates_to": {
                        "rel_type": "m.annotation",
                        "event_id": "$approval-event:matrix.weave.test",
                        "key": "allow"
                    }
                }
            })
            .to_string(),
            "matrix.weave.test".to_string(),
        )
        .unwrap();
        let reaction: Value = serde_json::from_str(&reaction).unwrap();
        assert_eq!(reaction["kind"], "reaction");
        assert_eq!(reaction["relationTargetEventId"], "approval-event");
        assert_eq!(reaction["reactionKey"], "allow");
    }

    #[test]
    fn sync_serializes_reactions_and_redactions_without_provider_payloads() {
        let input = json!({
            "subject": "user@example.com",
            "cursor": "revision-8",
            "accountData": {"m.direct": {"@assistant:matrix.weave.test": ["!general:matrix.weave.test"]}},
            "conversations": [{
                "conversationId": "general",
                "title": "General",
                "messages": [{
                    "messageId": "reaction-1",
                    "senderRef": "user:alice",
                    "sentAtEpochMillis": 1,
                    "kind": "reaction",
                    "relationKind": "reaction",
                    "relationTargetEventId": "approval-event",
                    "reactionKey": "allow",
                    "deliveryState": "sent"
                }, {
                    "messageId": "redacted-1",
                    "senderRef": "user:alice",
                    "sentAtEpochMillis": 2,
                    "kind": "message",
                    "body": "removed",
                    "redacted": true,
                    "deliveryState": "sent"
                }, {
                    "messageId": "redacted-encrypted-1",
                    "senderRef": "user:alice",
                    "sentAtEpochMillis": 3,
                    "kind": "encrypted",
                    "encryptedContent": {
                        "algorithm": "m.megolm.v1.aes-sha2",
                        "ciphertext": "removed-ciphertext",
                        "sender_key": "curve25519:alice",
                        "session_id": "removed-session",
                        "device_id": "WEAVEDEVICEALICE"
                    },
                    "redacted": true,
                    "deliveryState": "sent"
                }]
            }]
        });

        let sync = project_json(
            "sync".to_string(),
            input.to_string(),
            "matrix.weave.test".to_string(),
        )
        .unwrap();
        let sync: Value = serde_json::from_str(&sync).unwrap();
        let events = &sync["rooms"]["join"]["!general:matrix.weave.test"]["timeline"]["events"];
        assert_eq!(events[0]["type"], "m.reaction");
        assert_eq!(
            events[0]["content"]["m.relates_to"]["event_id"],
            "$approval-event:matrix.weave.test"
        );
        assert_eq!(events[1]["type"], "m.room.message");
        assert_eq!(events[1]["content"], json!({}));
        assert_eq!(events[2]["type"], "m.room.encrypted");
        assert_eq!(events[2]["content"], json!({}));
        assert_eq!(sync["account_data"]["events"][0]["type"], "m.direct");
        assert!(!sync.to_string().contains("providerSecret"));
    }

    #[test]
    fn validates_server_versions_descriptor_for_flutter_connect() {
        let descriptor = matrix_facade_descriptor("api.weave.test".to_string()).unwrap();
        let input = json!({
            "versions": ["v1.18"],
            "matrixCore": descriptor,
        });

        let output = project_json(
            "parse-versions".to_string(),
            input.to_string(),
            "api.weave.test".to_string(),
        )
        .unwrap();

        assert_eq!(
            serde_json::from_str::<Value>(&output).unwrap()["compatible"],
            true
        );
    }

    #[test]
    fn projects_weave_ids_into_ruma_validated_matrix_ids() {
        let projection = project_weave_matrix_ids(
            "User 123@example.com".to_string(),
            "Conversation 456".to_string(),
            "matrix.weave.test".to_string(),
        )
        .unwrap();
        assert_eq!(
            projection.user_id,
            "@user_123_example.com:matrix.weave.test"
        );
        assert_eq!(projection.room_id, "!conversation_456:matrix.weave.test");
    }

    #[test]
    fn encrypted_events_round_trip_as_opaque_megolm_content() {
        let encrypted_content = json!({
            "algorithm": "m.megolm.v1.aes-sha2",
            "ciphertext": "opaque-ciphertext",
            "sender_key": "curve25519:alice",
            "session_id": "megolm-session-1",
            "device_id": "WEAVEDEVICEALICE",
        });
        let parsed = project_json(
            "parse-event".to_string(),
            json!({
                "eventType": "m.room.encrypted",
                "content": encrypted_content,
            })
            .to_string(),
            "api.weave.test".to_string(),
        )
        .unwrap();
        let parsed: Value = serde_json::from_str(&parsed).unwrap();
        assert_eq!(parsed["kind"], "encrypted");
        assert_eq!(
            parsed["encryptedContent"]["ciphertext"],
            "opaque-ciphertext"
        );
        assert!(parsed["body"].is_null());

        let sync = project_json(
            "sync".to_string(),
            json!({
                "subject": "alice",
                "cursor": "chat-revision-2",
                "conversations": [{
                    "conversationId": "channel-general",
                    "title": "General",
                    "messages": [{
                        "messageId": "event-encrypted",
                        "senderRef": "user:alice",
                        "sentAtEpochMillis": 1_720_432_800_000_i64,
                        "kind": "encrypted",
                        "encryptedContent": encrypted_content,
                        "deliveryState": "sent"
                    }]
                }]
            })
            .to_string(),
            "api.weave.test".to_string(),
        )
        .unwrap();
        let sync: Value = serde_json::from_str(&sync).unwrap();
        let content = &sync["rooms"]["join"]["!channel-general:api.weave.test"]["timeline"]
            ["events"][0]["content"];
        assert_eq!(content["ciphertext"], "opaque-ciphertext");
        assert!(content.get("body").is_none());
    }
}
