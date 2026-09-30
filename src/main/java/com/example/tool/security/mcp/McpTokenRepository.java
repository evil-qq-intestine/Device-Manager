package com.example.tool.security.mcp;

import com.example.tool.security.mcp.entity.McpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface McpTokenRepository extends JpaRepository<McpToken, Integer> {
    Optional<McpToken> findByTokenHash(String tokenHash);

    Optional<McpToken> findByIdAndUserId(Integer id, Integer userId);

    List<McpToken> findByUserId(Integer userId);
}
