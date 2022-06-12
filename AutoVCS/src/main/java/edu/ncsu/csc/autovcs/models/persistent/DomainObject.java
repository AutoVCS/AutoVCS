package edu.ncsu.csc.autovcs.models.persistent;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import edu.ncsu.csc.autovcs.utils.HibernateUtil;

/**
 * The common super-class for all database entities. This is done to centralize
 * the logic for database saves and retrieval. When using this class, you should
 * create an overridden getAll() method in the subclasses that passes in their
 * class type to the DomainObject.getAll(Class cls) method so that it can
 * perform the database retrieval.
 *
 * <p>
 * The DomainObject (this class) itself isn't persisted to the database, but it
 * is included here as it is the root class for all persistent classes.
 *
 * <p>
 * Where applicable (ie, on retrieval requests) we use the @Transactional (
 * readOnly = true ) annotation which has the potential to get better
 * performance out of the underlying database system. It is not _required_ but
 * is better to have it than not.
 *
 * @author Kai Presler-Marshall
 * @param <D>
 *            Subtype of DomainObject in question
 */
@SuppressWarnings ( { "unchecked", "rawtypes" } ) // generally a bad idea but
// Hibernate returns a List<?>
// that we want to cast
public abstract class DomainObject <D extends DomainObject<D>> {

    /**
     * Lots of DomainObjects are retrieved by ID. This way we get compile-time
     * errors of typos
     */
    protected static final String ID = "id";

    /**
     * Performs a getAll on the subtype of DomainObject in question. The
     * resulting list can then be streamed and filtered on any parameters
     * desired
     *
     * @param cls
     *            class to find DomainObjects for
     * @return A List of all records for the selected type.
     */
    protected static List< ? extends DomainObject> getAll ( final Class cls ) {
        List< ? extends DomainObject> results = null;
        final Session session = HibernateUtil.getCurrentSession();
        try {
            session.beginTransaction();
            results = session.createCriteria( cls ).setResultTransformer( Criteria.DISTINCT_ROOT_ENTITY ).list();
        }
        finally {
            try {
                session.getTransaction().commit();
                // session.close();
            }
            catch ( final Exception e ) {
                e.printStackTrace( System.out );
                // Continue
            }
        }

        return results;
    }

    /**
     * Method for retrieving a subset of the DomainObjects from the database.
     * Requires the sub-class of DomainObject to retrieve and a list of what
     * criteria to retrieve by. Think of this as SQL similar to: `WHERE
     * condition1=value1 AND condition2=value2`. If you require an OR clause,
     * that must be created as a single Criterion that is passed as an element
     * in this list; all of the Criterion provided are AND'ed together into the
     * clause that is executed.
     *
     * @param cls
     *            Subclass of DomainObject to retrieve
     * @param criteriaList
     *            List of Criterion to AND together and search by
     * @return The resulting list of elements found
     */
    protected static List< ? extends DomainObject> getWhere ( final Class cls, final List<Criterion> criteriaList ) {
        final Session session = HibernateUtil.getCurrentSession();

        List< ? extends DomainObject> results = null;
        try {
            session.beginTransaction();
            final Criteria c = session.createCriteria( cls );
            for ( final Criterion criterion : criteriaList ) {
                c.add( criterion );
            }
            results = c.setResultTransformer( Criteria.DISTINCT_ROOT_ENTITY ).list();
        }
        finally {
            try {
                session.getTransaction().commit();
                // session.close();
            }
            catch ( final Exception e ) {
                e.printStackTrace( System.out );
                // Continue
            }
        }

        return results;
    }

    protected static List< ? extends DomainObject> getWhere ( final Class cls, final List<Criterion> criteriaList,
            final String orderBy, final Boolean asc, final Integer limit ) {
        final Session session = HibernateUtil.getCurrentSession();

        List< ? extends DomainObject> results = null;
        try {
            session.beginTransaction();
            final Criteria c = session.createCriteria( cls );
            for ( final Criterion criterion : criteriaList ) {
                c.add( criterion );
            }

            c.addOrder( asc ? Order.asc( orderBy ) : Order.desc( orderBy ) );
            c.setMaxResults( limit );

            results = c.setResultTransformer( Criteria.DISTINCT_ROOT_ENTITY ).list();
        }
        finally {
            try {
                session.getTransaction().commit();
                // session.close();
            }
            catch ( final Exception e ) {
                e.printStackTrace( System.out );
                // Continue
            }
        }

        return results;
    }

    /**
     * Provides the ability to quickly delete all instances of the current
     * class. Useful for clearing out data for testing or regeneration.
     * Visibility is set to protected to force subclasses of DomainObject to
     * override this.
     *
     * @param cls
     *            class to delete instances of
     */
    public static void deleteAll ( final Class cls ) {
        final Session session = HibernateUtil.getCurrentSession();
        session.beginTransaction();
        final List<DomainObject> instances = session.createCriteria( cls ).list();
        for ( final DomainObject d : instances ) {
            session.delete( d );
        }
        session.getTransaction().commit();
        // session.close();
    }

    /**
     * Saves the DomainObject into the database. If the object instance does not
     * exist a new record will be created in the database. If the object already
     * exists in the DB, then the existing record will be updated.
     */
    public void save () {
        final Session session = HibernateUtil.getCurrentSession();
        session.beginTransaction();
        session.saveOrUpdate( this );
        session.getTransaction().commit();
        // session.close();

        getCache( this.getClass() ).put( getKey(), this );
    }

    public static void saveAll ( final List< ? extends DomainObject> objects ) {
        final Session session = HibernateUtil.getCurrentSession();
        session.beginTransaction();
        for ( final DomainObject object : objects ) {
            session.saveOrUpdate( object );
            // Not everything worth caching
            if ( null != getCache( object.getClass() ) ) {
                getCache( object.getClass() ).put( object.getKey(), object );
            }
        }
        session.getTransaction().commit();
        // session.close();
    }

    /**
     * Deletes the selected DomainObject from the database. This is operation
     * cannot be reversed.
     */
    public void delete () {
        final Session session = HibernateUtil.getCurrentSession();
        session.beginTransaction();
        session.delete( this );
        session.getTransaction().commit();
        // session.close();
    }

    /**
     * Retrieves a specific instance of the DomainObject subtype by its
     * persistent ID
     *
     * @param cls
     *            class to retrieve instance of by id
     * @param id
     *            id of object
     * @return object with given id
     */
    public static DomainObject getById ( final Class cls, final Object id ) {
        DomainObject obj;
        try {
            obj = (DomainObject) cls.getDeclaredConstructor().newInstance();
        }
        catch ( final Exception e ) {
            return null;
        }
        final Session session = HibernateUtil.getCurrentSession();
        session.beginTransaction();
        session.load( obj, (Serializable) id );
        session.getTransaction().commit();
        // session.close();
        return obj;
    }

    /**
     * Ability to get a DomainObject of any type by any field/value provided.
     * Will return null if the field is not valid for the DomainObject type or
     * no record exists with the value provided.
     *
     * @param cls
     *            class of DomainObject
     * @param field
     *            The field of the object (ie, name, id, etc) requested
     * @param value
     *            The value for the field in question
     * @return object associated with class and field
     */
    public static DomainObject getBy ( final Class cls, final String field, final String value ) {
        try {
            return getWhere( cls, eqList( field, value ) ).get( 0 );
        }
        catch ( final Exception e ) {
            return null;
        }
    }

    /**
     * Retrieves the ID of the DomainObject. May be a numeric ID assigned by the
     * database or another primary key that is user-assigned
     *
     * @return ID of the DomainObject.
     */
    public abstract Serializable getId ();

    /**
     * Retrieves the instance of the DomainObjectCache associated with the
     * subclass of DomainObject specified
     *
     * @param cls
     *            The subclass of DomainObject to retrieve a cache for
     * @return The cache found, or null if it does not exist.
     */
    private static DomainObjectCache getCache ( final Class cls ) {
        return DomainObjectCache.getCacheByClass( cls );
    }

    protected abstract Serializable getKey ();

    /*
     * All of these are useful for creating the Criterion used to retrieve
     * DomainObjects from the database.
     */

    /**
     * Wrap a single Criterion into a SingletonList to pass it to a getWhere()
     * method
     *
     * @param c
     *            The Criterion to wrap
     * @return The List that results
     */
    protected static List<Criterion> createCriterionList ( final Criterion c ) {
        return Collections.singletonList( c );
    }

    /**
     * Creates a Criterion and wraps it in a SingletonList. Works like
     * {@link #createCriterion}, but the result comes out as a List to pass to
     * getWhere directly.
     *
     * @param field
     *            The field to create a restriction on
     * @param value
     *            The value to compare against
     * @return The List that results from creating a Criterion from these values
     *         and wrapping it
     */
    protected static List<Criterion> eqList ( final String field, final Object value ) {
        return createCriterionList( eq( field, value ) );
    }

    /**
     * Create an equals-relation Criterion between the field and the value
     * provided. This is used to retrieve DomainObjects from the database. Note
     * that the value provided must equal (value _and_ type) of the records that
     * are being retrieved. For example, to retrieve an OfficeVisit by the
     * Patient, you must pass in their User object, not their _username_ even
     * though the username is what goes in the table in the database.
     *
     * @param field
     *            The field to create a restriction on
     * @param value
     *            The value to compare against
     * @return The Criterion to create from these values
     */
    protected static Criterion eq ( final String field, final Object value ) {
        return Restrictions.eq( field, value );
    }

    /**
     * Creates a between-relation Criterion between the field and two values
     * provided.
     *
     * @param field
     *            Field to check equivalence on
     * @param lbound
     *            Lower bound for the range
     * @param ubound
     *            Upper bound for the range
     * @return Criterion created
     */
    protected static Criterion bt ( final String field, final Object lbound, final Object ubound ) {
        return Restrictions.between( field, lbound, ubound );
    }
}
