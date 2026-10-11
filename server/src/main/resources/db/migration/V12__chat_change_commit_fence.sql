CREATE TABLE weave_chat_change_commit_fence (
    id integer NOT NULL PRIMARY KEY CHECK (id = 1)
);

INSERT INTO weave_chat_change_commit_fence (id) VALUES (1);
