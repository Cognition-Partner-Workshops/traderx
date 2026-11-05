import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent
KEY = "TRADERX_REFERENCE_SECRET"
SERVICES = ("account-service", "position-service", "trade-service", "trade-processor")


class ReferenceConfigTest(unittest.TestCase):
    def compose(self, env):
        return subprocess.run(
            ["docker", "compose", "-p", "fs09-traderx", "config", "--format", "json"],
            cwd=ROOT, env=env, capture_output=True, text=True)

    def test_compose_forwards_one_shared_secret(self):
        env = dict(os.environ, **{KEY: secrets.token_hex(32)})
        result = self.compose(env)
        self.assertEqual(result.returncode, 0, result.stderr)
        services = json.loads(result.stdout)["services"]
        for service in SERVICES:
            self.assertTrue(services[service].get("environment", {}).get(KEY) == env[KEY],
                            service + " must receive the reference secret")

    def test_direct_compose_fails_closed(self):
        env = dict(os.environ)
        env.pop(KEY, None)
        self.assertNotEqual(self.compose(env).returncode, 0)
        env[KEY] = ""
        self.assertNotEqual(self.compose(env).returncode, 0)

    def test_kubernetes_uses_a_required_secret(self):
        for service in SERVICES:
            text = (ROOT / "gitops/base" / service / "deployment.yaml").read_text()
            self.assertRegex(text, r"name: TRADERX_REFERENCE_SECRET\s+valueFrom:\s+"
                             r"secretKeyRef:\s+name: traderx-reference\s+key: TRADERX_REFERENCE_SECRET")
            self.assertNotIn("optional: true", text)

    def test_local_runs_share_a_private_persistent_key_and_honor_overrides(self):
        with tempfile.TemporaryDirectory() as state:
            env = dict(os.environ, XDG_STATE_HOME=state)
            env.pop(KEY, None)
            command = [sys.executable, str(ROOT / "local-reference-secret.py"),
                       sys.executable, "-c",
                       "import os,hashlib; v=os.environ['" + KEY + "']; "
                       "assert len(v)>=32; print(hashlib.sha256(v.encode()).hexdigest())"]
            first = subprocess.run(command, env=env, capture_output=True, text=True)
            self.assertEqual(first.returncode, 0, first.stderr)
            second = subprocess.run(command, env=env, capture_output=True, text=True)
            self.assertEqual(first.stdout, second.stdout)
            self.assertEqual((Path(state) / "traderx/reference-secret").stat().st_mode & 0o777, 0o600)
            env[KEY] = secrets.token_hex(32)
            supplied = subprocess.run(command, env=env, capture_output=True, text=True)
            self.assertEqual(supplied.stdout.strip(), hashlib.sha256(env[KEY].encode()).hexdigest())
            rendered = subprocess.run(
                [sys.executable, str(ROOT / "local-reference-secret.py"), "--kubernetes"],
                env=env, capture_output=True, text=True)
            self.assertEqual(rendered.returncode, 0, rendered.stderr)
            secret = json.loads(rendered.stdout)
            self.assertEqual(secret["metadata"], {"name": "traderx-reference", "namespace": "traderx"})
            env[KEY] = "short"
            rejected = subprocess.run(command, env=env, capture_output=True, text=True)
            self.assertNotEqual(rejected.returncode, 0)

    def test_tilt_provisions_the_local_secret_without_logging_it(self):
        tilt = (ROOT / "gitops/local/Tiltfile").read_text()
        self.assertIn("local-reference-secret.py", tilt)
        self.assertIn("--kubernetes", tilt)
        self.assertIn("quiet=True", tilt)
        for service in SERVICES:
            self.assertIn("docker_build('traderx/" + service
                          + "', './../..', dockerfile='../../" + service + "/Dockerfile')", tilt)

    def test_empty_xdg_uses_home_instead_of_the_checkout(self):
        with tempfile.TemporaryDirectory() as home:
            env = dict(os.environ, HOME=home, XDG_STATE_HOME="")
            env.pop(KEY, None)
            result = subprocess.run(
                [sys.executable, str(ROOT / "local-reference-secret.py"),
                 sys.executable, "-c", "import os; assert len(os.environ['" + KEY + "'])>=32"],
                env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue((Path(home) / ".local/state/traderx/reference-secret").is_file())

    def test_generated_keys_cannot_be_written_into_the_checkout(self):
        env = dict(os.environ, XDG_STATE_HOME=str(ROOT))
        env.pop(KEY, None)
        result = subprocess.run(
            [sys.executable, str(ROOT / "local-reference-secret.py"), sys.executable, "-c", "pass"],
            env=env, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("outside the checkout", result.stderr)

    def test_java_images_include_the_schema_used_by_their_build_tests(self):
        for service in SERVICES:
            dockerfile = (ROOT / service / "Dockerfile").read_text()
            self.assertIn("COPY database/initialSchema.sql /database/initialSchema.sql", dockerfile)


if __name__ == "__main__":
    unittest.main()
