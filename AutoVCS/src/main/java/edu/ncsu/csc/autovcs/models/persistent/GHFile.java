package edu.ncsu.csc.autovcs.models.persistent;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

@Entity
@Table ( name = "GHFiles" )
@SuppressWarnings ( "unchecked" )
public class GHFile extends DomainObject<GHFile> {

    private static DomainObjectCache<String, GHFile> cache = new DomainObjectCache<String, GHFile>( GHFile.class );

    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private Long                                     id;

    private String                                   filename;

    private int                                      linesAdded;

    private int                                      linesDeleted;

    private int                                      linesChanged;

    private String                                   url;

    @Column ( columnDefinition = "LONGTEXT" )
    private String                                   changes;

    @ManyToOne
    @NotNull
    private GHCommit                                 associatedCommit;

    public GHFile ( final org.kohsuke.github.GHCommit.File file, final GHCommit commit ) {

        this.associatedCommit = commit;

        this.filename = file.getFileName();

        this.linesAdded = file.getLinesAdded();

        this.linesDeleted = file.getLinesDeleted();

        this.linesChanged = file.getLinesChanged();

        this.url = file.getBlobUrl().toString();

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

    @Override
    protected Serializable getKey () {
        return associatedCommit.getSha1() + filename;
    }

    public static List<GHFile> getByCommit ( final GHCommit commit ) {
        return (List<GHFile>) getWhere( GHFile.class, eqList( "associatedCommit", commit ) );
    }
}
