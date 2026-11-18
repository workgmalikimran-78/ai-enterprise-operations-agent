package com.ai.aiops.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.UserRole;
import com.ai.aiops.domain.UserRoleId;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}
