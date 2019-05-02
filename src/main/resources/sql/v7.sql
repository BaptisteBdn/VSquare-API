CREATE TABLE `snapshot` (
    `id` INT NOT NULL AUTO_INCREMENT,
	`creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	`id_vm` INT(64) NOT NULL,
    `name` VARCHAR(64) NOT NULL,
   	`description` VARCHAR(500) NOT NULL,
    `id_snapshot_vcenter` VARCHAR(64) NOT NULL,
   	`id_parent` INT,
    PRIMARY KEY (`id`),
	CONSTRAINT `FK_SnapshotGroup` FOREIGN KEY (`id_vm`) REFERENCES `vm`(`id`) ON DELETE CASCADE,
	CONSTRAINT `FK_SnapshotParent` FOREIGN KEY (`id_parent`) REFERENCES `snapshot`(`id`) ON DELETE SET NULL
);