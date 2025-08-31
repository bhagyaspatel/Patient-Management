package com.pm.service;

import com.pm.dto.LoginRequestDto;
import com.pm.model.User;
import com.pm.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory .annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {
    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    // password in request -> encode -> compare with db pwd
    public Optional<String> authenticate(LoginRequestDto loginRequestDto){
        return userService
                .findByEmail(loginRequestDto.getEmail())
                .filter(u -> passwordEncoder.matches(loginRequestDto.getPassword(), u.getPassword()))
                .map(u -> jwtUtil.generateToken(u.getEmail(), u.getRole()));
    }

    public boolean validateToken(String token) {
        try {
            jwtUtil.validateToken(token);
            return true;
        } catch (JwtException e){
            return false;
        }
    }
}
