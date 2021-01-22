#!/usr/bin/env bash
set -euox pipefail

# Adjust permissions of the mounted directory so that user builder can access it.
chown -R 10000:10001 /home/builder/workspace

# Drop root privileges and build the package.
gosu builder /home/builder/build.sh
