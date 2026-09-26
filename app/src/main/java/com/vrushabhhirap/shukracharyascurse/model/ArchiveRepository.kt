package com.vrushabhhirap.shukracharyascurse.model

import java.io.Serializable
import java.util.Collections
import java.util.Locale

/**
 * Data repository representing the sacred archival documents preserved in the Ashrama.
 * Provides lookup, search, classification filtering, pagination, and sorting for lore records.
 */
class ArchiveRepository private constructor() {

    companion object {
        @Volatile
        private var sInstance: ArchiveRepository? = null

        fun getInstance(): ArchiveRepository {
            return sInstance ?: synchronized(this) {
                sInstance ?: ArchiveRepository().also { sInstance = it }
            }
        }
    }

    enum class Classification(val displayName: String, val level: Int) {
        PUBLIC("Public Manuscript", 1),
        RESTRICTED("Restricted Ashrama Study", 2),
        CLASSIFIED("Classified Samhita", 3),
        SEALED("Sealed Tantric Formula", 4),
        FORBIDDEN("Forbidden Sigils of Ushanas", 5)
    }

    enum class SortOrder {
        TITLE_ASC,
        TITLE_DESC,
        SECTIONS_DESC,
        CATALOG_ASC
    }

    data class ArchiveRecord(
        val id: String,
        val catalogNumber: String,
        val title: String,
        val author: String,
        val period: String,
        val classification: Classification,
        val excerpt: String,
        val totalSections: Int,
        val sealVerified: Boolean,
        val tags: List<String> = emptyList(),
        val preservationRating: Int = 95
    ) : Serializable {
        fun isAccessibleBy(maxLevel: Int): Boolean = classification.level <= maxLevel
    }

    data class PageResult<T>(
        val items: List<T>,
        val pageIndex: Int,
        val pageSize: Int,
        val totalCount: Int,
        val totalPages: Int
    )

    private val records = mutableListOf<ArchiveRecord>()

    init {
        seedInitialCatalogue()
    }

    private fun seedInitialCatalogue() {
        records.add(
            ArchiveRecord(
                id = "ARC-001",
                catalogNumber = "MS-RIG-8821",
                title = "Vedic Hymns to the Dawn (Ushas)",
                author = "Sage Vamadeva",
                period = "Early Vedic",
                classification = Classification.PUBLIC,
                excerpt = "Bright leader of glad days, dawn shines forth with ancient radiance across the celestial canopy.",
                totalSections = 12,
                sealVerified = true,
                tags = listOf("Hymns", "Cosmology", "Solar"),
                preservationRating = 98
            )
        )
        records.add(
            ArchiveRecord(
                id = "ARC-002",
                catalogNumber = "MS-BHR-1049",
                title = "The Lineage of the Bhrigus",
                author = "Chyavana Mahamuni",
                period = "Mid Vedic",
                classification = Classification.RESTRICTED,
                excerpt = "From Bhrigu came the masters of fire and planetary lore, presiding over the auspicious transitions of Shukra.",
                totalSections = 8,
                sealVerified = true,
                tags = listOf("Bhrigu", "Genealogy", "Planetary"),
                preservationRating = 92
            )
        )
        records.add(
            ArchiveRecord(
                id = "ARC-003",
                catalogNumber = "MS-SAN-0091",
                title = "Treatise on Mrita-Sanjivani Vidya",
                author = "Acharya Shukracharya",
                period = "Pre-Mahabharata",
                classification = Classification.SEALED,
                excerpt = "By invocation of the primordial mantra and the infusion of purified herbs, the departed life-breath returns to mortal clay.",
                totalSections = 16,
                sealVerified = true,
                tags = listOf("Sanjivani", "Resurrection", "Mahadeva"),
                preservationRating = 88
            )
        )
        records.add(
            ArchiveRecord(
                id = "ARC-004",
                catalogNumber = "MS-KAC-0312",
                title = "The Ordeal of Kacha at the Ashrama",
                author = "Brihaspati Samhita Compiler",
                period = "Treta Yuga",
                classification = Classification.RESTRICTED,
                excerpt = "Three times slain by the Asuras and three times restored by the guru's unbounded compassion.",
                totalSections = 6,
                sealVerified = true,
                tags = listOf("Kacha", "Discipleship", "Ordeal"),
                preservationRating = 94
            )
        )
        records.add(
            ArchiveRecord(
                id = "ARC-005",
                catalogNumber = "MS-DEV-9901",
                title = "The Final Utterance of Devayani",
                author = "Devayani Devi",
                period = "Treta Yuga",
                classification = Classification.CLASSIFIED,
                excerpt = "What was acquired through deceit shall not bear the fruit of divine union; the lore remains unbroken yet bound.",
                totalSections = 4,
                sealVerified = true,
                tags = listOf("Devayani", "Curse", "Lineage"),
                preservationRating = 96
            )
        )
        records.add(
            ArchiveRecord(
                id = "ARC-006",
                catalogNumber = "MS-SHU-9999",
                title = "The Forbidden Sigils of the Asura Realm",
                author = "Ushanas (Shukracharya)",
                period = "Primordial",
                classification = Classification.FORBIDDEN,
                excerpt = "None but the pure of intent shall pierce the veil of the nine checkpoints and gaze upon the inner flame.",
                totalSections = 9,
                sealVerified = false,
                tags = listOf("Sigils", "Forbidden", "Checkpoints"),
                preservationRating = 74
            )
        )
        records.add(
            ArchiveRecord(
                id = "ARC-007",
                catalogNumber = "MS-AST-4410",
                title = "Treatise on Astronomical Conjunctions",
                author = "Garga Muni",
                period = "Late Vedic",
                classification = Classification.PUBLIC,
                excerpt = "When Shukra enters the sixth house of the ecliptic, the occult properties of sacred soma herbs reach maximum potency.",
                totalSections = 14,
                sealVerified = true,
                tags = listOf("Astronomy", "Herbs", "Planets"),
                preservationRating = 91
            )
        )
        records.add(
            ArchiveRecord(
                id = "ARC-008",
                catalogNumber = "MS-OSH-7702",
                title = "The Himalayan Herbal Pharmacopeia",
                author = "Dhanvantari Samhita Disciples",
                period = "Treta Yuga",
                classification = Classification.RESTRICTED,
                excerpt = "Identification of celestial flora: Sanjivani, Vishalyakarani, Sandhakarani, and Savarnyakarani.",
                totalSections = 20,
                sealVerified = true,
                tags = listOf("Herbs", "Pharmacopeia", "Healing"),
                preservationRating = 89
            )
        )
    }

    fun getAllRecords(): List<ArchiveRecord> {
        return Collections.unmodifiableList(records)
    }

    fun getRecordById(id: String): ArchiveRecord? {
        return records.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    fun searchRecords(query: String): List<ArchiveRecord> {
        val q = query.lowercase(Locale.getDefault()).trim()
        if (q.isEmpty()) return getAllRecords()
        return records.filter {
            it.title.lowercase(Locale.getDefault()).contains(q) ||
            it.author.lowercase(Locale.getDefault()).contains(q) ||
            it.catalogNumber.lowercase(Locale.getDefault()).contains(q) ||
            it.excerpt.lowercase(Locale.getDefault()).contains(q) ||
            it.tags.any { tag -> tag.lowercase(Locale.getDefault()).contains(q) }
        }
    }

    fun getRecordsByClassification(classification: Classification): List<ArchiveRecord> {
        return records.filter { it.classification == classification }
    }

    fun getRecordsByTag(tag: String): List<ArchiveRecord> {
        val t = tag.lowercase(Locale.getDefault())
        return records.filter { it.tags.any { tg -> tg.lowercase(Locale.getDefault()) == t } }
    }

    fun getSortedRecords(order: SortOrder): List<ArchiveRecord> {
        return when (order) {
            SortOrder.TITLE_ASC -> records.sortedBy { it.title }
            SortOrder.TITLE_DESC -> records.sortedByDescending { it.title }
            SortOrder.SECTIONS_DESC -> records.sortedByDescending { it.totalSections }
            SortOrder.CATALOG_ASC -> records.sortedBy { it.catalogNumber }
        }
    }

    fun getPagedRecords(page: Int, pageSize: Int, filterClassification: Classification? = null): PageResult<ArchiveRecord> {
        val filtered = if (filterClassification != null) {
            records.filter { it.classification == filterClassification }
        } else {
            records
        }

        val totalCount = filtered.size
        val totalPages = (totalCount + pageSize - 1) / pageSize
        val safePage = page.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        val startIndex = safePage * pageSize
        val endIndex = (startIndex + pageSize).coerceAtMost(totalCount)

        val items = if (startIndex < totalCount) {
            filtered.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        return PageResult(items, safePage, pageSize, totalCount, totalPages)
    }

    fun getAveragePreservationScore(): Double {
        if (records.isEmpty()) return 0.0
        return records.map { it.preservationRating }.average()
    }

    fun getAllTags(): Set<String> {
        return records.flatMap { it.tags }.toSet()
    }

    fun getRecordCount(): Int = records.size
}
