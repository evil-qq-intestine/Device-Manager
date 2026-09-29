package com.example.tool.security.mcp;

import com.example.tool.security.mcp.entity.McpJwtToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface McpTokenRepository extends JpaRepository<McpJwtToken, Integer> {
    Optional<McpJwtToken> findByTokenHash(String tokenHash);
}
