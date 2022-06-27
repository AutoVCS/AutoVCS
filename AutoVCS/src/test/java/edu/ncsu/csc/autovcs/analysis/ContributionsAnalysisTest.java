package edu.ncsu.csc.autovcs.analysis;

import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import ch.uzh.ifi.seal.changedistiller.api.ChangeSummary;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.java.JavaEntityType;
import edu.ncsu.csc.autovcs.DBUtils;
import edu.ncsu.csc.autovcs.forms.ContributionsSummaryForm;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;
import edu.ncsu.csc.autovcs.services.ContributionAnalysisService;
import edu.ncsu.csc.autovcs.services.ContributionAnalysisService.ChangeSummariesList;

public class ContributionsAnalysisTest {

    private static final String ORG = "AutoVCS";
    private static final String CM  = "AutoVCS-CoffeeMaker";
    private static final String TU  = "AutoVCS-MultiUserProject";

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

        final Map<GitUser, ChangeSummariesList> aggregatedChanges = ContributionAnalysisService.aggregateByUser( csf )
                .getChangesPerUser();

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

        final Map<GitUser, ChangeSummariesList> aggregatedChanges = ContributionAnalysisService.aggregateByUser( csf )
                .getChangesPerUser();

        final GitUser kai = GitUser.getByEmailContaining( "kpresle@ncsu.edu" ).get( 0 );
        final ChangeSummariesList kaiContributions = aggregatedChanges.get( kai );
        Assert.assertEquals( 2165, (int) kaiContributions.getContributionsScore() );
    }

    @Test
    public void testAnalysisMultipleUsers () {

        final ContributionsSummaryForm csf = new ContributionsSummaryForm();

        csf.setOrganisation( ORG );
        csf.setRepository( TU );
        csf.setInitialiseUnknown( true );
        csf.setExcludeGUI( true );
        csf.setType( "BY_USER" );

        final Map<GitUser, ChangeSummariesList> aggregatedChanges = ContributionAnalysisService.aggregateByUser( csf )
                .getChangesPerUser();

        /* Two users on the project, both should show up */
        Assert.assertEquals( 2, aggregatedChanges.size() );

        /*
         * Both users should have been created during the analysis...if not,
         * fail
         */
        final GitUser a = GitUser.getByNameContaining( "User A" ).get( 0 );
        final GitUser b = GitUser.getByNameContaining( "User B" ).get( 0 );

        Assert.assertNotNull( a );
        Assert.assertNotNull( b );

        final ChangeSummariesList aChanges = aggregatedChanges.get( a );

        /* Two ChangeSummary objects, one for each class */
        Assert.assertEquals( 2, aChanges.getChanges().size() );

        final ChangeSummary ClassB = aChanges.getChanges().get( 0 );
        /**
         * <pre>
         * [0] variable assignment initialising singleton in getInstance
         * [1] then branch of if statement in getInstance
         * [2] if statement in getInstance
         * [3] return statement in getInstance
         * [4] getInstance method
         * [5] instance field
         * [6] ClassB declaration
         * </pre>
         */

        Assert.assertEquals( JavaEntityType.ASSIGNMENT, ClassB.getAllChanges().get( 0 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.THEN_STATEMENT,
                ClassB.getAllChanges().get( 1 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.IF_STATEMENT,
                ClassB.getAllChanges().get( 2 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.RETURN_STATEMENT,
                ClassB.getAllChanges().get( 3 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.METHOD, ClassB.getAllChanges().get( 4 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.FIELD, ClassB.getAllChanges().get( 5 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.CLASS, ClassB.getAllChanges().get( 6 ).getChangedEntity().getType() );

        final ChangeSummary ClassA = aChanges.getChanges().get( 1 );
        /**
         * <pre>
         * [0] snarky comment in doSomething
         * [1] first assignment in doSomething
         * [2] second assignment in doSomething
         * [3] doSomething method
         * [4] first field declaration
         * [5] second field declaration
         * [6] Class declaration
         * </pre>
         */

        Assert.assertEquals( JavaEntityType.BLOCK_COMMENT,
                ClassA.getAllChanges().get( 0 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.ASSIGNMENT, ClassA.getAllChanges().get( 1 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.ASSIGNMENT, ClassA.getAllChanges().get( 2 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.METHOD, ClassA.getAllChanges().get( 3 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.FIELD, ClassA.getAllChanges().get( 4 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.FIELD, ClassA.getAllChanges().get( 5 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.CLASS, ClassA.getAllChanges().get( 6 ).getChangedEntity().getType() );

        /* Check overall score too */
        Assert.assertEquals( 299, (int) aChanges.getContributionsScore() );

        final ChangeSummariesList bChanges = aggregatedChanges.get( b );

        /* Only a single ChangeSummary, b/c the only touched one class */
        Assert.assertEquals( 1, bChanges.getChanges().size() );

        final ChangeSummary bChange = bChanges.getChanges().get( 0 );

        /*
         * NOTE: You would logically expect the test setup method created
         * (https://github.com/AutoVCS/AutoVCS-MultiUserProject/commit/
         * ed90d2ece10bd77bbc78b247eee835851cb7b7b9#diff-
         * 1e1f05fe6cf663538b117c907b4056f4c1c596c3215338341de881f71d46cfebR7)
         * to be included here. However, this matches against the method
         * signature for setter methods (name matches against set*, only one
         * setter statement in the setter method. Rather than hacky hard-coding
         * things around the specifics of JUnit setter method signatures, it
         * feels preferable to accept that occasionally things will fall through
         * the cracks like this. The test is working as expected :)
         */

        /**
         * <pre>
         * [0] assertEquals in second method
         * [1] annotation on second method
         * [2] assertEquals in first method
         * [3] annotation on first method
         * [4] second test method
         * [5] first test method
         * [6] field in test class for instance of tested class
         * [7] test class itself
         * </pre>
         */

        Assert.assertEquals( JavaEntityType.METHOD_INVOCATION,
                bChange.getAllChanges().get( 0 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.ANNOTATION, bChange.getAllChanges().get( 1 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.METHOD_INVOCATION,
                bChange.getAllChanges().get( 2 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.ANNOTATION, bChange.getAllChanges().get( 3 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.METHOD, bChange.getAllChanges().get( 4 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.METHOD, bChange.getAllChanges().get( 5 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.FIELD, bChange.getAllChanges().get( 6 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.CLASS, bChange.getAllChanges().get( 7 ).getChangedEntity().getType() );

        /* Check overall score too */
        Assert.assertEquals( 172, (int) bChanges.getContributionsScore() );

    }

    @Test
    public void testContributionsAnalysisMultipleUsersOneExcluded () {

        /*
         * If we have multiple users, but exclude one, we should still only see
         * what the single user remaining did
         */

        /*
         * First, have to create a user to represent the one we want to exclude
         */
        final GitUser userB = new GitUser();
        userB.setEmail( "UserB@domain.com" );
        userB.setName( "User B" );
        userB.setExcluded( true );
        userB.save();

        final ContributionsSummaryForm csf = new ContributionsSummaryForm();

        csf.setOrganisation( ORG );
        csf.setRepository( TU );
        csf.setInitialiseUnknown( true );
        csf.setExcludeGUI( true );
        csf.setType( "BY_USER" );

        final Map<GitUser, ChangeSummariesList> aggregatedChanges = ContributionAnalysisService.aggregateByUser( csf )
                .getChangesPerUser();

        /* Two users on the project, both should show up */
        Assert.assertEquals( 1, aggregatedChanges.size() );

        /*
         * Both users should have been created during the analysis...if not,
         * fail
         */
        final GitUser a = GitUser.getByNameContaining( "User A" ).get( 0 );

        Assert.assertNotNull( a );

        final ChangeSummariesList aChanges = aggregatedChanges.get( a );

        Assert.assertEquals( 299, (int) aChanges.getContributionsScore() );
        Assert.assertEquals( 100, aChanges.getContributionsScorePercent(), 0 );

    }

}
