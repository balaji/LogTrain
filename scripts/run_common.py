"""Shared helpers for run-android.py and run-ios.py."""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Callable

DEFAULT_LOCALES: dict[str, tuple[str, str]] = {
    "en": ("en", "US"),
    "sv": ("sv", "SE"),
}


def configure_java_home() -> None:
    """Point JAVA_HOME at Homebrew OpenJDK on macOS when not already set."""
    if not os.environ.get("JAVA_HOME") and sys.platform == "darwin":
        homebrew_jdk = Path("/opt/homebrew/opt/openjdk")
        if homebrew_jdk.is_dir():
            os.environ["JAVA_HOME"] = str(homebrew_jdk)


def require_tool(name: str, hint: str = "") -> str:
    """Return the resolved path to a CLI tool or exit with an error."""
    resolved = shutil.which(name)
    if not resolved:
        suffix = f" {hint}" if hint else ""
        sys.exit(f"Error: '{name}' not found on PATH.{suffix}")
    return resolved


def run(cmd: list[str], **kwargs) -> subprocess.CompletedProcess:
    """Run a command and raise CalledProcessError on non-zero exit."""
    return subprocess.run(cmd, check=True, **kwargs)


def create_arg_parser(
    description: str,
    *,
    default_lang: str = "",
    lang_help: str = 'Optional language override, e.g. "en" or "sv".',
) -> argparse.ArgumentParser:
    """Build an ArgumentParser with the shared --clean, --seed, and language-code args."""
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument(
        "--clean",
        action="store_true",
        help="Uninstall the app (if present) before installing, wiping its data.",
    )
    parser.add_argument(
        "--seed",
        nargs="?",
        const=60,
        default=0,
        type=int,
        metavar="DAYS",
        help="Uninstall first (implies --clean), then seed test workout history (default: 60 days).",
    )
    parser.add_argument(
        "lang_code",
        nargs="?",
        default=default_lang,
        metavar="language-code",
        help=lang_help,
    )
    return parser


def _normalize_seed_argv(argv: list[str]) -> list[str]:
    """Keep `--seed <lang>` from treating a non-numeric language code as DAYS."""
    normalized: list[str] = []
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg == "--seed" and i + 1 < len(argv):
            next_arg = argv[i + 1]
            if not next_arg.startswith("-") and not next_arg.isdigit():
                normalized.append("--seed=60")
                i += 1
                continue
        normalized.append(arg)
        i += 1
    return normalized


def parse_common_args(
    parser: argparse.ArgumentParser, argv: list[str] | None = None
) -> argparse.Namespace:
    """Parse arguments with --seed/--clean normalization."""
    raw_argv = sys.argv[1:] if argv is None else list(argv)
    args = parser.parse_args(_normalize_seed_argv(raw_argv))
    if args.seed < 0:
        parser.error("--seed DAYS must be a non-negative integer")
    if args.seed > 0:
        args.clean = True
    return args


def resolve_locale(lang_code: str, separator: str = "-") -> str:
    """Map a short language code (e.g. 'en', 'sv') to a regional locale."""
    if lang_code in DEFAULT_LOCALES:
        lang, region = DEFAULT_LOCALES[lang_code]
        return f"{lang}{separator}{region}"
    return lang_code


def _write_stderr(data: str | bytes | None) -> None:
    if not data:
        return
    if isinstance(data, bytes):
        sys.stderr.buffer.write(data)
    else:
        sys.stderr.write(data)


def run_main(main_fn: Callable[[], None]) -> None:
    """Execute main_fn, surfacing subprocess output on failure."""
    try:
        main_fn()
    except subprocess.CalledProcessError as exc:
        _write_stderr(exc.stdout)
        _write_stderr(exc.stderr)
        sys.exit(exc.returncode)
