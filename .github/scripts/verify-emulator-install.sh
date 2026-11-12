#!/usr/bin/env bash
set -Eeuo pipefail

APK="\${GITHUB_WORKSPACE:-$PWD}/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_ID="com.peramsuneel.cleanplayer.glass"
SERIAL="\${ANDROID_SERIAL:-emulator-5554}"

echo "Workspace: \${GITHUB_WORKSPACE:-$PWD}"
echo "Checking APK: $APK"
if [[ ! -s "$APK" ]]; then
  echo "::error::Debug APK is missing or empty at the expected path."
  find "\${GITHUB_WORKSPACE:-$PWD}/app/build/outputs/apk" -maxdepth 5 -type f -name '*.apk' -print 2>/dev/null || true
  exit 1
fi
ls -lh "$APK"

adb_probe() {
  timeout 10s adb -s "$SERIAL" "$@"
}

diagnostics() {
  echo "::group::Emulator diagnostics"
  adb devices -l || true
  adb -s "$SERIAL" get-state || true
  adb_probe shell getprop sys.boot_completed || true
  adb_probe shell getprop init.svc.bootanim || true
  adb_probe shell service check input || true
  adb_probe shell service check activity || true
  adb_probe shell service check package || true
  adb_probe shell settings get global device_provisioned 2>&1 || true
  adb_probe shell cmd package list packages 2>&1 | tail -n 80 || true
  adb_probe logcat -b all -d -t 2000 2>&1 | tail -n 500 || true
  echo "::endgroup::"
}

service_is_ready() {
  local result="$1"
  [[ "$result" =~ found$ ]] && [[ ! "$result" =~ not[[:space:]]found ]]
}

wait_for_android() {
  echo "Waiting up to 240 seconds for a stable, fully responsive Android emulator..."
  local state booted bootanim input_status activity_status package_status settings_value
  local consecutive=0

  for attempt in $(seq 1 120); do
    state="$(adb -s "$SERIAL" get-state 2>/dev/null || true)"
    if [[ "$state" != "device" ]]; then
      consecutive=0
      echo "Attempt $attempt/120: ADB state is \${state:-unavailable}; waiting for device transport."
      sleep 2
      continue
    fi

    booted="$(adb_probe shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    bootanim="$(adb_probe shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r' || true)"
    if [[ "$booted" != "1" || "$bootanim" != "stopped" ]]; then
      consecutive=0
      echo "Attempt $attempt/120: boot_completed=\${booted:-unavailable}, boot_animation=\${bootanim:-unavailable}."
      sleep 2
      continue
    fi

    input_status="$(adb_probe shell service check input 2>&1 || true)"
    activity_status="$(adb_probe shell service check activity 2>&1 || true)"
    package_status="$(adb_probe shell service check package 2>&1 || true)"

    if service_is_ready "$input_status" &&
       service_is_ready "$activity_status" &&
       service_is_ready "$package_status" &&
       settings_value="$(adb_probe shell settings get global device_provisioned 2>&1)" &&
       adb_probe shell cmd package list packages >/dev/null 2>&1; then
      consecutive=$((consecutive + 1))
      echo "Healthy readiness probe $consecutive/3: input, Activity Manager, Package Manager and Settings provider respond."
      if [[ "$consecutive" -ge 3 ]]; then
        echo "Emulator is stable and all required Android services/providers are responsive."
        echo "Settings provider probe returned device_provisioned=$settings_value."
        return 0
      fi
    else
      consecutive=0
      echo "Attempt $attempt/120: Android framework is not fully ready."
      echo "Input: \${input_status:-unavailable}"
      echo "Activity Manager: \${activity_status:-unavailable}"
      echo "Package Manager: \${package_status:-unavailable}"
    fi

    sleep 2
  done

  echo "::error::Emulator did not remain healthy across three consecutive readiness probes within 240 seconds."
  echo "Last ADB state: \${state:-unavailable}"
  echo "Last boot_completed: \${booted:-unavailable}"
  echo "Last boot animation: \${bootanim:-unavailable}"
  echo "Last input service: \${input_status:-unavailable}"
  echo "Last Activity Manager: \${activity_status:-unavailable}"
  echo "Last Package Manager: \${package_status:-unavailable}"
  echo "Last Settings probe: \${settings_value:-unavailable}"
  return 1
}

recover_emulator() {
  echo "Attempting one bounded ADB transport reconnect without killing the action-managed ADB server..."
  adb reconnect offline || true
  if wait_for_android; then
    return 0
  fi
  echo "::error::ADB reconnect did not restore a stable emulator. No unbounded restart will be attempted."
  diagnostics
  return 1
}

if ! wait_for_android; then
  echo "Initial emulator readiness check failed."
  diagnostics
  exit 1
fi

echo "Installing $APK..."
set +e
install_output="$(adb_probe install -r "$APK" 2>&1)"
install_status=$?
set -e
printf '%s\n' "$install_output"

if [[ $install_status -ne 0 ]]; then
  if printf '%s' "$install_output" | grep -Eiq 'Broken pipe|Failure calling service package|device offline|no devices/emulators found|device not found|transport error|connection reset|closed|protocol fault|cannot connect to daemon'; then
    echo "Installation failed with a recoverable ADB/emulator communication error."
    diagnostics
    recover_emulator || exit 1
    echo "Retrying installation once after confirmed emulator recovery..."
    adb_probe install -r "$APK"
  else
    echo "Installation failed with a non-recoverable APK/package installation error; not retrying."
    diagnostics
    exit "$install_status"
  fi
fi

echo "Verifying installed package..."
package_path="$(adb_probe shell pm path "$PACKAGE_ID" | tr -d '\r')"
if [[ -z "$package_path" || "$package_path" != package:* ]]; then
  echo "::error::Android did not report the expected installed package: $PACKAGE_ID"
  diagnostics
  exit 1
fi
echo "$package_path"

echo "Launching application..."
adb_probe shell monkey -p "$PACKAGE_ID" -c android.intent.category.LAUNCHER 1
launched=false
for attempt in $(seq 1 15); do
  if adb_probe shell dumpsys activity activities 2>/dev/null | grep -Fq "$PACKAGE_ID"; then
    launched=true
    break
  fi
  sleep 2
done
if [[ "$launched" != true ]]; then
  echo "::error::Package is installed, but the launcher activity was not observed as running."
  diagnostics
  exit 1
fi

echo "PASS: APK installed, package verified, and application launch observed."
