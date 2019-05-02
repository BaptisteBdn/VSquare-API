package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.model.*;
import fr.eseo.vsquare.model.User.UserType;
import fr.klemek.betterlists.BetterArrayList;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import javax.persistence.NoResultException;
import javax.persistence.Query;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.logging.Level;

/**
 * Utility class that store useful database functions.
 *
 * @author Clement Gouin
 */
public final class DatabaseManager {

	private static String defaultConnectionString = null;
	private static SessionFactory sessionFactory;
	private static final String DB_USER = "db_user";

	private static boolean databaseUpToDate = false;

	private DatabaseManager() {
	}

	/**
	 * Init the driver, default connection string and update database.
	 * 
	 * @param defaultConnectionString
	 *            the connectionString to set as default
	 * @return true if the operation is successful
	 */
	public static boolean init(String defaultConnectionString) {
		Logger.log(Level.INFO, "Initializing database...");
		DatabaseManager.checkDriver(false);
		DatabaseManager.setDefaultConnectionString(defaultConnectionString);
		try (Connection conn = openConnection()) {
			Logger.log(Level.INFO, "Connection successful with DB user : {0}", Utils.getString(DB_USER));
		} catch (SQLException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
			return false;
		}
		try (Connection conn = openConnection(true)) {
			Logger.log(Level.INFO, "Connection successful with DB super user : {0}", Utils.getString("db_super_user"));
		} catch (SQLException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
			return false;
		}
		if (sessionFactory == null)
			DatabaseManager.buildSessionFactory(defaultConnectionString);
		return DatabaseManager.updateDatabase();
	}

	/**
	 * Change the connection string used by default in functions.
	 * 
	 * @param defaultConnectionString
	 *            the connectionString to set as default
	 */
	public static void setDefaultConnectionString(String defaultConnectionString) {
		DatabaseManager.defaultConnectionString = defaultConnectionString;
	}

	/**
     * Register or unregister the mysql jdbc driver.
     * @param unregister change mode
	 */
    public static void checkDriver(boolean unregister) {
		try {
			Enumeration<Driver> loadedDrivers = DriverManager.getDrivers();
			while (loadedDrivers.hasMoreElements()) {
				Driver driver = loadedDrivers.nextElement();
				if (driver instanceof com.mysql.cj.jdbc.Driver) {
					Logger.log(Level.INFO, "Driver registered");
                    if (unregister) {
						DriverManager.deregisterDriver(driver);
					}
					return;
				}
			}
			Logger.log(Level.INFO, "Driver not registered");
            if (!unregister) {
				DriverManager.registerDriver(new com.mysql.cj.jdbc.Driver());
			}
		} catch (SQLException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
		}

	}

	/**
	 * Open a new connection to the default database.
	 * 
	 * @return a Connection object to make queries
	 * @throws SQLException
	 *             if cannot open connection
	 */
	public static Connection openConnection() throws SQLException {
		return openConnection(false);
	}

	/**
	 * Open a new connection to the default database.
	 * 
	 * @param superuser
	 *            if the user can modify the structure of the database
	 * @return a Connection object to make queries
	 * @throws SQLException
	 *             if cannot open connection
	 */
	public static Connection openConnection(boolean superuser) throws SQLException {
		return openConnection(superuser, null);
	}

	/**
	 * Open a new connection to the database.
	 * 
	 * @param superuser
	 *            if the user can modify the structure of the database
	 * @param connectionString
	 *            the desired database connection string (null for default)
	 * @return a Connection object to make queries
	 * @throws SQLException
	 *             if cannot open connection
	 */
    private static Connection openConnection(boolean superuser, String connectionString) throws SQLException {
		if (connectionString == null && defaultConnectionString == null)
			throw new ExceptionInInitializerError("Default ConnectionString is null");
		String userName = Utils.getString(superuser ? "db_super_user" : DB_USER);
		String password = Utils.getString(superuser ? "db_super_password" : "db_password");
		String url = connectionString == null ? defaultConnectionString : connectionString;
		return DriverManager.getConnection(url, userName, password);
	}

	/**
	 * Get an hibernate session factory for database manipulation.
	 * 
	 * @return the hibernate session factory
	 */
	public static SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	public static boolean isInitialized() {
		return databaseUpToDate && isHibernateInitialized();
	}

	public static boolean isHibernateInitialized() {
		return sessionFactory != null;
	}

	/**
	 * Initiate the singleton sessionFactory.
	 * 
	 * @param connectionString
	 *            the desired connectionString (null for default)
	 */
    private static void buildSessionFactory(String connectionString) {
		try {
			Logger.log(Level.INFO, "Creating SessionFactory...");
			Level lvl = Logger.getLevel();
			Logger.setLevel(Level.SEVERE);
			sessionFactory = new Configuration().configure()
					.setProperty("hibernate.connection.url",
							connectionString == null ? defaultConnectionString : connectionString)
					.setProperty("hibernate.connection.username", Utils.getString(DB_USER))
					.setProperty("hibernate.connection.password", Utils.getString("db_password"))
					.addAnnotatedClass(VSquareObject.class).addAnnotatedClass(Group.class).addAnnotatedClass(User.class)
					.addAnnotatedClass(Token.class).addAnnotatedClass(Vm.class).addAnnotatedClass(ErrorLog.class)
                    .addAnnotatedClass(EventLog.class).addAnnotatedClass(Permission.class).addAnnotatedClass(DownloadLink.class)
                    .addAnnotatedClass(Network.class).addAnnotatedClass(Snapshot.class).buildSessionFactory();
			Logger.setLevel(lvl);
			Logger.log(Level.INFO, "SessionFactory created");
		} catch (Exception ex) {
			Logger.log(Level.SEVERE, "Initial SessionFactory creation failed : {0}", ex);
			throw new ExceptionInInitializerError(ex);
		}
	}

	/**
	 * Execute an hibernate query and returns the first element.
	 * 
	 * @param <T>
	 *            the type of the hibernate object
	 * @param hibernateQuery
     *            with unnamed parameters (ex : '?1')
	 * @param parameters
     *            the unnamed parameters in order
	 * @return the first object or null if not found
	 */
	public static <T> T getFirstFromSessionQuery(String hibernateQuery, Object... parameters) {
		return getFirstFromSessionQueryBase(hibernateQuery, null, parameters);
	}

	/**
	 * Execute an hibernate query and returns the first element.
	 * 
	 * @param <T>
	 *            the type of the hibernate object
	 * @param hibernateQuery
	 *            with named parameters (ex : ':id')
	 * @param parameters
	 *            named parameters and their values
	 * @return the first object or null if not found
	 */
	public static <T> T getFirstFromSessionQueryNamed(String hibernateQuery, Map<String, Object> parameters) {
		return getFirstFromSessionQueryBase(hibernateQuery, parameters);
	}

	/**
	 * Execute an hibernate query and returns the first element.
	 * 
	 * @param <T>
	 *            the type of the hibernate object
	 * @param hibernateQuery
	 *            the hibernate query
	 * @param parametersMap
	 *            named parameters and their values (null for named parameters)
	 * @param parameters
     *            the unnamed parameters in order
	 * @return the first object or null if not found
	 */
	private static <T> T getFirstFromSessionQueryBase(String hibernateQuery, Map<String, Object> parametersMap,
			Object... parameters) {
		if (!isHibernateInitialized()) {
			Logger.log(Level.SEVERE, "Database not initialized, cannot do query");
			return null;
		}
		Session session = getSessionFactory().getCurrentSession();
		Transaction tx = null;
		try {
			tx = session.beginTransaction();
			Query query = session.createQuery(hibernateQuery);
			loadQueryParameters(query, parametersMap, parameters);
			@SuppressWarnings("unchecked")
			T result = (T) query.getSingleResult();
			tx.commit();
			return result;
		} catch (NoResultException e) {
			if (tx != null)
				tx.commit();
			return null;
		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			Logger.log(Level.WARNING, e.toString(), e);
			return null;
		}
	}

	/**
	 * Execute an hibernate query and returns all the rows.
	 * 
	 * @param <T>
	 *            the type of the hibernate object
	 * @param hibernateQuery
     *            with unnamed parameters (ex : '?1')
	 * @param parameters
     *            the unnamed parameters in order
	 * @return all the returned rows
	 */
	public static <T> List<T> getRowsFromSessionQuery(String hibernateQuery, Object... parameters) {
		return getRowsFromSessionQueryBase(hibernateQuery, 0, 0, null, parameters);
	}

	/**
	 * Execute an hibernate query and returns all the rows.
	 * 
	 * @param <T>
	 *            the type of the hibernate object
	 * @param hibernateQuery
	 *            with named parameters (ex : ':id')
	 * @param start
	 *            the first result
	 * @param max
	 *            the max size of the query
	 * @param parameters
	 *            named parameters and their values
	 * @return all the returned rows
	 */
	public static <T> List<T> getRowsFromSessionQueryNamed(String hibernateQuery, int start, int max,
			Map<String, Object> parameters) {
		return getRowsFromSessionQueryBase(hibernateQuery, start, max, parameters);
	}

	/**
	 * Execute an hibernate query and returns all the rows.
	 * 
	 * @param <T>
	 *            the type of the hibernate object
	 * @param hibernateQuery
	 *            with named parameters (ex : ':id')
	 * @param parameters
	 *            named parameters and their values
	 * @return all the returned rows
	 */
	public static <T> List<T> getRowsFromSessionQueryNamed(String hibernateQuery, Map<String, Object> parameters) {
		return getRowsFromSessionQueryBase(hibernateQuery, 0, 0, parameters);
	}

	/**
	 * Execute an hibernate query and returns all the rows.
	 * 
	 * @param <T>
	 *            the type of the hibernate object
	 * @param hibernateQuery
	 *            the hibernate query
	 * @param start
	 *            the first result
	 * @param max
	 *            the max size of the query
	 * @param parametersMap
	 *            named parameters and their values (null for named parameters)
	 * @param parameters
     *            the unnamed parameters in order
	 * @return all the returned rows
	 */
	private static <T> List<T> getRowsFromSessionQueryBase(String hibernateQuery, int start, int max,
			Map<String, Object> parametersMap, Object... parameters) {
		if (!isHibernateInitialized()) {
			Logger.log(Level.SEVERE, "Database not initialized, cannot do query");
			return new ArrayList<>();
		}
		Transaction tx = null;
		Session session = getSessionFactory().getCurrentSession();
		try {
			tx = session.beginTransaction();
			Query query = session.createQuery(hibernateQuery);
			if (start > 0)
				query.setFirstResult(start);
			if (max > 0)
				query.setMaxResults(max);
			loadQueryParameters(query, parametersMap, parameters);
			@SuppressWarnings("unchecked")
			List<T> rows = (List<T>) query.getResultList();
			tx.commit();
			return rows;
		} catch (NoResultException e) {
			if (tx != null)
				tx.commit();
			return new ArrayList<>(0);
		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			Logger.log(Level.SEVERE, e.toString(), e);
			return new ArrayList<>(0);
		}
	}

	/**
	 * Load parameters into the query.
	 * 
	 * @param query
	 *            the hibernate query object
	 * @param parametersMap
	 *            named parameters and their values (null for named parameters)
	 * @param parameters
     *            the unnamed parameters in order
	 */
	private static void loadQueryParameters(Query query, Map<String, Object> parametersMap, Object... parameters) {
		if (parametersMap != null)
			for (Map.Entry<String, Object> entry : parametersMap.entrySet()) {
				query.setParameter(entry.getKey(), entry.getValue());
			}
		else
			for (int i = 0; i < parameters.length; i++) {
				query.setParameter(i, parameters[i]);
			}
	}

	/**
	 * Execute SQL statements from a file.
	 * 
	 * @param conn
	 *            the SQL Connection
	 * @param in
	 *            the file InputStream
	 * @throws SQLException
	 *             if sql error in file
	 */
	public static void importSQL(Connection conn, InputStream in) throws SQLException {
		try (Scanner s = new Scanner(in)) {
			s.useDelimiter("(;(\r)?\n)|(--\n)");
			try (Statement st = conn.createStatement()) {
				while (s.hasNext()) {
					String line = s.next();
					if (line.startsWith("/*!") && line.endsWith("*/")) {
						int i = line.indexOf(' ');
						line = line.substring(i + 1, line.length() - " */".length());
					}
					if (line.trim().length() > 0) {
						st.execute(line);
					}
				}
			}
		}
	}

	/**
	 * Check if a table exists in the database.
	 * 
	 * @param conn
	 *            the SQL Connection
	 * @param name
	 *            the name of the table
	 * @return true if it exists
	 */
	public static boolean tableExists(Connection conn, String name) {
		try (PreparedStatement st = conn.prepareStatement("SHOW TABLES LIKE ?")) {
			st.setString(1, name);
			try (ResultSet rs = st.executeQuery()) {
				return rs.first();
			}
		} catch (SQLException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
			return false;
		}
	}

	/**
	 * Update the database.
	 * 
	 * @return true if the operation is successful
	 */
	public static boolean updateDatabase() {
		return updateDatabase(null);
	}

	/**
	 * Update the database.
	 * 
	 * @param connectionString
	 *            the desired connectionString (null for default)
	 * @return true if the operation is successful
	 */
    private static boolean updateDatabase(String connectionString) {
        int version = Utils.getInt("db_version");
		int currentVersion = 0;

		Logger.log(Level.INFO, "Updating database...");

		try (Connection conn = openConnection(true, connectionString)) {
			try (Statement st = conn.createStatement()) {
				if (!tableExists(conn, "db_info")) {
					Logger.log(Level.INFO, "No information on database, assuming empty");
				} else {
					try (ResultSet rs = st.executeQuery("SELECT * FROM db_info")) {
						if (!rs.first()) {
							Logger.log(Level.INFO, "No information on database, assuming empty");
						} else {
							currentVersion = rs.getInt("version");
							Date lastUpdate = rs.getTimestamp("update_date");
							Logger.log(Level.INFO, "Database v{0} last updated : {1}", currentVersion, lastUpdate);
						}
					}
				}
			}

			conn.setAutoCommit(false);

			if (currentVersion == 0 && !updateDatabaseToVersion(conn, currentVersion)) {
				Logger.log(Level.SEVERE, "Error updating database");
				return false;
			}

			while (currentVersion < version) {
				currentVersion++;
				if (!updateDatabaseToVersion(conn, currentVersion)) {
					currentVersion--;
					break;
				}
			}

			initDefaultGroups();

			if (currentVersion == version) {
				Logger.log(Level.INFO, "Database up to date");
				databaseUpToDate = true;
				return true;
			} else {
				Logger.log(Level.SEVERE, "Error updating database - bad version : {0} expected, got {1}", version, currentVersion);
				return false;
			}

		} catch (SQLException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
			return false;
		}
	}

	/**
	 * Init default groups (admin/referent/users) and update.
	 */
	public static void initDefaultGroups() {
		Logger.log(Level.INFO, "Initializing default groups...");
		BetterArrayList<Group> groups = BetterArrayList.fromList(Group.getAll());
		Group gAdmin = groups.firstOrDefault(g -> g.getName().equals(Utils.getString("admin_group_name")), null);
		if (gAdmin == null) {
			gAdmin = new Group(Utils.getString("admin_group_name"), "");
			gAdmin.saveOrUpdate();
		}
		Group gReferent = groups.firstOrDefault(g -> g.getName().equals(Utils.getString("referent_group_name")), null);
		if (gReferent == null) {
			gReferent = new Group(Utils.getString("referent_group_name"), "");
			gReferent.saveOrUpdate();
		}
		Group gStudent = groups.firstOrDefault(g -> g.getName().equals(Utils.getString("student_group_name")), null);
		if (gStudent == null) {
			gStudent = new Group(Utils.getString("student_group_name"), "");
			gStudent.saveOrUpdate();
		}
		User.setDefaultGroup(UserType.ADMIN, gAdmin);
		User.setDefaultGroup(UserType.REFERENT, gReferent);
		User.setDefaultGroup(UserType.STUDENT, gStudent);
	}

	/**
	 * Execute an update script on the database.
	 * 
	 * @param conn
	 *            the SQLConnection
	 * @param version
	 *            the version of the database or 0 to clean it
	 * @return true if the operation is successful
	 * @throws SQLException
	 *             if error during database update
	 */
	private static boolean updateDatabaseToVersion(Connection conn, int version) throws SQLException {
		String filePath;
		if (version <= 0) {
			Logger.log(Level.INFO, "Cleaning database...");
			filePath = "sql/clean.sql";
		} else {
			Logger.log(Level.INFO, "Updating to v{0}...", version);
			filePath = "sql/v" + version + ".sql";
		}

		InputStream is = DatabaseManager.class.getClassLoader().getResourceAsStream(filePath);
		if (is != null) {
			try {
				// Import SQL file for current version update and set the version in the db
				importSQL(conn, is);
				if (version > 0) {
					try (PreparedStatement st = conn.prepareStatement(
							"UPDATE db_info SET update_date = CURRENT_TIMESTAMP(), version = ? WHERE 1")) {
						st.setInt(1, version);
						st.executeUpdate();
					}
				}
				conn.commit();
			} catch (SQLException e) {
				// In case of error rollback last version update and quit updating version
				Logger.log(Level.SEVERE, e.toString(), e);
				conn.rollback();
				return false;
			}
		} else {
			Logger.log(Level.WARNING, "File {0} not found", filePath);
		}
		return true;
	}
}
