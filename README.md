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
| 🚀 Version update | Periodically compares against GitHub Releases and notifies in the UI; Docker deployments get a copy-paste update command, bare-metal deployments can enable a "direct update" that downloads the release asset and replaces itself (a watchdog script restarts it) |
| 👤 Users & roles | JWT login, `ADMIN` / `USER`, admin user management, self-service username / password change |
| 🌗 UI | Vue 3 + Element Plus + ECharts, light/dark theme, bilingual, 5-second live polling |
| 🔒 Security | SSH key authentication only; host key fingerprint recorded on first connect and verified afterwards; private keys / passphrases / sudo passwords encrypted with AES-256-GCM and never returned by the API |

## Tech stack

- **Backend**: Spring Boot 4.1, Java 21, Spring Web MVC, Spring Data JPA, Spring Security + JJWT, SQLite (xerial JDBC, single connection), Apache MINA SSHD, BouncyCastle, Lombok
- **Frontend**: Vue 3 + Element Plus + ECharts + Monaco (all vendored locally, no Node or build step)
- **Build & deploy**: Maven, Docker (multi-arch amd64/arm64), GitHub Actions → GHCR + Docker Hub. GraalVM native images are **no longer built or published** (too fragile); only the JVM image is maintained.

## Quick start

### Run locally

```bash
./mvnw spring-boot:run        # Linux / macOS
mvnw.cmd spring-boot:run      # Windows
```

Open <http://localhost:8080>. On first boot the random admin password is printed to the log (`Password: xxxxx`) — change it right after signing in.

### Docker

```bash
docker build -t device-manager .
docker run -d --name device-manager \
  -p 8080:8080 \
  -v device-manager-data:/app/data \
  device-manager
```

> - Add `--cap-add=NET_RAW` if you need ICMP `ping` inside the container.
> - Use `--network host` if Wake-on-LAN broadcast / multicast must reach your LAN.

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
| `app.update.docker-image` | Image name used to build the Docker update command (pull only; you restart the container yourself) |
| `SPRING_PROFILES_ACTIVE` | Set to `docker` for low-memory tuning (no SQL logs, Tomcat threads = 8, graceful shutdown, …) |

> ⚠️ **Back up the master key.** It encrypts the stored private keys / passphrases; lose it and they cannot be decrypted.

## Startup & runtime parameters

**JVM image memory flags** (`JAVA_OPTS`, defaults set in the Dockerfile, override with `-e JAVA_OPTS=...`):

```
-Xms48m -Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k \
-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError
```

On tighter devices lower `-Xmx` to `192m` or even `128m`.

**Key environment variables**:

| Variable | Description | Default |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Set to `docker` for low-memory tuning (no SQL logs, Tomcat threads = 8, graceful shutdown) | empty |
| `SPRING_DATASOURCE_URL` | SQLite path | `jdbc:sqlite:./data.db` |
| `APP_SCRIPT_MASTER_KEY_FILE` | Master key file path | `./.master-key` |
| `APP_MASTER_KEY` | Master key (Base64, 32 bytes), takes precedence over the file | empty |
| `APP_SERVER_URL` | Server URL baked into device heartbeat scripts | `http://10.34.70.66:8080` |
| `JAVA_OPTS` | JVM flags (JVM image only) | see above |
| `TZ` | Time zone, e.g. `Asia/Shanghai` | container default UTC |

**Docker run notes**:

- Persist data: `-v device-manager-data:/app/data` (SQLite and the master key live there — do not lose it)
- Port: `-p 8080:8080`
- ICMP ping: `NET_RAW` is in Docker's default capability set, usually nothing extra is needed; if you `--cap-drop=ALL` or enable `no-new-privileges`, add `--cap-add=NET_RAW` (`/bin/ping` is `setcap`-enabled inside the image)
- Wake-on-LAN broadcast / multicast: prefer `--network host`, otherwise magic packets cannot leave the host's subnet
- Full example:

```bash
docker run -d --name device-manager \
  -p 8080:8080 \
  -v device-manager-data:/app/data \
  -e TZ=Asia/Shanghai \
  --network host \
  ghcr.io/evil-qq-intestine/device-manager:latest
```

### Native images are discontinued

> ⚠️ GraalVM native images are **no longer built or published** (dropped in `1.0.5`). Only the JVM image and the runnable jar are maintained. Native builds kept hitting closed-world pitfalls (reflection, dynamic proxies, runtime-registered security providers) that were not worth the maintenance cost. Use `ghcr.io/evil-qq-intestine/device-manager:latest` (or the Docker Hub mirror) instead.
>
> Historical note: native images up to and including `1.0.3` could not use SSH features at all (`Internal server error` — the BouncyCastle provider was not registered at native-image build time). `1.0.4` fixed it, but native was dropped right after.

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

On startup the app periodically (every 6 hours by default) calls the GitHub Releases API to compare the running version with the latest release, and shows a notice in the top bar when an update is available. The current version is injected at build time (`app.version`, i.e. the `pom.xml` version), so published images always match their release tag.

**Docker deployments**: open the notice and copy the update command (images are published to both GHCR and Docker Hub, the latter defaulting to `emmmm666/device-manager` and overridable via the `DOCKERHUB_IMAGE` repository variable):

```bash
docker pull ghcr.io/evil-qq-intestine/device-manager:1.0.8
```

The image name comes from `app.update.docker-image`; after pulling, restart the container your usual way (e.g. `docker compose up -d` or `docker restart <name>`).

**Bare-metal deployments (jar)**: an admin can enable "direct update". The app then downloads the release asset (`device-manager.jar`), replaces itself and exits, after which the **watchdog script** starts the new version. Download the watchdog from the same dialog:

```bash
DEVICE_MANAGER_APP=java \
DEVICE_MANAGER_ARGS="-jar /opt/device-manager/device-manager.jar --server.port=8080" \
./device-manager-watchdog.sh
```

> ⚠️ A direct update replaces the running executable / jar and exits the process, so it **must** be supervised (watchdog or systemd) or the service will not come back.
> The switch lives in the `system_config` table (key `update.direct-enabled`), defaults to off and is admin-only.
> Docker deployments do not support direct update (replacing files inside the container is pointless) — use the command above.

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
└── application-docker.yaml
```

## License

Released under the [MIT License](LICENSE).
