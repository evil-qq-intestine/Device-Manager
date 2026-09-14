package com.example.tool.system.service;

import com.example.tool.system.entity.SystemConfig;
import com.example.tool.system.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemConfigService {

    public static final String DIRECT_UPDATE_ENABLED = "update.direct-enabled";

    @Autowired
    private SystemConfigRepository repository;

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        return repository.findById(key).map(SystemConfig::getConfigValue).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        return repository.findById(key)
                .map(config -> Boolean.parseBoolean(config.getConfigValue()))
                .orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value) {
        repository.save(new SystemConfig(key, value));
    }

    @Transactional
    public void setBoolean(String key, boolean value) {
        set(key, Boolean.toString(value));
    }
}
