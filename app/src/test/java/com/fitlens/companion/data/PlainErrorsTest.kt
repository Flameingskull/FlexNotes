package com.fitlens.companion.data

import com.fitlens.companion.data.PlainErrors.Cause
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

/** Failure messages in plain words (#274): a failure is sorted into a cause a person can act on. */
class PlainErrorsTest {
    @Test fun aFullPhoneIsNoSpace() {
        assertEquals(Cause.NO_SPACE, PlainErrors.classify(IOException("write failed: ENOSPC (No space left on device)")))
    }

    @Test fun aRefusedFolderIsPermission() {
        assertEquals(Cause.PERMISSION, PlainErrors.classify(SecurityException("Permission Denial: opening provider")))
        assertEquals(Cause.PERMISSION, PlainErrors.classify(FileNotFoundException("/storage/emulated/0/x (Permission denied)")))
    }

    @Test fun aMissingFileIsMissing() {
        assertEquals(Cause.MISSING, PlainErrors.classify(FileNotFoundException("/storage/emulated/0/x.flexnotes: open failed: ENOENT")))
    }

    @Test fun aDamagedArchiveIsUnreadable() {
        assertEquals(Cause.UNREADABLE, PlainErrors.classify(java.util.zip.ZipException("invalid entry size")))
    }

    @Test fun theCauseInsideAWrapperCounts() {
        assertEquals(Cause.NO_SPACE, PlainErrors.classify(RuntimeException("backup", IOException("No space left on device"))))
    }

    @Test fun anythingElseIsUnknownAndNullIsToo() {
        assertEquals(Cause.UNKNOWN, PlainErrors.classify(IllegalStateException("boom")))
        assertEquals(Cause.UNKNOWN, PlainErrors.classify(null))
    }
}
