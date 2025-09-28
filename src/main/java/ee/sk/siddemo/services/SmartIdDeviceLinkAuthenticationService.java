package ee.sk.siddemo.services;

/*-
 * #%L
 * Smart-ID sample Java client
 * %%
 * Copyright (C) 2018 - 2025 SK ID Solutions AS
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ee.sk.siddemo.model.DeviceLinkAuthenticationDeviceLinkSessionInfo;
import ee.sk.siddemo.model.UserDocumentNumberRequest;
import ee.sk.siddemo.model.UserRequest;
import ee.sk.smartid.AuthenticationCertificateLevel;
import ee.sk.smartid.DeviceLinkAuthenticationSessionRequestBuilder;
import ee.sk.smartid.HashAlgorithm;
import ee.sk.smartid.RpChallengeGenerator;
import ee.sk.smartid.SignatureAlgorithm;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.common.devicelink.interactions.DeviceLinkInteraction;
import ee.sk.smartid.rest.dao.DeviceLinkAuthenticationSessionRequest;
import ee.sk.smartid.rest.dao.DeviceLinkSessionResponse;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.rest.dao.SessionStatus;
import jakarta.servlet.http.HttpSession;

@Service
public class SmartIdDeviceLinkAuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdDeviceLinkAuthenticationService.class);

    private final SmartIdClient smartIdClient;
    private final SmartIdSessionsStatusService smartIdSessionsStatusService;
    private final SessionStore sessionStore;

    @Value("${sid.auth.displayText}")
    private String displayText;

    public SmartIdDeviceLinkAuthenticationService(SmartIdClient smartIdClient,
                                                  SmartIdSessionsStatusService smartIdSessionsStatusService,
                                                  SessionStore sessionStore) {
        this.smartIdClient = smartIdClient;
        this.smartIdSessionsStatusService = smartIdSessionsStatusService;
        this.sessionStore = sessionStore;
    }

    public void startAuthentication(HttpSession session) {
        String rpChallenge = RpChallengeGenerator.generate().toBase64EncodedValue();
        var authenticationCertificateLevel = AuthenticationCertificateLevel.ADVANCED;
        DeviceLinkAuthenticationSessionRequestBuilder builder = smartIdClient.createDeviceLinkAuthentication()
                .withRpChallenge(rpChallenge)
                .withCertificateLevel(authenticationCertificateLevel)
                .withSignatureAlgorithm(SignatureAlgorithm.RSASSA_PSS)
                .withHashAlgorithm(HashAlgorithm.SHA3_512)
                .withInteractions(List.of(DeviceLinkInteraction.displayTextAndPin(displayText)))
                .withShareMdClientIpAddress(true);
        DeviceLinkSessionResponse response = builder.initAuthenticationSession();
        DeviceLinkAuthenticationSessionRequest request = builder.getAuthenticationSessionRequest();

        saveToSession(session, request, response);
        smartIdSessionsStatusService.startPolling(session, response.sessionID());
    }

    public void startAuthentication(HttpSession session, UserRequest userRequest) {
        String rpChallenge = RpChallengeGenerator.generate().toBase64EncodedValue();
        var semanticsIdentifier = new SemanticsIdentifier(SemanticsIdentifier.IdentityType.PNO, userRequest.getCountry(), userRequest.getNationalIdentityNumber());
        var requestedCertificateLevel = AuthenticationCertificateLevel.ADVANCED;
        List<DeviceLinkInteraction> interactions = List.of(DeviceLinkInteraction.displayTextAndPin(displayText));
        DeviceLinkAuthenticationSessionRequestBuilder builder = smartIdClient.createDeviceLinkAuthentication()
                .withRpChallenge(rpChallenge)
                .withSemanticsIdentifier(semanticsIdentifier)
                .withCertificateLevel(requestedCertificateLevel)
                .withSignatureAlgorithm(SignatureAlgorithm.RSASSA_PSS)
                .withHashAlgorithm(HashAlgorithm.SHA3_512)
                .withShareMdClientIpAddress(true)
                .withInteractions(interactions);
        DeviceLinkSessionResponse response = builder.initAuthenticationSession();
        DeviceLinkAuthenticationSessionRequest request = builder.getAuthenticationSessionRequest();

        saveToSession(session, request, response);
        smartIdSessionsStatusService.startPolling(session, response.sessionID());
    }

    public void startAuthentication(HttpSession session, UserDocumentNumberRequest userDocumentNumberRequest) {
        String rpChallenge = RpChallengeGenerator.generate().toBase64EncodedValue();
        var requestedCertificateLevel = AuthenticationCertificateLevel.ADVANCED;
        List<DeviceLinkInteraction> interactions = List.of(DeviceLinkInteraction.displayTextAndPin(displayText));
        DeviceLinkAuthenticationSessionRequestBuilder builder = smartIdClient.createDeviceLinkAuthentication()
                .withRpChallenge(rpChallenge)
                .withDocumentNumber(userDocumentNumberRequest.getDocumentNumber())
                .withCertificateLevel(requestedCertificateLevel)
                .withSignatureAlgorithm(SignatureAlgorithm.RSASSA_PSS)
                .withHashAlgorithm(HashAlgorithm.SHA3_512)
                .withShareMdClientIpAddress(true)
                .withInteractions(interactions);
        DeviceLinkSessionResponse response = builder.initAuthenticationSession();
        DeviceLinkAuthenticationSessionRequest request = builder.getAuthenticationSessionRequest();

        saveToSession(session, request, response);
        smartIdSessionsStatusService.startPolling(session, response.sessionID());
    }

    public boolean checkAuthenticationStatus(HttpSession session) {
        Optional<SessionStatus> sessionStatus = smartIdSessionsStatusService.getSessionsStatus(session.getId());
        return sessionStatus
                .map(status -> {
                    if (status.getState().equals("COMPLETE")) {
                        DeviceLinkAuthenticationDeviceLinkSessionInfo sessionInfo = (DeviceLinkAuthenticationDeviceLinkSessionInfo) sessionStore.get(session.getId(), "deviceLinkSessionInfo");
                        sessionInfo.setSessionStatus(status);
                        logger.debug("Mobile device IP address: {}", status.getDeviceIpAddress());
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    private void saveToSession(HttpSession session, DeviceLinkAuthenticationSessionRequest request, DeviceLinkSessionResponse response) {
        var deviceLinkAuthenticationDeviceLinkSessionInfo = new DeviceLinkAuthenticationDeviceLinkSessionInfo(response, request);
        sessionStore.put(session.getId(), "deviceLinkSessionInfo", deviceLinkAuthenticationDeviceLinkSessionInfo);
    }
}
