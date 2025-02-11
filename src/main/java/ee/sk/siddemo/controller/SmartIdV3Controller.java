package ee.sk.siddemo.controller;

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

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import ee.sk.siddemo.model.UserDocumentNumberRequest;
import ee.sk.siddemo.model.UserRequest;
import ee.sk.siddemo.services.SmartIdV3AuthenticationService;
import ee.sk.siddemo.services.SmartIdV3SessionsStatusService;
import ee.sk.smartid.AuthenticationIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Controller
public class SmartIdV3Controller {

    private final SmartIdV3AuthenticationService smartIdV3AuthenticationService;
    private final SmartIdV3SessionsStatusService smartIdV3SessionsStatusService;

    public SmartIdV3Controller(SmartIdV3AuthenticationService smartIdV3AuthenticationService,
                               SmartIdV3SessionsStatusService smartIdV3SessionsStatusService) {
        this.smartIdV3AuthenticationService = smartIdV3AuthenticationService;
        this.smartIdV3SessionsStatusService = smartIdV3SessionsStatusService;
    }

    @ModelAttribute("userRequest")
    public UserRequest userRequest() {
        return new UserRequest();
    }

    @ModelAttribute("userDocumentNumberRequest")
    public UserDocumentNumberRequest userDocumentNumberRequest() {
        return new UserDocumentNumberRequest();
    }

    @GetMapping(value = "/rp-api-v3")
    public String viewRpApiV3Tab(Model model) {
        model.addAttribute("activeTab", "rp-api-v3");
        return "v3/main";
    }

    @GetMapping(value = "/v3/start-authentication")
    public ModelAndView startAuthentication(ModelMap model, HttpServletRequest request) {
        HttpSession session = resetSession(request);
        smartIdV3AuthenticationService.startAuthentication(session);
        model.addAttribute("activeTab", "rp-api-v3");
        return new ModelAndView("v3/authentication", model);
    }

    @PostMapping(value = "/v3/start-authentication-with-person-code")
    public ModelAndView startAuthentication(ModelMap model, HttpServletRequest request,
                                            @ModelAttribute("userRequest") @Valid UserRequest userRequest,
                                            BindingResult bindingResult) {
        model.addAttribute("activeTab", "rp-api-v3");
        if (bindingResult.hasErrors()) {
            return new ModelAndView("v3/main", "userRequest", userRequest);
        }
        HttpSession session = resetSession(request);
        smartIdV3AuthenticationService.startAuthentication(session, userRequest);
        return new ModelAndView("v3/authentication", model);
    }

    @PostMapping(value = "/v3/start-authentication-with-document-number")
    public ModelAndView startAuthentication(ModelMap model, HttpServletRequest request,
                                            @ModelAttribute("userDocumentNumberRequest") @Valid UserDocumentNumberRequest userDocumentNumberRequest,
                                            BindingResult bindingResult) {
        model.addAttribute("activeTab", "rp-api-v3");
        if (bindingResult.hasErrors()) {
            return new ModelAndView("v3/main", "userDocumentNumberRequest", userDocumentNumberRequest);
        }
        HttpSession session = resetSession(request);
        smartIdV3AuthenticationService.startAuthentication(session, userDocumentNumberRequest);
        return new ModelAndView("v3/authentication", model);
    }

    @GetMapping(value = "/v3/authentication-result")
    public ModelAndView getAuthenticationResult(ModelMap model, HttpSession session) {
        AuthenticationIdentity authenticationIdentity = smartIdV3AuthenticationService.authenticate(session);
        model.addAttribute("person", authenticationIdentity);
        return new ModelAndView("v3/authentication-result", model);
    }

    @GetMapping(value = "/v3/cancel-authentication")
    public ModelAndView cancelAuthentication(ModelMap model, HttpServletRequest request) {
        resetSession(request);
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
