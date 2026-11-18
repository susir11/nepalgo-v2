#!/bin/sh
# Container entrypoint: refuse to start unless every required environment
# variable is set, and say EXACTLY which ones are missing (instead of a cryptic
# Spring stack trace). Then hand PID 1 to the JVM with exec (clean signals).
missing=""
for v in $REQUIRED_ENV; do
  eval "val=\${$v}"
  [ -z "$val" ] && missing="$missing $v"
done
if [ -n "$missing" ]; then
  echo "FATAL: missing required environment variable(s):$missing" >&2
  echo "       See RUNBOOK.md -> 'Environment variables'." >&2
  exit 64
fi
exec java org.springframework.boot.loader.launch.JarLauncher "$@"
