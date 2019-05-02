package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.utils.*;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.logging.Level;

/**
 * Application Lifecycle Listener implementation class InitContextListener.
 * It will be called at server launch and closed.
 * 
 * @author Clement Gouin
 */
@WebListener
public class InitContextListener implements ServletContextListener {

	public InitContextListener() {
		super();
	}

	/**
	 * Called at the server launch.
	 */
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		try {
			Logger.init("logging.properties");
			Logger.log(Level.INFO, "Server starting");
			if(!DatabaseManager.init(Utils.getConnectionString("db_connection_string")))
				throw new IllegalStateException("Database cannot be initialized");
			LDAPUtils.setConnectionString(Utils.getConnectionString("ldap_connection_string"));
			if(!VSphereManager.init(Utils.getString("vsphere_host")))
                throw new IllegalStateException("VCenter cannot be reached (API)");
            if (!VCenterManager.init())
                throw new IllegalStateException("VCenter cannot be reached (SDK)");
            Utils.createLDAPUsers();
            Utils.checkUsers();
			Logger.log(Level.INFO, "Server started");
		} catch (Exception e) {
			Logger.log(Level.SEVERE, e.toString(), e);
			throw new IllegalStateException("There was an error during initialization");
		}
		
	}

	/**
	 * Called at the server closure.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		DatabaseManager.getSessionFactory().getCurrentSession().close();
		DatabaseManager.checkDriver(true);
		Logger.log(Level.INFO, "Server closed");
	}

}
