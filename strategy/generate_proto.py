"""
Helper script to compile Protocol Buffers for the Python Strategy Engine.

Compiles:
    ../proto/trading.proto  -> strategy/src/proto/trading_pb2.py
    ../proto/services.proto -> strategy/src/proto/services_pb2.py, services_pb2_grpc.py
"""

import sys
import os
import re
from pathlib import Path
from grpc_tools import protoc

def generate_protos():
    strategy_root = Path(__file__).resolve().parent
    repo_root = strategy_root.parent
    proto_dir = repo_root / "proto"
    output_dir = strategy_root / "src" / "proto"

    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "__init__.py").touch()

    proto_files = [
        str(proto_dir / "trading.proto"),
        str(proto_dir / "services.proto"),
    ]

    print(f"[PROTO] Compiling protos from: {proto_dir}")
    print(f"[PROTO] Output directory: {output_dir}")

    cmd = [
        "protoc",
        f"-I{proto_dir}",
        f"--python_out={output_dir}",
        f"--grpc_python_out={output_dir}",
        f"--pyi_out={output_dir}",
    ] + proto_files

    ret = protoc.main(cmd)
    if ret != 0:
        print(f"[ERROR] protoc failed with code {ret}", file=sys.stderr)
        sys.exit(ret)

    # Post-process generated files to fix relative imports for packages
    # (services_pb2.py needs `from . import trading_pb2` instead of `import trading_pb2`)
    for py_file in output_dir.glob("*_pb2*.py"):
        content = py_file.read_text(encoding="utf-8")
        fixed_content = re.sub(
            r"^import (trading_pb2|services_pb2) as",
            r"from . import \1 as",
            content,
            flags=re.MULTILINE
        )
        if content != fixed_content:
            py_file.write_text(fixed_content, encoding="utf-8")
            print(f"[PROTO] Patched imports in {py_file.name}")

    print("[PROTO] Compilation successful!")

if __name__ == "__main__":
    generate_protos()
