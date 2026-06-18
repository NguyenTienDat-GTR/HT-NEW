# ==========================================
# STAGE 1: Build Application using Gradle
# ==========================================
FROM gradle:8.12-jdk21-alpine AS builder
WORKDIR /app

# Sao chép các file cấu hình phụ thuộc trước để tận dụng Docker Cache layer
COPY build.gradle settings.gradle ./

# Sao chép mã nguồn vào container
COPY src ./src

# Biên dịch ứng dụng ra file JAR (Bỏ qua chạy test để tăng tốc độ build)
RUN gradle bootJar -x test --no-daemon

# ==========================================
# STAGE 2: Lightweight Runtime Environment
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Tạo user non-root để đảm bảo bảo mật cho hệ thống, tránh lỗ hổng leo thang đặc quyền
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Sao chép file JAR đã biên dịch từ Stage 1 sang Stage 2
COPY --from=builder /app/build/libs/*.jar app.jar

# Khai báo cổng lắng nghe của ứng dụng (Trùng với port 1007 trong cấu hình của bạn)
EXPOSE 1007

# Định nghĩa các tham số tối ưu bộ nhớ JVM khi chạy container
ENTRYPOINT ["java", "-XX:+UseG1GC", "-jar", "app.jar"]