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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import com.google.gson.Gson;

import edu.ncsu.csc.autovcs.AutoVCSProperties;
import edu.ncsu.csc.autovcs.forms.ContributionsSummaryForm;
import edu.ncsu.csc.autovcs.services.ContributionAnalysisService;

@ComponentScan ( "edu.ncsu.csc.autovcs" )
@SpringBootApplication
public class BatchRunner {

    static private final List<String>          successfulRepositories = new Vector<String>();

    static private final Map<String, String>   failedRepositories     = new ConcurrentHashMap<String, String>();

    private static final Gson                  gson                   = new Gson();

    static private ContributionAnalysisService cas;

    public static void main ( final String[] args ) throws Exception {
        /* Don't launch Tomcat: https://stackoverflow.com/a/44394305 */
        final ConfigurableApplicationContext ctx = new SpringApplicationBuilder( BatchRunner.class )
                .web( WebApplicationType.NONE ).run( args );
        /* Service lookup: https://stackoverflow.com/questions/46617044/how-to-use-autowired-autowired-references-from-mainstring-args-method */
        cas = ctx.getBean( ContributionAnalysisService.class );


        run( args );

        System.exit( 0 );

    }

    static public void run ( final String... args ) throws Exception {

        final CommandLine line = parseOptions( args );
        final Path configurationFile = Path.of( getConfigurationFile( line ) );
        final Path templateFile = Path.of( getTemplateFile( line ) );
        final boolean debug = getDebug( line );
        final Integer timeout = getTimeout( line );
        final Integer nCPUs = getNCPUs( line );

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

        final ExecutorService threadPool = Executors.newFixedThreadPool( nCPUs );

        /*
         * JSON array supports running arbitrarily many different repositories,
         * or repository prefixes, at once, each with different configurations
         */

        
        System.out.println( "\n\n\n\n\n\n\n\n" );
        System.out.println( "##########################################################" );
        System.out.printf("Parsed %d run configurations from JSON file\n", bc.getRepositories().size());
        System.out.println( "##########################################################" );
        
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

        threadPool.awaitTermination( timeout, TimeUnit.HOURS );

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
                e.printStackTrace();
                failedRepositories.put( name, e.getMessage() );
            }

        }

        private void analyseAndWrite () throws Exception {
            final String data = cas.getContributionSummaries( csf );

            final String builtPage = template.replace( "AUTOVCS_JSON_DATA", data );

            final Path outputFile = Path.of( "output/" + name + ".html" );

            Files.writeString( outputFile, builtPage );
        }

    }

    static private CommandLine parseOptions ( final String[] args ) {

        final Options options = new Options();
        options.addOption( new Option( "d", "debug", false, "Enable debug mode" ) );
        options.addOption( new Option( "n", "threads", true, "Maximum number of worker threads for analysis" ) );
        options.addOption( new Option( "te", "template", true, "Location of the template file" ) );
        options.addOption( new Option( "c", "config", true,
                "Location of JSON configuration file specifying what repositories to analyse" ) );
        options.addOption(
                new Option( "ti", "timeout", true, "Maximum time, in hours, to wait for analyses to complete" ) );

        // create the parser
        final CommandLineParser parser = new DefaultParser();
        try {
            // parse the command line arguments
            final CommandLine line = parser.parse( options, args );
            return line;
        }
        catch ( final ParseException exp ) {
            // oops, something went wrong
            System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
            System.exit( -1 );
            return null; // will never happen, but won't compile w/o it
        }

    }

    static private Integer getNCPUs ( final CommandLine line ) {
        if ( line.hasOption( "threads" ) ) {
            return Integer.valueOf( line.getOptionValue( "threads" ) );
        }
        return Math.min( 8, Runtime.getRuntime().availableProcessors() );

    }

    static private String getTemplateFile ( final CommandLine line ) {
        if ( line.hasOption( "template" ) ) {
            return line.getOptionValue( "template" );
        }
        return "AutoVCS.template";
    }

    static private String getConfigurationFile ( final CommandLine line ) {
        if ( line.hasOption( "config" ) ) {
            return line.getOptionValue( "config" );
        }
        return "config.json";
    }

    static private Boolean getDebug ( final CommandLine line ) {
        if ( line.hasOption( "debug" ) ) {
            return Boolean.valueOf( line.getOptionValue( "debug" ) );
        }
        return false;
    }

    static private Integer getTimeout ( final CommandLine line ) {
        if ( line.hasOption( "timeout" ) ) {
            return Integer.valueOf( line.getOptionValue( "timeout" ) );
        }
        return 1;
    }

}
