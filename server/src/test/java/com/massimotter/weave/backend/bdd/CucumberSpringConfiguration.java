package com.massimotter.weave.backend.bdd;

import com.massimotter.weave.backend.WeaveBackendApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@CucumberContextConfiguration
@SpringBootTest(
        classes = WeaveBackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
                "weave.meetings.livekit.enabled=true",
                "weave.meetings.livekit.url=",
                "weave.meetings.livekit.api-key=",
                "weave.meetings.livekit.api-secret=",
                "weave.meetings.livekit.token-endpoint=",
                "weave.context.authorization.memberships[0].tenant-id=tenant-default",
                "weave.context.authorization.memberships[0].context-id=workspace-default",
                "weave.context.authorization.memberships[0].principal-ref=user:user-123",
                "weave.context.authorization.memberships[0].role=MEMBER",
                "weave.context.authorization.memberships[0].source=cucumber-open-standards"
        })
@AutoConfigureMockMvc
public class CucumberSpringConfiguration {

    @MockBean
    private JwtDecoder jwtDecoder;
}
