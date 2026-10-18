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
fn projected_membership_advances_opaque_matrix_sync_token() {
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
        assert_ne!(empty["next_batch"], pending["next_batch"]);
        assert_eq!(pending["rooms"]["invite"].as_object().unwrap().len(), 1);
        let mut repeated_token = pending.clone();
        repeated_token["next_batch"] = empty["next_batch"].clone();
        client
            .receive_sync_response(response(repeated_token))
            .await
            .unwrap();
        assert!(client.rooms().is_empty());
        client
            .receive_sync_response(response(pending))
            .await
            .unwrap();
        assert_eq!(client.rooms().len(), 1);
        assert_eq!(client.rooms()[0].state(), RoomState::Invited);
        assert_eq!(client.rooms()[0].name().as_deref(), Some("Room"));
    });
}

#[test]
fn joined_room_becomes_left_without_remaining_in_the_joined_projection() {
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    runtime.block_on(async {
        let client = BaseClient::new(
            StoreConfig::new(CrossProcessLockConfig::SingleProcess),
            ThreadingSupport::Disabled,
            DmRoomDefinition::default(),
        );
        let who: Value = serde_json::from_str(&weave_matrix_protocol::project_json_or_error(
            "whoami".into(),
            json!({"subject":"member", "identityIssuer":"https://issuer.test", "deviceId":"DEVICE0001"}).to_string(),
            "api.weave.test".into(),
        )).unwrap();
        client.activate(SessionMeta {
            user_id: who["user_id"].as_str().unwrap().try_into().unwrap(),
            device_id: "DEVICE0001".into(),
        }, RoomLoadSettings::default(), None).await.unwrap();
        let project = |cursor: &str, joined: bool| -> Value {
            serde_json::from_str(&weave_matrix_protocol::project_json_or_error(
                "sync".into(),
                json!({"subject":"user:member", "identityIssuer":"https://issuer.test", "cursor":cursor,
                    "conversations":if joined { json!([{"conversationId":"owned-room", "title":"Room",
                        "memberships":[{"memberRef":"user:member", "state":"joined"}]}]) } else { json!([]) },
                    "leftRooms":if joined { vec![] } else { vec!["owned-room"] } }).to_string(),
                "api.weave.test".into(),
            )).unwrap()
        };
        client.receive_sync_response(response(project("chat-revision-1", true))).await.unwrap();
        assert_eq!(client.rooms().len(), 1);
        let room = client.rooms()[0].clone();
        assert_eq!(room.state(), RoomState::Joined);
        client.receive_sync_response(response(project("chat-revision-2", false))).await.unwrap();
        assert_eq!(room.state(), RoomState::Left);
        assert!(client.rooms().iter().all(|room| room.state() != RoomState::Joined));
    });
}

#[test]
fn admitted_direct_pair_uses_standard_account_data_without_invitation_acceptance() {
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    runtime.block_on(async {
        let issuer = "https://issuer.test";
        let server = "api.weave.test";
        let who = |subject: &str| -> String {
            let result: Value = serde_json::from_str(&weave_matrix_protocol::project_json_or_error(
                "whoami".into(), json!({"subject":subject,"identityIssuer":issuer,"deviceId":"DEVICE0001"}).to_string(), server.into())).unwrap();
            result["user_id"].as_str().unwrap().to_owned()
        };
        let author = who("author");
        let client = BaseClient::new(StoreConfig::new(CrossProcessLockConfig::SingleProcess),
            ThreadingSupport::Disabled, DmRoomDefinition::default());
        client.activate(SessionMeta { user_id: who("peer").try_into().unwrap(), device_id:"DEVICE0001".into() },
            RoomLoadSettings::default(),None).await.unwrap();
        let projected: Value = serde_json::from_str(&weave_matrix_protocol::project_json_or_error(
            "sync".into(),json!({"subject":"user:peer","identityIssuer":issuer,"cursor":"chat-revision-1",
                "conversations":[{"conversationId":"direct-pair","title":"Direct",
                    "memberships":[{"memberRef":"user:author","state":"joined"},{"memberRef":"user:peer","state":"joined"}]}],
                "accountData":{"m.direct":{(author):["!direct-pair:api.weave.test"],"@unrelated:api.weave.test":["!forbidden:api.weave.test"]}}
            }).to_string(),server.into())).unwrap();
        assert!(projected["rooms"]["invite"].as_object().unwrap().is_empty());
        client.receive_sync_response(response(projected)).await.unwrap();
        assert_eq!(client.rooms().len(),1);
        let room = &client.rooms()[0];
        assert_eq!(room.state(),RoomState::Joined);
        assert!(room.is_direct().await.unwrap());
        assert_eq!(room.direct_targets().len(),1);
        assert!(client.get_room(ruma::room_id!("!forbidden:api.weave.test")).is_none());
    });
}
