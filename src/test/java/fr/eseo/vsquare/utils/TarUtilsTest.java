package fr.eseo.vsquare.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import fr.eseo.vsquare.TestUtils;



/**
 * @author Kalioz
 *
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "java.*", "javax.*", "org.*", "fr.eseo.vsquare.model.*", "fr.eseo.vsquare.utils.DatabaseManager" })
@PrepareForTest({ VSphereManager.class, LDAPUtils.class, ServletUtils.class, VCenterManager.class,
		VSphereConnector.class })
public class TarUtilsTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        Logger.init("logging.properties", TestUtils.LOG_LEVEL);
    }

	private File getOVATestFile(){
		String pathToOVAtest = "vmware_compatible_light.ova";
		ClassLoader classLoader = getClass().getClassLoader();
		return new File(classLoader.getResource(pathToOVAtest).getFile());
	}
	
	@Test
	public void testCompressSuccess() {
		File outputFile = new File(Utils.getString("temp_dir")+"test_compress");
		
		if (outputFile.exists()){
			assertTrue(Utils.deleteFolder(outputFile));
		}
		assertFalse(outputFile.exists());
		try {
			TARUtils.compress(outputFile, getOVATestFile());
		} catch (IOException e) {
			e.printStackTrace();
			fail("IO exception raised");
		}
		
		assertTrue(outputFile.exists());
		assertTrue("File is empty",outputFile.length() > 10);
		assertTrue(Utils.deleteFolder(outputFile));
	}
	
	@Test
	public void testCompressOutputStreamSuccess() {
		File outputFile = new File(Utils.getString("temp_dir")+"test_compress");
		
		if (outputFile.exists()){
			assertTrue(Utils.deleteFolder(outputFile));
		}
		assertFalse(outputFile.exists());
		try(FileOutputStream fos = new FileOutputStream(outputFile)) {
			TARUtils.compress(fos, getOVATestFile());
		} catch (IOException e) {
			e.printStackTrace();
			fail("IO exception raised");
		}
		
		assertTrue(outputFile.exists());
		assertTrue("File is empty",outputFile.length() > 10);
		assertTrue(Utils.deleteFolder(outputFile));
	}

	@Test
	public void testDecompress() {
		File outputFile = new File(Utils.getString("temp_dir")+"test_decompress");
		if (outputFile.exists()){
			assertTrue(Utils.deleteFolder(outputFile));
		}
		assertFalse(outputFile.exists());
		try {
			TARUtils.decompress(getOVATestFile(), outputFile);
		} catch (IOException e) {
			e.printStackTrace();
			fail("IO exception raised");
		}
		
		assertTrue(outputFile.exists());
		assertTrue(Utils.deleteFolder(outputFile));
	}

}
