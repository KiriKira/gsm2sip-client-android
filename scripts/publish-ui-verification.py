#!/usr/bin/env python3
"""Publish verified Android UI screenshots from a successful KVM artifact."""

from __future__ import annotations

import argparse
import binascii
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import struct
import sys
import tempfile
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
CALL_SCREENSHOTS = (
    "call-synthetic-dialing.png",
    "call-synthetic-active.png",
    "call-synthetic-keypad.png",
    "call-synthetic-incoming.png",
)


@dataclass(frozen=True)
class Screenshot:
    category: str
    source: str
    description: str
    paired_stage: str | None = None


SCREENSHOTS = (
    Screenshot("未配对界面", "host_navigation_initial.png", "三标签导航"),
    Screenshot("电话与拨号", "host_call_history.png", "未配对电话记录页"),
    Screenshot("电话与拨号", "host_dialpad.png", "独立拨号键盘"),
    Screenshot("短信", "host_sms_thread_list.png", "短信会话列表"),
    Screenshot("设置", "host_settings_tab.png", "设置与配对表单"),
    Screenshot("设置", "host_settings_backup_entry.png", "设置中的短信备份入口"),
    Screenshot("窗口适配", "folded_ui.png", "主界面合拢窗口"),
    Screenshot("窗口适配", "unfolded_ui.png", "主界面展开窗口"),
    Screenshot("窗口适配", "hole_cutout_enabled.png", "显示挖孔时的主界面"),
    Screenshot("短信备份", "main-backup-entry-host.png", "滚动后可见的备份入口"),
    Screenshot("短信备份", "backup-overview.png", "短信备份与归档页面"),
    Screenshot("短信备份", "backup-rotation-landscape.png", "备份页面横屏"),
    Screenshot("短信备份", "backup-rotation-portrait.png", "备份页面竖屏"),
    Screenshot("短信备份", "backup-folded.png", "备份页面合拢"),
    Screenshot("短信备份", "backup-unfolded.png", "备份页面展开"),
    Screenshot("短信备份", "backup-cutout.png", "挖孔安全区中的备份页面"),
    Screenshot("短信备份", "backup-import-preview.png", "合成短信归档导入预览"),
    Screenshot("短信备份", "backup-imported-history.png", "导入后的归档历史"),
    Screenshot("配对数据界面", "paired-fixture/paired_three_tabs.png", "配对状态下的三标签", "paired_three_tabs"),
    Screenshot("配对数据界面", "paired-fixture/paired_call_history.png", "双 SIM 合成通话记录", "paired_call_history"),
    Screenshot("配对数据界面", "paired-fixture/paired_dialpad_from_history.png", "从通话记录预填号码的拨号页"),
    Screenshot("配对数据界面", "paired-fixture/paired_dialpad.png", "配对状态下的独立拨号键盘", "paired_dialpad"),
    Screenshot("配对数据界面", "paired-fixture/paired_sms_threads.png", "三条合成短信会话", "paired_sms_threads"),
    Screenshot("配对数据界面", "paired-fixture/paired_sms_threads_folded.png", "合拢窗口中的短信会话", "paired_sms_threads_folded"),
    Screenshot("配对数据界面", "paired-fixture/paired_sms_threads_unfolded.png", "展开窗口中的短信会话", "paired_sms_threads_unfolded"),
    Screenshot("配对数据界面", "paired-fixture/paired_sms_conversation.png", "既有短信对话与回复框", "paired_sms_conversation"),
    Screenshot("配对数据界面", "paired-fixture/paired_new_message.png", "新短信编辑页", "paired_new_message"),
    Screenshot("配对数据界面", "paired-fixture/paired_new_message_sim_b.png", "选择 SIM 2 的新短信页", "paired_new_message_sim_b"),
    Screenshot("配对数据界面", "paired-fixture/paired_new_message_landscape.png", "横屏且键盘显示时的短信草稿", "paired_new_message_landscape"),
    Screenshot("配对数据界面", "paired-fixture/paired_new_message_folded.png", "合拢窗口中的短信草稿", "paired_new_message_folded"),
    Screenshot("配对数据界面", "paired-fixture/paired_new_message_unfolded.png", "展开窗口中的短信草稿", "paired_new_message_unfolded"),
    Screenshot("配对数据界面", "paired-fixture/paired_settings.png", "配对状态下的设置", "paired_settings"),
    Screenshot("配对数据界面", "paired-fixture/paired_settings_backup.png", "配对设置中的短信备份入口", "paired_settings_backup"),
    *(Screenshot("合成通话界面", f"paired-fixture/{filename}", description, filename.removesuffix(".png"))
      for filename, description in zip(CALL_SCREENSHOTS, (
          "合成出站呼叫准备界面", "合成通话中界面", "合成通话键盘界面", "合成来电界面",
      ))),
)


class PublicationError(Exception):
    pass


def _load_json(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise PublicationError(f"缺少 {label}: {path}") from exc
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise PublicationError(f"无法读取 {label} {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise PublicationError(f"{label} 必须是 JSON 对象: {path}")
    return value


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        raise PublicationError(f"无法计算文件 SHA-256 {path}: {exc}") from exc
    return digest.hexdigest()


def _validate_png(path: Path) -> tuple[bytes, int, int, str]:
    try:
        data = path.read_bytes()
    except OSError as exc:
        raise PublicationError(f"无法读取截图 {path}: {exc}") from exc
    if not data.startswith(PNG_SIGNATURE):
        raise PublicationError(f"截图不是 PNG 或签名错误: {path}")

    offset = len(PNG_SIGNATURE)
    width = height = 0
    saw_ihdr = saw_iend = False
    while offset < len(data):
        if offset + 12 > len(data):
            raise PublicationError(f"PNG chunk header 截断: {path}")
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        chunk_end = offset + 12 + length
        if chunk_end > len(data):
            raise PublicationError(f"PNG chunk 数据截断: {path}")
        chunk_data = data[offset + 8:offset + 8 + length]
        expected_crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        actual_crc = binascii.crc32(kind + chunk_data) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            raise PublicationError(f"PNG chunk CRC 校验失败: {path} ({kind!r})")
        if not saw_ihdr:
            if kind != b"IHDR" or length != 13:
                raise PublicationError(f"PNG 首个 chunk 不是有效 IHDR: {path}")
            width, height = struct.unpack_from(">II", chunk_data, 0)
            if width <= 0 or height <= 0:
                raise PublicationError(f"PNG 尺寸无效: {path} ({width}x{height})")
            saw_ihdr = True
        elif kind == b"IHDR":
            raise PublicationError(f"PNG 中存在重复 IHDR: {path}")
        offset = chunk_end
        if kind == b"IEND":
            if length != 0 or offset != len(data):
                raise PublicationError(f"PNG IEND 无效或存在尾随数据: {path}")
            saw_iend = True
            break
    if not saw_ihdr or not saw_iend:
        raise PublicationError(f"PNG 缺少 IHDR 或 IEND: {path}")
    return data, width, height, hashlib.sha256(data).hexdigest()


def _normalize_artifact_digest(value: str) -> str:
    digest = value.strip()
    if digest.lower().startswith("sha256:"):
        digest = digest.split(":", 1)[1]
    if not re.fullmatch(r"[0-9a-fA-F]{64}", digest):
        raise PublicationError("artifact digest 必须是 64 位 SHA-256，可带 sha256: 前缀")
    return f"sha256:{digest.lower()}"


def _validate_metadata(args: argparse.Namespace) -> dict[str, str]:
    if not re.fullmatch(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}", args.source_commit.strip()):
        raise PublicationError("source commit 必须是完整的 40 位或 64 位十六进制提交 SHA")
    if not re.fullmatch(r"[0-9]+", args.run_id.strip()):
        raise PublicationError("run-id 必须是正整数")
    if not re.fullmatch(r"[^/\s]+/[^/\s]+", args.repo.strip()):
        raise PublicationError("repo 必须使用 owner/name 格式")
    if not re.fullmatch(r"[0-9]+", args.artifact_id.strip()):
        raise PublicationError("artifact-id 必须是正整数")
    return {
        "source_commit": args.source_commit.strip().lower(),
        "run_id": args.run_id.strip(),
        "repo": args.repo.strip(),
        "artifact_id": args.artifact_id.strip(),
        "artifact_digest": _normalize_artifact_digest(args.artifact_digest),
    }


def _validate_sources(artifact_dir: Path, apk: Path) -> tuple[
        dict[str, Any], dict[str, Any], dict[str, Any], dict[str, Any], Path]:
    if not artifact_dir.is_dir():
        raise PublicationError(f"artifact directory 不存在: {artifact_dir}")
    if not apk.is_file() or apk.is_symlink():
        raise PublicationError(f"APK 不存在或不是普通文件: {apk}")
    host_result_path = artifact_dir / "results.json"
    host_results = _load_json(host_result_path, "未配对 UI smoke 结果")
    if host_results.get("scenario") != "host":
        raise PublicationError(f"未配对 UI smoke scenario 应为 host，实际为 {host_results.get('scenario')!r}")
    if host_results.get("overall") != "pass":
        raise PublicationError(f"未配对 UI smoke 未通过: overall={host_results.get('overall')!r}")

    host_manifest_path = artifact_dir / "screenshot_manifest.json"
    host_manifest = _load_json(host_manifest_path, "未配对 UI 截图清单")
    host_screenshots = {
        item.get("stage"): item.get("png")
        for item in host_manifest.get("screenshots", [])
        if isinstance(item, dict)
    }

    paired_dir = artifact_dir / "paired-fixture"
    paired_result_path = paired_dir / "results.json"
    paired_results = _load_json(paired_result_path, "配对 fixture 结果")
    paired_manifest_path = paired_dir / "fixture_manifest.json"
    paired_manifest = _load_json(paired_manifest_path, "配对 fixture 截图清单")
    if paired_results.get("scenario") != "synthetic_cached_offline_paired_ui":
        raise PublicationError(f"配对 fixture scenario 不匹配: {paired_results.get('scenario')!r}")
    if paired_results.get("status") != "pass" or paired_manifest.get("overall") != "pass":
        raise PublicationError(
            f"配对 fixture 未通过: results.status={paired_results.get('status')!r}, "
            f"fixture_manifest.overall={paired_manifest.get('overall')!r}"
        )
    paired_check = paired_results.get("checks", {}).get("fixture_active_call_ui", {})
    if not isinstance(paired_check, dict) or paired_check.get("status") != "pass":
        raise PublicationError("配对 fixture 的 fixture_active_call_ui 检查未通过")
    safety = paired_manifest.get("safety", {})
    required_safety = {
        "real_sms_sent": False,
        "real_sms_received": False,
        "sip_registration_attempted": False,
        "real_call_started": False,
        "sip_telecom_audio_carrier_call_started": False,
        "call_ui_state_is_local_synthetic_coordinator_only": True,
        "wifi_and_mobile_data_disabled": True,
    }
    if not isinstance(safety, dict) or any(safety.get(name) is not expected
                                            for name, expected in required_safety.items()):
        raise PublicationError("配对 fixture safety 元数据未确认离线合成 UI 范围")
    paired_artifacts = paired_results.get("artifacts", {})
    if not isinstance(paired_artifacts, dict) or set(paired_artifacts.get("synthetic_call_screens", [])) != set(CALL_SCREENSHOTS):
        raise PublicationError("配对 fixture 没有登记全部四张合成通话截图")
    paired_stages = {
        item.get("name"): item
        for item in paired_manifest.get("stages", [])
        if isinstance(item, dict)
    }

    for screenshot in SCREENSHOTS:
        source = artifact_dir / screenshot.source
        if not source.is_file() or source.is_symlink():
            raise PublicationError(f"缺少必需截图: {screenshot.source}")
        if screenshot.source.startswith("paired-fixture/"):
            if screenshot.paired_stage:
                stage = paired_stages.get(screenshot.paired_stage)
                if not isinstance(stage, dict) or stage.get("status") != "pass":
                    raise PublicationError(f"配对 fixture 截图阶段未通过: {screenshot.paired_stage}")
                expected_png = Path(screenshot.source).name
                if stage.get("screenshot") != expected_png:
                    raise PublicationError(f"配对 fixture 截图阶段文件不匹配: {screenshot.paired_stage}")
        else:
            expected_png = Path(screenshot.source).name
            if host_screenshots.get(Path(expected_png).stem) != expected_png:
                raise PublicationError(f"未配对 UI 截图清单未登记必需阶段: {expected_png}")

    for filename in CALL_SCREENSHOTS:
        stage = paired_stages.get(filename.removesuffix(".png"))
        if not isinstance(stage, dict) or stage.get("status") != "pass" or stage.get("screenshot") != filename:
            raise PublicationError(f"合成通话截图阶段未通过或文件名错误: {filename}")
    return host_results, host_manifest, paired_results, paired_manifest, paired_result_path


def _markdown_cell(value: Any) -> str:
    return str(value).replace("|", "\\|").replace("\r", " ").replace("\n", " ")


def _render_readme(metadata: dict[str, str], host_results: dict[str, Any],
                   paired_results: dict[str, Any], apk_path: Path, apk_sha256: str,
                   apk_size: int, screenshots: list[dict[str, Any]], reports: list[dict[str, Any]],
                   generated_at: str) -> str:
    lines = [
        "# Android 主机三标签 UI 验证",
        "",
        "本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。",
        "",
        "## 运行来源",
        "",
        f"- 仓库：`{metadata['repo']}`",
        f"- 源提交：`{metadata['source_commit']}`",
        f"- GitHub Actions run：[{metadata['run_id']}](https://github.com/{metadata['repo']}/actions/runs/{metadata['run_id']})",
        f"- artifact ID：`{metadata['artifact_id']}`",
        f"- artifact digest：`{metadata['artifact_digest']}`",
        f"- APK：`{apk_path.name}`，SHA-256 `{apk_sha256}`，{apk_size} 字节",
        f"- artifact 内未配对 smoke：`{host_results['overall']}`（{host_results.get('generated_at_utc', '时间未记录')}）",
        f"- artifact 内配对 fixture：`{paired_results['status']}`（{paired_results.get('generated_at_utc', '时间未记录')}）",
        f"- 报告生成时间：`{generated_at}`",
        "",
        "## 验证范围",
        "",
        "未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。",
        "",
        "四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。",
        "",
        "本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。",
        "",
        "## 截图与文件校验",
        "",
        "| 页面 | PNG | 尺寸 | 文件 SHA-256 |",
        "|---|---|---:|---|",
    ]
    for screenshot in screenshots:
        lines.append(
            f"| {_markdown_cell(screenshot['description'])} "
            f"| [`{screenshot['path']}`]({screenshot['path']}) "
            f"| {screenshot['width']} × {screenshot['height']} "
            f"| `{screenshot['sha256']}` |"
        )
    lines.extend(["", "## 截图", ""])
    last_category = None
    for screenshot in screenshots:
        if screenshot["category"] != last_category:
            last_category = screenshot["category"]
            lines.extend([f"### {last_category}", ""])
        lines.extend([f"#### {screenshot['description']}", "", f"![{screenshot['description']}]({screenshot['path']})", ""])
    lines.extend([
        "## 结果文件校验",
        "",
        "| artifact 文件 | SHA-256 |",
        "|---|---|",
    ])
    lines.extend(f"| `{report['path']}` | `{report['sha256']}` |" for report in reports)
    lines.extend([
        "",
        "初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。",
        "",
    ])
    return "\n".join(lines)


def _stage_and_publish(output_dir: Path, screenshot_data: list[tuple[Screenshot, bytes, int, int, str]],
                       readme: str, verified_run: dict[str, Any]) -> None:
    output_dir = output_dir.absolute()
    parent = output_dir.parent
    if output_dir.is_symlink():
        raise PublicationError(f"output directory 不可为符号链接: {output_dir}")
    parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix=f".{output_dir.name}.stage-", dir=parent))
    backup: Path | None = None
    try:
        if output_dir.exists():
            if not output_dir.is_dir():
                raise PublicationError(f"output path 不是目录: {output_dir}")
            shutil.copytree(output_dir, stage, dirs_exist_ok=True, symlinks=True)

        images = stage / "images"
        if images.is_symlink():
            images.unlink()
        elif images.exists():
            shutil.rmtree(images)
        images.mkdir(parents=True)

        screenshot_records = []
        for screenshot, data, width, height, digest in screenshot_data:
            destination_rel = Path("images") / screenshot.source
            destination = stage / destination_rel
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
            if _sha256_file(destination) != digest:
                raise PublicationError(f"复制后的截图 SHA-256 不匹配: {destination_rel}")
            screenshot_records.append({
                "category": screenshot.category,
                "description": screenshot.description,
                "source": screenshot.source,
                "path": destination_rel.as_posix(),
                "width": width,
                "height": height,
                "size_bytes": len(data),
                "sha256": digest,
            })

        # The report is rendered after copying so its hash table covers the
        # bytes that are actually present in the publication directory.
        verified_run["screenshots"] = screenshot_records
        (stage / "README.md").write_text(readme, encoding="utf-8")
        (stage / "verified-run.json").write_text(
            json.dumps(verified_run, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )

        if output_dir.exists():
            backup = parent / f".{output_dir.name}.backup-{uuid.uuid4().hex}"
            os.replace(output_dir, backup)
        try:
            os.replace(stage, output_dir)
        except Exception:
            if backup is not None and backup.exists():
                os.replace(backup, output_dir)
                backup = None
            raise
        if backup is not None:
            shutil.rmtree(backup)
    finally:
        if stage.exists():
            shutil.rmtree(stage)


def publish(args: argparse.Namespace) -> Path:
    metadata = _validate_metadata(args)
    artifact_dir = Path(args.artifact_dir).resolve()
    apk_path = Path(args.apk).resolve()
    output_dir = Path(args.output_dir)
    host_results, host_manifest, paired_results, paired_manifest, paired_result_path = _validate_sources(artifact_dir, apk_path)

    screenshot_data: list[tuple[Screenshot, bytes, int, int, str]] = []
    for screenshot in SCREENSHOTS:
        source = artifact_dir / screenshot.source
        data, width, height, digest = _validate_png(source)
        screenshot_data.append((screenshot, data, width, height, digest))

    apk_sha256 = _sha256_file(apk_path)
    apk_size = apk_path.stat().st_size
    reports_to_hash = (
        ("results.json", artifact_dir / "results.json"),
        ("screenshot_manifest.json", artifact_dir / "screenshot_manifest.json"),
        ("paired-fixture/results.json", paired_result_path),
        ("paired-fixture/fixture_manifest.json", artifact_dir / "paired-fixture" / "fixture_manifest.json"),
    )
    reports = []
    for name, path in reports_to_hash:
        if not path.is_file() or path.is_symlink():
            raise PublicationError(f"缺少必需的结果清单: {name}")
        reports.append({"path": name, "sha256": _sha256_file(path), "size_bytes": path.stat().st_size})

    generated_at = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    screenshot_records = [
        {
            "category": screenshot.category,
            "description": screenshot.description,
            "source": screenshot.source,
            "path": (Path("images") / screenshot.source).as_posix(),
            "width": width,
            "height": height,
            "size_bytes": len(data),
            "sha256": digest,
        }
        for screenshot, data, width, height, digest in screenshot_data
    ]
    verified_run: dict[str, Any] = {
        "schema_version": 1,
        "overall": "pass",
        "generated_at_utc": generated_at,
        "source_commit": metadata["source_commit"],
        "repository": metadata["repo"],
        "run_id": metadata["run_id"],
        "artifact": {"id": metadata["artifact_id"], "digest": metadata["artifact_digest"]},
        "apk": {"filename": apk_path.name, "sha256": apk_sha256, "size_bytes": apk_size},
        "results": {
            "unpaired": {"path": "results.json", "overall": host_results["overall"]},
            "paired": {"path": "paired-fixture/results.json", "status": paired_results["status"],
                       "fixture_manifest_overall": paired_manifest["overall"],
                       "fixture_active_call_ui": "pass"},
        },
        "result_files": reports,
        "screenshots": screenshot_records,
    }
    readme = _render_readme(metadata, host_results, paired_results, apk_path, apk_sha256,
                            apk_size, screenshot_records, reports, generated_at)
    _stage_and_publish(output_dir, screenshot_data, readme, verified_run)
    return output_dir


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact-dir", required=True, help="下载并解压后的 Android UI smoke artifact 目录")
    parser.add_argument("--apk", required=True, help="artifact 中实际测试的 debug APK 文件")
    parser.add_argument("--output-dir", required=True, help="长期保存验证报告的目录")
    parser.add_argument("--source-commit", required=True, help="artifact 对应的完整源提交 SHA")
    parser.add_argument("--run-id", required=True, help="GitHub Actions run ID")
    parser.add_argument("--repo", required=True, help="GitHub 仓库 owner/name")
    parser.add_argument("--artifact-id", required=True, help="GitHub artifact ID")
    parser.add_argument("--artifact-digest", required=True, help="GitHub artifact SHA-256 digest")
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        output = publish(args)
    except PublicationError as exc:
        print(f"拒绝发布 Android UI 验证报告：{exc}", file=sys.stderr)
        return 2
    except OSError as exc:
        print(f"写入 Android UI 验证报告失败：{exc}", file=sys.stderr)
        return 2
    print(f"Android UI 验证报告已生成：{output / 'README.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
