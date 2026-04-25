package eu.sancris.cititor.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class UploadRepo(private val configurare: Configurare) {
    private val api = ApiBuilder.build(configurare.urlBaza)

    suspend fun trimitePoza(fotografie: File, serial: String): Result<Long> {
        return try {
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val pozaBody = fotografie.asRequestBody(mediaType)
            val pozaPart = MultipartBody.Part.createFormData("poza", fotografie.name, pozaBody)
            val serialBody: RequestBody = serial.toRequestBody("text/plain".toMediaTypeOrNull())

            val raspuns = api.trimiteCitire(configurare.token, pozaPart, serialBody)
            if (raspuns.isSuccessful) {
                Result.success(raspuns.body()?.id ?: 0L)
            } else {
                Result.failure(IllegalStateException("HTTP ${raspuns.code()} — ${raspuns.errorBody()?.string()}"))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun ping(): Result<String> {
        return try {
            val raspuns = api.ping(configurare.token)
            if (raspuns.isSuccessful) {
                Result.success(raspuns.body()?.cititor ?: "")
            } else {
                Result.failure(IllegalStateException("HTTP ${raspuns.code()}"))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
