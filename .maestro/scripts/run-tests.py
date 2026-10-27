#!/usr/bin/env python3
"""Run isolated Maestro flows. This module uses only the Python standard library."""

import argparse
import datetime
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[2]
APP_ID = "com.project200.undabang"
COMPONENT = APP_ID + "/com.project200.undabang.e2e.MaestroSetupActivity"
REQUEST = "files/maestro-request.json"
RESULT = "files/maestro-result.json"
SESSION = "files/maestro-session.json"
RECOVERY = "files/maestro-feature-recovery.json"
MAESTRO_VERSION = "2.11.0"
FLOWS = {
    "smoke": ("smoke-tabs", "login-screen"),
    "regression": ("exercise-form-entry", "exercise-crud"),
    "all": ("smoke-tabs", "exercise-form-entry", "exercise-crud", "login-screen"),
    "login": ("login-screen",),
}
TOOL_ENV_KEYS = (
    "PATH", "HOME", "USER", "LOGNAME", "JAVA_HOME", "ANDROID_HOME",
    "ANDROID_SDK_ROOT", "ANDROID_ADB_SERVER_PORT", "ADB_SERVER_SOCKET",
    "ADB_VENDOR_KEYS", "TMPDIR", "TMP", "TEMP", "SYSTEMROOT",
)
FEATURE_NAME = re.compile(r"[a-z][a-z0-9-]*")
FLOW_NAME = re.compile(r"[a-z][a-z0-9-]*")
VARIABLE_NAME = re.compile(r"[A-Z][A-Z0-9_]*")
REQUIRED_ENVIRONMENT = frozenset({
    "MAESTRO_PEER_AUTH_STATE_JSON", "MAESTRO_PEER_MEMBER_ID",
    "MAESTRO_PEER_FIXTURE_START_DATE", "MAESTRO_CHAT_ROOM_ID",
    "MAESTRO_MATCHING_LEFT_TOP_LATITUDE", "MAESTRO_MATCHING_LEFT_TOP_LONGITUDE",
    "MAESTRO_MATCHING_RIGHT_BOTTOM_LATITUDE", "MAESTRO_MATCHING_RIGHT_BOTTOM_LONGITUDE",
    "MAESTRO_MATCHING_SEARCH_QUERY", "MAESTRO_MATCHING_CATALOG_PLACE_NAME",
    "MAESTRO_MATCHING_ROOM_ID",
})
COMMON_PREPARE_FAILURES = {
    "discovery_failed": "bootstrap-prepare-failed-discovery",
    "refresh_failed": "bootstrap-prepare-failed-refresh",
    "registration_failed": "bootstrap-prepare-failed-registration",
    "fixture_read_failed": "bootstrap-prepare-failed-fixture-read",
    "fixture_delete_failed": "bootstrap-prepare-failed-fixture-delete",
    "setup_failed": "bootstrap-prepare-failed-setup",
}

PROFILE_PREPARE_FAILURES = {
    "profile_recovery_pending": "bootstrap-prepare-failed-profile-recovery-pending",
    "profile_fixture_read_failed": "bootstrap-prepare-failed-profile-fixture-read-failed",
    "profile_baseline_nickname_unsupported": "bootstrap-prepare-failed-profile-baseline-nickname-unsupported",
    "profile_baseline_gender_unsupported": "bootstrap-prepare-failed-profile-baseline-gender-unsupported",
    "profile_baseline_bio_null": "bootstrap-prepare-failed-profile-baseline-bio-null",
    "profile_baseline_bio_too_long": "bootstrap-prepare-failed-profile-baseline-bio-too-long",
    "profile_baseline_bio_control_characters":
        "bootstrap-prepare-failed-profile-baseline-bio-control-characters",
    "profile_preferences_invalid": "bootstrap-prepare-failed-profile-preferences-invalid",
    "profile_preference_baseline_unavailable":
        "bootstrap-prepare-failed-profile-preference-baseline-unavailable",
    "profile_catalog_invalid": "bootstrap-prepare-failed-profile-catalog-invalid",
    "profile_unselected_exercise_missing": "bootstrap-prepare-failed-profile-unselected-exercise-missing",
    "profile_fcm_token_timeout": "bootstrap-prepare-failed-profile-fcm-token-timeout",
    "profile_fcm_registration_failed": "bootstrap-prepare-failed-profile-fcm-registration-failed",
    "profile_fcm_registration_timeout": "bootstrap-prepare-failed-profile-fcm-registration-timeout",
    "profile_fcm_settings_timeout": "bootstrap-prepare-failed-profile-fcm-settings-timeout",
    "profile_notifications_invalid": "bootstrap-prepare-failed-profile-notifications-invalid",
    "profile_fcm_storage_failed": "bootstrap-prepare-failed-profile-fcm-storage-failed",
    "profile_fcm_identity_changed": "bootstrap-prepare-failed-profile-fcm-identity-changed",
    "profile_snapshot_write_failed": "bootstrap-prepare-failed-profile-snapshot-write-failed",
    "profile_fixture_write_failed": "bootstrap-prepare-failed-profile-fixture-write-failed",
    "profile_notification_prepare_failed": "bootstrap-prepare-failed-profile-notification-prepare-failed",
    "profile_request_invalid": "bootstrap-prepare-failed-profile-request-invalid",
}

MATCHING_PREPARE_FAILURES = {
    "matching_recovery_required": "bootstrap-prepare-failed-matching-recovery-required",
    "matching_peer_must_differ": "bootstrap-prepare-failed-matching-peer-must-differ",
    "matching_invalid_bounds": "bootstrap-prepare-failed-matching-invalid-bounds",
    "matching_peer_unavailable": "bootstrap-prepare-failed-matching-peer-unavailable",
    "matching_peer_identity_changed": "bootstrap-prepare-failed-matching-peer-identity-changed",
    "matching_peer_location_unavailable":
        "bootstrap-prepare-failed-matching-peer-location-unavailable",
    "matching_search_query_requires_ascii":
        "bootstrap-prepare-failed-matching-search-query-requires-ascii",
    "matching_catalog_result_changed":
        "bootstrap-prepare-failed-matching-catalog-result-changed",
    "matching_catalog_result_unavailable":
        "bootstrap-prepare-failed-matching-catalog-result-unavailable",
    "matching_catalog_value_invalid":
        "bootstrap-prepare-failed-matching-catalog-value-invalid",
    "matching_place_capacity_or_identity_invalid":
        "bootstrap-prepare-failed-matching-place-capacity-or-identity-invalid",
    "matching_room_invalid": "bootstrap-prepare-failed-matching-room-invalid",
    "matching_existing_peer_room_required":
        "bootstrap-prepare-failed-matching-existing-peer-room-required",
    "matching_marker_collision": "bootstrap-prepare-failed-matching-marker-collision",
    "matching_required_input_missing":
        "bootstrap-prepare-failed-matching-required-input-missing",
    "matching_api_read_failed": "bootstrap-prepare-failed-matching-api-read-failed",
    "matching_private_file_failed":
        "bootstrap-prepare-failed-matching-private-file-failed",
    "matching_request_invalid": "bootstrap-prepare-failed-matching-request-invalid",
}


class RunnerFailure(Exception):
    """Only fixed, non-secret messages may cross the command-line boundary."""


class RunInterrupted(RunnerFailure):
    pass


def tool_environment():
    # 셸/CI 비밀과 기존 MAESTRO_* 설정을 도구 프로세스로 상속하지 않습니다.
    env = {key: os.environ[key] for key in TOOL_ENV_KEYS if key in os.environ}
    env.update({"LANG": "C.UTF-8", "LC_ALL": "C.UTF-8", "TZ": "Asia/Seoul"})
    return env


def command(argv, env, timeout=60, payload=None):
    process = None
    try:
        process = subprocess.Popen(
            argv, cwd=ROOT, env=env, stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True,
        )
        stdout, stderr = process.communicate(input=payload, timeout=timeout)
        return process.returncode, stdout, stderr
    except subprocess.TimeoutExpired:
        if process is not None:
            os.killpg(process.pid, signal.SIGKILL)
            process.communicate()
        raise RunnerFailure("tool-timeout") from None
    except (KeyboardInterrupt, RunInterrupted):
        if process is not None:
            os.killpg(process.pid, signal.SIGKILL)
            process.communicate()
        raise RunInterrupted("run-interrupted") from None
    except OSError:
        raise RunnerFailure("tool-unavailable") from None


def read_credentials(prefix="MAESTRO_"):
    if prefix not in ("MAESTRO_", "MAESTRO_PEER_"):
        raise RunnerFailure("invalid-credentials-prefix")
    keys = tuple(prefix + name for name in ("AUTH_STATE_JSON", "MEMBER_ID", "FIXTURE_START_DATE"))
    missing = [key for key in keys if not os.environ.get(key, "").strip()]
    if missing:
        raise RunnerFailure("missing-required-session-secrets: " + ", ".join(missing))
    try:
        auth = json.loads(os.environ[keys[0]])
        start = datetime.date.fromisoformat(os.environ[keys[2]])
    except (ValueError, TypeError):
        raise RunnerFailure("invalid-session-secrets") from None
    if not isinstance(auth, dict) or not auth:
        raise RunnerFailure("invalid-session-secrets")
    if start.isoformat() != os.environ[keys[2]] or start > datetime.datetime.now(
        datetime.timezone(datetime.timedelta(hours=9))
    ).date():
        raise RunnerFailure("invalid-fixture-start-date")
    return {"authState": auth, "memberId": os.environ[keys[1]], "fixtureStartDate": start.isoformat()}


def discover_suites():
    directory = ROOT / ".maestro" / "suites"
    descriptors = {}
    if not directory.exists():
        return descriptors
    if directory.is_symlink() or not directory.is_dir():
        raise RunnerFailure("invalid-suite-directory")
    owned_flows = set(flow for flows in FLOWS.values() for flow in flows)
    for path in sorted(directory.iterdir()):
        if path.suffix != ".json":
            raise RunnerFailure("invalid-suite-descriptor-file")
        name = path.stem
        if (not FEATURE_NAME.fullmatch(name) or name in {*FLOWS, "ci"}
                or path.is_symlink() or not path.is_file()):
            raise RunnerFailure("invalid-suite-descriptor")
        try:
            descriptor = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            raise RunnerFailure("invalid-suite-descriptor") from None
        if not isinstance(descriptor, dict) or set(descriptor) != {
            "flows", "apiLevel", "fixture", "requiredEnvironment", "driver",
        }:
            raise RunnerFailure("invalid-suite-descriptor")
        flows = descriptor["flows"]
        environment = descriptor["requiredEnvironment"]
        driver = descriptor["driver"]
        if (not isinstance(flows, list) or not flows
                or any(not isinstance(flow, str) or not FLOW_NAME.fullmatch(flow) for flow in flows)
                or len(set(flows)) != len(flows) or owned_flows.intersection(flows)
                or type(descriptor["apiLevel"]) is not int or not 30 <= descriptor["apiLevel"] <= 99
                or type(descriptor["fixture"]) is not bool
                or not isinstance(environment, list)
                or any(not isinstance(key, str) or key not in REQUIRED_ENVIRONMENT for key in environment)
                or len(set(environment)) != len(environment)
                or (driver is not None and driver != name)):
            raise RunnerFailure("invalid-suite-descriptor")
        for flow in flows:
            flow_path = ROOT / ".maestro" / "flows" / (flow + ".yaml")
            if flow_path.is_symlink() or not flow_path.is_file():
                raise RunnerFailure("selected-flow-file-missing")
        module = ROOT / ".maestro" / "scripts" / "features" / (name + ".py")
        if module.is_symlink() or (driver is not None and not module.is_file()):
            raise RunnerFailure("invalid-feature-module")
        owned_flows.update(flows)
        descriptors[name] = {"name": name, **descriptor}
    return descriptors


def builtin_suite(flows):
    return {"name": None, "flows": list(flows), "apiLevel": 30, "fixture": False,
            "requiredEnvironment": [], "driver": None}


def select_suites(name):
    descriptors = discover_suites()
    if name in ("ci", "all"):
        suites = [builtin_suite(("smoke-tabs",))]
        if name == "all":
            suites.append(builtin_suite(FLOWS["regression"]))
        suites.extend(
            descriptor for feature, descriptor in descriptors.items()
            if name != "ci" or feature != "chatting"
        )
        suites.append(builtin_suite(("login-screen",)))
    elif name in FLOWS:
        suites = [builtin_suite(FLOWS[name])]
    elif name in descriptors:
        suites = [descriptors[name]]
    else:
        raise RunnerFailure("unknown-selected-suite")
    for flow in selected_flows(suites):
        path = ROOT / ".maestro" / "flows" / (flow + ".yaml")
        if path.is_symlink() or not path.is_file():
            raise RunnerFailure("selected-flow-file-missing")
    return suites


def selected_flows(suites):
    return [flow for suite in suites for flow in suite["flows"]]


def required_api(suites):
    return max(suite["apiLevel"] for suite in suites)


def read_required_environment(suites):
    required = sorted({key for suite in suites for key in suite["requiredEnvironment"]})
    missing = [key for key in required if not os.environ.get(key, "").strip()]
    if missing:
        raise RunnerFailure("missing-required-environment: " + ", ".join(missing))
    values = {key: os.environ[key] for key in required}
    if "MAESTRO_PEER_AUTH_STATE_JSON" in values:
        read_credentials("MAESTRO_PEER_")
    return values


def safe_variables(variables):
    if not isinstance(variables, dict):
        raise RunnerFailure("invalid-feature-variables")
    for key, value in variables.items():
        if (not isinstance(key, str) or not VARIABLE_NAME.fullmatch(key)
                or re.search(r"AUTH|TOKEN|FCM|SECRET|PASSWORD|SESSION", key)
                or not isinstance(value, str) or len(value) > 4096
                or any(ord(character) < 32 for character in value)
                or re.search(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.", value)):
            raise RunnerFailure("invalid-feature-variables")
    return dict(variables)


def feature_metadata(operation, feature, flow, run_id, parameters):
    if all(value is None for value in (feature, flow, run_id, parameters)):
        return {}
    if (operation not in ("prepare", "cleanup", "feature-observe")
            or not isinstance(feature, str) or not FEATURE_NAME.fullmatch(feature)
            or not isinstance(flow, str) or not FLOW_NAME.fullmatch(flow)
            or not isinstance(run_id, str) or not re.fullmatch(r"[a-f0-9]{16,32}", run_id)
            or not isinstance(parameters, dict)
            or any(not isinstance(key, str) or not isinstance(value, str) for key, value in parameters.items())):
        raise RunnerFailure("invalid-feature-metadata")
    return {"feature": feature, "flow": flow, "runId": run_id, "parameters": parameters}


class Redactor:
    def __init__(self, credentials):
        values = []

        def collect(value):
            if isinstance(value, dict):
                for item in value.values():
                    collect(item)
            elif isinstance(value, list):
                for item in value:
                    collect(item)
            elif isinstance(value, str) and value:
                values.extend((value, json.dumps(value, ensure_ascii=False)[1:-1]))

        collect(credentials or {})
        self.values = sorted(set(values), key=len, reverse=True)

    def clean(self, text):
        original = text
        for value in self.values:
            text = text.replace(value, "[REDACTED]")
        text = re.sub(r"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b", "[REDACTED]", text)
        # 갱신된 토큰도 필드명/헤더가 있는 줄 전체를 제거해 원본 토큰 목록에 의존하지 않습니다.
        sensitive = text != original
        lines = []
        for line in text.splitlines():
            if re.search(
                r"authorization|bearer\s|(?:access|refresh|id)[_-]?token|authState|maestro-session|maestro-request",
                line, re.IGNORECASE,
            ):
                lines.append("[REDACTED sensitive line]")
                sensitive = True
            else:
                lines.append(line)
        text = "\n".join(lines)
        text = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", text)
        return text, sensitive


ANDROID_XML = "{http://schemas.android.com/apk/res/android}"


def ime_diagnostic(stage, **details):
    # 공개 IME 식별자·검증된 qualifier·SDK·고정 분류·개수만 전달하며 원문/경로/환경/stderr는 제외합니다.
    print("IME metadata: " + json.dumps({"stage": stage, **details}, sort_keys=True), file=sys.stderr)


def ime_attribute_names(nodes):
    names = set()
    allowed = {
        "name", "resource", "imeSubtypeLocale", "imeSubtypeMode", "languageTag",
        "isAsciiCapable", "subtypeId", "isAuxiliary", "overridesImplicitlyEnabledSubtype",
    }
    for node in nodes:
        for key in node.attrib:
            local = key.rsplit("}", 1)[-1].rsplit(":", 1)[-1]
            if local not in allowed:
                continue
            form = (
                "expanded" if key.startswith(ANDROID_XML) else
                "android-prefix" if key.startswith("android:") else
                "bare" if key == local else "other-namespace"
            )
            names.add(form + ":" + local)
    return sorted(names)


def aapt_xml(output, document="metadata"):
    # AAPT2 Debug::DumpXml의 들여쓰기와 공개 Android 속성만 읽습니다.
    root = None
    stack = []
    for line in output.splitlines():
        element = re.fullmatch(r"(\s*)E: ([\w-]+) \(line=\d+\)", line)
        if element:
            depth = len(element[1])
            while stack and stack[-1][0] >= depth:
                stack.pop()
            node = ET.Element(element[2])
            if stack:
                stack[-1][1].append(node)
            elif root is None:
                root = node
            else:
                ime_diagnostic("xml", document=document, status="multiple-roots")
                raise RunnerFailure("emulator-ime-metadata-unavailable")
            stack.append((depth, node))
        elif line.lstrip().startswith("A: "):
            attribute = re.fullmatch(r"\s*A: (.+?)(?:\(0x[0-9a-fA-F]+\))?=(.*)", line)
            if not attribute or not stack:
                ime_diagnostic("xml", document=document, status="invalid-attribute")
                raise RunnerFailure("emulator-ime-metadata-unavailable")
            name, value = attribute[1], attribute[2].split(' (Raw: "', 1)[0]
            namespace = "http://schemas.android.com/apk/res/android:"
            if name.startswith(namespace):
                name = ANDROID_XML + name[len(namespace):]
            if value.startswith('"') and value.endswith('"'):
                value = value[1:-1]
            stack[-1][1].set(name, value)
    if root is None:
        ime_diagnostic("xml", document=document, status="missing-root")
        raise RunnerFailure("emulator-ime-metadata-unavailable")
    ime_diagnostic(
        "xml", document=document, status="parsed",
        root=root.tag if root.tag in ("manifest", "input-method") else "other",
        android_attribute_names=ime_attribute_names(root.iter()),
    )
    return root


def ime_resource_id(manifest, ime):
    package, service = ime.split("/", 1)
    service = package + service if service.startswith(".") else service
    resources = []
    services = manifest.findall("./application/service")
    matching_services = 0
    if manifest.tag == "manifest" and manifest.get("package") == package:
        for node in services:
            name = node.get(ANDROID_XML + "name", "")
            name = package + name if name.startswith(".") else name
            if name == service:
                matching_services += 1
                resources.extend(
                    item.get(ANDROID_XML + "resource", "")
                    for item in node.findall("meta-data")
                    if item.get(ANDROID_XML + "name") == "android.view.im"
                )
    ime_diagnostic(
        "ime-resource", package_matches=manifest.get("package") == package,
        selected_service=service if re.fullmatch(r"[A-Za-z0-9_.$]+", service) else "invalid",
        service_count=len(services), matching_service_count=matching_services,
        service_attribute_names=ime_attribute_names(services),
        metadata_attribute_names=ime_attribute_names(
            item for node in services for item in node.findall("meta-data")
        ),
        resource_count=len(resources),
        numeric_resource_ids=[item for item in resources if re.fullmatch(r"@0x[0-9a-fA-F]{8}", item)],
    )
    if len(resources) != 1 or not re.fullmatch(r"@0x[0-9a-fA-F]{8}", resources[0]):
        raise RunnerFailure("emulator-ime-metadata-unavailable")
    return resources[0][1:]


def ime_resource_path(output, resource_id, sdk_level):
    selected = False
    variants = []
    resource_matches = 0
    for line in output.splitlines():
        resource = re.match(r"\s*resource (0x[0-9a-fA-F]{8}) \S+", line)
        if resource:
            selected = resource[1].lower() == resource_id.lower()
            resource_matches += selected
        elif selected:
            variant = re.match(r"\s*\(([^)]*)\) \(file\) (\S+) type=XML\b", line)
            if variant:
                variants.append((variant[1], variant[2]))
    configs = [config for config, _ in variants]
    version_only = all(not config or re.fullmatch(r"v[0-9]+", config) for config in configs)
    rule = "version" if version_only else "all-variants-agree"
    if len(variants) == 1 and not configs[0]:
        rule = "single"
    ime_diagnostic(
        "resource-path", selected_resource=resource_id if re.fullmatch(r"0x[0-9a-fA-F]{8}", resource_id) else "invalid",
        matching_resource_count=resource_matches, path_count=len(variants),
        configs=sorted(config if re.fullmatch(r"[A-Za-z0-9+-]*", config) else "invalid" for config in configs),
        sdk_level=sdk_level, rule=rule,
    )
    if resource_matches != 1 or not variants or len(configs) != len(set(configs)):
        raise RunnerFailure("emulator-ime-metadata-unavailable")
    if version_only:
        selected_path = None
        selected_version = -1
        for config, path in variants:
            version = int(config[1:]) if config else 0
            if selected_version < version <= sdk_level:
                selected_path = path
                selected_version = version
        if selected_path is None:
            raise RunnerFailure("emulator-ime-metadata-unavailable")
        return [selected_path]
    # 버전 외 qualifier는 해석하지 않고 모든 XML의 subtype ID가 같은지 호출부에서 확인합니다.
    return [path for _, path in variants]


def java_string_hash(value):
    # Java String은 Unicode code point가 아니라 UTF-16 code unit으로 int 연산합니다.
    utf16 = value.encode("utf-16-be", errors="surrogatepass")
    result = 0
    for index in range(0, len(utf16), 2):
        result = (31 * result + (utf16[index] << 8 | utf16[index + 1])) & 0xffffffff
    return result - 0x100000000 if result >= 0x80000000 else result


def android_subtype_id(attributes):
    value = attributes.get("subtypeId", "0")
    subtype = int(value, 16 if value.startswith("0x") else 10)
    if not -0x80000000 <= subtype <= 0xffffffff:
        raise ValueError("invalid-subtype-id")
    if subtype >= 0x80000000:
        subtype -= 0x100000000
    if subtype != 0:
        return subtype

    # InputMethodSubtype.hashCodeInternal: ID 생략/0은 원래 locale·mode·extra로 계산합니다.
    strings = [
        attributes.get(name, "")
        for name in ("imeSubtypeLocale", "imeSubtypeMode", "imeSubtypeExtraValue")
    ]
    if any(value.startswith(("@", "?")) for value in strings):
        # Android getString의 resource 참조는 해석하지 못하므로 ID를 추측하지 않습니다.
        raise ValueError("unresolved-subtype-string")
    values = [java_string_hash(value) for value in strings]
    values.extend(
        1231 if attributes.get(name, "false") == "true" else 1237
        for name in ("isAuxiliary", "overridesImplicitlyEnabledSubtype")
    )
    # ASCII 미지원 subtype의 기존 5항 hash는 Android와 동일하게 보존합니다.
    if attributes.get("isAsciiCapable", "false") == "true":
        values.append(1231)
    result = 1
    for value in values:
        result = (31 * result + value) & 0xffffffff
    return result - 0x100000000 if result >= 0x80000000 else result


def ascii_ime_subtype(metadata):
    candidates = []
    if metadata.tag == "input-method":
        for node in metadata.findall("subtype"):
            attributes = {key.removeprefix(ANDROID_XML): value for key, value in node.attrib.items()}
            locale = attributes.get("languageTag") or attributes.get("imeSubtypeLocale", "")
            if (locale.replace("_", "-").lower() != "en-us"
                    or attributes.get("imeSubtypeMode") != "keyboard"
                    or attributes.get("isAsciiCapable") != "true"
                    or attributes.get("isAuxiliary", "false") != "false"
                    or attributes.get("overridesImplicitlyEnabledSubtype", "false") != "false"):
                continue
            try:
                subtype = android_subtype_id(attributes)
            except ValueError:
                continue
            # -1은 설정의 자동 선택 sentinel이므로 구체적인 subtype으로 선택할 수 없습니다.
            if subtype != -1:
                candidates.append(subtype)
    ime_diagnostic(
        "ascii-subtype", root=metadata.tag if metadata.tag == "input-method" else "other",
        subtype_count=len(metadata.findall("subtype")), candidate_count=len(candidates),
    )
    if len(candidates) != 1:
        raise RunnerFailure("emulator-ascii-subtype-unavailable")
    return candidates[0]


def installed_ascii_subtype(device):
    ime = device.require("shell", "settings", "get", "secure", "default_input_method").decode().strip()
    valid_ime = re.fullmatch(r"[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+", ime) is not None
    ime_diagnostic("default-ime", valid=valid_ime, component=ime if valid_ime else "invalid")
    if not valid_ime:
        raise RunnerFailure("emulator-ime-metadata-unavailable")
    aapt = shutil.which("aapt2")
    tool_source = "path" if aapt else "unavailable"
    if not aapt:
        sdk = device.env.get("ANDROID_HOME") or device.env.get("ANDROID_SDK_ROOT")
        candidates = sorted((Path(sdk) / "build-tools").glob("*/aapt2")) if sdk else []
        aapt = next((str(path) for path in reversed(candidates) if os.access(path, os.X_OK)), None)
        tool_source = "sdk" if aapt else "unavailable"
    ime_diagnostic("aapt-tool", source=tool_source)
    if not aapt:
        raise RunnerFailure("emulator-ascii-input-requires-aapt2")
    paths = device.require("shell", "pm", "path", ime.split("/", 1)[0]).decode().splitlines()
    valid_apk = len(paths) == 1 and paths[0].startswith("package:/") and paths[0].endswith(".apk")
    ime_diagnostic("apk-paths", count=len(paths), valid_single_apk=valid_apk)
    if not valid_apk:
        raise RunnerFailure("emulator-ime-metadata-unavailable")
    with tempfile.TemporaryDirectory(prefix="undabang-ime-") as temporary:
        apk = str(Path(temporary) / "keyboard.apk")
        ime_diagnostic("apk-pull", status="started")
        device.require("pull", paths[0][len("package:"):], apk)
        ime_diagnostic("apk-pull", status="completed")

        def dump(operation, *args):
            ime_diagnostic("aapt", operation=operation, command=args[0], status="started")
            code, output, _ = command([aapt, "dump", *args, apk], device.env)
            ime_diagnostic("aapt", operation=operation, command=args[0], exit_code=code, status="completed")
            if code != 0:
                raise RunnerFailure("emulator-ime-metadata-unavailable")
            return output.decode("utf-8", "replace")

        manifest = aapt_xml(dump("manifest", "xmltree", "--file", "AndroidManifest.xml"), "manifest")
        resource = ime_resource_id(manifest, ime)
        try:
            sdk_level = int(device.require("shell", "getprop", "ro.build.version.sdk"))
        except ValueError:
            raise RunnerFailure("emulator-ime-metadata-unavailable") from None
        paths = ime_resource_path(dump("resources", "resources"), resource, sdk_level)
        subtypes = set()
        for path in paths:
            metadata = aapt_xml(dump("input-method", "xmltree", "--file", path), "input-method")
            subtypes.add(ascii_ime_subtype(metadata))
        if len(subtypes) != 1:
            raise RunnerFailure("emulator-ascii-subtype-unavailable")
        return ime, subtypes.pop()


class Device:
    def __init__(self, serial):
        adb = shutil.which("adb")
        if not adb:
            raise RunnerFailure("adb-unavailable")
        self.env = tool_environment()
        if not serial:
            code, output, _ = command([adb, "devices"], self.env)
            devices = [line.split()[0] for line in output.decode("utf-8", "replace").splitlines()
                       if len(line.split()) == 2 and line.split()[1] == "device"]
            if code != 0 or len(devices) != 1:
                raise RunnerFailure("select-one-connected-device-with-device")
            serial = devices[0]
        self.serial = serial
        self.adb = [adb, "-s", serial]
        code, output, _ = self.call("get-state")
        if code != 0 or output.strip() != b"device":
            raise RunnerFailure("device-unavailable")

    def call(self, *args, timeout=60, payload=None):
        return command(self.adb + list(args), self.env, timeout, payload)

    def require(self, *args, timeout=60, payload=None):
        code, output, _ = self.call(*args, timeout=timeout, payload=payload)
        if code != 0:
            raise RunnerFailure("adb-operation-failed")
        return output

    def configure_emulator(self):
        if self.require("shell", "getprop", "ro.kernel.qemu").strip() != b"1":
            raise RunnerFailure("configure-emulator-refuses-physical-devices")
        self.require("root")
        self.require("wait-for-device")
        self.require("shell", "setprop", "persist.sys.locale", "ko-KR")
        self.require("shell", "setprop", "persist.sys.timezone", "Asia/Seoul")
        locale = self.require("shell", "getprop", "persist.sys.locale").strip()
        timezone = self.require("shell", "getprop", "persist.sys.timezone").strip()
        locale_matches = locale in (b"ko-KR", b"ko_KR")
        timezone_matches = timezone == b"Asia/Seoul"
        print(
            f"Locale after-setprop: locale_matches={locale_matches} timezone_matches={timezone_matches}",
            file=sys.stderr,
        )
        self.require("reboot")
        self.require("wait-for-device", timeout=180)
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            if self.require("shell", "getprop", "sys.boot_completed").strip() == b"1":
                self.configure_ascii_input()
                return
            time.sleep(1)
        raise RunnerFailure("emulator-boot-timeout")

    def configure_ascii_input(self):
        ime, subtype = installed_ascii_subtype(self)
        enabled = self.require("shell", "settings", "get", "secure", "enabled_input_methods").decode().strip()
        entries = enabled.split(":")
        if sum(entry.split(";", 1)[0] == ime for entry in entries) != 1:
            raise RunnerFailure("emulator-ascii-input-not-configured")
        configured = ":".join(
            ime + ";" + str(subtype) if entry.split(";", 1)[0] == ime else entry
            for entry in entries
        )
        # Maestro는 비ASCII 입력 후 ime set으로 복원하므로 자동 선택 후보도 영어 하나로 제한합니다.
        self.require("shell", "settings", "put", "secure", "enabled_input_methods", shlex.quote(configured))
        self.require("shell", "settings", "put", "secure", "selected_input_method_subtype", str(subtype))
        self.verify_ascii_input(ime, subtype)

    def verify_ascii_input(self, ime, subtype):
        selected = self.require("shell", "settings", "get", "secure", "default_input_method").decode().strip()
        enabled = self.require("shell", "settings", "get", "secure", "enabled_input_methods").decode().strip()
        current = self.require("shell", "settings", "get", "secure", "selected_input_method_subtype").decode().strip()
        entries = [entry for entry in enabled.split(":") if entry.split(";", 1)[0] == ime]
        if selected != ime or entries != [ime + ";" + str(subtype)] or current != str(subtype):
            raise RunnerFailure("emulator-ascii-input-not-configured")

    def verify_locale(self):
        locale = self.require("shell", "getprop", "persist.sys.locale").strip()
        timezone = self.require("shell", "getprop", "persist.sys.timezone").strip()
        locale_matches = locale in (b"ko-KR", b"ko_KR")
        timezone_matches = timezone == b"Asia/Seoul"
        print(
            f"Locale verification: locale_matches={locale_matches} timezone_matches={timezone_matches}",
            file=sys.stderr,
        )
        if not locale_matches or not timezone_matches:
            raise RunnerFailure("device-must-use-ko-KR-and-Asia-Seoul")

    def exercise_variables(self):
        output = self.require("shell", "date", "+%Y-%m-%dT%H:%M:%S%z")
        try:
            now = datetime.datetime.strptime(output.decode().strip(), "%Y-%m-%dT%H:%M:%S%z")
        except ValueError:
            raise RunnerFailure("invalid-device-clock") from None
        if now.utcoffset() != datetime.timedelta(hours=9):
            raise RunnerFailure("device-must-use-Asia-Seoul")
        end = now.replace(minute=0, second=0, microsecond=0)
        start = end - datetime.timedelta(hours=1)
        title = "maestro-e2e-" + uuid.uuid4().hex
        return {
            "EXERCISE_TITLE": title,
            "EDITED_EXERCISE_TITLE": title + "-edited",
            "EXERCISE_START_HOUR": start.strftime("%H"),
            "EXERCISE_END_HOUR": end.strftime("%H"),
            "EXERCISE_START_DATE": start.strftime("%Y.%m.%d"),
            "EXERCISE_END_DATE": end.strftime("%Y.%m.%d"),
        }

    def install(self, apk):
        try:
            with zipfile.ZipFile(apk) as archive:
                abis = {name.split("/")[1] for name in archive.namelist()
                        if name.startswith("lib/") and name.endswith(".so") and len(name.split("/")) == 3}
        except (OSError, zipfile.BadZipFile):
            raise RunnerFailure("invalid-e2e-apk") from None
        arm_abis = abis.intersection({"armeabi-v7a", "arm64-v8a"})
        effective = set(self.require("shell", "getprop", "ro.product.cpu.abilist").decode().strip().split(","))
        # 설치 실패 원인 분리를 위한 임시 진단: 공개 ABI 이름과 개수만 출력합니다.
        public_abis = {
            "arm64-v8a", "armeabi-v7a", "armeabi", "x86_64", "x86", "riscv64", "mips", "mips64",
        }
        print("ABI compatibility: " + json.dumps({
            "apk_abis": sorted(abis.intersection(public_abis)),
            "apk_other_abi_count": len(abis - public_abis),
            "apk_arm_abis": sorted(arm_abis.intersection(public_abis)),
            "apk_arm_other_abi_count": len(arm_abis - public_abis),
            "device_abis": sorted(effective.intersection(public_abis)),
            "device_other_abi_count": len(effective - public_abis),
            "empty_apk_arm": not arm_abis,
            "has_arm_intersection": bool(effective.intersection(arm_abis)),
        }, sort_keys=True), file=sys.stderr)
        if not arm_abis or not effective.intersection(arm_abis):
            raise RunnerFailure("device-abi-does-not-support-apk-arm-libraries")
        # 같은 ID의 release 앱과 서명이 다르면 설치 실패를 유지합니다. 자동 제거하지 않습니다.
        output = self.require("install", "-r", str(apk), timeout=180)
        if b"Success" not in output:
            raise RunnerFailure("e2e-apk-install-failed")

    def verify_setup(self):
        output = self.require("shell", "cmd", "package", "resolve-activity", "--brief", "-n", COMPONENT)
        resolved = {line.strip() for line in output.decode("utf-8", "replace").splitlines()}
        names = {COMPONENT, APP_ID + "/.e2e.MaestroSetupActivity"}
        if not resolved.intersection(names):
            raise RunnerFailure("installed-app-has-no-e2e-setup-component")
        self.require("shell", "-T", "run-as", APP_ID, "pwd")

    def clear(self):
        # 이전 실행의 복구 snapshot이 있으면 새 실행도 pm clear로 이를 지우지 않습니다.
        probe = "if [ -e " + RECOVERY + " ] || [ -e " + RECOVERY + ".bak ] || [ -e " + RECOVERY + ".new ]; then exit 4; fi"
        code, _, _ = self.call("shell", "-T", "run-as", APP_ID, "sh", "-c", shlex.quote(probe))
        if code == 4:
            raise RunnerFailure("pending-feature-restoration-refuses-app-data-clear")
        if code != 0:
            raise RunnerFailure("adb-operation-failed")
        self.require("shell", "am", "force-stop", APP_ID)
        output = self.require("shell", "pm", "clear", APP_ID)
        if output.strip() != b"Success":
            raise RunnerFailure("app-data-clear-failed")

    def remove_private(self, include_session=False):
        files = (REQUEST, RESULT, SESSION) if include_session else (REQUEST, RESULT)
        # export 완료 뒤 호스트가 읽을 때까지 세션 본문과 AtomicFile sidecar를 보존합니다.
        paths = [path + suffix for path in files for suffix in ("", ".new", ".bak")]
        self.require("shell", "-T", "run-as", APP_ID, "rm", "-f", *paths)

    def bootstrap(self, operation, credentials=None, timeout=300, feature=None, flow=None,
                  run_id=None, parameters=None):
        if operation not in ("prepare", "cleanup", "export", "offline-observe", "feature-observe"):
            raise RunnerFailure("invalid-bootstrap-operation")
        metadata = feature_metadata(operation, feature, flow, run_id, parameters)
        request = {"operation": operation, **(credentials or {}), **metadata}
        # 관찰은 실행 중 Room과 미저장 폼을 초기화하지 않습니다.
        if operation not in ("offline-observe", "feature-observe"):
            self.require("shell", "am", "force-stop", APP_ID)
        self.remove_private(include_session=operation == "export")
        payload = json.dumps(request, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        # shell -T는 PTY 없이 stdin과 원격 종료 상태를 전달합니다. argv에는 고정 명령만 넣습니다.
        script = "umask 077; mkdir -p files && cat > " + REQUEST
        self.require("shell", "-T", "run-as", APP_ID, "sh", "-c", shlex.quote(script), payload=payload)
        self.require("shell", "am", "start", "-W", "-n", COMPONENT)
        expected = {
            "prepare": "ready",
            "cleanup": "cleaned",
            "export": "exported",
            "offline-observe": "observed",
            "feature-observe": "observed",
        }[operation]
        # 결과 파일이 아직 없을 때만 기다리고, 실제 읽기 실패의 종료 상태는 보존합니다.
        probe = "if [ ! -e " + RESULT + " ]; then exit 3; fi; cat " + RESULT
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            code, output, _ = self.call("shell", "-T", "run-as", APP_ID, "sh", "-c", shlex.quote(probe))
            if code == 3:
                time.sleep(0.5)
                continue
            if code != 0:
                raise RunnerFailure("adb-operation-failed")
            try:
                result = json.loads(output)
            except (ValueError, TypeError):
                raise RunnerFailure("invalid-bootstrap-result") from None
            if not isinstance(result, dict) or result.get("status") != expected:
                # 앱의 reason도 외부 입력이므로 해당 기능에서 검토된 준비 코드만 고정 오류로 공개합니다.
                failure = "bootstrap-" + operation + "-failed"
                if (
                    operation in ("prepare", "offline-observe", "feature-observe") and
                    isinstance(result, dict) and
                    result.get("status") == "failed" and
                    isinstance(result.get("reason"), str)
                ):
                    reason = result["reason"]
                    if operation == "offline-observe" or feature == "offline":
                        failure = {
                            "offline_cache_flag_disabled": "offline-suite-requires-enabled-cache-flag",
                            "offline_upload_not_observed": "offline-suite-requires-real-server-upload",
                            "offline_exercise_server_mismatch": "offline-suite-exercise-server-mismatch",
                            "offline_flag_fetch_failed": "offline-suite-flag-fetch-failed",
                        }.get(reason, failure)
                    elif feature == "timer":
                        failure = {
                            "timer_local_id_missing": "timer-local-id-not-observed",
                            "timer_local_id_changed": "timer-local-id-changed",
                            "timer_server_id_missing": "timer-server-id-not-observed",
                            "timer_server_cleanup_failed": "timer-server-cleanup-failed",
                        }.get(reason, failure)
                    elif feature is None:
                        failure = COMMON_PREPARE_FAILURES.get(reason, failure)
                    elif feature == "profile":
                        failure = PROFILE_PREPARE_FAILURES.get(reason, failure)
                    elif feature == "matching":
                        failure = MATCHING_PREPARE_FAILURES.get(reason, failure)
                raise RunnerFailure(failure)
            variables = safe_variables(result.get("variables", {}))
            self.remove_private()
            return variables
        raise RunnerFailure("bootstrap-" + operation + "-timeout")


def secure_export_path(value):
    path = Path(value).expanduser()
    if not path.is_absolute():
        raise RunnerFailure("export-path-must-be-absolute")
    if any(item.is_symlink() for item in (path, *path.parents)):
        raise RunnerFailure("export-path-must-not-use-symlinks")
    path = path.resolve()
    if path == ROOT or ROOT in path.parents or path.exists():
        raise RunnerFailure("export-path-must-be-new-and-outside-repository")
    try:
        parent = path.parent.stat()
    except OSError:
        raise RunnerFailure("export-parent-must-exist-with-mode-0700") from None
    if not stat.S_ISDIR(parent.st_mode) or parent.st_uid != os.getuid() or stat.S_IMODE(parent.st_mode) & 0o077:
        raise RunnerFailure("export-parent-must-exist-with-mode-0700")
    return path


def export_session(device, output):
    written = False
    try:
        device.bootstrap("export")
        payload = device.require("shell", "-T", "run-as", APP_ID, "cat", SESSION)
        try:
            session = json.loads(payload)
        except (ValueError, TypeError):
            raise RunnerFailure("invalid-exported-session") from None
        if not isinstance(session, dict) or not isinstance(session.get("authState"), dict) or not session["authState"]:
            raise RunnerFailure("invalid-exported-session")
        if not isinstance(session.get("memberId"), str) or not session["memberId"]:
            raise RunnerFailure("invalid-exported-session")
        descriptor = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        written = True
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(json.dumps(session, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
            handle.flush()
            os.fsync(handle.fileno())
    except BaseException:
        if written:
            output.unlink(missing_ok=True)
        raise
    finally:
        cleanup_failed = False
        handlers = {sig: signal.signal(sig, signal.SIG_IGN) for sig in (signal.SIGINT, signal.SIGTERM)}
        for cleanup in (
            lambda: device.require("shell", "am", "force-stop", APP_ID),
            lambda: device.remove_private(include_session=True),
        ):
            try:
                cleanup()
            except Exception:
                cleanup_failed = True
        for sig, handler in handlers.items():
            signal.signal(sig, handler)
        if cleanup_failed:
            if written:
                output.unlink(missing_ok=True)
            raise RunnerFailure("export-private-cleanup-failed")


def save_debug(raw, destination, redactor, console):
    destination.mkdir(parents=True, exist_ok=True)
    clean, sensitive = redactor.clean(console.decode("utf-8", "replace"))
    (destination / "console.log").write_text(clean, encoding="utf-8")
    images = []
    index = 0
    for path in sorted(raw.rglob("*")):
        if path.is_symlink() or not path.is_file() or "logcat" in path.name.lower():
            continue
        suffix = path.suffix.lower()
        if suffix == ".png":
            images.append(path)
        elif suffix in {".log", ".json"}:
            clean, changed = redactor.clean(path.read_text(encoding="utf-8", errors="replace"))
            sensitive = sensitive or changed
            index += 1
            (destination / ("debug-%03d" % index + suffix)).write_text(clean, encoding="utf-8")
    # 텍스트에 자격 증명이 있으면 이미지의 안전성을 판정할 수 없으므로 이미지도 보관하지 않습니다.
    if not sensitive:
        for index, path in enumerate(images, 1):
            content = path.read_bytes()
            if content.startswith(b"\x89PNG\r\n\x1a\n"):
                (destination / ("screenshot-%03d.png" % index)).write_bytes(content)

def save_failure_logcat(device, destination, redactor):
    try:
        destination.mkdir(parents=True, exist_ok=True)
        code, output, _ = device.call("logcat", "-d", "-t", "500", "-v", "brief", timeout=30)
        if code != 0:
            return
        lines = []
        for line in output.decode("utf-8", "replace").splitlines():
            if re.search(r"\[MEASURE\]|WebSocket|Connectivity|AndroidRuntime|FATAL EXCEPTION|OkHttp",
                         line, re.IGNORECASE):
                lines.append(line)
        clean, _ = redactor.clean("\n".join(lines) + ("\n" if lines else ""))
        (destination / "device-log.log").write_text(clean, encoding="utf-8")
    except RunInterrupted:
        raise
    except (RunnerFailure, OSError):
        return



def diagnostic_number(value):
    try:
        number = float(value)
    except (TypeError, ValueError, OverflowError):
        return None
    return number if math.isfinite(number) and number >= 0 else None


def parse_last_anr(report):
    process = re.search(r"^ANR in ([A-Za-z0-9_.:]+)(?:\s|$)", report, re.MULTILINE)
    process_name = process.group(1) if process else None
    category = {
        "com.android.systemui": "system-ui", APP_ID: "application",
    }.get(process_name, "other" if process_name else "unknown")
    reason = re.search(r"^[ \t]*Reason: ([^\n]*)", report, re.MULTILINE)
    reason_category = "unknown"
    if reason:
        for prefix, name in (
            ("Input dispatching timed out", "input-dispatch"),
            ("Broadcast of Intent", "broadcast"),
            ("executing service", "service"),
            ("ContentProvider not responding", "content-provider"),
        ):
            if reason.group(1).startswith(prefix):
                reason_category = name
                break
    load = re.search(r"^Load: ([0-9]+(?:\.[0-9]+)?) / ([0-9]+(?:\.[0-9]+)?) / ([0-9]+(?:\.[0-9]+)?)$",
                     report, re.MULTILINE)
    cpu = re.search(r"^\s*([0-9]+(?:\.[0-9]+)?)% TOTAL:", report, re.MULTILINE)
    load_average = [diagnostic_number(value) for value in load.groups()] if load else None
    if load_average is not None and any(value is None for value in load_average):
        load_average = None
    return {
        "process": category, "reason": reason_category,
        "load_average": load_average,
        "total_cpu_percent": diagnostic_number(cpu.group(1)) if cpu else None,
    }


def save_failure_anr(device, destination):
    try:
        code, output, _ = device.call("shell", "dumpsys", "activity", "lastanr", timeout=30)
        if code != 0:
            return
        # 마지막 ANR은 이전 사건일 수 있습니다. 원문·PID·창 제목·Intent는 공개하지 않습니다.
        summary = parse_last_anr(output.decode("utf-8", "replace"))
        destination.mkdir(parents=True, exist_ok=True)
        (destination / "last-anr-summary.json").write_text(
            json.dumps(summary, sort_keys=True, allow_nan=False) + "\n", encoding="utf-8",
        )
    except RunInterrupted:
        raise
    except (RunnerFailure, OSError, ValueError):
        return


def parse_anr_events(report):
    events = []
    unrecognized = False
    for line in report.splitlines():
        line = line.strip()
        if not line or re.fullmatch(r"--------- (?:beginning of|switch to) events", line):
            continue
        event = re.fullmatch(
            r"([0-9]+(?:\.[0-9]+)?)\s+[0-9]+\s+[0-9]+\s+I\s+am_anr\s*:\s*"
            r"\[-?[0-9]+,[0-9]+,([A-Za-z0-9_.:]+),-?[0-9]+,(.*)\]",
            line,
        )
        if not event:
            unrecognized = True
            continue
        try:
            timestamp = float(event.group(1))
        except ValueError:
            unrecognized = True
            continue
        if not math.isfinite(timestamp) or timestamp < 0:
            unrecognized = True
            continue
        process = {
            "com.android.systemui": "system-ui", APP_ID: "application",
        }.get(event.group(2), "other")
        reason = "unknown"
        for prefix, category in (
            ("Input dispatching timed out", "input-dispatch"),
            ("Broadcast of Intent", "broadcast"),
            ("executing service", "service"),
            ("ContentProvider not responding", "content-provider"),
        ):
            if event.group(3).startswith(prefix):
                reason = category
                break
        # 쉼표·괄호가 있는 reason도 원문은 버리고 고정 분류와 시각만 남깁니다.
        events.append({"timestamp_epoch": timestamp, "process": process, "reason": reason})
        events.sort(key=lambda item: item["timestamp_epoch"])
        del events[:-8]
    if events:
        status = "partial" if unrecognized else "captured"
    else:
        status = "unrecognized" if unrecognized else "empty"
    return {"status": status, "events": events}


def save_failure_anr_events(device, destination):
    summary = {"status": "unavailable", "events": []}
    try:
        code, output, _ = device.call(
            "logcat", "-b", "events", "-d", "-v", "epoch", "-s", "am_anr:I", "*:S", timeout=30,
        )
        if code == 0:
            summary = parse_anr_events(output.decode("utf-8", "replace"))
    except RunInterrupted:
        raise
    except (RunnerFailure, OSError):
        pass
    except ValueError:
        summary = {"status": "unrecognized", "events": []}
    # events 버퍼는 기기의 과거 기록이며 현재 플로우에서 발생했다는 증거가 아닙니다.
    summary["scope"] = "device-history"
    try:
        destination.mkdir(parents=True, exist_ok=True)
        # 원본 로그·stderr·예외 본문은 저장하지 않고 허용한 분류만 공개합니다.
        (destination / "anr-events-summary.json").write_text(
            json.dumps(summary, sort_keys=True, allow_nan=False) + "\n", encoding="utf-8",
        )
    except OSError:
        return


def capture_anr_checkpoint(device, destination, phase):
    if phase not in (
        "peer-ready-before-primary-prepare", "primary-fixture-ready",
        "receiver-ready-before-primary-launch", "failure",
    ):
        raise ValueError("invalid-anr-checkpoint-phase")
    started_epoch = diagnostic_number(time.time())
    started_monotonic = diagnostic_number(time.monotonic())

    def read(*arguments):
        try:
            code, output, _ = device.call(*arguments, timeout=5)
            return output.decode("utf-8", "replace") if code == 0 else None
        except RunInterrupted:
            raise
        except (RunnerFailure, OSError):
            return None

    last_anr = {
        "status": "unavailable", "scope": "device-history",
        "process": "unknown", "reason": "unknown",
        "load_average": None, "total_cpu_percent": None,
    }
    report = read("shell", "dumpsys", "activity", "lastanr")
    if report is not None:
        try:
            last_anr.update(parse_last_anr(report))
            if not report.strip() or report.strip() == "<no ANR has occurred>":
                last_anr["status"] = "empty"
            elif last_anr["process"] != "unknown" and last_anr["reason"] != "unknown":
                last_anr["status"] = "captured"
            elif (last_anr["process"] != "unknown" or last_anr["reason"] != "unknown" or
                    last_anr["load_average"] is not None or last_anr["total_cpu_percent"] is not None):
                last_anr["status"] = "partial"
            else:
                last_anr["status"] = "unrecognized"
        except RunInterrupted:
            raise
        except ValueError:
            last_anr["status"] = "unrecognized"
    events = {"status": "unavailable", "scope": "device-history", "events": []}
    report = read("logcat", "-b", "events", "-d", "-v", "epoch", "-s", "am_anr:I", "*:S")
    if report is not None:
        try:
            events.update(parse_anr_events(report))
        except RunInterrupted:
            raise
        except ValueError:
            events["status"] = "unrecognized"
    clock = {"status": "unavailable", "wall_epoch": None, "uptime_seconds": None}
    wall = read("shell", "date", "+%s")
    uptime = read("shell", "cat", "/proc/uptime")
    if wall is not None:
        value = wall.strip()
        if re.fullmatch(r"[0-9]+(?:\.[0-9]+)?", value):
            clock["wall_epoch"] = diagnostic_number(value)
    if uptime is not None:
        value = re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)\s+[0-9]+(?:\.[0-9]+)?\s*", uptime)
        if value:
            clock["uptime_seconds"] = diagnostic_number(value.group(1))
    if clock["wall_epoch"] is not None and clock["uptime_seconds"] is not None:
        clock["status"] = "captured"
    elif clock["wall_epoch"] is not None or clock["uptime_seconds"] is not None:
        clock["status"] = "partial"
    elif wall is not None or uptime is not None:
        clock["status"] = "unrecognized"
    metadata = {
        "phase": phase, "scope": "device-history",
        "host_capture_started_epoch": started_epoch,
        "host_capture_ended_epoch": diagnostic_number(time.time()),
        "host_capture_started_monotonic": started_monotonic,
        "host_capture_ended_monotonic": diagnostic_number(time.monotonic()),
        "device_clock": clock,
        "last_anr_status": last_anr["status"], "anr_events_status": events["status"],
    }
    # 파일별 실패를 격리하여 다른 요약과 원래 UI 결과·fixture 정리를 보존합니다.
    for name, summary in (
        ("last-anr-summary.json", last_anr), ("anr-events-summary.json", events),
        ("checkpoint.json", metadata),
    ):
        try:
            destination.mkdir(parents=True, exist_ok=True)
            (destination / name).write_text(
                json.dumps(summary, sort_keys=True, allow_nan=False) + "\n", encoding="utf-8",
            )
        except RunInterrupted:
            raise
        except (OSError, ValueError):
            continue


def host_resource_sampler(destination):
    # sys.path나 패키지 import에 의존하지 않아 파일 경로로 로드하는 기존 테스트와 호환됩니다.
    path = Path(__file__).with_name("host-resources.py")
    spec = importlib.util.spec_from_file_location("maestro_host_resources", path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module.HostResourceSampler(destination)


def maestro_result(report):
    try:
        root = ET.parse(report).getroot()
    except (OSError, ET.ParseError):
        raise RunnerFailure("missing-or-invalid-maestro-junit") from None
    cases = list(root.iter("testcase"))
    if not cases or any(case.find("skipped") is not None for case in cases):
        raise RunnerFailure("maestro-did-not-execute-selected-flow")
    if any(case.find("failure") is not None or case.find("error") is not None for case in cases):
        raise RunnerFailure("maestro-assertion-failed")


def run_maestro(device, flow, output, redactor, timeout, fixture_start_date=None, variables=None):
    maestro = shutil.which("maestro")
    if not maestro:
        raise RunnerFailure("maestro-unavailable")
    with tempfile.TemporaryDirectory(prefix="undabang-maestro-") as temporary:
        raw = Path(temporary)
        home = raw / "home"
        home.mkdir()
        env = tool_environment()
        env.update({
            "HOME": str(home), "XDG_CONFIG_HOME": str(home),
            "JAVA_TOOL_OPTIONS": "-Duser.home=" + str(home),
            "MAESTRO_CLI_NO_ANALYTICS": "true",
            "MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED": "true",
            "MAESTRO_DISABLE_UPDATE_CHECK": "true",
        })
        code, stdout, stderr = command([maestro, "--version"], env)
        if code != 0 or MAESTRO_VERSION not in stdout.decode("utf-8", "replace").split():
            raise RunnerFailure("maestro-version-must-be-2.11.0")
        explicit = safe_variables(variables or {})
        variables = {**device.exercise_variables(), **explicit}
        if flow == "exercise-crud" and (not fixture_start_date or
                variables["EXERCISE_START_DATE"].replace(".", "-") < fixture_start_date):
            raise RunnerFailure("fixture-start-date-excludes-flow-record-date")
        report = raw / "report.xml"
        argv = [
            maestro, "test", "--device", device.serial, "--no-ansi", "--format", "junit",
            "--output", str(report), "--debug-output", str(raw / "debug"),
            "--test-output-dir", str(raw / "artifacts"), "--flatten-debug-output",
            str(ROOT / ".maestro" / "flows" / (flow + ".yaml")),
        ]
        for key, value in variables.items():
            argv[2:2] = ["-e", key + "=" + value]
        stdout = stderr = b""
        try:
            code, stdout, stderr = command(argv, env, timeout=timeout)
            if code != 0:
                save_failure_logcat(device, output / flow, redactor)
                save_failure_anr(device, output / flow)
                save_failure_anr_events(device, output / flow)
                raise RunnerFailure("maestro-flow-failed")
            maestro_result(report)
        finally:
            save_debug(raw, output / flow, redactor, stdout + b"\n" + stderr)

def write_report(output, results):
    suite = ET.Element("testsuite", name="undabang-maestro", tests=str(len(results)),
                       failures=str(sum(bool(errors) for _, _, errors in results)), errors="0", skipped="0")
    for name, duration, errors in results:
        case = ET.SubElement(suite, "testcase", classname="maestro", name=name, time="%.3f" % duration)
        if errors:
            ET.SubElement(case, "failure", message="; ".join(errors)).text = "; ".join(errors)
    ET.ElementTree(suite).write(output / "report.xml", encoding="utf-8", xml_declaration=True)


def interrupt(_signum, _frame):
    raise RunInterrupted("run-interrupted")


def load_feature_module(suite):
    if not suite["name"]:
        return None
    path = ROOT / ".maestro" / "scripts" / "features" / (suite["name"] + ".py")
    if not path.is_file():
        return None
    spec = importlib.util.spec_from_file_location("maestro_feature_" + suite["name"], path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    if suite["driver"] and not callable(getattr(module, "run_suite", None)):
        raise RunnerFailure("invalid-feature-driver")
    return module


def core_module():
    # importlib로 로드하는 회귀 테스트도 실행 모듈과 같은 공개 API를 받습니다.
    if __name__ in sys.modules:
        return sys.modules[__name__]
    module = type(sys)("maestro_core", __doc__)
    module.__dict__.update(globals())
    return module


def run_ordinary_flow(args, device, suite, flow, output, redactor, credentials, parameters, hooks):
    started = time.monotonic()
    errors = []
    run_id = uuid.uuid4().hex[:16]
    ownership = output / flow
    ownership.mkdir(parents=True, exist_ok=True)
    (ownership / "ownership.log").write_text("E2E_RUN_ID=" + run_id + "\n", encoding="utf-8")
    variables = {"E2E_RUN_ID": run_id}
    metadata = ({"feature": suite["name"], "flow": flow, "run_id": run_id, "parameters": parameters}
                if suite["fixture"] else {})
    prepared = False
    safe_to_continue = True
    try:
        device.clear()
        if flow != "login-screen":
            # prepare 중 부분 변경이 발생해도 동일 ownership으로 cleanup을 요청합니다.
            prepared = True
            added = safe_variables(device.bootstrap("prepare", credentials, **metadata))
            if "E2E_RUN_ID" in added and added["E2E_RUN_ID"] != run_id:
                raise RunnerFailure("invalid-feature-variables")
            variables.update(added)
        if hooks and callable(getattr(hooks, "before_flow", None)):
            try:
                added = safe_variables(hooks.before_flow(
                    core_module(), device, flow, dict(variables), credentials, run_id,
                ))
                if "E2E_RUN_ID" in added and added["E2E_RUN_ID"] != run_id:
                    raise RunnerFailure("invalid-feature-variables")
                variables.update(added)
            except RunnerFailure:
                raise
            except Exception:
                raise RunnerFailure("feature-hook-failed") from None
        run_maestro(device, flow, output, redactor, args.flow_timeout,
                    credentials["fixtureStartDate"] if credentials else None, variables=variables)
        if hooks and callable(getattr(hooks, "after_flow", None)):
            try:
                hooks.after_flow(core_module(), device, flow, dict(variables), credentials, run_id)
            except RunnerFailure:
                raise
            except Exception:
                raise RunnerFailure("feature-hook-failed") from None
    except RunInterrupted:
        errors.append("run-interrupted")
        safe_to_continue = False
    except RunnerFailure as failure:
        errors.append(str(failure))
    except Exception:
        errors.append("runner-operation-failed")
    finally:
        handlers = {sig: signal.signal(sig, signal.SIG_IGN) for sig in (signal.SIGINT, signal.SIGTERM)}
        try:
            if hooks and callable(getattr(hooks, "collect_diagnostics", None)):
                try:
                    hooks.collect_diagnostics(
                        core_module(), device, flow, dict(variables), credentials, run_id, ownership,
                    )
                except Exception:
                    # 수집 오류는 원래 UI 오류를 덮거나 fixture 복구/최종 clear를 막지 않습니다.
                    errors.append("feature-diagnostics-failed")
            cleanup_failed = False
            if prepared:
                try:
                    device.bootstrap("cleanup", credentials, **metadata)
                except Exception:
                    errors.append("fixture-cleanup-failed")
                    cleanup_failed = True
                    safe_to_continue = False
            if cleanup_failed:
                # pm clear는 복구 snapshot도 지우므로 정리 실패 뒤에는 다음 flow도 실행하지 않습니다.
                errors.append("private-state-preserved-for-recovery")
            else:
                try:
                    device.clear()
                except Exception:
                    errors.append("final-app-data-clear-failed")
                    safe_to_continue = False
        finally:
            for sig, handler in handlers.items():
                signal.signal(sig, handler)
    return (flow, time.monotonic() - started, errors), safe_to_continue


def validate_driver_results(flows, results):
    if not isinstance(results, (list, tuple)):
        return [(flow, 0, ["invalid-driver-results"]) for flow in flows]
    reported = {}
    invalid = False
    for item in results:
        if not isinstance(item, (list, tuple)) or len(item) != 3:
            invalid = True
            continue
        flow, duration, errors = item
        if (not isinstance(flow, str) or flow not in flows or flow in reported
                or type(duration) not in (int, float) or not math.isfinite(duration) or duration < 0
                or not isinstance(errors, list)
                or any(not isinstance(error, str) or not re.fullmatch(r"[a-z][a-z0-9-]*", error) for error in errors)):
            invalid = True
            continue
        reported[flow] = (flow, duration, errors)
    return [(flow, reported[flow][1], [*reported[flow][2], "invalid-driver-results"])
            if invalid and flow in reported else reported.get(flow, (flow, 0, ["driver-flow-not-reported"]))
            for flow in flows]


def run_suite(args):
    output = ROOT / "build" / "maestro" / (datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8])
    output.mkdir(parents=True, mode=0o700)
    args.output = output
    results = []
    selected = []
    try:
        suites = select_suites(args.suite)
        selected = selected_flows(suites)
        environment = read_required_environment(suites)
        credentials = read_credentials() if any(flow != "login-screen" for flow in selected) else None
        peer = read_credentials("MAESTRO_PEER_") if "MAESTRO_PEER_AUTH_STATE_JSON" in environment else None
        redactor = Redactor({"primary": credentials, "peer": peer, "feature": environment})
        # 모듈의 import/계약 오류도 장치 초기화나 서버 변경 전에 검출합니다.
        modules = {suite["name"]: load_feature_module(suite) for suite in suites}
        device = None
        continue_run = True
        for suite in suites:
            parameters = {key: environment[key] for key in suite["requiredEnvironment"]}
            hooks = modules[suite["name"]]
            if not continue_run:
                results.extend((flow, 0, ["not-executed-after-unsafe-cleanup-or-interruption"])
                               for flow in suite["flows"])
                continue
            if suite["driver"]:
                try:
                    returned = hooks.run_suite(args, core_module())
                    driver_results = validate_driver_results(suite["flows"], returned)
                except RunInterrupted:
                    driver_results = [(flow, 0, ["run-interrupted"]) for flow in suite["flows"]]
                except Exception:
                    driver_results = [(flow, 0, ["feature-driver-failed"]) for flow in suite["flows"]]
                results.extend(driver_results)
                continue_run = not any(errors for _, _, errors in driver_results)
                device = None
            else:
                if device is None:
                    device = Device(args.device)
                    if args.configure_emulator:
                        device.configure_emulator()
                    device.verify_locale()
                    if args.apk:
                        device.install(Path(args.apk).resolve())
                    # 일반 debug/release 앱은 이 확인을 통과하지 못하며 pm clear도 하지 않습니다.
                    device.verify_setup()
                for flow in suite["flows"]:
                    if not continue_run:
                        results.append((flow, 0, ["not-executed-after-unsafe-cleanup-or-interruption"]))
                        continue
                    result, continue_run = run_ordinary_flow(
                        args, device, suite, flow, output, redactor, credentials, parameters, hooks,
                    )
                    results.append(result)
                    write_report(output, results)
            write_report(output, results)
    except RunnerFailure as failure:
        results.append(("host-setup", 0, [str(failure)]))
    except Exception:
        results.append(("host-setup", 0, ["runner-operation-failed"]))
    finally:
        reported = {name for name, _, _ in results}
        results.extend((flow, 0, ["not-executed-host-setup-failed"])
                       for flow in selected if flow not in reported)
        write_report(output, results)
    for flow, _, errors in results:
        print(flow + (": FAILED (" + "; ".join(errors) + ")" if errors else ": PASSED"))
    print("Safe artifacts: " + str(output.relative_to(ROOT)))
    return 1 if not results or any(errors for _, _, errors in results) else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument("--suite", help="smoke, regression, login, ci, all, or a discovered feature")
    operation.add_argument("--export-session", metavar="ABSOLUTE_PRIVATE_PATH")
    parser.add_argument("--device", help="ADB serial; required when multiple devices are connected")
    parser.add_argument("--peer-device", help="Second ADB serial for the two-account chat driver")
    parser.add_argument("--preflight", action="store_true", help="Validate selected inputs and emit the required emulator API")
    parser.add_argument("--apk", help="Install this exact e2e APK after checking effective ARM ABI support")
    parser.add_argument("--flow-timeout", type=int, default=900, help="Per-flow Maestro timeout in seconds")
    parser.add_argument("--configure-emulator", action="store_true", help="Set ko-KR/Asia-Seoul and reboot a rootable emulator")
    parser.add_argument("--timer-initial-seconds", type=int, choices=range(1, 3600), metavar="1..3599")
    parser.add_argument("--timer-updated-seconds", type=int, choices=range(1, 3600), metavar="1..3599")
    parser.add_argument("--offline-initial-seconds", type=int, choices=range(5, 3600), metavar="5..3599")
    parser.add_argument("--offline-updated-seconds", type=int, choices=range(5, 3600), metavar="5..3599")
    parser.add_argument("--offline-step-name", help="ASCII step name, 1..40 characters")
    args = parser.parse_args()
    if args.offline_step_name is not None and not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9 _-]{0,39}", args.offline_step_name):
        parser.error("--offline-step-name must match [A-Za-z0-9][A-Za-z0-9 _-]{0,39}")
    offline_initial = 45 if args.offline_initial_seconds is None else args.offline_initial_seconds
    offline_updated = 75 if args.offline_updated_seconds is None else args.offline_updated_seconds
    if offline_initial == offline_updated:
        parser.error("offline initial and updated seconds must differ")
    timer_initial = 41 if args.timer_initial_seconds is None else args.timer_initial_seconds
    timer_updated = 42 if args.timer_updated_seconds is None else args.timer_updated_seconds
    if timer_initial == timer_updated:
        parser.error("timer-simple-preset-crud initial and updated seconds must differ")
    if args.flow_timeout <= 0:
        parser.error("--flow-timeout must be positive")
    signal.signal(signal.SIGINT, interrupt)
    signal.signal(signal.SIGTERM, interrupt)
    try:
        if args.preflight:
            if not args.suite:
                raise RunnerFailure("preflight-requires-suite")
            suites = select_suites(args.suite)
            selected = selected_flows(suites)
            api = required_api(suites)
            print("Selected flows: " + ", ".join(selected))
            print("Required emulator API: " + str(api))
            try:
                read_required_environment(suites)
                if any(flow != "login-screen" for flow in selected):
                    read_credentials()
            except RunnerFailure as failure:
                output = ROOT / "build" / "maestro" / "preflight"
                output.mkdir(parents=True, exist_ok=True)
                write_report(output, [(flow, 0, [str(failure), "not-executed-input-preflight-failed"])
                                      for flow in selected])
                raise
            if os.environ.get("GITHUB_OUTPUT"):
                with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as handle:
                    handle.write("api-level=" + str(api) + "\n")
            return 0
        if args.export_session:
            output = secure_export_path(args.export_session)
            device = Device(args.device)
            if args.configure_emulator:
                device.configure_emulator()
            if args.apk:
                device.install(Path(args.apk).resolve())
            device.verify_setup()
            export_session(device, output)
            print("Session exported to the requested private file. No credentials were printed.")
            return 0
        return run_suite(args)
    except RunnerFailure as failure:
        print("Runner failed: " + str(failure), file=sys.stderr)
        return 1
    except Exception:
        # 파이썬/subprocess 예외 객체에는 stdin 원문이 있을 수 있어 traceback을 출력하지 않습니다.
        print("Runner failed: internal-operation-failed", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
