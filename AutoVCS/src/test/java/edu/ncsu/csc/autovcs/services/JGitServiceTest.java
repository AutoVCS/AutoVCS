package edu.ncsu.csc.autovcs.services;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
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

	@Test
	public void testCheckoutEverythingOK() throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		
		final String testContentsPath = "test-contents/";
		

		final String username = AutoVCSProperties.getUsername();
		final String password = AutoVCSProperties.getToken();

		String repoName = "AutoVCS-CoffeeMaker";
		
		File a = new File(testContentsPath + repoName);
		
		a.delete();

		@SuppressWarnings("unused")
		Git git;

		git = Git.cloneRepository().setURI("https://github.com/AutoVCS/" + repoName)
				.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password)).setDirectory(a).setNoCheckout(true)
				.call();

		List<String> filesChanged = List.of(".github/badges/branches.svg", ".github/badges/jacoco.svg",
				".github/checkstyle-8.44-all.jar", ".github/checkstyle.xml", ".github/generate_badge.py", ".github/workflows/build-cm.yml",
				"README.md");

		service.safeCheckout(testContentsPath + repoName, "ba3baffc2e638d3d3158a327b70d6ecc9e680fd7", filesChanged);

	}

	@Test
	public void testCheckoutRenameFiles() throws InvalidRemoteException, TransportException, GitAPIException, IOException {

		final String testContentsPath = "test-contents/";
		

		final String username = AutoVCSProperties.getUsername();
		final String password = AutoVCSProperties.getToken();

		String repoName = "illegal/";
		
		File a = new File(testContentsPath + repoName);


		Git git = Git.cloneRepository().setURI("https://github.com/kpresler/illegal/")
				.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
				.setDirectory(a).setNoCheckout(true).call();

		List<String> filesChanged = List.of("ok.txt");

		service.safeCheckout(testContentsPath + repoName, "8900cdff1181d4103d3ed6a43735cf64d99aa929", filesChanged);
		
		
		
		

	}

}
