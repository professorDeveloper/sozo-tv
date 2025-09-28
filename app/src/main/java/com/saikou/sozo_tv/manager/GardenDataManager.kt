package com.saikou.sozo_tv.manager

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saikou.sozo_tv.data.model.Category
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.data.model.Country
import com.saikou.sozo_tv.data.model.GitHubFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object GardenDataManager {
    private val client = OkHttpClient()
    private val gson = Gson()

    private val isoCountryNames = mapOf(
        "ad" to "Andorra",
        "ae" to "United Arab Emirates",
        "af" to "Afghanistan",
        "ag" to "Antigua and Barbuda",
        "al" to "Albania",
        "am" to "Armenia",
        "ao" to "Angola",
        "ar" to "Argentina",
        "at" to "Austria",
        "au" to "Australia",
        "aw" to "Aruba",
        "az" to "Azerbaijan",
        "ba" to "Bosnia and Herzegovina",
        "bd" to "Bangladesh",
        "be" to "Belgium",
        "bf" to "Burkina Faso",
        "bg" to "Bulgaria",
        "bh" to "Bahrain",
        "bj" to "Benin",
        "bm" to "Bermuda",
        "bn" to "Brunei Darussalam",
        "bo" to "Bolivia",
        "bq" to "Bonaire, Sint Eustatius and Saba",
        "br" to "Brazil",
        "bs" to "Bahamas",
        "by" to "Belarus",
        "ca" to "Canada",
        "cd" to "Congo, Democratic Republic of the",
        "ch" to "Switzerland",
        "ci" to "Côte d'Ivoire",
        "cl" to "Chile",
        "cm" to "Cameroon",
        "cn" to "China",
        "co" to "Colombia",
        "cr" to "Costa Rica",
        "cu" to "Cuba",
        "cv" to "Cabo Verde",
        "cw" to "Curaçao",
        "cy" to "Cyprus",
        "cz" to "Czechia",
        "de" to "Germany",
        "dj" to "Djibouti",
        "dk" to "Denmark",
        "do" to "Dominican Republic",
        "dz" to "Algeria",
        "ec" to "Ecuador",
        "ee" to "Estonia",
        "eg" to "Egypt",
        "eh" to "Western Sahara",
        "er" to "Eritrea",
        "es" to "Spain",
        "et" to "Ethiopia",
        "fi" to "Finland",
        "fo" to "Faroe Islands",
        "fr" to "France",
        "ge" to "Georgia",
        "gh" to "Ghana",
        "gl" to "Greenland",
        "gm" to "Gambia",
        "gn" to "Guinea",
        "gp" to "Guadeloupe",
        "gq" to "Equatorial Guinea",
        "gr" to "Greece",
        "gt" to "Guatemala",
        "gu" to "Guam",
        "gy" to "Guyana",
        "hk" to "Hong Kong",
        "hn" to "Honduras",
        "hr" to "Croatia",
        "ht" to "Haiti",
        "hu" to "Hungary",
        "id" to "Indonesia",
        "ie" to "Ireland",
        "il" to "Israel",
        "in" to "India",
        "iq" to "Iraq",
        "ir" to "Iran",
        "is" to "Iceland",
        "it" to "Italy",
        "jm" to "Jamaica",
        "jo" to "Jordan",
        "jp" to "Japan",
        "ke" to "Kenya",
        "kh" to "Cambodia",
        "kn" to "Saint Kitts and Nevis",
        "kr" to "Korea, Republic of",
        "kw" to "Kuwait",
        "kz" to "Kazakhstan",
        "la" to "Lao People's Democratic Republic",
        "lb" to "Lebanon",
        "lc" to "Saint Lucia",
        "lk" to "Sri Lanka",
        "lt" to "Lithuania",
        "lu" to "Luxembourg",
        "lv" to "Latvia",
        "ly" to "Libya",
        "ma" to "Morocco",
        "mc" to "Monaco",
        "md" to "Moldova",
        "me" to "Montenegro",
        "mk" to "North Macedonia",
        "ml" to "Mali",
        "mm" to "Myanmar",
        "mn" to "Mongolia",
        "mq" to "Martinique",
        "mt" to "Malta",
        "mu" to "Mauritius",
        "mv" to "Maldives",
        "mx" to "Mexico",
        "my" to "Malaysia",
        "mz" to "Mozambique",
        "na" to "Namibia",
        "ng" to "Nigeria",
        "ni" to "Nicaragua",
        "nl" to "Netherlands",
        "no" to "Norway",
        "np" to "Nepal",
        "nz" to "New Zealand",
        "om" to "Oman",
        "pa" to "Panama",
        "pe" to "Peru",
        "pf" to "French Polynesia",
        "ph" to "Philippines",
        "pk" to "Pakistan",
        "pl" to "Poland",
        "pr" to "Puerto Rico",
        "ps" to "Palestine",
        "pt" to "Portugal",
        "py" to "Paraguay",
        "qa" to "Qatar",
        "ro" to "Romania",
        "rs" to "Serbia",
        "ru" to "Russian Federation",
        "rw" to "Rwanda",
        "sa" to "Saudi Arabia",
        "sd" to "Sudan",
        "se" to "Sweden",
        "sg" to "Singapore",
        "si" to "Slovenia",
        "sk" to "Slovakia",
        "sm" to "San Marino",
        "sn" to "Senegal",
        "so" to "Somalia",
        "sr" to "Suriname",
        "sv" to "El Salvador",
        "sx" to "Sint Maarten",
        "sy" to "Syrian Arab Republic",
        "td" to "Chad",
        "tg" to "Togo",
        "th" to "Thailand",
        "tj" to "Tajikistan",
        "tn" to "Tunisia",
        "tr" to "Türkiye",
        "tt" to "Trinidad and Tobago",
        "tw" to "Taiwan",
        "ua" to "Ukraine",
        "ug" to "Uganda",
        "uk" to "United Kingdom",
        "us" to "United States",
        "uy" to "Uruguay",
        "uz" to "Uzbekistan",
        "ve" to "Venezuela",
        "vg" to "Virgin Islands (British)",
        "vi" to "Virgin Islands (U.S.)",
        "vn" to "Viet Nam",
        "ws" to "Samoa",
        "xk" to "Kosovo",
        "ye" to "Yemen",
        "za" to "South Africa",
        "zw" to "Zimbabwe"
    )

    suspend fun loadCategoriesFromApi(): List<Category> = withContext(Dispatchers.IO) {
        val apiUrl =
            "https://api.github.com/repos/professorDeveloper/tv-garden-channel-list/contents/channels/raw/categories"
        val request = Request.Builder().url(apiUrl).build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response code: ${response.code}")
                }

                val json = response.body?.string() ?: throw IOException("Empty response body")

                val type = object : TypeToken<List<GitHubFile>>() {}.type
                val files: List<GitHubFile> = gson.fromJson(json, type) ?: emptyList()

                files.filter { it.type == "file" && it.name.endsWith(".json") && it.name != "countries_metadata.json" }
                    .map { file ->
                        val key = file.name.removeSuffix(".json")
                        val name = key.split("-")
                            .joinToString(" ") { it.capitalize() }  // e.g., "all-channels" -> "All Channels"
                        Category(key, name)
                    }.sortedBy { it.name }
            }
        } catch (e: Exception) {
            println("Error loading categories from API: ${e.message}. Returning empty list.")
            emptyList()
        }
    }

    suspend fun loadCountriesFromApi(): List<Country> = withContext(Dispatchers.IO) {
        val apiUrl =
            "https://api.github.com/repos/professorDeveloper/tv-garden-channel-list/contents/channels/raw/countries"
        val request = Request.Builder().url(apiUrl).build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response code: ${response.code}")
                }

                val json = response.body?.string() ?: throw IOException("Empty response body")

                val type = object : TypeToken<List<GitHubFile>>() {}.type
                val files: List<GitHubFile> = gson.fromJson(json, type) ?: emptyList()

                files.filter { it.type == "file" && it.name.endsWith(".json") }
                    .map { file ->
                        val code = file.name.removeSuffix(".json").lowercase()
                        val name = isoCountryNames[code]
                            ?: code.uppercase()  // Fallback to uppercase code if no name
                        Country(code, name)
                    }.sortedBy { it.name }
            }
        } catch (e: Exception) {
            println("Error loading countries from API: ${e.message}. Returning empty list.")
            emptyList()
        }
    }

    suspend fun loadChannelsForCountry(countryCode: String): List<Channel> =
        withContext(Dispatchers.IO) {
            val url =
                "https://raw.githubusercontent.com/professorDeveloper/tv-garden-channel-list/main/channels/raw/countries/${countryCode.lowercase()}.json"
            val request = Request.Builder().url(url).build()

            return@withContext try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response code: ${response.code}")
                    }

                    val json = response.body?.string() ?: throw IOException("Empty response body")

                    if (json.trim().isEmpty()) {
                        throw IOException("JSON content is empty")
                    }

                    val type = object : TypeToken<List<Channel>>() {}.type
                    gson.fromJson(json, type) ?: emptyList()
                }
            } catch (e: Exception) {
                println("Error loading channels for $countryCode: ${e.message}. Returning empty list.")
                emptyList()
            }
        }

    suspend fun loadChannelsForCategory(categoryKey: String): List<Channel> =
        withContext(Dispatchers.IO) {
            val url =
                "https://raw.githubusercontent.com/professorDeveloper/tv-garden-channel-list/main/channels/raw/categories/${categoryKey.lowercase()}.json"
            val request = Request.Builder().url(url).build()

            return@withContext try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response code: ${response.code}")
                    }

                    val json = response.body?.string() ?: throw IOException("Empty response body")

                    if (json.trim().isEmpty()) {
                        throw IOException("JSON content is empty")
                    }

                    val type = object : TypeToken<List<Channel>>() {}.type
                    gson.fromJson(json, type) ?: emptyList()
                }
            } catch (e: Exception) {
                println("Error loading channels for category $categoryKey: ${e.message}. Returning empty list.")
                emptyList()
            }
        }
}