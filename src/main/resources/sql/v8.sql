CREATE TABLE `download_link` (
    `id` INT NOT NULL AUTO_INCREMENT,
	`creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	`download_try` INT NOT NULL DEFAULT 0,
	`download_success` INT NOT NULL DEFAULT 0,
	`id_vm` INT(64) NOT NULL,
   	`external_link` VARCHAR(100) NOT NULL,
    `internal_link` VARCHAR(400) NOT NULL,
    PRIMARY KEY (`id`),
	CONSTRAINT `FK_downloadLinkVM` FOREIGN KEY (`id_vm`) REFERENCES `vm`(`id`) ON DELETE CASCADE
);