package ee.sk.siddemo.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.DynamicContent;
import ee.sk.siddemo.model.UserDocumentNumberRequest;
import ee.sk.siddemo.model.UserRequest;
import ee.sk.siddemo.services.DynamicContentService;
import ee.sk.siddemo.services.SmartIdV3DynamicLinkAuthenticationService;
import ee.sk.smartid.v3.SessionType;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;


@Controller
public class SmartIdV3DynamicLinkAuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdV3DynamicLinkAuthenticationController.class);

    private final SmartIdV3DynamicLinkAuthenticationService smartIdV3DynamicLinkAuthenticationService;
    private final DynamicContentService dynamicContentService;

    public SmartIdV3DynamicLinkAuthenticationController(SmartIdV3DynamicLinkAuthenticationService smartIdV3DynamicLinkAuthenticationService,
                                                        DynamicContentService dynamicContentService) {
        this.smartIdV3DynamicLinkAuthenticationService = smartIdV3DynamicLinkAuthenticationService;
        this.dynamicContentService = dynamicContentService;
    }

    @GetMapping(value = "/v3/dynamic-link/start-authentication")
    public ModelAndView startAuthentication(ModelMap model, HttpSession session) {
        smartIdV3DynamicLinkAuthenticationService.startAuthentication(session);
        model.addAttribute("activeTab", "rp-api-v3");
        return new ModelAndView("v3/dynamic-link/authentication", model);
    }

    @PostMapping(value = "/v3/dynamic-link/start-authentication-with-person-code")
    public ModelAndView startAuthentication(@ModelAttribute("userRequest") @Valid UserRequest userRequest,
                                            ModelMap model,
                                            HttpSession session,
                                            BindingResult bindingResult) {
        model.addAttribute("activeTab", "rp-api-v3");
        if (bindingResult.hasErrors()) {
            return new ModelAndView("v3/main", "userRequest", userRequest);
        }
        smartIdV3DynamicLinkAuthenticationService.startAuthentication(session, userRequest);
        return new ModelAndView("v3/dynamic-link/authentication", model);
    }

    @PostMapping(value = "/v3/dynamic-link/start-authentication-with-document-number")
    public ModelAndView startAuthentication(@ModelAttribute("userDocumentNumberRequest") @Valid UserDocumentNumberRequest userDocumentNumberRequest,
                                            ModelMap model,
                                            HttpSession session,
                                            BindingResult bindingResult) {
        model.addAttribute("activeTab", "rp-api-v3");
        if (bindingResult.hasErrors()) {
            return new ModelAndView("v3/main", "userDocumentNumberRequest", userDocumentNumberRequest);
        }
        smartIdV3DynamicLinkAuthenticationService.startAuthentication(session, userDocumentNumberRequest);
        return new ModelAndView("v3/dynamic-link/authentication", model);
    }


    @GetMapping(value = "/v3/dynamic-link/check-authentication-status")
    public ResponseEntity<Map<String, String>> checkAuthenticationStatus(HttpSession session) {
        boolean checkCompleted;
        try {
            checkCompleted = smartIdV3DynamicLinkAuthenticationService.checkAuthenticationStatus(session);
        } catch (SidOperationException ex) {
            logger.error("Error occurred while checking authentication status", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("errorMessage", ex.getMessage()));
        }
        if (checkCompleted) {
            logger.debug("Session status: COMPLETED");
            return ResponseEntity.ok(Map.of("sessionStatus", "COMPLETED"));
        }

        // Generate QR-code and dynamic link
        logger.debug("Generate dynamic content for session {}", session.getId());
        DynamicContent dynamicContent = dynamicContentService.getDynamicContent(session, SessionType.AUTHENTICATION);
        Map<String, String> content = new HashMap<>();
        content.put("dynamicLink", dynamicContent.getDynamicLink().toString());
        content.put("qrCode", dynamicContent.getQrCode());
        return ResponseEntity.ok(content);
    }
}
