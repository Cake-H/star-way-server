# ============================================================
# 多阶段 Docker 构建 — Maven 多模块 Spring Boot 项目
# 支持两个可执行服务：
#   docker build --build-arg MODULE=ida-app-server    -t ida-app-server .
#   docker build --build-arg MODULE=ida-plugin-server -t ida-plugin-server .
# 默认构建 ida-app-server
# ============================================================

# ---------- Stage 1: 编译 ----------
FROM crpi-57bx5zcp97h0pj85.cn-guangzhou.personal.cr.aliyuncs.com/cakeh/maven:3.9-eclipse-temurin-17-alpine AS builder

ARG MODULE=ida-app-server

WORKDIR /build

# 先复制 POM 文件（利用 Docker 缓存层，避免每次改代码都重新下载依赖）
COPY pom.xml .
COPY ida-common/pom.xml     ida-common/pom.xml
COPY ida-ai/pom.xml         ida-ai/pom.xml
COPY ida-app/pom.xml        ida-app/pom.xml
COPY ida-plugin/pom.xml     ida-plugin/pom.xml
COPY ida-app-server/pom.xml ida-app-server/pom.xml
COPY ida-plugin-server/pom.xml ida-plugin-server/pom.xml

# 下载依赖（此时源码尚未复制，依赖有缓存）
RUN mvn dependency:go-offline -pl ${MODULE} -am -B -q || true

# 复制源码
COPY . .

# 编译指定模块（跳过测试，加快构建）
RUN mvn package -pl ${MODULE} -am -DskipTests -B -q

# ---------- Stage 2: 运行 ----------
FROM crpi-57bx5zcp97h0pj85.cn-guangzhou.personal.cr.aliyuncs.com/cakeh/eclipse-temurin:17-jre-alpine

ARG MODULE=ida-app-server

LABEL maintainer="cakeH"
LABEL module=${MODULE}

# 创建非 root 用户运行应用
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /build/${MODULE}/target/*.jar app.jar

# 根据模块暴露不同端口
# ida-app-server    默认 8088
# ida-plugin-server 默认 8089
EXPOSE 8088 8089

USER appuser

# JVM 参数可通过环境变量覆盖
ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]
