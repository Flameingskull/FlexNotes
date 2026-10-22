package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun theBackupRecordRoundTripsAndBelongsToItsFolder() {
        val entries = mapOf(2L to ExportRecord.Entry("10:20:30", "2026-10-16_front.jpg"), 1L to ExportRecord.Entry("1:2:3", "undated_photo.png"))
        val text = ExportRecord.encode("content://tree/backups", entries)
        assertEquals(entries, ExportRecord.parse(text, "content://tree/backups"))
        // Another folder starts again, so a new backup folder gets every photo once.
        assertEquals(emptyMap<Long, ExportRecord.Entry>(), ExportRecord.parse(text, "content://tree/other"))
        assertEquals(emptyMap<Long, ExportRecord.Entry>(), ExportRecord.parse(null, "content://tree/backups"))
        // A damaged line is dropped, the rest kept.
        assertEquals(setOf(2L), ExportRecord.parse("content://tree/backups\n2\ta\tb.jpg\nx\ty\tz\n3\t\tc.jpg", "content://tree/backups").keys)
    }

    @Test
    fun onlyNewOrChangedPhotosAreDue() {
        val a = Photo(1, "a.jpg", "2026-10-16", null, DateSources.EXIF, "Front", null, null)
        val b = Photo(2, "b.jpg", "2026-10-16", null, DateSources.EXIF, "Side", null, null)
        val c = Photo(3, "c.jpg", "2026-10-16", null, DateSources.EXIF, "Back", null, null)
        val missing = Photo(4, "d.jpg", "2026-10-16", null, DateSources.EXIF, "Back", null, null)
        val sig = mapOf(1L to ExportRecord.signature(a, 100, 5), 2L to ExportRecord.signature(b, 100, 5), 3L to ExportRecord.signature(c, 100, 5))
        val record = mapOf(
            1L to ExportRecord.Entry(sig.getValue(1L), "2026-10-16_front.jpg"),
            2L to ExportRecord.Entry("old", "2026-10-16_side.jpg")
        )
        // a is unchanged, b changed since its copy, c is new, and the missing file is never due.
        assertEquals(listOf(2L, 3L), ExportRecord.due(listOf(a, b, c, missing), record) { sig[it.id] }.map { it.id })
    }

    @Test
    fun aSignatureChangesWithTheFileOrWhatItsDetailsCarry() {
        val p = Photo(1, "a.jpg", "2026-10-16", "2026-10-16T07:45", DateSources.EXIF, "Front", null, null)
        val base = ExportRecord.signature(p, 100, 5)
        assertEquals(base, ExportRecord.signature(p.copy(originalName = "IMG_1.jpg"), 100, 5))
        assertNotEquals(base, ExportRecord.signature(p, 101, 5))
        assertNotEquals(base, ExportRecord.signature(p, 100, 6))
        assertNotEquals(base, ExportRecord.signature(p.copy(pose = "Side"), 100, 5))
        assertNotEquals(base, ExportRecord.signature(p.copy(date = "2026-10-15"), 100, 5))
        assertNotEquals(base, ExportRecord.signature(p.copy(note = "Cut week 4"), 100, 5))
    }
}
