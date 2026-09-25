# Device Manager (Beta)

> Self-hosted device monitoring, Wake-on-LAN and SSH remote operations console

[English](README.md) · [中文](README.zh-CN.md) · [MIT License](LICENSE)

---

## Overview

**Device Manager** is a lightweight, self-hosted console for managing a handful of machines — home labs, small teams or classrooms. See whether devices are online, wake them over LAN when they are off, run scripts remotely over SSH, and optionally power them off on a schedule. The backend is Spring Boot + SQLite (single file, no external service) and the frontend is a zero-build Vue 3 SPA, so it runs comfortably on a resource-constrained box.

## Features

| Module | Description |
| --- | --- |
| 🖥️ Devices | MAC / IP / name, IPv4 and IPv6 |
| 📡 Monitoring | `PING` (system ping) or `HEARTBEAT` (device reports in), statuses `ONLINE / OFFLINE / PROBE / UNKNOWN`, with configurable interval, timeout and offline tolerance |
| ⚡ Wake-on-LAN | Magic packets over IPv4 broadcast / IPv6 multicast; after waking the device enters `PROBE` and is marked offline if it never comes up |
| 🔌 Remote shutdown | Over SSH with passwordless sudo (`sudo -n`) or a sudo password (`sudo -S`); works for Bash and PowerShell targets |
| 📜 Script tasks | **One script can target many devices**; triggers: `MANUAL` (run from the UI only), `ONCE`, `CRON`, `ON_BOOT`; on success optionally shut down (never / immediately / after a delay); one log per target device |
| 🧭 Script checker | Built-in, frontend-only syntax check for Bash / PowerShell. The editor is powered by **Monaco** (the editor engine behind VS Code, vendored locally – offline-friendly): syntax highlighting, minimap, code folding, find & replace, bracket matching/colorization; check results appear as inline squiggles plus a problems panel (quotes, brackets, here-docs, `if/fi`… pairing, CRLF) |
| ⬇️ Heartbeat script | Generate and download a Linux (systemd) or Windows (scheduled task) heartbeat agent |
| 🚀 Version update | Periodically compares against GitHub Releases and notifies in the UI; an admin can enable a "direct update" that downloads the release asset and replaces itself (a watchdog script restarts it) |
| 👤 Users & roles | JWT login, `ADMIN` / `USER`, admin user management, self-service username / password change |
| 🌗 UI | Vue 3 + Element Plus + ECharts, light/dark theme, bilingual, 5-second live polling |
| 🔒 Security | SSH key authentication only; host key fingerprint recorded on first connect and verified afterwards; private keys / passphrases / sudo passwords encrypted with AES-256-GCM and never returned by the API |

## Tech stack

- **Backend**: Spring Boot 4.1, Java 21, Spring Web MVC, Spring Data JPA, Spring Security + JJWT, SQLite (xerial JDBC, single connection), Apache MINA SSHD, BouncyCastle, Lombok
- **Frontend**: Vue 3 + Element Plus + ECharts + Monaco (all vendored locally, no Node or build step)
- **Build & deploy**: Maven; GitHub Actions builds the runnable jar and attaches it to a GitHub Release on every `v*` tag

## Quick start

### Run locally

```bash
./mvnw spring-boot:run        # Linux / macOS
mvnw.cmd spring-boot:run      # Windows
```

Open <http://localhost:8080>. On first boot the random admin password is printed to the log (`Password: xxxxx`) — change it right after signing in.

## Configuration

Settings live in `src/main/resources/application.yaml` and can be overridden with environment variables (Spring relaxed binding).

| Setting / Env | Description |
| --- | --- |
| `jwt.secret` / `jwt.expiration` | JWT signing key and lifetime |
| `wol.ipv4.broadcast-address`, `wol.ipv6.multicast-address`, `wol.port` | Magic packet destination |
| `ping.task-time` | Health-check scan interval (seconds) |
| `app.server-url` / `APP_SERVER_URL` | Server URL baked into the device heartbeat script |
| `app.script.master-key-file` / `APP_SCRIPT_MASTER_KEY_FILE` | Master key file path (default `./.master-key`) |
| `APP_MASTER_KEY` | Master key (Base64, 32 bytes); takes precedence over the file |
| `app.script.ssh.connect-timeout-ms` / `command-timeout-ms` | SSH connect / command timeouts |
| `app.script.ssh.output-charset` | Remote output decoding: `AUTO` (default; strict UTF-8, fall back to GB18030) or `UTF-8` / `GBK` / `GB18030` / `windows-1252` |
| `app.script.on-boot-cooldown-seconds` | Cooldown for the same `ON_BOOT` task |
| `app.version` | Current version (injected at build time from `pom.xml`) |
| `app.update.github-repo` | Repository used for version checks, default `evil-qq-intestine/Device-Manager` |
| `app.update.check-interval-ms` | Version check interval in ms, default `21600000` (6 hours) |
| `app.update.asset-prefix` | File name prefix of the release asset, default `device-manager` (`device-manager.jar`) |

> ⚠️ **Back up the master key.** It encrypts the stored private keys / passphrases; lose it and they cannot be decrypted.

## Startup & runtime parameters

**Key environment variables**:

| Variable | Description | Default |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | SQLite path | `jdbc:sqlite:./data.db` |
| `APP_SCRIPT_MASTER_KEY_FILE` | Master key file path | `./.master-key` |
| `APP_MASTER_KEY` | Master key (Base64, 32 bytes), takes precedence over the file | empty |
| `APP_SERVER_URL` | Server URL baked into device heartbeat scripts | `http://10.34.70.66:8080` |
| `JWT_SECRET` | JWT signing key, overrides `jwt.secret` from `application.yaml` | see `jwt.secret` |
| `TZ` | Time zone, e.g. `Asia/Shanghai` | system default |

## Remote shutdown & sudo

On Linux targets, configure passwordless sudo (no password stored on the server):

```
<ssh-user> ALL=(root) NOPASSWD: /sbin/shutdown, /usr/sbin/shutdown, /sbin/poweroff, /usr/sbin/poweroff, /sbin/halt, /usr/sbin/halt
```

Alternatively, store a **sudo password** on the device / task; the server feeds it to `sudo -S` over stdin (it never appears on the command line).
On Windows targets, the SSH user must be an administrator. Windows has no `sudo`, so the sudo password does not apply there — the field is hidden for PowerShell targets.

### SSH key format

SSH connections use **key authentication only**. Use an **OpenSSH-format** private key (generated by `ssh-keygen`); PuTTY `.ppk` keys are not supported. If the key is passphrase-protected, fill in the key passphrase.

## Version detection & update

On startup the app periodically (every 6 hours by default) calls the GitHub Releases API to compare the running version with the latest release, and shows a notice in the top bar when an update is available. The current version is injected at build time (`app.version`, i.e. the `pom.xml` version), so published releases always match their release tag.

An admin can enable "direct update". The app then downloads the release asset (`device-manager.jar`), replaces itself and exits, after which the **watchdog script** starts the new version. Download the watchdog from the same dialog:

```bash
DEVICE_MANAGER_APP=java \
DEVICE_MANAGER_ARGS="-jar /opt/device-manager/device-manager.jar --server.port=8080" \
./device-manager-watchdog.sh
```

> ⚠️ A direct update replaces the running jar and exits the process, so it **must** be supervised (watchdog or systemd) or the service will not come back.
> The switch lives in the `system_config` table (key `update.direct-enabled`), defaults to off and is admin-only.

## API

The backend is a REST API under `/api/**`. Everything except `/api/auth/**`, `/api/heartbeat/**`, `/api/version` and static assets requires authentication, and you can only access devices and tasks you own.

## Project layout

```
src/main/java/com/example/tool
├── device/        # devices, monitoring, WOL, health checks
├── scripttask/    # SSH script tasks, remote shutdown, crypto, scheduler, events
├── user/          # users, JWT, roles
├── system/        # system configuration (key-value table)
├── versionController/  # version check, update and watchdog script
└── config/        # security, async, global exception handling
src/main/resources
├── static/        # zero-build frontend (Vue 3 + Element Plus + ECharts + Monaco, vendored)
├── application.yaml
```

## License

Released under the [MIT License](LICENSE).
