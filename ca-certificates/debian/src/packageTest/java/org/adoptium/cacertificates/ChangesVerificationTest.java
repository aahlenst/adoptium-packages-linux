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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Andreas Ahlenstorf
 */
class ChangesVerificationTest {

	@Test
	void allDistributionsListedInChangesFile() throws IOException {
		Path changesFile = DebFiles.hostChangesPath();
		assertThat(changesFile).exists();

		List<String> lines = Files.readAllLines(changesFile, StandardCharsets.UTF_8);
		assertThat(lines).contains("Distribution: stretch buster bullseye bionic focal groovy hirsute");
	}
}
