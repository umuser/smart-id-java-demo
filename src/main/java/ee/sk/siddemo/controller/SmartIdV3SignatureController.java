package ee.sk.siddemo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import ee.sk.siddemo.model.SigningResult;
import jakarta.servlet.http.HttpSession;

@Controller
public class SmartIdV3SignatureController {

    private final SmartIdV3SignatureService signatureService;

    public SmartIdV3SignatureController(SmartIdV3SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @GetMapping(value = "/v3/signing-result")
    public ModelAndView toSigningResult(ModelMap model, HttpSession session) {
        SigningResult signingResult = signatureService.handleSignatureResult(session);
        model.addAttribute("signingResult", signingResult);
        model.addAttribute("activeTab", "rp-api-v3");
        return new ModelAndView("v3/signing-result", model);
    }
}
