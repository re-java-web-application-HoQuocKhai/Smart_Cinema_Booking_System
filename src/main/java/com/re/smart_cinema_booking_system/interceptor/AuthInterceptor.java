package com.re.smart_cinema_booking_system.interceptor;

import com.re.smart_cinema_booking_system.dto.UserSessionDTO;
import com.re.smart_cinema_booking_system.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_USER = "currentUser";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

        HttpSession session = request.getSession(false);
        UserSessionDTO currentUser = null;

        if (session != null) {
            currentUser = (UserSessionDTO) session.getAttribute(SESSION_USER);
        }

        // Not logged in -> redirect to login
        if (currentUser == null) {
            response.sendRedirect("/auth/login");
            return false;
        }

        String uri = request.getRequestURI();

        // ADMIN-only pages
        if (uri.startsWith("/admin") && currentUser.getRole() != UserRole.ADMIN) {
            response.sendRedirect("/error/403");
            return false;
        }

        // STAFF pages (STAFF + ADMIN allowed)
        if (uri.startsWith("/staff")
                && currentUser.getRole() != UserRole.STAFF
                && currentUser.getRole() != UserRole.ADMIN) {
            response.sendRedirect("/error/403");
            return false;
        }

        // CUSTOMER pages (only CUSTOMER)
        if (uri.startsWith("/customer") && currentUser.getRole() != UserRole.CUSTOMER) {
            response.sendRedirect("/error/403");
            return false;
        }

        // Set currentUser as request attribute for controllers/views
        request.setAttribute(SESSION_USER, currentUser);
        return true;
    }
}
