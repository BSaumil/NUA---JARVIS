import wasmtime

from nua_companion.sandbox import WasmJob, WasmSandboxLimits, program_digest, run_wasm


def wasm(wat: str) -> bytes:
    return bytes(wasmtime.wat2wasm(wat))


def test_pure_wasm_job_runs_with_no_host_imports():
    program = wasm("""
        (module
          (func (export "run") (param i64) (result i64)
            local.get 0
            i64.const 2
            i64.mul))
    """)
    result = run_wasm(WasmJob(program, program_digest(program), input_value=21))
    assert result.succeeded
    assert result.result == 42
    assert result.fuel_remaining is not None


def test_imported_host_capability_is_refused():
    program = wasm("""
        (module
          (import "wasi_snapshot_preview1" "fd_write"
            (func $fd_write (param i32 i32 i32 i32) (result i32)))
          (func (export "run") (result i32)
            i32.const 0))
    """)
    result = run_wasm(WasmJob(program, program_digest(program)))
    assert not result.succeeded
    assert result.error_code == "IMPORTS_FORBIDDEN"


def test_digest_mismatch_is_refused_before_execution():
    program = wasm('(module (func (export "run") (result i32) i32.const 1))')
    result = run_wasm(WasmJob(program, "00" * 32))
    assert not result.succeeded
    assert result.error_code == "DIGEST_MISMATCH"


def test_infinite_loop_is_bounded_by_fuel_or_epoch():
    program = wasm("""
        (module
          (func (export "run")
            (loop $again
              i32.const 1
              drop
              br $again)))
    """)
    result = run_wasm(
        WasmJob(program, program_digest(program)),
        WasmSandboxLimits(fuel=10_000, memory_bytes=1024 * 1024, wall_time_ms=100),
    )
    assert not result.succeeded
    assert result.error_code in {"EXECUTION_TRAP", "EXECUTION_FAILURE"}
