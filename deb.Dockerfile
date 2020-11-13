FROM debian:buster

# Combine apt-get update with apt-get install to prevent stale package indexes.
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y build-essential \
 		debhelper sudo lsb-release reprepro gosu java-common libasound2 libc6 libx11-6 \
 		libfontconfig1 libfreetype6 libxext6 libxi6 libxrender1 libxtst6 zlib1g

# Create unprivileged user for building
RUN useradd -m -d /home/builder builder

# Prepare entrypoint and build scripts
ADD entrypoint.sh /entrypoint.sh
RUN printf '#!/usr/bin/env bash\n\
set -euxo pipefail\n\
cd /home/builder/workspace/generated/packaging\n\
dpkg-buildpackage -us -uc -b\n\
mv /home/builder/workspace/generated/*.{deb,changes,buildinfo} /home/builder/out\n\
changestool /home/builder/out/*.changes setdistribution $VERSIONS\n\
'\
>> /home/builder/build.sh
RUN chmod +x /home/builder/build.sh

ENTRYPOINT /bin/bash /entrypoint.sh
