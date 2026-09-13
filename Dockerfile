# syntax=docker/dockerfile:1

# ==================== 构建阶段 ====================
# --platform=$BUILDPLATFORM：构建始终在构建机架构上跑一次（jar 是跨平台的），
# 只有运行阶段按目标架构打包，多架构构建才快。
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷贝 pom，利用镜像层缓存预下载依赖（源码变动时不必重新下载）
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -B -q -DskipTests package

# ==================== 运行阶段 ====================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 非 root 运行；数据目录用于持久化 SQLite 与主密钥
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /app/data \
    && chown -R app:app /app
COPY --from=build --chown=app:app /build/target/*.jar /app/app.jar

# 小内存设备 JVM 参数：限制堆/元空间，串行 GC，降低 JIT 层级
ENV JAVA_OPTS="-Xms48m -Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k \
-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=docker \
    SPRING_DATASOURCE_URL="jdbc:sqlite:/app/data/data.db" \
    APP_SCRIPT_MASTER_KEY_FILE="/app/data/.master-key" \
    APP_SERVER_URL="http://localhost:8080"

VOLUME ["/app/data"]
EXPOSE 8080
USER app

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
