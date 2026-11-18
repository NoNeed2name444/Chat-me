package com.cramdown.sorta.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S07b A4: the shared permission table (addons/conformance/permissions.json), the same rows the harness and iOS run. */
class AddonPermissionsTest {
    private val rows = AddonTestFiles.json("permissions.json")["rows"]?.array ?: emptyList()

    @Test fun everyRowOfTheSharedTableGivesItsAnswer() {
        assertTrue("the table has its rows", rows.size >= 600)
        for (r in rows) {
            val grants = r["grants"]?.strings ?: emptyList()
            val netOn = r["net"]?.bool != false
            val answer = r["has"]?.string?.let { AddonPermissions.has(grants, it, netOn) }
                ?: AddonPermissions.allows(grants, r["call"]?.string ?: "", r["host"]?.string, netOn)
            assertEquals(r.canonical, r["allowed"]?.bool, answer)
        }
    }

    @Test fun aFolderGrantSeesItsFolderAndTheFoldersInsideIt() {
        val snap = AddonSnapshot.read(AddonTestFiles.json("store.json"))
        val work = AddonTestFiles.id(901)
        val view = AddonView(snap, listOf(AddonPermissions.FOLDER_GRANT + work))
        assertEquals(setOf(work, AddonTestFiles.id(902)), view.folders)
        assertTrue(view.ideas.isNotEmpty() && view.ideas.all { it.folder in view.folders })
        // a link to an idea outside the grant is not shown
        for (i in view.ideas) assertTrue(view.links.getValue(i.id).all { it in view.ids })
        assertEquals(snap.ideas.size, AddonView(snap, listOf("ideas.read")).ideas.size)
        assertTrue(AddonView(snap, listOf("cards", "commands")).ideas.isEmpty())
    }
}
