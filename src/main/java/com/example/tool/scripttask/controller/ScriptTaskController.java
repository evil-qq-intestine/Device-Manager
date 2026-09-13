package com.example.tool.scripttask.controller;

import com.example.tool.scripttask.entity.TriggeredBy;
import com.example.tool.scripttask.request.ScriptTaskRequest;
import com.example.tool.scripttask.request.TestSshRequest;
import com.example.tool.scripttask.response.PageResponse;
import com.example.tool.scripttask.response.ScriptTaskLogResponse;
import com.example.tool.scripttask.response.ScriptTaskResponse;
import com.example.tool.scripttask.service.ScriptTaskService;
import com.example.tool.user.util.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ScriptTaskController {

    private final ScriptTaskService scriptTaskService;

    public ScriptTaskController(ScriptTaskService scriptTaskService) {
        this.scriptTaskService = scriptTaskService;
    }

    @PostMapping("/script-task")
    @ResponseStatus(HttpStatus.CREATED)
    public ScriptTaskResponse create(@Valid @RequestBody ScriptTaskRequest request,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        return scriptTaskService.create(user.getUserId(), request);
    }

    @GetMapping("/script-task")
    public List<ScriptTaskResponse> list(@AuthenticationPrincipal CustomUserDetails user) {
        return scriptTaskService.list(user.getUserId());
    }

    @GetMapping("/script-task/{taskId}")
    public ScriptTaskResponse get(@PathVariable Long taskId,
                                  @AuthenticationPrincipal CustomUserDetails user) {
        return scriptTaskService.get(taskId, user.getUserId());
    }

    @PutMapping("/script-task/{taskId}")
    public ScriptTaskResponse update(@PathVariable Long taskId,
                                     @Valid @RequestBody ScriptTaskRequest request,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        return scriptTaskService.update(taskId, user.getUserId(), request);
    }

    @DeleteMapping("/script-task/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long taskId,
                       @AuthenticationPrincipal CustomUserDetails user) {
        scriptTaskService.delete(taskId, user.getUserId());
    }

    @PatchMapping("/script-task/{taskId}/toggle")
    public ScriptTaskResponse toggle(@PathVariable Long taskId,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        return scriptTaskService.toggle(taskId, user.getUserId());
    }

    @PostMapping("/script-task/{taskId}/execute")
    public Map<String, Object> execute(@PathVariable Long taskId,
                                       @AuthenticationPrincipal CustomUserDetails user) {
        scriptTaskService.get(taskId, user.getUserId());
        List<Long> logIds = scriptTaskService.submit(taskId, TriggeredBy.MANUAL);
        return Map.of("logIds", logIds);
    }

    @PostMapping("/script-task/{taskId}/shutdown")
    public Map<String, Object> shutdown(@PathVariable Long taskId,
                                        @AuthenticationPrincipal CustomUserDetails user) {
        return Map.of("results", scriptTaskService.shutdownNow(taskId, user.getUserId()));
    }

    @GetMapping("/script-task/{taskId}/logs")
    public PageResponse<ScriptTaskLogResponse> logs(@PathVariable Long taskId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @AuthenticationPrincipal CustomUserDetails user) {
        return scriptTaskService.logs(taskId, user.getUserId(), page, size);
    }

    @PostMapping("/script-task/test-ssh")
    public Map<String, Object> testSsh(@Valid @RequestBody TestSshRequest request) {
        return scriptTaskService.testSsh(request);
    }
}
