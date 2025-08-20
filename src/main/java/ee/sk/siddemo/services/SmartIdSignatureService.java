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

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.time.ZonedDateTime;

import org.digidoc4j.DataToSign;
import org.springframework.stereotype.Service;

import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.SigningResult;
import ee.sk.smartid.SignatureResponse;
import ee.sk.smartid.SignatureValueValidator;
import ee.sk.smartid.SignatureValueValidatorImpl;
import jakarta.servlet.http.HttpSession;

@Service
public class SmartIdSignatureService {

    private final FileService fileService;

    public SmartIdSignatureService(FileService fileService) {
        this.fileService = fileService;
    }

    public SigningResult handleSignatureResult(HttpSession session) {
        var signatureResponse = (SignatureResponse) session.getAttribute("signatureResponse");
        var dataToSign = (DataToSign) session.getAttribute("dataToSign");
        if (signatureResponse == null) {
            throw new SidOperationException("No signature response found in session");
        }
        SignatureValueValidator validator = SignatureValueValidatorImpl.getInstance();
        validator.validate(
                signatureResponse.getSignatureValue(),
                dataToSign.getDataToSign(),
                signatureResponse.getCertificate(),
                signatureResponse.getRsaSsaPssParameters());

        validateSignatureValue(signatureResponse, dataToSign);

        return SigningResult.newBuilder()
                .withResult("Signing successful")
                .withValid(true)
                .withTimestamp(java.util.Date.from(ZonedDateTime.now().toInstant()))
                .withContainerFilePath("N/A – container not created in demo")
                .build();
    }

    private static void validateSignatureValue(SignatureResponse signatureResponse, DataToSign dataToSign) {
        try {
            var params = new PSSParameterSpec(
                    signatureResponse.getHashAlgorithm().getAlgorithmName(),
                    signatureResponse.getMaskGenAlgorithm().getMgfName(),
                    new MGF1ParameterSpec(signatureResponse.getMaskHashAlgorithm().getAlgorithmName()),
                    signatureResponse.getSaltLength(),
                    signatureResponse.getTrailerField().getPssSpecValue()
            );
            var signature = Signature.getInstance(signatureResponse.getSignatureAlgorithm().getAlgorithmName());
            signature.setParameter(params);
            signature.initVerify(signatureResponse.getCertificate().getPublicKey());
            signature.update(dataToSign.getDataToSign());
            signature.verify(signatureResponse.getSignatureValue());
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new SidOperationException("Unable to construct signature instance", e);
        } catch (InvalidKeyException | SignatureException e) {
            throw new SidOperationException("Unable to validate signature", e);
        }
    }
}
