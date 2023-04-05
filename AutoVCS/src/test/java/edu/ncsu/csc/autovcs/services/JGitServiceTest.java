package edu.ncsu.csc.autovcs.services;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import edu.ncsu.csc.autovcs.AutoVCSProperties;
import edu.ncsu.csc.autovcs.TestConfig;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles({ "test" })
public class JGitServiceTest {

	@Autowired
	private JGitService service;

	@BeforeEach
	public void setup() throws Exception {
		deleteDirectory("test-contents/");
	}

	@AfterAll
	static public void cleanup() throws Exception {
		deleteDirectory("test-contents/");
	}

	@Test
	public void testCheckoutEverythingOK()
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {

		final String testContentsPath = "test-contents/";

		final String username = AutoVCSProperties.getUsername();
		final String password = AutoVCSProperties.getToken();

		String repoName = "AutoVCS-CoffeeMaker";

		File a = new File(testContentsPath + repoName);

		a.delete();

		@SuppressWarnings("unused")
		Git git;

		git = Git.cloneRepository().setURI("https://github.com/AutoVCS/" + repoName)
				.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password)).setDirectory(a)
				.setNoCheckout(true).call();

		List<String> filesChanged = List.of(".github/badges/branches.svg", ".github/badges/jacoco.svg",
				".github/checkstyle-8.44-all.jar", ".github/checkstyle.xml", ".github/generate_badge.py",
				".github/workflows/build-cm.yml", "README.md");

		service.safeCheckout(testContentsPath + repoName, "ba3baffc2e638d3d3158a327b70d6ecc9e680fd7", filesChanged);
		
		List<String> checkstyleXmlContents = Files.readAllLines(
				Paths.get(String.format("%s/%s/%s", testContentsPath, repoName, ".github/checkstyle.xml")),
				StandardCharsets.UTF_8);

		Assertions.assertEquals(85, checkstyleXmlContents.size());

	}

	@Test
	public void testCheckoutRenameFiles()
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {

		final String testContentsPath = "test-contents/";

		final String username = AutoVCSProperties.getUsername();
		final String password = AutoVCSProperties.getToken();

		String repoName = "AutoVCS-InvalidFilenames";

		File a = new File(testContentsPath + repoName);

		Git git = Git.cloneRepository().setURI(String.format("https://github.com/AutoVCS/%s/", repoName))
				.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password)).setDirectory(a)
				.setNoCheckout(true).call();

		List<String> filesChanged = List.of("MyClass.java", "valid_file.txt", "invalid_file.txt ");

		service.safeCheckout(testContentsPath + repoName, "88cd679ce49a6b05f66a84a0f0f6455ae510d61b", filesChanged);

		// check contents
		List<String> myClassContents = Files.readAllLines(
				Paths.get(String.format("%s/%s/%s", testContentsPath, repoName, "MyClass.java")),
				StandardCharsets.UTF_8);

		Assertions.assertEquals(8, myClassContents.size());

		Assertions.assertEquals("	static public void main (String args[]){", myClassContents.get(2));

		List<String> validFileContents = Files.readAllLines(
				Paths.get(String.format("%s/%s/%s", testContentsPath, repoName, "valid_file.txt")),
				StandardCharsets.UTF_8);

		Assertions.assertEquals(2, validFileContents.size());

		Assertions.assertEquals(
				"This file has valid contents -- should still be readable even with the invalid one, since we'll also copy",
				validFileContents.get(0));
		Assertions.assertEquals("it from Git's object store.", validFileContents.get(1));

		// rename on checkout will pull off the space at the end
		List<String> invalidFileContents = Files.readAllLines(
				Paths.get(String.format("%s/%s/%s", testContentsPath, repoName, "invalid_file.txt")),
				StandardCharsets.UTF_8);

		Assertions.assertEquals(2, invalidFileContents.size());

		Assertions.assertEquals("This is some content that would like to be able to read back, later on.",
				invalidFileContents.get(0));
		Assertions.assertEquals(
				"The filename of this file is mangled, which normally prevents the git checkout operation.",
				invalidFileContents.get(1));

		filesChanged = List.of("Cloneable.java");

		service.safeCheckout(testContentsPath + repoName, "3af9c0cb15f87d12120365b2e35bfdc61e0c9aa1", filesChanged);
		
		List<String> cloneableContents = Files.readAllLines(
				Paths.get(String.format("%s/%s/%s", testContentsPath, repoName, "Cloneable.java")),
				StandardCharsets.UTF_8);

		Assertions.assertEquals(5, cloneableContents.size());

		Assertions.assertEquals("public interface Cloneable {",
				cloneableContents.get(0));
		

		service.safeCheckout(testContentsPath + repoName, "cc33c2b0772607c422c69a27ab27bbe4f8314d96", filesChanged);
		
		cloneableContents = Files.readAllLines(
				Paths.get(String.format("%s/%s/%s", testContentsPath, repoName, "Cloneable.java")),
				StandardCharsets.UTF_8);

		Assertions.assertEquals(6, cloneableContents.size());

		Assertions.assertEquals("public interface Cloneable {",
				cloneableContents.get(0));
		Assertions.assertEquals("public Object shallowClone();",
				cloneableContents.get(4));

	}
	
	
	static private void deleteDirectory(String directoryName) throws IOException {
		try (Stream<Path> dirStream = Files.walk(Paths.get(directoryName))) {
		    dirStream
		        .map(Path::toFile)
		        .sorted(Comparator.reverseOrder())
		        .forEach(File::delete);
		}
	}
	
	

}
