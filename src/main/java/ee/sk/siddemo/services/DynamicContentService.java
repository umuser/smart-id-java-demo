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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ee.sk.smartid.DeviceLinkType;
import ee.sk.smartid.QrCodeGenerator;
import ee.sk.smartid.SessionType;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.dao.DeviceLinkSessionResponse;
import jakarta.servlet.http.HttpSession;

@Service
public class DynamicContentService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicContentService.class);

    public final SmartIdClient smartIdClient;

    public DynamicContentService(SmartIdClient smartIdClient) {
        this.smartIdClient = smartIdClient;
    }

    public String getQrCode(HttpSession session, SessionType sessionType) {
        logger.debug("Getting QR-code content for session id: {}, session type: {}", session.getId(), sessionType);

        String digest = (String) session.getAttribute("rpChallenge");
        String interactions = (String) session.getAttribute("interactions");
        DeviceLinkSessionResponse sessionInitResponse = (DeviceLinkSessionResponse) session.getAttribute("sessionInitResponse");

        long elapsedSeconds = Duration.between(sessionInitResponse.receivedAt(), Instant.now()).getSeconds();
        URI qrLink = smartIdClient.createDynamicContent()
                .withSchemeName("smart-id-demo")
                .withDeviceLinkBase(sessionInitResponse.deviceLinkBase().toString())
                .withDeviceLinkType(DeviceLinkType.QR_CODE)
                .withSessionType(sessionType)
                .withSessionToken(sessionInitResponse.sessionToken())
                .withLang("eng")
                .withElapsedSeconds(elapsedSeconds)
                .withInteractions(interactions)
                .withDigest(digest)
                .buildDeviceLink(sessionInitResponse.sessionSecret());

        return QrCodeGenerator.generateDataUri(qrLink.toString());
    }

    public String getDeviceLink(HttpSession session, SessionType sessionType) {
        logger.debug("Getting device link for session id: {}, session type: {}", session.getId(), sessionType);
        String digest = (String) session.getAttribute("rpChallenge");
        String interactions = (String) session.getAttribute("interactions");
        DeviceLinkSessionResponse sessionInitResponse = (DeviceLinkSessionResponse) session.getAttribute("sessionInitResponse");

        URI deviceLink = smartIdClient.createDynamicContent()
                .withSchemeName("smart-id-demo")
                .withDeviceLinkBase(sessionInitResponse.deviceLinkBase().toString())
                .withDeviceLinkType(DeviceLinkType.WEB_2_APP)
                .withSessionType(sessionType)
                .withSessionToken(sessionInitResponse.sessionToken())
                .withLang("eng")
                .withInitialCallbackUrl("https://localhost:8080/callback")
                .withInteractions(interactions)
                .withDigest(digest)
                .buildDeviceLink(sessionInitResponse.sessionSecret());
        return deviceLink.toString();
    }
}
