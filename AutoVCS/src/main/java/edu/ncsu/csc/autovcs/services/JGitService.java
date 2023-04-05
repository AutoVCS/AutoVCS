package edu.ncsu.csc.autovcs.services;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.InvalidPathException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Component;

/**
 * This class provides functionality for interacting with JGit, which is used by
 * AutoVCS for handling repository checkout, and getting the filesystem in a
 * position for performing analyses.
 * 
 * @author Kai Presler-Marshall
 *
 */
@Component
public class JGitService {

	/**
	 * This method will safely check out a repository. If on a Unix-based system, or
	 * if all files have legal names, then the repository will be fully checked out
	 * to the commit specified. If we are on Windows, and the current commit
	 * contains any files with illegal names, this will perform a partial check-out,
	 * checking out just those files which were changed on the commit (the others
	 * are not necessary for performing diffs).
	 * 
	 * @param repositoryName
	 * @param commit
	 * @param filesOnCommit
	 * @throws IOException
	 */
	public void safeCheckout(String repositoryLocation, String commit, List<String> filesOnCommit) throws IOException {

		String os = System.getProperty("os.name");
		if (os.contains("Windows") && containsUnsafeFile(filesOnCommit)) {
			partialCheckout(repositoryLocation, commit, filesOnCommit);
		} else {

			File aPath = new File(repositoryLocation);

			try (Git vA = Git.open(aPath);) {
				String branchName = vA.getRepository().getFullBranch();
				vA.checkout().setName(branchName).setStartPoint(commit).setForced(true).call();
			} catch (InvalidPathException|GitAPIException e) {
				e.printStackTrace();
				partialCheckout(repositoryLocation, commit, filesOnCommit);
			}

		}

	}

	private void partialCheckout(String repositoryLocation, String commit, List<String> filesOnCommit)
			throws IOException {
		try (Repository existingRepo = new FileRepositoryBuilder().setGitDir(new File(repositoryLocation + "/.git"))
				.build();) {


			ObjectInserter inserter = existingRepo.newObjectInserter();

			DirCache index = existingRepo.readDirCache();

			if (!index.lock()) {
				throw new IOException("Could not get lock on Git directory!");
			}

			RevWalk revisionWalker = new RevWalk(existingRepo);

			DirCacheEditor stateEditor = index.editor();

			ObjectId commitId = ObjectId.fromString(commit);

			RevCommit c = revisionWalker.parseCommit(commitId);
			Map<String, ObjectId> existingContents = collectPathAndObjectIdFromTree(existingRepo, revisionWalker, c);

			// this makes sure we keep all changes _except_ for the illegal ones
			// we'll massage and fix those as part of the loop
			createTemporaryIndexForContent(existingRepo, existingContents, stateEditor);

			String base = repositoryLocation + "/";

			for (String file : existingContents.keySet()) {
				
				String newContents = getContent(existingRepo, c, file);

				String newFileName = file;
				boolean added = false;
				if (nameIsIllegal(file)) {
					newFileName = correct(file);
					added = true;
				}

				Files.write(Paths.get(base + newFileName), newContents.getBytes());

				/*
				 * Need to know how big the file is. We can pull that directly from the BOAS
				 * above but the docs say that's unsafe & may not work well.
				 */
				File writtenFile = new File(newFileName);

				if (added) {
					InputStream stream = new ByteArrayInputStream(newContents.getBytes(StandardCharsets.UTF_8));

					ObjectId id = inserter.insert(Constants.OBJ_BLOB, writtenFile.length(), stream);
					addToTemporaryInCoreIndex(stateEditor, newFileName, id, FileMode.REGULAR_FILE);
				}

			}

			stateEditor.commit();

		}

	}

	private boolean containsUnsafeFile(List<String> filesOnCommit) {
		for (String fileName : filesOnCommit) {
			if (nameIsIllegal(fileName)) {
				return true;
			}
		}
		return false;
	}

	private boolean nameIsIllegal(final String fileName) {
		return fileName.contains(":") || fileName.contains(":") || fileName.endsWith(" ") || fileName.startsWith(" ");
	}

	private String correct(final String fileName) {

		String correctedName = fileName;

		correctedName = correctedName.replaceAll("\\:", "_");
		correctedName = correctedName.replaceAll("\\|", "_");

		if (correctedName.startsWith(" ")) {
			correctedName = correctedName.substring(1);
		}
		if (correctedName.endsWith(" ")) {
			correctedName = correctedName.substring(0, correctedName.length() - 1);
		}

		return correctedName;

	}

	/*
	 * These methods come, with some slight modification, from UberFire's `JGitUtil`
	 * and `Squash` classes:
	 * https://github.com/AppFormer/uberfire/blob/1.4.x/uberfire-nio2-backport/
	 * uberfire-nio2-impls/uberfire-nio2-jgit/src/main/java/org/uberfire/java/nio/fs
	 * /jgit/util/JGitUtil.java
	 * https://github.com/AppFormer/uberfire/blob/1.4.x/uberfire-nio2-backport/
	 * uberfire-nio2-impls/uberfire-nio2-jgit/src/main/java/org/uberfire/java/nio/fs
	 * /jgit/util/commands/Squash.java
	 */

	private void addToTemporaryInCoreIndex(final DirCacheEditor editor, final String path, ObjectId objectId,
			FileMode fileMode) {

		/*
		 * AS BEST AS I CAN TELL this exception happens only when we have a file with an invalid
		 * name, which is the exact one we want to correct
		 */
		try {
			DirCacheEntry dcEntry = new DirCacheEntry(path);
			addToTemporaryInCoreIndex(editor, dcEntry, objectId, fileMode);
		} catch (IllegalArgumentException iae) {

		}


	}

	private void addToTemporaryInCoreIndex(final DirCacheEditor editor, final DirCacheEntry dcEntry,
			final ObjectId objectId, final FileMode fileMode) {
		// add to temporary in-core index
		editor.add(new DirCacheEditor.PathEdit(dcEntry) {
			@Override
			public void apply(final DirCacheEntry ent) {
				ent.setObjectId(objectId);
				ent.setFileMode(fileMode);
			}
		});
	}

	private void createTemporaryIndexForContent(final Repository repository, final Map<String, ObjectId> content,
			final DirCacheEditor editor) {

		try (final ObjectInserter inserter = repository.newObjectInserter();) {
			for (final String path : content.keySet()) {
				addToTemporaryInCoreIndex(editor, path, content.get(path), FileMode.REGULAR_FILE);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Collect all paths and object IDs from Git Tree
	 * 
	 * @param repo    the repository
	 * @param revWalk the object that walks into the commit graph.
	 * @param commit  the commit
	 * @return a Map where the key is the path and the values is the object id
	 * @throws {@link GitException} if something wrong happens
	 */
	private Map<String, ObjectId> collectPathAndObjectIdFromTree(Repository repo, RevWalk revWalk, RevCommit commit) {

		try {
			Map<String, ObjectId> content = new HashMap<String, ObjectId>();

			RevTree tree = getRevTree(revWalk, commit);
			try (TreeWalk treeWalk = new TreeWalk(repo);) {

				treeWalk.addTree(tree);
				treeWalk.setRecursive(false);

				while (treeWalk.next()) {
					if (treeWalk.isSubtree()) {
						treeWalk.enterSubtree();
					} else {
						ObjectId objectId = treeWalk.getObjectId(0);
						content.put(treeWalk.getPathString(), objectId);
					}
				}

				return content;
			}

		} catch (IOException e) {
			String message = "Impossible to collect path and objectId from Tree";
			throw new RuntimeException(message, e);
		}
	}

	private RevTree getRevTree(RevWalk revWalk, RevCommit commit) {
		try {
			return revWalk.parseTree(commit.getTree().getId());
		} catch (IOException e) {
			String message = String.format("An error has ocurred trying to get the Revision Tree from commit (%s)",
					commit.getId());
			throw new RuntimeException(message, e);
		}
	}
	
	/**
	 * credit to https://stackoverflow.com/a/47850713
	 * @param repository
	 * @param commit
	 * @param path
	 * @return
	 * @throws IOException
	 */
	private String getContent(Repository repository, RevCommit commit, String path) throws IOException {
		  try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
		    ObjectId blobId = treeWalk.getObjectId(0);
		    try (ObjectReader objectReader = repository.newObjectReader()) {
		      ObjectLoader objectLoader = objectReader.open(blobId);
		      byte[] bytes = objectLoader.getBytes();
		      return new String(bytes, StandardCharsets.UTF_8);
		    }
		  }
		}

}
