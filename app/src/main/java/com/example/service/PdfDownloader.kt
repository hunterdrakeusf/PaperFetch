package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class DownloadResult {
    data class Success(val file: File, val sizeBytes: Long) : DownloadResult()
    data class Error(val message: String, val statusCode: Int? = null) : DownloadResult()
}

class PdfDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    suspend fun downloadPdf(
        pdfUrl: String,
        doi: String,
        title: String,
        destinationDir: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            // Create safe filename
            val safeDoi = doi.replace("/", "_").replace(":", "_").replace("""[^\w.-]""".toRegex(), "_")
            val safeTitle = if (title.isNotBlank()) {
                title.take(40).replace("""[^\w\s-]""".toRegex(), "").trim().replace("""\s+""".toRegex(), "_") + "_"
            } else ""

            val fileName = "${safeTitle}${safeDoi}.pdf"
            val targetFile = File(destinationDir, fileName)

            val request = Request.Builder()
                .url(pdfUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/pdf,application/octet-stream,*/*")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DownloadResult.Error(
                        "Server returned HTTP ${response.code}: ${response.message}",
                        response.code
                    )
                }

                val responseBody = response.body
                    ?: return@withContext DownloadResult.Error("Empty response body from PDF server")

                val contentLength = responseBody.contentLength()
                val inputStream = responseBody.byteStream()
                val outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                onProgress(totalRead, contentLength)
                            }
                        }
                    }
                }

                // Verify file validity
                if (targetFile.length() < 1024) {
                    // Small file might be an HTML error page or paywall gate
                    val preview = targetFile.readBytes().take(200).toByteArray()
                    val text = String(preview)
                    if (text.contains("html", ignoreCase = true) || text.contains("<!doctype", ignoreCase = true)) {
                        targetFile.delete()
                        return@withContext DownloadResult.Error("Paywall / Landing page returned instead of direct PDF binary")
                    }
                }

                // Check magic bytes for PDF (%PDF-)
                if (targetFile.length() >= 4) {
                    val header = targetFile.inputStream().use { stream ->
                        val b = ByteArray(4)
                        stream.read(b)
                        String(b)
                    }
                    if (header != "%PDF") {
                        // Some servers prefix with whitespace or UTF-8 BOM, let's scan first 32 bytes
                        val first32 = targetFile.inputStream().use { stream ->
                            val b = ByteArray(32)
                            val read = stream.read(b)
                            String(b, 0, read)
                        }
                        if (!first32.contains("%PDF")) {
                            targetFile.delete()
                            return@withContext DownloadResult.Error("File signature mismatch (not a valid PDF format)")
                        }
                    }
                }

                DownloadResult.Success(targetFile, targetFile.length())
            }
        } catch (e: Exception) {
            DownloadResult.Error("Download failed: ${e.localizedMessage ?: e.message}")
        }
    }
}
