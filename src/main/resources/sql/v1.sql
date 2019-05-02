CREATE TABLE `user_group` (
    `id` INT NOT NULL AUTO_INCREMENT,
	`creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	`id_parent_group` INT,
    `name` VARCHAR(64) NOT NULL,
    `description` TEXT NOT NULL,
    PRIMARY KEY (`id`),
	CONSTRAINT `FK_ParentGroup` FOREIGN KEY (`id_parent_group`) REFERENCES `user_group`(`id`) ON DELETE CASCADE
);

CREATE TABLE `user` (
    `id` INT NOT NULL AUTO_INCREMENT,
	`creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	`login` VARCHAR(64) NOT NULL,
    `type` ENUM('STUDENT','REFERENT','ADMIN') NOT NULL,
	`id_group` INT,
    `common_name` VARCHAR(256) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `UK_UserLogin` UNIQUE (`login`),
	CONSTRAINT `FK_GroupUser` FOREIGN KEY (`id_group`) REFERENCES `user_group`(`id`) ON DELETE CASCADE
);

CREATE TABLE `vm` (
    `id` INT NOT NULL AUTO_INCREMENT,
	`creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	`id_user` INT NOT NULL,
    `id_vm_vcenter` VARCHAR(50) NOT NULL,
    `name` VARCHAR(64) NOT NULL,
    `description` TEXT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `UK_VmIdVmcenter` UNIQUE (`id_vm_vcenter`),
	CONSTRAINT `FK_UserVm` FOREIGN KEY (`id_user`) REFERENCES `user`(`id`) ON DELETE CASCADE
);

CREATE TABLE `token` (
    `id` INT NOT NULL AUTO_INCREMENT,
	`creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	`id_user` INT(64) NOT NULL,
    `value` VARCHAR(64) NOT NULL,
    PRIMARY KEY (`id`),
	CONSTRAINT `FK_UserToken` FOREIGN KEY (`id_user`) REFERENCES `user`(`id`) ON DELETE CASCADE
);

CREATE TABLE `db_info` (
	`version` SMALLINT NOT NULL,
	`update_date` TIMESTAMP NOT NULL
);

INSERT INTO `db_info` VALUES (0, CURRENT_TIMESTAMP());