#!/usr/bin/env python3
"""Copy original final UI evidence and the tested APK into one small artifact.

The complete diagnostics/screenshots artifacts remain available separately.
This copy preserves bytes and original paths for artifact checksum verification.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-dir', type=Path, required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    workspace = Path.cwd().resolve()
    source = args.artifact_dir.resolve()
    output = args.output_dir.resolve()
    if output.exists():
        raise ValueError('Review evidence output must be a new directory')
    if output.is_relative_to(source) or source.is_relative_to(output):
        raise ValueError('Evidence input and output must be separate directories')
    names = {
        'app_foreground.png', 'input_before_rotation.png',
        'landscape_after_rotation.png', 'after_portrait_rotation.png',
        'folded_ui.png', 'unfolded_ui.png', 'hole_cutout_enabled.png',
        'main-backup-entry-host.png',
        'host_call_history.png', 'host_dialpad.png',
        'host_sms_thread_list.png', 'host_settings_tab.png',
    }
    names.update(stage + '.png' for stage in (
        'backup-overview', 'backup-password', 'backup-json-warning',
        'backup-xml-warning', 'backup-import-preview',
        'backup-import-repeat-preview', 'backup-imported-history',
        'backup-imported-history-count', 'backup-imported-history-inbound',
        'backup-imported-history-outbound', 'backup-reimported-history',
        'backup-reimported-history-inbound', 'backup-reimported-history-outbound',
        'backup-rotation-landscape', 'backup-rotation-portrait',
        'backup-cutout', 'backup-folded', 'backup-unfolded',
    ))
    selected = [path for path in source.rglob('*') if path.is_file() and
                (path.suffix in {'.json', '.txt', '.xml', '.log', '.md'} or
                 path.name in names)]
    # Every paired stage is a final documented viewport. Include its original
    # image, including any separately captured offline-state explanations.
    manifest = source / 'paired-fixture/fixture_manifest.json'
    if manifest.exists():
        for stage in json.loads(manifest.read_text())['stages']:
            value = stage.get('screenshot')
            if not value:
                continue
            path = Path(value)
            candidates = [path, manifest.parent / path, source / path]
            actual = next((p.resolve() for p in candidates if p.is_file()), None)
            if actual is None or not actual.is_relative_to(source):
                raise ValueError('Paired screenshot is missing or outside evidence')
            selected.append(actual)
    if args.apk.is_file():
        selected.append(args.apk.resolve())
    copied = {}
    for path in sorted(set(p.resolve() for p in selected)):
        relative = path.relative_to(workspace)
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        assert hashlib.sha256(target.read_bytes()).hexdigest() == digest
        copied[str(relative)] = digest
    output.mkdir(parents=True, exist_ok=True)
    (output / 'review-evidence-manifest.json').write_text(
        json.dumps({'format': 1, 'original_files_sha256': copied}, indent=2) + '\n')
    print(f'Copied {len(copied)} original files for review')


if __name__ == '__main__':
    main()
