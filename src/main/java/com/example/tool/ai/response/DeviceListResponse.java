package com.example.tool.ai.response;

import com.example.tool.device.entity.Device;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
public class DeviceListResponse {
    private String deviceName;
    private Integer deviceId;

    public static DeviceListResponse from(Device device){
        DeviceListResponse deviceListResponse = new DeviceListResponse();
        deviceListResponse.setDeviceName(device.getDeviceName());
        deviceListResponse.setDeviceId(device.getDeviceId());
        return deviceListResponse;
    }

    public static List<DeviceListResponse> from(List<Device> devices) {
        return devices.stream().map(DeviceListResponse::from).collect(Collectors.toList());
    }
}
