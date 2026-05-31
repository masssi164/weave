package com.massimotter.weave.backend.service;

public interface WeaverPaChatClient {
    WeaverPaChatTurnResult completeTurn(WeaverPaChatTurnRequest request);
}
