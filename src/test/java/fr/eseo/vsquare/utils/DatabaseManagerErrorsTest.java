package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertFalse;
import static org.powermock.api.easymock.PowerMock.mockStatic;
import static org.powermock.api.easymock.PowerMock.replay;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DriverManager.class, DatabaseManager.class})
public class DatabaseManagerErrorsTest {

    @Before
    public void setUp() {
        Logger.init("logging.properties", TestUtils.LOG_LEVEL);
        mockStatic(DriverManager.class);
    }

    @Test
    public void testInitInvalidUser() throws SQLException {
        expect(DriverManager.getDrivers())
                .andReturn(Collections.enumeration(Arrays.asList(new Driver[]{new com.mysql.cj.jdbc.Driver()})));

        expect(DriverManager.getConnection(TestUtils.DB_CONNECTION_STRING, Utils.getString("db_user"), Utils.getString("db_password")))
                .andThrow(new SQLException("Test exception do not panic"));

        replay(DriverManager.class);

        assertFalse(DatabaseManager.init(TestUtils.DB_CONNECTION_STRING));
    }

    @Test
    public void testInitInvalidSuperUser() throws SQLException {
        expect(DriverManager.getDrivers())
                .andReturn(Collections.enumeration(Arrays.asList(new Driver[]{new com.mysql.cj.jdbc.Driver()})));

        expect(DriverManager.getConnection(TestUtils.DB_CONNECTION_STRING, Utils.getString("db_user"), Utils.getString("db_password")))
                .andReturn(null);

        expect(DriverManager.getConnection(TestUtils.DB_CONNECTION_STRING, Utils.getString("db_super_user"), Utils.getString("db_super_password")))
                .andThrow(new SQLException("Test exception do not panic"));

        replay(DriverManager.class);

        assertFalse(DatabaseManager.init(TestUtils.DB_CONNECTION_STRING));
    }

    @Test
    public void testRegisterDriver() throws SQLException {
        expect(DriverManager.getDrivers())
                .andReturn(Collections.enumeration(new ArrayList<>()));

        DriverManager.registerDriver(anyObject());
        expectLastCall();

        replay(DriverManager.class);

        DatabaseManager.checkDriver(false);

        PowerMock.verifyAll();
    }

    @Test
    public void testUnregisterDriver() throws SQLException {
        expect(DriverManager.getDrivers())
                .andReturn(Collections.enumeration(Arrays.asList(new Driver[]{new com.mysql.cj.jdbc.Driver()})));

        DriverManager.deregisterDriver(anyObject());
        expectLastCall();

        replay(DriverManager.class);

        DatabaseManager.checkDriver(true);

        PowerMock.verifyAll();
    }

    @Test
    public void testRegisterDriverError() throws SQLException {
        expect(DriverManager.getDrivers())
                .andReturn(Collections.enumeration(new ArrayList<>()));

        DriverManager.registerDriver(anyObject());
        expectLastCall().andThrow(new SQLException("Test exception do not panic"));

        replay(DriverManager.class);

        DatabaseManager.checkDriver(false);

        PowerMock.verifyAll();
    }
}
