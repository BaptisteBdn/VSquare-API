CREATE TABLE `link_user_group` (
    `id_user` INT NOT NULL,
	`id_group` INT NOT NULL,
    PRIMARY KEY (`id_user`, `id_group`),
	CONSTRAINT `FK_GroupLink` FOREIGN KEY (`id_group`) REFERENCES `user_group`(`id`) ON DELETE CASCADE,
	CONSTRAINT `FK_UserLink` FOREIGN KEY (`id_user`) REFERENCES `user`(`id`) ON DELETE CASCADE
);

ALTER TABLE `user` DROP FOREIGN KEY `FK_GroupUser`;
ALTER TABLE `user` DROP COLUMN `id_group`;
ALTER TABLE `user_group` ADD CONSTRAINT `UK_GroupName` UNIQUE (`name`);