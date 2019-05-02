package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.ErrorLog;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.User.UserType;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.logging.Level;

import static org.junit.Assert.*;

public class LoggerTest {

    @Before
    public void setUp() throws Exception {
        TestUtils.initTest(true);
    }

	@Test
	public void testReportError() throws Exception{
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		Exception e = new Exception("Testing errors do not panic");
		
		Logger.log(Level.SEVERE, e.toString(), e, u);
		
		List<ErrorLog> errors = ErrorLog.getAll();
		assertEquals(1, errors.size());
		
		ErrorLog el = errors.get(0);
		assertTrue(el.getError().startsWith("[VSquare-LoggerTest] "+e.toString()));
		assertEquals(u,el.getUser());
	}
	
	@Test
	public void testReportError2() throws Exception{
		Exception e = new Exception("Testing errors do not panic");
		
		Logger.log(Level.SEVERE, e.toString(), e);
		
		List<ErrorLog> errors = ErrorLog.getAll();
		assertEquals(1, errors.size());
		
		ErrorLog el = errors.get(0);
		assertTrue(el.getError().startsWith("[VSquare-LoggerTest] "+e.toString()));
		assertNull(el.getUser());
	}

	@Test
	public void testReportError3() throws Exception{
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		Logger.log(Level.SEVERE, "Test error do not panic", u);
		
		List<ErrorLog> errors = ErrorLog.getAll();
		assertEquals(1, errors.size());
		
		ErrorLog el = errors.get(0);
		assertTrue(el.getError().startsWith("[VSquare-LoggerTest] Test error do not panic"));
		assertEquals(u,el.getUser());
	}
	
	@Test
	public void testReportError4() throws Exception{
		Logger.log(Level.SEVERE, "Test error do not panic");
		
		List<ErrorLog> errors = ErrorLog.getAll();
		assertEquals(1, errors.size());
		
		ErrorLog el = errors.get(0);
		assertTrue(el.getError().startsWith("[VSquare-LoggerTest] Test error do not panic"));
		assertNull(el.getUser());
	}
}
