package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.Group;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.User.UserType;
import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

public class DatabaseManagerTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(false);
	}

	@Test
	public void testUpdateDatabase() throws SQLException, IOException {
		try(Connection conn = DatabaseManager.openConnection(true)){
			TestUtils.cleanDatabase(conn);
		}
		
		assertFalse(DatabaseManager.tableExists(TestUtils.getConnection(), "db_info"));

		assertTrue(DatabaseManager.updateDatabase());

		assertTrue(DatabaseManager.tableExists(TestUtils.getConnection(), "db_info"));

		try (Statement st = TestUtils.getConnection().createStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT * FROM db_info")) {
				rs.first();
                assertEquals(Utils.getInt("db_version"), rs.getInt("version"));
			}
		}

		List<Group> list = Group.getAll();
		assertEquals(3, list.size());

		assertNotNull(UserType.ADMIN.getDefaultGroup());
		assertNotNull(UserType.REFERENT.getDefaultGroup());
		assertNotNull(UserType.STUDENT.getDefaultGroup());
	}

	@Test
	public void testTableExists() throws SQLException {
		assertTrue(DatabaseManager.tableExists(TestUtils.getConnection(), "db_info"));
		assertFalse(DatabaseManager.tableExists(TestUtils.getConnection(), "invalid_table_name"));
	}

	@Test
	public void testDatabaseOpenConnectionError() throws SQLException {
		try {
			DatabaseManager.setDefaultConnectionString(null);
			DatabaseManager.openConnection();
			fail("No error");
		} catch (ExceptionInInitializerError e) {
		}
		DatabaseManager.setDefaultConnectionString(TestUtils.DB_CONNECTION_STRING);
	}

	@Test
	public void testGetFirstFromSessionQueryNamed() throws SQLException {
		TestUtils.emptyDatabase();
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		HashMap<String, Object> params = new HashMap<>();
		params.put("id", u.getId());

		Object obj = DatabaseManager.getFirstFromSessionQueryNamed("FROM User WHERE id = :id", params);

		assertTrue(obj instanceof User);
		User u2 = (User) obj;
		assertEquals(u, u2);
	}
	
	@Test
	public void testGetFirstFromSessionQuery() throws SQLException {
		TestUtils.emptyDatabase();
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		Object obj = DatabaseManager.getFirstFromSessionQuery("FROM User WHERE id = ?0", u.getId());

		assertTrue(obj instanceof User);
		User u2 = (User) obj;
		assertEquals(u, u2);
	}
	
	@Test
	public void testGetFirstFromSessionQueryError() throws SQLException {
		TestUtils.emptyDatabase();
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		Object obj = DatabaseManager.getFirstFromSessionQuery("FROM Userd WHERE id = ?0", u.getId());

		assertNull(obj);
	}
	
	@Test
	public void testGetFirstFromSessionQueryError2() throws SQLException, IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		TestUtils.emptyDatabase();
		
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		Field sessionFactory = DatabaseManager.class.getDeclaredField("sessionFactory");
		SessionFactory tmp = DatabaseManager.getSessionFactory();
		sessionFactory.setAccessible(true);
		sessionFactory.set(DatabaseManager.class, null);

		Object obj = DatabaseManager.getFirstFromSessionQuery("FROM User WHERE id = ?0", u.getId());

		assertNull(obj);
		
		sessionFactory.set(DatabaseManager.class, tmp);
	}
	
	@Test
	public void testGetFirstFromSessionQueryNoResult() throws SQLException {
		TestUtils.emptyDatabase();
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		Object obj = DatabaseManager.getFirstFromSessionQuery("FROM User WHERE id = ?0", u.getId()+1);

		assertNull(obj);
	}
	
	@Test
	public void testGetRowsFromSessionQueryNamed() throws SQLException {
		TestUtils.emptyDatabase();
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		HashMap<String, Object> params = new HashMap<>();
		params.put("id", u.getId());

		List<Object> lst = DatabaseManager.getRowsFromSessionQueryNamed("FROM User WHERE id = :id", params);

		assertEquals(1, lst.size());
		assertTrue(lst.get(0) instanceof User);
		User u2 = (User) lst.get(0);
		assertEquals(u, u2);
	}
	
	@Test
	public void testGetRowsFromSessionQuery() throws SQLException {
		TestUtils.emptyDatabase();
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		List<Object> lst = DatabaseManager.getRowsFromSessionQuery("FROM User WHERE id = ?0", u.getId());

		assertEquals(1, lst.size());
		assertTrue(lst.get(0) instanceof User);
		User u2 = (User) lst.get(0);
		assertEquals(u, u2);
	}
	
	@Test
	public void testGetRowsFromSessionQueryError() throws SQLException {
		TestUtils.emptyDatabase();
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		List<Object> lst = DatabaseManager.getRowsFromSessionQuery("FROM Userd WHERE id = ?0", u.getId());

		assertEquals(0, lst.size());
	}
	
	@Test
	public void testGetRowsFromSessionQueryError2() throws SQLException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		TestUtils.emptyDatabase();
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		Field sessionFactory = DatabaseManager.class.getDeclaredField("sessionFactory");
		SessionFactory tmp = DatabaseManager.getSessionFactory();
		sessionFactory.setAccessible(true);
		sessionFactory.set(DatabaseManager.class, null);

		List<Object> lst = DatabaseManager.getRowsFromSessionQuery("FROM User WHERE id = ?0", u.getId());

		assertEquals(0, lst.size());
		
		sessionFactory.set(DatabaseManager.class, tmp);
	}
	
	@Test
	public void testGetRowsFromSessionQueryNotFound() throws SQLException {
		TestUtils.emptyDatabase();
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		List<Object> lst = DatabaseManager.getRowsFromSessionQuery("FROM User WHERE id = ?0", u.getId()+1);

		assertEquals(0, lst.size());
	}

}
