package com.ai.aiops.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.Role;

public interface RoleRepository extends JpaRepository<Role, UUID> {
}
