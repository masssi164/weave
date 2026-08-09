#[cfg(feature = "flutter")]
mod flutter_crypto;
#[cfg(feature = "flutter")]
mod frb_generated;

pub mod frb_api {
    pub fn project_matrix_json(operation: String, input_json: String, server_name: String) -> String {
        weave_matrix_core::project_json_or_error(operation, input_json, server_name)
    }

    #[cfg(feature = "flutter")]
    pub async fn initialize_matrix_client(profile_key: String, homeserver_url: String, user_id: String, device_id: String, access_token: String, store_path: String, store_passphrase: String, extra_root_certificate_pem: String) -> String {
        crate::flutter_crypto::initialize(profile_key, homeserver_url, user_id, device_id, access_token, store_path, store_passphrase, extra_root_certificate_pem).await
    }
    #[cfg(feature = "flutter")]
    pub async fn sync_matrix_client(profile_key: String) -> String { crate::flutter_crypto::sync(profile_key).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_rooms(profile_key: String) -> String { crate::flutter_crypto::rooms(profile_key).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_create_encrypted_room(profile_key: String, title: String) -> String { crate::flutter_crypto::create_encrypted_room(profile_key, title).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_room_messages(profile_key: String, room_id: String, limit: u32) -> String { crate::flutter_crypto::room_messages(profile_key, room_id, limit).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_send_text(profile_key: String, room_id: String, body: String) -> String { crate::flutter_crypto::send_text(profile_key, room_id, body).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_mark_read(profile_key: String, room_id: String, event_id: String) -> String { crate::flutter_crypto::mark_read(profile_key, room_id, event_id).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_security_state(profile_key: String) -> String { crate::flutter_crypto::security_state(profile_key).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_bootstrap_recovery(profile_key: String, passphrase: String) -> String { crate::flutter_crypto::bootstrap_recovery(profile_key, passphrase).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_recover(profile_key: String, recovery_key_or_passphrase: String) -> String { crate::flutter_crypto::recover(profile_key, recovery_key_or_passphrase).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_start_verification(profile_key: String) -> String { crate::flutter_crypto::start_verification(profile_key).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_accept_verification(profile_key: String) -> String { crate::flutter_crypto::accept_verification(profile_key).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_start_sas(profile_key: String) -> String { crate::flutter_crypto::start_sas(profile_key).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_confirm_sas(profile_key: String, matches: bool) -> String { crate::flutter_crypto::confirm_sas(profile_key, matches).await }
    #[cfg(feature = "flutter")]
    pub async fn matrix_cancel_verification(profile_key: String) -> String { crate::flutter_crypto::cancel_verification(profile_key).await }
    #[cfg(feature = "flutter")]
    pub fn matrix_dismiss_verification(profile_key: String) -> String { crate::flutter_crypto::dismiss_verification(profile_key) }
    #[cfg(feature = "flutter")]
    pub async fn dispose_matrix_client(profile_key: String) -> String { crate::flutter_crypto::dispose(profile_key).await }
}
