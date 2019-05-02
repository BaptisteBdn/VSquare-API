package fr.eseo.vsquare;

import antlr.Utils;
import fr.eseo.vsquare.model.*;
import fr.eseo.vsquare.utils.*;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;

public class CoverageTests {

	@Before
	public void setUp() throws Exception {
		Logger.init("logging.properties", Level.WARNING);
	}
	
	@Test
	public void utilsConstructorCoverage()
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Constructor<?> ctr;
		for (Class<?> c : new Class[] { DatabaseManager.class, HttpUtils.class, LDAPUtils.class, Logger.class,
				ServletUtils.class, TARUtils.class, Utils.class, VSphereConnector.class, VSphereManager.class, VCenterManager.class}) {
			ctr = c.getDeclaredConstructors()[0];
			ctr.setAccessible(true);
			ctr.newInstance();
		}
	}
	
	@Test
	public void hashCodeCoverage() {
		new DownloadLink().hashCode();
		new ErrorLog().hashCode();
		new EventLog().hashCode();
		new Group().hashCode();
		new Network().hashCode();
		new Permission().hashCode();
		new Snapshot().hashCode();
		new Token().hashCode();
		new User().hashCode();
		new Vm().hashCode();
	}
	
	@Test
	public void servletUtilsBruteForceSecurityCoverage() throws Exception {
		Field bruteForceSecurity = ServletUtils.class.getDeclaredField("bruteForceSecurity");
		bruteForceSecurity.setAccessible(true);
		bruteForceSecurity.set(ServletUtils.class, true);
		ServletUtils.bruteForceSecurity();
		bruteForceSecurity.set(ServletUtils.class, false);
	}
}
