package fr.eseo.vsquare.model;

import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.DatabaseManager;
import fr.eseo.vsquare.utils.HttpUtils.HttpResult;
import fr.eseo.vsquare.utils.VSphereConnector;
import org.json.JSONObject;

import javax.persistence.*;
import java.util.List;

/**
 * VM object model.
 * 
 * Implements the VM object class as defined in the domain.
 * 
 * @author Baptiste Beduneau
 */

@Entity
@Table(name = "vm")
public class Vm extends VSquareObject {

	// region Variables
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "id_user")
	private User user;

	@Column(name = "id_vm_vcenter")
	private String idVmVcenter;

	@Column(name = "name")
	private String name;

	@Column(name = "description")
	private String description;
	
	@Column(name = "template")
	private boolean template;

	// endregion_

	// region Constructors

	public Vm() {

	}

	/**
	 * Constructor 
	 * 
	 * @param user the user owning the VM
	 * @param idVmVc the id defined in vCenter
	 * @param name the name of the VM
	 * @param desc the description of the VM
	 */
	public Vm(User user, String idVmVc, String name, String desc) {
		super();
		this.user = user;
		this.idVmVcenter = idVmVc;
		this.name = name;
		this.description = desc;
	}

	// endregion

	// region Accessors

	/**
	 * @return user owning the VM.
	 */
	public User getUser() {
		return user;
	}

	/**
	 * @param user the new owner of the VM.
	 */
	public void setUser(User user) {
		this.user = user;
	}

	/**
	 * @return the id of the VM from Vcenter.
	 */
	public String getIdVmVcenter() {
		return idVmVcenter;
	}

	/**
	 * @param idVmVcenter the id of the VM from Vcenter.
	 */
	public void setIdVmVcenter(String idVmVcenter) {
		this.idVmVcenter = idVmVcenter;
	}

	/**
	 * @return the name of the VM.
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the new name for the VM.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the description of the VM.
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * @param desc the new description for the VM.
	 */
	public void setDescription(String desc) {
		this.description = desc;
	}
	
	/**
	 * @return isTemplate : true if the vm is a template.
	 */
	public boolean isTemplate() {
		return template;
	}

	/**
     * @param template true if the vm is a template.
	 */
	public void setTemplate(boolean template) {
		this.template = template;
	}

	// endregion

	// region Functions

	@Override
	public boolean equals(Object obj) {
		return super.equals(obj);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

	/**
	 * Return the http result after querying VSphere to change the power state of the VM.
	 * 
	 * @param str the action to do on the VM among "start, stop, reset, suspend"
	 * @return the http result after the call was made at VSphere
	 */
	public HttpResult vsphereSetPower(String str){
		return VSphereConnector.setVmPower(this, str);
	}
	
	/**
	 * Check read permission on the VM for the user.
	 * 
	 * @param userTest the user to check
	 * @return true if the user has the read permission, false otherwise
	 */
	public boolean hasAccessRead(User userTest){
		return (userTest.equals(this.user) || userTest.getType().greaterThan(user.getType()) || userTest.getType() == UserType.ADMIN);
	}

	/**
	 * Check write permission on the VM for the user.
	 *
	 * @param userTest the user to check
	 * @return true if the user has the write permission, false otherwise
	 */
	public boolean hasAccessWrite(User userTest){
		return (userTest.equals(this.user) || userTest.getType() == UserType.ADMIN);
	}

    /**
     * Check if the name is valid.
     * @param name the name to check
     * @return true if the name is valid else false
     */
	public boolean valid(String name){
        return name.length() <= 64 && name.length() > 0;
    }
	

	/**
	 * Find a VM by its id.
	 * 
	 * @param id the id to find
	 * @return the group containing the VM or null if not found
	 */
	public static Vm findById(int id) {
		return VSquareObject.findById(id, Vm.class);
	}
	

	/**
	 * Find a VM by its vCenter id.
	 * 
	 * @param idVmVcenter the id to find
	 * @return the group containing the VM or null if not found
	 */
	public static Vm findByIdVmVcenter(String idVmVcenter) {
		return DatabaseManager.getFirstFromSessionQuery("FROM " + Vm.class.getSimpleName() + " WHERE id_vm_vcenter = ?0", idVmVcenter);
	}
	
	/**
	 * Get all template.
     *
     * @return all templates from the table
	 */
	public static List<Vm> getAllTemplate() {
		return DatabaseManager.getRowsFromSessionQuery("FROM " + Vm.class.getSimpleName() + " WHERE template = 1");
	}


	/**
	 * @return all VMs from the table
	 */
	public static List<Vm> getAll() {
		return VSquareObject.getAll(Vm.class);
	}

	@Override
	public JSONObject toJSON(boolean detailed) {
		JSONObject json = super.toJSON(detailed);
		json.put("name", name);
        json.put("desc", description);
        json.put("vm", idVmVcenter);
		json.put("user", getUser().toJSON());
		return json;
	}
	
	// endregion

}
