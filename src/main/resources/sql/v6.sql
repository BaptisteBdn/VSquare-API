CREATE TABLE `network` (
    `id` INT NOT NULL AUTO_INCREMENT,
	`creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	`id_group` INT(64) NOT NULL,
    `name` VARCHAR(64) NOT NULL,
    `id_network_vcenter` VARCHAR(64) NOT NULL,
    PRIMARY KEY (`id`),
	CONSTRAINT `FK_NetworkGroup` FOREIGN KEY (`id_group`) REFERENCES `user_group`(`id`) ON DELETE CASCADE
);

ALTER TABLE `user` ADD COLUMN `id_network_vcenter` VARCHAR(64);