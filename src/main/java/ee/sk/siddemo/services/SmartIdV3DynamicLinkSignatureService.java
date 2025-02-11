package ee.sk.siddemo.services;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureBuilder;
import org.digidoc4j.SignatureProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ee.sk.siddemo.exception.FileUploadException;
import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.SigningResult;
import ee.sk.siddemo.model.UserDocumentNumberRequest;
import ee.sk.siddemo.model.UserRequest;
import ee.sk.smartid.CertificateParser;
import ee.sk.smartid.exception.useraction.SessionTimeoutException;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.v3.CertificateLevel;
import ee.sk.smartid.v3.SignableData;
import ee.sk.smartid.v3.SignatureResponseMapper;
import ee.sk.smartid.v3.SingatureResponse;
import ee.sk.smartid.v3.SmartIdClient;
import ee.sk.smartid.v3.rest.dao.DynamicLinkInteraction;
import ee.sk.smartid.v3.rest.dao.DynamicLinkSessionResponse;
import ee.sk.smartid.v3.rest.dao.SessionCertificate;
import ee.sk.smartid.v3.rest.dao.SessionStatus;
import jakarta.servlet.http.HttpSession;

@Service
public class SmartIdV3DynamicLinkSignatureService {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdV3DynamicLinkSignatureService.class);

    private final SmartIdV3NotificationBasedCertificateChoiceService certificateChoiceService;
    private final SmartIdV3SessionsStatusService sessionsStatusService;
    private final SmartIdClient smartIdClientV3;
    private final FileService fileService;

    public SmartIdV3DynamicLinkSignatureService(SmartIdV3NotificationBasedCertificateChoiceService certificateChoiceService,
                                                SmartIdV3SessionsStatusService sessionsStatusService,
                                                SmartIdClient smartIdClientV3, FileService fileService) {
        this.certificateChoiceService = certificateChoiceService;
        this.sessionsStatusService = sessionsStatusService;
        this.smartIdClientV3 = smartIdClientV3;
        this.fileService = fileService;
    }

    public void startSigningWithDocumentNumber(HttpSession session, UserDocumentNumberRequest userDocumentNumberRequest) {
        certificateChoiceService.startCertificateChoice(session, userDocumentNumberRequest);
        var signableData = toSignableData(userDocumentNumberRequest.getFile(), getX509Certificate(session), session);

        DynamicLinkSessionResponse sessionResponse = smartIdClientV3.createDynamicLinkSignature()
                .withCertificateLevel(CertificateLevel.QUALIFIED)
                .withSignableData(signableData)
                .withAllowedInteractionsOrder(List.of(DynamicLinkInteraction.displayTextAndPIN("Sign the document!")))
                .withDocumentNumber(userDocumentNumberRequest.getDocumentNumber())
                .initSignatureSession();
        Instant responseReceivedTime = Instant.now();

        saveResponseAttributes(session, sessionResponse, responseReceivedTime);

        sessionsStatusService.startPolling(session, sessionResponse.getSessionID());
    }

    public void startSigningWithPersonCode(HttpSession session, UserRequest userRequest) {
        certificateChoiceService.startCertificateChoice(session, userRequest);
        var signableData = toSignableData(userRequest.getFile(), getX509Certificate(session), session);

        var semanticsIdentifier = new SemanticsIdentifier(SemanticsIdentifier.IdentityType.PNO, userRequest.getCountry(), userRequest.getNationalIdentityNumber());

        DynamicLinkSessionResponse sessionResponse = smartIdClientV3.createDynamicLinkSignature()
                .withCertificateLevel(CertificateLevel.QUALIFIED)
                .withSignableData(signableData)
                .withSemanticsIdentifier(semanticsIdentifier)
                .withAllowedInteractionsOrder(List.of(DynamicLinkInteraction.displayTextAndPIN("Sign the document!")))
                .initSignatureSession();
        Instant responseReceivedTime = Instant.now();

        saveResponseAttributes(session, sessionResponse, responseReceivedTime);

        sessionsStatusService.startPolling(session, sessionResponse.getSessionID());
    }

    public boolean checkSignatureStatus(HttpSession session) {
        Optional<SessionStatus> sessionStatus = sessionsStatusService.getSessionsStatus(session.getId());
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

    public SigningResult handleSignatureResult(HttpSession session) {
        var dynamicLinkSignatureResponse = (SingatureResponse) session.getAttribute("signing_response");
        if (dynamicLinkSignatureResponse == null) {
            throw new SidOperationException("No signature response found in session");
        }

        byte[] signatureValue = dynamicLinkSignatureResponse.getSignatureValue();
        DataToSign dataToSign = (DataToSign) session.getAttribute("dataToSign");
        Signature signature = dataToSign.finalize(signatureValue);

        Container container = (Container) session.getAttribute("container");
        container.addSignature(signature);

        String filePath = fileService.createPath();
        container.saveAsFile(filePath);
        return SigningResult.newBuilder()
                .withResult("Signing successful")
                .withValid(signature.validateSignature().isValid())
                .withTimestamp(signature.getTimeStampCreationTime())
                .withContainerFilePath(filePath)
                .build();
    }

    private SignableData toSignableData(MultipartFile file, X509Certificate signingCertificate, HttpSession session) {
        Container container = toContainer(file);
        DataToSign dataToSign = toDataToSign(container, signingCertificate);
        saveSigningAttributes(session, container, dataToSign);
        return new SignableData(dataToSign.getDataToSign());
    }

    private Container toContainer(MultipartFile userDocumentNumberRequest) {
        DataFile uploadedFile = getUploadedDataFile(userDocumentNumberRequest);

        var configuration = new Configuration(Configuration.Mode.TEST);
        return ContainerBuilder.aContainer()
                .withConfiguration(configuration)
                .withDataFile(uploadedFile)
                .build();
    }

    private DataFile getUploadedDataFile(MultipartFile uploadedFile) {
        try {
            return new DataFile(uploadedFile.getInputStream(), uploadedFile.getOriginalFilename(), uploadedFile.getContentType());
        } catch (IOException e) {
            throw new FileUploadException(e.getCause());
        }
    }

    private X509Certificate getX509Certificate(HttpSession session) {
        Optional<SessionStatus> certSessionStatus;
        do {
            certSessionStatus = sessionsStatusService.getSessionsStatus(session.getId());
        } while (certSessionStatus.isEmpty());

        SessionCertificate sessionCertificate = certSessionStatus.get().getCert();
        return CertificateParser.parseX509Certificate(sessionCertificate.getValue());
    }

    private static void saveSigningAttributes(HttpSession session, Container container, DataToSign dataToSign) {
        session.setAttribute("container", container);
        session.setAttribute("dataToSign", dataToSign);
    }

    private static void saveResponseAttributes(HttpSession session, DynamicLinkSessionResponse sessionResponse, Instant responseReceivedTime) {
        session.setAttribute("sessionSecret", sessionResponse.getSessionSecret());
        session.setAttribute("sessionToken", sessionResponse.getSessionToken());
        session.setAttribute("sessionID", sessionResponse.getSessionID());
        session.setAttribute("responseReceivedTime", responseReceivedTime);
    }

    private static DataToSign toDataToSign(Container container, X509Certificate certificate) {
        return SignatureBuilder.aSignature(container)
                .withSigningCertificate(certificate)
                .withSignatureDigestAlgorithm(DigestAlgorithm.SHA512)
                .withSignatureProfile(SignatureProfile.LT)
                .buildDataToSign();
    }

    private static void saveValidateResponse(HttpSession session, SessionStatus status) {
        try {
            var dynamicLinkSignatureResponse = SignatureResponseMapper.from(status, CertificateLevel.QUALIFIED.name());
            session.setAttribute("signing_response", dynamicLinkSignatureResponse);
        } catch (SessionTimeoutException ex) {
            throw new SidOperationException(ex.getMessage());
        }
    }
}
