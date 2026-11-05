#!/usr/bin/env bash
set -uo pipefail

root=$(cd "${1:-$(dirname "${BASH_SOURCE[0]}")/..}" && pwd)
cd "$root" || exit 1
failures=0
projects=(trade-feed reference-data web-front-end/angular web-front-end/react)
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT

check() {
    local id=$1 description=$2
    shift 2
    if "$@"; then
        printf '%s PASS: %s\n' "$id" "$description"
    else
        printf '%s FAIL: %s\n' "$id" "$description"
        failures=$((failures + 1))
    fi
}

tracked_lock() {
    test -f "$1/package-lock.json" &&
        git ls-files --error-unmatch "$1/package-lock.json" >/dev/null 2>&1
}

docker_ci() {
    node - "$1/Dockerfile" <<'NODE'
const fs = require('fs');
const lines = fs.readFileSync(process.argv[2], 'utf8').split(/\r?\n/);
process.exit(lines.some(line => /^RUN\s+npm\s+ci\s*$/.test(line)) &&
    !lines.some(line => /^RUN\s+.*\bnpm\s+install\b/.test(line)) ? 0 : 1);
NODE
}

versions() {
    node - "$1" "$2" "$3" <<'NODE'
const fs = require('fs');
const [project, minimum, names] = process.argv.slice(2);
try {
    const lock = JSON.parse(fs.readFileSync(`${project}/package-lock.json`, 'utf8'));
    for (const name of names.split(',')) {
        const version = lock.packages?.[`node_modules/${name}`]?.version;
        if (!version || version.includes('-')) throw new Error(`${name}: missing or prerelease`);
        const actual = version.split('.').map(Number);
        const expected = minimum.split('.').map(Number);
        let comparison = 0;
        for (let i = 0; i < 3 && comparison === 0; i++) comparison = actual[i] - expected[i];
        if (!(comparison >= 0)) throw new Error(`${name}@${version} < ${minimum}`);
    }
} catch (error) {
    console.error(error.message);
    process.exit(1);
}
NODE
}

dependency_hygiene() {
    node <<'NODE'
const fs = require('fs');
const angular = JSON.parse(fs.readFileSync('web-front-end/angular/package.json', 'utf8'));
const react = JSON.parse(fs.readFileSync('web-front-end/react/package.json', 'utf8'));
const assert = (condition, message) => { if (!condition) throw new Error(message); };
try {
    for (const name of ['protractor', 'protractor-jasmine2-html-reporter', 'codelyzer', 'puppeteer', 'selenium-server-standalone-jar']) {
        for (const section of ['dependencies', 'devDependencies', 'optionalDependencies']) {
            assert(!angular[section]?.[name], `Angular still declares ${name}`);
        }
    }
    const faker = angular.devDependencies?.['@faker-js/faker'];
    assert(faker && Number(faker.match(/\d+/)?.[0]) >= 8, 'Faker must be >=8 in devDependencies');
    assert(!angular.dependencies?.['@faker-js/faker'], 'Faker must not be a runtime dependency');
    const testPackages = ['@testing-library/jest-dom', '@testing-library/react', '@testing-library/user-event', '@types/jest'];
    for (const name of testPackages) assert(react.devDependencies?.[name], `${name} missing from devDependencies`);
    for (const section of ['dependencies', 'optionalDependencies']) {
        for (const name of Object.keys(react[section] || {})) {
            assert(!name.startsWith('@testing-library/') && name !== '@types/jest', `${name} must be dev-only`);
        }
    }
} catch (error) {
    console.error(error.message);
    process.exit(1);
}
NODE
}

reference_ci() {
    local project="$root/reference-data" copy="$scratch/reference-data"
    if [[ $(cd "$project" && npm config get legacy-peer-deps) != false ]]; then
        echo 'legacy-peer-deps must be disabled'
        return 1
    fi
    if ! node - "$root" <<'NODE'
const fs = require('fs');
const path = require('path');
const root = process.argv[2];
const files = [path.join(root, 'reference-data', '.npmrc')];
for (let directory = root; ; directory = path.dirname(directory)) {
    files.push(path.join(directory, '.npmrc'));
    if (directory === path.dirname(directory)) break;
}
for (const file of files) {
    if (fs.existsSync(file) && /^\s*legacy-peer-deps\s*=\s*(true|1)\s*(?:[;#].*)?$/im.test(fs.readFileSync(file, 'utf8'))) {
        console.error(`${file} enables legacy-peer-deps`);
        process.exit(1);
    }
}
NODE
    then
        return 1
    fi
    mkdir -p "$copy"
    cp "$project/package.json" "$project/package-lock.json" "$copy/" || return 1
    if [[ -f "$project/.npmrc" ]]; then cp "$project/.npmrc" "$copy/"; fi
    (cd "$copy" && npm ci)
}

for project in "${projects[@]}"; do
    check G2-7 "$project has a tracked lockfile" tracked_lock "$project"
done
for project in trade-feed reference-data web-front-end/angular; do
    check G2-7 "$project Dockerfile uses npm ci" docker_ci "$project"
done
check G2-8 'reference-data clean npm ci without legacy-peer-deps' reference_ci
check G2-1 'Angular runtime packages >=20.3.32' versions web-front-end/angular 20.3.32 '@angular/core,@angular/common,@angular/compiler,@angular/router'
check G2-4 'React AG Grid packages >=31.3.4' versions web-front-end/react 31.3.4 'ag-grid-community,ag-grid-react'
for project in web-front-end/angular web-front-end/react; do
    check G2-6 "$project Socket.IO client >=4.8.0" versions "$project" 4.8.0 socket.io-client
done
check G2-3 'obsolete Angular tooling removed and test dependencies dev-only' dependency_hygiene
for project in "${projects[@]}"; do
    case "$project" in
        web-front-end/angular) id=G2-2 ;;
        web-front-end/react) id=G2-5 ;;
        reference-data) id=G2-8 ;;
        trade-feed) id=G2-6 ;;
    esac
    check "$id" "$project audit has no critical/high findings" \
        npm --prefix "$project" audit --audit-level=high --package-lock-only
done
printf 'Supply-chain regression: %s failed checks\n' "$failures"
((failures == 0))
