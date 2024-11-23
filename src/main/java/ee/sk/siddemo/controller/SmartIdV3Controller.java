package ee.sk.siddemo.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.DynamicContent;
import ee.sk.siddemo.model.UserRequest;
import ee.sk.siddemo.services.DynamicContentService;
import ee.sk.siddemo.services.SmartIdV3AuthenticationService;
import jakarta.servlet.http.HttpSession;

@Controller
public class SmartIdV3Controller {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdV3Controller.class);

    private final SmartIdV3AuthenticationService smartIdV3AuthenticationService;
    private final DynamicContentService dynamicContentService;

    public SmartIdV3Controller(SmartIdV3AuthenticationService smartIdV3AuthenticationService,
                               DynamicContentService dynamicContentService) {
        this.smartIdV3AuthenticationService = smartIdV3AuthenticationService;
        this.dynamicContentService = dynamicContentService;
    }

    @GetMapping(value = "/rp-api-v3")
    public ModelAndView userRequestForm(Model model) {
        model.addAttribute("activeTab", "rp-api-v3");
        return new ModelAndView("v3/main", "userRequest", new UserRequest());
    }

    @GetMapping(value = "/v3/start-authentication")
    public ModelAndView sendAuthenticationRequest(ModelMap model, HttpSession session) {
        smartIdV3AuthenticationService.startAuthentication(session);
        model.addAttribute("activeTab", "rp-api-v3");
        return new ModelAndView("v3/authentication", model);
    }

    @GetMapping(value = "/v3/check-authentication-status")
    @ResponseBody
    public Map<String, Object> getPageContent(HttpSession session) {
        String sessionStatus = (String) session.getAttribute("session_status");
        if ("ERROR".equals(sessionStatus)) {
            logger.error("Error occurred while fetching session status");
            throw new SidOperationException(session.getAttribute("session_status_error_message").toString());
        } else if ("COMPLETED".equals(sessionStatus)) {
            logger.debug("Session status: COMPLETED");
            return Map.of("sessionStatus", "COMPLETED");
        } else {
            logger.debug("Update dynamic content");
            DynamicContent dynamicContent = dynamicContentService.getDynamicContent(session);
            return Map.of("dynamicLink", dynamicContent.getDynamicLink().toString(), "qrCode", dynamicContent.getQrCode());
        }
    }

    @GetMapping(value = "/v3/authentication-result")
    public ModelAndView getAuthenticationResult(ModelMap model, HttpSession session) {
        smartIdV3AuthenticationService.authenticate(session);
        return new ModelAndView("v3/authentication-result", model);
    }
}
