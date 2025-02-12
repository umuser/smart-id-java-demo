package ee.sk.siddemo.controller;

import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;
import org.digidoc4j.Signature;
import org.springframework.stereotype.Service;

import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.SigningResult;
import ee.sk.siddemo.services.FileService;
import ee.sk.smartid.v3.SingatureResponse;
import jakarta.servlet.http.HttpSession;

@Service
public class SmartIdV3SignatureService {

    private final FileService fileService;

    public SmartIdV3SignatureService(FileService fileService) {
        this.fileService = fileService;
    }

    public SigningResult handleSignatureResult(HttpSession session) {
        var signatureResponse = (SingatureResponse) session.getAttribute("signing_response");
        if (signatureResponse == null) {
            throw new SidOperationException("No signature response found in session");
        }

        byte[] signatureValue = signatureResponse.getSignatureValue();
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
}
