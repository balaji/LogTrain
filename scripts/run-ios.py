#!/usr/bin/env python3
"""Build sharedLogic/sharedUI + iosApp and run it in the iOS Simulator.

Usage: run-ios.py [--clean] [--seed [days]] [language-code]
  --clean: uninstall the app (if present) before installing, wiping its data.
  --seed [days]: uninstall first (implies --clean), then on launch create a
    default profile and generate that many days of test workout history
    (default 60). Debug-only hook.
  language-code: optional, e.g. "en" or "sv". Overrides the app's language
    for this launch only (via -AppleLanguages/-AppleLocale), without changing
    the simulator's system-wide language setting.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import subprocess
import sys
from pathlib import Path

from run_common import (
    configure_java_home,
    create_arg_parser,
    parse_common_args,
    require_tool,
    resolve_locale,
    run,
    run_main,
)

SIM_NAME = "iPhone 17"
APP_ID = "dev.balaji.moonrose.logtrain.LogTrain"
PROJECT_PATH = "iosApp/iosApp.xcodeproj"
SCHEME = "iosApp"
DERIVED_DATA_PATH = "iosApp/build"
APP_BUNDLE_PATH = f"{DERIVED_DATA_PATH}/Build/Products/Debug-iphonesimulator/LogTrain.app"


def configure_environment() -> None:
    configure_java_home()
    if not os.environ.get("DEVELOPER_DIR"):
        xcode_dev = Path("/Applications/Xcode.app/Contents/Developer")
        if xcode_dev.is_dir():
            os.environ["DEVELOPER_DIR"] = str(xcode_dev)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = create_arg_parser(
        description="Build sharedLogic/sharedUI + iosApp and run it in the iOS Simulator.",
        default_lang="",
        lang_help='Optional language override for this launch only, e.g. "en" or "sv".',
    )
    return parse_common_args(parser, argv)


def has_booted_simulator(xcrun: str) -> bool:
    result = run([xcrun, "simctl", "list", "devices", "-j"], capture_output=True, text=True)
    data = json.loads(result.stdout)
    return any(
        device.get("state") == "Booted"
        for devices in data.get("devices", {}).values()
        for device in devices
    )


def ensure_simulator_booted(xcrun: str, sim_name: str = SIM_NAME) -> None:
    if not has_booted_simulator(xcrun):
        print(f'No simulator booted -- booting "{sim_name}"...')
        run([xcrun, "simctl", "boot", sim_name])


def open_simulator_ui() -> None:
    # DeviceHub replaces Simulator.app as the simulator UI on macOS 27+.
    mac_ver = platform.mac_ver()[0]
    macos_major = int(mac_ver.split(".")[0]) if mac_ver else 0
    ui_app = "DeviceHub" if macos_major >= 27 else "Simulator"
    pgrep = subprocess.run(
        ["pgrep", "-x", ui_app],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    if pgrep.returncode != 0:
        run(["open", "-a", ui_app])


def is_app_installed(xcrun: str, app_id: str) -> bool:
    res = subprocess.run(
        [xcrun, "simctl", "get_app_container", "booted", app_id],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return res.returncode == 0


def main() -> None:
    repo_root = Path(__file__).resolve().parent.parent
    os.chdir(repo_root)

    configure_environment()
    args = parse_args()

    if sys.platform != "darwin":
        sys.exit("Error: run-ios.py requires macOS with Xcode.")

    tool_hint = "Ensure Xcode and its command-line tools are installed."
    xcrun = require_tool("xcrun", tool_hint)
    xcodebuild = require_tool("xcodebuild", tool_hint)

    ensure_simulator_booted(xcrun)
    open_simulator_ui()

    if args.clean:
        if is_app_installed(xcrun, APP_ID):
            print(f"Uninstalling {APP_ID}...")
            run([xcrun, "simctl", "uninstall", "booted", APP_ID])
        else:
            print(f"{APP_ID} not installed -- nothing to uninstall.")

    run(
        [
            xcodebuild,
            "-project",
            PROJECT_PATH,
            "-scheme",
            SCHEME,
            "-configuration",
            "Debug",
            "-sdk",
            "iphonesimulator",
            "-derivedDataPath",
            DERIVED_DATA_PATH,
            "CODE_SIGN_IDENTITY=",
            "CODE_SIGNING_REQUIRED=NO",
        ]
    )

    run([xcrun, "simctl", "install", "booted", APP_BUNDLE_PATH])

    launch_args: list[str] = []
    if args.lang_code:
        locale = resolve_locale(args.lang_code, separator="_")
        print(f"Launching with language override: {args.lang_code} ({locale})")
        launch_args.extend(["-AppleLanguages", f"({args.lang_code})", "-AppleLocale", locale])

    if args.seed > 0:
        print(f"Launching with test-data seed: {args.seed} days")
        launch_args.extend(["-seedTestData", str(args.seed)])

    if launch_args:
        # Relaunch (not just foreground) so the launch arguments actually apply.
        subprocess.run(
            [xcrun, "simctl", "terminate", "booted", APP_ID],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )

    run([xcrun, "simctl", "launch", "booted", APP_ID, *launch_args])


if __name__ == "__main__":
    run_main(main)
