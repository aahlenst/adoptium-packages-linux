#!/usr/bin/env bash
# The UID/GID of the current user in the container might not match the UID/GID of the user running the container on the
# host. This causes permission problems, for example when writing to mounted directories. This script adjusts the
# UID/GID of the user in the container to match the UID/GID of the user running the container before dropping root
# privileges and continuing as unprivileged user.

set -euox pipefail

# The directory mounted into the container has the UID/GID of the host user.
HOST_USER_UID=$(stat -c "%u" /home/builder/workspace)
HOST_USER_GID=$(stat -c "%g" /home/builder/workspace)

# Get UID/GID of user inside the container.
CONTAINER_USER_UID=$(id -u builder)
CONTAINER_USER_GID=$(id -g builder)

# If they do not match, change the user inside the container.
if [ -n "$HOST_USER_UID" ] && [ "$HOST_USER_UID" != "$CONTAINER_USER_UID" ]; then
	usermod  -u "$HOST_USER_UID" -o builder
fi
if [ -n "$HOST_USER_GID" ] && [ "$HOST_USER_GID" != "$CONTAINER_USER_GID" ]; then
	groupmod -g "$HOST_USER_GID" -o builder
fi

# Drop root privileges and continue as unprivileged user.
gosu builder /home/builder/build.sh
