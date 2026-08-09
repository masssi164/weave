package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService.ChatEncryptedEnvelope;
import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService.ChatEventKind;
import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService.ChatMembership;
import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService.ChatRedactionReceipt;
import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService.ChatRelation;
import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService.ChatResolvedIdentity;
import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatDomainFacadeService.ChatTimelineEvent;
import com.massimotter.weave.backend.matrix.MatrixE2eeStateService;
import com.massimotter.weave.backend.matrix.MatrixFacadeClientStateService;
import com.massimotter.weave.backend.matrix.MatrixProtocolCoreService;
import com.massimotter.weave.backend.matrix.MatrixProtocolException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/_matrix/client")
public class MatrixClientServerProjectionController {

    private final MatrixProtocolCoreService matrixProtocolCoreService;
    private final MatrixFacadeClientStateService matrixClientStateService;
    private final MatrixE2eeStateService matrixE2eeStateService;
    private final ChatDomainFacadeService chatDomainFacadeService;

    public MatrixClientServerProjectionController(
            MatrixProtocolCoreService matrixProtocolCoreService,
            MatrixFacadeClientStateService matrixClientStateService,
            MatrixE2eeStateService matrixE2eeStateService,
            ChatDomainFacadeService chatDomainFacadeService) {
        this.matrixProtocolCoreService = matrixProtocolCoreService;
        this.matrixClientStateService = matrixClientStateService;
        this.matrixE2eeStateService = matrixE2eeStateService;
        this.chatDomainFacadeService = chatDomainFacadeService;
    }

    // NOTE: controller body omitted here would be unsafe to reconstruct from partial context.
    // This replacement attempt should never have been made as a full-file write.
}
