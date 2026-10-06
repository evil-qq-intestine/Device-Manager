package com.example.tool.ai.mcp;

import com.example.tool.ai.response.DeviceListResponse;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.security.mcp.McpScopeGuard;
import com.example.tool.security.mcp.entity.McpTokenPermission;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class McpDevice {

    @Autowired
    private DeviceRepository deviceRepository;

    @McpTool(name = "get_user_device",description = "Get a list of all devices currently owned by the user")
    public List<DeviceListResponse> getDevice() {
        McpScopeGuard.require(McpTokenPermission.DEVICE_READ);
        Integer userId = McpScopeGuard.ownerId();
        return DeviceListResponse.from(deviceRepository.findByUserId(userId));
    }

    //@McpTool(name = "get")

}
