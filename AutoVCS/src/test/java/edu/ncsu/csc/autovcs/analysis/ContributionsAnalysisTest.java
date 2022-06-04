package edu.ncsu.csc.autovcs.analysis;

import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import edu.ncsu.csc.autovcs.DBUtils;
import edu.ncsu.csc.autovcs.forms.ContributionsSummaryForm;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;
import edu.ncsu.csc.autovcs.services.ContributionAnalysisService;
import edu.ncsu.csc.autovcs.services.ContributionAnalysisService.ChangeSummariesList;

public class ContributionsAnalysisTest {

    private static final String ORG = "AutoVCS";
    private static final String CM  = "AutoVCS-CoffeeMaker";

    @Before
    public void setup () {
        DBUtils.resetDB();
    }

    /**
     * This test will spew _many_ NoSuchFileExceptions due to the changed
     * structure of the CoffeeMaker project & files that were deleted. This is
     * OK!
     */
    @Test
    public void testFullAnalysis () {

        final ContributionsSummaryForm csf = new ContributionsSummaryForm();

        csf.setOrganisation( ORG );
        csf.setRepository( CM );
        csf.setInitialiseUnknown( true );
        csf.setExcludeGUI( true );
        csf.setType( "BY_USER" );

        final Map<GitUser, ChangeSummariesList> aggregatedChanges = ContributionAnalysisService.aggregateByUser( csf );

        /*
         * Only one user should show up here, b/c other users have only made
         * merge commits
         */
        Assert.assertEquals( 1, aggregatedChanges.size() );

        final GitUser kai = GitUser.getByEmailContaining( "kpresle@ncsu.edu" ).get( 0 );
        final ChangeSummariesList kaiContributions = aggregatedChanges.get( kai );
        Assert.assertEquals( 14232, (int) kaiContributions.getContributionsScore() );

    }

    @Test
    public void testFullAnalysisTimeWindow () {
        final ContributionsSummaryForm csf = new ContributionsSummaryForm();

        csf.setOrganisation( ORG );
        csf.setRepository( CM );
        csf.setInitialiseUnknown( true );
        csf.setExcludeGUI( true );
        csf.setType( "BY_USER" );
        /* Aug 9-11 includes some interesting Java refactoring to analyse */
        csf.setStartDate( "2021-08-09T00:00:00Z" );
        csf.setEndDate( "2021-08-11T00:00:00Z" );

        final Map<GitUser, ChangeSummariesList> aggregatedChanges = ContributionAnalysisService.aggregateByUser( csf );

        final GitUser kai = GitUser.getByEmailContaining( "kpresle@ncsu.edu" ).get( 0 );
        final ChangeSummariesList kaiContributions = aggregatedChanges.get( kai );
        Assert.assertEquals( 2165, (int) kaiContributions.getContributionsScore() );
    }

}
