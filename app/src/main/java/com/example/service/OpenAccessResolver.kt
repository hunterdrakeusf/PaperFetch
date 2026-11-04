package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ResolvedPaperInfo(
    val doi: String,
    val title: String,
    val authors: String,
    val journal: String,
    val year: Int?,
    val isOa: Boolean,
    val oaStatus: String, // gold, green, bronze, hybrid, closed
    val license: String?,
    val pdfUrl: String?,
    val landingPageUrl: String?,
    val sourceDescription: String
)

class OpenAccessResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    suspend fun resolve(doi: String): ResolvedPaperInfo = withContext(Dispatchers.IO) {
        val cleanDoi = doi.trim().lowercase()

        // 1. Try OpenAlex first (most comprehensive, includes gold, green, repository PDFs, licenses)
        try {
            val openAlexResult = queryOpenAlex(cleanDoi)
            if (openAlexResult != null) {
                // If OpenAlex returned OA but no direct PDF URL, try Europe PMC as fallback
                if (openAlexResult.pdfUrl.isNullOrBlank()) {
                    val epResult = queryEuropePmc(cleanDoi)
                    if (epResult != null && !epResult.pdfUrl.isNullOrBlank()) {
                        return@withContext openAlexResult.copy(
                            isOa = true,
                            oaStatus = if (openAlexResult.oaStatus == "closed") "green" else openAlexResult.oaStatus,
                            pdfUrl = epResult.pdfUrl,
                            sourceDescription = "Europe PMC (${epResult.sourceDescription})"
                        )
                    }
                }
                return@withContext openAlexResult
            }
        } catch (_: Exception) {
            // Proceed to secondary resolvers
        }

        // 2. Try Europe PMC fallback
        try {
            val epResult = queryEuropePmc(cleanDoi)
            if (epResult != null) {
                return@withContext epResult
            }
        } catch (_: Exception) {
            // Proceed to Crossref
        }

        // 3. Try Crossref fallback
        try {
            val crossrefResult = queryCrossref(cleanDoi)
            if (crossrefResult != null) {
                return@withContext crossrefResult
            }
        } catch (_: Exception) {
            // Ignore
        }

        // Default closed/paywalled info if no resolver found OA PDF
        ResolvedPaperInfo(
            doi = cleanDoi,
            title = "",
            authors = "",
            journal = "",
            year = null,
            isOa = false,
            oaStatus = "closed",
            license = null,
            pdfUrl = null,
            landingPageUrl = "https://doi.org/$cleanDoi",
            sourceDescription = "Closed / Paywalled"
        )
    }

    private fun queryOpenAlex(doi: String): ResolvedPaperInfo? {
        val encodedDoi = URLEncoder.encode("https://doi.org/$doi", "UTF-8")
        val url = "https://api.openalex.org/works/$encodedDoi?mailto=paperfetch.app@gmail.com"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PaperFetch/1.0 (Open-Access Research Downloader; mailto:paperfetch.app@gmail.com)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val title = json.optString("title", "").trim()
            val year = if (json.has("publication_year")) json.optInt("publication_year") else null

            // Authors
            val authorsList = mutableListOf<String>()
            val authorships = json.optJSONArray("authorships")
            if (authorships != null) {
                for (i in 0 until authorships.length()) {
                    val authObj = authorships.optJSONObject(i)
                    val authorInfo = authObj?.optJSONObject("author")
                    val name = authorInfo?.optString("display_name", "") ?: ""
                    if (name.isNotBlank()) authorsList.add(name)
                }
            }
            val authors = authorsList.take(4).joinToString(", ") + if (authorsList.size > 4) " et al." else ""

            // Journal / Venue
            var journal = ""
            val primaryLoc = json.optJSONObject("primary_location")
            val sourceObj = primaryLoc?.optJSONObject("source")
            if (sourceObj != null) {
                journal = sourceObj.optString("display_name", "")
            }

            // Open Access info
            val openAccessObj = json.optJSONObject("open_access")
            val isOa = openAccessObj?.optBoolean("is_oa", false) ?: false
            val oaStatus = openAccessObj?.optString("oa_status", if (isOa) "gold" else "closed") ?: "closed"
            val oaUrl = openAccessObj?.optString("oa_url", "")?.ifEmpty { null }

            // Best OA location
            var pdfUrl: String? = null
            var landingPageUrl = oaUrl
            var license: String? = null
            var sourceName = journal

            val bestOa = json.optJSONObject("best_oa_location")
            if (bestOa != null) {
                val candidatePdf = bestOa.optString("pdf_url", "")
                if (candidatePdf.isNotBlank() && candidatePdf != "null") {
                    pdfUrl = candidatePdf
                }
                val landing = bestOa.optString("landing_page_url", "")
                if (landing.isNotBlank() && landing != "null") {
                    landingPageUrl = landing
                }
                val lic = bestOa.optString("license", "")
                if (lic.isNotBlank() && lic != "null") {
                    license = lic
                }
                val bestSource = bestOa.optJSONObject("source")?.optString("display_name", "")
                if (!bestSource.isNullOrBlank()) {
                    sourceName = bestSource
                }
            }

            // Check primary_location if pdf_url wasn't in best_oa_location
            if (pdfUrl.isNullOrBlank() && primaryLoc != null) {
                val primaryPdf = primaryLoc.optString("pdf_url", "")
                if (primaryPdf.isNotBlank() && primaryPdf != "null") {
                    pdfUrl = primaryPdf
                }
            }

            // ArXiv fallback if DOI is an arXiv DOI
            if (pdfUrl.isNullOrBlank() && (doi.contains("arxiv", ignoreCase = true) || doi.startsWith("10.48550/"))) {
                val arxivId = doi.substringAfterLast("/")
                pdfUrl = "https://arxiv.org/pdf/$arxivId.pdf"
            }

            return ResolvedPaperInfo(
                doi = doi,
                title = title,
                authors = authors,
                journal = journal,
                year = year,
                isOa = isOa || !pdfUrl.isNullOrBlank(),
                oaStatus = oaStatus,
                license = license,
                pdfUrl = pdfUrl,
                landingPageUrl = landingPageUrl ?: "https://doi.org/$doi",
                sourceDescription = "OpenAlex ($sourceName)"
            )
        }
    }

    private fun queryEuropePmc(doi: String): ResolvedPaperInfo? {
        val encodedDoi = URLEncoder.encode("DOI:\"$doi\"", "UTF-8")
        val url = "https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=$encodedDoi&format=json&resultType=core"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PaperFetch/1.0 (Open-Access Research Downloader)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val resultList = json.optJSONObject("resultList")?.optJSONArray("result")
            if (resultList == null || resultList.length() == 0) return null

            val item = resultList.getJSONObject(0)
            val title = item.optString("title", "").trim()
            val authorString = item.optString("authorString", "")
            val journalTitle = item.optJSONObject("journalInfo")?.optJSONObject("journal")?.optString("title", "") ?: ""
            val year = item.optInt("pubYear", 0).takeIf { it > 0 }
            val hasPdf = item.optString("hasPDF", "N") == "Y"
            val pmcid = item.optString("pmcid", "")

            var pdfUrl: String? = null
            if (hasPdf && pmcid.isNotBlank()) {
                pdfUrl = "https://europepmc.org/backend/ptpmcrender.fcgi?accid=$pmcid&blobtype=pdf"
            }

            val isOa = item.optString("isOpenAccess", "N") == "Y" || hasPdf

            return ResolvedPaperInfo(
                doi = doi,
                title = title,
                authors = authorString,
                journal = journalTitle,
                year = year,
                isOa = isOa,
                oaStatus = if (isOa) "green" else "closed",
                license = item.optString("license", null),
                pdfUrl = pdfUrl,
                landingPageUrl = "https://europepmc.org/article/MED/${item.optString("id", "")}",
                sourceDescription = "Europe PMC"
            )
        }
    }

    private fun queryCrossref(doi: String): ResolvedPaperInfo? {
        val url = "https://api.crossref.org/works/$doi"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PaperFetch/1.0 (mailto:paperfetch.app@gmail.com)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body).optJSONObject("message") ?: return null

            val titleArray = json.optJSONArray("title")
            val title = titleArray?.optString(0, "") ?: ""
            val containerArray = json.optJSONArray("container-title")
            val journal = containerArray?.optString(0, "") ?: ""

            // Year
            var year: Int? = null
            val issued = json.optJSONObject("issued")?.optJSONArray("date-parts")
            if (issued != null && issued.length() > 0) {
                val dates = issued.optJSONArray(0)
                year = dates?.optInt(0, 0)?.takeIf { it > 0 }
            }

            // Authors
            val authorArr = json.optJSONArray("author")
            val authorsList = mutableListOf<String>()
            if (authorArr != null) {
                for (i in 0 until authorArr.length()) {
                    val a = authorArr.optJSONObject(i)
                    val given = a?.optString("given", "") ?: ""
                    val family = a?.optString("family", "") ?: ""
                    val fullName = "$given $family".trim()
                    if (fullName.isNotBlank()) authorsList.add(fullName)
                }
            }
            val authors = authorsList.take(4).joinToString(", ")

            var pdfUrl: String? = null
            val linkArr = json.optJSONArray("link")
            if (linkArr != null) {
                for (i in 0 until linkArr.length()) {
                    val linkObj = linkArr.optJSONObject(i)
                    val contentType = linkObj?.optString("content-type", "") ?: ""
                    val linkUrl = linkObj?.optString("URL", "") ?: ""
                    if (contentType.contains("pdf", ignoreCase = true) && linkUrl.isNotBlank()) {
                        pdfUrl = linkUrl
                        break
                    }
                }
            }

            return ResolvedPaperInfo(
                doi = doi,
                title = title,
                authors = authors,
                journal = journal,
                year = year,
                isOa = !pdfUrl.isNullOrBlank(),
                oaStatus = if (!pdfUrl.isNullOrBlank()) "hybrid" else "closed",
                license = null,
                pdfUrl = pdfUrl,
                landingPageUrl = "https://doi.org/$doi",
                sourceDescription = "Crossref"
            )
        }
    }
}
