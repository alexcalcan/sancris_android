package eu.sancris.cititor.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.configStore: DataStore<Preferences> by preferencesDataStore(name = "configurare")

private object Chei {
    val URL_BAZA = stringPreferencesKey("url_baza")
    val TOKEN = stringPreferencesKey("token")
    val NUME = stringPreferencesKey("nume")
}

data class Configurare(
    val urlBaza: String,
    val token: String,
    val nume: String,
)

class ConfigurareRepo(private val context: Context) {

    val configurareFlow: Flow<Configurare?> = context.configStore.data.map { prefs ->
        val urlBaza = prefs[Chei.URL_BAZA] ?: return@map null
        val token = prefs[Chei.TOKEN] ?: return@map null
        val nume = prefs[Chei.NUME] ?: ""
        Configurare(urlBaza = urlBaza, token = token, nume = nume)
    }

    suspend fun salveaza(c: Configurare) {
        context.configStore.edit { prefs ->
            prefs[Chei.URL_BAZA] = c.urlBaza
            prefs[Chei.TOKEN] = c.token
            prefs[Chei.NUME] = c.nume
        }
    }

    suspend fun sterge() {
        context.configStore.edit { it.clear() }
    }

    /**
     * Parseaza un URI de forma `sancris://config?base=<url>&token=<t>&nume=<n>`
     * (codat in QR-ul afisat de Filament la crearea unui cititor).
     */
    fun parseazaUriConfigurare(uriString: String): Configurare? {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme != "sancris" || uri.host != "config") return null
            val base = uri.getQueryParameter("base")?.trimEnd('/') ?: return null
            val token = uri.getQueryParameter("token") ?: return null
            val nume = uri.getQueryParameter("nume") ?: ""
            Configurare(urlBaza = base, token = token, nume = nume)
        } catch (e: Throwable) {
            null
        }
    }
}
