package com.keltruc.mymemos.database

/** DDL of schema version 1, lifted from schemas/1.json so the spike can build a v1 file
 *  the way a real Android install would have left it. */
val V1_DDL: List<String> = listOf(
    """CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `serverUrl` TEXT NOT NULL, `userResourceName` TEXT NOT NULL, `username` TEXT NOT NULL, `displayName` TEXT NOT NULL, `avatarUrl` TEXT NOT NULL, `role` TEXT NOT NULL, `authMethod` TEXT NOT NULL, `serverVersion` TEXT NOT NULL, `lastSyncEpochMs` INTEGER, `isActive` INTEGER NOT NULL)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_serverUrl_userResourceName` ON `accounts` (`serverUrl`, `userResourceName`)""",
    """CREATE TABLE IF NOT EXISTS `memos` (`localId` TEXT NOT NULL, `accountId` INTEGER NOT NULL, `remoteName` TEXT, `creator` TEXT, `content` TEXT NOT NULL, `visibility` TEXT NOT NULL, `state` TEXT NOT NULL, `pinned` INTEGER NOT NULL, `tagsJoined` TEXT NOT NULL, `createTimeEpochMs` INTEGER NOT NULL, `updateTimeEpochMs` INTEGER NOT NULL, `snippet` TEXT NOT NULL, `hasTaskList` INTEGER NOT NULL, `hasIncompleteTasks` INTEGER NOT NULL, `hasLink` INTEGER NOT NULL, `hasCode` INTEGER NOT NULL, `locationPlaceholder` TEXT, `latitude` REAL, `longitude` REAL, `syncStatus` TEXT NOT NULL, `baseUpdateTimeEpochMs` INTEGER, PRIMARY KEY(`localId`), FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE INDEX IF NOT EXISTS `index_memos_accountId` ON `memos` (`accountId`)""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_memos_accountId_remoteName` ON `memos` (`accountId`, `remoteName`)""",
    """CREATE INDEX IF NOT EXISTS `index_memos_accountId_state_pinned_createTimeEpochMs` ON `memos` (`accountId`, `state`, `pinned`, `createTimeEpochMs`)""",
    """CREATE INDEX IF NOT EXISTS `index_memos_syncStatus` ON `memos` (`syncStatus`)""",
    """CREATE VIRTUAL TABLE IF NOT EXISTS `memos_fts` USING FTS4(`localId` TEXT NOT NULL, `content` TEXT NOT NULL, `tagsJoined` TEXT NOT NULL, content=`memos`)""",
    """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memos_fts_BEFORE_UPDATE BEFORE UPDATE ON `memos` BEGIN DELETE FROM `memos_fts` WHERE `docid`=OLD.`rowid`; END""",
    """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memos_fts_BEFORE_DELETE BEFORE DELETE ON `memos` BEGIN DELETE FROM `memos_fts` WHERE `docid`=OLD.`rowid`; END""",
    """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memos_fts_AFTER_UPDATE AFTER UPDATE ON `memos` BEGIN INSERT INTO `memos_fts`(`docid`, `localId`, `content`, `tagsJoined`) VALUES (NEW.`rowid`, NEW.`localId`, NEW.`content`, NEW.`tagsJoined`); END""",
    """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memos_fts_AFTER_INSERT AFTER INSERT ON `memos` BEGIN INSERT INTO `memos_fts`(`docid`, `localId`, `content`, `tagsJoined`) VALUES (NEW.`rowid`, NEW.`localId`, NEW.`content`, NEW.`tagsJoined`); END""",
    """CREATE TABLE IF NOT EXISTS `attachments` (`localId` TEXT NOT NULL, `memoLocalId` TEXT NOT NULL, `remoteName` TEXT, `filename` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `externalLink` TEXT, `localPath` TEXT, `createTimeEpochMs` INTEGER NOT NULL, PRIMARY KEY(`localId`), FOREIGN KEY(`memoLocalId`) REFERENCES `memos`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE INDEX IF NOT EXISTS `index_attachments_memoLocalId` ON `attachments` (`memoLocalId`)""",
    """CREATE INDEX IF NOT EXISTS `index_attachments_remoteName` ON `attachments` (`remoteName`)""",
    """CREATE TABLE IF NOT EXISTS `pending_ops` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER NOT NULL, `memoLocalId` TEXT NOT NULL, `type` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `lastError` TEXT, `failed` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE INDEX IF NOT EXISTS `index_pending_ops_accountId` ON `pending_ops` (`accountId`)""",
    """CREATE INDEX IF NOT EXISTS `index_pending_ops_memoLocalId` ON `pending_ops` (`memoLocalId`)""",
    """CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)""",
    """INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c8e6ee6cc2b922d8d542edf5d77a9352')""",
)
