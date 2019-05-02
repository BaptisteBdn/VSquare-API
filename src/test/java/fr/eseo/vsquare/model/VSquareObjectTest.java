package fr.eseo.vsquare.model;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Test;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.DatabaseManager;

import static org.junit.Assert.*;

public class VSquareObjectTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
	}
	
	@Test
	public void testDelete() throws SQLException {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		try (Statement st = TestUtils.getConnection().createStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT * FROM user WHERE 1")) {
				assertTrue(rs.first());
			}

			assertTrue(u.delete());

			try (ResultSet rs = st.executeQuery("SELECT * FROM user WHERE 1")) {
				assertFalse(rs.first());
			}
		}
	}
	
	@Test
	public void testDeleteError() throws SQLException, IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		Field sessionFactory = DatabaseManager.class.getDeclaredField("sessionFactory");
		SessionFactory tmp = DatabaseManager.getSessionFactory();
		sessionFactory.setAccessible(true);
		sessionFactory.set(DatabaseManager.class, null);
		
		assertFalse(u.delete());
		
		sessionFactory.set(DatabaseManager.class, tmp);
	}
	
	@Test
	public void testDeleteError2() throws SQLException, IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		User u = new User("test_user", UserType.ADMIN, "test");

		assertFalse(u.delete());
	}
	
	@Test
	public void testSaveOrUpdateError() throws SQLException, IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		User u = new User("test_user", UserType.ADMIN, "test");

		Field sessionFactory = DatabaseManager.class.getDeclaredField("sessionFactory");
		SessionFactory tmp = DatabaseManager.getSessionFactory();
		sessionFactory.setAccessible(true);
		sessionFactory.set(DatabaseManager.class, null);
		
		assertFalse(u.saveOrUpdate());
		
		sessionFactory.set(DatabaseManager.class, tmp);
	}

	@Test
	public void testFindById() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		int id = u.getId();

		User u2 = User.findById(id);

		assertEquals("test_user", u2.getLogin());
		assertEquals("test", u2.getCommonName());

		u.delete();

		assertNull(Group.findById(-1));
	}

	@Test
	public void testGetAll() {
		User u1 = new User("test_user", UserType.ADMIN, "test");
		u1.saveOrUpdate();

		User u2 = new User("test_user2", UserType.ADMIN, "test2");
		u2.saveOrUpdate();

		List<User> list = User.getAll();

		assertEquals(2, list.size());
		assertTrue(list.contains(u1));
		assertTrue(list.contains(u2));
	}
	
	@Test
	public void testToString() {
		User u = new User("test_user", UserType.ADMIN, "test");

		assertEquals("User:0", u.toString());
		
		u.saveOrUpdate();
		
		assertEquals("User:"+u.getId(), u.toString());
	}

	@SuppressWarnings("unlikely-arg-type")
	@Test
	public void testEquals() {
		User u = new User("test_user", UserType.ADMIN, "test");

        assertNotNull(u);
        assertNotEquals("string", u);
		
		Vm v  = new Vm(u, "id", "name", "desc");

        assertNotEquals(v, u);
	}
}
