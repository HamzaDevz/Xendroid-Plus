#!/usr/bin/env python3
"""The version of a Xendroid+ CI build (.github/workflows/XenDroid.yml).

Versions follow SemVer and live in the GitHub releases themselves: a stable release
is named "Xendroid+ X.Y.Z" and gets the git tag vX.Y.Z, so there is no version file
to bump. The release tag keeps the XenDroid-v<build>-<sha> form the updater of
installed apps reads (updater/ReleaseTags.kt).

- A manual run on main with release=patch|minor|major publishes the stable version
  after the newest one (the first counts from 0.0.0: major gives 1.0.0).
- A push to main publishes a preview of the next minor version, X.(Y+1).0-preview.N,
  N being the build number.
- Anything else (pull requests, test builds) publishes nothing; the build keeps its
  commit as the version name.

Prints GITHUB_OUTPUT lines: channel=stable|preview|none, version=..., and
previous_stable=TAG, the newest stable release, whose notes a stable release
continues from.

usage: release_version.py   (reads GITHUB_EVENT_NAME, GITHUB_REF, GITHUB_RUN_NUMBER,
                             GITHUB_REPOSITORY, INPUT_RELEASE and GH_TOKEN)
"""
import json
import os
import re
import sys
import urllib.request

STABLE_NAME = re.compile(r"^Xendroid\+ (\d+)\.(\d+)\.(\d+)$")
BUMPS = ("patch", "minor", "major")


def bump(version, kind):
    major, minor, patch = version
    if kind == "major":
        return (major + 1, 0, 0)
    if kind == "minor":
        return (major, minor + 1, 0)
    if kind == "patch":
        return (major, minor, patch + 1)
    raise ValueError(f"unknown bump {kind!r}")


def latest_stable(releases):
    """((X, Y, Z), tag): the highest stable version named "Xendroid+ X.Y.Z", or
    (0, 0, 0) with the newest stable release of any name (the builds before
    versions had their commit in the name)."""
    stable = [r for r in releases if not r.get("draft") and not r.get("prerelease")]
    named = []
    for r in stable:
        m = STABLE_NAME.match((r.get("name") or "").strip())
        if m:
            named.append((tuple(int(x) for x in m.groups()), r["tag_name"]))
    if named:
        return max(named)
    newest = max(stable, key=lambda r: r.get("created_at") or "", default=None)
    return ((0, 0, 0), newest["tag_name"] if newest else "")


def decide(event, ref, build, release, releases):
    """(channel, version, previous_stable) of this run."""
    if ref != "refs/heads/main":
        return ("none", "", "")
    version, previous = latest_stable(releases)
    if event == "workflow_dispatch" and release in BUMPS:
        return ("stable", "%d.%d.%d" % bump(version, release), previous)
    if event == "push":
        major, minor, _ = version
        return ("preview", f"{major}.{minor + 1}.0-preview.{build}", previous)
    return ("none", "", "")


def releases_of(repo, token):
    out, page = [], 1
    while page <= 20:
        request = urllib.request.Request(
            f"https://api.github.com/repos/{repo}/releases?per_page=100&page={page}",
            headers={"Accept": "application/vnd.github+json",
                     **({"Authorization": f"Bearer {token}"} if token else {})})
        with urllib.request.urlopen(request, timeout=30) as response:
            batch = json.load(response)
        if not batch:
            break
        out += batch
        page += 1
    return out


def main():
    env = os.environ
    event, ref = env.get("GITHUB_EVENT_NAME", ""), env.get("GITHUB_REF", "")
    release = (env.get("INPUT_RELEASE") or "none").strip()
    releases = []
    if ref == "refs/heads/main" and (event == "push" or release in BUMPS):
        releases = releases_of(env["GITHUB_REPOSITORY"], env.get("GH_TOKEN", ""))
    channel, version, previous = decide(event, ref, int(env.get("GITHUB_RUN_NUMBER", "0")),
                                        release, releases)
    print(f"channel={channel}")
    print(f"version={version}")
    print(f"previous_stable={previous}")
    print(f"{channel} {version or '(commit)'} after {previous or 'nothing'}", file=sys.stderr)


if __name__ == "__main__":
    main()
