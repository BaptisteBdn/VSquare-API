package fr.eseo.vsquare.model;

import fr.eseo.vsquare.utils.DatabaseManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.persistence.*;
import java.util.HashMap;
import java.util.List;


/**
 * Snapshot object model.
 * 
 * Implements the Snapshot object class as defined in the domain.
 * 
 * @author Clément Loiselet
 */

@Entity
@Table(name = "snapshot")
public class Snapshot extends VSquareObject {

	// region Variables
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "id_vm")
	private Vm vm;

	@Column(name = "id_snapshot_vcenter")
	private String idSnapshotVcenter;

	@Column(name = "name")
	private String name;

	@Column(name = "description")
	private String description;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_parent")
	private Snapshot parent;

	// endregion

	// region Constructors

	public Snapshot() {}
	
	/**
	 * Constructor 
	 * 
	 * @param vm the vm owning the snapshot
	 * @param idSnapshotVc the id defined in vCenter
	 * @param name the name of the VM
	 * @param desc the description of the VM
	 */
	public Snapshot(Vm vm, String idSnapshotVc, String name, String desc) {
		this(vm, idSnapshotVc, name, desc, null);
	}

	/**
	 * Constructor 
	 * 
	 * @param vm the vm owning the snapshot
	 * @param idSnapshotVc the id defined in vCenter
	 * @param name the name of the VM
	 * @param desc the description of the VM
	 * @param parent the parent snapshot
	 */
	public Snapshot(Vm vm, String idSnapshotVc, String name, String desc, Snapshot parent) {
		super();
		this.vm = vm;
		this.idSnapshotVcenter = idSnapshotVc;
		this.name = name;
		this.description = desc;
		this.parent = parent;
	}

	// endregion

	// region Accessors

	/**
	 * @return user owning the VM
	 */
	public Vm getVm() {
		return vm;
	}

	/**
	 * @param vm the new owner of the Snapshot
	 */
	public void setVm(Vm vm) {
		this.vm = vm;
	}

	/**
	 * @return the id of the Snapshot from Vcenter
	 */
	public String getIdSnapshotVcenter() {
		return idSnapshotVcenter;
	}

	/**
	 * @param idSnapshotVcenter the id of the Snapshot from Vcenter
	 */
	public void setIdSnapshotVcenter(String idSnapshotVcenter) {
		this.idSnapshotVcenter = idSnapshotVcenter;
	}

	/**
	 * @return the name of the Snapshot
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the new name for the snapshot
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the description of the snapshot
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * @param desc the new description for the snapshot
	 */
	public void setDescription(String desc) {
		this.description = desc;
	}
	
	/**
	 * @return the parent snapshot of this snapshot. can be null
	 */
	public Snapshot getParent(){
		return parent;
	}
	
	/**
	 * @param parent the parent snapshot of this snapshot. can be null
	 */
	public void setParent(Snapshot parent){
		this.parent = parent;
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
	 * Check read permission on the Snapshot for the user.
	 * 
	 * @param userTest the user to check
	 * @return true if the user has the read permission, false otherwise
	 */
	public boolean hasAccessRead(User userTest){
		return vm != null && vm.hasAccessRead(userTest);
	}

	/**
	 * Check write permission on the Snapshot for the user.
	 *
	 * @param userTest the user to check
	 * @return true if the user has the write permission, false otherwise
	 */
	public boolean hasAccessWrite(User userTest){
		return vm != null && vm.hasAccessWrite(userTest);
	}

	/**
     * @return all direct children of this snapshot
	 */
	public List<Snapshot> getChildren(){
		HashMap<String, Object> params = new HashMap<>();
		params.put("id_parent", this.getId());
		return DatabaseManager.getRowsFromSessionQueryNamed("FROM Snapshot WHERE id_parent=:id_parent",
				params);
	}
	
	/**
	 * Find a Snapshot by its id.
	 * 
	 * @param id the id to find
	 * @return the group containing the Snapshot or null if not found
	 */
	public static Snapshot findById(int id) {
		return VSquareObject.findById(id, Snapshot.class);
	}
	

	/**
	 * Find a Snapshot by its vCenter id.
	 * 
	 * @param idSnapshotVcenter the id to find
	 * @return the group containing the VM or null if not found
	 */
	public static Snapshot findByIdSnapshotVcenter(String idSnapshotVcenter) {
		return DatabaseManager.getFirstFromSessionQuery("FROM " + Snapshot.class.getSimpleName() + " WHERE id_snapshot_vcenter = ?0", idSnapshotVcenter);
	}
	
	/**
	 * get the snapshots associated to a VM
     * @param vm the source Vm
	 * @return Snapshot 
	 */
	public static List<Snapshot> getSnapshotsForVm(Vm vm){
		HashMap<String, Object> params = new HashMap<>();
		params.put("id_vm", vm.getId());
		return DatabaseManager.getRowsFromSessionQueryNamed("FROM Snapshot WHERE id_vm=:id_vm",
				params);
	}

	/**
	 * @return all Snapshot from the table
	 */
	public static List<Snapshot> getAll() {
		return VSquareObject.getAll(Snapshot.class);
	}

	@Override
	public JSONObject toJSON(boolean detailed) {
		JSONObject json = super.toJSON(detailed);
		json.put("name", name);
        json.put("description", description);
		json.put("id_vcenter", idSnapshotVcenter);
		
        if (vm != null)
        	json.put("vm", vm.toJSON());
		if (detailed){
			JSONArray children = new JSONArray();
			for (Snapshot s:getChildren()){
				children.put(s.toJSON());
			}
			json.put("children", children);
		}
		return json;
	}
	
	// endregion

}
