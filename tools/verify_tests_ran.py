#!/usr/bin/env python3
"""Verifies :app:testDebugUnitTest actually executed a nonzero number of tests.

docs/ENGINEERING.md documents the lesson this operationalizes: "A passing test suite is
not proof a specific test ran... NO-SOURCE means a module has no tests." That corollary
existed only as something a human had to remember to check by eye — `:wear:testDebugUnitTest`
being NO-SOURCE was noted, not gated. This script makes the check automatic and puts it
in CI: Gradle reports `BUILD SUCCESSFUL` whether a test task ran 263 tests or zero, and a
future refactor that accidentally excludes the test source set, misconfigures a variant,
or renames a directory Gradle silently stops picking up would still show green — exactly
the shape of the founding incident this project's whole verification discipline responds
to (32887d3: a change believed shipped while its test's evidence had never executed once).

Self-test:  python3 tools/verify_tests_ran.py --selftest
  Builds two fixture report directories — one with real JUnit XML results, one empty
  (the NO-SOURCE shape) — and asserts the detector accepts the first and rejects the
  second. A version of this script that cannot tell them apart must not be trusted to
  gate CI.
"""
import glob
import os
import re
import sys
import tempfile

TESTS_ATTR = re.compile(r'<testsuite\b[^>]*\btests="(\d+)"')

DEFAULT_REPORT_DIR = os.path.join('app', 'build', 'test-results', 'testDebugUnitTest')


def count_tests(report_dir):
    """Returns (file_count, total_tests) across every TEST-*.xml in report_dir."""
    files = sorted(glob.glob(os.path.join(report_dir, 'TEST-*.xml')))
    total = 0
    for path in files:
        content = open(path).read()
        m = TESTS_ATTR.search(content)
        if m:
            total += int(m.group(1))
    return len(files), total


FIXTURE_WITH_TESTS = '<testsuite name="com.example.FooTest" tests="3" failures="0" errors="0" skipped="0"></testsuite>'


def selftest():
    d = tempfile.mkdtemp()
    populated = os.path.join(d, 'populated')
    empty = os.path.join(d, 'empty')
    os.makedirs(populated, exist_ok=True)
    os.makedirs(empty, exist_ok=True)
    open(os.path.join(populated, 'TEST-com.example.FooTest.xml'), 'w').write(FIXTURE_WITH_TESTS)

    pop_files, pop_total = count_tests(populated)
    empty_files, empty_total = count_tests(empty)

    if pop_files == 1 and pop_total == 3 and empty_files == 0 and empty_total == 0:
        print('SELFTEST PASS — detector distinguishes a real test run from a NO-SOURCE one')
        return 0
    print('SELFTEST FAIL — detector did not distinguish the two fixture cases.')
    print(f'  populated: files={pop_files} tests={pop_total}  empty: files={empty_files} tests={empty_total}')
    return 1


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    if selftest():
        sys.exit(2)
    report_dir = next((a for a in sys.argv[1:] if not a.startswith('-')), DEFAULT_REPORT_DIR)
    file_count, total_tests = count_tests(report_dir)
    print(f'report_dir={report_dir}  files={file_count}  tests={total_tests}')
    if file_count == 0:
        print(f'FAIL: no JUnit XML reports found under {report_dir} — testDebugUnitTest may not have run at all.')
        sys.exit(1)
    if total_tests == 0:
        print('FAIL: test reports exist but contain zero tests (the NO-SOURCE shape) — '
              'testDebugUnitTest ran without exercising any actual test.')
        sys.exit(1)
    print(f'PASS: {total_tests} tests actually ran.')
    sys.exit(0)
