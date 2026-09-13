package com.example.tool.scripttask.controller;

import com.example.tool.scripttask.response.ScriptTaskLogDetailResponse;
import com.example.tool.scripttask.service.ScriptTaskService;
import com.example.tool.user.util.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ScriptTaskLogController {

    private final ScriptTaskService scriptTaskService;

    public ScriptTaskLogController(ScriptTaskService scriptTaskService) {
        this.scriptTaskService = scriptTaskService;
    }

    @GetMapping("/script-task-log/{logId}")
    public ScriptTaskLogDetailResponse detail(@PathVariable Long logId,
                                              @AuthenticationPrincipal CustomUserDetails user) {
        return scriptTaskService.logDetail(logId, user.getUserId());
    }
}
