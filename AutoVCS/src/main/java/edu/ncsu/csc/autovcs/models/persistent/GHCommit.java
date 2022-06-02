package edu.ncsu.csc.autovcs.models.persistent;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.kohsuke.github.GHCommit.File;
import org.kohsuke.github.GHCommit.GHAuthor;

@Entity
@Table ( name = "GHCommits" )
@SuppressWarnings ( { "deprecation", "unchecked" } )
public class GHCommit extends DomainObject<GHCommit> {

    private static DomainObjectCache<String, GHCommit> cache = new DomainObjectCache<String, GHCommit>(
            GHCommit.class );

    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private Long                                       id;

    @ManyToOne // (cascade = CascadeType.ALL)
    private GitUser                                    author;

    @ManyToOne // (cascade = CascadeType.ALL)
    private GitUser                                    committer;

    @NotNull
    @Column ( columnDefinition = "text" )
    private String                                     commitMessage;

    @NotNull
    private String                                     sha1;

    @ManyToOne
    @NotNull
    private GHRepository                               repository;

    @ElementCollection ( fetch = FetchType.EAGER )
    private final Set<String>                          associatedBranches;

    private Instant                                    commitDate;

    private boolean                                    isMergeCommit;

    private Integer                                    linesAdded;

    private Integer                                    linesRemoved;

    private Integer                                    linesChanged;

    private Integer                                    filesChanged;

    private String                                     url;

    private String                                     parent;

    @OneToMany ( mappedBy = "associatedCommit", cascade = CascadeType.ALL, fetch = FetchType.LAZY )
    private Set<GHFile>                                files;

    public GHCommit ( final org.kohsuke.github.GHCommit c ) {
        this( c, null );
    }

    public GHCommit ( final org.kohsuke.github.GHCommit c, final String branchName ) {
        this();
        try {
            final GHAuthor author = (GHAuthor) c.getCommitShortInfo().getAuthor();
            final GHAuthor committer = (GHAuthor) c.getCommitShortInfo().getCommitter();

            this.author = GitUser.forUser( author );
            this.committer = GitUser.forUser( committer );
            this.commitMessage = c.getCommitShortInfo().getMessage();
            this.sha1 = c.getSHA1();

            final List<File> filesOnCommit = c.getFiles();

            setFiles( filesOnCommit );

            setLinesAdded( filesOnCommit.stream().map( file -> file.getLinesAdded() ).reduce( 0, ( a, b ) -> a + b ) );
            setLinesRemoved(
                    filesOnCommit.stream().map( file -> file.getLinesDeleted() ).reduce( 0, ( a, b ) -> a + b ) );

            setLinesChanged(
                    filesOnCommit.stream().map( file -> file.getLinesChanged() ).reduce( 0, ( a, b ) -> a + b ) );

            final List<File> filesModified = new ArrayList<File>( filesOnCommit );

            filesModified
                    .removeIf( e -> e.getLinesAdded() == 0 && e.getLinesDeleted() == 0 && e.getLinesChanged() == 0 );

            setFilesChanged( filesModified.size() );

        }
        catch ( final Exception e ) {
            throw new RuntimeException( e );
        }
        try {
            this.commitDate = c.getCommitShortInfo().getAuthor().getDate().toInstant();
        }
        catch ( final Exception e ) {
            System.err.println( "Could not retrieve date" );
        }
        try {
            this.isMergeCommit = 2 == c.getParents().size();
        }
        catch ( final Exception e ) {
            // first commit will have no parents, carry on
        }

        /* If not a merge commit, label the parents */
        try {
            if ( c.getParents().size() == 1 ) {
                this.parent = c.getParentSHA1s().get( 0 );
            }
        }
        catch ( final Exception e ) {
            // initial commit will have no parents, carry on
        }

        this.url = c.getHtmlUrl().toString();

        addBranch( branchName );
    }

    private void setFiles ( final List<File> filesOnCommit ) {
        if ( null == this.files ) {
            this.files = new HashSet<edu.ncsu.csc.autovcs.models.persistent.GHFile>();
        }
        filesOnCommit.forEach(
                file -> this.files.add( new edu.ncsu.csc.autovcs.models.persistent.GHFile( file, this ) ) );
    }

    public static List<GHCommit> getForUser ( final GitUser author ) {
        return (List<GHCommit>) getWhere( GHCommit.class, eqList( "author", author ) );
    }

    public static List<GHCommit> getByRepository ( final GHRepository repository ) {
        return (List<GHCommit>) getWhere( GHCommit.class, eqList( "repository", repository ) );
    }

    public static GHCommit getMostRecentByRepository ( final GHRepository repository ) {
        try {
            return (GHCommit) getWhere( GHCommit.class, eqList( "repository", repository ), "commitDate", false, 1 )
                    .get( 0 );
        }
        catch ( final IndexOutOfBoundsException ioobe ) {
            return null;
        }
    }

    // For Hibernate
    public GHCommit () {
        associatedBranches = new HashSet<String>();
    }

    public static List<GHCommit> getAll () {
        return (List<GHCommit>) getAll( GHCommit.class );
    }

    public GitUser getCommitter () {
        return committer;
    }

    public void setCommitter ( final GitUser committer ) {
        this.committer = committer;
    }

    public String getSha1 () {
        return sha1;
    }

    public void setSha1 ( final String sha1 ) {
        this.sha1 = sha1;
    }

    public void setId ( final Long id ) {
        this.id = id;
    }

    @Override
    public Long getId () {
        return id;
    }

    public GitUser getAuthor () {
        return author;
    }

    public void setAuthor ( final GitUser author ) {
        this.author = author;
    }

    public String getCommitMessage () {
        return commitMessage;
    }

    public void setCommitMessage ( final String commitMessage ) {
        this.commitMessage = commitMessage;
    }

    public Instant getCommitDate () {
        return commitDate;
    }

    public void setCommitDate ( final Instant commitDate ) {
        this.commitDate = commitDate;
    }

    public GHRepository getRepository () {
        return repository;
    }

    public void setRepository ( final GHRepository repository ) {
        this.repository = repository;
    }

    public void addBranch ( final String branch ) {
        if ( null != branch ) {
            this.associatedBranches.add( branch );
        }
    }

    public Set<String> getBranches () {
        return this.associatedBranches;
    }

    @Override
    public void save () {
        if ( null == author && null == committer ) {
            throw new RuntimeException( "Must have an author or committer present" );
        }

        super.save();
    }

    @Override
    public int hashCode () {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( sha1 == null ) ? 0 : sha1.hashCode() );
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
        final GHCommit other = (GHCommit) obj;
        if ( sha1 == null ) {
            if ( other.sha1 != null ) {
                return false;
            }
        }
        else if ( !sha1.equals( other.sha1 ) ) {
            return false;
        }
        return true;
    }

    @Override
    protected Serializable getKey () {
        return getSha1();
    }

    public boolean isMergeCommit () {
        return isMergeCommit;
    }

    public void setMergeCommit ( final boolean isMergeCommit ) {
        this.isMergeCommit = isMergeCommit;
    }

    public Integer getLinesAdded () {
        return linesAdded;
    }

    public void setLinesAdded ( final Integer linesAdded ) {
        this.linesAdded = linesAdded;
    }

    public Integer getLinesRemoved () {
        return linesRemoved;
    }

    public void setLinesRemoved ( final Integer linesRemoved ) {
        this.linesRemoved = linesRemoved;
    }

    public Integer getLinesChanged () {
        return linesChanged;
    }

    public void setLinesChanged ( final Integer linesChanged ) {
        this.linesChanged = linesChanged;
    }

    public Integer getFilesChanged () {
        return filesChanged;
    }

    public void setFilesChanged ( final Integer filesChanged ) {
        this.filesChanged = filesChanged;
    }

    public DisplayCommit format () {
        return new DisplayCommit( this );
    }

    public static Map<LocalDate, List<DisplayCommit>> format ( final List<GHCommit> commits ) {

        // Sort backwards, so most recent ones go at the top
        final TreeMap<LocalDate, List<DisplayCommit>> toReturn = new TreeMap<LocalDate, List<DisplayCommit>>(
                ( a, b ) -> b.compareTo( a ) );

        commits.forEach( commit -> {
            LocalDate dateNoTime = null;
            dateNoTime = LocalDate.ofInstant( commit.getCommitDate(), ZoneId.systemDefault() );

            if ( toReturn.containsKey( dateNoTime ) ) {
                toReturn.get( dateNoTime ).add( commit.format() );
            }
            else {
                final List<DisplayCommit> aDay = new ArrayList<DisplayCommit>();
                aDay.add( commit.format() );
                toReturn.put( dateNoTime, aDay );
            }
        } );

        return toReturn;
    }

    public class DisplayCommit {

        private final Instant date;

        private final Integer linesAdded;

        private final Integer linesRemoved;

        private final Integer linesChanged;

        private final String  commitMessage;

        private final String  url;

        private DisplayCommit ( final GHCommit commit ) {
            this.date = commit.commitDate;
            this.linesAdded = commit.linesAdded;
            this.linesRemoved = commit.linesRemoved;
            this.commitMessage = commit.commitMessage;
            this.url = commit.url;
            this.linesChanged = commit.linesChanged;
        }

        public Instant getDate () {
            return date;
        }

        public Integer getLinesAdded () {
            return linesAdded;
        }

        public Integer getLinesRemoved () {
            return linesRemoved;
        }

        public String getCommitMessage () {
            return commitMessage;
        }

        public String getUrl () {
            return url;
        }

        public Integer getLinesChanged () {
            return linesChanged;
        }

    }

    public String getParent () {
        return parent;
    }
}
