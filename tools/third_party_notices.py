#!/usr/bin/env python3
"""Writes the licenses of the components built into the app, for the repository and for the app.

Run from the repository root after a dependency change, once `cargo fetch` has downloaded the Rust
components: python3 tools/third_party_notices.py
The Android components are listed below by hand, from the release runtime classpath.
"""
import html
import json
import os
import re
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MARKDOWN = os.path.join(ROOT, "THIRD_PARTY_NOTICES.md")
APP_PAGE = os.path.join(ROOT, "app", "src", "main", "res", "raw", "third_party_notices.html")
RUST_TARGET = "aarch64-linux-android"
RUST_PACKAGES = ["adaway-android", "adaway-root"]

ANDROID = [
    ("AndroidX (Activity, Annotation, Arch Core, Autofill, Collection, Compose, Concurrent, Core, Custom View, "
     "DataStore, Emoji2, Graphics, Interpolator, Lifecycle, Navigation, Navigation Event, Paging, Profile Installer, "
     "Room, Saved State, SQLite, Startup, Tracing, Versioned Parcelable, Window, WorkManager)",
     "https://developer.android.com/jetpack/androidx"),
    ("Kotlin standard library", "https://github.com/JetBrains/kotlin"),
    ("kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines"),
    ("kotlinx.serialization", "https://github.com/Kotlin/kotlinx.serialization"),
    ("JetBrains Java Annotations", "https://github.com/JetBrains/java-annotations"),
    ("libsu", "https://github.com/topjohnwu/libsu"),
    ("Okio", "https://github.com/square/okio"),
    ("Guava ListenableFuture", "https://github.com/google/guava"),
    ("JSpecify", "https://github.com/jspecify/jspecify"),
]
ANDROID_LICENSE = "Apache-2.0"

LICENSE_TITLES = {
    "Apache-2.0": "Apache License 2.0",
    "MIT": "MIT License",
    "ISC": "ISC License",
    "BSD-3-Clause": "BSD 3-Clause License",
    "CDLA-Permissive-2.0": "Community Data License Agreement Permissive 2.0",
}


def rust_components():
    """The Rust components shipped in the app: the normal dependencies, without the compile-time macros."""
    tree = subprocess.run(
        ["cargo", "tree", "--offline", "-e", "normal,no-proc-macro", "--target", RUST_TARGET, "--prefix", "none", "-f", "{p}"]
        + [arg for package in RUST_PACKAGES for arg in ("-p", package)],
        cwd=os.path.join(ROOT, "rust"), capture_output=True, text=True, check=True,
    ).stdout
    shipped = {
        tuple(line.split()[:2]) for line in tree.splitlines()
        if line.strip() and not line.startswith("adaway-")
    }
    metadata = json.loads(subprocess.run(
        ["cargo", "metadata", "--offline", "--format-version", "1", "--filter-platform", RUST_TARGET],
        cwd=os.path.join(ROOT, "rust"), capture_output=True, text=True, check=True,
    ).stdout)
    packages = [p for p in metadata["packages"] if (p["name"], "v" + p["version"]) in shipped]
    return sorted(packages, key=lambda p: p["name"])


def applied_license(published):
    """Apache 2.0 when offered as a choice, as for the Android components; else the published terms."""
    choices = [choice.strip(" ()") for choice in re.split(r"\s+OR\s+|/", published)]
    if "Apache-2.0" in choices:
        return "Apache-2.0"
    if "MIT" in choices:
        return "MIT"
    return published


def license_files(package):
    """The license texts of a component under terms other than Apache 2.0, read from its sources."""
    directory = os.path.dirname(package["manifest_path"])
    texts = []
    for name in sorted(os.listdir(directory)):
        upper = name.upper()
        if not upper.startswith("LICENSE") or "APACHE" in upper:
            continue
        with open(os.path.join(directory, name)) as file:
            text = file.read().strip("\n")
        if "Apache License" in text[:200]:
            continue
        texts.append("\n".join(re.sub(r"^// ?", "", line) for line in text.splitlines()))
    return texts


def apache_text(packages):
    for package in packages:
        directory = os.path.dirname(package["manifest_path"])
        for name in os.listdir(directory):
            if "APACHE" in name.upper():
                with open(os.path.join(directory, name)) as file:
                    return file.read().strip("\n")
    raise SystemExit("No Apache 2.0 license text found")


def sections(packages):
    """The license texts by license: the Apache 2.0 text once, the others per component."""
    result = [("Apache-2.0", None, apache_text(packages))]
    for package in packages:
        applied = applied_license(package["license"])
        if applied == "Apache-2.0":
            continue
        for license_id in LICENSE_TITLES:
            if license_id != "Apache-2.0" and license_id in applied:
                result.append((license_id, package["name"], "\n\n".join(license_files(package))))
                break
    return sorted(result, key=lambda section: (list(LICENSE_TITLES).index(section[0]), section[1] or ""))


def markdown(packages, texts):
    lines = [
        "# Third-party notices", "",
        "AdAway is licensed under the [GNU General Public License v3.0 or later](LICENSE.md). It is built with the "
        "open source components below, whose licenses are reproduced at the end of this file.", "",
        "When a Rust component offers a choice of licenses, the one applied in AdAway is named in the "
        "\"Applied license\" column.", "",
        "This file is written by `tools/third_party_notices.py`.", "",
        "## Android components", "",
        "| Component | License |", "| --- | --- |",
    ]
    lines += [f"| [{name}]({url}) | {ANDROID_LICENSE} |" for name, url in ANDROID]
    lines += [
        "", "## Rust components", "",
        "The components built into the native engine and the root processes.", "",
        "| Component | Published license | Applied license |", "| --- | --- | --- |",
    ]
    lines += [
        f"| [{p['name']}]({p['repository'].removesuffix('.git')}) | {p['license']} | {applied_license(p['license'])} |"
        for p in packages
    ]
    lines += ["", "## License texts", ""]
    current = None
    for license_id, component, text in texts:
        if license_id != current:
            current = license_id
            lines += [f"### {LICENSE_TITLES[license_id]}", ""]
            if license_id == "Apache-2.0":
                lines += ["Applies to the Android components and to the Rust components marked Apache-2.0.", ""]
        if component:
            lines += [f"#### {component}", ""]
        lines += ["```text", text, "```", ""]
    return "\n".join(lines).rstrip("\n") + "\n"


def app_page(packages, texts):
    """The same notices for the app, in the HTML subset Android renders as text."""
    def paragraphs(text):
        blocks = re.split(r"\n\s*\n", text)
        return "".join(f"<p>{html.escape(' '.join(block.split()))}</p>" for block in blocks if block.strip())

    parts = [
        "<p>AdAway is licensed under the GNU General Public License v3.0 or later. It is built with the open "
        "source components below.</p>",
        "<h3>Android components</h3><ul>",
    ]
    parts += [f'<li><a href="{url}">{html.escape(name)}</a>: {ANDROID_LICENSE}</li>' for name, url in ANDROID]
    parts.append("</ul><h3>Rust components</h3><ul>")
    for p in packages:
        applied = applied_license(p["license"])
        terms = p["license"] if applied == p["license"] else f"{p['license']}, {applied} applied"
        parts.append(f'<li><a href="{p["repository"].removesuffix(".git")}">{p["name"]}</a>: {html.escape(terms)}</li>')
    parts.append("</ul>")
    current = None
    for license_id, component, text in texts:
        if license_id != current:
            current = license_id
            parts.append(f"<h3>{LICENSE_TITLES[license_id]}</h3>")
        if component:
            parts.append(f"<p><b>{component}</b></p>")
        parts.append(paragraphs(text))
    return "\n".join(parts) + "\n"


def main():
    packages = rust_components()
    texts = sections(packages)
    with open(MARKDOWN, "w") as file:
        file.write(markdown(packages, texts))
    with open(APP_PAGE, "w") as file:
        file.write(app_page(packages, texts))


if __name__ == "__main__":
    main()
