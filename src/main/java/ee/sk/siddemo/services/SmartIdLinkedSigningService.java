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

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.SignatureBuilder;
import org.digidoc4j.SignatureProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ee.sk.siddemo.exception.FileUploadException;
import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.LinkedSigningRequest;
import ee.sk.smartid.CertificateChoiceResponse;
import ee.sk.smartid.CertificateChoiceResponseValidator;
import ee.sk.smartid.CertificateLevel;
import ee.sk.smartid.SignableData;
import ee.sk.smartid.SignatureResponseValidator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.common.devicelink.interactions.DeviceLinkInteraction;
import ee.sk.smartid.exception.useraccount.CertificateLevelMismatchException;
import ee.sk.smartid.exception.useraction.SessionTimeoutException;
import ee.sk.smartid.exception.useraction.UserRefusedException;
import ee.sk.smartid.rest.dao.DeviceLinkSessionResponse;
import ee.sk.smartid.rest.dao.LinkedSignatureSessionResponse;
import ee.sk.smartid.rest.dao.SessionStatus;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Service
public class SmartIdLinkedSigningService {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdDeviceLinkCertificateChoiceService.class);
    private static final Map<String, String> OID_MAP = Map.of("2.5.4.5", "serialNumber", "2.5.4.42", "givenName", "2.5.4.4", "surname");

    private final SmartIdClient smartIdClient;
    private final SmartIdSessionsStatusService smartIdSessionsStatusService;
    private final CertificateChoiceResponseValidator certificateChoiceResponseValidator;
    private final SignatureResponseValidator signatureResponseValidator;

    public SmartIdLinkedSigningService(SmartIdClient smartIdClient,
                                       SmartIdSessionsStatusService smartIdSessionsStatusService,
                                       CertificateChoiceResponseValidator certificateChoiceResponseValidator,
                                       SignatureResponseValidator signatureResponseValidator) {
        this.smartIdClient = smartIdClient;
        this.smartIdSessionsStatusService = smartIdSessionsStatusService;
        this.certificateChoiceResponseValidator = certificateChoiceResponseValidator;
        this.signatureResponseValidator = signatureResponseValidator;
    }

    public void startSigning(HttpSession session, @Valid LinkedSigningRequest linkedSigningRequest) {
        CertificateLevel certificateLevel = CertificateLevel.ADVANCED;
        DeviceLinkSessionResponse response = this.smartIdClient.createDeviceLinkCertificateRequest()
                .withCertificateLevel(certificateLevel)
                .initCertificateChoice();

        session.setAttribute("sessionInitResponse", response);
        session.setAttribute("certificateLevel", certificateLevel);
        session.setAttribute("signableFile", getUploadedDataFile(linkedSigningRequest.getFile()));
        smartIdSessionsStatusService.startPolling(session, response.sessionID());
    }

    public boolean checkCertificateChoiceStatus(HttpSession session) {
        Optional<SessionStatus> sessionStatus = smartIdSessionsStatusService.getSessionsStatus(session.getId());
        return sessionStatus
                .map(ss -> {
                    if (ss.getState().equals("COMPLETE")) {
                        saveValidateCertificateChoiceResponse(session, ss);
                        session.setAttribute("session_status", "COMPLETED");
                        logger.debug("Mobile device IP address: {}", ss.getDeviceIpAddress());
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    public void continueSigning(HttpSession session) {
        CertificateChoiceResponse certChoiceResponse = (CertificateChoiceResponse) session.getAttribute("certificateChoiceResponse");
        DeviceLinkSessionResponse deviceLinkSessionResponse = (DeviceLinkSessionResponse) session.getAttribute("sessionInitResponse");
        DataFile dataFile = (DataFile) session.getAttribute("signableFile");
        CertificateLevel requestCertificateLevel = (CertificateLevel) session.getAttribute("certificateLevel");

        SignableData signableData = toSignableData(dataFile, certChoiceResponse.getCertificate(), session);
        LinkedSignatureSessionResponse linkedSignatureSessionResponse = smartIdClient.createLinkedNotificationSignature()
                .withDocumentNumber(certChoiceResponse.getDocumentNumber())
                .withLinkedSessionID(deviceLinkSessionResponse.sessionID())
                .withCertificateLevel(requestCertificateLevel)
                .withSignableData(signableData)
                .withInteractions(List.of(DeviceLinkInteraction.displayTextAndPin("Sign it!")))
                .initSignatureSession();
        smartIdSessionsStatusService.startPolling(session, linkedSignatureSessionResponse.sessionID());
    }

    public boolean checkSignatureSessionStatus(HttpSession session) {
        Optional<SessionStatus> sessionStatus = smartIdSessionsStatusService.getSessionsStatus(session.getId());
        return sessionStatus
                .map(ss -> {
                    if (ss.getState().equals("COMPLETE")) {
                        saveValidateSignatureResponse(session, ss);
                        session.setAttribute("session_status", "COMPLETED");
                        logger.debug("Mobile device IP address: {}", ss.getDeviceIpAddress());
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    private void saveValidateCertificateChoiceResponse(HttpSession session, SessionStatus sessionStatus) {
        try {
            CertificateLevel requestCertificateLevel = (CertificateLevel) session.getAttribute("certificateLevel");
            CertificateChoiceResponse certChoiceResponse = certificateChoiceResponseValidator.validate(sessionStatus, requestCertificateLevel);
            X509Certificate certificate = certChoiceResponse.getCertificate();
            String distinguishedName = certificate.getSubjectX500Principal().getName("RFC1779", OID_MAP);
            session.setAttribute("distinguishedName", distinguishedName);
            session.setAttribute("certificateChoiceResponse", certChoiceResponse);
        } catch (SessionTimeoutException | UserRefusedException ex) {
            throw new SidOperationException(ex.getMessage());
        }
    }

    private SignableData toSignableData(DataFile file, X509Certificate certificate, HttpSession session) {
        Container container = toContainer(file);
        DataToSign dataToSign = toDataToSign(container, certificate);
        saveSigningAttributes(session, container, dataToSign);
        return new SignableData(dataToSign.getDataToSign());
    }

    private Container toContainer(DataFile file) {
        var configuration = new Configuration(Configuration.Mode.TEST);
        return ContainerBuilder.aContainer()
                .withConfiguration(configuration)
                .withDataFile(file)
                .build();
    }

    private static DataToSign toDataToSign(Container container, X509Certificate certificate) {
        return SignatureBuilder.aSignature(container)
                .withSigningCertificate(certificate)
                .withSignatureDigestAlgorithm(DigestAlgorithm.SHA512)
                .withSignatureProfile(SignatureProfile.LT)
                .buildDataToSign();
    }

    private static void saveSigningAttributes(HttpSession session, Container container, DataToSign dataToSign) {
        session.setAttribute("container", container);
        session.setAttribute("dataToSign", dataToSign);
    }

    private DataFile getUploadedDataFile(MultipartFile uploadedFile) {
        try {
            return new DataFile(uploadedFile.getInputStream(), uploadedFile.getOriginalFilename(), uploadedFile.getContentType());
        } catch (IOException e) {
            throw new FileUploadException(e.getCause());
        }
    }

    private void saveValidateSignatureResponse(HttpSession session, SessionStatus status) {
        try {
            CertificateLevel requestedCertificateLevel = (CertificateLevel) session.getAttribute("certificateLevel");
            var dynamicLinkSignatureResponse = signatureResponseValidator.validate(status, requestedCertificateLevel);
            session.setAttribute("signatureResponse", dynamicLinkSignatureResponse);
        } catch (SessionTimeoutException | UserRefusedException | CertificateLevelMismatchException ex) {
            throw new SidOperationException(ex.getMessage());
        }
    }
}
