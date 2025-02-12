package ee.sk.siddemo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import ee.sk.siddemo.services.SmartIdV3SessionsStatusService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class SmartIdV3SessionController {

    private final SmartIdV3SessionsStatusService smartIdV3SessionsStatusService;

    public SmartIdV3SessionController(SmartIdV3SessionsStatusService smartIdV3SessionsStatusService) {
        this.smartIdV3SessionsStatusService = smartIdV3SessionsStatusService;
    }

    @GetMapping(value = "/v3/cancel-session")
    public ModelAndView cancelAuthentication(ModelMap model, HttpServletRequest request) {
        resetSession(request);
        return new ModelAndView("v3/main", model);
    }

    @GetMapping(value = "/v3/session-error")
    public ModelAndView handleSigningSessionError(@RequestParam(value = "errorMessage", required = false) String errorMessage,
                                                  HttpServletRequest request,
                                                  ModelMap model) {
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("activeTab", "rp-api-v3");
        resetSession(request);
        return new ModelAndView("sidOperationError", model);
    }

    private void resetSession(HttpServletRequest request) {
        HttpSession session = request.getSession();
        if (session != null) {
            smartIdV3SessionsStatusService.cancelPolling(session.getId());
            session.invalidate();
        }
        // Create a new session
        request.getSession(true);
    }
}
