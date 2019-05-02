CREATE TABLE `link_template_group` (
    `id_vm` INT NOT NULL,
	`id_group` INT NOT NULL,
    PRIMARY KEY (`id_vm`, `id_group`),
	CONSTRAINT `FK_GroupLinkTemplate` FOREIGN KEY (`id_group`) REFERENCES `user_group`(`id`) ON DELETE CASCADE,
	CONSTRAINT `FK_VmLinkTemplate` FOREIGN KEY (`id_vm`) REFERENCES `vm`(`id`) ON DELETE CASCADE
);