from __future__ import annotations

import hashlib
import threading
from dataclasses import dataclass

import wasmtime


@dataclass(frozen=True)
class WasmSandboxLimits:
    fuel: int = 2_000_000
    memory_bytes: int = 32 * 1024 * 1024
    wall_time_ms: int = 1_000


@dataclass(frozen=True)
class WasmJob:
    program: bytes
    expected_sha256: str
    export: str = "run"
    input_value: int | None = None


@dataclass(frozen=True)
class WasmResult:
    succeeded: bool
    result: int | float | None = None
    error_code: str | None = None
    fuel_remaining: int | None = None


def program_digest(program: bytes) -> str:
    return hashlib.sha256(program).hexdigest()


def run_wasm(job: WasmJob, limits: WasmSandboxLimits = WasmSandboxLimits()) -> WasmResult:
    if program_digest(job.program) != job.expected_sha256.lower():
        return WasmResult(False, error_code="DIGEST_MISMATCH")
    if not (1 <= limits.fuel <= 50_000_000):
        return WasmResult(False, error_code="INVALID_FUEL_LIMIT")
    if not (64 * 1024 <= limits.memory_bytes <= 128 * 1024 * 1024):
        return WasmResult(False, error_code="INVALID_MEMORY_LIMIT")
    if not (10 <= limits.wall_time_ms <= 10_000):
        return WasmResult(False, error_code="INVALID_TIME_LIMIT")

    config = wasmtime.Config()
    config.consume_fuel = True
    config.epoch_interruption = True
    engine = wasmtime.Engine(config)

    try:
        module = wasmtime.Module(engine, job.program)
    except Exception:
        return WasmResult(False, error_code="INVALID_MODULE")

    # No WASI and no host imports are linked. Any module requesting filesystem,
    # sockets, clocks, process/environment APIs, or custom host functions is refused.
    if module.imports:
        return WasmResult(False, error_code="IMPORTS_FORBIDDEN")

    store = wasmtime.Store(engine)
    store.set_limits(
        memory_size=limits.memory_bytes,
        table_elements=10_000,
        instances=2,
        tables=4,
        memories=2,
    )
    store.set_fuel(limits.fuel)
    store.set_epoch_deadline(1)

    timer = threading.Timer(limits.wall_time_ms / 1000.0, engine.increment_epoch)
    timer.daemon = True
    timer.start()
    try:
        instance = wasmtime.Instance(store, module, [])
        exported = instance.exports(store)
        try:
            function = exported[job.export]
        except KeyError:
            return WasmResult(False, error_code="EXPORT_NOT_FOUND")
        if not isinstance(function, wasmtime.Func):
            return WasmResult(False, error_code="EXPORT_NOT_FUNCTION")

        try:
            if job.input_value is None:
                value = function(store)
            else:
                value = function(store, job.input_value)
            if isinstance(value, tuple):
                if len(value) != 1:
                    return WasmResult(False, error_code="MULTI_RESULT_UNSUPPORTED")
                value = value[0]
            if value is not None and not isinstance(value, (int, float)):
                return WasmResult(False, error_code="RESULT_TYPE_UNSUPPORTED")
            return WasmResult(True, result=value, fuel_remaining=store.get_fuel())
        except wasmtime.Trap:
            return WasmResult(False, error_code="EXECUTION_TRAP", fuel_remaining=store.get_fuel())
        except Exception:
            return WasmResult(False, error_code="EXECUTION_FAILURE")
    finally:
        timer.cancel()
