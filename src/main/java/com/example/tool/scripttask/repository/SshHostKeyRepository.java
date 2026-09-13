package com.example.tool.scripttask.repository;

import com.example.tool.scripttask.entity.SshHostKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SshHostKeyRepository extends JpaRepository<SshHostKey, Long> {

    Optional<SshHostKey> findByHostAndPort(String host, Integer port);
}
