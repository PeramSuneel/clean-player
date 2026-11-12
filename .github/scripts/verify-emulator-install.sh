#!/usr/bin/env bash
set -Eeuo pipefail

APK="${GITHUB_WORKSPACE:-$PWD}/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_ID="com.peramsuneel.cleanplayer.glass"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"

echo "Workspace: ${GITHUB_WORKSPACE:-$PWD}"
echo "Checking APK: $APK"
if [[ ! -s "$APK" ]]; then
  echo "::error::Debug APK is missing or empty at the expected path."
  find "${GITHUB_WORKSPACE:-$PWD}/app/build/outputs/apk" -maxdepth 5 -type f -name '*.apk' -print 2>/dev/null || true
  exit 1
fi
ls -lh "$APK"

diagnostics() {
  echo "::group::Emulator diagnostics"
  adb devices -l || true
  adb -s "$SERIAL" get-state || true
  adb -s "$SERIAL" shell getprop sys.boot_completed || true
  adb -s "$SERIAL" shell getprop init.svc.bootanim || true
  adb -s "$SERIAL" shell service check activity || true
  adb -s "$SERIAL" shell service check package || true
  adb -s "$SERIAL" shell settings get global device_provisioned 2>&1 || true
  adb -s "$SERIAL" shell cmd package list packages 2>&1 | tail -n 80 || true
  adb -s "$SERIAL" logcat -b all -d -t 2000 2>&1 | tail -n 500 || true
  echo "::endgroup::"
}

wait_for_android() {
  echo "Waiting for emulator device and completed Android boot..."
  adb start-server
  adb -s "$SERIAL" wait-for-device
  for attempt in $(seq 1 300); do
    state="$(adb -s "$SERIAL" get-state 2>/dev/null || true)"
    booted="$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    bootanim="$(adb -s "$SERIAL" shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r' || true)"
    if [[ "$state" == "device" && "$booted" == "1" && "$bootanim" == "stopped" ]]; then
      break
    fi
    sleep 2
  done

  state="$(adb -s "$SERIAL" get-state 2>/dev/null || true)"
  booted="$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$state" != "device" || "$booted" != "1" ]]; then
    echo "Emulator did not reach a fully booted, ADB-ready state."
    return 1
  fi

  if [[ "$bootanim" != "stopped" ]]; then
    echo "Boot animation has not stopped (current state: ${bootanim:-unavailable})."
    return 2
  fi

  echo "Android boot completed. Waiting for Activity Manager, Package Manager, and Settings provider..."
  local activity_status package_status package_list_status settings_status settings_value
  for attempt in $(seq 1 90); do
    activity_status="$(adb -s "$SERIAL" shell service check activity 2>&1 || true)"
    package_status="$(adb -s "$SERIAL" shell service check package 2>&1 || true)"

    if printf '%s' "$activity_status" | grep -q "found" &&
       printf '%s' "$package_status" | grep -q "found"; then
      if adb -s "$SERIAL" shell cmd package list packages >/dev/null 2>&1; then
        package_list_status=0
      else
        package_list_status=1
      fi

      if [[ "$package_list_status" -eq 0 ]]; then
        # Read-only probe: successful access proves the Settings content provider
        # is queryable. The value itself may legitimately be 0, 1, or null.
        if settings_value="$(adb -s "$SERIAL" shell settings get global device_provisioned 2>&1)"; then
          settings_status=0
        else
          settings_status=$?
        fi
        if [[ "$settings_status" -eq 0 ]]; then
          echo "Activity Manager is available: $activity_status"
          echo "Package Manager is available and responsive: $package_status"
          echo "Settings content provider is accessible (device_provisioned=$settings_value)."
          return 0
        fi
      fi
    fi
    sleep 2
  done

  echo "Android system services/providers did not become ready within 180 seconds."
  echo "Last Activity Manager check: ${activity_status:-not completed}"
  echo "Last Package Manager check: ${package_status:-not completed}"
  echo "Last Settings provider check: ${settings_value:-not accessible}"
  return 2
}

recover_emulator() {
  echo "Attempting one controlled ADB reconnect..."
  adb kill-server || true
  adb start-server
  if wait_for_android; then
    return 0
  fi

  echo "ADB reconnect did not restore Android services; attempting one emulator reboot..."
  adb -s "$SERIAL" reboot || true
  if wait_for_android; then
    return 0
  fi

  echo "Emulator recovery failed."
  diagnostics
  return 1
}

if ! wait_for_android; then
  echo "Initial emulator readiness check failed."
  diagnostics
  recover_emulator || exit 1
fi

echo "Installing $APK..."
set +e
install_output="$(adb -s "$SERIAL" install -r "$APK" 2>&1)"
install_status=$?
set -e
printf '%s\n' "$install_output"

if [[ $install_status -ne 0 ]]; then
  if printf '%s' "$install_output" | grep -Eiq 'Broken pipe|Failure calling service package|device offline|no devices/emulators found|device not found|transport error|connection reset|closed|protocol fault|cannot connect to daemon'; then
    echo "Installation failed with a recoverable ADB/emulator communication error."
    diagnostics
    recover_emulator || exit 1
    echo "Retrying installation once after emulator recovery..."
    adb -s "$SERIAL" install -r "$APK"
  else
    echo "Installation failed with a non-recoverable APK/package installation error; not retrying."
    diagnostics
    exit "$install_status"
  fi
fi

echo "Verifying installed package..."
package_path="$(adb -s "$SERIAL" shell pm path "$PACKAGE_ID" | tr -d '\r')"
if [[ -z "$package_path" || "$package_path" != package:* ]]; then
  echo "Android did not report the expected installed package: $PACKAGE_ID"
  diagnostics
  exit 1
fi
echo "$package_path"

echo "Launching application..."
adb -s "$SERIAL" shell monkey -p "$PACKAGE_ID" -c android.intent.category.LAUNCHER 1
launched=false
for attempt in $(seq 1 15); do
  if adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -Fq "$PACKAGE_ID"; then
    launched=true
    break
  fi
  sleep 2
done
if [[ "$launched" != true ]]; then
  echo "Package is installed, but the launcher activity was not observed as running."
  diagnostics
  exit 1
fi

echo "PASS: APK installed, package verified, and application launch observed."
