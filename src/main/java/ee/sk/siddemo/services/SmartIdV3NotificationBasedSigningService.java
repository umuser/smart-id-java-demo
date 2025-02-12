package ee.sk.siddemo.services;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.SignatureBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ee.sk.siddemo.exception.FileUploadException;
import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.UserDocumentNumberRequest;
import ee.sk.siddemo.model.UserRequest;
import ee.sk.smartid.CertificateParser;
import ee.sk.smartid.HashType;
import ee.sk.smartid.exception.useraction.SessionTimeoutException;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.v3.CertificateLevel;
import ee.sk.smartid.v3.SignableData;
import ee.sk.smartid.v3.SignatureResponseMapper;
import ee.sk.smartid.v3.SmartIdClient;
import ee.sk.smartid.v3.rest.dao.NotificationInteraction;
import ee.sk.smartid.v3.rest.dao.NotificationSignatureSessionResponse;
import ee.sk.smartid.v3.rest.dao.SessionCertificate;
import ee.sk.smartid.v3.rest.dao.SessionStatus;
import jakarta.servlet.http.HttpSession;

@Service
public class SmartIdV3NotificationBasedSigningService {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdV3NotificationBasedSigningService.class);

    private final SmartIdClient smartIdClientV3;
    private final SmartIdV3SessionsStatusService sessionStatusService;
    private final SmartIdV3NotificationBasedCertificateChoiceService notificationCertificateChoiceService;


    public SmartIdV3NotificationBasedSigningService(SmartIdClient smartIdClientV3,
                                                    SmartIdV3SessionsStatusService sessionStatusService,
                                                    SmartIdV3NotificationBasedCertificateChoiceService notificationCertificateChoiceService) {
        this.smartIdClientV3 = smartIdClientV3;
        this.sessionStatusService = sessionStatusService;
        this.notificationCertificateChoiceService = notificationCertificateChoiceService;
    }

    public String startSigningWithDocumentNumber(HttpSession session, UserDocumentNumberRequest userDocumentNumberRequest) {
        notificationCertificateChoiceService.startCertificateChoice(session, userDocumentNumberRequest);
        var signableData = toSignableData(userDocumentNumberRequest.getFile(), session);

        NotificationSignatureSessionResponse sessionResponse = smartIdClientV3.createNotificationSignature()
                .withCertificateLevel(CertificateLevel.QUALIFIED)
                .withSignableData(signableData)
                .withDocumentNumber(userDocumentNumberRequest.getDocumentNumber())
                .withAllowedInteractionsOrder(List.of(NotificationInteraction.verificationCodeChoice("Sign the document!")))
                .initSignatureSession();

        session.setAttribute("sessionID", sessionResponse.getSessionID());
        sessionStatusService.startPolling(session, sessionResponse.getSessionID());

        return sessionResponse.getVc().getValue();
    }

    public String startSigningWithPersonCode(HttpSession session, UserRequest userRequest) {
        notificationCertificateChoiceService.startCertificateChoice(session, userRequest);
        var signableData = toSignableData(userRequest.getFile(), session);

        var semanticsIdentifier = new SemanticsIdentifier(SemanticsIdentifier.IdentityType.PNO, userRequest.getCountry(), userRequest.getNationalIdentityNumber());

        NotificationSignatureSessionResponse sessionResponse = smartIdClientV3.createNotificationSignature()
                .withCertificateLevel(CertificateLevel.QUALIFIED)
                .withSignableData(signableData)
                .withSemanticsIdentifier(semanticsIdentifier)
                .withAllowedInteractionsOrder(List.of(NotificationInteraction.verificationCodeChoice("Sign the document!")))
                .initSignatureSession();

        session.setAttribute("sessionID", sessionResponse.getSessionID());
        sessionStatusService.startPolling(session, sessionResponse.getSessionID());

        return sessionResponse.getVc().getValue();
    }

    public boolean checkSignatureStatus(HttpSession session) {
        Optional<SessionStatus> sessionStatus = sessionStatusService.getSessionsStatus(session.getId());
        return sessionStatus
                .map(status -> {
                    if (status.getState().equals("COMPLETE")) {
                        saveValidateResponse(session, status);
                        session.setAttribute("session_status", "COMPLETED");
                        logger.debug("Mobile device IP address: {}", status.getDeviceIpAddress());
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    private SignableData toSignableData(MultipartFile uploadedFile, HttpSession session) {
        Container container = toContainer(uploadedFile);
        X509Certificate certificate = getCertificate(session);
        DataToSign dataToSign = toDataToSign(container, certificate);
        saveSigningAttributes(session, dataToSign, container);

        SignableData signableData = new SignableData(dataToSign.getDataToSign());
        signableData.setHashType(HashType.SHA256); // has to match SignatureDigestAlgorithm used in dataToSign
        return signableData;
    }

    private Container toContainer(MultipartFile userDocumentNumberRequest) {
        DataFile uploadedFile = getUploadedDataFile(userDocumentNumberRequest);

        var configuration = new Configuration(Configuration.Mode.TEST);
        return ContainerBuilder.aContainer()
                .withConfiguration(configuration)
                .withDataFile(uploadedFile)
                .build();
    }

    private X509Certificate getCertificate(HttpSession httpSession) {
        Optional<SessionStatus> certSessionStatus;
        do {
            certSessionStatus = sessionStatusService.getSessionsStatus(httpSession.getId());
        } while (certSessionStatus.isEmpty());

        SessionCertificate sessionCertificate = certSessionStatus.get().getCert();
        return CertificateParser.parseX509Certificate(sessionCertificate.getValue());
    }

    private DataFile getUploadedDataFile(MultipartFile uploadedFile) {
        try {
            return new DataFile(uploadedFile.getInputStream(), uploadedFile.getOriginalFilename(), uploadedFile.getContentType());
        } catch (IOException e) {
            throw new FileUploadException(e.getCause());
        }
    }

    private static DataToSign toDataToSign(Container container, X509Certificate certificate) {
        return SignatureBuilder.aSignature(container)
                .withSigningCertificate(certificate)
                .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
                .buildDataToSign();
    }

    private static void saveSigningAttributes(HttpSession session,
                                              DataToSign dataToSign,
                                              Container container) {
        session.setAttribute("dataToSign", dataToSign);
        session.setAttribute("container", container);
    }

    private static void saveValidateResponse(HttpSession session, SessionStatus status) {
        try {
            var signatureResponse = SignatureResponseMapper.from(status, CertificateLevel.QUALIFIED.name());
            session.setAttribute("signing_response", signatureResponse);
        } catch (SessionTimeoutException ex) {
            throw new SidOperationException(ex.getMessage());
        }
    }
}
