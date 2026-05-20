package com.re.smart_cinema_booking_system.controller;

import com.re.smart_cinema_booking_system.dto.UserSessionDTO;
import com.re.smart_cinema_booking_system.interceptor.AuthInterceptor;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping({"/", "/home"})
    public String home(HttpSession session, Model model) {
        UserSessionDTO currentUser = null;
        if (session != null) {
            currentUser = (UserSessionDTO) session.getAttribute(AuthInterceptor.SESSION_USER);
        }
        model.addAttribute("currentUser", currentUser);
        return "home";
    }
}
