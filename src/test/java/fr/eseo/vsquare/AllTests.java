package fr.eseo.vsquare;

import fr.eseo.vsquare.model.*;
import fr.eseo.vsquare.servlet.*;
import fr.eseo.vsquare.utils.*;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
        //Coverage tests
        CoverageTests.class,
        //Utils tests
        HttpUtilsTest.class, LDAPUtilsTest.class, VSphereManagerTest.class, VSphereConnectorTest.class,
        VCenterManagerTest.class, DatabaseManagerTest.class, ServletUtilsTest.class, UtilsTest.class,
        TarUtilsTest.class,
        //Utils errors tests
        VCenterManagerErrorTest.class, DatabaseManagerErrorsTest.class,
        //Models tests
        VSquareObjectTest.class, UserTest.class, ErrorLogTest.class, EventLogTest.class, TokenTest.class,
        GroupTest.class, VmTest.class, SnapshotTest.class, DownloadLinkTest.class, NetworkTest.class,
        PermissionTest.class,
        //Servlets tests
        InitContextListenerTest.class, AuthServletTest.class, InfoServletTest.class, LogServletTest.class,
        UserServletTest.class, NetworkServletTest.class, GroupServletTest.class, VMsServletTest.class,
        ImportServletTest.class, ExportServletTest.class,})
public class AllTests {

}
