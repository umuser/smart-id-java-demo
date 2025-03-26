package ee.sk.siddemo.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.UserRequest;
import ee.sk.siddemo.services.SmartIdV3SessionsStatusService;
import ee.sk.smartid.exception.useraction.SessionTimeoutException;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.v3.AuthenticationResponseMapper;
import ee.sk.smartid.v3.RandomChallenge;
import ee.sk.smartid.v3.SmartIdClient;
import ee.sk.smartid.v3.rest.dao.NotificationAuthenticationSessionResponse;
import ee.sk.smartid.v3.rest.dao.NotificationInteraction;
import ee.sk.smartid.v3.rest.dao.SessionStatus;
import jakarta.servlet.http.HttpSession;

@Service
public class SmartIdV3NotificationBasedAuthenticationService {

    private final SmartIdClient smartIdClientV3;
    private final SmartIdV3SessionsStatusService sessionStatusService;

    @Value("${sid.v3.auth.displayText}")
    private String displayText;

    public SmartIdV3NotificationBasedAuthenticationService(SmartIdClient smartIdClientV3, SmartIdV3SessionsStatusService sessionStatusService) {
        this.smartIdClientV3 = smartIdClientV3;
        this.sessionStatusService = sessionStatusService;
    }

    public String startAuthenticationWithPersonCode(HttpSession session, UserRequest userRequest) {
        var semanticsIdentifier = new SemanticsIdentifier(SemanticsIdentifier.IdentityType.PNO, userRequest.getCountry(), userRequest.getNationalIdentityNumber());

        String randomChallenge = RandomChallenge.generate();
        NotificationAuthenticationSessionResponse sessionResponse = smartIdClientV3.createNotificationAuthentication()
                .withSemanticsIdentifier(semanticsIdentifier)
                .withRandomChallenge(randomChallenge)
                .withAllowedInteractionsOrder(List.of(NotificationInteraction.verificationCodeChoice(displayText)))
                .initAuthenticationSession();

        session.setAttribute("sessionID", sessionResponse.getSessionID());
        session.setAttribute("randomChallenge", randomChallenge);
        return sessionResponse.getVc().getValue();
    }

    public void checkAuthenticationStatus(HttpSession session) {
        String sessionId = (String) session.getAttribute("sessionID");
        if (sessionId == null) {
            throw new SidOperationException("Session ID is missing");
        }
        SessionStatus sessionStatus = sessionStatusService.poll(sessionId);
        saveValidateResponse(session, sessionStatus);
    }

    private static void saveValidateResponse(HttpSession session, SessionStatus status) {
        try {
            var signatureResponse = AuthenticationResponseMapper.from(status);
            session.setAttribute("authentication_response", signatureResponse);
        } catch (SessionTimeoutException ex) {
            throw new SidOperationException(ex.getMessage());
        }
    }
}
