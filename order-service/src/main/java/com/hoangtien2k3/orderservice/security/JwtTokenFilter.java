package com.hoangtien2k3.orderservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class JwtTokenFilter {

    private JwtTokenFilter() {
        // utility class
    }

    public static String getTokenFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            return jwtToken.getToken().getTokenValue();
        }
        return null;
    }
}
