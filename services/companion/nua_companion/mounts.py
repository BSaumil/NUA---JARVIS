"""Owner-configured, named, read-only file mounts for the companion's files
connector (NUA OpenJarvis Final 10, connector library priority item #10:
"companion mounted-files connector").

This is deliberately NOT arbitrary remote filesystem access, which the
directive this work follows explicitly forbids. A client can only ever name
one of the owner's own pre-configured mount aliases (never a raw path), and
every resolved path is verified to still be inside that mount's root after
resolution -- defeating "../" traversal and symlink escapes alike.
"""
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class MountPoint:
    name: str
    root: Path


class MountRegistry:
    """Loads named mounts from NUA_COMPANION_MOUNTS, formatted as
    "name1:/abs/path1,name2:/abs/path2". A mount whose root doesn't exist or
    isn't a directory at load time is dropped rather than silently kept
    broken -- callers see it simply isn't an available mount."""

    def __init__(self, mounts: dict[str, MountPoint]) -> None:
        self._mounts = mounts

    @classmethod
    def from_env(cls, env_value: str | None = None) -> "MountRegistry":
        raw = env_value if env_value is not None else os.getenv("NUA_COMPANION_MOUNTS", "")
        mounts: dict[str, MountPoint] = {}
        for entry in raw.split(","):
            entry = entry.strip()
            if not entry or ":" not in entry:
                continue
            name, _, path_str = entry.partition(":")
            name = name.strip()
            path_str = path_str.strip()
            if not name or not path_str:
                continue
            root = Path(path_str).expanduser().resolve()
            if root.is_dir():
                mounts[name] = MountPoint(name=name, root=root)
        return cls(mounts)

    def names(self) -> list[str]:
        return sorted(self._mounts)

    def resolve(self, mount_name: str, relative_path: str = "") -> Path | None:
        """Resolves `relative_path` against the named mount's root, returning
        None if the mount doesn't exist or the resolved path would escape the
        mount's root (a "../" component, an absolute path, or a symlink that
        resolves outside it)."""
        mount = self._mounts.get(mount_name)
        if mount is None:
            return None
        if relative_path in ("", "."):
            return mount.root
        candidate = Path(relative_path)
        if candidate.is_absolute() or ".." in candidate.parts:
            return None
        resolved = (mount.root / candidate).resolve()
        try:
            resolved.relative_to(mount.root)
        except ValueError:
            return None
        return resolved

    def list_entries(self, mount_name: str, relative_path: str = "") -> list[dict] | None:
        directory = self.resolve(mount_name, relative_path)
        if directory is None or not directory.is_dir():
            return None
        entries = []
        for child in sorted(directory.iterdir(), key=lambda p: p.name):
            try:
                stat = child.stat()
            except OSError:
                continue
            entries.append({
                "name": child.name,
                "isDir": child.is_dir(),
                "sizeBytes": 0 if child.is_dir() else stat.st_size,
            })
        return entries
