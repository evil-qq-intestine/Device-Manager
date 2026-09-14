package com.example.tool.versionController;

public final class WatchdogScript {

    private WatchdogScript() {
    }

    public static String linux() {
        return """
                #!/usr/bin/env bash
                # Device Manager watchdog
                # Keeps the Device Manager process alive and restarts it after a self-update.
                set -u

                APP="${DEVICE_MANAGER_APP:-/opt/device-manager/device-manager}"
                ARGS="${DEVICE_MANAGER_ARGS:-}"
                INTERVAL="${DEVICE_MANAGER_RESTART_INTERVAL:-3}"

                if [ ! -x "$APP" ]; then
                  echo "[watchdog] not executable: $APP" >&2
                  echo "[watchdog] set DEVICE_MANAGER_APP to the full path of the binary" >&2
                  exit 1
                fi

                echo "[watchdog] watching $APP"
                while true; do
                  # shellcheck disable=SC2086
                  "$APP" $ARGS
                  code=$?
                  echo "[watchdog] exited (code=$code), restarting in ${INTERVAL}s"
                  sleep "$INTERVAL"
                done
                """;
    }
}
