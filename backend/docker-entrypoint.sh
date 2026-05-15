#!/bin/sh
set -eu

mkdir -p /app/logs/archive
chown -R spring:spring /app/logs

exec gosu spring "$@"
