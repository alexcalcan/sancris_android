package eu.sancris.cititor.data

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.GET

@Serializable
data class PingResponse(val ok: Boolean, val cititor: String)

@Serializable
data class CitireResponse(val ok: Boolean, val id: Long, val serial: String)

@Serializable
data class ContorDeCititDto(val serial: String, val descriere: String)

@Serializable
data class ContoareDeCititResponse(val contoare: List<ContorDeCititDto>)

interface SancrisApi {
    @GET("api/cititori/ping")
    suspend fun ping(@Header("X-Cititor-Token") token: String): Response<PingResponse>

    @Multipart
    @POST("api/cititori/citire")
    suspend fun trimiteCitire(
        @Header("X-Cititor-Token") token: String,
        @Part poza: MultipartBody.Part,
        @Part("serial") serial: RequestBody,
    ): Response<CitireResponse>

    @GET("api/cititori/contoare-de-citit")
    suspend fun contoareDeCitit(
        @Header("X-Cititor-Token") token: String,
    ): Response<ContoareDeCititResponse>
}
