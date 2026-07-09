use ruma::{OwnedRoomId, OwnedUserId};
use serde::{Deserialize, Serialize};
use thiserror::Error;
use tracing::instrument;

pub const MATRIX_PROTOCOL_SURFACE: &str = "matrix-client-server-facade";
pub const OIDC_GATEKEEPER: &str = "spring-boot-resource-server";
pub const SERVER_JNI_BOUNDARY: &str = "server-jni-wrapper";
pub const FLUTTER_BRIDGE_BOUNDARY: &str = "flutter-rust-bridge";

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MatrixFacadeDescriptor {
    pub protocol_surface: String,
    pub oidc_gatekeeper: String,
    pub northbound_homeserver_dependency: bool,
    pub rust_protocol_core: String,
    pub server_jni_boundary: String,
    pub flutter_bridge_boundary: String,
    pub server_name: String,
    pub supported_matrix_versions: Vec<String>,
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
    #[error("failed to serialize Matrix core response")]
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
            MatrixCoreError::Serialization(_) => MatrixCoreFailure::Serialization,
        }
    }
}

#[instrument(skip(server_name))]
pub fn matrix_facade_descriptor(
    server_name: String,
) -> Result<MatrixFacadeDescriptor, MatrixCoreError> {
    ensure_server_name(&server_name)?;

    Ok(MatrixFacadeDescriptor {
        protocol_surface: MATRIX_PROTOCOL_SURFACE.to_string(),
        oidc_gatekeeper: OIDC_GATEKEEPER.to_string(),
        northbound_homeserver_dependency: false,
        rust_protocol_core: "ruma-serde-serde_json-thiserror-tracing".to_string(),
        server_jni_boundary: SERVER_JNI_BOUNDARY.to_string(),
        flutter_bridge_boundary: FLUTTER_BRIDGE_BOUNDARY.to_string(),
        server_name,
        supported_matrix_versions: vec!["v1.18".to_string()],
    })
}

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
    ensure_server_name(&server_name)?;

    let user_localpart = canonical_localpart(&subject, "subject")?;
    let room_localpart = canonical_localpart(&conversation_id, "conversation")?;

    let user_id = validate_user_id(format!("@{}:{}", user_localpart, server_name))?;
    let room_id = validate_room_id(format!("!{}:{}", room_localpart, server_name))?;

    Ok(MatrixIdProjection { user_id, room_id })
}

fn ensure_server_name(server_name: &str) -> Result<(), MatrixCoreError> {
    if server_name.trim().is_empty() {
        return Err(MatrixCoreError::EmptyServerName);
    }

    validate_user_id(format!("@weave-core:{}", server_name.trim()))?;
    Ok(())
}

fn validate_user_id(value: String) -> Result<String, MatrixCoreError> {
    OwnedUserId::try_from(value)
        .map(|id| id.to_string())
        .map_err(|_| MatrixCoreError::InvalidMatrixId { kind: "user_id" })
}

fn validate_room_id(value: String) -> Result<String, MatrixCoreError> {
    OwnedRoomId::try_from(value)
        .map(|id| id.to_string())
        .map_err(|_| MatrixCoreError::InvalidMatrixId { kind: "room_id" })
}

fn canonical_localpart(value: &str, field: &'static str) -> Result<String, MatrixCoreError> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return Err(if field == "subject" {
            MatrixCoreError::EmptySubject
        } else {
            MatrixCoreError::InvalidMatrixId { kind: "room_id" }
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
        return Err(if field == "subject" {
            MatrixCoreError::EmptySubject
        } else {
            MatrixCoreError::InvalidMatrixId { kind: "room_id" }
        });
    }
    Ok(output)
}

#[cfg(feature = "flutter")]
pub mod frb_api {
    pub use crate::{
        matrix_facade_descriptor, matrix_facade_descriptor_json, project_weave_matrix_ids,
        MatrixCoreFailure, MatrixFacadeDescriptor, MatrixIdProjection,
    };
}

#[cfg(feature = "jni")]
pub mod jni_bridge {
    use jni::errors::ThrowRuntimeExAndDefault;
    use jni::objects::{JClass, JString};
    use jni::sys::jstring;
    use jni::EnvUnowned;
    use serde_json::json;

    use crate::matrix_facade_descriptor_json;

    #[no_mangle]
    pub extern "system" fn Java_com_massimotter_weave_backend_matrix_NativeMatrixCore_matrixFacadeDescriptorJson<
        'local,
    >(
        mut unowned_env: EnvUnowned<'local>,
        _class: JClass<'local>,
        server_name: JString<'local>,
    ) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let server_name = server_name.try_to_string(env).unwrap_or_default();
                let payload = matrix_facade_descriptor_json(server_name).unwrap_or_else(|error| {
                    json!({
                        "code": "M_WEAVE_MATRIX_CORE_ERROR",
                        "error": error.support_safe_failure()
                    })
                    .to_string()
                });
                JString::from_str(env, payload).map(JString::into_raw)
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn descriptor_marks_spring_oidc_as_gatekeeper_and_never_requires_homeserver() {
        let descriptor = matrix_facade_descriptor("weave.local".to_string()).unwrap();

        assert_eq!(descriptor.protocol_surface, MATRIX_PROTOCOL_SURFACE);
        assert_eq!(descriptor.oidc_gatekeeper, OIDC_GATEKEEPER);
        assert!(!descriptor.northbound_homeserver_dependency);
        assert_eq!(descriptor.server_jni_boundary, SERVER_JNI_BOUNDARY);
        assert_eq!(descriptor.flutter_bridge_boundary, FLUTTER_BRIDGE_BOUNDARY);
        assert_eq!(descriptor.supported_matrix_versions, vec!["v1.18"]);
    }

    #[test]
    fn descriptor_json_is_support_safe_and_machine_readable() {
        let json = matrix_facade_descriptor_json("weave.local".to_string()).unwrap();
        let parsed: serde_json::Value = serde_json::from_str(&json).unwrap();

        assert_eq!(parsed["protocolSurface"], MATRIX_PROTOCOL_SURFACE);
        assert_eq!(parsed["oidcGatekeeper"], OIDC_GATEKEEPER);
        assert_eq!(parsed["northboundHomeserverDependency"], false);
        assert_eq!(
            parsed["rustProtocolCore"],
            "ruma-serde-serde_json-thiserror-tracing"
        );
    }

    #[test]
    fn projects_weave_ids_into_ruma_validated_matrix_ids() {
        let projection = project_weave_matrix_ids(
            "User 123@example.com".to_string(),
            "Conversation 456".to_string(),
            "weave.local".to_string(),
        )
        .unwrap();

        assert_eq!(projection.user_id, "@user_123_example.com:weave.local");
        assert_eq!(projection.room_id, "!conversation_456:weave.local");
    }

    #[test]
    fn invalid_server_name_fails_without_leaking_raw_provider_state() {
        let error = matrix_facade_descriptor("".to_string()).unwrap_err();

        assert_eq!(
            error.support_safe_failure(),
            MatrixCoreFailure::EmptyServerName
        );
    }
}
