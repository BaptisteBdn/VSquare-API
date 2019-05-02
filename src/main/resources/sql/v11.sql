CREATE TABLE `link_network_group` (
    `id_network` INT NOT NULL,
	`id_group` INT NOT NULL,
    PRIMARY KEY (`id_network`, `id_group`),
	CONSTRAINT `FK_GroupNetworkLink` FOREIGN KEY (`id_group`) REFERENCES `user_group`(`id`) ON DELETE CASCADE,
	CONSTRAINT `FK_NetworkLink` FOREIGN KEY (`id_network`) REFERENCES `network`(`id`) ON DELETE CASCADE
);

ALTER TABLE `network` DROP FOREIGN KEY `FK_NetworkGroup`;
ALTER TABLE `network` DROP COLUMN `id_group`;
ALTER TABLE `event_log` MODIFY COLUMN `object_type` ENUM('USER','GROUP','VM','NETWORK') NOT NULL;