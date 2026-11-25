# syntax=docker/dockerfile:1

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 容器内构建同样使用阿里云 Maven 镜像（可被 -s 参数覆盖）
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml ./
RUN mvn -B -s /root/.m2/settings.xml dependency:go-offline || true

COPY src ./src
RUN mvn -B -s /root/.m2/settings.xml clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone || true

COPY --from=build /build/target/water-repair-service.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
