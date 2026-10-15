use matrix_sdk::ruma;
use matrix_sdk::ruma::api::IncomingResponse;
use matrix_sdk::ruma::exports::http;
use matrix_sdk_base::cross_process_lock::CrossProcessLockConfig;
use matrix_sdk_base::store::{RoomLoadSettings, StoreConfig};
use matrix_sdk_base::{BaseClient, DmRoomDefinition, RoomState, SessionMeta, ThreadingSupport};
use serde_json::{json, Value};

fn response(value: Value) -> ruma::api::client::sync::sync_events::v3::Response {
    ruma::api::client::sync::sync_events::v3::Response::try_from_http_response(
        http::Response::builder()
            .status(200)
            .body(serde_json::to_vec(&value).unwrap())
            .unwrap(),
    )
    .unwrap()
}

#[test]
fn invitation_requires_a_new_canonical_sync_cursor() {
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    runtime.block_on(async {
        let issuer = "https://issuer.test";
        let server = "api.weave.test";
        let who: Value = serde_json::from_str(&weave_matrix_protocol::project_json_or_error(
            "whoami".into(),
            json!({"subject":"peer", "identityIssuer":issuer, "deviceId":"DEVICE0001"}).to_string(),
            server.into(),
        ))
        .unwrap();
        let client = BaseClient::new(
            StoreConfig::new(CrossProcessLockConfig::SingleProcess),
            ThreadingSupport::Disabled,
            DmRoomDefinition::default(),
        );
        client
            .activate(
                SessionMeta {
                    user_id: who["user_id"].as_str().unwrap().try_into().unwrap(),
                    device_id: "DEVICE0001".into(),
                },
                RoomLoadSettings::default(),
                None,
            )
            .await
            .unwrap();
        let project = |cursor: &str, invited: bool| -> Value {
            let conversations = if invited {
                json!([{
                    "conversationId":"room-owned", "title":"Room", "inviterRef":"user:author",
                    "memberships":[{"memberRef":"user:peer", "state":"invited"}]
                }])
            } else {
                json!([])
            };
            serde_json::from_str(&weave_matrix_protocol::project_json_or_error(
                "sync".into(),
                json!({"subject":"user:peer", "identityIssuer":issuer, "cursor":cursor,
                    "conversations":conversations})
                .to_string(),
                server.into(),
            ))
            .unwrap()
        };
        let empty = project("chat-revision-0", false);
        client
            .receive_sync_response(response(empty.clone()))
            .await
            .unwrap();
        assert!(client.rooms().is_empty());
        let pending = project("chat-revision-0", true);
        assert_eq!(empty["next_batch"], pending["next_batch"]);
        assert_eq!(pending["rooms"]["invite"].as_object().unwrap().len(), 1);
        client
            .receive_sync_response(response(pending))
            .await
            .unwrap();
        assert!(client.rooms().is_empty());
        client
            .receive_sync_response(response(project("chat-revision-1", true)))
            .await
            .unwrap();
        assert_eq!(client.rooms().len(), 1);
        assert_eq!(client.rooms()[0].state(), RoomState::Invited);
        assert_eq!(client.rooms()[0].name().as_deref(), Some("Room"));
    });
}
