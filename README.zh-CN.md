# 设备管家 · Device Manager (Beta)

> 自托管的设备在线监控、网络唤醒与 SSH 远程运维控制台

[English](README.md) · [中文](README.zh-CN.md) · [MIT License](LICENSE)

---

## 简介

**设备管家（Device Manager）** 是一个轻量、自托管的设备管理控制台，面向家庭 / 小团队 / 实验室里的多台机器：随时看到设备是否在线，离线时一键网络唤醒，需要时通过 SSH 远程执行脚本，甚至按计划自动关机。后端基于 Spring Boot + SQLite（单文件、零外部依赖），前端是零构建的 Vue 3 单页应用，可直接跑在资源紧凑的小主机上。

## 功能特性

| 模块 | 说明 |
| --- | --- |
| 🖥️ 设备管理 | 维护 MAC / IP / 名称，支持 IPv4 与 IPv6 |
| 📡 在线监控 | `PING`（系统 ping）或 `HEARTBEAT`（设备定时上报）两种模式，状态 `ONLINE / OFFLINE / PROBE / UNKNOWN`，可配置检测间隔、超时与离线容忍 |
| ⚡ 网络唤醒 | Wake-on-LAN 魔术包，IPv4 广播 / IPv6 组播；唤醒后进入 `PROBE` 等待上线，超时判定为离线 |
| 🔌 远程关机 | 通过 SSH 关机，支持免密 sudo（`sudo -n`）或 sudo 密码（`sudo -S`），bash / PowerShell 目标均可 |
| 📜 脚本任务 | **一个脚本可挂多台设备**；触发方式：`MANUAL`（仅界面手动执行）/ `ONCE`（定时一次）/ `CRON`（周期）/ `ON_BOOT`（设备上线时）；脚本成功后可选择不关机 / 立即关机 / 延迟关机；每个目标设备独立记录执行日志 |
| 🧭 脚本检查 | 内置纯前端语法检查，编辑器基于 **Monaco**（VS Code 同款内核，本地 vendor，离线可用）：语法高亮、minimap、代码折叠、查找替换、括号配对 / 着色，检查结果以行内波浪线 + 问题面板呈现；支持 Bash / PowerShell（引号、括号、here-doc、`if/fi` 配对、CRLF 等） |
| ⬇️ 心跳脚本 | 一键生成并下载 Linux（systemd）或 Windows（计划任务）心跳上报脚本 |
| 🚀 版本更新 | 定时对比 GitHub Releases 检测新版本并在界面提示；Docker 部署给出更新命令，裸机部署可开启「直接更新」下载产物替换自身（看门狗脚本负责拉起） |
| 👤 用户与权限 | JWT 登录，`ADMIN` / `USER` 角色，管理员可管理用户，所有人可改自己的用户名与密码 |
| 🌗 界面 | Vue 3 + Element Plus + ECharts，明暗主题切换、中英双语、5 秒实时轮询 |
| 🔒 安全 | SSH 仅支持密钥认证；目标主机指纹首次记录、之后校验；私钥 / 口令 / sudo 密码均以 AES-256-GCM 加密落库，接口永不返回 |

## 技术栈

- **后端**：Spring Boot 4.1、Java 21、Spring Web MVC、Spring Data JPA、Spring Security + JJWT、SQLite（xerial JDBC，单连接）、Apache MINA SSHD、BouncyCastle、Lombok
- **前端**：Vue 3 + Element Plus + ECharts + Monaco（全部本地 vendor，无需 Node / 构建步骤）
- **构建与部署**：Maven、Docker（多架构 amd64/arm64）、GitHub Actions → GHCR + Docker Hub。**不再构建 / 发布 GraalVM native 镜像**（封闭世界编译坑太多、维护成本高），只维护 JVM 镜像。

## 快速开始

### 本地运行

```bash
./mvnw spring-boot:run        # Linux / macOS
mvnw.cmd spring-boot:run      # Windows
```

打开 <http://localhost:8080>。首次启动会在日志里打印管理员随机密码（`Password: xxxxx`），登录后请立即修改。

### Docker

```bash
docker build -t device-manager .
docker run -d --name device-manager \
  -p 8080:8080 \
  -v device-manager-data:/app/data \
  device-manager
```

> - 容器内需要 `ping`（ICMP）时加 `--cap-add=NET_RAW`；
> - 需要 Wake-on-LAN 广播 / 组播出宿主机网段时建议 `--network host`。

## 配置

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
| `app.script.ssh.output-charset` | 远程输出解码：`AUTO`（默认，严格 UTF-8，失败回退 GB18030）或 `UTF-8` / `GBK` / `GB18030` / `windows-1252` |
| `app.script.on-boot-cooldown-seconds` | `ON_BOOT` 同一任务冷却时间 |
| `app.version` | 当前版本号（构建时由 Maven 注入，即 `pom.xml` 的版本） |
| `app.update.github-repo` | 版本检测来源仓库，默认 `evil-qq-intestine/Device-Manager` |
| `app.update.check-interval-ms` | 版本检测间隔（毫秒），默认 `21600000`（6 小时） |
| `app.update.docker-image` | 镜像名，用于生成 Docker 更新命令（仅 `docker pull`，重启由你自行处理） |
| `SPRING_PROFILES_ACTIVE` | 设为 `docker` 启用小内存设备优化（关 SQL 日志、Tomcat 线程 8、优雅停机等） |

> ⚠️ **主密钥务必妥善备份**：它用于加解密存储的私钥 / 口令，丢失后已保存的密钥将无法解密。

## 启动与运行参数

**JVM 镜像内存参数**（`JAVA_OPTS`，Dockerfile 已有默认值，可用 `-e JAVA_OPTS=...` 覆盖）：

```
-Xms48m -Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k \
-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError
```

资源更紧张的设备可把 `-Xmx` 调到 `192m` 甚至 `128m`。

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

### native 镜像已停止维护

> ⚠️ 自 `1.0.5` 起**不再构建、不再发布 GraalVM native 镜像**，只维护 JVM 镜像与可执行 jar。native 封闭世界编译的坑太多（反射、动态代理、运行期注册安全 Provider），维护成本不划算。请使用 `ghcr.io/evil-qq-intestine/device-manager:latest`（或 Docker Hub 镜像）。
>
> 历史说明：`1.0.3` 及更早的 native 包 SSH 功能完全不可用（报 `Internal server error`，BouncyCastle 提供者未在构建期注册）；`1.0.4` 修复了该问题，但随后即放弃 native。

## 远程关机与 sudo

Linux 目标机推荐配置免密 sudo（服务端无需保存密码）：

```
<ssh-user> ALL=(root) NOPASSWD: /sbin/shutdown, /usr/sbin/shutdown, /sbin/poweroff, /usr/sbin/poweroff, /sbin/halt, /usr/sbin/halt
```

若不便改 sudoers，也可在设备 / 任务里填写 **sudo 密码**，服务端会用 `sudo -S` 通过 stdin 传入（不会出现在命令行中）。
Windows 目标则要求 SSH 用户为管理员；Windows 没有 sudo，该密码不适用（PowerShell 目标下输入框会隐藏）。

### SSH 私钥格式

SSH 连接**仅支持密钥认证**。请使用 **OpenSSH 格式**私钥（`ssh-keygen` 生成）；不支持 PuTTY 的 `.ppk`（会解析失败）。私钥有口令时请填写「私钥口令」。

## 版本检测与更新

服务启动后会定时（默认 6 小时）调用 GitHub Releases API 对比当前版本与最新版本，有新版本时顶栏出现提示。当前版本由构建时注入（`app.version`，即 `pom.xml` 的版本号），因此正式发布的镜像版本号与 Release 标签一致。

**Docker 部署**：点开提示弹窗，复制更新命令执行即可（镜像同时发布到 GHCR 与 Docker Hub，后者默认 `emmmm666/device-manager`，可用仓库变量 `DOCKERHUB_IMAGE` 覆盖）：

```bash
docker pull ghcr.io/evil-qq-intestine/device-manager:1.0.6
```

镜像名由 `app.update.docker-image` 配置；拉取后请按你的方式重启容器（如 `docker compose up -d` 或 `docker restart <容器名>`）。

**裸机部署（jar）**：管理员可在弹窗里开启「直接更新」。开启后应用会下载 Release 产物（`device-manager.jar`）替换自身并退出，再由**看门狗脚本**拉起新版本。看门狗脚本可直接在弹窗里下载：

```bash
DEVICE_MANAGER_APP=java \
DEVICE_MANAGER_ARGS="-jar /opt/device-manager/device-manager.jar --server.port=8080" \
./device-manager-watchdog.sh
```

> ⚠️ 直接更新会替换正在运行的可执行文件 / jar 并退出进程，**必须**由看门狗或 systemd 之类的守护进程拉起，否则服务不会自动恢复。
> 该开关存放在 `system_config` 表（键 `update.direct-enabled`），默认关闭，仅管理员可修改。
> Docker 部署不支持直接更新（容器内替换无意义），请使用上面的命令。

## 接口

后端为 REST API（`/api/**`）。除 `/api/auth/**`、`/api/heartbeat/**`、`/api/version` 与静态资源外，其余接口均需登录，且只能访问自己名下的设备与任务。

## 项目结构

```
src/main/java/com/example/tool
├── device/        # 设备、监控、WOL、健康检查
├── scripttask/    # SSH 脚本任务、远程关机、加密、调度、事件
├── user/          # 用户、JWT、权限
├── system/        # 系统配置（键值表）
├── versionController/  # 版本检测、更新与看门狗脚本
└── config/        # 安全配置、异步、全局异常
src/main/resources
├── static/        # 零构建前端（Vue 3 + Element Plus + ECharts + Monaco，含 vendor）
├── application.yaml
└── application-docker.yaml
```

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
