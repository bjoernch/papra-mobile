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
    val mimeType: String?
)
