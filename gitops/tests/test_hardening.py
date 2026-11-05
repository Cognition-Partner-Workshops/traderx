#!/usr/bin/env python3
"""Static checks on the rendered gitops/local/traderx manifests.

Run: python3 gitops/tests/test_hardening.py   (needs kubectl and PyYAML)
"""
import pathlib
import subprocess
import sys
import unittest

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[2]
OVERLAY = ROOT / "gitops" / "local" / "traderx"

DEPLOYMENTS = {
    "account-service", "database-deployment", "people-service", "position-service",
    "reference-data", "trade-feed", "trade-processor-deployment", "trade-service",
    "web-front-end-angular",
}

# target app label -> (pod port, set of caller app labels)
CALL_GRAPH = {
    "database": (18082, {"account-service", "position-service", "trade-processor"}),
    "account-service": (18088, {"trade-service"}),
    "people-service": (18089, {"trade-service", "account-service"}),
    "reference-data": (18085, {"trade-service"}),
    "trade-feed": (18086, {"trade-service", "trade-processor"}),
}
INGRESS_EXPOSED = {
    "web-front-end-angular": 18093, "account-service": 18088, "people-service": 18089,
    "position-service": 18090, "reference-data": 18085, "trade-feed": 18086,
    "trade-service": 18092, "trade-processor": 18091,
}


def render():
    out = subprocess.run(["kubectl", "kustomize", str(OVERLAY)], check=True,
                         capture_output=True, text=True).stdout
    return [d for d in yaml.safe_load_all(out) if d]


DOCS = render()


def of_kind(kind):
    return [d for d in DOCS if d["kind"] == kind]


def container_ports(c):
    return {p["containerPort"] for p in c.get("ports", [])}


class DeploymentHardening(unittest.TestCase):
    def setUp(self):
        self.deps = {d["metadata"]["name"]: d for d in of_kind("Deployment")}

    def test_all_deployments_present(self):
        self.assertEqual(set(self.deps), DEPLOYMENTS)

    def test_f1_security_context(self):
        for name, d in self.deps.items():
            spec = d["spec"]["template"]["spec"]
            psc = spec.get("securityContext", {})
            with self.subTest(deployment=name):
                self.assertTrue(psc.get("runAsNonRoot"))
                self.assertGreater(psc.get("runAsUser", 0), 10000)
                self.assertGreater(psc.get("runAsGroup", 0), 10000)
                self.assertEqual(psc.get("seccompProfile", {}).get("type"), "RuntimeDefault")
                self.assertFalse(spec.get("automountServiceAccountToken", True))
                # Service links inject e.g. TRADE_PROCESSOR_SERVICE_PORT=8080, overriding the app's own port setting.
                self.assertIs(spec.get("enableServiceLinks"), False)
                volumes = {v["name"]: v for v in spec.get("volumes", [])}
                for c in spec["containers"] + spec.get("initContainers", []):
                    csc = c.get("securityContext", {})
                    self.assertIs(csc.get("allowPrivilegeEscalation"), False)
                    self.assertIs(csc.get("readOnlyRootFilesystem"), True)
                    self.assertFalse(csc.get("privileged", False))
                    self.assertEqual(csc.get("capabilities", {}).get("drop"), ["ALL"])
                    mounts = {m["mountPath"]: m["name"] for m in c.get("volumeMounts", [])}
                    self.assertIn("/tmp", mounts, "read-only rootfs needs a writable /tmp")
                    for vol in mounts.values():
                        self.assertIn("emptyDir", volumes[vol])

    def test_database_data_dir_writable(self):
        c = self.deps["database-deployment"]["spec"]["template"]["spec"]["containers"][0]
        self.assertIn("/database/_data", {m["mountPath"] for m in c["volumeMounts"]})

    def test_f2_resources(self):
        for name, d in self.deps.items():
            for c in d["spec"]["template"]["spec"]["containers"]:
                with self.subTest(deployment=name):
                    r = c.get("resources", {})
                    for k in ("requests", "limits"):
                        self.assertIn("cpu", r.get(k, {}))
                        self.assertIn("memory", r.get(k, {}))

    def test_f3_explicit_image_tags(self):
        for name, d in self.deps.items():
            for c in d["spec"]["template"]["spec"]["containers"]:
                with self.subTest(deployment=name):
                    image = c["image"]
                    last = image.rsplit("/", 1)[-1]
                    self.assertTrue(":" in last or "@sha256:" in image, image)
                    self.assertFalse(image.endswith(":latest"), image)

    def test_f4_namespace(self):
        for d in DOCS:
            if d["kind"] == "Namespace":
                continue
            with self.subTest(kind=d["kind"], name=d["metadata"]["name"]):
                self.assertEqual(d["metadata"].get("namespace"), "traderx")

    def test_f4_base_components_set_namespace(self):
        for k in (ROOT / "gitops" / "base").glob("*/kustomization.yaml"):
            with self.subTest(kustomization=str(k.relative_to(ROOT))):
                self.assertEqual(yaml.safe_load(k.read_text()).get("namespace"), "traderx")

    def test_f5_probes_target_a_declared_port(self):
        for name, d in self.deps.items():
            for c in d["spec"]["template"]["spec"]["containers"]:
                for probe in ("readinessProbe", "livenessProbe"):
                    with self.subTest(deployment=name, probe=probe):
                        self.assertIn(probe, c)
                        port = c[probe]["tcpSocket"]["port"]
                        self.assertIn(port, container_ports(c))

    def test_reference_data_listens_on_its_container_port(self):
        c = self.deps["reference-data"]["spec"]["template"]["spec"]["containers"][0]
        env = {e["name"]: e.get("value") for e in c.get("env", [])}
        self.assertIn(int(env["REFERENCE_DATA_SERVICE_PORT"]), container_ports(c))


class ServicePorts(unittest.TestCase):
    def test_services_expose_the_ports_callers_dial(self):
        # Callers build URLs as http://<service>:<container port> (e.g. trade-feed:18086).
        svcs = {s["metadata"]["name"]: {p["port"] for p in s["spec"]["ports"]} for s in of_kind("Service")}
        for app, (port, _) in CALL_GRAPH.items():
            with self.subTest(service=app):
                self.assertIn(port, svcs[app])


class NetworkPolicies(unittest.TestCase):
    def setUp(self):
        self.pols = of_kind("NetworkPolicy")

    def selects(self, pol, app):
        sel = pol["spec"]["podSelector"].get("matchLabels", {})
        return sel == {} or sel.get("app") == app

    def allowed_ingress(self, app, port):
        """Set of peers (app label, or 'ns:<name>') allowed to reach app:port."""
        peers = set()
        for p in self.pols:
            if "Ingress" not in p["spec"]["policyTypes"] or not self.selects(p, app):
                continue
            for rule in p["spec"].get("ingress", []):
                if rule.get("ports") and port not in {x["port"] for x in rule["ports"]}:
                    continue
                for f in rule.get("from", [{}]):
                    if "podSelector" in f and "namespaceSelector" not in f:
                        peers.add(f["podSelector"].get("matchLabels", {}).get("app", "*"))
                    elif "namespaceSelector" in f:
                        ns = f["namespaceSelector"].get("matchLabels", {})
                        peers.add("ns:" + ns.get("kubernetes.io/metadata.name", "*"))
                    else:
                        peers.add("*")
        return peers

    def allowed_egress(self, app):
        targets = set()
        for p in self.pols:
            if "Egress" not in p["spec"]["policyTypes"] or not self.selects(p, app):
                continue
            for rule in p["spec"].get("egress", []):
                for t in rule.get("to", [{}]):
                    if "namespaceSelector" in t:
                        continue
                    for port in rule.get("ports", [{"port": "*"}]):
                        targets.add((t.get("podSelector", {}).get("matchLabels", {}).get("app", "*"),
                                     port["port"]))
        return targets

    def test_f6_default_deny_all(self):
        self.assertTrue(any(
            p["spec"]["podSelector"] == {} and set(p["spec"]["policyTypes"]) == {"Ingress", "Egress"}
            and not p["spec"].get("ingress") and not p["spec"].get("egress")
            for p in self.pols))

    def test_f6_internal_ingress_matches_call_graph(self):
        for app, (port, callers) in CALL_GRAPH.items():
            with self.subTest(target=app):
                peers = self.allowed_ingress(app, port) - {"ns:ingress-nginx"}
                self.assertEqual(peers, callers)

    def test_f6_database_not_reachable_from_ingress_or_ui(self):
        for port in (18082, 18083, 18084):
            peers = self.allowed_ingress("database", port)
            self.assertNotIn("ns:ingress-nginx", peers)
            self.assertNotIn("web-front-end-angular", peers)
            self.assertNotIn("*", peers)

    def test_f6_ingress_controller_reaches_exposed_services(self):
        for app, port in INGRESS_EXPOSED.items():
            with self.subTest(app=app):
                self.assertIn("ns:ingress-nginx", self.allowed_ingress(app, port))

    def test_f6_egress_matches_call_graph(self):
        expected = {}
        for tgt, (port, callers) in CALL_GRAPH.items():
            for c in callers:
                expected.setdefault(c, set()).add((tgt, port))
        apps = {"database", "web-front-end-angular", "position-service", "trade-service",
                "trade-processor", "account-service", "people-service", "reference-data", "trade-feed"}
        for app in apps:
            with self.subTest(app=app):
                self.assertEqual(self.allowed_egress(app), expected.get(app, set()))


if __name__ == "__main__":
    sys.exit(not unittest.main(exit=False, verbosity=2).result.wasSuccessful())
