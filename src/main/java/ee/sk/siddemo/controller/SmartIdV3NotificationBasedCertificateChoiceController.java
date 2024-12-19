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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.DynamicContent;
import ee.sk.siddemo.model.UserRequest;
import ee.sk.siddemo.services.DynamicContentService;
import ee.sk.siddemo.services.SmartIdV3NotificationBasedCertificateChoiceService;
import ee.sk.siddemo.services.SmartIdV3SessionsStatusService;
import ee.sk.smartid.v3.SessionType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Controller
public class SmartIdV3NotificationBasedCertificateChoiceController {

    private final Logger logger = LoggerFactory.getLogger(SmartIdV3NotificationBasedCertificateChoiceController.class);

    private final SmartIdV3NotificationBasedCertificateChoiceService smartIdV3NotificationBasedCertificateChoiceService;
    private final SmartIdV3SessionsStatusService smartIdV3SessionsStatusService;
    private final DynamicContentService dynamicContentService;

    public SmartIdV3NotificationBasedCertificateChoiceController(SmartIdV3NotificationBasedCertificateChoiceService smartIdV3NotificationBasedCertificateChoiceService,
                                                                 SmartIdV3SessionsStatusService smartIdV3SessionsStatusService,
                                                                 DynamicContentService dynamicContentService) {
        this.smartIdV3NotificationBasedCertificateChoiceService = smartIdV3NotificationBasedCertificateChoiceService;
        this.smartIdV3SessionsStatusService = smartIdV3SessionsStatusService;
        this.dynamicContentService = dynamicContentService;
    }


    @PostMapping(value = "/v3/notification-based/start-certificate-choice-with-person-code")
    public ModelAndView startNotificationCertificateChoiceWithPersonCode(ModelMap model,
                                                                         HttpServletRequest request,
                                                                         @ModelAttribute("certUserRequest") @Valid UserRequest certUserRequest,
                                                                         BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ModelAndView("v3/main", "certUserRequest", certUserRequest);
        }
        HttpSession session = resetSession(request);
        smartIdV3NotificationBasedCertificateChoiceService.startCertificateChoice(session, certUserRequest);
        model.addAttribute("activeTab", "rp-api-v3");
        return new ModelAndView("v3/notification-based/certificate-choice", model);
    }

    @GetMapping(value = "/v3/notification-based/check-certificate-choice-status")
    @ResponseBody
    public ResponseEntity<Map<String, String>> checkCertificateChoiceStatus(HttpSession session) {
        boolean checkCompleted;
        try {
            checkCompleted = smartIdV3NotificationBasedCertificateChoiceService.checkCertificateChoiceStatus(session);
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
        DynamicContent dynamicContent = dynamicContentService.getDynamicContent(session, SessionType.CERTIFICATE_CHOICE);
        Map<String, String> content = new HashMap<>();
        content.put("dynamicLink", dynamicContent.getDynamicLink().toString());
        content.put("qrCode", dynamicContent.getQrCode());
        return ResponseEntity.ok(content);
    }

    @GetMapping(value = "/v3/notification-based/certificate-choice-result")
    public ModelAndView getAuthenticationResult(ModelMap model, HttpSession session) {
        String documentNumber = (String) session.getAttribute("documentNumber");
        String distinguishedName = (String) session.getAttribute("distinguishedName");
        model.addAttribute("documentNumber", documentNumber);
        model.addAttribute("distinguishedName", distinguishedName);
        model.addAttribute("activeTab", "rp-api-v3");
        return new ModelAndView("v3/notification-based/certificate-choice-result", model);
    }

    @GetMapping(value = "/v3/notification-based/cancel-certificate-choice")
    public ModelAndView cancelAuthentication(ModelMap model, HttpServletRequest request) {
        resetSession(request);
        model.addAttribute("activeTab", "rp-api-v3");
        return new ModelAndView("v3/main", model);
    }

    private HttpSession resetSession(HttpServletRequest request) {
        HttpSession session = request.getSession();
        if (session != null) {
            smartIdV3SessionsStatusService.cancelPolling(session.getId());
            session.invalidate();
        }
        // Create a new session
        session = request.getSession(true);
        return session;
    }
}
