# ==============================================================================
# ReelFlow - Android APK Containerized Build Environment
# ==============================================================================
FROM eclipse-temurin:21-jdk-jammy AS builder

LABEL maintainer="ReelFlow Developers"
LABEL description="Headless reproducible Docker build environment for ReelFlow Android app"

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

# 1. Install prerequisites
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    unzip \
    git \
    bash \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# 2. Download and setup Android Command-Line Tools
ARG CMDLINE_TOOLS_VERSION=11076708
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip -o /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# 3. Accept licenses & install required SDK components
RUN yes | sdkmanager --licenses > /dev/null && \
    sdkmanager --install \
        "platform-tools" \
        "platforms;android-35" \
        "build-tools;35.0.0"

# 4. Set working directory
WORKDIR /workspace

# 5. Cache Gradle dependencies first
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

RUN chmod +x ./gradlew && \
    ./gradlew --version

# 6. Copy source code
COPY app ./app

# 7. Compile Debug APK
RUN ./gradlew assembleDebug --no-daemon --stacktrace

# ==============================================================================
# Export Stage: Lightweight image containing the output APK
# ==============================================================================
FROM alpine:3.19 AS export
WORKDIR /out
COPY --from=builder /workspace/app/build/outputs/apk/debug/app-debug.apk /out/reelflow-debug.apk
CMD ["sh", "-c", "echo 'ReelFlow APK built successfully at /out/reelflow-debug.apk' && ls -la /out/reelflow-debug.apk"]
