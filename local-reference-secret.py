#!/usr/bin/env python3
import base64
import json
import os
from pathlib import Path
import secrets
import stat
import sys
import tempfile


def reference_secret():
    value = os.environ.get("TRADERX_REFERENCE_SECRET")
    if not value:
        state = Path(os.environ.get("XDG_STATE_HOME") or Path.home() / ".local/state") / "traderx"
        state = state.expanduser().resolve()
        checkout = Path(__file__).resolve().parent
        if state == checkout or checkout in state.parents:
            raise SystemExit("Local reference-secret state must be outside the checkout")
        state.mkdir(parents=True, exist_ok=True, mode=0o700)
        path = state / "reference-secret"
        if not path.exists():
            with tempfile.NamedTemporaryFile(dir=state, delete=False) as file:
                temporary = Path(file.name)
                file.write(secrets.token_hex(32).encode())
            try:
                try:
                    os.link(temporary, path)
                except FileExistsError:
                    pass
            finally:
                temporary.unlink()
        mode = path.lstat().st_mode
        if not stat.S_ISREG(mode) or mode & 0o077:
            raise SystemExit("Local reference-secret must be a private regular file (mode 0600)")
        value = path.read_text().strip()
    if len(value) < 32:
        raise SystemExit("TRADERX_REFERENCE_SECRET must contain at least 32 characters")
    return value


def main():
    if len(sys.argv) < 2:
        raise SystemExit("Usage: python3 local-reference-secret.py COMMAND [ARGS...] | --kubernetes")
    value = reference_secret()
    if sys.argv[1:] == ["--kubernetes"]:
        print(json.dumps({
            "apiVersion": "v1", "kind": "Secret", "type": "Opaque",
            "metadata": {"name": "traderx-reference", "namespace": "traderx"},
            "data": {"TRADERX_REFERENCE_SECRET": base64.b64encode(value.encode()).decode()}
        }))
    else:
        os.environ["TRADERX_REFERENCE_SECRET"] = value
        os.execvpe(sys.argv[1], sys.argv[1:], os.environ)


if __name__ == "__main__":
    main()
