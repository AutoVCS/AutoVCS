package edu.ncsu.csc.autovcs.datapopulation;

import javax.sql.DataSource;
import javax.transaction.Transactional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import edu.ncsu.csc.autovcs.DBUtils;
import edu.ncsu.csc.autovcs.TestConfig;
import edu.ncsu.csc.autovcs.controllers.api.APIRepositoryController;
import edu.ncsu.csc.autovcs.controllers.api.APIRepositoryController.RepositoryFetchInformation;
import edu.ncsu.csc.autovcs.forms.PopulateDataForm;
import edu.ncsu.csc.autovcs.models.persistent.GHRepository;
import edu.ncsu.csc.autovcs.services.GHRepositoryService;

@RunWith ( SpringRunner.class )
@EnableAutoConfiguration
@SpringBootTest ( classes = TestConfig.class )
@ActiveProfiles ( { "test" } )
public class PopulateDataTest {

    /** Github org for project; test data lives here */
    static private final String     AutoVCS_Org      = "AutoVCS";

    static private final String     AutoVCS_DemoProj = "AutoVCS-CoffeeMaker";

    static private final String     NotExistsOrg     = "ThisOrganisationDoesntExist";
    static private final String     NotExistsProj    = "ThisProjectDoesntExist";

    @Autowired
    private GHRepositoryService     repositoryService;

    @Autowired
    private DataSource              ds;

    @Autowired
    private APIRepositoryController ctrl;

    @Before
    public void setup () {
        DBUtils.resetDB( ds );
    }

    @Test
    @Transactional
    public void testSuccessfulPopulateDataFromGithub () {

        final PopulateDataForm pdf = prepareRepoInitialisation();

        HttpStatus status = null;
        try {
            @SuppressWarnings ( "rawtypes" )
            final ResponseEntity re = ctrl.populateRepositories( pdf );
            status = re.getStatusCode();
        }
        catch ( final Exception e ) {
            e.printStackTrace( System.err );
            Assert.fail(
                    "Trying to populate data from sample repository should always work.  Check your `gh.properties` file" );
        }

        Assert.assertEquals( "Populating data from Github should return a HttpStatus::Ok on success", HttpStatus.OK,
                status );

        final GHRepository cm = repositoryService.findByNameAndOrganisation( AutoVCS_DemoProj, AutoVCS_Org );

        Assert.assertNotNull( "Populated organisation should exist", cm );

        repositoryService.loadCommits( cm );

        Assert.assertEquals( "AutoVCS-CoffeeMaker should have 20 commits", 20, cm.getCommits().size() );

    }

    @Test
    @Transactional
    public void testCannotPopulateWhenProjectDoesNotExist () {
        final PopulateDataForm pdf = prepareRepoInitialisation();
        pdf.setRepository( NotExistsProj );

        HttpStatus status = null;
        RepositoryFetchInformation rfe = null;
        try {
            @SuppressWarnings ( "rawtypes" )
            final ResponseEntity re = ctrl.populateRepositories( pdf );
            status = re.getStatusCode();
            rfe = (RepositoryFetchInformation) re.getBody();
        }
        catch ( final Exception e ) {
            e.printStackTrace( System.err );
            Assert.fail(
                    "A repository that does not exist should not cause an Exception.  Check your `gh.properties` file" );
        }
        Assert.assertEquals(
                "If the repository does not exist, this should come back with a HttpStatus::Ok, and with no repositories fetched",
                HttpStatus.OK, status );

        Assert.assertEquals( "If the repository does not exist, then 0 repositories should be fetched successfully", 0,
                (int) rfe.getHowManyFetched() );

    }

    @Test
    @Transactional
    public void testCannotPopulateWhenRepoDoesNotExist () {
        final PopulateDataForm pdf = prepareRepoInitialisation();
        pdf.setOrganisation( NotExistsOrg );

        HttpStatus status = null;
        try {
            @SuppressWarnings ( "rawtypes" )
            final ResponseEntity re = ctrl.populateRepositories( pdf );
            status = re.getStatusCode();
        }
        catch ( final Exception e ) {
            e.printStackTrace( System.err );
            Assert.fail(
                    "An organisation that does not exist should not cause an Exception.  Check your `gh.properties` file" );
        }

        Assert.assertEquals( "An organisation that does not exist should give a HttpStatus::Not_Found",
                HttpStatus.NOT_FOUND, status );
    }

    @Test
    @Transactional
    public void testCannotCheckDuplicateUsersWithoutTeams () {

        final PopulateDataForm pdf = prepareRepoInitialisation();
        pdf.setCheckDuplicates( true );

        RepositoryFetchInformation rfe = null;
        try {
            @SuppressWarnings ( "rawtypes" )
            final ResponseEntity re = ctrl.populateRepositories( pdf );
            rfe = (RepositoryFetchInformation) re.getBody();
        }
        catch ( final Exception e ) {
            e.printStackTrace( System.err );
            Assert.fail(
                    "Trying to check for duplicate users when not possible should still populate data from Github, so this should not fail" );
        }

        Assert.assertEquals( "Even if duplicate users cannot be checked, the repository should be populated", 1,
                (int) rfe.getHowManyFetched() );

        Assert.assertEquals( "When duplicate users cannot be checked, the repository should be marked accordingly", 1,
                rfe.getUnableToCheckDuplicateUsers().size() );

        Assert.assertTrue( "When duplicate users cannot be checked, the repository should be marked accordingly",
                rfe.getUnableToCheckDuplicateUsers().containsKey( AutoVCS_DemoProj ) );

    }

    /**
     * Generates a population form with a reasonable set of settings for
     * populating data for a sample project we have provided on the AutoVCS
     * organisation
     *
     * @return
     */
    private PopulateDataForm prepareRepoInitialisation () {
        /*
         * Set all information for populating from sample repository on
         * Github.com/AutoVCS
         */
        final PopulateDataForm pdf = new PopulateDataForm();
        pdf.setCheckDuplicates( false );
        pdf.setCommit( true );
        pdf.setFetchAllHistory( true );
        pdf.setOrganisation( AutoVCS_Org );
        pdf.setRepository( AutoVCS_DemoProj );
        pdf.setUser( false );

        return pdf;
    }

}
