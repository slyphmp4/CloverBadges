import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile


AGENT = "CloverBadgesTests/1.14.4 (https://github.com/slyphmp4/CloverBadges)"


def download_release(repository, tag, name, destination):
    endpoint = f"https://api.github.com/repos/{repository}/releases/tags/{tag}"
    request = urllib.request.Request(endpoint, headers={"User-Agent": AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        metadata = json.load(response)
    asset = next(item for item in metadata["assets"] if item["name"] == name)
    expected = asset.get("digest", "")
    if not expected.startswith("sha256:"):
        raise RuntimeError(f"Missing release checksum: {name}")
    request = urllib.request.Request(asset["browser_download_url"], headers={"User-Agent": AGENT})
    digest = hashlib.sha256()
    with urllib.request.urlopen(request, timeout=60) as response, destination.open("wb") as output:
        while chunk := response.read(1024 * 1024):
            digest.update(chunk)
            output.write(chunk)
    if digest.hexdigest() != expected.removeprefix("sha256:"):
        destination.unlink()
        raise RuntimeError(f"Release SHA-256 verification failed: {name}")


def download_paper(version, build, destination):
    endpoint = f"https://fill.papermc.io/v3/projects/paper/versions/{version}/builds/{build}"
    request = urllib.request.Request(endpoint, headers={"User-Agent": AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        metadata = json.load(response)
    artifact = metadata["downloads"]["server:default"]
    request = urllib.request.Request(artifact["url"], headers={"User-Agent": AGENT})
    digest = hashlib.sha256()
    with urllib.request.urlopen(request, timeout=60) as response, destination.open("wb") as output:
        while chunk := response.read(1024 * 1024):
            digest.update(chunk)
            output.write(chunk)
    if digest.hexdigest() != artifact["checksums"]["sha256"]:
        destination.unlink()
        raise RuntimeError("Paper SHA-256 verification failed")


def run_phase(args, directory, phase):
    command = [
        args.java,
        "-Xms256M",
        "-Xmx1G",
        "-Dterminal.jline=false",
        "-Dterminal.ansi=false",
        "-Dcloverbadges.smoke=true",
        f"-Dcloverbadges.smoke.phase={phase}",
        f"-Dcloverbadges.smoke.plugins={','.join(args.expected_plugins)}",
        f"-Dcloverbadges.smoke.allow-renderer-fallback={str(getattr(args, 'allow_renderer_fallback', False)).lower()}",
        "-jar",
        "paper.jar",
        "--nogui",
    ]
    log = directory / f"{phase}.log"
    with log.open("w") as output:
        process = subprocess.Popen(command, cwd=directory, stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=180)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            raise RuntimeError(f"Server timed out during {phase}; log: {log}") from None
    report = directory / "plugins" / "CloverBadgesSmokeTest" / f"{phase}.yml"
    passed = report.exists() and re.search(r"^passed: true$", report.read_text(), re.MULTILINE)
    content = log.read_text(errors="replace")
    plugin_errors = re.search(
        r"(?:Error occurred while (?:enabling|disabling) CloverBadges\b|"
        r"for CloverBadges v[^\n]*generated an exception|"
        r"(?:ERROR|SEVERE)\][^\n]*\[CloverBadges\])",
        content,
    )
    if code != 0 or not passed or plugin_errors:
        lines = content.splitlines()
        failure = next((index for index, line in enumerate(lines) if "SMOKE FAIL" in line), max(0, len(lines) - 60))
        print("\n".join(lines[failure:failure + 45]), flush=True)
        raise RuntimeError(f"Smoke test failed during {phase}; log: {log}")
    summary = next(line for line in log.read_text(errors="replace").splitlines() if "SMOKE PASS" in line)
    print(summary, flush=True)
    print(f"Report: {report}", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--minecraft", required=True)
    parser.add_argument("--build", required=True, type=int)
    parser.add_argument("--java", default="java")
    parser.add_argument("--paper", type=Path)
    parser.add_argument("--work-dir", type=Path)
    parser.add_argument("--plugin", type=Path, action="append", default=[])
    parser.add_argument("--placeholderapi", action="store_true")
    parser.add_argument("--tab", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    if args.work_dir:
        directory = args.work_dir.resolve()
        if directory.exists() and any(directory.iterdir()):
            raise RuntimeError("Use a new empty work directory for this destructive test")
        directory.mkdir(parents=True, exist_ok=True)
    else:
        parent = root / "build" / "smoke"
        parent.mkdir(parents=True, exist_ok=True)
        directory = Path(tempfile.mkdtemp(prefix=f"paper-{args.minecraft}-", dir=parent))
    if args.paper:
        shutil.copy2(args.paper.resolve(), directory / "paper.jar")
    else:
        download_paper(args.minecraft, args.build, directory / "paper.jar")
    plugins = directory / "plugins"
    plugins.mkdir()
    main_jars = list((root / "build" / "libs").glob("CloverBadges-*.jar"))
    test_jars = list((root / "build" / "libs").glob("CloverBadgesSmokeTest-*.jar"))
    if len(main_jars) != 1 or len(test_jars) != 1:
        raise RuntimeError("Run ./gradlew clean build smokeTestJar before this test")
    for jar in main_jars + test_jars + args.plugin:
        shutil.copy2(jar.resolve(), plugins / jar.name)
    if args.placeholderapi:
        download_release("PlaceholderAPI/PlaceholderAPI", "2.12.3", "PlaceholderAPI-2.12.3.jar", plugins / "PlaceholderAPI-2.12.3.jar")
    if args.tab:
        download_release("NEZNAMY/TAB", "6.2.1", "TAB.v6.2.1.jar", plugins / "TAB-6.2.1.jar")
    args.expected_plugins = []
    for jar in plugins.glob("*.jar"):
        with zipfile.ZipFile(jar) as archive:
            metadata = archive.read("plugin.yml").decode()
        match = re.search(r"^name:\s*['\"]?([A-Za-z0-9_]+)", metadata, re.MULTILINE)
        if not match:
            raise RuntimeError(f"Missing plugin name: {jar}")
        args.expected_plugins.append(match.group(1))
    (directory / "eula.txt").write_text("eula=true\n")
    (directory / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n"
        "level-name=smoke-world\ngenerate-structures=false\n"
        "view-distance=2\nsimulation-distance=2\nmax-players=1\n"
        "enable-query=false\nenable-rcon=false\n"
    )
    print(f"Testing Paper {args.minecraft} build {args.build} in {directory}", flush=True)
    run_phase(args, directory, "exercise")
    run_phase(args, directory, "restore")


if __name__ == "__main__":
    try:
        main()
    except Exception as failure:
        print(str(failure), file=sys.stderr)
        sys.exit(1)
