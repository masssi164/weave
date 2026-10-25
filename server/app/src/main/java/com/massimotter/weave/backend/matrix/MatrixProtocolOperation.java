package com.massimotter.weave.backend.matrix;

/** Closed, versioned operation set crossing the server JNI protocol boundary. */
public enum MatrixProtocolOperation {
    DESCRIPTOR("descriptor"),
    VERSIONS("versions"),
    WHOAMI("whoami"),
    SYNC("sync"),
    VALIDATE_SYNC_TOKEN("validate-sync-token"),
    DECODE_SYNC_TOKEN("decode-sync-token"),
    JOINED_ROOMS("joined-rooms"),
    MESSAGES("messages"),
    MEMBERS("members"),
    PARSE_OBJECT("parse-object"),
    PARSE_SEND("parse-send"),
    PARSE_EVENT("parse-event"),
    SERIALIZE_SEND("serialize-send"),
    SEND_RESPONSE("send-response"),
    DECODE_ROOM("decode-room"),
    DECODE_EVENT("decode-event"),
    ROOM_ID("room-id"),
    USER_ID("user-id"),
    ERROR("error"),
    PARSE_SYNC("parse-sync"),
    PARSE_MESSAGES("parse-messages"),
    PARSE_VERSIONS("parse-versions"),
    PARSE_WHOAMI("parse-whoami");

    public static final int ABI_VERSION = 1;

    private final String wireName;

    MatrixProtocolOperation(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
