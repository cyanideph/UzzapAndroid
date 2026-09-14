# syntax=docker/dockerfile:1
# Builds the Uzzap (KOTLINJARZAP) Android app and outputs a debug APK.
#
# Usage:
#   docker build -t uzzap-build .
#   docker create --name uzzap-tmp uzzap-build
#   docker cp uzzap-tmp:/output/app-debug.apk ./app-debug.apk
#   docker rm uzzap-tmp
#
# Requires network access to Docker Hub, dl.google.com, services.gradle.org
# and Maven Central.

FROM eclipse-temurin:17-jdk-jammy AS build

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
        wget unzip curl \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    cd ${ANDROID_SDK_ROOT}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O tools.zip && \
    unzip -q tools.zip && \
    rm tools.zip && \
    mv cmdline-tools latest

ENV PATH=${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}

RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true
RUN sdkmanager \
        "platform-tools" \
        "platforms;android-34" \
        "build-tools;34.0.0"

WORKDIR /workspace
COPY . .

# Only non-privileged client configuration is accepted.
ARG SUPABASE_URL
ARG SUPABASE_ANON_KEY
ENV SUPABASE_URL=${SUPABASE_URL}
ENV SUPABASE_ANON_KEY=${SUPABASE_ANON_KEY}

RUN chmod +x gradlew && ./gradlew --no-daemon assembleDebug

RUN mkdir -p /output && \
    cp app/build/outputs/apk/debug/app-debug.apk /output/app-debug.apk

FROM scratch AS export
COPY --from=build /output/app-debug.apk /app-debug.apk
