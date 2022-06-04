package edu.ncsu.csc.autovcs;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.jdbc.Work;

import edu.ncsu.csc.autovcs.models.persistent.DomainObjectCache;
import edu.ncsu.csc.autovcs.utils.HibernateUtil;

/**
 * Helper class for resetting the database between tests. Truncates all tables,
 * so RIP any data you had.
 *
 * This is a _horrendous_ hack, but will hold us off until I have time to
 * migrate to Spring Boot 2.x and a proper component-based architecture with
 * application profiles and transactions.
 *
 * @author Kai Presler-Marshall
 *
 */
public class DBUtils {

    static public void resetDB () {
        final Session session = HibernateUtil.getCurrentSession();

        final Transaction tx = session.beginTransaction();

        /*
         * credit for how we exploit the Session to get a DB connection:
         * https://stackoverflow.com/a/9482813
         */
        session.doWork( new Work() {
            @Override
            public void execute ( final Connection connection ) throws SQLException {
                final DatabaseMetaData metaData = connection.getMetaData();
                String dbName = metaData.getURL();

                /*
                 * DB URL looks something like
                 * `jdbc:mysql://localhost:3306/AutoVCS?
                 * createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true`,
                 * so it has to be pulled apart to get the actual name out
                 */
                dbName = dbName.split( "/" )[3].split( "\\?" )[0];

                final ResultSet tables = metaData.getTables( dbName, null, null, new String[] { "TABLE" } );

                try ( Statement st = connection.createStatement(); ) {
                    st.executeUpdate( "SET FOREIGN_KEY_CHECKS = 0" );

                    while ( tables.next() ) {
                        final String tableName = tables.getString( "TABLE_NAME" );

                        /*
                         * If you delete all entries from Hibernate's table it
                         * gets very unhappy
                         */
                        if ( "hibernate_sequence".equals( tableName ) ) {
                            continue;
                        }

                        st.executeUpdate( "TRUNCATE TABLE " + tableName );
                    }

                    st.executeUpdate( "SET FOREIGN_KEY_CHECKS = 1" );

                }

            }

        } );

        tx.commit();

        DomainObjectCache.getCaches().forEach( cache -> cache.clear() );
    }

}
