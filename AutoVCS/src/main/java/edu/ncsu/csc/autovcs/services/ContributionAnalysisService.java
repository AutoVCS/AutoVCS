package edu.ncsu.csc.autovcs.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ch.uzh.ifi.seal.changedistiller.api.ChangeExtractor;
import ch.uzh.ifi.seal.changedistiller.api.ChangeSummary;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import edu.ncsu.csc.autovcs.AutoVCSProperties;
import edu.ncsu.csc.autovcs.config.SourceCodeChangeSerialiser;
import edu.ncsu.csc.autovcs.controllers.api.APIRepositoryController;
import edu.ncsu.csc.autovcs.forms.ContributionsSummaryForm;
import edu.ncsu.csc.autovcs.forms.PopulateDataForm;
import edu.ncsu.csc.autovcs.models.persistent.GHCommit;
import edu.ncsu.csc.autovcs.models.persistent.GHCommit.DisplayCommit;
import edu.ncsu.csc.autovcs.models.persistent.GHRepository;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;

/**
 * Main logic class of the AutoVCS application.  This class handles traversing a Git repository (from metadata previously extracted from GitHub via its API, and stored in a local database) and creating summaries of what each user has contributed.
 * 
 * <code>createUnaggregatedDiffs()</code> implements the following algorithm:
 * <pre>
 *  \State $R1\gets$ \texttt{clone(repo)}
      \State $R2\gets$ \texttt{clone(repo)}
      \State $ContribsByCommit \gets \{\}$
      \For{\texttt{commit c in r}}
        \If{\texttt{c.parent} is \texttt{null} or \texttt{c.isMergeCommit} or \texttt{c.isOutOfTimeWindow}}
          \State continue
        \EndIf
        \State $ContribsForCommit \gets \{\}$
        \State Check out $R1$ to \texttt{c}
        \State Check out $R2$ to \texttt{c.parent}
        \For{\texttt{ChangedFile} in \texttt{c.ChangedFiles}}
          \State $AstNew \gets$ \texttt{buildAST(R1.ChangedFile)}\Comment{Build an AST representing the new version of the file}
          \State $AstOld \gets$ \texttt{buildAST(R2.ChangedFile)}\Comment{Build an AST representing the old version of the file}
          \State $ContribsForFile \gets$ \texttt{diff(AstNew, AstOld)}\Comment{Compute an edit script between ASTs to identify contributions}
          \State \texttt{ContribsForCommit.insert( ContribsForFile)}
        \EndFor
        \State \texttt{ContribsByCommit.insert(c, ContribsForCommit)}\Comment{Map each commit to the changes made as part of it}
      \EndFor
      \State $ByUser \gets$ \texttt{summarise(ContribsByCommit)}\Comment{Summarise changes per-user, to show changes across files and commits}
      \State \texttt{return ContribsByUser}
    </pre>
    
    To present higher-level summaries of changes, <code>aggregateByUser()</code> implements the following algorithm:
    <pre>
    \State $ContribsPerUser \gets \{\}$
      \For{\texttt{(commit, contribs) in ContribsByCommit}} \Comment{For each user, combine contributions}
        \If{commit.author not in ContribsPerUser}
          \State \texttt{ContribsPerUser.insert( commit.author, $\{\}$)}
        \EndIf
        \State \texttt{ContribsPerUser.insert( commit.author, contribs)} \Comment{Add contributions from this commit to the running tally of contributions for this user}
      \EndFor
      \State $SummarisedContribs \gets \{\}$
      \For{\texttt{user, contribs in ContribsPerUser}} \Comment{Summarise and weight contributions for each user}
        \State $UserContribScore \gets 0$
        \State $UserContribSummary \gets \{\}$
        \For{\texttt{contrib in contribs}}
          \State \texttt{UserContribSummary.insert( label( contrib.type), existingCount+1)} \Comment{Summarises detailed edit operations into higher-level contribution type}
          \State \texttt{UserContribScore += weight(contrib.type)} \Comment{Computes weighted score for user based on type of contribution}
      
        \EndFor
        \State \texttt{SummarisedContribs.insert(user, \{UserContribScore, UserContribSummary\})}
      \EndFor
      \State \texttt{return SummarisedContribs}
    </pre>
    
    Finally, <code>getContributionSummaries</code> is a brief wrapper method to handle JSON data serialisation for the API endpoint, and several helper classes provide intermediate data structures for storing partially and fully summarised data.
 * @author Kai Presler-Marshall
 *
 */
@Component
public class ContributionAnalysisService {

	/**
	 * Autowired service for providing access to GitHub repositories
	 */
    @Autowired
    private GHRepositoryService     repositoryService;

    /**
     * Autowired service for providing access to GitHub commits
     */
    @Autowired
    private GHCommitService         commitService;

    /**
     * Autowired service for providing access to files changed on GitHub commits
     */
    @Autowired
    private GHFileService           fileService;

    @Autowired
    private APIRepositoryController apiCtrl;

    /**
     * Wraps around the summaries to create a method that provides JSON data for the API endpoints.
     * @param csf Form describing the details of the summaries to be produced
     * @return JSON data that represents summaries for a repository & time window
     * @throws Exception
     */
    public String getContributionSummaries ( final ContributionsSummaryForm csf ) throws Exception {
        return write( aggregateByUser( csf ) );

    }

    /**
     * Creates fully-summarised data by user
     * @param csf Form describing the details of the summaries to be produced
     * @return {@link edu.ncsu.csc.autovcs.services.ContributionAnalysisService.ContributionsSummariesAPIData} Summary for the API, showing contributions per user, a contributions by file, and a list of commits for each user
     */
    public ContributionsSummariesAPIData aggregateByUser ( final ContributionsSummaryForm csf ) {

        final ContributionsSummaries summaries = createUnaggregatedDiffs( csf );

        final Map<GHCommit, ChangeSummariesList> contributionsPerCommit = summaries.getContributionsPerCommit();

        final Map<GitUser, List<GHCommit.DisplayCommit>> commitsPerUser = summaries.getCommitsPerUser();

        final Map<GitUser, List<ChangeSummary>> remappedContributions = new HashMap<GitUser, List<ChangeSummary>>();

        Double total = (double) 0;

        /* For each commit, look at the contributions made... */
        contributionsPerCommit.entrySet().stream().forEach( e -> {
            final GHCommit commit = e.getKey();

            final GitUser author = commit.getAuthor();

            /*
             * Skip excluded users here so their contributions aren't factored
             * into percentages
             */
            if ( author.isExcluded() ) {
                return;
            }

            final ChangeSummariesList contributions = e.getValue();

            /* If we don't already have a record for this user */
            if ( !remappedContributions.containsKey( author ) ) {
                remappedContributions.put( author, new ArrayList<ChangeSummary>() );
            }

            remappedContributions.get( author ).addAll( contributions.getChanges() );
        } );

        final Map<GitUser, ChangeSummariesList> changes = remappedContributions.entrySet().stream()
                .collect( Collectors.toMap( k -> k.getKey(), v -> {
                    return new ChangeSummariesList( v.getValue() );
                } ) );

        /*
         * Add up the contributions across all users, so that we can compute
         * percentages
         */
        for ( final Map.Entry<GitUser, ChangeSummariesList> entry : changes.entrySet() ) {
            total += entry.getValue().getContributionsScore();
        }

        /* Compute percentages */
        for ( final Map.Entry<GitUser, ChangeSummariesList> entry : changes.entrySet() ) {
            final ChangeSummariesList summary = entry.getValue();
            summary.setContributionsScorePercent( 100 * summary.getContributionsScore() / total );

        }

        /* Fill in the specific commits this user is responsible for */
        for ( final Map.Entry<GitUser, ChangeSummariesList> entry : changes.entrySet() ) {
            final ChangeSummariesList summary = entry.getValue();
            summary.setCommits( commitsPerUser.get( entry.getKey() ) );
        }

        final Map<String, Map<GitUser, Double>> percentageContributionPerFile = new LinkedHashMap<String, Map<GitUser, Double>>();

        /**
         * To display how involved each user was with each file that has been
         * changed, we want to map:
         *
         * <pre>
         * fileName ->
         *   userA -> scorePercent
         *   userB -> scorePercent
         * fileName2 ->
         *   userB -> scorePercent
         *   userC -> scorePercent
         * </pre>
         *
         * This double remapping converts the raw scores that were summed up
         * from the previous steps into percentages
         */

        /*
         * Sort based on filename to make it easier for the user to find things
         */
        final List<Entry<String, FileContributions>> sortedContributions = new ArrayList<>(
                summaries.getContributionsPerFile().entrySet() );

        sortedContributions.sort( ( a, b ) -> a.getKey().compareTo( b.getKey() ) );

        sortedContributions.forEach( entry -> {

            final String fileName = entry.getKey();
            final FileContributions fileContribs = entry.getValue();

            final Map<GitUser, Double> contributionsForIndividualFile = new LinkedHashMap<>();

            final Integer overallContributionToFile = fileContribs.getOverallScore();

            fileContribs.getContributionPerUser().forEach( ( user, contribution ) -> {

                final Double percentContribution = ( (double) contribution ) / overallContributionToFile;

                contributionsForIndividualFile.put( user, percentContribution );

            } );

            percentageContributionPerFile.put( fileName, contributionsForIndividualFile );

        } );

        return new ContributionsSummariesAPIData( changes, percentageContributionPerFile, csf.getRepository(), summaries.getStartDate(), summaries.getEndDate() );
    }

    private ContributionsSummaries createUnaggregatedDiffs ( final ContributionsSummaryForm form ) {
        final String repo = form.getRepository();
        final String organisation = form.getOrganisation();

        GHRepository repository = repositoryService.findByNameAndOrganisation( repo, organisation );

        if ( null == repository && !form.isInitialiseUnknown() ) {
            throw new NoSuchElementException( "Repository not found" );
        }
        /*
         * If we didn't find the repository, but were supposed to initialise it,
         * go do so
         */
        else if ( null == repository ) {
            final PopulateDataForm pdf = new PopulateDataForm();
            pdf.setRepository( repo );
            pdf.setOrganisation( organisation );
            pdf.setCommit( true );
            apiCtrl.populateRepositories( pdf );
            repository = repositoryService.findByNameAndOrganisation( repo, organisation );

        }

        final List<GHCommit> commits = commitService.findByRepository( repository );

        if ( null == commits ) {
            throw new NoSuchElementException( "No commits found" );
        }

        /* Sort most recent to least recent */
        commits.sort( ( a, b ) -> b.getCommitDate().compareTo( a.getCommitDate() ) );

        try {
            Files.walk( new File( repo ).toPath(), FileVisitOption.FOLLOW_LINKS ).sorted( Comparator.reverseOrder() )
                    .map( Path::toFile ).forEach( File::delete );
        }
        catch ( final Exception e ) {
            // wasn't there, continue on
        }

        @SuppressWarnings ( "unused" )
        Git git;
        @SuppressWarnings ( "unused" )
        Git git2;

        /* Will be used to hold each commit's repo snapshot */

        final long time = System.currentTimeMillis();

        final String aPath = String.format( "diffs/%d-version-A", time );
        final String bPath = String.format( "diffs/%d-version-B", time );

        File a;
        File b;

        try {

            Thread.sleep( 500 );

            a = new File( aPath );
            b = new File( bPath );

            a.mkdirs();
            b.mkdirs();

            final String url = AutoVCSProperties.getGithubURL();
            final String username = AutoVCSProperties.getUsername();
            final String password = AutoVCSProperties.getToken();

            git = Git.cloneRepository().setURI( String.format( "%s/%s/%s", url, organisation, repo ) )
                    .setCredentialsProvider( new UsernamePasswordCredentialsProvider( username, password ) )
                    .setDirectory( a ).call();

            git2 = Git.cloneRepository().setURI( String.format( "%s/%s/%s", url, organisation, repo ) )
                    .setCredentialsProvider( new UsernamePasswordCredentialsProvider( username, password ) )
                    .setDirectory( b ).call();
        }
        catch ( final Exception e ) {
            e.printStackTrace();
            throw new RuntimeException(
                    "[" + Thread.currentThread().getName() + "] Unable to clone Git repository for further analysis!" );
        }
        try {
            /* Wait for clone */
            Thread.sleep( 500 );
        }
        catch ( final InterruptedException ie ) {
            // this won't happen
        }

        final Map<GHCommit, ChangeSummariesList> contributionsPerCommit = new HashMap<GHCommit, ChangeSummariesList>();

        final Map<GitUser, List<GHCommit.DisplayCommit>> commitsPerUser = new HashMap<GitUser, List<GHCommit.DisplayCommit>>();

        final Map<String, FileContributions> contributionsPerFile = new HashMap<String, FileContributions>();

        commits.forEach( commit -> {
            if ( commit.isMergeCommit() ) {
                return; // don't look at merges
            }
            if ( null == commit.getParent() ) {
                return; // skip first commit
            }

            /* Check date bounds, if provided */
            if ( null != form.getStartDate() && null != form.getEndDate() ) {
                Instant startDate;
                Instant endDate;

                final DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
                final TemporalAccessor start = formatter.parse( form.getStartDate() );
                final TemporalAccessor end = formatter.parse( form.getEndDate() );
                startDate = Instant.from( start );
                endDate = Instant.from( end );

                if ( commit.getCommitDate().isBefore( startDate ) || commit.getCommitDate().isAfter( endDate ) ) {
                    // out of bounds, skip
                    return;
                }
            }
            System.out.printf( "[" + Thread.currentThread().getName() + "] Commit %s with parent %s\n",
                    commit.getSha1(), commit.getParent() );

            if ( null == commitsPerUser.get( commit.getAuthor() ) ) {
                commitsPerUser.put( commit.getAuthor(), new ArrayList<GHCommit.DisplayCommit>() );
            }
            commitsPerUser.get( commit.getAuthor() ).add( commit.format() );

            try {
                /* Wait for filesystem to catch up */
                Thread.sleep( 1000 );

                try {
                    new File( a + "/.git/index.lock" ).delete();
                }
                catch ( final Exception e ) {
                    System.out.println(
                            "[" + Thread.currentThread().getName() + "] Couldn't delete a.lock: " + e.getClass() );
                }
                try {
                    new File( b + "/.git/index.lock" ).delete();
                }
                catch ( final Exception e ) {
                    System.out.println(
                            "[" + Thread.currentThread().getName() + "] Couldn't delete b.lock" + e.getClass() );
                }

                final Git vA = Git.open( a );
                final Git vB = Git.open( b );

                vA.checkout().setName( commit.getSha1() ).call();

                vB.checkout().setName( commit.getParent() ).call();

                vA.close();

                vB.close();

                /* Wait for filesystem to catch up */

                Thread.sleep( 1000 );
            }
            catch ( final Exception e ) {
                e.printStackTrace();
            }

            final List<ChangeSummary> changesForCommit = new ArrayList<ChangeSummary>();

            fileService.findByCommit( commit ).forEach( file -> {

                final String fileName = file.getFilename();

                if ( !fileName.endsWith( "java" ) ) {
                    return;

                }

                /* Skip UI files, if prompted to do so */
                if ( ( fileName.contains( "ui" ) || fileName.contains( "view" ) ) && form.isExcludeGUI() ) {
                    System.out.printf(
                            "[" + Thread.currentThread().getName() + "] Excluding %s as it looks like a GUI file\n",
                            fileName );
                    return;
                }

                try {
                    final ChangeSummary changesInFile = ChangeExtractor.extractChanges(
                            String.format( "%s/%s", bPath, fileName ), String.format( "%s/%s", aPath, fileName ) );
                    if ( null != changesInFile ) {
                        changesForCommit.add( changesInFile );
                        final String filenameTrimmed = fileName.substring( fileName.lastIndexOf( "/" ) + 1 );
                        if ( !contributionsPerFile.containsKey( filenameTrimmed ) ) {
                            contributionsPerFile.put( filenameTrimmed, new FileContributions() );
                        }
                        contributionsPerFile.get( filenameTrimmed ).addContribution( commit.getAuthor(),
                                changesInFile.getScore() );
                    }
                }
                catch ( final Exception e ) {
                    final StackTraceElement[] elements = e.getStackTrace();
                    /* Print a bit of context */
                    System.err.println( "[" + Thread.currentThread().getName() + "] " + elements[0] );
                    System.err.println( "[" + Thread.currentThread().getName() + "] " + elements[1] );
                    System.err.println( "[" + Thread.currentThread().getName() + "] " + elements[2] );
                    System.err.println( "[" + Thread.currentThread().getName() + "] " + elements[3] );

                }

            } );
            contributionsPerCommit.put( commit, new ChangeSummariesList( changesForCommit ) );

        } );

        if ( contributionsPerCommit.isEmpty() ) {
            return new ContributionsSummaries(form.getStartDate(), form.getEndDate());
        }
        else if ( "BY_USER".equals( form.getType() ) ) {
            return new ContributionsSummaries( contributionsPerCommit, commitsPerUser, contributionsPerFile, form.getStartDate(), form.getEndDate() );
        }
        else {
            throw new IllegalArgumentException( "Unrecognised aggregation option" );
        }

    }

    /**
     * Override the default JSON serialisation because otherwise we run into
     * infinite loops with the SourceCodeChanges that reference each other.
     * Sigh.
     *
     * @param what
     *            What to write out
     * @return ResponseEntity, with OK status and created JSON on success, or
     *         error on false
     */
    static private String write ( final Object what ) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule( new JavaTimeModule() );
            final SimpleModule module = new SimpleModule( "SourceCodeChangeSerialiser",
                    new Version( 1, 0, 0, null, null, null ) );
            module.addSerializer( SourceCodeChange.class, new SourceCodeChangeSerialiser() );
            mapper.registerModule( module );
            final String json = mapper.writeValueAsString( what );

            return json;
        }
        catch ( final IOException ioee ) {
            throw new IllegalArgumentException( "Unable to write JSON" );
        }

    }

    /**
     * Extends {@link ch.uzh.ifi.seal.changedistiller.api.ChangeSummariesList} to track not just the detailed summaries
     * from the program analysis, but to supplement it with a list of commits (for each user) as well.
     * @author Kai Presler-Marshall
     *
     */
    public static final class ChangeSummariesList extends ch.uzh.ifi.seal.changedistiller.api.ChangeSummariesList {

    	/**
    	 * List of commits associated with the summarised changes
    	 */
        private List<GHCommit.DisplayCommit> commits = new ArrayList<GHCommit.DisplayCommit>();

        /**
         * Creates a ChangeSummariesList from a list of individual changes
         * @param changes
         */
        public ChangeSummariesList ( final List<ChangeSummary> changes ) {
            super( changes );
        }

        /**
         * Tracks the commits associated with this ChangeSummariesList
         * TODO: Why is this method separate from the constructor?  This should be initialised alongside the ChangeSummaries there
         * @param commits Commits that are associated with the summaries thereof
         */
        private void setCommits ( final List<GHCommit.DisplayCommit> commits ) {
            this.commits = commits;
        }

        /**
         * Returns the list of {@link edu.ncsu.csc.autovcs.models.persistent.GHCommit.DisplayCommit} associated with this ChangeSummaries.
         * @return
         */
        public List<GHCommit.DisplayCommit> getCommits () {
            return this.commits;
        }

    }

    /**
     * Represents a partially-summarised view of a repository before final processing for the frontend.  In particular, this view of the data has not yet
     * aggregated together all summaries on a user-by-user basis.
     * 
     * contributionsPerCommit maps a {@link edu.ncsu.csc.autovcs.models.persistent.GHCommit.DisplayCommit} to the contributions associated specifically with that commit; before the display of the information, summaries should be made *across* commits for a user, rather than per-commit.
     * 
     * commitsPerUser maps each {@link edu.ncsu.csc.autovcs.models.persistent.GitUser} on a repository to the commits they have authored during a time window.
     * 
     * contributionsPerFile maps each file changed (represented by the filename) to a summary of the contributions made by each user (see {@link edu.ncsu.csc.autovcs.services.ContributionAnalysisService.FileContributions} for more information).
     * 
     * startDate tracks the start date, if any, associated with this summary
     * 
     * endDate tracks the end date, if any, associated with this summary
     * 
     * @author Kai Presler-Marshall
     *
     */
    public static final class ContributionsSummaries {

    	/**
    	 * Maps each commit to a summary of the changes made on that commit (across the possibly various files associated with it)
    	 */
        private final Map<GHCommit, ChangeSummariesList>         contributionsPerCommit;

        /**
         * Maps each user ({@link edu.ncsu.csc.autovcs.models.persistent.GitUser} to a list of formatted commits ({@link edu.ncsu.csc.autovcs.models.persistent.GHCommit.DisplayCommit}) they have authored.  
         */
        private final Map<GitUser, List<GHCommit.DisplayCommit>> commitsPerUser;

        /**
         * Maps each changed file (by filename) to a numeric summary of the contributions made to that file.
         */
        private final Map<String, FileContributions>             contributionsPerFile;
        
        /**
         * End date for time window summarised.
         */
        private final Instant startDate;
        
        /**
         * Start date for time window summarised.
         */
        private final Instant endDate;
        
        /**
         * This constructor builds a "Contributions Summary" when there really were no contributions to summarise.  Instead, this summary is used by the frontend to indicate that there *were no* summaries present, and just displays the time window the "summary" was for.
         * @param startDate Start date for attempted summaries time window.
         * @param endDate End date for attempted summaries time window.
         */
        public ContributionsSummaries (final String startDate, final String endDate) {
            this(new HashMap<GHCommit, ChangeSummariesList>(),
                    new HashMap<GitUser, List<DisplayCommit>>(),
                    new HashMap<String, FileContributions>(), startDate, endDate);
        }

        /**
         * Creates a full ContributionsSummaries object, storing all associated information.
         * @param contributionsPerCommit
         * @param commitsPerUser
         * @param contributionsPerFile
         * @param startDate
         * @param endDate
         */
        public ContributionsSummaries ( final Map<GHCommit, ChangeSummariesList> contributionsPerCommit,
                final Map<GitUser, List<DisplayCommit>> commitsPerUser,
                final Map<String, FileContributions> contributionsPerFile, final String startDate, final String endDate ) {
            super();
            this.contributionsPerCommit = contributionsPerCommit;
            this.commitsPerUser = commitsPerUser;
            this.contributionsPerFile = contributionsPerFile;
            
            if ( null != startDate && null != endDate ) {

                final DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
                final TemporalAccessor start = formatter.parse( startDate );
                final TemporalAccessor end = formatter.parse( endDate );
                this.startDate = Instant.from( start );
                this.endDate = Instant.from( end );
            }
            else {
            	this.startDate = null;
            	this.endDate = null;
            }

        }

        public Map<GHCommit, ChangeSummariesList> getContributionsPerCommit () {
            return contributionsPerCommit;
        }

        public Map<GitUser, List<GHCommit.DisplayCommit>> getCommitsPerUser () {
            return commitsPerUser;
        }

        public Map<String, FileContributions> getContributionsPerFile () {
            return contributionsPerFile;
        }
        
        public Instant getStartDate() {
        	return this.startDate;
        }
        
        public Instant getEndDate() {
        	return this.endDate;
        }

    }

    /**
     * Represents contributions to an individual file.  Tracks both the overall magnitude of changes made to the file across all users, and the changes made to the file per-user, which are then used to calcualte relative percentages for each user.
     * 
     * @author Kai Presler-Marshall
     *
     */
    public static final class FileContributions {
    	
    	/**
    	 * Sum of all contributions (from the *Total Score* from the program analysis) made to a given file
    	 */
        private Integer                     overallScore;

        /**
         * Map from each user to the sum of their contributions to the file
         */
        private final Map<GitUser, Integer> contributionPerUser;

        /**
         * Creates a new FileContributions, tracking the total score as 0 and setting no contributions per user
         */
        public FileContributions () {
            overallScore = 0;
            contributionPerUser = new HashMap<GitUser, Integer>();

        }

        /**
         * Records a contribution to a given file, incrementing the contributions for the user specified & the sum across all users
         * @param who Who has made the contributions
         * @param contributionScore The extent of the contributions made
         */
        public void addContribution ( final GitUser who, final Integer contributionScore ) {
            if ( !contributionPerUser.containsKey( who ) ) {
                contributionPerUser.put( who, 0 );
            }
            contributionPerUser.put( who, contributionScore + contributionPerUser.get( who ) );
            overallScore += contributionScore;
        }

        public Integer getOverallScore () {
            return overallScore;
        }

        public Map<GitUser, Integer> getContributionPerUser () {
            return contributionPerUser;
        }

    }

    /**
     * Represents a summary of contributions made as sent over the API to the frontend.  This includes two high-level summaries of the information:
     * changesPerUser tracks per-user, showing both results of the program analysis techniques and a summary of the Git commits made
     * changesPerFile shows on a file-by-file basis who has been most heavily involved with each file, using the high-level contributions score
     * from the changesPerUser
     * 
     * repo tracks the name of the repository to display on the batch-generated pages
     * startDate tracks the start of the time window specified, if any
     * endDate tracks the end of the time window specified, if any
     * 
     * @author Kai Presler-Marshall
     *
     */
    public static final class ContributionsSummariesAPIData {

    	/**
    	 * Maps each user (as a {@link edu.ncsu.csc.autovcs.models.persistent.GitUser} to the contributions they have made {@link edu.ncsu.csc.autovcs.services.ContributionAnalysisService.ChangeSummariesList}.  These summaries include both the program analysis results, showing multi-level views of contributions, and a list of the individual commits they have authored.
    	 */
        private final Map<GitUser, ChangeSummariesList> changesPerUser;

        /**
         * Maps each file changed (by filename) to a map of who has touched it, and their relative contributions to the file.
         */
        private final Map<String, Map<GitUser, Double>> changesPerFile;
        
        /**
         * Name of the repository
         */
        private final String repo;
        
        /**
         * End date for the contributions summary, if any
         */
        private final Instant startDate;
        
        /**
         * Start date for the contributions summary, if any
         */
        private final Instant endDate;

        /**
         * All-arguments constructor
         * @param changesPerUser Map of changes made by each user
         * @param percentageContributionPerFile Map from each filename to who has changed the file
         * @param repo Name of the repository the summaries are on
         * @param startDate Start date of the window for the summaries
         * @param endDate End date of the window for the summaries
         */
        public ContributionsSummariesAPIData ( final Map<GitUser, ChangeSummariesList> changesPerUser,
                final Map<String, Map<GitUser, Double>> percentageContributionPerFile, final String repo, final Instant startDate, final Instant endDate) {
            this.changesPerUser = changesPerUser;
            this.changesPerFile = percentageContributionPerFile;
            this.repo = repo;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public Map<GitUser, ChangeSummariesList> getChangesPerUser () {
            return changesPerUser;
        }

        public Map<String, Map<GitUser, Double>> getChangesPerFile () {
            return changesPerFile;
        }

		public String getRepo() {
			return repo;
		}

		public Instant getStartDate() {
			return startDate;
		}
		
		public Instant getEndDate() {
			return endDate;
		}

        
    }

}
