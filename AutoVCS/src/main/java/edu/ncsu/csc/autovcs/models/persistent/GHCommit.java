package edu.ncsu.csc.autovcs.models.persistent;

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
import javax.validation.constraints.NotNull;

import org.kohsuke.github.GHCommit.File;

@Entity
public class GHCommit extends DomainObject {

    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private Long              id;

    @ManyToOne ( cascade = { CascadeType.PERSIST, CascadeType.REFRESH } )
    private GitUser           author;

    @ManyToOne ( cascade = { CascadeType.PERSIST, CascadeType.REFRESH } )
    private GitUser           committer;

    @NotNull
    @Column ( columnDefinition = "text" )
    private String            commitMessage;

    @NotNull
    private String            sha1;

    @ManyToOne
    @NotNull
    private GHRepository      repository;

    @ElementCollection ( fetch = FetchType.EAGER )
    private final Set<String> associatedBranches;

    private Instant           commitDate;

    private boolean           isMergeCommit;

    private Integer           linesAdded;

    private Integer           linesRemoved;

    private Integer           linesChanged;

    private Integer           filesChanged;

    private String            url;

    private String            parent;

    @OneToMany ( mappedBy = "associatedCommit", cascade = CascadeType.ALL, fetch = FetchType.LAZY )
    private Set<GHFile>       files;

    public void setFiles ( final List<File> filesOnCommit ) {
        if ( null == this.files ) {
            this.files = new HashSet<edu.ncsu.csc.autovcs.models.persistent.GHFile>();
        }
        filesOnCommit
                .forEach( file -> this.files.add( new edu.ncsu.csc.autovcs.models.persistent.GHFile( file, this ) ) );
    }

    // For Hibernate
    public GHCommit () {
        associatedBranches = new HashSet<String>();
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

    public String getUrl () {
        return url;
    }

    public void setUrl ( final String url ) {
        this.url = url;
    }

    public Set<GHFile> getFiles () {
        return files;
    }

    public void setFiles ( final Set<GHFile> files ) {
        this.files = files;
    }

    public void setParent ( final String parent ) {
        this.parent = parent;
    }

    public DisplayCommit format () {
        return new DisplayCommit( this );
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
