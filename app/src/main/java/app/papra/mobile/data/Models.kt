package app.papra.mobile.data

data class Organization(
    val id: String,
    val name: String
)

data class Document(
    val id: String,
    val name: String,
    val size: Long?,
    val createdAt: String?,
    val mimeType: String?,
    val tags: List<Tag>
)

data class Tag(
    val id: String,
    val name: String,
    val color: String?,
    val description: String?
)

data class OrganizationStats(
    val documentsCount: Int,
    val documentsSize: Long
)

data class ActivityEvent(
    val id: String,
    val type: String?,
    val createdAt: String?,
    val details: String
)
