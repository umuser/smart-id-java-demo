package ee.sk.siddemo.services;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.smartid.exception.useraccount.DocumentUnusableException;
import ee.sk.smartid.v3.DynamicLinkAuthenticationSessionResponse;
import ee.sk.smartid.v3.RandomChallenge;
import ee.sk.smartid.v3.SmartIdClient;
import ee.sk.smartid.v3.rest.dao.Interaction;
import ee.sk.smartid.v3.rest.dao.SessionStatus;
import ee.sk.smartid.v3.service.SmartIdRequestBuilderService;
import jakarta.servlet.http.HttpSession;

@Service
public class SmartIdV3AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdV3AuthenticationService.class);

    private final SmartIdClient smartIdClientV3;
    private final SmartIdV3SessionsStatusService smartIdV3SessionsStatusService;
    private final SmartIdRequestBuilderService smartIdRequestBuilderService;

    public SmartIdV3AuthenticationService(SmartIdClient smartIdClientV3,
                                          DynamicContentService dynamicContentService,
                                          SmartIdV3SessionsStatusService smartIdV3SessionsStatusService,
                                          SmartIdRequestBuilderService smartIdRequestBuilderService) {
        this.smartIdClientV3 = smartIdClientV3;
        this.smartIdV3SessionsStatusService = smartIdV3SessionsStatusService;
        this.smartIdRequestBuilderService = smartIdRequestBuilderService;
    }

    public void startAuthentication(HttpSession session) {
        String randomChallenge = RandomChallenge.generate();
        DynamicLinkAuthenticationSessionResponse response = smartIdClientV3.createDynamicLinkAuthentication()
                .withRandomChallenge(randomChallenge)
                .withAllowedInteractionsOrder(
                        List.of(Interaction.displayTextAndPIN("Login ? "))
                ).initAuthenticationSession();
        Instant responseReceivedTime = Instant.now();

        session.setAttribute("randomChallenge", randomChallenge);
        session.setAttribute("sessionSecret", response.getSessionSecret());
        session.setAttribute("sessionToken", response.getSessionToken());
        session.setAttribute("sessionID", response.getSessionID());
        session.setAttribute("responseReceivedTime", responseReceivedTime);

        startSessionsStatusPolling(session, response);
    }

    private void startSessionsStatusPolling(HttpSession session, DynamicLinkAuthenticationSessionResponse response) {
        smartIdV3SessionsStatusService.getSessionStatus(response.getSessionID(), (result, error) -> {
            if (error != null) {
                logger.debug("Error occurred while fetching session status", error);
                session.setAttribute("session_status", "ERROR");
                session.setAttribute("session_status_error_message", error.getMessage());
            } else {
                logger.debug("Querying sessions completed");
                session.setAttribute("session_status", "COMPLETED");
                session.setAttribute("session_status_response", result);
            }
        });
    }

    public void authenticate(HttpSession session) {
        SessionStatus sessionsStatusResponse = (SessionStatus) session.getAttribute("session_status_response");
        try {
            smartIdRequestBuilderService.createSmartIdAuthenticationResponse(sessionsStatusResponse, "QUALIFIED", null, (String) session.getAttribute("randomChallenge"));
        } catch (DocumentUnusableException ex) {
            throw new SidOperationException("Invalid authentication response", ex);
        }
    }
}
