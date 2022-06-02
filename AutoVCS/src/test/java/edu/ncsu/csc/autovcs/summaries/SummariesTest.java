package edu.ncsu.csc.autovcs.summaries;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import ch.uzh.ifi.seal.changedistiller.api.ChangeSummariesList;
import ch.uzh.ifi.seal.changedistiller.api.ChangeSummary;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.java.JavaEntityType;
import ch.uzh.ifi.seal.changedistiller.model.entities.Insert;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeEntity;
import ch.uzh.ifi.seal.changedistiller.model.entities.StructureEntityVersion;

public class SummariesTest {

    @Test
    public void testSummariesCanBeComputed () {

        final List<SourceCodeChange> changes = new ArrayList<SourceCodeChange>();
        /* 100 points for class */
        final SourceCodeChange newClass = generateSCC( Insert.class, "SampleClass", JavaEntityType.CLASS );
        changes.add( newClass );
        /* 25 points for method */
        final SourceCodeChange newMethod = generateSCC( Insert.class, "myMethod", JavaEntityType.METHOD );
        changes.add( newMethod );
        /* 10 points for field */
        final SourceCodeChange newField = generateSCC( Insert.class, "myField", JavaEntityType.FIELD );
        changes.add( newField );

        ChangeSummary cs = new ChangeSummary( "SampleClass.java", changes );

        Integer score = cs.getScore();

        Assert.assertEquals( "Score should be computed for the elements added", 135, (int) score );

        /* 3 points for annotation, which is marked as "other" */
        final SourceCodeChange newAnnotation = generateSCC( Insert.class, "@Ignore", JavaEntityType.ANNOTATION );

        /* 1 point for JavaDoc, marked as "comment" */
        final SourceCodeChange newComment = generateSCC( Insert.class, "JavaDoc comment", JavaEntityType.JAVADOC );

        changes.add( newAnnotation );
        changes.add( newComment );

        cs = new ChangeSummary( "SampleClass.java", changes );

        score = cs.getScore();

        Assert.assertEquals( "Score should be computed for the elements added", 139, (int) score );

    }

    @Test
    public void testChangesForTwoFiles () {

        final List<SourceCodeChange> changes = new ArrayList<SourceCodeChange>();
        /* 100 points for class */
        final SourceCodeChange newClass = generateSCC( Insert.class, "SampleClass", JavaEntityType.CLASS );
        changes.add( newClass );
        /* 25 points for method */
        final SourceCodeChange newMethod = generateSCC( Insert.class, "myMethod", JavaEntityType.METHOD );
        changes.add( newMethod );
        /* 10 points for field */
        final SourceCodeChange newField = generateSCC( Insert.class, "myField", JavaEntityType.FIELD );
        changes.add( newField );

        final ChangeSummary sampleClass = new ChangeSummary( "SampleClass.java", changes );

        Assert.assertEquals( "Score for the first class should be sum of its components", 135,
                (int) sampleClass.getScore() );

        final List<SourceCodeChange> classTwoChanges = new ArrayList<SourceCodeChange>();

        /* 100 points for class */
        final SourceCodeChange secondNewClass = generateSCC( Insert.class, "SecondSampleClass", JavaEntityType.CLASS );

        /* 3 points for annotation, which is marked as "other" */
        final SourceCodeChange newAnnotation = generateSCC( Insert.class, "@Ignore", JavaEntityType.ANNOTATION );

        /* 1 point for JavaDoc, marked as "comment" */
        final SourceCodeChange newComment = generateSCC( Insert.class, "JavaDoc comment", JavaEntityType.JAVADOC );

        classTwoChanges.add( secondNewClass );
        classTwoChanges.add( newAnnotation );
        classTwoChanges.add( newComment );

        final ChangeSummary sampleClassTwo = new ChangeSummary( "SecondSampleClass.java", classTwoChanges );
        Assert.assertEquals( "Score for the second class should be sum of its components", 104,
                (int) sampleClassTwo.getScore() );

        final ChangeSummariesList csl = new ChangeSummariesList( List.of( sampleClass, sampleClassTwo ) );

        Assert.assertEquals( "Score for both classes should be sum of their components", 239,
                (int) csl.getContributionsScore() );

    }

    private SourceCodeChange generateSCC ( final Class< ? extends SourceCodeChange> typeOfChange, final String name,
            final JavaEntityType entityChanged ) {
        final SourceCodeEntity sce = new SourceCodeEntity( name, entityChanged, null );

        try {
            return typeOfChange.getDeclaredConstructor( StructureEntityVersion.class, SourceCodeEntity.class,
                    SourceCodeEntity.class ).newInstance( null, sce, null );
        }
        catch ( InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | SecurityException | NoSuchMethodException e ) {
            System.err.println( "You done goofed" );
            e.printStackTrace( System.err );
            Assert.fail();

        }
        return null;

    }

}
