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

@Component
public class ContributionAnalysisService {

    @Autowired
    private GHRepositoryService     repositoryService;

    @Autowired
    private GHCommitService         commitService;

    @Autowired
    private GHFileService           fileService;

    @Autowired
    private APIRepositoryController apiCtrl;

    public String getContributionSummaries ( final ContributionsSummaryForm form ) throws Exception {
        return write( aggregateByUser( form ) );

    }

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

        return new ContributionsSummariesAPIData( changes, percentageContributionPerFile );
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

            Thread.sleep( 250 );

            a = new File( aPath );
            b = new File( bPath );

            a.mkdirs();
            b.mkdirs();

            final String url = AutoVCSProperties.getGithubURL();
            final String username = AutoVCSProperties.getUsername();
            final String password = AutoVCSProperties.getPassword();

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
            throw new NoSuchElementException( "There was no data matching the parameters requested" );
        }
        else if ( "BY_USER".equals( form.getType() ) ) {
            return new ContributionsSummaries( contributionsPerCommit, commitsPerUser, contributionsPerFile );
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

    public static final class ChangeSummariesList extends ch.uzh.ifi.seal.changedistiller.api.ChangeSummariesList {

        private List<GHCommit.DisplayCommit> commits = new ArrayList<GHCommit.DisplayCommit>();

        public ChangeSummariesList ( final List<ChangeSummary> changes ) {
            super( changes );
        }

        private void setCommits ( final List<GHCommit.DisplayCommit> commits ) {
            this.commits = commits;
        }

        public List<GHCommit.DisplayCommit> getCommits () {
            return this.commits;
        }

    }

    public static final class ContributionsSummaries {

        private final Map<GHCommit, ChangeSummariesList>         contributionsPerCommit;

        private final Map<GitUser, List<GHCommit.DisplayCommit>> commitsPerUser;

        private final Map<String, FileContributions>             contributionsPerFile;

        public ContributionsSummaries ( final Map<GHCommit, ChangeSummariesList> contributionsPerCommit,
                final Map<GitUser, List<DisplayCommit>> commitsPerUser,
                final Map<String, FileContributions> contributionsPerFile ) {
            super();
            this.contributionsPerCommit = contributionsPerCommit;
            this.commitsPerUser = commitsPerUser;
            this.contributionsPerFile = contributionsPerFile;

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

    }

    public static final class FileContributions {
        private Integer                     overallScore;

        private final Map<GitUser, Integer> contributionPerUser;

        public FileContributions () {
            overallScore = 0;
            contributionPerUser = new HashMap<GitUser, Integer>();

        }

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

    public static final class ContributionsSummariesAPIData {

        private final Map<GitUser, ChangeSummariesList> changesPerUser;

        private final Map<String, Map<GitUser, Double>> changesPerFile;

        public ContributionsSummariesAPIData ( final Map<GitUser, ChangeSummariesList> changesPerUser,
                final Map<String, Map<GitUser, Double>> percentageContributionPerFile ) {
            this.changesPerUser = changesPerUser;
            this.changesPerFile = percentageContributionPerFile;
        }

        public Map<GitUser, ChangeSummariesList> getChangesPerUser () {
            return changesPerUser;
        }

        public Map<String, Map<GitUser, Double>> getChangesPerFile () {
            return changesPerFile;
        }

    }

}
