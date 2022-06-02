package edu.ncsu.csc.autovcs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.OkHttpConnector;

import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;

public class AutoVCSProperties {

    private static final String     PROPERTIES_FILE = "gh.properties";

    private static final Properties prop            = new Properties();

    private static final GitHub     gh;

    static {
        try ( InputStream input = new FileInputStream( PROPERTIES_FILE ) ) {
            prop.load( input );
        }
        catch ( final Exception e ) {
            System.err.println( "Could not load properties file to connect to Github." );
            System.exit( -1 );

        }

        gh = connect();

    }

    public static GitHub getGH () {
        return gh;
    }

    static final public Boolean isEnterprise () {
        return Boolean.valueOf( prop.getProperty( "githubEnterprise" ) );
    }

    static final public String getGithubAPIUrl () {
        return isEnterprise() ? prop.getProperty( "enterpriseAPI" ) : "https://api.github.com/";
    }

    static final public String getGithubURL () {
        return isEnterprise() ? prop.getProperty( "enterpriseURL" ) : "https://www.github.com/";
    }

    static final public String getUsername () {
        return prop.getProperty( "username" );
    }

    static final public String getPassword () {
        return prop.getProperty( "password" );
    }

    static final public String getEmailDomain () {
        return prop.getProperty( "desiredEmailDomain" );
    }

    static private final GitHub connect () {
        final File cacheFile = new File( "githubCache" );

        /* 100 MB */
        final Cache cache = new Cache( cacheFile, 100 * 1024 * 1024 );

        GitHub gh;

        GitHubBuilder builder = null;

        try {
            if ( isEnterprise() ) {

                builder = new GitHubBuilder().withEndpoint( getGithubAPIUrl() )
                        .withOAuthToken( prop.getProperty( "token" ) );

            }
            else {
                builder = new GitHubBuilder().withOAuthToken( prop.getProperty( "token" ) );
            }
            builder = builder
                    .withConnector( new OkHttpConnector( new OkUrlFactory( new OkHttpClient().setCache( cache ) ) ) );

            gh = builder.build();

        }
        catch ( final IOException ioe ) {
            throw new IllegalArgumentException(
                    "Unable to connect to Github.  Check your " + PROPERTIES_FILE + " file?" );
        }
        return gh;
    }

}
