package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** Exported photo files (#195): their names never replace a file in the folder, and their dates follow FlexNotes's. */
class PhotoExportTest {

    private fun photo(date: String?, takenAt: String? = null, pose: String = "Front", file: String = "a1b2.jpg") =
        Photo(1, file, date, takenAt, DateSources.EXIF, pose, null, null)

    @Test
    fun namesAreDateAndPoseWithASuffixForRepeats() {
        val taken = mutableSetOf("2026-10-16_front.jpg")
        assertEquals("2026-10-16_front_2.jpg", ExportNames.fileName("2026-10-16", "Front", "jpg", taken))
        assertEquals("2026-10-16_front_3.jpg", ExportNames.fileName("2026-10-16", "Front", "jpg", taken))
        assertEquals("2026-10-16_side.jpg", ExportNames.fileName("2026-10-16", "Side", "jpg", taken))
        assertEquals("2026-10-16_photo.png", ExportNames.fileName("2026-10-16", "", "png", taken))
        assertEquals("undated_back.jpg", ExportNames.fileName(null, "Back", "jpg", taken))
        // A pose is matched whatever its capitals.
        assertEquals("2026-10-16_front_2.jpg", ExportNames.fileName("2026-10-16", "FRONT", "jpg", mutableSetOf("2026-10-16_front.jpg")))
    }

    @Test
    fun theExtensionAndTypeComeFromTheStoredFile() {
        assertEquals("jpg", ExportNames.extension("photos/abc.JPG"))
        assertEquals("heic", ExportNames.extension("abc.heic"))
        assertEquals("jpg", ExportNames.extension("abc"))
        assertEquals("image/png", ExportNames.mime("png"))
        assertEquals("image/jpeg", ExportNames.mime("jpeg"))
    }

    @Test
    fun theCaptureTimeIsTheFlexNotesDate() {
        // Its own time when it's on its FlexNotes date; midday when the photo was moved to another date.
        assertEquals(LocalDateTime.of(2026, 10, 16, 7, 45), ExportNames.takenAt(photo("2026-10-16", "2026-10-16T07:45")))
        assertEquals(LocalDateTime.of(2026, 10, 15, 12, 0), ExportNames.takenAt(photo("2026-10-15", "2026-10-16T07:45")))
        assertEquals(LocalDateTime.of(2026, 10, 15, 12, 0), ExportNames.takenAt(photo("2026-10-15")))
        assertNull(ExportNames.takenAt(photo(null)))
    }

    @Test
    fun aDateRangeIsInclusiveAndOldestFirst() {
        val photos = listOf(photo("2026-10-20"), photo("2026-10-01"), photo("2026-10-10"), photo(null))
        assertEquals(listOf("2026-10-01", "2026-10-10"), PhotoExport.inRange(photos, "2026-10-01", "2026-10-10").map { it.date })
    }
}
