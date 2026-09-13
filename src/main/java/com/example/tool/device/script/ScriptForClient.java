package com.example.tool.device.script;

import com.example.tool.device.exception.BusinessException;

public enum ScriptForClient {

    WINDOWS {
        @Override
        public String render(String server, String mac, Integer deviceId, String deviceToken) {
            return """
                    #Requires -RunAsAdministrator

                    $Server      = "%s"
                    $Mac         = "%s"
                    $DeviceId    = "%d"
                    $DeviceToken = "%s"
                    $Interval    = 60

                    $TaskName    = "Heartbeat-$DeviceId"
                    $InstallPath = "$env:ProgramData\\Heartbeat\\heartbeat-$DeviceId.ps1"

                    function Show-Menu {
                        Write-Host "=========================================="
                        Write-Host "  Device Heartbeat Script Management"
                        Write-Host "  Device ID: $DeviceId"
                        Write-Host "  MAC   : $Mac"
                        Write-Host "=========================================="
                        Write-Host "  1. Set up to start automatically on boot"
                        Write-Host "  2. Uninstall startup program"
                        Write-Host "  3. Exit"
                        Write-Host "=========================================="
                    }

                    function Invoke-Heartbeat {
                        Write-Host "[Heartbeat] Reporting started, interval $Interval seconds"
                        while ($true) {
                            try {
                                Invoke-RestMethod -Uri "$Server/api/heartbeat?mac=$Mac" `
                                    -Method Post `
                                    -Headers @{ "X-Device-Token" = $DeviceToken } | Out-Null
                            } catch {
                                Write-Host "[Warning] Report failed: $($_.Exception.Message)"
                            }
                            Start-Sleep -Seconds $Interval
                        }
                    }

                    function Install-Service {
                        $dir = Split-Path $InstallPath -Parent
                        if (-not (Test-Path $dir)) {
                            New-Item -ItemType Directory -Path $dir -Force | Out-Null
                        }
                        Copy-Item -Path $PSCommandPath -Destination $InstallPath -Force

                        $action = New-ScheduledTaskAction `
                            -Execute "powershell.exe" `
                            -Argument "-WindowStyle Hidden -ExecutionPolicy Bypass -File `"$InstallPath`" run"

                        $trigger = New-ScheduledTaskTrigger -AtStartup

                        $principal = New-ScheduledTaskPrincipal `
                            -UserId "SYSTEM" `
                            -LogonType ServiceAccount `
                            -RunLevel Highest

                        $settings = New-ScheduledTaskSettingsSet `
                            -AllowStartIfOnBatteries `
                            -DontStopIfGoingOnBatteries `
                            -RestartCount 3 `
                            -RestartInterval (New-TimeSpan -Minutes 1) `
                            -ExecutionTimeLimit (New-TimeSpan -Days 0)

                        Register-ScheduledTask `
                            -TaskName $TaskName `
                            -Action $action `
                            -Trigger $trigger `
                            -Principal $principal `
                            -Settings $settings `
                            -Force | Out-Null

                        Start-ScheduledTask -TaskName $TaskName

                        Write-Host "[Success] Installed and started: $TaskName"
                        Write-Host "[View] Get-ScheduledTask -TaskName $TaskName"
                        Write-Host "[Stop] Stop-ScheduledTask -TaskName $TaskName"
                    }

                    function Uninstall-Service {
                        Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
                        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
                        Remove-Item -Path $InstallPath -Force -ErrorAction SilentlyContinue
                        Write-Host "[Success] Uninstalled: $TaskName"
                    }

                    if ($args.Count -gt 0 -and $args[0] -eq "run") {
                        Invoke-Heartbeat
                        exit 0
                    }

                    while ($true) {
                        Show-Menu
                        $choice = Read-Host "Please select [1-3]"
                        switch ($choice) {
                            "1" { Install-Service }
                            "2" { Uninstall-Service }
                            "3" { Write-Host "exit"; exit 0 }
                            default { Write-Host "[Error] Invalid selection" }
                        }
                    }
                    """.formatted(server, mac, deviceId, deviceToken);
        }
    },

    LINUX {
        @Override
        public String render(String server, String mac, Integer deviceId, String deviceToken) {
            return """
                    #!/bin/bash

                    SERVER="%s"
                    MAC="%s"
                    DEVICE_ID="%d"
                    DEVICE_TOKEN="%s"
                    INTERVAL=60

                    SERVICE_NAME="heartbeat-${DEVICE_ID}"
                    INSTALL_PATH="/usr/local/bin/${SERVICE_NAME}.sh"
                    SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

                    print_menu() {
                        echo "=========================================="
                        echo "  Device Heartbeat Script Management"
                        echo "  Device ID: ${DEVICE_ID}"
                        echo "  MAC   : ${MAC}"
                        echo "=========================================="
                        echo "  1. Set up to start automatically on boot"
                        echo "  2. Uninstall startup program"
                        echo "  3. Exit"
                        echo "=========================================="
                    }

                    check_root() {
                        if [ "$EUID" -ne 0 ]; then
                            echo "[Error] Please run this script using sudo"
                            exit 1
                        fi
                    }

                    do_heartbeat() {
                        echo "[Heartbeat] Start toward ${SERVER} Report，interval ${INTERVAL} second"
                        while true; do
                            curl -s -X POST "${SERVER}/api/heartbeat?mac=${MAC}" \\
                                 -H "X-Device-Token: ${DEVICE_TOKEN}" > /dev/null 2>&1
                            sleep "${INTERVAL}"
                        done
                    }

                    install_service() {
                        check_root
                        SCRIPT_PATH=$(readlink -f "$0")
                        cp "${SCRIPT_PATH}" "${INSTALL_PATH}"
                        chmod +x "${INSTALL_PATH}"

                        cat > "${SERVICE_FILE}" <<UNIT
                    [Unit]
                    Description=Heartbeat for device ${DEVICE_ID}
                    After=network-online.target
                    Wants=network-online.target

                    [Service]
                    Type=simple
                    ExecStart=/bin/bash ${INSTALL_PATH} run
                    Restart=always
                    RestartSec=10

                    [Install]
                    WantedBy=multi-user.target
                    UNIT

                        systemctl daemon-reload
                        systemctl enable "${SERVICE_NAME}"
                        systemctl start "${SERVICE_NAME}"

                        echo "[Success] Installed and started: ${SERVICE_NAME}"
                    }

                    uninstall_service() {
                        check_root
                        systemctl stop "${SERVICE_NAME}" 2>/dev/null
                        systemctl disable "${SERVICE_NAME}" 2>/dev/null
                        rm -f "${SERVICE_FILE}"
                        rm -f "${INSTALL_PATH}"
                        systemctl daemon-reload
                        echo "[Success] Uninstalled: ${SERVICE_NAME}"
                    }

                    if [ "$1" = "run" ]; then
                        do_heartbeat
                        exit 0
                    fi

                    while true; do
                        print_menu
                        read -p "Please select [1-3]: " choice
                        case "${choice}" in
                            1) install_service ;;
                            2) uninstall_service ;;
                            3) echo "exit"; exit 0 ;;
                            *) echo "[Error] Invalid selection" ;;
                        esac
                    done
                    """.formatted(server, mac, deviceId, deviceToken);
        }
    };

    public abstract String render(String server, String mac, Integer deviceId, String deviceToken);

    public static ScriptForClient fromString(String os) {
        return switch (os.toLowerCase()) {
            case "windows" -> WINDOWS;
            case "linux" -> LINUX;
            default -> throw new BusinessException("Unsupported OS: " + os);
        };
    }
}