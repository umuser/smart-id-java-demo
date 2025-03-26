package ee.sk.siddemo.controller;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.UserDocumentNumberRequest;
import ee.sk.siddemo.model.UserRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class SmartIdV3NotificationBasedAuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdV3NotificationBasedAuthenticationController.class);

    private final SmartIdV3NotificationBasedAuthenticationService smartIdV3NotificationBasedAuthenticationService;

    public SmartIdV3NotificationBasedAuthenticationController(SmartIdV3NotificationBasedAuthenticationService smartIdV3NotificationBasedAuthenticationService) {
        this.smartIdV3NotificationBasedAuthenticationService = smartIdV3NotificationBasedAuthenticationService;
    }

    @PostMapping("v3/notification-based/start-authentication-with-person-code")
    public ModelAndView startAuthenticationWithPersonCode(@ModelAttribute("userRequest") UserRequest userRequest,
                                                          BindingResult bindingResult,
                                                          ModelMap model,
                                                          RedirectAttributes redirectAttributes,
                                                          HttpSession session) {
        model.addAttribute("activeTab", "rp-api-v3");
        if (bindingResult.hasErrors()) {
            logger.debug("Validation errors: {}", bindingResult.getAllErrors());
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.userRequest", bindingResult);
            redirectAttributes.addFlashAttribute("userRequest", userRequest);
            return new ModelAndView("redirect:/rp-api-v3");
        }

        String verificationCode = smartIdV3NotificationBasedAuthenticationService.startAuthenticationWithPersonCode(session, userRequest);
        model.addAttribute("verificationCode", verificationCode);
        return new ModelAndView("v3/notification-based/authentication", model);
    }

    @PostMapping("v3/notification-based/start-authentication-with-document-number")
    public ModelAndView startAuthenticationWithDocumentNumber(@ModelAttribute("userDocumentNumberRequest") UserDocumentNumberRequest userDocumentNumberRequest,
                                                              BindingResult bindingResult,
                                                              ModelMap model,
                                                              RedirectAttributes redirectAttributes,
                                                              HttpSession session) {
        model.addAttribute("activeTab", "rp-api-v3");
        if (bindingResult.hasErrors()) {
            logger.debug("Validation errors: {}", bindingResult.getAllErrors());
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.userDocumentNumberRequest", bindingResult);
            redirectAttributes.addFlashAttribute("userDocumentNumberRequest", userDocumentNumberRequest);
            return new ModelAndView("redirect:/rp-api-v3");
        }

        String verificationCode = smartIdV3NotificationBasedAuthenticationService.startAuthenticationWithDocumentNumber(session, userDocumentNumberRequest);
        model.addAttribute("verificationCode", verificationCode);
        return new ModelAndView("v3/notification-based/authentication", model);
    }

    @GetMapping(value = "v3/notification-based/check-authentication-status")
    @ResponseBody
    public ResponseEntity<?> checkNotificationSigningStatus(HttpSession session) {
        try {
            smartIdV3NotificationBasedAuthenticationService.checkAuthenticationStatus(session);
            return ResponseEntity.ok().build();
        } catch (SidOperationException ex) {
            logger.error("Error occurred while checking authentication status", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }
}
