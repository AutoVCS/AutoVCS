package edu.ncsu.csc.autovcs.models.persistent;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

@Entity
public class GHFile extends DomainObject {

    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private Long     id;

    private String   filename;

    private int      linesAdded;

    private int      linesDeleted;

    private int      linesChanged;

    @Column ( columnDefinition = "LONGTEXT" )
    private String   url;

    @Column ( columnDefinition = "LONGTEXT" )
    private String   changes;

    @ManyToOne
    @NotNull
    private GHCommit associatedCommit;

    public GHFile ( final org.kohsuke.github.GHCommit.File file, final GHCommit commit ) {

        this.associatedCommit = commit;

        this.filename = file.getFileName();

        this.linesAdded = file.getLinesAdded();

        this.linesDeleted = file.getLinesDeleted();

        this.linesChanged = file.getLinesChanged();
        
        /* Null check in case of submodules or other files with no convenient blob */
        this.url = null == file.getBlobUrl() ? null : file.getBlobUrl().toString();

        this.changes = file.getPatch();
    }

    /** For Hibernate */
    public GHFile () {
    }

    public String getFilename () {
        return filename;
    }

    public int getLinesAdded () {
        return linesAdded;
    }

    public int getLinesDeleted () {
        return linesDeleted;
    }

    public int getLinesChanged () {
        return linesChanged;
    }

    public String getUrl () {
        return url;
    }

    public String getChanges () {
        return changes;
    }

    public GHCommit getAssociatedCommit () {
        return associatedCommit;
    }

    @Override
    public Serializable getId () {
        return id;
    }

}
