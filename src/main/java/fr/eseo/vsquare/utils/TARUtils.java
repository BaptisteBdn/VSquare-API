package fr.eseo.vsquare.utils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.util.logging.Level;

/**
 * Class enabling the quick support of TAR.
 * source : https://memorynotfound.com/java-tar-example-compress-decompress-tar-tar-gz-files/
 * 
 * @author Kalioz
 */
public class TARUtils {
    private TARUtils() {}

    /**
     * Compress all the files and put them into the name file.
     *
     * @param name the final file name
     * @param files files to compress
     * @throws IOException error compressing files
     */
    public static void compress(String name, File... files) throws IOException {
    	File outFile = new File(name);
        compress(outFile, files);
    }
    
    public static void compress(File output, File... files) throws IOException {
        try (TarArchiveOutputStream out = getTarArchiveOutputStream(output)){
            for (File file : files){
                addToArchiveCompression(out, file, ".");
            }
        }
    }
    
    public static void compress(OutputStream output, File... files) throws IOException{
    	try (TarArchiveOutputStream out = getTarArchiveOutputStream(output)){
            for (File file : files){
                addToArchiveCompression(out, file, ".");
            }
        }
    }
    
    /**
     * Decompress a file into the folder out.
     *
     * @param in the input file
     * @param out the output file
     * @throws IOException On decompression error
     */
    public static void decompress(File in, File out) throws IOException{
    	try(FileInputStream fis = new FileInputStream(in)){
    		decompress(fis, out);
    	}
    }
    
    public static void decompress(InputStream is, File out) throws IOException{
    	 try (TarArchiveInputStream fin = new TarArchiveInputStream(is)){
             TarArchiveEntry entry;
             while ((entry = fin.getNextTarEntry()) != null) {
                 if (entry.isDirectory()) {
                     continue;
                 }
                 File curfile = new File(out, entry.getName());
                 File parent = curfile.getParentFile();
                 if (!parent.exists()) {
                     parent.mkdirs();
                 }
                 try(OutputStream os = new FileOutputStream(curfile)){
                	 IOUtils.copy(fin, os);
                 }
             }
         }
    }

    /**
     * Open a stream to the output file.
     * @param output the output file
     * @return the opened stream
     * @throws IOException On opening error
     */
    private static TarArchiveOutputStream getTarArchiveOutputStream(File output) throws IOException {
        return getTarArchiveOutputStream(new FileOutputStream(output));
    }
    
    static TarArchiveOutputStream getTarArchiveOutputStream(OutputStream os){
    	TarArchiveOutputStream taos = new TarArchiveOutputStream(os);
        // TAR has an 8 gig file limit by default, this gets around that
        taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
        // TAR originally didn't support long file names, so enable the support for it
        taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        taos.setAddPaxHeadersForNonAsciiNames(true);
        return taos;
    }

    /**
     * Add a file/folder to the archive.
     *
     * @param out the output stream
     * @param file the file to add
     * @param dir the desired path in the archive
     * @throws IOException On adding error
     */
    private static void addToArchiveCompression(TarArchiveOutputStream out, File file, String dir) throws IOException {
        String entry = dir + File.separator + file.getName();
        if (file.isFile()){
            out.putArchiveEntry(new TarArchiveEntry(file, entry));
            try (FileInputStream in = new FileInputStream(file)){
                IOUtils.copy(in, out);
            }
            out.closeArchiveEntry();
        } else if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null){
                for (File child : children){
                    addToArchiveCompression(out, child, entry);
                }
            }
        } else {
            Logger.log(Level.WARNING, "TAR compression : file not supported :", file);
        }
    }
}
