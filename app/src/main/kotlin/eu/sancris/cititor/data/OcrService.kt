package eu.sancris.cititor.data

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

object OcrService {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Ruleaza ML Kit Text Recognition pe poza si extrage cea mai probabila
     * valoare numerica (cifrele de pe display-ul contorului). Returneaza null
     * daca nu gaseste nimic util.
     *
     * Heuristica simpla: cel mai lung sir de cifre, cu zero sau un singur
     * punct/virgula. Preferam pe cele cu zecimala. Filtru minim 3 cifre
     * (sub atat probabil sunt cifre dintr-un cod, dat, etc.).
     */
    suspend fun extrageValoare(fisier: File): String? = withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeFile(fisier.absolutePath) ?: return@withContext null
        val image = InputImage.fromBitmap(bitmap, 0)

        val text = suspendCancellableCoroutine<Text?> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return@withContext null

        text.textBlocks
            .asSequence()
            .flatMap { it.lines.asSequence() }
            .flatMap { Regex("\\d+[.,]?\\d*").findAll(it.text).map { m -> m.value } }
            .map { it.replace(',', '.') }
            .filter { it.replace(".", "").length >= 3 }
            .maxByOrNull { numar ->
                // Scor: lungime cifre + bonus daca are zecimala
                val cifre = numar.replace(".", "").length
                val bonusZecimala = if (numar.contains('.')) 1 else 0
                cifre * 10 + bonusZecimala
            }
    }
}
