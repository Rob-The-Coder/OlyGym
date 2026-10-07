package olygym.app.lib

/*
 * The auto-backup's own name -- the port of the path writeAutoBackup builds in
 * frontend/src/lib/mobile.js. One file per day, so a later trigger the same day overwrites it
 * instead of piling up copies in a folder somebody is syncing.
 */

fun backupFileName(iso: String): String = "opengym-backup-" + iso + ".json"
