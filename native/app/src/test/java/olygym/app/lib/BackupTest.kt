package olygym.app.lib

import org.junit.Assert.assertEquals
import org.junit.Test

/* The name the snapshot lands under in Documents, which is the promise the Settings row makes. */

class BackupTest {

    @Test
    fun theSnapshotIsNamedForItsDay() {
        assertEquals("opengym-backup-2026-10-07.json", backupFileName("2026-10-07"))
    }

    @Test
    fun anotherDayIsAnotherFile() {
        assertEquals("opengym-backup-2026-10-08.json", backupFileName("2026-10-08"))
    }
}
