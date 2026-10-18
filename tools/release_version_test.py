#!/usr/bin/env python3
"""release_version.py without the network: the next stable and preview versions."""
import unittest

from release_version import bump, decide, latest_stable

MAIN = "refs/heads/main"


def release(name, tag, prerelease=False, created="2026-10-18T00:00:00Z", draft=False):
    return {"name": name, "tag_name": tag, "prerelease": prerelease,
            "created_at": created, "draft": draft}


# The builds before versions: the commit in the name, all of them stable.
OLD = [
    release("Xendroid+ 726ef8f8 (build 79)", "XenDroid-v79-726ef8f8", created="2026-10-18T19:54:00Z"),
    release("Xendroid+ 29223981 (build 87)", "XenDroid-v87-29223981", created="2026-10-18T20:10:00Z"),
]


class Versions(unittest.TestCase):
    def test_bumps(self):
        self.assertEqual(bump((1, 2, 3), "patch"), (1, 2, 4))
        self.assertEqual(bump((1, 2, 3), "minor"), (1, 3, 0))
        self.assertEqual(bump((1, 2, 3), "major"), (2, 0, 0))
        with self.assertRaises(ValueError):
            bump((1, 2, 3), "none")

    def test_first_stable_counts_from_zero_and_continues_the_newest_release(self):
        self.assertEqual(latest_stable(OLD), ((0, 0, 0), "XenDroid-v87-29223981"))
        self.assertEqual(decide("workflow_dispatch", MAIN, 90, "major", OLD),
                         ("stable", "1.0.0", "XenDroid-v87-29223981"))
        self.assertEqual(decide("workflow_dispatch", MAIN, 90, "minor", OLD),
                         ("stable", "0.1.0", "XenDroid-v87-29223981"))

    def test_next_stable_follows_the_highest_version_not_the_newest_release(self):
        releases = OLD + [
            release("Xendroid+ 1.2.0", "XenDroid-v95-aaaaaaaa", created="2026-11-01T00:00:00Z"),
            release("Xendroid+ 1.1.4", "XenDroid-v99-bbbbbbbb", created="2026-11-02T00:00:00Z"),
            release("Xendroid+ 1.3.0-preview.120", "XenDroid-v120-cccccccc", prerelease=True,
                    created="2026-11-03T00:00:00Z"),
            release("Xendroid+ 9.9.9", "XenDroid-v121-dddddddd", draft=True),
        ]
        self.assertEqual(decide("workflow_dispatch", MAIN, 130, "patch", releases),
                         ("stable", "1.2.1", "XenDroid-v95-aaaaaaaa"))

    def test_a_push_to_main_is_a_preview_of_the_next_minor(self):
        self.assertEqual(decide("push", MAIN, 88, "", OLD)[:2], ("preview", "0.1.0-preview.88"))
        releases = OLD + [release("Xendroid+ 1.0.0", "XenDroid-v90-eeeeeeee")]
        self.assertEqual(decide("push", MAIN, 91, "", releases),
                         ("preview", "1.1.0-preview.91", "XenDroid-v90-eeeeeeee"))

    def test_nothing_is_published_off_main_or_without_a_bump(self):
        self.assertEqual(decide("pull_request", "refs/pull/50/merge", 92, "", OLD), ("none", "", ""))
        self.assertEqual(decide("workflow_dispatch", MAIN, 93, "none", OLD), ("none", "", ""))
        self.assertEqual(decide("workflow_dispatch", "refs/heads/test", 94, "major", OLD),
                         ("none", "", ""))
        self.assertEqual(decide("push", "refs/heads/feature", 95, "", OLD), ("none", "", ""))

    def test_no_release_at_all(self):
        self.assertEqual(decide("workflow_dispatch", MAIN, 1, "major", []), ("stable", "1.0.0", ""))


if __name__ == "__main__":
    unittest.main()
