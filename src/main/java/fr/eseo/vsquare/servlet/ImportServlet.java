package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.model.EventLog;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.Vm;
import fr.eseo.vsquare.utils.Logger;
import fr.eseo.vsquare.utils.ServletUtils;
import fr.eseo.vsquare.utils.Utils;
import fr.eseo.vsquare.utils.VCenterManager;

import org.apache.commons.compress.utils.IOUtils;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Servlet implementation class ImportServlet.
 * 
 * @author Clément Loiselet
 */
@WebServlet(name = "import", urlPatterns = { "/import/*" })
@MultipartConfig
public class ImportServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private enum MIMEType {	ISO, OVA}
	
	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public ImportServlet() {
		super();
	}

	/**
	 * Service at /api/import/*.
	 */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			User user = ServletUtils.verifyToken(request, response);
			if (user == null)
				return;
			LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
			map.put("POST /api/import/ova", () -> importOVA(user, request, response));
			map.put("POST /api/import/iso", () -> importISO(user, request, response));
			ServletUtils.mapRequest(request, response, map);
		} catch (Exception e) {
			Logger.log(Level.SEVERE, e.toString(), e);
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * save a file to the disk and verify if its type is correct.
	 * @param request
	 * @param response
	 * @param name the name of the Part
	 * @param output the file in which it should be created
	 * @param extension the extension the part should have. set to null if it doesn't matter
	 * @return
	 */
	private boolean savePart(HttpServletRequest request, HttpServletResponse response, String name, File output, String extension){
		Part part;
		try {
			part = request.getPart(name);
		} catch (IOException | ServletException e) {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Internal error - could not read file parameter");
			return false;
		}

		if (part == null){
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
					"missing file parameter : " + name);
			return false;
		}

		if ( extension != null && ! extension.equals(Utils.getExtension(part.getSubmittedFileName()))){
			Logger.log(Level.INFO, "import failed - bad filetype: "+part.getSubmittedFileName() );
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "submitted file was not an "+ extension);
			return false;
		}

		File file = output;
		
		if (! file.getParentFile().exists()){
			file.getParentFile().mkdirs();
		}

		//write content on disk
		try(OutputStream out = new FileOutputStream(file);
				InputStream filecontent = part.getInputStream();) {
			IOUtils.copy(filecontent, out);
		} catch ( IOException fne) {
			Logger.log(Level.SEVERE, "Problem during file upload while writing on disk");
			ServletUtils.sendError(response,HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Problem during file upload while writing on disk");
			return false;
		}
		
		return true;
	}
	
	/**
	 * finish the import of the VM (common part between all improt when the vcenter part is succesfull)
	 * @param response
	 * @param user
	 * @param idVm
	 * @param vmName
	 * @param desc
	 */
	private void finishImport(HttpServletResponse response, User user,String idVm, String vmName,String desc){
		if (idVm == null){
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"VM has been created but seems unreachable");
			return;
		}
		
		//create the VM for the user
		Vm vm = new Vm(user, idVm, vmName, desc);
		if (! vm.saveOrUpdate()){
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"VM has been created but could not be saved - idVm :"+idVm);
			return;
		}

        EventLog.log(user, EventLog.EventAction.IMPORT, vm);
		
		JSONObject result = new JSONObject();
		result.put("id", idVm);
		
		ServletUtils.sendJSONResponse(response, result);
	}
	
	/**
	 * Import an OVA file to VSphere.
	 * 
	 * @param user the user who made the request
	 * @param request the http servlet request
	 * @param response the http servlet response
	 */
	private void importOVA(User user, HttpServletRequest request, HttpServletResponse response) {
		importFormat(user, request, response, MIMEType.OVA);
	}
	
	/**
	 * Import an OVA file to VSphere.
	 * 
	 * @param user the user who made the request
	 * @param request the http servlet request
	 * @param response the http servlet response
	 */
	private void importISO(User user, HttpServletRequest request, HttpServletResponse response){
		importFormat(user, request, response, MIMEType.ISO);
	}

	private void importFormat(User user, HttpServletRequest request, HttpServletResponse response, MIMEType format){
		if (user.isStudent() && user.getEffectivePermission().getVmCount() >= user.getVms().size()){
			ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN,"Maximum number of VM reached");
			return;
		}

		String vmName = request.getParameter("vmName");
		String desc = request.getParameter("description");
		
		if (vmName == null){
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "missing parameter : vmName");
		}
		
		if (desc == null){
			desc = "";
		}

		String fileName = UUID.randomUUID()+".iso";
		File file = new File(Utils.getString("temp_dir")+fileName);
		
		boolean success = false;
		
		if (format == MIMEType.ISO){
			success = savePart(request, response, "isoFile", file, "iso");
		}else{
			success = savePart(request, response, "ovaFile", file, "ova");
		}
		
		if (! success){
			return;//error handled in the savePart
		}

		//write content on vsphere
		String idVm = null;
		try {
			if (format == MIMEType.ISO){
				idVm = VCenterManager.importLocalISO(file, user.getLogin()+"_"+vmName+"_"+UUID.randomUUID());
			}else{
				idVm = VCenterManager.importLocalOVA(file, user.getLogin()+"_"+vmName+"_"+UUID.randomUUID());
			}
		} catch (IOException e) {
			Logger.log(Level.WARNING, "Import failed", e);
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "problem occured during import to vcenter");
			return;
		}finally{
			//cleanup
			try {
				java.nio.file.Files.delete(file.toPath());
			} catch (IOException e) {
				Logger.log(Level.WARNING, "could not delete file after use : "+file.getAbsolutePath());
			}
		}
				
		finishImport(response,user, idVm, vmName, desc);
	}
}
