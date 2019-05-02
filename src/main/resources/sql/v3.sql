CREATE TABLE `event_log` (
    `id` INT NOT NULL AUTO_INCREMENT,
	`creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	`id_user` INT(64) NOT NULL,
    `action` ENUM('CREATE','EDIT','DELETE', 'POWER_ON', 'POWER_OFF', 'RESET', 'SUSPEND') NOT NULL,
    `object_type` ENUM('USER','GROUP','VM') NOT NULL,
    `object_id` INT(64) NOT NULL,
    `object_name` VARCHAR(64) NOT NULL,
    PRIMARY KEY (`id`),
	CONSTRAINT `FK_UserEventLog` FOREIGN KEY (`id_user`) REFERENCES `user`(`id`) ON DELETE CASCADE
);

CREATE TABLE `error_log` (
    `id` INT NOT NULL AUTO_INCREMENT,
	`creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	`id_user` INT(64),
	`error` TEXT NOT NULL,
    PRIMARY KEY (`id`),
	CONSTRAINT `FK_UserErrorLog` FOREIGN KEY (`id_user`) REFERENCES `user`(`id`) ON DELETE CASCADE
);