package com.re.smart_cinema_booking_system.controller;

import com.re.smart_cinema_booking_system.dto.LoginRequest;
import com.re.smart_cinema_booking_system.dto.RegisterRequest;
import com.re.smart_cinema_booking_system.dto.UserSessionDTO;
import com.re.smart_cinema_booking_system.exception.BusinessException;
import com.re.smart_cinema_booking_system.interceptor.AuthInterceptor;
import com.re.smart_cinema_booking_system.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ---- LOGIN ----

    @GetMapping("/login")
    public String loginPage(Model model, HttpSession session) {
        // If already logged in, redirect to home
        if (session.getAttribute(AuthInterceptor.SESSION_USER) != null) {
            return "redirect:/home";
        }
        model.addAttribute("loginRequest", new LoginRequest());
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginRequest loginRequest,
                        BindingResult result,
                        HttpSession session,
                        Model model) {
        if (result.hasErrors()) {
            return "auth/login";
        }
        try {
            UserSessionDTO userSession = authService.login(loginRequest);
            session.setAttribute(AuthInterceptor.SESSION_USER, userSession);
            return "redirect:/home";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/login";
        }
    }

    // ---- REGISTER ----

    @GetMapping("/register")
    public String registerPage(Model model, HttpSession session) {
        if (session.getAttribute(AuthInterceptor.SESSION_USER) != null) {
            return "redirect:/home";
        }
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest registerRequest,
                           BindingResult result,
                           HttpSession session,
                           Model model) {
        if (result.hasErrors()) {
            return "auth/register";
        }
        try {
            UserSessionDTO userSession = authService.register(registerRequest);
            session.setAttribute(AuthInterceptor.SESSION_USER, userSession);
            return "redirect:/home";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register";
        }
    }

    // ---- LOGOUT ----

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/auth/login";
    }
}
