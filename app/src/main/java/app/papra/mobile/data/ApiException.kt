package app.papra.mobile.data

import java.io.IOException

class ApiException(
    val statusCode: Int,
    message: String,
    val hint: String?
) : IOException(message)
