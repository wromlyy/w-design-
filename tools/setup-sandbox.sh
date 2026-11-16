#!/usr/bin/env bash
# Prepares a fresh low-memory Linux sandbox for building W Design (JDK 17 + Android SDK 35).
set -e
REPO="$(cd "$(dirname "$0")/.." && pwd)"
if ! swapon --show 2>/dev/null | grep -q swapfile; then
  sudo fallocate -l 8G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile >/dev/null && sudo swapon /swapfile
fi
sudo apt-get update -qq && sudo apt-get install -y -qq openjdk-17-jdk-headless unzip libxml2-utils python3-yaml >/dev/null
SDK=$HOME/android-sdk
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools" && cd /tmp
  curl -sSLo clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q -o clt.zip -d "$SDK/cmdline-tools" && rm -rf "$SDK/cmdline-tools/latest" && mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools' >/dev/null
cat > "$HOME/.bashrc_wd" <<EOT
export ANDROID_HOME=$SDK
export ANDROID_SDK_ROOT=$SDK
export PATH=$SDK/platform-tools:$SDK/build-tools/35.0.0:\$PATH
EOT
echo "sdk.dir=$SDK" > "$REPO/local.properties"
echo "sandbox ready"
