#!/usr/bin/env python3
"""Build sharedLogic/sharedUI + androidApp and run it in the Android Emulator.

Usage: run-android.py [--clean] [--seed [days]] [language-code]
  --clean: uninstall the app (if present) before installing, wiping its data.
  --seed [days]: uninstall first (implies --clean), then on launch create a
    default profile and generate that many days of test workout history
    (default 60). Debug-only hook.
  language-code: optional, e.g. "en" or "sv". Overrides the app's per-app
    language via `cmd locale set-app-locales`, without changing the
    emulator's system-wide language setting.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

AVD_NAME = "Medium_Phone"
APP_ID = "dev.balaji.moonrose.logtrain"


def configure_environment() -> None:
    if not os.environ.get("JAVA_HOME") and sys.platform == "darwin":
        homebrew_jdk = Path("/opt/homebrew/opt/openjdk")
        if homebrew_jdk.is_dir():
            os.environ["JAVA_HOME"] = str(homebrew_jdk)

    sdk_env = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk_env:
        sdk_dir = Path(sdk_env)
    elif sys.platform == "darwin":
        sdk_dir = Path.home() / "Library" / "Android" / "sdk"
    elif os.name == "nt":
        local_app_data = os.environ.get("LOCALAPPDATA")
        sdk_dir = (
            Path(local_app_data) / "Android" / "Sdk"
            if local_app_data
            else Path.home() / "AppData" / "Local" / "Android" / "Sdk"
        )
    else:
        sdk_dir = Path.home() / "Android" / "Sdk"

    if sdk_dir.is_dir():
        extra_paths = [
            str(sdk_dir / "platform-tools"),
            str(sdk_dir / "emulator"),
        ]
        current_path = os.environ.get("PATH", "")
        os.environ["PATH"] = os.pathsep.join(extra_paths + ([current_path] if current_path else []))


def parse_args(argv: list[str]) -> tuple[bool, int, str]:
    clean = False
    seed_days = 0
    lang_code = ""

    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg in ("-h", "--help"):
            print(__doc__.strip())
            sys.exit(0)
        elif arg == "--clean":
            clean = True
        elif arg == "--seed":
            seed_days = 60
            if i + 1 < len(argv) and re.fullmatch(r"[0-9]+", argv[i + 1]):
                seed_days = int(argv[i + 1])
                i += 1
        else:
            lang_code = arg
        i += 1

    # Seeding only fires on an install with no profile, so start from one.
    if seed_days > 0:
        clean = True

    return clean, seed_days, lang_code


def require_tool(name: str) -> str:
    resolved = shutil.which(name)
    if not resolved:
        sys.exit(f"Error: '{name}' not found on PATH. Ensure the Android SDK tools are installed.")
    return resolved


def run(cmd: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, check=True, **kwargs)


def has_connected_device(adb: str) -> bool:
    result = run([adb, "devices"], capture_output=True, text=True)
    for line in result.stdout.splitlines()[1:]:
        if re.search(r"\bdevice$", line.strip()):
            return True
    return False


def ensure_device_ready(adb: str) -> None:
    if has_connected_device(adb):
        return

    emulator = require_tool("emulator")
    log_path = Path(tempfile.gettempdir()) / "logtrain-emulator.log"
    print(f'No emulator running — booting "{AVD_NAME}" (log: {log_path})...')

    popen_kwargs: dict = {}
    if os.name == "nt":
        popen_kwargs["creationflags"] = (
            subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP
        )
    else:
        popen_kwargs["start_new_session"] = True

    with log_path.open("wb") as log_file:
        subprocess.Popen(
            [emulator, "-avd", AVD_NAME],
            stdin=subprocess.DEVNULL,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            **popen_kwargs,
        )

    run([adb, "wait-for-device"])

    # Wait for full boot, not just the ADB link.
    while True:
        res = subprocess.run(
            [adb, "shell", "getprop", "sys.boot_completed"],
            capture_output=True,
            text=True,
        )
        if res.stdout.strip() == "1":
            break
        time.sleep(2)


def is_app_installed(adb: str, app_id: str) -> bool:
    result = run([adb, "shell", "pm", "list", "packages"], capture_output=True, text=True)
    target = f"package:{app_id}"
    return any(line.strip() == target for line in result.stdout.splitlines())


def main() -> None:
    repo_root = Path(__file__).resolve().parent.parent
    os.chdir(repo_root)

    configure_environment()
    clean, seed_days, lang_code = parse_args(sys.argv[1:])

    adb = require_tool("adb")
    ensure_device_ready(adb)

    if clean:
        if is_app_installed(adb, APP_ID):
            print(f"Uninstalling {APP_ID}...")
            run([adb, "uninstall", APP_ID])
        else:
            print(f"{APP_ID} not installed — nothing to uninstall.")

    gradlew = str(repo_root / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    run([gradlew, ":androidApp:installDebug"])

    run([adb, "shell", "am", "force-stop", APP_ID])

    if lang_code:
        locale_map = {
            "en": "en-US",
            "sv": "sv-SE",
        }
        locale = locale_map.get(lang_code, lang_code)
        print(f"Launching with language override: {lang_code} ({locale})")
        run(
            [
                adb,
                "shell",
                "cmd",
                "locale",
                "set-app-locales",
                APP_ID,
                "--user",
                "current",
                "--locales",
                locale,
            ]
        )

    start_cmd = [adb, "shell", "am", "start", "-n", f"{APP_ID}/.MainActivity"]
    if seed_days > 0:
        print(f"Launching with test-data seed: {seed_days} days")
        start_cmd.extend(["--ei", "seedTestData", str(seed_days)])

    run(start_cmd)


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as exc:
        sys.exit(exc.returncode)

    if clean:
        if is_app_installed(adb, APP_ID):
            print(f"Uninstalling {APP_ID}...")
            run([adb, "uninstall", APP_ID])
        else:
            print(f"{APP_ID} not installed — nothing to uninstall.")

    gradlew = str(repo_root / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    run([gradlew, ":androidApp:installDebug"])

    run([adb, "shell", "am", "force-stop", APP_ID])

    if lang_code:
        locale_map = {
            "en": "en-US",
            "sv": "sv-SE",
        }
        locale = locale_map.get(lang_code, lang_code)
        print(f"Launching with language override: {lang_code} ({locale})")
        run(
            [
                adb,
                "shell",
                "cmd",
                "locale",
                "set-app-locales",
                APP_ID,
                "--user",
                "current",
                "--locales",
                locale,
            ]
        )

    start_cmd = [adb, "shell", "am", "start", "-n", f"{APP_ID}/.MainActivity"]
    if seed_days > 0:
        print(f"Launching with test-data seed: {seed_days} days")
        start_cmd.extend(["--ei", "seedTestData", str(seed_days)])

    run(start_cmd)


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as exc:
        sys.exit(exc.returncode)

