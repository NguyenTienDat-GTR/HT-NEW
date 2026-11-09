# ==========================================
# STAGE 1: Build Application using Gradle Wrapper
# ==========================================
FROM gradle:8.12-jdk21-alpine AS builder
WORKDIR /app

# Sao chep cac file cau hinh phu thuoc truoc de tan dung Docker cache layer.
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Tai san thu vien (layer nay duoc cache khi build.gradle khong doi) de lan build sau khong tai lai.
RUN sh ./gradlew dependencies --no-daemon -q > /dev/null 2>&1 || true

# Sao chep ma nguon vao container.
COPY src ./src

# Bien dich ung dung ra file JAR (bo qua test de tang toc do build image).
RUN sh ./gradlew bootJar -x test --no-daemon

# ==========================================
# STAGE 2: Lightweight Runtime Environment
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Tao user non-root de tang bao mat.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Sao chep file JAR da build sang runtime image.
COPY --from=builder /app/build/libs/*.jar app.jar

# Cong ung dung.
EXPOSE 1007

# Cau hinh lenh chay mac dinh cho image production.
ENTRYPOINT ["java", "-XX:+UseG1GC", "-jar", "app.jar"]
