"""Asserts the k8s ingress applies the same edge security headers as the nginx template.

Run: python3 ingress/test/test_k8s_security_headers.py
"""
import pathlib
import re
import sys
import unittest

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = ROOT / "gitops" / "base" / "ingress"
TEMPLATE = ROOT / "ingress" / "nginx.traderx.conf.template"
DOCKERFILE = ROOT / "ingress" / "Dockerfile"
REQUIRED = {
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "strict-origin-when-cross-origin",
}


def load_resources():
    kustomization = yaml.safe_load((BASE / "kustomization.yaml").read_text())
    docs = []
    for res in kustomization["resources"]:
        docs.extend(d for d in yaml.safe_load_all((BASE / res).read_text()) if d)
    return docs


class K8sIngressSecurityHeadersTest(unittest.TestCase):
    def setUp(self):
        docs = load_resources()
        self.ingress = next(d for d in docs if d["kind"] == "Ingress")
        self.configmaps = {d["metadata"]["name"]: d for d in docs if d["kind"] == "ConfigMap"}
        namespace = yaml.safe_load((ROOT / "gitops/local/traderx/kustomization.yaml").read_text())["namespace"]
        self.namespace = namespace

    def headers(self):
        ref = self.ingress["metadata"]["annotations"].get("nginx.ingress.kubernetes.io/custom-headers")
        self.assertIsNotNone(ref, "custom-headers annotation missing on traderx-ingress")
        ns, name = ref.split("/")
        self.assertEqual(ns, self.namespace)
        self.assertIn(name, self.configmaps, "custom-headers ConfigMap not part of the kustomization")
        return self.configmaps[name]["data"]

    def test_required_headers(self):
        data = self.headers()
        for header, value in REQUIRED.items():
            self.assertEqual(data.get(header), value)

    def test_csp(self):
        csp = self.headers()["Content-Security-Policy"]
        for directive in ("default-src 'self'", "script-src 'self';", "object-src 'none'", "frame-ancestors 'none'"):
            self.assertIn(directive, csp)
        self.assertNotIn("unsafe-eval", csp)

    def test_csp_matches_docker_compose_ingress(self):
        m = re.search(r'ARG NGINX_CSP="([^"]+)"', DOCKERFILE.read_text())
        self.assertIsNotNone(m)
        self.assertEqual(m.group(1), self.headers()["Content-Security-Policy"])
        self.assertIn("server_tokens off;", TEMPLATE.read_text())


if __name__ == "__main__":
    sys.exit(unittest.main())
