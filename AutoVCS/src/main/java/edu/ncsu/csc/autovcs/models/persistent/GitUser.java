package edu.ncsu.csc.autovcs.models.persistent;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.kohsuke.github.GHCommit.GHAuthor;
import org.kohsuke.github.GHUser;

import edu.ncsu.csc.autovcs.AutoVCSProperties;

/**
 * Represents a local Git user or a user in a Github/Github Enterprise system.
 * Stores the user's email address and name. Users are considered identical if
 * they have the same name and email address.
 *
 *
 * @author Kai Presler-Marshall
 */
@Entity
public class GitUser extends DomainObject {

    @Id
    @GeneratedValue
    private Long    id;

    private String  name;

    private String  email;

    /**
     * Excluded users won't show up in summaries, but their data is still stored
     * in case we change our mind later
     */
    private boolean excluded = false;

    public GitUser () {
    }

    public GitUser ( final GHAuthor other ) {
        this.email = other.getEmail();
        this.name = other.getName();
    }

    public GitUser ( final GHUser other ) {
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

    static private String buildEmail ( final String username ) {
        return username + "@" + AutoVCSProperties.getEmailDomain();
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
        return Objects.hash( email, excluded, id, name );
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
        return Objects.equals( email, other.email ) && excluded == other.excluded && Objects.equals( id, other.id )
                && Objects.equals( name, other.name );
    }

}
