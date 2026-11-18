package com.ai.aiops.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {
}
