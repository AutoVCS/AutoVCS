package edu.ncsu.csc.autovcs.summaries;

import org.junit.Assert;
import org.junit.Test;

import ch.uzh.ifi.seal.changedistiller.api.ChangeExtractor;
import ch.uzh.ifi.seal.changedistiller.api.ChangeSummary;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.java.JavaEntityType;
import ch.uzh.ifi.seal.changedistiller.model.entities.Update;

/**
 * A set of tests to verify that the content we expect to see excluded from
 * differences is excluded, but that other analyses work as expected
 *
 * @author Kai Presler-Marshall
 *
 */
public class FileDifferencingTest {

    static private final String BASE_PATH = "test-files/FileDifferencingTest/";

    /**
     * HashCode methods should always be excluded from the differences produced
     */
    @Test
    public void testHashCodeExcluded () {

        final String oldFile = BASE_PATH + "hashcode/AutoVCSSampleClass.java-old";

        final String newFile = BASE_PATH + "hashcode/AutoVCSSampleClass.java-new";

        final ChangeSummary cs = ChangeExtractor.extractChanges( oldFile, newFile );

        Assert.assertEquals( "A new HashCode method should be worth 0 points", 0, (int) cs.getScore() );

        Assert.assertEquals( "A new HashCode method should not show up in the list of changes", 0,
                cs.getAllChanges().size() );

    }

    /**
     * Equals methods should always be excluded from the differences produced
     */
    @Test
    public void testEqualsExcluded () {

        final String oldFile = BASE_PATH + "equals/AutoVCSSampleClass.java-old";

        final String newFile = BASE_PATH + "equals/AutoVCSSampleClass.java-new";

        final ChangeSummary cs = ChangeExtractor.extractChanges( oldFile, newFile );

        Assert.assertEquals( "A new Equals method should be worth 0 points", 0, (int) cs.getScore() );

        Assert.assertEquals( "A new Equals method should not show up in the list of changes", 0,
                cs.getAllChanges().size() );
    }

    /**
     * Getter methods that just set a field should be excluded
     */
    @Test
    public void testGettersExcluded () {

        final String oldFile = BASE_PATH + "getters/AutoVCSSampleClass.java-old";

        final String newFile = BASE_PATH + "getters/AutoVCSSampleClass.java-new";

        ChangeSummary cs = ChangeExtractor.extractChanges( oldFile, newFile );

        Assert.assertEquals( "A new get* method should be worth 0 points", 0, (int) cs.getScore() );

        Assert.assertEquals( "A new get* method should not show up in the list of changes", 0,
                cs.getAllChanges().size() );

        /*
         * Try again with the `this.field` convention to make sure results are
         * the same
         */

        final String oldFile2 = BASE_PATH + "getters/AutoVCSSampleClass2.java-old";

        final String newFile2 = BASE_PATH + "getters/AutoVCSSampleClass2.java-new";

        cs = ChangeExtractor.extractChanges( oldFile2, newFile2 );

        Assert.assertEquals( "A new get* method should be worth 0 points", 0, (int) cs.getScore() );

        Assert.assertEquals( "A new get* method should not show up in the list of changes", 0,
                cs.getAllChanges().size() );

    }

    /**
     * Setter methods that just return a field should be excluded
     */
    @Test
    public void testSettersExcluded () {

        final String oldFile = BASE_PATH + "setters/AutoVCSSampleClass.java-old";

        final String newFile = BASE_PATH + "setters/AutoVCSSampleClass.java-new";

        ChangeSummary cs = ChangeExtractor.extractChanges( oldFile, newFile );

        Assert.assertEquals( "A new set* method should be worth 0 points", 0, (int) cs.getScore() );

        Assert.assertEquals( "A new set* method should not show up in the list of changes", 0,
                cs.getAllChanges().size() );

        /*
         * Try again with the `this.field` convention to make sure results are
         * the same
         */

        final String oldFile2 = BASE_PATH + "setters/AutoVCSSampleClass2.java-old";

        final String newFile2 = BASE_PATH + "setters/AutoVCSSampleClass2.java-new";

        cs = ChangeExtractor.extractChanges( oldFile2, newFile2 );

        Assert.assertEquals( "A new set* method should be worth 0 points", 0, (int) cs.getScore() );

        Assert.assertEquals( "A new set* method should not show up in the list of changes", 0,
                cs.getAllChanges().size() );

    }

    /**
     * Getter methods with any logic in them should be included
     */
    @Test
    public void testGettersNotExcludedWhenContent () {

        final String oldFile = BASE_PATH + "gettersContent/AutoVCSSampleClass.java-old";

        final String newFile = BASE_PATH + "gettersContent/AutoVCSSampleClass.java-new";

        final ChangeSummary cs = ChangeExtractor.extractChanges( oldFile, newFile );

        Assert.assertEquals( "A new get* method with content should be worth >0 points", 34, (int) cs.getScore() );

        Assert.assertEquals( "A new get* method with content should cause a list of changes", 4,
                cs.getAllChanges().size() );

        /**
         * Expect to see four elements:
         *
         * <pre>
         * [0] Method invocation for String.format
         * [1] Variable declaration for new variable
         * [2] Return statement
         * [3] New method
         * </pre>
         *
         */

        Assert.assertEquals( JavaEntityType.METHOD_INVOCATION,
                cs.getAllChanges().get( 0 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.VARIABLE_DECLARATION_STATEMENT,
                cs.getAllChanges().get( 1 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.RETURN_STATEMENT,
                cs.getAllChanges().get( 2 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.METHOD, cs.getAllChanges().get( 3 ).getChangedEntity().getType() );

    }

    /**
     * Setter methods with any logic in them should be included
     */
    @Test
    public void testSettersNotExcludedWhenContent () {

        final String oldFile = BASE_PATH + "settersContent/AutoVCSSampleClass.java-old";

        final String newFile = BASE_PATH + "settersContent/AutoVCSSampleClass.java-new";

        final ChangeSummary cs = ChangeExtractor.extractChanges( oldFile, newFile );

        Assert.assertEquals( "A new set* method with content should be worth >0 points", 37, (int) cs.getScore() );

        Assert.assertEquals( "A new set* method with content should cause a list of changes", 5,
                cs.getAllChanges().size() );

        /**
         * Expect to see five elements:
         *
         * <pre>
         * [0] Throws statement
         * [1] Then block of if statement
         * [2] If statement
         * [3] Assignment
         * [4] Method declaration
         * </pre>
         *
         */

        Assert.assertEquals( JavaEntityType.THROW_STATEMENT, cs.getAllChanges().get( 0 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.THEN_STATEMENT, cs.getAllChanges().get( 1 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.IF_STATEMENT, cs.getAllChanges().get( 2 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.ASSIGNMENT, cs.getAllChanges().get( 3 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.METHOD, cs.getAllChanges().get( 4 ).getChangedEntity().getType() );

    }

    /**
     * Content added to a class should be discovered
     */
    @Test
    public void testContentInClassesIncluded () {

        final String oldFile = BASE_PATH + "classes/AutoVCSSampleClass.java-old";

        final String newFile = BASE_PATH + "classes/AutoVCSSampleClass.java-new";

        final ChangeSummary cs = ChangeExtractor.extractChanges( oldFile, newFile );

        Assert.assertEquals( "Content in a new class should be found", 44, (int) cs.getScore() );

        /**
         * <pre>
         * [0] int variable declaration
         * [1] string variable declaration
         * [2] return statement
         * [3] doSomething method declaration
         * [4] field outside of method declaration
         * </pre>
         */

        Assert.assertEquals( JavaEntityType.VARIABLE_DECLARATION_STATEMENT,
                cs.getAllChanges().get( 0 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.VARIABLE_DECLARATION_STATEMENT,
                cs.getAllChanges().get( 1 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.RETURN_STATEMENT,
                cs.getAllChanges().get( 2 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.METHOD, cs.getAllChanges().get( 3 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.FIELD, cs.getAllChanges().get( 4 ).getChangedEntity().getType() );

    }

    /**
     * Adding an inner class should produce differences including all of their
     * content
     */
    @Test
    public void testContentInInnerClassesIncluded () {

        final String oldFile = BASE_PATH + "innerClasses/AutoVCSSampleClass.java-old";

        final String newFile = BASE_PATH + "innerClasses/AutoVCSSampleClass.java-new";

        final ChangeSummary cs = ChangeExtractor.extractChanges( oldFile, newFile );

        Assert.assertEquals( "Content in a new class should be found", 192, (int) cs.getScore() );

        /**
         * <pre>
         *
         * [0] `super()` call in constructor in inner class
         * [1] method call for variable declaration in inner class
         * [2] variable declaration for inner class
         * [3] return statement for inner class
         * [4] null constructor for inner class
         * [5] method in inner class
         * [6] field1 in inner class
         * [7] field2 in inner class
         * [8] new field in outer class
         * [9] inner class class definition
         * </pre>
         */

        Assert.assertEquals( JavaEntityType.CONSTRUCTOR_INVOCATION,
                cs.getAllChanges().get( 0 ).getChangedEntity().getType() );
        Assert.assertEquals( "super();", cs.getAllChanges().get( 0 ).getChangedEntity().getUniqueName() );

        Assert.assertEquals( JavaEntityType.METHOD_INVOCATION,
                cs.getAllChanges().get( 1 ).getChangedEntity().getType() );
        Assert.assertEquals( "String.format(\"%s,%s\", innerClassField1, innerClassField2);",
                cs.getAllChanges().get( 1 ).getChangedEntity().getUniqueName() );

        Assert.assertEquals( JavaEntityType.VARIABLE_DECLARATION_STATEMENT,
                cs.getAllChanges().get( 2 ).getChangedEntity().getType() );
        Assert.assertEquals( "Object result = String.format(\"%s,%s\", innerClassField1, innerClassField2);",
                cs.getAllChanges().get( 2 ).getChangedEntity().getUniqueName() );

        Assert.assertEquals( JavaEntityType.RETURN_STATEMENT,
                cs.getAllChanges().get( 3 ).getChangedEntity().getType() );
        Assert.assertEquals( "return result;", cs.getAllChanges().get( 3 ).getChangedEntity().getUniqueName() );

        Assert.assertEquals( JavaEntityType.METHOD, cs.getAllChanges().get( 4 ).getChangedEntity().getType() );
        Assert.assertEquals( "AutoVCSSampleClass.InnerClass.InnerClass()",
                cs.getAllChanges().get( 4 ).getChangedEntity().getUniqueName() );

        Assert.assertEquals( JavaEntityType.METHOD, cs.getAllChanges().get( 5 ).getChangedEntity().getType() );
        Assert.assertEquals( "AutoVCSSampleClass.InnerClass.InnerClassMethod()",
                cs.getAllChanges().get( 5 ).getChangedEntity().getUniqueName() );

        Assert.assertEquals( JavaEntityType.FIELD, cs.getAllChanges().get( 6 ).getChangedEntity().getType() );
        Assert.assertEquals( "AutoVCSSampleClass.InnerClass.innerClassField1 : Object",
                cs.getAllChanges().get( 6 ).getChangedEntity().getUniqueName() );

        Assert.assertEquals( JavaEntityType.FIELD, cs.getAllChanges().get( 7 ).getChangedEntity().getType() );
        Assert.assertEquals( "AutoVCSSampleClass.InnerClass.innerClassField2 : Boolean",
                cs.getAllChanges().get( 7 ).getChangedEntity().getUniqueName() );

        Assert.assertEquals( JavaEntityType.FIELD, cs.getAllChanges().get( 8 ).getChangedEntity().getType() );
        Assert.assertEquals( "AutoVCSSampleClass.anInteger : Integer",
                cs.getAllChanges().get( 8 ).getChangedEntity().getUniqueName() );

        Assert.assertEquals( JavaEntityType.CLASS, cs.getAllChanges().get( 9 ).getChangedEntity().getType() );
        Assert.assertEquals( "AutoVCSSampleClass.InnerClass",
                cs.getAllChanges().get( 9 ).getChangedEntity().getUniqueName() );

    }

    /**
     * Adding a new method should produce differences including all of their
     * content
     */
    @Test
    public void testContentInMethodsIncluded () {

        final String oldFile = BASE_PATH + "methods/AutoVCSSampleClass.java-old";

        final String newFile = BASE_PATH + "methods/AutoVCSSampleClass.java-new";

        final ChangeSummary cs = ChangeExtractor.extractChanges( oldFile, newFile );

        Assert.assertEquals( "Content in a new method should be found", 37, (int) cs.getScore() );

        /**
         * <pre>
         * [0] print statement in new method
         * [1] variable declaration in new method
         * [2] return statement in new method
         * [3] new method itself
         * [4] updated old method
         *
         * </pre>
         */

        Assert.assertEquals( JavaEntityType.METHOD_INVOCATION,
                cs.getAllChanges().get( 0 ).getChangedEntity().getType() );
        Assert.assertEquals( "System.out.println(\"\");",
                cs.getAllChanges().get( 0 ).getChangedEntity().getUniqueName() );

        Assert.assertEquals( JavaEntityType.VARIABLE_DECLARATION_STATEMENT,
                cs.getAllChanges().get( 1 ).getChangedEntity().getType() );
        Assert.assertEquals( "int x = (a + 50);", cs.getAllChanges().get( 1 ).getChangedEntity().getUniqueName() );

        Assert.assertEquals( JavaEntityType.RETURN_STATEMENT,
                cs.getAllChanges().get( 2 ).getChangedEntity().getType() );
        Assert.assertEquals( "return \"\";", cs.getAllChanges().get( 2 ).getChangedEntity().getUniqueName() );

        Assert.assertEquals( JavaEntityType.METHOD, cs.getAllChanges().get( 3 ).getChangedEntity().getType() );
        Assert.assertEquals( "AutoVCSSampleClass.myStringMethod()",
                cs.getAllChanges().get( 3 ).getChangedEntity().getUniqueName() );

        /* Make sure we have an Update and not an Insert */
        Assert.assertTrue( cs.getAllChanges().get( 4 ) instanceof Update );
        Assert.assertEquals( JavaEntityType.RETURN_STATEMENT,
                cs.getAllChanges().get( 4 ).getChangedEntity().getType() );
        Assert.assertEquals( "return 4;", cs.getAllChanges().get( 4 ).getChangedEntity().getUniqueName() );

    }

    @Test
    public void testEmptyFileIsHandled () {

        /*
         * this one doesn't exist. When it is detected that it doesn't, it
         * should be created automatically to support comparison
         */
        final String oldFile = BASE_PATH + "classes/AutoVCSSampleClass.java";

        final String newFile = BASE_PATH + "classes/AutoVCSSampleClass.java-new";

        final ChangeSummary cs = ChangeExtractor.extractChanges( oldFile, newFile );

        Assert.assertEquals( "Content in a new class should be found", 144, (int) cs.getScore() );

        /**
         * <pre>
         * [0] int variable declaration
         * [1] string variable declaration
         * [2] return statement
         * [3] doSomething method declaration
         * [4] field outside of method declaration
         * </pre>
         */

        Assert.assertEquals( JavaEntityType.VARIABLE_DECLARATION_STATEMENT,
                cs.getAllChanges().get( 0 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.VARIABLE_DECLARATION_STATEMENT,
                cs.getAllChanges().get( 1 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.RETURN_STATEMENT,
                cs.getAllChanges().get( 2 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.METHOD, cs.getAllChanges().get( 3 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.FIELD, cs.getAllChanges().get( 4 ).getChangedEntity().getType() );
        Assert.assertEquals( JavaEntityType.CLASS, cs.getAllChanges().get( 5 ).getChangedEntity().getType() );

    }

}
