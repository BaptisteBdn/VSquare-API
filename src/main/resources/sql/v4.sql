CREATE TABLE `permission` (
    `id` INT NOT NULL AUTO_INCREMENT,
	`creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	`vm_count` INT NOT NULL,
	`cpu_count` INT NOT NULL,
	`memory_size_MiB` INT NOT NULL,
	`disk_storage_MiB` INT NOT NULL,
    PRIMARY KEY (`id`)
);

ALTER TABLE `user_group` ADD COLUMN `id_permission` INT;
ALTER TABLE `user_group` ADD CONSTRAINT `FK_Permission` FOREIGN KEY (`id_permission`) REFERENCES `permission`(`id`) ON DELETE CASCADE;