/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.adoptium.cacertificates;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.nio.file.Path;

import static org.adoptium.cacertificates.TestContainersUtil.runShell;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Andreas Ahlenstorf
 */
class AptOperationsTest {

	@ParameterizedTest(name = "{0}:{1}")
	@ArgumentsSource(DebianFlavours.class)
	void packageSuccessfullyInstalled(String distribution, String codename) {
		Path hostDeb = DebFiles.hostDebPath();
		assertThat(hostDeb).exists();
		File containerDeb = new File("", hostDeb.toFile().getName());

		try (GenericContainer container = new GenericContainer(String.format("%s:%s", distribution, codename))) {
			container.withCommand("/bin/bash", "-c", "while true; do sleep 10; done")
				.withCopyFileToContainer(MountableFile.forHostPath(hostDeb), containerDeb.toString())
				.start();

			Container.ExecResult result;

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get update");
			assertThat(result.getExitCode()).isEqualTo(0);

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get install -y apt-utils");
			assertThat(result.getExitCode()).isEqualTo(0);

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get install -y " + containerDeb);
			assertThat(result.getExitCode()).isEqualTo(0);

			result = runShell(container, "apt-cache show adoptium-ca-certificates");
			assertThat(result.getExitCode()).isEqualTo(0);
			assertThat(result.getStdout())
				.contains("Package: adoptium-ca-certificates")
				.contains("Version: 1.0.0-1")
				.contains("Priority: optional")
				.contains("Architecture: all")
				.contains("Status: install ok installed");
		}
	}

	@ParameterizedTest(name = "{0}:{1}")
	@ArgumentsSource(DebianFlavours.class)
	void packageContentsMatchExpectations(String distribution, String codename) {
		Path hostDeb = DebFiles.hostDebPath();
		assertThat(hostDeb).exists();
		File containerDeb = new File("", hostDeb.toFile().getName());
		String packageContents = IOUtil.resourceAsString("/deb-contents.txt");

		try (GenericContainer container = new GenericContainer(String.format("%s:%s", distribution, codename))) {
			container.withCommand("/bin/bash", "-c", "while true; do sleep 10; done")
				.withCopyFileToContainer(MountableFile.forHostPath(hostDeb), containerDeb.toString())
				.start();

			Container.ExecResult result;

			result = runShell(container, "dpkg --contents " + containerDeb);
			assertThat(result.getExitCode())
				.overridingErrorMessage("dpkg --contents failed")
				.isEqualTo(0);
			assertThat(result.getStdout().replaceAll("\n\n", "\n"))
				.isEqualTo(packageContents);
		}
	}

	@ParameterizedTest(name = "{0}:{1}")
	@ArgumentsSource(DebianFlavours.class)
	void uninstallRemovesPackage(String distribution, String codename) {
		Path hostDeb = DebFiles.hostDebPath();
		assertThat(hostDeb).exists();
		File containerDeb = new File("", hostDeb.toFile().getName());

		try (GenericContainer container = new GenericContainer(String.format("%s:%s", distribution, codename))) {
			container.withCommand("/bin/bash", "-c", "while true; do sleep 10; done")
				.withCopyFileToContainer(MountableFile.forHostPath(hostDeb), containerDeb.toString())
				.start();

			Container.ExecResult result;

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get update");
			assertThat(result.getExitCode())
				.overridingErrorMessage("apt-get update failed")
				.isEqualTo(0);

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get install -y " + containerDeb);
			assertThat(result.getExitCode())
				.overridingErrorMessage("apt-get install -y %s failed", containerDeb)
				.isEqualTo(0);

			result = runShell(container, "test -f /etc/ssl/certs/adoptium/cacerts");
			assertThat(result.getExitCode())
				.overridingErrorMessage("/etc/ssl/certs/adoptium/cacerts is not a file")
				.isEqualTo(0);

			result = runShell(container, "test -f /etc/default/adoptium-ca-certificates");
			assertThat(result.getExitCode())
				.overridingErrorMessage("/etc/default/adoptium-ca-certificate is not a file")
				.isEqualTo(0);

			result = runShell(container, "stat -c \"%a %u %g\" /etc/ca-certificates/update.d/adoptium-cacerts");
			assertThat(result.getStdout()).isEqualToIgnoringNewLines("755 0 0");

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get remove -y adoptium-ca-certificates");
			assertThat(result.getExitCode())
				.overridingErrorMessage("apt-get remove -y adoptium-ca-certificates failed")
				.isEqualTo(0);

			result = runShell(container, "dpkg --list | grep adoptium-ca-certificates");
			assertThat(result.getStdout()).startsWith("rc");

			// Files must still exist because we haven't yet purged the package
			result = runShell(container, "test -f /etc/ssl/certs/adoptium/cacerts");
			assertThat(result.getExitCode())
				.overridingErrorMessage("/etc/ssl/certs/adoptium/cacerts is not a file")
				.isEqualTo(0);

			result = runShell(container, "test -f /etc/default/adoptium-ca-certificates");
			assertThat(result.getExitCode())
				.overridingErrorMessage("/etc/default/adoptium-ca-certificate is not a file")
				.isEqualTo(0);

			result = runShell(container, "stat -c \"%a %u %g\" /etc/ca-certificates/update.d/adoptium-cacerts");
			assertThat(result.getStdout()).isEqualToIgnoringNewLines("755 0 0");

			// The hook is still there, so we must ensure that it still works.
			result = runShell(container, "update-ca-certificates");
			assertThat(result.getExitCode())
				.overridingErrorMessage("update-ca-certificates returned exit code %d", result.getExitCode())
				.isEqualTo(0);
			assertThat(result.getStdout()).contains("/etc/ssl/certs/adoptium/cacerts successfully populated.");

			// Ensure that removal of `trust` can be handled.
			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get remove -y p11-kit");
			assertThat(result.getExitCode())
				.overridingErrorMessage("apt-get remove -y p11-kit returned exit code %d", result.getExitCode())
				.isEqualTo(0);

			result = runShell(container, "update-ca-certificates");
			assertThat(result.getExitCode())
				.overridingErrorMessage("update-ca-certificates returned exit code %d", result.getExitCode())
				.isEqualTo(0);
			assertThat(result.getStdout()).contains("Updates of adoptium-ca-certificates' keystore disabled.");

		}
	}

	@ParameterizedTest(name = "{0}:{1}")
	@ArgumentsSource(DebianFlavours.class)
	void purgeRemovesPackageContents(String distribution, String codename) {
		Path hostDeb = DebFiles.hostDebPath();
		assertThat(hostDeb).exists();
		File containerDeb = new File("", hostDeb.toFile().getName());

		try (GenericContainer container = new GenericContainer(String.format("%s:%s", distribution, codename))) {
			container.withCommand("/bin/bash", "-c", "while true; do sleep 10; done")
				.withCopyFileToContainer(MountableFile.forHostPath(hostDeb), containerDeb.toString())
				.start();

			Container.ExecResult result;

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get update");
			assertThat(result.getExitCode()).isEqualTo(0);

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get install -y " + containerDeb);
			assertThat(result.getExitCode()).isEqualTo(0);

			result = runShell(container, "test -f /etc/ssl/certs/adoptium/cacerts");
			assertThat(result.getExitCode()).isEqualTo(0);
			result = runShell(container, "test -f /etc/default/adoptium-ca-certificates");
			assertThat(result.getExitCode()).isEqualTo(0);
			result = runShell(container, "stat -c \"%a %u %g\" /etc/ca-certificates/update.d/adoptium-cacerts");
			assertThat(result.getStdout()).isEqualToIgnoringNewLines("755 0 0");

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get purge -y adoptium-ca-certificates");
			assertThat(result.getExitCode()).isEqualTo(0);

			// No results because package was installed from file, not repository. If it was installed from repository,
			// it would show up and we had to check the installation status.
			result = runShell(container, "dpkg --list | grep adoptium-ca-certificates | wc -l");
			assertThat(result.getStdout()).isEqualToIgnoringNewLines("0");

			// Files must still exist because we haven't yet purged the package
			result = runShell(container, "test -e /etc/ssl/certs/adoptium");
			assertThat(result.getExitCode()).isEqualTo(1);
			result = runShell(container, "test -e /etc/default/adoptium-ca-certificates");
			assertThat(result.getExitCode()).isEqualTo(1);
			result = runShell(container, "test -e /etc/ca-certificates/update.d/adoptium-cacerts");
			assertThat(result.getExitCode()).isEqualTo(1);
		}
	}

	@ParameterizedTest(name = "{0}:{1}")
	@ArgumentsSource(DebianFlavours.class)
	void reinstallationIsSuccessful(String distribution, String codename) {
		Path hostDeb = DebFiles.hostDebPath();
		assertThat(hostDeb).exists();
		File containerDeb = new File("", hostDeb.toFile().getName());

		try (GenericContainer container = new GenericContainer(String.format("%s:%s", distribution, codename))) {
			container.withCommand("/bin/bash", "-c", "while true; do sleep 10; done")
				.withCopyFileToContainer(MountableFile.forHostPath(hostDeb), containerDeb.toString())
				.start();

			Container.ExecResult result;

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get update");
			assertThat(result.getExitCode()).isEqualTo(0);

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get install -y " + containerDeb);
			assertThat(result.getExitCode()).isEqualTo(0);

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get remove -y adoptium-ca-certificates");
			assertThat(result.getExitCode()).isEqualTo(0);

			result = runShell(container, "DEBIAN_FRONTEND=noninteractive apt-get install -y " + containerDeb);
			assertThat(result.getExitCode()).isEqualTo(0);

			result = runShell(container, "test -f /etc/ssl/certs/adoptium/cacerts");
			assertThat(result.getExitCode())
				.overridingErrorMessage("/etc/ssl/certs/adoptium/cacerts does not exist")
				.isEqualTo(0);

			result = runShell(container, "test -f /etc/default/adoptium-ca-certificates");
			assertThat(result.getExitCode())
				.overridingErrorMessage("/etc/default/adoptium-ca-certificates does not exist")
				.isEqualTo(0);

			result = runShell(container, "test -f /etc/ca-certificates/update.d/adoptium-cacerts");
			assertThat(result.getExitCode())
				.overridingErrorMessage("/etc/ca-certificates/update.d/adoptium-cacerts does not exist")
				.isEqualTo(0);

			result = runShell(container, "stat -c \"%a %u %g\" /etc/ca-certificates/update.d/adoptium-cacerts");
			assertThat(result.getStdout()).isEqualToIgnoringNewLines("755 0 0");
		}
	}
}
