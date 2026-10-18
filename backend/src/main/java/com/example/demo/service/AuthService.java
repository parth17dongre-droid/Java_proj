package com.example.demo.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.SignupRequest;
import com.example.demo.model.Users;
import com.example.demo.repository.UserRepository;
import com.example.demo.utils.CookieUtil;
import com.evcharge.wallet.service.WalletService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;
    private final WalletService walletService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       CookieUtil cookieUtil,
                       WalletService walletService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.cookieUtil = cookieUtil;
        this.walletService = walletService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest req, HttpServletResponse response) {
        if (userRepository.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }

        Users user = new Users();
        user.setName(req.name());
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setHashedPassword(passwordEncoder.encode(req.password()));

        AuthResponse result = issueTokens(user, response);
        walletService.createWallet(user.getId());   // same transaction as the user insert
        return result;
    }

    @Transactional
    public AuthResponse login(LoginRequest req, HttpServletResponse response) {
        Users user = userRepository.findByUsername(req.username())
                .orElseThrow(AuthService::invalidCredentials);

        if (!passwordEncoder.matches(req.password(), user.getHashedPassword())) {
            throw invalidCredentials();
        }

        return issueTokens(user, response);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = extractTokenFromCookie(request);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }

        Claims claims;
        try {
            // use parseIgnoreExpiry so logout works even if access token just expired
            claims = jwtService.parseIgnoreExpiry(token);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        Users user = userRepository.findByUsername(claims.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        user.setRefreshToken(null);
        userRepository.save(user);
        cookieUtil.clearAccessTokenCookie(response);
    }

    @Transactional
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String expiredToken = extractTokenFromCookie(request);
        if (expiredToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No token found");
        }

        // Parse ignoring expiry to get the username from the expired token
        Claims claims;
        try {
            claims = jwtService.parseIgnoreExpiry(expiredToken);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        Users user = userRepository.findByUsername(claims.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        // Check that the refresh token in the DB is still valid
        String storedRefresh = user.getRefreshToken();
        if (storedRefresh == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session ended, please log in again");
        }

        try {
            jwtService.parse(storedRefresh); // validates signature and expiry
        } catch (JwtException e) {
            user.setRefreshToken(null);
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired, please log in again");
        }

        // Refresh token is valid — issue a new access token cookie
        String newAccess = jwtService.generateAccessToken(user);
        cookieUtil.setAccessTokenCookie(response, newAccess);
        return AuthResponse.ok();
    }

    private AuthResponse issueTokens(Users user, HttpServletResponse response) {
        user = userRepository.save(user);

        String access = jwtService.generateAccessToken(user);
        String refresh = jwtService.generateRefreshToken(user);

        user.setRefreshToken(refresh);
        userRepository.save(user);

        cookieUtil.setAccessTokenCookie(response, access);
        return AuthResponse.ok();
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if ("access_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
}