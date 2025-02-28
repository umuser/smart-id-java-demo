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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.UserDocumentNumberRequest;
import ee.sk.siddemo.model.UserRequest;
import ee.sk.siddemo.services.SmartIdV3NotificationBasedSigningService;
import jakarta.servlet.http.HttpSession;

@Controller
public class SmartIdV3NotificationBasedSigningController {

    private final Logger logger = LoggerFactory.getLogger(SmartIdV3NotificationBasedSigningController.class);

    private final SmartIdV3NotificationBasedSigningService smartIdV3NotificationBasedSigningService;

    public SmartIdV3NotificationBasedSigningController(SmartIdV3NotificationBasedSigningService smartIdV3NotificationBasedSigningService) {
        this.smartIdV3NotificationBasedSigningService = smartIdV3NotificationBasedSigningService;
    }

    @PostMapping(value = "v3/notification-based/start-signing-with-document-number")
    public ModelAndView sendNotificationSigningRequestWithDocumentNumber(@ModelAttribute("userDocumentNumberRequest") UserDocumentNumberRequest userDocumentNumberRequest,
                                                                         BindingResult bindingResult,
                                                                         ModelMap model,
                                                                         RedirectAttributes redirectAttributes,
                                                                         HttpSession session) {
        model.addAttribute("activeTab", "rp-api-v3");
        if (isFileMissing(userDocumentNumberRequest.getFile())) {
            bindingResult.rejectValue("file", "error.file", "Please select a file to upload");
        }

        if (bindingResult.hasErrors()) {
            logger.debug("Validation errors: {}", bindingResult.getAllErrors());
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.userDocumentNumberRequest", bindingResult);
            redirectAttributes.addFlashAttribute("userDocumentNumberRequest", userDocumentNumberRequest);
            return new ModelAndView("redirect:/rp-api-v3");
        }
        String verificationCode = smartIdV3NotificationBasedSigningService.startSigningWithDocumentNumber(session, userDocumentNumberRequest);
        model.addAttribute("verificationCode", verificationCode);
        return new ModelAndView("v3/notification-based/signing", model);
    }

    @PostMapping(value = "v3/notification-based/start-signing-with-person-code")
    public ModelAndView sendNotificationSigningRequestWithPersonCode(@ModelAttribute("userRequest") UserRequest userRequest,
                                                                     BindingResult bindingResult,
                                                                     ModelMap model,
                                                                     RedirectAttributes redirectAttributes,
                                                                     HttpSession session) {
        model.addAttribute("activeTab", "rp-api-v3");
        if (isFileMissing(userRequest.getFile())) {
            bindingResult.rejectValue("file", "error.file", "Please select a file to upload");
        }

        if (bindingResult.hasErrors()) {
            logger.debug("Validation errors: {}", bindingResult.getAllErrors());
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.userRequest", bindingResult);
            redirectAttributes.addFlashAttribute("userRequest", userRequest);
            return new ModelAndView("redirect:/rp-api-v3");
        }
        String verificationCode = smartIdV3NotificationBasedSigningService.startSigningWithPersonCode(session, userRequest);
        model.addAttribute("verificationCode", verificationCode);
        return new ModelAndView("v3/notification-based/signing", model);
    }

    @GetMapping(value = "v3/notification-based/check-signing-status")
    @ResponseBody
    public ResponseEntity<?> checkNotificationSigningStatus(HttpSession session) {
        try {
            smartIdV3NotificationBasedSigningService.checkSignatureStatus(session);
            return ResponseEntity.ok().build();
        } catch (SidOperationException ex) {
            logger.error("Error occurred while checking authentication status", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    private static boolean isFileMissing(MultipartFile userRequest) {
        return userRequest == null || userRequest.getOriginalFilename() == null || userRequest.isEmpty();
    }
}
