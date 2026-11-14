from pathlib import Path

from nua_companion.mounts import MountRegistry


def _registry(tmp_path: Path) -> tuple[MountRegistry, Path]:
    root = tmp_path / "docs"
    root.mkdir()
    (root / "notes.txt").write_text("hello")
    sub = root / "sub"
    sub.mkdir()
    (sub / "nested.txt").write_text("nested")
    return MountRegistry.from_env(f"documents:{root}"), root


def test_a_configured_mount_resolves_its_root(tmp_path):
    registry, root = _registry(tmp_path)
    assert registry.resolve("documents") == root


def test_an_unconfigured_mount_name_resolves_to_nothing(tmp_path):
    registry, _ = _registry(tmp_path)
    assert registry.resolve("nonexistent-mount") is None


def test_a_relative_path_inside_the_mount_resolves(tmp_path):
    registry, root = _registry(tmp_path)
    assert registry.resolve("documents", "notes.txt") == root / "notes.txt"
    assert registry.resolve("documents", "sub/nested.txt") == root / "sub" / "nested.txt"


def test_a_parent_directory_escape_is_refused(tmp_path):
    registry, _ = _registry(tmp_path)
    assert registry.resolve("documents", "../../etc/passwd") is None
    assert registry.resolve("documents", "sub/../../etc/passwd") is None


def test_an_absolute_path_is_refused_even_if_it_names_a_real_file(tmp_path):
    registry, root = _registry(tmp_path)
    # An absolute path must never be honored, even one that happens to point
    # back inside the mount -- only paths relative to the mount root are ever
    # accepted, so a client can't smuggle in an absolute path to another
    # mount or the wider filesystem.
    assert registry.resolve("documents", str(root / "notes.txt")) is None


def test_a_symlink_that_escapes_the_mount_root_is_refused(tmp_path):
    registry, root = _registry(tmp_path)
    outside = tmp_path / "secret.txt"
    outside.write_text("do not read this")
    (root / "escape-link").symlink_to(outside)

    assert registry.resolve("documents", "escape-link") is None


def test_list_entries_reports_names_and_whether_each_is_a_directory(tmp_path):
    registry, _ = _registry(tmp_path)
    entries = registry.list_entries("documents")
    by_name = {entry["name"]: entry for entry in entries}
    assert by_name["notes.txt"]["isDir"] is False
    assert by_name["notes.txt"]["sizeBytes"] == len("hello")
    assert by_name["sub"]["isDir"] is True


def test_list_entries_on_an_unconfigured_mount_is_none(tmp_path):
    registry, _ = _registry(tmp_path)
    assert registry.list_entries("nonexistent-mount") is None


def test_a_mount_whose_root_does_not_exist_is_dropped_at_load_time(tmp_path):
    registry = MountRegistry.from_env(f"ghost:{tmp_path / 'does-not-exist'}")
    assert registry.names() == []
    assert registry.resolve("ghost") is None


def test_multiple_mounts_parse_from_one_env_value(tmp_path):
    first = tmp_path / "a"
    second = tmp_path / "b"
    first.mkdir()
    second.mkdir()
    registry = MountRegistry.from_env(f"a-mount:{first},b-mount:{second}")
    assert registry.names() == ["a-mount", "b-mount"]
