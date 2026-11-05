#!/usr/bin/env python3
"""Checks that every TraderX Radius container gets the hardened Kubernetes pod patch.

Run: python3 radius-traderx/tests/test_app_bicep.py
Compiles app.bicep with the Radius bicep binary ($BICEP, or ~/.rad/bin/bicep from `rad bicep download`).
"""
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import unittest

HERE = pathlib.Path(__file__).resolve().parent
APP = HERE.parent / "app.bicep"
APP_CONTAINERS = {
    "database", "reference-data", "trade-feed", "people-service", "account-service",
    "position-service", "trade-service", "trade-processor", "web-front-end-angular",
}


def find_bicep():
    for c in (os.environ.get("BICEP"), str(pathlib.Path.home() / ".rad/bin/bicep"), shutil.which("bicep")):
        if c and pathlib.Path(c).exists():
            return c
    return None


class RadiusHardening(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        bicep = find_bicep()
        if not bicep:
            raise unittest.SkipTest("Radius bicep not found; run `rad bicep download`")
        out = subprocess.run([bicep, "build", str(APP), "--stdout"], check=True,
                             capture_output=True, text=True).stdout
        cls.arm = json.loads(out)

    def resource(self, name):
        return next(r for r in self.arm["resources"].values() if r["properties"]["name"] == name)

    def test_every_app_container_is_patched_with_its_own_name(self):
        for name in APP_CONTAINERS:
            with self.subTest(container=name):
                runtimes = self.resource(name)["properties"]["properties"].get("runtimes", "")
                self.assertRegex(runtimes, r"^\[__bicep\.hardenedRuntime\('%s'," % re.escape(name))

    def test_patch_contents(self):
        fn = self.arm["functions"][0]["members"]["hardenedRuntime"]["output"]["value"]
        pod = fn["kubernetes"]["pod"]
        psc = pod["securityContext"]
        self.assertTrue(psc["runAsNonRoot"])
        self.assertGreater(psc["runAsUser"], 10000)
        self.assertEqual(psc["seccompProfile"]["type"], "RuntimeDefault")
        csc = pod["containers"][0]["securityContext"]
        self.assertIs(csc["allowPrivilegeEscalation"], False)
        self.assertIs(csc["readOnlyRootFilesystem"], True)
        self.assertEqual(csc["capabilities"]["drop"], ["ALL"])
        self.assertIn("/tmp", json.dumps(pod["volumes"]) + json.dumps(pod["containers"][0]["volumeMounts"]))

    def test_database_data_dir_is_writable(self):
        runtimes = self.resource("database")["properties"]["properties"]["runtimes"]
        self.assertIn("/database/_data", runtimes)


if __name__ == "__main__":
    sys.exit(not unittest.main(exit=False, verbosity=2).result.wasSuccessful())
