package edu.ncsu.csc.autovcs.utils;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

/**
 * A utility class for setting up the Hibernate SessionFactory
 *
 * @author Kai Presler-Marshall
 */
public class HibernateUtil {

    private static SessionFactory sessionFactory = buildSessionFactory();

    private static SessionFactory buildSessionFactory () {
        try {
            // Create the SessionFactory from hibernate.cfg.xml
            return new Configuration().configure().buildSessionFactory();
        }
        catch ( final Throwable ex ) {
            // Make sure you log the exception, as it might be swallowed
            System.err.println( "Initial SessionFactory creation failed." + ex );
            throw new ExceptionInInitializerError( ex );
        }
    }

    /**
     * Returns the Hibernate SessionFactory.
     *
     * @return session factory
     */
    public static SessionFactory getSessionFactory () {
        return sessionFactory;
    }

    public static Session openSession () {
        return getSessionFactory().openSession();
    }

    /** Shuts down the connection to the database. */
    public static void shutdown () {
        // Close caches and connection pools
        if ( sessionFactory != null ) {
            sessionFactory.close();
        }
    }

    public static Session getCurrentSession () {
        return getSessionFactory().getCurrentSession();
    }
}
