#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <instrumentation-output-file>" >&2
  exit 2
fi

evidence="$1"

if [[ ! -f "$evidence" ]]; then
  echo "instrumentation evidence file missing: $evidence" >&2
  exit 1
fi

if grep -Eq '^FAILURES!!!\r?$' "$evidence"; then
  echo "instrumentation reported FAILURES: $evidence" >&2
  exit 1
fi

if ! grep -Eq '^OK \(1 test\)\r?$' "$evidence"; then
  echo "expected exactly one executed passing test (OK (1 test)): $evidence" >&2
  grep -E '^OK \(|^FAILURES!!!|^Tests run:' "$evidence" >&2 || true
  exit 1
fi

if grep -Eq '^OK \((0|[2-9][0-9]*) tests?\)\r?$' "$evidence"; then
  echo "unexpected instrumentation test count: $evidence" >&2
  exit 1
fi
