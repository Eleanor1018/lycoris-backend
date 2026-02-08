package com.lycoris.service;

import com.lycoris.entity.User;
import com.lycoris.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User login(String usernameOrEmail, String rawPassword) {
        Optional<User> userOpt =
                usernameOrEmail.contains("@")
                        ? userRepository.findByEmail(usernameOrEmail)
                        : userRepository.findByUsername(usernameOrEmail);

        if (userOpt.isEmpty()) return null;

        User user = userOpt.get();
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            return null;
        }
        return user;
    }

    @Transactional
    public User register(String username, String nickname, String email, String rawPassword) {
        //calibrate
        if (username == null || username.isEmpty()) return null;
        if (email == null || email.isEmpty()) return null;
        if (rawPassword == null || rawPassword.length() < 4) return null;

        //uniqueness
        if(userRepository.existsByUsername(username)) {return null;}
        if(userRepository.existsByEmail(email)) {return null;}

        //create account
        User user = new User();
        user.setUsername(username.trim());
        user.setNickname(nickname == null || nickname.isBlank() ? username.trim() : nickname.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole("USER");


        return userRepository.save(user);
    }

    public Optional<User> findById(Integer id) {
        return userRepository.findById(id);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public boolean changePassword(User user, String oldPassword, String newPassword) {
        if (user == null) return false;
        if (newPassword == null || newPassword.length() < 4) return false;
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) return false;
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return true;
    }

}
