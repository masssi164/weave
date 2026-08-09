use ruma::{OwnedEventId, OwnedRoomId, OwnedServerName, OwnedUserId};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::BTreeMap;
use thiserror::Error;
use tracing::instrument;

pub const MATRIX_PROTOCOL_SURFACE: &str = "matrix-client-server-facade";
pub const OIDC_GATEKEEPER: &str = "spring-boot-resource-server";
pub const SERVER_JNI_BOUNDARY: &str = "server-jni-wrapper";
pub const SUPPORTED_MATRIX_VERSIONS: &[&str] = &["v1.18"];
pub const NATIVE_LIBRARY: &str = "weave_matrix_protocol";
pub const NATIVE_METHOD: &str = "projectJson";

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MatrixFacadeDescriptor {
    pub protocol_surface: String,
    pub oidc_gatekeeper: String,
    pub northbound_homeserver_dependency: bool,
    pub rust_protocol_core: String,
    pub server_jni_boundary: String,
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
    #[error("unsupported Matrix protocol operation")]
    InvalidOperation,
    #[error("invalid Matrix request")]
    InvalidRequest,
    #[error("unsupported Matrix message/event type")]
    UnsupportedMessageType,
    #[error("failed to serialize Matrix protocol payload")]
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
            | MatrixCoreError::InvalidMatrixId { .. } => "M_WEAVE_MATRIX_PROTOCOL_ERROR",
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

fn default_event_kind() -> String { "message".to_string() }
fn default_delivery_state() -> String { "sent".to_string() }

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

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
struct MatrixTimeline {
    #[serde(default)] limited: bool,
    #[serde(default)] prev_batch: String,
    #[serde(default)] events: Vec<MatrixEvent>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
struct MatrixState { #[serde(default)] events: Vec<MatrixEvent> }

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
struct MatrixUnreadNotifications {
    #[serde(default)] notification_count: u64,
    #[serde(default)] highlight_count: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct MatrixJoinedRoom {
    #[serde(default)] state: MatrixState,
    #[serde(default)] timeline: MatrixTimeline,
    #[serde(default)] unread_notifications: MatrixUnreadNotifications,
}

#[derive(Debug, Clone, Deserialize)]
struct SendMessageRequest { msgtype: String, body: String }

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SendEventRequest { event_type: String, content: Value }

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ClientSyncProjection { next_batch: String, rooms: Vec<ClientRoomProjection> }

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
pub fn matrix_facade_descriptor(server_name: String) -> Result<MatrixFacadeDescriptor, MatrixCoreError> {
    let server_name = validate_server_name(&server_name)?.to_string();
    Ok(MatrixFacadeDescriptor {
        protocol_surface: MATRIX_PROTOCOL_SURFACE.to_string(),
        oidc_gatekeeper: OIDC_GATEKEEPER.to_string(),
        northbound_homeserver_dependency: false,
        rust_protocol_core: "ruma-serde-serde_json-thiserror-tracing".to_string(),
        server_jni_boundary: SERVER_JNI_BOUNDARY.to_string(),
        native_library: NATIVE_LIBRARY.to_string(),
        native_method: NATIVE_METHOD.to_string(),
        native_linked: true,
        server_name,
        supported_matrix_versions: SUPPORTED_MATRIX_VERSIONS.iter().map(|v| (*v).to_string()).collect(),
        supported_endpoints: vec![
            "GET /_matrix/client/versions".to_string(),
            "GET /_matrix/client/v3/account/whoami".to_string(),
            "GET /_matrix/client/v3/sync".to_string(),
            "GET /_matrix/client/v3/joined_rooms".to_string(),
            "GET /_matrix/client/v3/rooms/{roomId}/messages".to_string(),
            "GET /_matrix/client/v3/rooms/{roomId}/members".to_string(),
            "PUT /_matrix/client/v3/rooms/{roomId}/send/{eventType}/{txnId}".to_string(),
            "POST /_matrix/client/v3/rooms/{roomId}/redact/{eventId}/{txnId}".to_string(),
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
    Ok(serde_json::to_string(&matrix_facade_descriptor(server_name)?)?)
}

#[instrument(skip(subject, conversation_id, server_name))]
pub fn project_weave_matrix_ids(subject: String, conversation_id: String, server_name: String) -> Result<MatrixIdProjection, MatrixCoreError> {
    let server_name = validate_server_name(&server_name)?;
    Ok(MatrixIdProjection {
        user_id: matrix_user_id(&subject, &server_name)?.to_string(),
        room_id: matrix_room_id(&conversation_id, &server_name)?.to_string(),
    })
}

#[instrument(skip(input_json, server_name))]
pub fn project_json(operation: String, input_json: String, server_name: String) -> Result<String, MatrixCoreError> {
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
            "matrixProtocolFailure": error.support_safe_failure(),
        }).to_string()
    })
}

fn versions_value(server_name: &str) -> Result<Value, MatrixCoreError> {
    Ok(json!({
        "versions": SUPPORTED_MATRIX_VERSIONS,
        "unstable_features": {},
        "weaveBoundary": "northbound-matrix-client-server",
        "canonicalDomain": "chat",
        "providerDataPlaneExposed": false,
        "matrixProtocol": matrix_facade_descriptor(server_name.to_string())?,
    }))
}

fn whoami_value(input: &ProjectionInput, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let device_id = validate_device_id(&input.device_id)?;
    Ok(json!({
        "user_id": matrix_user_id(&input.subject, server_name)?.to_string(),
        "device_id": device_id,
        "is_guest": false,
        "weaveBoundary": "northbound-matrix-client-server",
        "canonicalDomain": "chat",
        "providerDataPlaneExposed": false,
        "matrixProtocol": matrix_facade_descriptor(server_name.to_string())?,
    }))
}

fn validate_device_id(value: &str) -> Result<&str, MatrixCoreError> {
    if value.len() < 8 || value.len() > 128 || !value.bytes().all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'_' | b'=' | b'-')) {
        return Err(MatrixCoreError::InvalidMatrixId { kind: "device" });
    }
    Ok(value)
}

fn parse_whoami_value(input_json: &str, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let input: Value = parse(input_json)?;
    let user_id = input.get("user_id").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?;
    validate_user_for_server(user_id, server_name)?;
    Ok(json!({ "userId": user_id }))
}

fn sync_value(input: &ProjectionInput, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    if let Some(since) = &input.since { decode_sync_token(since)?; }
    let mut joined = BTreeMap::<String, MatrixJoinedRoom>::new();
    for conversation in &input.conversations {
        let room_id = matrix_room_id(&conversation.conversation_id, server_name)?.to_string();
        joined.insert(room_id, MatrixJoinedRoom {
            state: MatrixState { events: room_state_events(conversation, server_name)? },
            timeline: MatrixTimeline {
                limited: false,
                prev_batch: encode_sync_token("start"),
                events: conversation.messages.iter().map(|m| message_event(m, server_name)).collect::<Result<Vec<_>, _>>()?,
            },
            unread_notifications: MatrixUnreadNotifications { notification_count: conversation.unread_count, highlight_count: 0 },
        });
    }
    Ok(json!({
        "next_batch": encode_sync_token(&input.cursor),
        "rooms": { "join": joined },
        "account_data": { "events": input.account_data.iter().map(|(t,c)| json!({"type":t,"content":c})).collect::<Vec<_>>() },
        "to_device": { "events": input.to_device_events },
        "device_lists": { "changed": input.device_lists_changed, "left": input.device_lists_left },
        "device_one_time_keys_count": input.device_one_time_keys_count,
        "device_unused_fallback_key_types": input.device_unused_fallback_key_types,
        "weaveBoundary": "northbound-matrix-client-server",
        "canonicalDomain": "chat",
        "providerDataPlaneExposed": false,
        "matrixProtocol": matrix_facade_descriptor(server_name.to_string())?,
    }))
}

fn validate_sync_token_value(input: &ProjectionInput) -> Result<Value, MatrixCoreError> {
    if let Some(since) = &input.since { decode_sync_token(since)?; }
    Ok(json!({ "valid": true }))
}

fn decode_sync_token_value(input: &ProjectionInput) -> Result<Value, MatrixCoreError> {
    let cursor = input.since.as_deref().map(decode_sync_token).transpose()?.unwrap_or_default();
    Ok(json!({ "cursor": cursor }))
}

fn joined_rooms_value(input: &ProjectionInput, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let rooms = input.conversations.iter()
        .map(|c| matrix_room_id(&c.conversation_id, server_name).map(|id| id.to_string()))
        .collect::<Result<Vec<_>, MatrixCoreError>>()?;
    Ok(json!({ "joined_rooms": rooms }))
}

fn messages_value(input: &ProjectionInput, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let conversation = input.conversations.first().ok_or(MatrixCoreError::InvalidRequest)?;
    let room_id = matrix_room_id(&conversation.conversation_id, server_name)?.to_string();
    let events = conversation.messages.iter().map(|message| Ok(MatrixRoomEvent {
        event: message_event(message, server_name)?, room_id: room_id.clone(), unsigned: BTreeMap::new(),
    })).collect::<Result<Vec<_>, MatrixCoreError>>()?;
    Ok(json!({ "start": input.since.clone().unwrap_or_else(|| encode_sync_token("start")), "end": encode_sync_token(&input.cursor), "chunk": events }))
}

fn members_value(input: &ProjectionInput, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let conversation = input.conversations.first().ok_or(MatrixCoreError::InvalidRequest)?;
    let room_id = matrix_room_id(&conversation.conversation_id, server_name)?.to_string();
    let chunk = conversation.memberships.iter().map(|membership| Ok(MatrixRoomEvent {
        event: membership_event(conversation, membership, server_name)?, room_id: room_id.clone(), unsigned: BTreeMap::new(),
    })).collect::<Result<Vec<_>, MatrixCoreError>>()?;
    Ok(json!({ "chunk": chunk }))
}

fn parse_send_value(input_json: &str) -> Result<Value, MatrixCoreError> {
    let request: SendMessageRequest = serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    if request.msgtype != "m.text" { return Err(MatrixCoreError::UnsupportedMessageType); }
    let body = request.body.trim();
    if body.is_empty() || body.len() > 65_536 { return Err(MatrixCoreError::InvalidRequest); }
    Ok(json!({ "body": body, "msgtype": "m.text" }))
}

fn parse_object_value(input_json: &str) -> Result<Value, MatrixCoreError> {
    let value: Value = serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    if !value.is_object() { return Err(MatrixCoreError::InvalidRequest); }
    Ok(json!({ "value": value }))
}

fn parse_event_value(input_json: &str, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let request: SendEventRequest = serde_json::from_str(input_json).map_err(|_| MatrixCoreError::InvalidRequest)?;
    match request.event_type.as_str() {
        "m.room.message" => parse_message_content(&request.content, server_name),
        "m.reaction" => parse_reaction_content(&request.content, server_name),
        "m.room.encrypted" => parse_encrypted_content(&request.content),
        _ => Err(MatrixCoreError::UnsupportedMessageType),
    }
}

fn parse_encrypted_content(content: &Value) -> Result<Value, MatrixCoreError> {
    let object = content.as_object().ok_or(MatrixCoreError::InvalidRequest)?;
    if object.get("algorithm").and_then(Value::as_str) != Some("m.megolm.v1.aes-sha2") { return Err(MatrixCoreError::UnsupportedMessageType); }
    for (field, max_length) in [("ciphertext", 262_144), ("session_id", 512)] {
        let value = object.get(field).and_then(Value::as_str).filter(|v| !v.is_empty() && v.len() <= max_length).ok_or(MatrixCoreError::InvalidRequest)?;
        if value.chars().any(char::is_control) { return Err(MatrixCoreError::InvalidRequest); }
    }
    for (field, max_length) in [("sender_key", 512), ("device_id", 128)] {
        if let Some(raw) = object.get(field) {
            let value = raw.as_str().filter(|v| !v.is_empty() && v.len() <= max_length).ok_or(MatrixCoreError::InvalidRequest)?;
            if value.chars().any(char::is_control) { return Err(MatrixCoreError::InvalidRequest); }
        }
    }
    if serde_json::to_vec(content)?.len() > 393_216 { return Err(MatrixCoreError::InvalidRequest); }
    Ok(json!({
        "kind":"encrypted","messageType":Value::Null,"body":Value::Null,"format":Value::Null,"formattedBody":Value::Null,
        "relationKind":Value::Null,"relationTargetEventId":Value::Null,"replyToEventId":Value::Null,"reactionKey":Value::Null,
        "presentationExtensions":{},"encryptedContent":content
    }))
}

fn parse_message_content(content: &Value, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let object = content.as_object().ok_or(MatrixCoreError::InvalidRequest)?;
    let message_type = object.get("msgtype").and_then(Value::as_str).unwrap_or("m.text");
    if !matches!(message_type, "m.text" | "m.notice" | "m.emote") { return Err(MatrixCoreError::UnsupportedMessageType); }
    let body = object.get("body").and_then(Value::as_str).map(str::trim).filter(|v| !v.is_empty() && v.len() <= 65_536).ok_or(MatrixCoreError::InvalidRequest)?;
    let (relation_kind, relation_target_event_id, reply_to_event_id) = parse_relation(object.get("m.relates_to"), server_name)?;
    let extensions = object.iter().filter(|(k,_)| matches!(k.as_str(), "com.openclaw.approval"|"com.openclaw.presentation"))
        .map(|(k,v)|(k.clone(),v.clone())).collect::<BTreeMap<_,_>>();
    Ok(json!({
        "kind":"message","messageType":message_type,"body":body,"format":object.get("format").and_then(Value::as_str),
        "formattedBody":object.get("formatted_body").and_then(Value::as_str),"relationKind":relation_kind,
        "relationTargetEventId":relation_target_event_id,"replyToEventId":reply_to_event_id,"reactionKey":Value::Null,
        "presentationExtensions":extensions,"encryptedContent":Value::Null
    }))
}

fn parse_reaction_content(content: &Value, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let relation = content.get("m.relates_to").and_then(Value::as_object).ok_or(MatrixCoreError::InvalidRequest)?;
    if relation.get("rel_type").and_then(Value::as_str) != Some("m.annotation") { return Err(MatrixCoreError::InvalidRequest); }
    let event_id = relation.get("event_id").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?;
    validate_event_for_server(event_id, server_name)?;
    let key = relation.get("key").and_then(Value::as_str).map(str::trim).filter(|v| !v.is_empty() && v.len() <= 256).ok_or(MatrixCoreError::InvalidRequest)?;
    Ok(json!({
        "kind":"reaction","messageType":Value::Null,"body":Value::Null,"format":Value::Null,"formattedBody":Value::Null,
        "relationKind":"m.annotation","relationTargetEventId":event_id,"replyToEventId":Value::Null,"reactionKey":key,
        "presentationExtensions":{},"encryptedContent":Value::Null
    }))
}

fn parse_relation(value: Option<&Value>, server_name: &OwnedServerName) -> Result<(Option<String>,Option<String>,Option<String>),MatrixCoreError> {
    let Some(object) = value.and_then(Value::as_object) else { return Ok((None,None,None)); };
    if let Some(in_reply_to) = object.get("m.in_reply_to").and_then(Value::as_object) {
        let event_id = in_reply_to.get("event_id").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?;
        validate_event_for_server(event_id, server_name)?;
        return Ok((Some("m.in_reply_to".to_string()), Some(event_id.to_string()), Some(event_id.to_string())));
    }
    let rel_type = object.get("rel_type").and_then(Value::as_str).map(ToString::to_string);
    let target = object.get("event_id").and_then(Value::as_str).map(ToString::to_string);
    if let Some(target) = target.as_deref() { validate_event_for_server(target, server_name)?; }
    Ok((rel_type, target, None))
}

fn serialize_send_value(input_json: &str) -> Result<Value, MatrixCoreError> { parse_object_value(input_json) }

fn send_response_value(input_json: &str, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let input: Value = parse(input_json)?;
    let id = input.get("messageId").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?;
    Ok(json!({"event_id": matrix_event_id(id, server_name)?.to_string()}))
}

fn decode_room_value(input_json: &str, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let input: Value = parse(input_json)?;
    let room = input.get("roomId").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?;
    let id = validate_room_for_server(room, server_name)?;
    let localpart = id.as_str().trim_start_matches('!').split(':').next().ok_or(MatrixCoreError::InvalidRequest)?;
    Ok(json!({"conversationId": decode_localpart(localpart)?}))
}

fn decode_event_value(input_json: &str, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let input: Value = parse(input_json)?;
    let event = input.get("eventId").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?;
    let id = validate_event_for_server(event, server_name)?;
    let localpart = id.as_str().trim_start_matches('$').split(':').next().ok_or(MatrixCoreError::InvalidRequest)?;
    Ok(json!({"eventId": decode_localpart(localpart)?}))
}

fn room_id_value(input_json: &str, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let input: Value = parse(input_json)?;
    let id = input.get("conversationId").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?;
    Ok(json!({"roomId":matrix_room_id(id,server_name)?.to_string()}))
}

fn user_id_value(input_json: &str, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let input: Value = parse(input_json)?;
    let member = input.get("memberRef").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?;
    Ok(json!({"userId":matrix_user_id(member,server_name)?.to_string()}))
}

fn matrix_error_value(input_json: &str) -> Result<Value, MatrixCoreError> {
    let input: Value = parse(input_json)?;
    let errcode = input.get("errcode").and_then(Value::as_str).filter(|v| v.starts_with("M_") && v.len() <= 128).ok_or(MatrixCoreError::InvalidRequest)?;
    let error = input.get("error").and_then(Value::as_str).map(str::trim).filter(|v| !v.is_empty() && v.len() <= 2048).ok_or(MatrixCoreError::InvalidRequest)?;
    Ok(json!({"errcode":errcode,"error":error}))
}

fn parse_sync_value(input_json: &str, server_name: &OwnedServerName) -> Result<Value, MatrixCoreError> {
    let root: Value = parse(input_json)?;
    let next_batch = root.get("next_batch").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?;
    decode_sync_token(next_batch)?;
    let mut rooms = Vec::new();
    if let Some(joined) = root.pointer("/rooms/join").and_then(Value::as_object) {
        for (room_id, room) in joined {
            validate_room_for_server(room_id, server_name)?;
            let mut messages = Vec::new();
            if let Some(events) = room.pointer("/timeline/events").and_then(Value::as_array) {
                for event in events {
                    let event_id = event.get("event_id").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?;
                    validate_event_for_server(event_id, server_name)?;
                    let sender = event.get("sender").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?;
                    validate_user_for_server(sender, server_name)?;
                    let content = event.get("content").cloned().unwrap_or(Value::Object(Default::default()));
                    messages.push(ClientMessageProjection {
                        event_id:event_id.to_string(),sender:sender.to_string(),origin_server_ts:event.get("origin_server_ts").and_then(Value::as_i64).unwrap_or_default(),
                        body:content.get("body").and_then(Value::as_str).map(ToString::to_string),
                        content_type:event.get("type").and_then(Value::as_str).unwrap_or("m.room.message").to_string(),
                    });
                }
            }
            rooms.push(ClientRoomProjection { room_id:room_id.clone(), title:room.pointer("/state/events/0/content/name").and_then(Value::as_str).unwrap_or("Room").to_string(), unread_count:room.pointer("/unread_notifications/notification_count").and_then(Value::as_u64).unwrap_or_default(), messages });
        }
    }
    Ok(serde_json::to_value(ClientSyncProjection{next_batch:next_batch.to_string(),rooms})?)
}

fn parse_messages_value(input_json:&str,server_name:&OwnedServerName)->Result<Value,MatrixCoreError>{
    let root:Value=parse(input_json)?;
    let mut messages=Vec::new();
    for event in root.get("chunk").and_then(Value::as_array).into_iter().flatten(){
        let event_id=event.get("event_id").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?; validate_event_for_server(event_id,server_name)?;
        let sender=event.get("sender").and_then(Value::as_str).ok_or(MatrixCoreError::InvalidRequest)?; validate_user_for_server(sender,server_name)?;
        let content=event.get("content").cloned().unwrap_or(Value::Object(Default::default()));
        messages.push(ClientMessageProjection{event_id:event_id.to_string(),sender:sender.to_string(),origin_server_ts:event.get("origin_server_ts").and_then(Value::as_i64).unwrap_or_default(),body:content.get("body").and_then(Value::as_str).map(ToString::to_string),content_type:event.get("type").and_then(Value::as_str).unwrap_or("m.room.message").to_string()});
    }
    Ok(json!({"messages":messages,"end":root.get("end").and_then(Value::as_str).unwrap_or_default()}))
}

fn parse_versions_value(input_json:&str)->Result<Value,MatrixCoreError>{
    let root:Value=parse(input_json)?;
    let versions=root.get("versions").and_then(Value::as_array).ok_or(MatrixCoreError::InvalidRequest)?;
    Ok(json!({"versions":versions}))
}

fn validate_server_name(value:&str)->Result<OwnedServerName,MatrixCoreError>{
    if value.trim().is_empty(){return Err(MatrixCoreError::EmptyServerName);} OwnedServerName::try_from(value).map_err(|_|MatrixCoreError::InvalidMatrixId{kind:"server name"})
}
fn matrix_user_id(subject:&str,server_name:&OwnedServerName)->Result<OwnedUserId,MatrixCoreError>{if subject.trim().is_empty(){return Err(MatrixCoreError::EmptySubject);} let local=encode_localpart(subject);OwnedUserId::try_from(format!("@{}:{}",local,server_name)).map_err(|_|MatrixCoreError::InvalidMatrixId{kind:"user id"})}
fn matrix_room_id(id:&str,server_name:&OwnedServerName)->Result<OwnedRoomId,MatrixCoreError>{OwnedRoomId::try_from(format!("!{}:{}",encode_localpart(id),server_name)).map_err(|_|MatrixCoreError::InvalidMatrixId{kind:"room id"})}
fn matrix_event_id(id:&str,server_name:&OwnedServerName)->Result<OwnedEventId,MatrixCoreError>{OwnedEventId::try_from(format!("${}:{}",encode_localpart(id),server_name)).map_err(|_|MatrixCoreError::InvalidMatrixId{kind:"event id"})}
fn validate_user_for_server<'a>(value:&'a str,server:&OwnedServerName)->Result<&'a str,MatrixCoreError>{let id=OwnedUserId::try_from(value).map_err(|_|MatrixCoreError::InvalidMatrixId{kind:"user id"})?;if id.server_name()!=server.as_ref(){return Err(MatrixCoreError::InvalidMatrixId{kind:"user id"});}Ok(value)}
fn validate_room_for_server(value:&str,server:&OwnedServerName)->Result<OwnedRoomId,MatrixCoreError>{let id=OwnedRoomId::try_from(value).map_err(|_|MatrixCoreError::InvalidMatrixId{kind:"room id"})?;if id.server_name()!=server.as_ref(){return Err(MatrixCoreError::InvalidMatrixId{kind:"room id"});}Ok(id)}
fn validate_event_for_server(value:&str,server:&OwnedServerName)->Result<OwnedEventId,MatrixCoreError>{let id=OwnedEventId::try_from(value).map_err(|_|MatrixCoreError::InvalidMatrixId{kind:"event id"})?;if let Some(name)=id.server_name(){if name!=server.as_ref(){return Err(MatrixCoreError::InvalidMatrixId{kind:"event id"});}}Ok(id)}
fn encode_localpart(value:&str)->String{value.as_bytes().iter().map(|b|format!("{b:02x}")).collect()}
fn decode_localpart(value:&str)->Result<String,MatrixCoreError>{if value.len()%2!=0{return Err(MatrixCoreError::InvalidMatrixId{kind:"encoded localpart"});}let bytes=(0..value.len()).step_by(2).map(|i|u8::from_str_radix(&value[i..i+2],16).map_err(|_|MatrixCoreError::InvalidMatrixId{kind:"encoded localpart"})).collect::<Result<Vec<_>,_>>()?;String::from_utf8(bytes).map_err(|_|MatrixCoreError::InvalidMatrixId{kind:"encoded localpart"})}
fn encode_sync_token(cursor:&str)->String{format!("w1_{}",encode_localpart(cursor))}
fn decode_sync_token(value:&str)->Result<String,MatrixCoreError>{let raw=value.strip_prefix("w1_").ok_or(MatrixCoreError::InvalidSyncToken)?;decode_localpart(raw).map_err(|_|MatrixCoreError::InvalidSyncToken)}
fn parse<T:for<'de>Deserialize<'de>>(input:&str)->Result<T,MatrixCoreError>{serde_json::from_str(input).map_err(|_|MatrixCoreError::InvalidRequest)}

fn room_state_events(conversation:&CanonicalConversationInput,server:&OwnedServerName)->Result<Vec<MatrixEvent>,MatrixCoreError>{
    let mut events=Vec::new();
    let creator=conversation.memberships.iter().find(|m|m.state=="joined").map(|m|m.member_ref.as_str()).unwrap_or("system");
    let sender=matrix_user_id(creator,server)?.to_string();
    events.push(MatrixEvent{event_type:"m.room.name".into(),sender:sender.clone(),event_id:matrix_event_id(&format!("{}-name",conversation.conversation_id),server)?.to_string(),origin_server_ts:conversation.updated_at_epoch_millis,content:json!({"name":conversation.title}),state_key:Some(String::new())});
    if let Some(algorithm)=&conversation.encryption_algorithm{events.push(MatrixEvent{event_type:"m.room.encryption".into(),sender:sender.clone(),event_id:matrix_event_id(&format!("{}-encryption",conversation.conversation_id),server)?.to_string(),origin_server_ts:conversation.updated_at_epoch_millis,content:json!({"algorithm":algorithm}),state_key:Some(String::new())});}
    for membership in &conversation.memberships{events.push(membership_event(conversation,membership,server)?);}
    Ok(events)
}
fn membership_event(conversation:&CanonicalConversationInput,membership:&CanonicalMembershipInput,server:&OwnedServerName)->Result<MatrixEvent,MatrixCoreError>{let user=matrix_user_id(&membership.member_ref,server)?.to_string();Ok(MatrixEvent{event_type:"m.room.member".into(),sender:user.clone(),event_id:matrix_event_id(&format!("{}-member-{}",conversation.conversation_id,membership.member_ref),server)?.to_string(),origin_server_ts:conversation.updated_at_epoch_millis,content:json!({"membership":membership.state}),state_key:Some(user)})}
fn message_event(message:&CanonicalMessageInput,server:&OwnedServerName)->Result<MatrixEvent,MatrixCoreError>{let sender=matrix_user_id(&message.sender_ref,server)?.to_string();let content=if message.redacted{json!({})}else if message.kind=="encrypted"{message.encrypted_content.clone().ok_or(MatrixCoreError::InvalidRequest)?}else if message.kind=="reaction"{json!({"m.relates_to":{"rel_type":"m.annotation","event_id":matrix_event_id(message.relation_target_event_id.as_deref().ok_or(MatrixCoreError::InvalidRequest)?,server)?.to_string(),"key":message.reaction_key.as_deref().ok_or(MatrixCoreError::InvalidRequest)?}})}else{let mut object=serde_json::Map::new();object.insert("msgtype".into(),json!(message.message_type.as_deref().unwrap_or("m.text")));object.insert("body".into(),json!(message.body.as_deref().unwrap_or_default()));if let Some(format)=&message.format{object.insert("format".into(),json!(format));}if let Some(body)=&message.formatted_body{object.insert("formatted_body".into(),json!(body));}for(k,v)in&message.presentation_extensions{object.insert(k.clone(),v.clone());}Value::Object(object)};Ok(MatrixEvent{event_type:if message.kind=="encrypted"{"m.room.encrypted"}else if message.kind=="reaction"{"m.reaction"}else{"m.room.message"}.into(),sender,event_id:matrix_event_id(&message.message_id,server)?.to_string(),origin_server_ts:message.sent_at_epoch_millis,content,state_key:None})}

#[cfg(feature="jni")]
mod jni_bridge{
    use super::*;
    use jni::{EnvUnowned, JavaVM, sys::jstring};
    use std::ptr;

    fn with_env_result(vm:&JavaVM,f:impl FnOnce(&mut jni::Env<'_>)->Result<jni::objects::JString<'_>,jni::errors::Error>)->jstring{
        let mut env=match vm.attach_current_thread(){Ok(env)=>env,Err(_)=>return ptr::null_mut()};
        match f(&mut env){Ok(value)=>value.into_raw(),Err(_)=>ptr::null_mut()}
    }

    #[no_mangle]
    pub extern "system" fn Java_com_massimotter_weave_backend_matrix_NativeMatrixCore_projectJson(
        env:EnvUnowned<'_>,_class:jni::objects::JClass<'_>,operation:jni::objects::JString<'_>,input_json:jni::objects::JString<'_>,server_name:jni::objects::JString<'_>)->jstring{
        let Ok(vm)=env.get_java_vm() else{return ptr::null_mut()};
        with_env_result(&vm,|env|{
            let operation:String=env.get_string(&operation)?.into();
            let input:String=env.get_string(&input_json)?.into();
            let server:String=env.get_string(&server_name)?.into();
            env.new_string(project_json_or_error(operation,input,server))
        })
    }
}

#[cfg(test)]
mod tests{
    use super::*;
    #[test]fn ids_round_trip(){let ids=project_weave_matrix_ids("user:abc".into(),"room-1".into(),"api.weave.test".into()).unwrap();assert!(ids.user_id.starts_with('@'));assert!(ids.room_id.starts_with('!'));}
    #[test]fn sync_tokens_are_opaque_and_reversible(){let token=encode_sync_token("chat-revision-42|e2ee:5");assert_eq!(decode_sync_token(&token).unwrap(),"chat-revision-42|e2ee:5");}
}
