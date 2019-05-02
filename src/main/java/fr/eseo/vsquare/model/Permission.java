package fr.eseo.vsquare.model;

import fr.eseo.vsquare.utils.Utils;
import org.json.JSONObject;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.List;

/**
 * Permission object model.
 * 
 * Implements the permission object defining the max values for each user.
 * A permission is associated with a group, and cascades from a parent to a child.
 *
 * @author Pierre PINEL
 */

@Entity
@Table(name = "permission")
public class Permission extends VSquareObject {

	/* Getting default value for permissions as defined in the configuration file */
    private static final int DEFAULT_VM_COUNT = Utils.getInt("default_vm_count");
    private static final int DEFAULT_CPU_COUNT = Utils.getInt("default_cpu_count");
    private static final int DEFAULT_MEMORY_SIZE = Utils.getInt("default_memory_size");
    private static final int DEFAULT_DISK_STORAGE = Utils.getInt("default_disk_storage");

    private static final int MINIMAL_VM_COUNT = Utils.getInt("minimal_vm_count");
    private static final int MINIMAL_CPU_COUNT = Utils.getInt("minimal_cpu_count");
    private static final int MINIMAL_MEMORY_SIZE = Utils.getInt("minimal_memory_size");
    private static final int MINIMAL_DISK_STORAGE = Utils.getInt("minimal_disk_storage");

	// region Variables

	@Column(name = "vm_count")
	private int vmCount;

	@Column(name = "cpu_count")
	private int cpuCount;

	@Column(name = "memory_size_MiB")
	private int memorySize;

	@Column(name = "disk_storage_MiB")
	private int diskStorage;

	// endregion

	// region Constructors

	public Permission() {
		this(Permission.DEFAULT_VM_COUNT, Permission.DEFAULT_CPU_COUNT, Permission.DEFAULT_MEMORY_SIZE,
				Permission.DEFAULT_DISK_STORAGE);
	}

	/**
	 * Constructor
	 * 
	 * @param vmCount the max number of VMs
	 * @param cpuCount the max number of CPUs
	 * @param memorySize the max number of RAM in MiB
	 * @param diskStorage the max number of storage in MiB
	 */
	public Permission(int vmCount, int cpuCount, int memorySize, int diskStorage) {
		super();
		this.vmCount = vmCount;
		this.cpuCount = cpuCount;
		this.memorySize = memorySize;
		this.diskStorage = diskStorage;
	}

	// endregion

	// region Accessors

	/**
	 * @return VM number limit
	 */
	public int getVmCount() {
		return vmCount;
	}

	/**
	 * @param vmCount the VM number limit
	 */
	public void setVmCount(int vmCount) {
		this.vmCount = vmCount;
	}

	/**
	 * @return CPU number limit
	 */
	public int getCpuCount() {
		return cpuCount;
	}

	/**
	 * @param cpuCount the CPU number limit
	 */
	public void setCpuCount(int cpuCount) {
		this.cpuCount = cpuCount;
	}

	/**
	 * @return RAM size limit in MiB
	 */
	public int getMemorySize() {
		return memorySize;
	}

	/**
	 * @param memorySize the RAM size limit in MiB
	 */
	public void setMemorySize(int memorySize) {
		this.memorySize = memorySize;
	}

	/**
	 * @return storage space in MIB
	 */
	public int getDiskStorage() {
		return diskStorage;
	}

	/**
	 * @param diskStorage the storage space in MiB
	 */
	public void setDiskStorage(int diskStorage) {
		this.diskStorage = diskStorage;
	}

	// endregion

	// region Functions

	/**
	 * Validate the values of a permission.
	 * 
	 * @return boolean true for a valid permission, false otherwise 
	 */
	public boolean isValid() {
        return vmCount >= MINIMAL_VM_COUNT && cpuCount >= MINIMAL_CPU_COUNT &&
                memorySize >= MINIMAL_MEMORY_SIZE && diskStorage >= MINIMAL_DISK_STORAGE;
	}

	@Override
	public boolean equals(Object obj) {
		return super.equals(obj);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

	/**
	 * Get the max value for each field between two permissions.
	 * 
	 * @param other the permission to compare with
	 * @return permission the permission with greatest value for each field
	 */
	public Permission getMax(Permission other) {
        int cpuMax;
        int vmCountMax;
        int memoryMax;
        int diskMax;
		if (other.getCpuCount() > this.cpuCount)
			cpuMax = other.getCpuCount();
		else
			cpuMax = this.cpuCount;
		if (other.getMemorySize() > this.memorySize)
			memoryMax = other.getMemorySize();
		else
			memoryMax = this.memorySize;
		if (other.getVmCount() > this.vmCount)
			vmCountMax = other.getVmCount();
		else
			vmCountMax = this.vmCount;
		if (other.getDiskStorage() > this.diskStorage)
			diskMax = other.getDiskStorage();
		else
			diskMax = this.diskStorage;
		return new Permission(vmCountMax, cpuMax, memoryMax, diskMax);
	}

	/**
	 * Gives the minimal permission as defined in the config file.
	 * 
	 * @return minimal permission
	 */
	public static Permission getMinimalPermission() {
        return new Permission(MINIMAL_VM_COUNT, MINIMAL_CPU_COUNT,
                MINIMAL_MEMORY_SIZE, MINIMAL_DISK_STORAGE);
	}

	/**
	 * Find a permission by its id.
	 * 
	 * @param id the id to search
	 * @return the permission or null if not found
	 */
	public static Permission findById(int id) {
		return VSquareObject.findById(id, Permission.class);
	}

	/**
	 * Get all permissions from the table.
	 * 
	 * @return all the permissions from the database
	 */
	public static List<Permission> getAll() {
		return VSquareObject.getAll(Permission.class);
	}

	@Override
    public JSONObject toJSON(boolean detailed) {
        JSONObject json = super.toJSON(detailed);
		json.put("vm_count", this.getVmCount());
		json.put("cpu_count", this.getCpuCount());
		json.put("memory_size", this.getMemorySize());
		json.put("disk_storage", this.getDiskStorage());
		return json;
	}

	// endregion

}
