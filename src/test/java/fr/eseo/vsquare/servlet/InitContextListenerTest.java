package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.utils.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "java.*", "javax.*", "org.*" })
@PrepareForTest({VSphereManager.class, LDAPUtils.class, DatabaseManager.class, VCenterManager.class, Utils.class})
public class InitContextListenerTest {

	@Test
	public void testContextInitializedSuccess() {
		PowerMockito.mockStatic(DatabaseManager.class);
		when(DatabaseManager.init(any())).thenReturn(true);
		
		PowerMockito.mockStatic(LDAPUtils.class);
		PowerMockito.doNothing().when(LDAPUtils.class);
		LDAPUtils.setConnectionString(any());
		
		PowerMockito.mockStatic(VSphereManager.class);
		when(VSphereManager.init(any())).thenReturn(true);

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.init()).thenReturn(true);

		PowerMockito.spy(Utils.class);
		PowerMockito.doNothing().when(Utils.class);
		Utils.createLDAPUsers();
        PowerMockito.doNothing().when(Utils.class);
        Utils.checkUsers();

		new InitContextListener().contextInitialized(null);
		
		PowerMockito.verifyStatic(DatabaseManager.class);
		DatabaseManager.init(Utils.getConnectionString("db_connection_string"));
		
		PowerMockito.verifyStatic(LDAPUtils.class);
		LDAPUtils.setConnectionString(Utils.getConnectionString("ldap_connection_string"));
		
		PowerMockito.verifyStatic(VSphereManager.class);
		VSphereManager.init(Utils.getConnectionString("vsphere_host"));
	}

	@Test
	public void testContextInitializedDBError() {
		PowerMockito.mockStatic(DatabaseManager.class);
		when(DatabaseManager.init(any())).thenReturn(false);
		
		try {
			new InitContextListener().contextInitialized(null);
			fail("No error on db update fail");
		}catch(IllegalStateException e) {}
	}
	
	@Test
	public void testContextInitializedVSphereError() {
		PowerMockito.mockStatic(DatabaseManager.class);
		when(DatabaseManager.init(any())).thenReturn(true);
		
		PowerMockito.mockStatic(LDAPUtils.class);
		PowerMockito.doNothing().when(LDAPUtils.class);
		LDAPUtils.setConnectionString(any());
		
		PowerMockito.mockStatic(VSphereManager.class);
		when(VSphereManager.init(any())).thenReturn(false);

		try {
			new InitContextListener().contextInitialized(null);
			fail("No error on vsphere connection fail");
		}catch(IllegalStateException e) {}
	}
}
