package com.lycoris.repository;

import com.lycoris.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface UserRepository extends JpaRepository<User,Integer> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findById(Integer id);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsById(Integer id);
}
