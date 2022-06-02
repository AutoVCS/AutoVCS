package edu.ncsu.csc.autovcs.batch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import com.google.gson.Gson;

import edu.ncsu.csc.autovcs.AutoVCSProperties;
import edu.ncsu.csc.autovcs.forms.ContributionsSummaryForm;
import edu.ncsu.csc.autovcs.services.ContributionAnalysisService;

public class BatchRunner {

    static private final List<String>        successfulRepositories = new Vector<String>();

    static private final Map<String, String> failedRepositories     = new ConcurrentHashMap<String, String>();

    private static final Gson                gson                   = new Gson();

    /**
     *
     * @param args
     *            args0 is JSON configuration file describing what repos to run,
     *            args1 is template file to substitute JSON data into args2 is
     *            debug flag
     */
    public static void main ( final String[] args ) throws Exception {

        /* Load in config files, prepare output folder */

        final Path configurationFile = Path.of( args[0] );

        final Path templateFile = Path.of( args[1] );

        final boolean debug = 3 == args.length ? Boolean.valueOf( args[2] ) : false;

        final File output = new File( "output" );
        output.mkdir();

        String configuration;
        String template;
        try {
            configuration = Files.readString( configurationFile );
        }
        catch ( final IOException e1 ) {
            throw new IllegalArgumentException( "Could not read input file" );
        }

        try {
            template = Files.readString( templateFile );
        }
        catch ( final IOException e1 ) {
            throw new IllegalArgumentException( "Could not read template file" );
        }

        final BatchConfiguration bc = gson.fromJson( configuration, BatchConfiguration.class );

        final ExecutorService threadPool = Executors.newFixedThreadPool( 8 );

        /*
         * JSON array supports running arbitrarily many different repositories,
         * or repository prefixes, at once, each with different configurations
         */

        bc.getRepositories().forEach( RepositoryOption -> {

            final String organisation = bc.getOrganisation();

            /*
             * If this configuration represents just one repository, very easy,
             * go run that repo
             */
            if ( RepositoryOption.getExactMatch() ) {
                final ContributionsSummaryForm csf = RepositoryOption.toForm( organisation );

                threadPool.execute( new RunnerWorker( csf, template, RepositoryOption.getName() ) );

            }
            /*
             * otherwise, we'll need to figure out all repos that match the
             * prefix provided
             */
            else {
                final GitHub github = AutoVCSProperties.getGH();
                /* Which requires hitting GH to get a list of matching ones */
                GHOrganization org;
                try {
                    org = github.getOrganization( organisation );

                    final Map<String, GHRepository> repoMap = org.getRepositories();

                    for ( final String repo : repoMap.keySet() ) {
                        if ( repo.startsWith( RepositoryOption.getName() ) ) {
                            /*
                             * If it matches, create a form to run with, same as
                             * before
                             */
                            final ContributionsSummaryForm csf = RepositoryOption.toForm( organisation, repo );
                            threadPool.execute( new RunnerWorker( csf, template, repo ) );

                            /*
                             * Back off a bit before we potentially launch the
                             * next thread
                             */
                            try {
                                Thread.sleep( 10 * 1000 );
                            }
                            catch ( final Exception e ) {

                            }

                        }
                    }
                }
                catch ( final IOException e ) {

                }

            }

        } );

        System.out.println( "\n\n\n\n\n\n\n\n" );
        System.out.println( "##########################################################" );
        System.out.println( "All analyses have been started" );
        System.out.println( "Waiting for tasks to complete" );
        System.out.println( "##########################################################" );

        threadPool.shutdown();

        threadPool.awaitTermination( 1, TimeUnit.HOURS );

        System.out.println( "\n\n\n\n\n\n\n\n" );
        System.out.println( "##########################################################" );
        System.out.printf( "Successfully created summary pages for %d repositories\n", successfulRepositories.size() );
        System.out.println( "##########################################################" );
        if ( !failedRepositories.isEmpty() ) {
            System.out.printf( "Could not create files for %d repositories:\n", failedRepositories.size() );
            failedRepositories.forEach( ( repository, failureCause ) -> {
                System.out.println( "    " + repository );
                if ( debug ) {
                    System.out.println( "    -> due to `" + failureCause + "`" );
                }
            } );
            System.out.println( "##########################################################" );

        }

        System.exit( 0 );

    }

    static private class RunnerWorker implements Runnable {

        private final ContributionsSummaryForm csf;
        private final String                   template;
        private final String                   name;

        public RunnerWorker ( final ContributionsSummaryForm csf, final String template, final String name ) {
            this.csf = csf;
            this.template = template;
            this.name = name;
        }

        @Override
        public void run () {
            try {
                analyseAndWrite();
                successfulRepositories.add( name );
            }
            catch ( final Exception e ) {
                failedRepositories.put( name, e.getMessage() );
            }

        }

        private void analyseAndWrite () throws Exception {
            final String data = ContributionAnalysisService.getContributionSummaries( csf );

            final String builtPage = template.replace( "AUTOVCS_JSON_DATA", data );

            final Path outputFile = Path.of( "output/" + name + ".html" );

            Files.writeString( outputFile, builtPage );
        }

    }

}
