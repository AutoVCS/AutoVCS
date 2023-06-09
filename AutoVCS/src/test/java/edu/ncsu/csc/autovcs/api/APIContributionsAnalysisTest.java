package edu.ncsu.csc.autovcs.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;
import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import edu.ncsu.csc.autovcs.DBUtils;
import edu.ncsu.csc.autovcs.TestConfig;
import edu.ncsu.csc.autovcs.TestUtils;
import edu.ncsu.csc.autovcs.forms.ContributionsSummaryForm;

@RunWith ( SpringRunner.class )
@EnableAutoConfiguration
@SpringBootTest ( classes = TestConfig.class )
@ActiveProfiles ( { "test" } )
public class APIContributionsAnalysisTest {

    private static final String   ORG = "AutoVCS";
    private static final String   CM  = "AutoVCS-CoffeeMaker";
    private static final String   INV_FN = "AutoVCS-InvalidFilenames";

    private MockMvc               mvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DataSource            ds;

    @BeforeEach
    public void setup () {
        mvc = MockMvcBuilders.webAppContextSetup( context ).build();

        DBUtils.resetDB( ds );

    }

    @Test
    @Transactional
    public void testContributionsAPI () throws Exception {

        final ContributionsSummaryForm csf = new ContributionsSummaryForm();

        csf.setOrganisation( ORG );
        csf.setRepository( CM );
        csf.setInitialiseUnknown( true );
        csf.setExcludeGUI( true );
        csf.setType( "BY_USER" );
        /* Aug 9-11 includes some interesting Java refactoring to analyse */
        csf.setStartDate( "2021-08-09T00:00:00Z" );
        csf.setEndDate( "2021-08-11T00:00:00Z" );

        final String contributionsData = mvc
                .perform( post( "/api/v1/contributions" ).contentType( MediaType.APPLICATION_JSON )
                        .content( TestUtils.asJsonString( csf ) ) )
                .andExpect( status().isOk() ).andReturn().getResponse().getContentAsString();

        final Object response = TestUtils.gson().fromJson( contributionsData, Map.class );

        final Map<String, Map<String, Object>> unpacked = (Map<String, Map<String, Object>>) response;

        final Map<String, Object> entry = unpacked.get( "changesPerUser" );

        final Map<String, Object> kaiContribs = (Map<String, Object>) entry
                .get( "Kai Presler-Marshall (kpresle@ncsu.edu)" );

        /*
         * The way the JSON data deserialises, we don't get a nice type that we
         * can cast into or deserialise into....instead we have this horrible
         * hack, where our values that were originally ints are now doubles, so
         * we need this fun
         */
        final Integer score = (int) (double) kaiContribs.get( "contributionsScore" );

        final Integer scorePercent = Double.valueOf(kaiContribs.get( "contributionsScorePercent" ).toString()).intValue();

        Assertions.assertEquals( 2165, (int) score );
        Assertions.assertEquals( 100, (int) scorePercent );

    }

    @Test
    @Transactional
    public void testContributionsAPINoData () throws Exception {
        final ContributionsSummaryForm csf = new ContributionsSummaryForm();

        csf.setOrganisation( ORG );
        csf.setRepository( CM );
        csf.setInitialiseUnknown( true );
        csf.setExcludeGUI( true );
        csf.setType( "BY_USER" );
        /*
         * Definitely nothing in this window as it predates the repo being
         * created
         */
        csf.setStartDate( "2011-08-09T00:00:00Z" );
        csf.setEndDate( "2011-08-11T00:00:00Z" );

        final String contributionsData = mvc
                .perform( post( "/api/v1/contributions" ).contentType( MediaType.APPLICATION_JSON )
                        .content( TestUtils.asJsonString( csf ) ) )
                .andExpect( status().isOk() ).andReturn().getResponse().getContentAsString();

        final Object response = TestUtils.gson().fromJson( contributionsData, Map.class );

        final Map<String, Map<String, Object>> unpacked = (Map<String, Map<String, Object>>) response;

        final Map<String, Object> entry = unpacked.get( "changesPerUser" );

        Assertions.assertEquals(0, entry.size());

    }
    
    
    @Test
    @Transactional
    public void testContributionsAPIInvalidPaths() throws Exception {
        final ContributionsSummaryForm csf = new ContributionsSummaryForm();

        csf.setOrganisation( ORG );
        csf.setRepository( INV_FN );
        csf.setInitialiseUnknown( true );
        csf.setExcludeGUI( true );
        csf.setType( "BY_USER" );
        
        final String contributionsData = mvc
                .perform( post( "/api/v1/contributions" ).contentType( MediaType.APPLICATION_JSON )
                        .content( TestUtils.asJsonString( csf ) ) )
                .andExpect( status().isOk() ).andReturn().getResponse().getContentAsString();

       
        final Object response = TestUtils.gson().fromJson( contributionsData, Map.class );

        final Map<String, Map<String, Object>> unpacked = (Map<String, Map<String, Object>>) response;

        final Map<String, Object> entry = unpacked.get( "changesPerUser" );
        
        
        final Map<String, Object> kaiContribs = (Map<String, Object>) entry
                .get( "Kai Presler-Marshall (kai@presler-marshall.org)" );

        /*
         * The way the JSON data deserialises, we don't get a nice type that we
         * can cast into or deserialise into....instead we have this horrible
         * hack, where our values that were originally ints are now doubles, so
         * we need this fun
         */
        final Integer score = (int) (double) kaiContribs.get( "contributionsScore" );

        final Integer scorePercent = Double.valueOf(kaiContribs.get( "contributionsScorePercent" ).toString()).intValue();

        Assertions.assertEquals( 253, (int) score );
        Assertions.assertEquals( 100, (int) scorePercent );
        
        Map<String, Object> binnedChanges = (Map<String, Object>) kaiContribs.get("binnedChanges");
        
        List<String> newClasses = (List<String>) binnedChanges.get("Insert CLASS");
        
        Assertions.assertEquals(2,  newClasses.size());

    }

}
