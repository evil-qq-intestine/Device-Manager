# 设备管家 · Device Manager

> 自托管的设备在线监控、网络唤醒与 SSH 远程运维控制台
> Self-hosted device monitoring, Wake-on-LAN and SSH remote operations console

[中文](#中文文档) · [English](#english-documentation) · [MIT License](#许可证--license)

---

## 中文文档

### 简介

**设备管家（Device Manager）** 是一个轻量、自托管的设备管理控制台，面向家庭 / 小团队 / 实验室里的多台机器：随时看到设备是否在线，离线时一键网络唤醒，需要时通过 SSH 远程执行脚本，甚至按计划自动关机。后端基于 Spring Boot + SQLite（单文件、零外部依赖），前端是零构建的 Vue 3 单页应用，可直接跑在资源紧凑的小主机上。

### 功能特性

| 模块 | 说明 |
| --- | --- |
| 🖥️ 设备管理 | 维护 MAC / IP / 名称，支持 IPv4 与 IPv6 |
| 📡 在线监控 | `PING`（系统 ping）或 `HEARTBEAT`（设备定时上报）两种模式，状态 `ONLINE / OFFLINE / PROBE / UNKNOWN`，可配置检测间隔、超时与离线容忍 |
| ⚡ 网络唤醒 | Wake-on-LAN 魔术包，IPv4 广播 / IPv6 组播；唤醒后进入 `PROBE` 等待上线，超时判定为离线 |
| 🔌 远程关机 | 通过 SSH 关机，支持免密 sudo（`sudo -n`）或 sudo 密码（`sudo -S`），bash / PowerShell 目标均可 |
| 📜 脚本任务 | **一个脚本可挂多台设备**；触发方式：`ONCE`（定时一次）/ `CRON`（周期）/ `ON_BOOT`（设备上线时）；脚本成功后可选择不关机 / 立即关机 / 延迟关机；每个目标设备独立记录执行日志 |
| ⬇️ 心跳脚本 | 一键生成并下载 Linux（systemd）或 Windows（计划任务）心跳上报脚本 |
| 👤 用户与权限 | JWT 登录，`ADMIN` / `USER` 角色，管理员可管理用户，所有人可改自己的用户名与密码 |
| 🌗 界面 | Vue 3 + Element Plus + ECharts，明暗主题切换、中英双语、5 秒实时轮询 |
| 🔒 安全 | SSH 仅支持密钥认证；目标主机指纹首次记录、之后校验；私钥 / 口令 / sudo 密码均以 AES-256-GCM 加密落库，接口永不返回 |

### 技术栈

- **后端**：Spring Boot 4.1、Java 21、Spring Web MVC、Spring Data JPA、Spring Security + JJWT、SQLite（xerial JDBC，单连接）、Apache MINA SSHD、BouncyCastle、Lombok
- **前端**：Vue 3 + Element Plus + ECharts + dayjs（全部本地 vendor，无需 Node / 构建步骤）
- **构建与部署**：Maven、Docker（多架构 amd64/arm64）、GitHub Actions → GHCR、GraalVM Native Image（实验性）

### 快速开始

#### 本地运行

```bash
./mvnw spring-boot:run        # Linux / macOS
mvnw.cmd spring-boot:run      # Windows
```

打开 <http://localhost:8080>。首次启动会在日志里打印管理员随机密码（`Password: xxxxx`），登录后请立即修改。

#### Docker

```bash
docker build -t device-manager .
docker run -d --name device-manager \
  -p 8080:8080 \
  -v device-manager-data:/app/data \
  device-manager
```

> - 容器内需要 `ping`（ICMP）时加 `--cap-add=NET_RAW`；
> - 需要 Wake-on-LAN 广播 / 组播出宿主机网段时建议 `--network host`。

### 配置

主要配置项在 `src/main/resources/application.yaml`，均可用环境变量覆盖（Spring 松绑定）。

| 配置 / 环境变量 | 说明 |
| --- | --- |
| `jwt.secret` / `jwt.expiration` | JWT 签名密钥与有效期 |
| `wol.ipv4.broadcast-address`、`wol.ipv6.multicast-address`、`wol.port` | 魔术包目标 |
| `ping.task-time` | 健康检查扫描间隔（秒） |
| `app.server-url` / `APP_SERVER_URL` | 下发给设备的心跳脚本里使用的服务端地址 |
| `app.script.master-key-file` / `APP_SCRIPT_MASTER_KEY_FILE` | 主密钥文件路径（默认 `./.master-key`） |
| `APP_MASTER_KEY` | 主密钥（Base64，32 字节）。设置后优先于密钥文件 |
| `app.script.ssh.connect-timeout-ms` / `command-timeout-ms` | SSH 连接 / 命令超时 |
| `app.script.on-boot-cooldown-seconds` | `ON_BOOT` 同一任务冷却时间 |
| `SPRING_PROFILES_ACTIVE` | 设为 `docker` 启用小内存设备优化（关 SQL 日志、Tomcat 线程 8、优雅停机等） |

> ⚠️ **主密钥务必妥善备份**：它用于加解密存储的私钥 / 口令，丢失后已保存的密钥将无法解密。

### 启动与运行参数

**JVM 镜像内存参数**（`JAVA_OPTS`，Dockerfile 已有默认值，可用 `-e JAVA_OPTS=...` 覆盖）：

```
-Xms48m -Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k \
-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError
```

资源更紧张的设备可把 `-Xmx` 调到 `192m` 甚至 `128m`。

**native 镜像内存参数**：native 可执行文件用 `-XX:` 前缀传运行时参数：

```bash
docker run -d ... device-manager-native -XX:MaxHeapSize=192m
```

native 默认最大堆约为物理内存的 80%，小内存设备建议显式限制。

**关键环境变量**：

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 设为 `docker` 启用小内存优化（关 SQL 日志、Tomcat 线程 8、优雅停机） | 空 |
| `SPRING_DATASOURCE_URL` | SQLite 路径 | `jdbc:sqlite:./data.db` |
| `APP_SCRIPT_MASTER_KEY_FILE` | 主密钥文件路径 | `./.master-key` |
| `APP_MASTER_KEY` | 主密钥（Base64，32 字节），优先于密钥文件 | 空 |
| `APP_SERVER_URL` | 下发心跳脚本时使用的服务端地址 | `http://10.34.70.66:8080` |
| `JAVA_OPTS` | JVM 启动参数（仅 JVM 镜像） | 见上 |
| `TZ` | 时区，如 `Asia/Shanghai` | 容器默认 UTC |

**Docker 运行要点**：

- 数据持久化：`-v device-manager-data:/app/data`（SQLite 与主密钥都在里面，别丢）
- 端口：`-p 8080:8080`
- ICMP ping：Docker 默认能力集已包含 `NET_RAW`，通常无需额外参数；若你显式 `--cap-drop=ALL` 或启用 `no-new-privileges`，需加 `--cap-add=NET_RAW`（镜像内 `/bin/ping` 已通过 `setcap` 授权）
- WOL 广播 / 组播：建议 `--network host`，否则魔术包出不了宿主机网段
- 完整示例：

```bash
docker run -d --name device-manager \
  -p 8080:8080 \
  -v device-manager-data:/app/data \
  -e TZ=Asia/Shanghai \
  --network host \
  ghcr.io/evil-qq-intestine/device-manager:latest
```

#### native 运行期排错（可选，遇到再处理）

GraalVM native 是封闭世界分析，第三方库用到的反射必须提前登记。本项目已处理
`FileSystemProvider`、安全 Provider 与 sqlite 的元数据；如果实际使用时（尤其是 SSH 脚本任务）
报 `MissingReflectionRegistrationError`，用 tracing agent **精确**生成配置，而不是手写猜：

```bash
# 需要 GraalVM（自带 native-image-agent）
# 1) 以 agent 方式启动 JVM 应用
java -agentlib:native-image-agent=config-merge-dir=target/native-config \
     -jar target/device-manager-*.jar
# 2) 在界面上把相关功能点一遍：登录 → 新建并执行脚本任务 → 测试 SSH → 设备关机
# 3) Ctrl-C 停止，配置会写到 target/native-config/
# 4) 拷进源码，重新构建 native
mkdir -p src/main/resources/META-INF/native-image/com.example/device-manager
cp target/native-config/*.json src/main/resources/META-INF/native-image/com.example/device-manager/
./mvnw -Pnative -DskipTests native:compile
```

生成的文件会随源码提交，之后 native 构建会自动读取。没遇到问题就不需要加。

### 远程关机与 sudo

Linux 目标机推荐配置免密 sudo（服务端无需保存密码）：

```
<ssh-user> ALL=(root) NOPASSWD: /sbin/shutdown, /usr/sbin/shutdown, /sbin/poweroff, /usr/sbin/poweroff, /sbin/halt, /usr/sbin/halt
```

若不便改 sudoers，也可在设备 / 任务里填写 **sudo 密码**，服务端会用 `sudo -S` 通过 stdin 传入（不会出现在命令行中）。
Windows 目标则要求 SSH 用户为管理员。

### 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth` | 登录，返回 JWT |
| `GET/POST` | `/api/device` | 设备列表 / 新增 |
| `GET/PUT/DELETE` | `/api/device/{id}` | 设备详情 / 更新 / 删除 |
| `POST` | `/api/device/{id}/wake` | 网络唤醒 |
| `GET/PUT` | `/api/device/{id}/ssh` | 设备级 SSH 配置 |
| `POST` | `/api/device/{id}/shutdown` | 设备级远程关机 |
| `GET` | `/api/device/{id}/heartbeat-script?os=linux\|windows` | 下载心跳脚本 |
| `POST` | `/api/heartbeat?mac=...` | 设备端心跳上报（需 `X-Device-Token`） |
| `GET/POST` | `/api/script-task` | 脚本任务列表 / 新增（可多目标） |
| `PUT/DELETE/PATCH` | `/api/script-task/{id}` `/toggle` | 更新 / 删除 / 启停 |
| `POST` | `/api/script-task/{id}/execute` | 手动执行（返回各目标日志 ID） |
| `POST` | `/api/script-task/{id}/shutdown` | 关闭该任务的所有目标 |
| `GET` | `/api/script-task/{id}/logs` | 执行日志（分页） |
| `POST` | `/api/script-task/test-ssh` | SSH 连通性测试 |
| `GET` | `/api/script-task-log/{logId}` | 单次日志详情 |
| `GET/POST/PUT/DELETE` | `/api/admin...` | 管理员用户管理（仅 `ADMIN`） |

除 `/api/auth/**`、`/api/heartbeat/**`、`/api/version` 与静态资源外，其余接口均需登录，且只能访问自己名下的设备与任务。

### 项目结构

```
src/main/java/com/example/tool
├── device/        # 设备、监控、WOL、健康检查
├── scripttask/    # SSH 脚本任务、远程关机、加密、调度、事件
├── user/          # 用户、JWT、权限
└── config/        # 安全配置、异步、全局异常
src/main/resources
├── static/        # 零构建前端（Vue 3 + Element Plus + ECharts，含 vendor）
├── application.yaml
└── application-docker.yaml
```

### 许可证 / License

本项目基于 [MIT License](LICENSE) 开源。

---

## English Documentation

### Overview

**Device Manager** is a lightweight, self-hosted console for managing a handful of machines — home labs, small teams or classrooms. See whether devices are online, wake them over LAN when they are off, run scripts remotely over SSH, and optionally power them off on a schedule. The backend is Spring Boot + SQLite (single file, no external service) and the frontend is a zero-build Vue 3 SPA, so it runs comfortably on a resource-constrained box.

### Features

| Module | Description |
| --- | --- |
| 🖥️ Devices | MAC / IP / name, IPv4 and IPv6 |
| 📡 Monitoring | `PING` (system ping) or `HEARTBEAT` (device reports in), statuses `ONLINE / OFFLINE / PROBE / UNKNOWN`, with configurable interval, timeout and offline tolerance |
| ⚡ Wake-on-LAN | Magic packets over IPv4 broadcast / IPv6 multicast; after waking the device enters `PROBE` and is marked offline if it never comes up |
| 🔌 Remote shutdown | Over SSH with passwordless sudo (`sudo -n`) or a sudo password (`sudo -S`); works for Bash and PowerShell targets |
| 📜 Script tasks | **One script can target many devices**; triggers: `ONCE`, `CRON`, `ON_BOOT`; on success optionally shut down (never / immediately / after a delay); one log per target device |
| ⬇️ Heartbeat script | Generate and download a Linux (systemd) or Windows (scheduled task) heartbeat agent |
| 👤 Users & roles | JWT login, `ADMIN` / `USER`, admin user management, self-service username / password change |
| 🌗 UI | Vue 3 + Element Plus + ECharts, light/dark theme, bilingual, 5-second live polling |
| 🔒 Security | SSH key authentication only; host key fingerprint recorded on first connect and verified afterwards; private keys / passphrases / sudo passwords encrypted with AES-256-GCM and never returned by the API |

### Tech stack

- **Backend**: Spring Boot 4.1, Java 21, Spring Web MVC, Spring Data JPA, Spring Security + JJWT, SQLite (xerial JDBC, single connection), Apache MINA SSHD, BouncyCastle, Lombok
- **Frontend**: Vue 3 + Element Plus + ECharts + dayjs (all vendored locally, no Node or build step)
- **Build & deploy**: Maven, Docker (multi-arch amd64/arm64), GitHub Actions → GHCR, GraalVM Native Image (experimental)

### Quick start

#### Run locally

```bash
./mvnw spring-boot:run        # Linux / macOS
mvnw.cmd spring-boot:run      # Windows
```

Open <http://localhost:8080>. On first boot the random admin password is printed to the log (`Password: xxxxx`) — change it right after signing in.

#### Docker

```bash
docker build -t device-manager .
docker run -d --name device-manager \
  -p 8080:8080 \
  -v device-manager-data:/app/data \
  device-manager
```

> - Add `--cap-add=NET_RAW` if you need ICMP `ping` inside the container.
> - Use `--network host` if Wake-on-LAN broadcast / multicast must reach your LAN.

### Configuration

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
| `app.script.on-boot-cooldown-seconds` | Cooldown for the same `ON_BOOT` task |
| `SPRING_PROFILES_ACTIVE` | Set to `docker` for low-memory tuning (no SQL logs, Tomcat threads = 8, graceful shutdown, …) |

> ⚠️ **Back up the master key.** It encrypts the stored private keys / passphrases; lose it and they cannot be decrypted.

### Startup & runtime parameters

**JVM image memory flags** (`JAVA_OPTS`, defaults set in the Dockerfile, override with `-e JAVA_OPTS=...`):

```
-Xms48m -Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k \
-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError
```

On tighter devices lower `-Xmx` to `192m` or even `128m`.

**native image memory flag**: pass runtime options with the `-XX:` prefix:

```bash
docker run -d ... device-manager-native -XX:MaxHeapSize=192m
```

A native image defaults to ~80% of physical RAM as max heap; cap it explicitly on small devices.

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

#### Troubleshooting native at runtime (optional)

GraalVM native is a closed-world analysis, so reflection used by third-party libraries must be
registered ahead of time. This project already handles `FileSystemProvider`, security providers
and the sqlite metadata. If you hit `MissingReflectionRegistrationError` at runtime (especially
with SSH script tasks), generate the config **precisely** with the tracing agent instead of guessing:

```bash
# requires GraalVM (ships native-image-agent)
# 1) start the JVM app with the agent
java -agentlib:native-image-agent=config-merge-dir=target/native-config \
     -jar target/device-manager-*.jar
# 2) exercise the relevant features: log in -> create & run a script task -> test SSH -> shut down
# 3) Ctrl-C; configs are written to target/native-config/
# 4) copy them into the sources and rebuild native
mkdir -p src/main/resources/META-INF/native-image/com.example/device-manager
cp target/native-config/*.json src/main/resources/META-INF/native-image/com.example/device-manager/
./mvnw -Pnative -DskipTests native:compile
```

The generated files are committed with the source and picked up automatically. You do not need
them unless you actually hit a problem.

### Remote shutdown & sudo

On Linux targets, configure passwordless sudo (no password stored on the server):

```
<ssh-user> ALL=(root) NOPASSWD: /sbin/shutdown, /usr/sbin/shutdown, /sbin/poweroff, /usr/sbin/poweroff, /sbin/halt, /usr/sbin/halt
```

Alternatively, store a **sudo password** on the device / task; the server feeds it to `sudo -S` over stdin (it never appears on the command line).
On Windows targets, the SSH user must be an administrator.

### API overview

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/auth` | Log in, returns a JWT |
| `GET/POST` | `/api/device` | List / create devices |
| `GET/PUT/DELETE` | `/api/device/{id}` | Get / update / delete a device |
| `POST` | `/api/device/{id}/wake` | Wake-on-LAN |
| `GET/PUT` | `/api/device/{id}/ssh` | Device-level SSH config |
| `POST` | `/api/device/{id}/shutdown` | Device-level remote shutdown |
| `GET` | `/api/device/{id}/heartbeat-script?os=linux\|windows` | Download the heartbeat script |
| `POST` | `/api/heartbeat?mac=...` | Device-side heartbeat (`X-Device-Token` required) |
| `GET/POST` | `/api/script-task` | List / create script tasks (multi-target) |
| `PUT/DELETE/PATCH` | `/api/script-task/{id}` `/toggle` | Update / delete / enable-disable |
| `POST` | `/api/script-task/{id}/execute` | Run now (returns per-target log IDs) |
| `POST` | `/api/script-task/{id}/shutdown` | Shut down all targets of the task |
| `GET` | `/api/script-task/{id}/logs` | Execution logs (paged) |
| `POST` | `/api/script-task/test-ssh` | SSH connectivity test |
| `GET` | `/api/script-task-log/{logId}` | Single log detail |
| `GET/POST/PUT/DELETE` | `/api/admin...` | Admin user management (`ADMIN` only) |

Everything except `/api/auth/**`, `/api/heartbeat/**`, `/api/version` and static assets requires authentication, and you can only access devices and tasks you own.

### Project layout

```
src/main/java/com/example/tool
├── device/        # devices, monitoring, WOL, health checks
├── scripttask/    # SSH script tasks, remote shutdown, crypto, scheduler, events
├── user/          # users, JWT, roles
└── config/        # security, async, global exception handling
src/main/resources
├── static/        # zero-build frontend (Vue 3 + Element Plus + ECharts, vendored)
├── application.yaml
└── application-docker.yaml
```

### License

Released under the [MIT License](LICENSE).
