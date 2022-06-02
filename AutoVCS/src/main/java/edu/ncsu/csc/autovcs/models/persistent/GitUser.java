package edu.ncsu.csc.autovcs.models.persistent;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Restrictions;
import org.kohsuke.github.GHCommit.GHAuthor;

import edu.ncsu.csc.autovcs.AutoVCSProperties;

import org.kohsuke.github.GHUser;

/**
 * Represents a local Git user or a user in a Github/Github Enterprise system.
 * Stores the user's email address and name. Users are considered identical if
 * they have the same name and email address.
 *
 *
 * @author Kai
 */
@Entity
@Table ( name = "GitUsers" )
@SuppressWarnings ( "deprecation" )
public class GitUser extends DomainObject<GitUser> {

    private static DomainObjectCache<String, GitUser> cache    = new DomainObjectCache<String, GitUser>(
            GitUser.class );

    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    Long                                              id;

    private String                                    name;

    private String                                    email;

    /**
     * Excluded users won't show up in summaries, but their data is still stored
     * in case we change our mind later
     */
    private boolean                                   excluded = false;

    public GitUser () {
    }

    @Override
    public String getKey () {
        return String.format( "%s:%s", name, email );
    }

    private GitUser ( final GHAuthor other ) {
        this.email = other.getEmail();
        this.name = other.getName();
    }

    private GitUser ( final GHUser other ) {
        try {
            if ( null != other.getEmail() ) {
                this.email = other.getEmail();
            }
            else {
                this.email = buildEmail( other.getLogin() );
            }

        }
        catch ( final IOException e ) {

        }
        this.name = other.getLogin();

    }

    public static GitUser forUser ( final GHUser other ) {

        String key = null;
        try {
            if ( null != other.getEmail() ) {
                key = String.format( "%s:%s", other.getLogin(), other.getEmail() );
            }
            else {
                key = String.format( "%s:%s", other.getLogin(), buildEmail( other.getLogin() ) );
            }
        }
        catch ( final IOException e1 ) {
            throw null;
        }
        GitUser user = checkCache( key );

        if ( null == user ) {
            final List<Criterion> criteria = new ArrayList<Criterion>();
            criteria.add( eq( "name", other.getLogin() ) );
            try {
                if ( null != other.getEmail() ) {
                    criteria.add( eq( "email", other.getEmail() ) );
                }
                else {
                    criteria.add( eq( "email", buildEmail( other.getLogin() ) ) );
                }

            }
            catch ( final IOException e ) {

            }
            @SuppressWarnings ( "unchecked" )
            final List<GitUser> matching = (List<GitUser>) getWhere( GitUser.class, criteria );

            if ( !matching.isEmpty() ) {
                user = matching.get( 0 );
            }
            else {
                user = new GitUser( other );
                user.save();
            }
            cache.put( key, user );
        }
        return user;
    }

    public static List<GitUser> getAll () {
        return (List<GitUser>) getAll( GitUser.class );
    }

    public static List<GitUser> getExcluded () {
        return (List<GitUser>) getWhere( GitUser.class, eqList( "excluded", true ) );
    }

    static private String buildEmail ( final String username ) {
        return username + "@" + AutoVCSProperties.getEmailDomain();
    }

    private static GitUser checkCache ( final String key ) {
        final GitUser user = cache.get( key );
        return user;
    }

    public static GitUser forUser ( final GHAuthor other ) {
        final String key = String.format( "%s:%s", other.getName(), other.getEmail() );

        GitUser user = checkCache( key );

        if ( null == user ) {

            final List<Criterion> criteria = new ArrayList<Criterion>();
            criteria.add( eq( "name", other.getName() ) );
            if ( null != other.getEmail() ) {
                criteria.add( eq( "email", other.getEmail() ) );
            }
            @SuppressWarnings ( "unchecked" )
            final List<GitUser> matching = (List<GitUser>) getWhere( GitUser.class, criteria );

            if ( !matching.isEmpty() ) {
                user = matching.get( 0 );
            }
            else {
                user = new GitUser( other );
                user.save();
            }
            cache.put( key, user );
        }
        return user;
    }

    public static GitUser getById ( final Long id ) {
        @SuppressWarnings ( "unchecked" )
        final List<GitUser> matching = (List<GitUser>) getWhere( GitUser.class, eqList( ID, id ) );
        if ( !matching.isEmpty() ) {
            return matching.get( 0 );
        }
        else {
            return null;
        }
    }

    public static List<GitUser> getByNameContaining ( final String name ) {
        return (List<GitUser>) getWhere( GitUser.class,
                Collections.singletonList( Restrictions.ilike( "name", name, MatchMode.ANYWHERE ) ) );
    }

    public static List<GitUser> getByEmailContaining ( final String email ) {
        return (List<GitUser>) getWhere( GitUser.class,
                Collections.singletonList( Restrictions.ilike( "email", email, MatchMode.ANYWHERE ) ) );
    }

    @Override
    public Serializable getId () {
        return id;
    }

    public String getName () {
        return name;
    }

    public String getEmail () {
        return email;
    }

    public boolean isExcluded () {
        return excluded;
    }

    public void setExcluded ( final boolean excluded ) {
        this.excluded = excluded;
    }

    public void setName ( final String name ) {
        this.name = name;
    }

    public void setEmail ( final String email ) {
        this.email = email;
    }

    @Override
    public String toString () {
        return null == getName() ? "Overall" : String.format( "%s (%s)", getName(), getEmail() );
    }

    @Override
    public int hashCode () {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( email == null ) ? 0 : email.hashCode() );
        result = prime * result + ( ( id == null ) ? 0 : id.hashCode() );
        result = prime * result + ( ( name == null ) ? 0 : name.hashCode() );
        return result;
    }

    @Override
    public boolean equals ( final Object obj ) {
        if ( this == obj ) {
            return true;
        }
        if ( obj == null ) {
            return false;
        }
        if ( getClass() != obj.getClass() ) {
            return false;
        }
        final GitUser other = (GitUser) obj;
        if ( email == null ) {
            if ( other.email != null ) {
                return false;
            }
        }
        else if ( !email.equals( other.email ) ) {
            return false;
        }
        if ( name == null ) {
            if ( other.name != null ) {
                return false;
            }
        }
        else if ( !name.equals( other.name ) ) {
            return false;
        }
        return true;
    }
}
