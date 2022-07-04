package edu.ncsu.csc.autovcs.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;
import javax.transaction.Transactional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import edu.ncsu.csc.autovcs.DBUtils;
import edu.ncsu.csc.autovcs.TestConfig;
import edu.ncsu.csc.autovcs.TestUtils;
import edu.ncsu.csc.autovcs.models.persistent.GHCommit;
import edu.ncsu.csc.autovcs.models.persistent.GHRepository;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;
import edu.ncsu.csc.autovcs.services.GHCommitService;
import edu.ncsu.csc.autovcs.services.GHRepositoryService;
import edu.ncsu.csc.autovcs.services.GitUserService;

@RunWith ( SpringRunner.class )
@EnableAutoConfiguration
@SpringBootTest ( classes = TestConfig.class )
public class APIUserTest {

    private static final String   API_TEST_USER  = "ApiTestUser";
    private static final String   API_TEST_EMAIL = "user@domain.com";

    private MockMvc               mvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private GitUserService        userService;

    @Autowired
    private GHRepositoryService   repositoryService;

    @Autowired
    private GHCommitService       commitService;

    @Autowired
    private DataSource            ds;

    @Before
    public void setup () {
        mvc = MockMvcBuilders.webAppContextSetup( context ).build();

        DBUtils.resetDB( ds );
    }

    @Test
    @Transactional
    public void testCreateUser () throws Exception {
        Assert.assertEquals( 0, userService.findAll().size() );

        final GitUser user = new GitUser();
        user.setName( API_TEST_USER );
        user.setEmail( API_TEST_EMAIL );

        mvc.perform( post( "/api/v1/users" ).contentType( MediaType.APPLICATION_JSON )
                .content( TestUtils.asJsonString( user ) ) ).andExpect( status().isCreated() );

        Assert.assertEquals( 1, userService.findAll().size() );

    }

    @Test
    @Transactional
    public void testExcludedUsers () throws Exception {
        Assert.assertEquals( 0, userService.findAll().size() );
        createUsers( 5, true );
        createUsers( 7, false );

        final String excludedUsers = mvc
                .perform( get( "/api/v1/users/excluded" ).contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( status().isOk() ).andReturn().getResponse().getContentAsString();

        final List<GitUser> excludedUsersList = TestUtils.gson().fromJson( excludedUsers, List.class );

        Assert.assertEquals( 5, excludedUsersList.size() );

        final String allUsers = mvc.perform( get( "/api/v1/users/" ).contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( status().isOk() ).andReturn().getResponse().getContentAsString();

        final List<GitUser> allUsersList = TestUtils.gson().fromJson( allUsers, List.class );

        Assert.assertEquals( 12, allUsersList.size() );

    }

    @Test
    @Transactional
    public void testIncludeExcludeUsers () throws Exception {
        Assert.assertEquals( 0, userService.findAll().size() );

        final GitUser user = new GitUser();
        user.setName( API_TEST_USER );
        user.setEmail( API_TEST_EMAIL );
        user.setExcluded( true );

        userService.save( user );

        List<GitUser> excluded = userService.findExcluded();

        Assert.assertEquals( 1, excluded.size() );

        final Long id = (Long) excluded.get( 0 ).getId();

        /* Including an excluded user should work */
        mvc.perform( post( String.format( "/api/v1/users/%d/include", id ) ).contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( status().isOk() );

        /* but one that doesn't exist should not work */

        mvc.perform( post( String.format( "/api/v1/users/%d/include", -1 ) ).contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( status().isNotFound() );

        /* make sure the user we included has the right status */

        excluded = userService.findExcluded();

        Assert.assertEquals( 0, excluded.size() );

        /* & that the user still exists */

        Assert.assertEquals( 1, userService.findAll().size() );

        /* try excluding them again */
        mvc.perform( post( String.format( "/api/v1/users/%d/exclude", id ) ).contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( status().isOk() );

        excluded = userService.findExcluded();

        Assert.assertEquals( 1, excluded.size() );
        Assert.assertEquals( 1, userService.findAll().size() );

    }

    @Test
    @Transactional
    public void testIncludeExcludeSearch () throws Exception {
        createUsers( 10, false );

        /*
         * If we search on which users _would_ be excluded by using a common
         * email, we should see all 10
         */
        final String excludedUsersEmail = mvc
                .perform( get( String.format( "/api/v1/users/%s/email/exclude", API_TEST_EMAIL ) )
                        .contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( status().isOk() ).andReturn().getResponse().getContentAsString();

        final List<GitUser> excludedUsersListEmail = TestUtils.gson().fromJson( excludedUsersEmail, List.class );

        Assert.assertEquals( 10, excludedUsersListEmail.size() );

        /* Same by name */
        final String excludedUsersName = mvc
                .perform( get( String.format( "/api/v1/users/%s/name/exclude", API_TEST_USER ) )
                        .contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( status().isOk() ).andReturn().getResponse().getContentAsString();

        final List<GitUser> excludedUsersNameList = TestUtils.gson().fromJson( excludedUsersName, List.class );

        Assert.assertEquals( 10, excludedUsersNameList.size() );

        /* If we search by name & email, we shouldn't get duplicates */
        final String excludedUsers = mvc
                .perform( get( String.format( "/api/v1/users/%s/both/exclude", API_TEST_EMAIL ) )
                        .contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( status().isOk() ).andReturn().getResponse().getContentAsString();

        final List<GitUser> excludedUsersList = TestUtils.gson().fromJson( excludedUsers, List.class );

        Assert.assertEquals( 10, excludedUsersList.size() );

        /* Searching on a unique name (or both) should find just one user */

        final String excludedUsersNameUnique = mvc
                .perform( get( String.format( "/api/v1/users/%s/name/exclude", API_TEST_USER + "0" ) )
                        .contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( status().isOk() ).andReturn().getResponse().getContentAsString();

        final List<GitUser> excludedUsersNameListUnique = TestUtils.gson().fromJson( excludedUsersNameUnique,
                List.class );

        Assert.assertEquals( 1, excludedUsersNameListUnique.size() );

        /* Excluding them should exclude just the one user */
        mvc.perform( post( String.format( "/api/v1/users/%s/name/exclude", API_TEST_USER + "0" ) )
                .contentType( MediaType.APPLICATION_JSON ) ).andExpect( status().isOk() );

        List<GitUser> excluded = userService.findExcluded();

        Assert.assertEquals( 1, excluded.size() );

        Assert.assertEquals( 10, userService.findAll().size() );

        /* If we exclude by the common email, everyone should be excluded now */
        mvc.perform( post( String.format( "/api/v1/users/%s/both/exclude", API_TEST_EMAIL ) )
                .contentType( MediaType.APPLICATION_JSON ) ).andExpect( status().isOk() );

        excluded = userService.findExcluded();

        Assert.assertEquals( 10, excluded.size() );

        Assert.assertEquals( 10, userService.findAll().size() );

    }

    @Test
    @Transactional
    public void testRemapUsers () throws Exception {
        /*
         * tl;dr is we want to collapse commits for all users down into a single
         * user
         */

        final GHRepository repo = new GHRepository();
        repo.setRepositoryName( "TestRepo" );
        repo.setOrganisationName( "TestOrganisation" );
        repositoryService.save( repo );

        for ( int i = 0; i < 20; i++ ) {
            final GitUser user = new GitUser();
            user.setName( API_TEST_USER + i );
            user.setEmail( API_TEST_EMAIL );
            userService.save( user );

            final GHCommit commit = new GHCommit();
            commit.setCommitDate( Instant.now() );
            commit.setSha1( "sha1-" + i );
            commit.setCommitMessage( "Commit Message-" + i );
            commit.setAuthor( user );
            commit.setRepository( repo );
            repo.addCommit( commit );

        }

        repositoryService.save( repo );

        Assert.assertEquals( 20, commitService.findAll().size() );

        final List<GitUser> allUsers = userService.findAll();

        Assert.assertEquals( 20, allUsers.size() );

        final GitUser targetUser = allUsers.get( 0 );

        final List<GHCommit> originalCommitsForUser = commitService.findByUser( targetUser );

        Assert.assertEquals( 1, originalCommitsForUser.size() );

        allUsers.remove( targetUser );

        final Map<Long, Long> remappingOptions = new HashMap<Long, Long>();

        allUsers.forEach( user -> {
            remappingOptions.put( (Long) user.getId(), (Long) targetUser.getId() );
        } );

        mvc.perform( post( "/api/v1/users/remap" ).contentType( MediaType.APPLICATION_JSON )
                .content( TestUtils.asJsonString( remappingOptions ) ) ).andExpect( status().isOk() );

        /* Single user should "own" all commits now */
        final List<GHCommit> remappedCommits = commitService.findByUser( targetUser );
        Assert.assertEquals( 20, remappedCommits.size() );
    }

    private void createUsers ( final Integer howMany, final boolean excluded ) {
        for ( int i = 0; i < howMany; i++ ) {
            final GitUser user = new GitUser();
            user.setName( API_TEST_USER + i );
            user.setEmail( API_TEST_EMAIL );
            user.setExcluded( excluded );
            userService.save( user );
        }
    }

}
