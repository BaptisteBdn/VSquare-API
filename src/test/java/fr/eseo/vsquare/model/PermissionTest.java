package fr.eseo.vsquare.model;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.utils.Utils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.*;

public class PermissionTest {

    @Before
    public void setUp() throws Exception {
        TestUtils.initTest(true);
    }

    @Test
    public void testSaveOrUpdatePermission() throws SQLException {
        Permission p = new Permission();

        p.setVmCount(5);

        try (Statement st = TestUtils.getConnection().createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT * FROM permission WHERE 1")) {
                assertFalse(rs.first());
            }

            assertTrue(p.saveOrUpdate());

            try (ResultSet rs = st.executeQuery("SELECT * FROM permission WHERE 1")) {
                assertTrue(rs.first());
                assertEquals((int) p.getId(), rs.getInt("id"));
                assertEquals(5, rs.getInt("vm_count"));
            }

            p.setVmCount(10);
            assertTrue(p.saveOrUpdate());

            try (ResultSet rs = st.executeQuery("SELECT * FROM permission WHERE 1")) {
                assertTrue(rs.first());
                assertEquals(10, rs.getInt("vm_count"));
            }
        }
    }

    @Test
    public void testToJSON() {
        Permission p = new Permission(1, 2, 3, 4);
        p.saveOrUpdate();
        JSONObject json = p.toJSON();
        assertEquals((int) p.getId(), json.getInt("id"));
        assertEquals(p.getVmCount(), json.getInt("vm_count"));
        assertEquals(p.getDiskStorage(), json.getInt("disk_storage"));
        assertEquals(p.getMemorySize(), json.getInt("memory_size"));
        assertEquals(p.getCpuCount(), json.getInt("cpu_count"));
        assertEquals(5, json.length());
    }

    @Test
    public void testToJSONDetailed() {
        Permission p = new Permission(1, 2, 3, 4);
        p.saveOrUpdate();
        JSONObject json = p.toJSON(true);
        assertEquals((int) p.getId(), json.getInt("id"));
        assertTrue(json.has("creation_date"));
        assertEquals(p.getVmCount(), json.getInt("vm_count"));
        assertEquals(p.getDiskStorage(), json.getInt("disk_storage"));
        assertEquals(p.getMemorySize(), json.getInt("memory_size"));
        assertEquals(p.getCpuCount(), json.getInt("cpu_count"));
        assertEquals(6, json.length());
    }

    @Test
    public void testValid() {
        Permission p = new Permission();
        assertTrue(p.isValid());
        p.setVmCount(Utils.getInt("minimal_vm_count") - 1);
        assertFalse(p.isValid());
        p = new Permission();
        p.setMemorySize(Utils.getInt("minimal_memory_size") - 1);
        assertFalse(p.isValid());
        p = new Permission();
        p.setDiskStorage(Utils.getInt("minimal_disk_storage") - 1);
        assertFalse(p.isValid());
        p = new Permission();
        p.setCpuCount(Utils.getInt("minimal_cpu_count") - 1);
        assertFalse(p.isValid());
    }
    
    @Test
    public void testMaxPermission() {
    	Permission p1 = new Permission();
    	Permission p2 = new Permission(10, 4, 4096, 24192);
    	assertTrue(p1.isValid());
    	assertTrue(p2.isValid());
    	
    	Permission p3 = p1.getMax(p2);
    	assertEquals(p2.getVmCount(), p3.getVmCount());
    	assertEquals(p2.getCpuCount(), p3.getCpuCount());
    	assertEquals(p2.getMemorySize(), p3.getMemorySize());
    	assertEquals(p2.getDiskStorage(), p3.getDiskStorage());
    }
    
    @Test
    public void testMaxPermission2() {
    	Permission p1 = new Permission(14, 4, 4096, 80192);
    	Permission p2 = new Permission();
    	assertTrue(p1.isValid());
    	assertTrue(p2.isValid());
    	
    	Permission p3 = p1.getMax(p2);
    	assertEquals(p1.getVmCount(), p3.getVmCount());
    	assertEquals(p1.getCpuCount(), p3.getCpuCount());
    	assertEquals(p1.getMemorySize(), p3.getMemorySize());
    	assertEquals(p1.getDiskStorage(), p3.getDiskStorage());
    }
    
    @Test
    public void testMaxPermission3() {
    	Permission p1 = new Permission(14, 2, 1024, 8192);
    	Permission p2 = new Permission(8, 4, 4096, 4096);
    	assertTrue(p1.isValid());
    	assertTrue(p2.isValid());
    	
    	Permission p3 = p1.getMax(p2);
    	
    	assertEquals(p1.getVmCount(), p3.getVmCount());
    	assertEquals(p2.getCpuCount(), p3.getCpuCount());
    	assertEquals(p2.getMemorySize(), p3.getMemorySize());
    	assertEquals(p1.getDiskStorage(), p3.getDiskStorage());
    }
    
    @Test
    public void testMaxPermission4() {
    	Permission p1 = new Permission(9, 6, 1024, 4096);
    	Permission p2 = new Permission(17, 4, 2048, 1024);
    	assertTrue(p1.isValid());
    	assertTrue(p2.isValid());
    	
    	Permission p3 = p1.getMax(p2);
    	assertEquals(p2.getVmCount(), p3.getVmCount());
    	assertEquals(p1.getCpuCount(), p3.getCpuCount());
    	assertEquals(p2.getMemorySize(), p3.getMemorySize());
    	assertEquals(p1.getDiskStorage(), p3.getDiskStorage());
    }
    
    @Test
    public void testMinimalPermission() {
    	Permission p = Permission.getMinimalPermission();
    	assertEquals(p.getVmCount(), Utils.getInt("minimal_vm_count"));
    	assertEquals(p.getCpuCount(), Utils.getInt("minimal_cpu_count"));
    	assertEquals(p.getMemorySize(), Utils.getInt("minimal_memory_size"));
    	assertEquals(p.getDiskStorage(), Utils.getInt("minimal_disk_storage"));
    }

}
