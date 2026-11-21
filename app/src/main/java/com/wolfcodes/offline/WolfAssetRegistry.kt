package com.wolfcodes.offline

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

enum class WolfAssetCategory {
    CHARACTER, CREATURE, ENVIRONMENT, PROP, TEXTURE, MATERIAL, ANIMATION, AUDIO, FONT, SHADER, GENERATOR
}

enum class WolfAssetLifecycle {
    CANDIDATE, UNDER_REVIEW, APPROVED, REJECTED, QUARANTINED, RETIRED
}

enum class WolfAssetVisualStyle {
    CINEMATIC_REALISTIC, SCOPED_STYLIZED, CARTOON, TOY_LIKE, COMIC, UNKNOWN
}

enum class WolfAssetBudgetStatus { PASS, PENDING, FAIL }

data class WolfAssetFile(
    val relativePath: String,
    val format: String,
    val byteSize: Long,
    val sha256: String
)

data class WolfAssetLicenseEvidence(
    val creatorOrRightsHolder: String,
    val sourceName: String,
    val sourceLocation: String,
    val licenseName: String,
    val licenseVersion: String,
    val evidenceReference: String,
    val verified: Boolean,
    val commercialUse: Boolean,
    val modificationAllowed: Boolean,
    val redistributionAllowed: Boolean,
    val appBundlingAllowed: Boolean,
    val attributionRequired: Boolean,
    val attributionText: String,
    val restrictions: String
)

data class WolfAssetReview(
    val reviewer: String,
    val reviewedAtEpochMillis: Long,
    val decisionReason: String,
    val rightsPassed: Boolean,
    val visualPassed: Boolean,
    val technicalPassed: Boolean,
    val realismScore: Int,
    val visualStyle: WolfAssetVisualStyle,
    val approvedStyleScopes: Set<String>
)

data class WolfAssetRegistryEntry(
    val assetId: String,
    val name: String,
    val version: String,
    val category: WolfAssetCategory,
    val tags: Set<String>,
    val lifecycle: WolfAssetLifecycle,
    val license: WolfAssetLicenseEvidence,
    val files: List<WolfAssetFile>,
    val dependencies: Set<String>,
    val compatibleRuntimes: Map<String, String>,
    val approvedScopes: Set<String>,
    val budgetStatus: WolfAssetBudgetStatus,
    val review: WolfAssetReview
)

data class WolfAssetUseRequest(
    val targetRuntime: String,
    val intendedScope: String
)

data class WolfAssetGateResult(
    val approved: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

/**
 * Verifies that a manifest entry resolves to a real, packaged local file and
 * that its exact byte length and SHA-256 match the reviewed manifest.
 */
class WolfAssetFileVerifier(private val packagedAssetRoot: File) {
    fun verify(file: WolfAssetFile): Boolean {
        if (!isSafeRelativePath(file.relativePath)) return false
        return runCatching {
            val root = packagedAssetRoot.canonicalFile
            val candidate = File(root, file.relativePath).canonicalFile
            if (!candidate.path.startsWith(root.path + File.separator)) return false
            if (!candidate.isFile || candidate.length() != file.byteSize) return false
            sha256(candidate) == file.sha256.lowercase()
        }.getOrDefault(false)
    }

    companion object {
        fun isSafeRelativePath(path: String): Boolean {
            if (path.isBlank() || path.startsWith("/") || path.startsWith("\\") ||
                path.contains("\\") || path.contains(":")
            ) return false
            val segments = path.split("/")
            return segments.all { it.isNotBlank() && it != "." && it != ".." }
        }

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * Fail-closed quality gate. Only explicitly APPROVED entries that pass every
 * mandatory rights, realism, review, scope, dependency and packaged-file check
 * can be returned to a runtime consumer.
 */
class WolfAssetQualityGate(
    private val entries: Map<String, WolfAssetRegistryEntry>,
    private val fileVerifier: WolfAssetFileVerifier
) {
    fun evaluate(entry: WolfAssetRegistryEntry, request: WolfAssetUseRequest): WolfAssetGateResult =
        evaluateInternal(entry, request, linkedSetOf())

    fun approvedAssets(request: WolfAssetUseRequest): List<WolfAssetRegistryEntry> =
        entries.values.filter { evaluate(it, request).approved }

    private fun evaluateInternal(
        entry: WolfAssetRegistryEntry,
        request: WolfAssetUseRequest,
        dependencyPath: MutableSet<String>
    ): WolfAssetGateResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        fun requireValue(condition: Boolean, message: String) {
            if (!condition) errors += message
        }

        requireValue(entry.assetId.matches(Regex("wolf_[a-z0-9][a-z0-9._-]{2,63}")), "معرّف الأصل غير صالح")
        requireValue(entry.name.isNotBlank(), "اسم الأصل مفقود")
        requireValue(entry.version.isNotBlank(), "إصدار الأصل مفقود")
        requireValue(entry.lifecycle == WolfAssetLifecycle.APPROVED, "حالة الأصل ليست APPROVED")
        requireValue(request.targetRuntime.isNotBlank(), "بيئة التشغيل المطلوبة غير محددة")
        requireValue(request.intendedScope.isNotBlank(), "نطاق الاستخدام المطلوب غير محدد")

        val license = entry.license
        requireValue(license.creatorOrRightsHolder.isNotBlank(), "صاحب الحقوق غير موثق")
        requireValue(license.sourceName.isNotBlank() && license.sourceLocation.isNotBlank(), "مصدر الأصل غير موثق")
        requireValue(license.licenseName.isNotBlank() && license.licenseVersion.isNotBlank(), "اسم الترخيص أو إصداره مفقود")
        requireValue(license.evidenceReference.isNotBlank() && license.verified, "دليل الترخيص غير موثق أو غير متحقق")
        requireValue(license.commercialUse, "الاستخدام التجاري غير مسموح أو غير مثبت")
        requireValue(license.modificationAllowed, "التعديل غير مسموح أو غير مثبت")
        requireValue(license.redistributionAllowed, "إعادة التوزيع غير مسموحة أو غير مثبتة")
        requireValue(license.appBundlingAllowed, "تضمين الأصل داخل التطبيق غير مسموح أو غير مثبت")
        if (license.attributionRequired) {
            requireValue(license.attributionText.isNotBlank(), "نص الإسناد الإلزامي مفقود")
        }

        val blockedTags = setOf("cartoon", "cartoonish", "chibi", "toy-like", "comic", "mascot", "humorous", "comical", "primitive-human")
        requireValue(entry.tags.none { it.trim().lowercase() in blockedTags }, "وسم بصري مرفوض لأسلوب WOLF السينمائي")
        when (entry.review.visualStyle) {
            WolfAssetVisualStyle.CINEMATIC_REALISTIC -> Unit
            WolfAssetVisualStyle.SCOPED_STYLIZED -> requireValue(
                request.intendedScope in entry.review.approvedStyleScopes,
                "الأسلوب غير الواقعي غير معتمد لهذا النطاق المحدد"
            )
            else -> errors += "النمط البصري غير مقبول للرعب السينمائي الواقعي"
        }

        val review = entry.review
        requireValue(review.reviewer.isNotBlank(), "اسم المراجع مفقود")
        requireValue(review.reviewedAtEpochMillis > 0L, "تاريخ المراجعة مفقود")
        requireValue(review.decisionReason.isNotBlank(), "سبب قرار المراجعة مفقود")
        requireValue(review.rightsPassed, "مراجعة الحقوق لم تجتز")
        requireValue(review.visualPassed, "مراجعة الجودة البصرية لم تجتز")
        requireValue(review.technicalPassed, "المراجعة التقنية لم تجتز")
        requireValue(review.realismScore in 4..5, "درجة الواقعية أقل من الحد المعتمد (4/5)")

        requireValue(request.intendedScope in entry.approvedScopes, "الأصل غير معتمد لنطاق الاستخدام المطلوب")
        requireValue(
            !entry.compatibleRuntimes[request.targetRuntime].isNullOrBlank(),
            "لا يوجد إثبات توافق مختبر مع بيئة التشغيل المطلوبة"
        )

        requireValue(entry.files.isNotEmpty(), "قائمة ملفات الأصل فارغة")
        entry.files.forEach { file ->
            requireValue(WolfAssetFileVerifier.isSafeRelativePath(file.relativePath), "مسار ملف غير آمن: ${file.relativePath}")
            requireValue(file.format.isNotBlank(), "صيغة ملف غير محددة: ${file.relativePath}")
            requireValue(file.byteSize > 0L, "حجم ملف غير صالح: ${file.relativePath}")
            requireValue(file.sha256.matches(Regex("[a-fA-F0-9]{64}")), "بصمة SHA-256 غير صالحة: ${file.relativePath}")
            requireValue(fileVerifier.verify(file), "الملف المعبأ أو حجمه أو بصمته لا يطابق السجل: ${file.relativePath}")
        }

        if (!dependencyPath.add(entry.assetId)) {
            errors += "تم اكتشاف دورة في اعتماديات الأصول: ${(dependencyPath + entry.assetId).joinToString(" -> ")}"
        } else {
            entry.dependencies.forEach { dependencyId ->
                val dependency = entries[dependencyId]
                if (dependency == null) {
                    errors += "اعتمادية غير موجودة: $dependencyId"
                } else {
                    val dependencyResult = evaluateInternal(dependency, request, dependencyPath)
                    if (!dependencyResult.approved) {
                        errors += "اعتمادية غير معتمدة: $dependencyId"
                    }
                }
            }
            dependencyPath.remove(entry.assetId)
        }

        when (entry.budgetStatus) {
            WolfAssetBudgetStatus.PASS -> Unit
            WolfAssetBudgetStatus.PENDING -> warnings += "قياسات/ميزانية الأداء ما زالت معلقة؛ لا تُعد هذه الموافقة اعتمادًا لميزانية الجهاز النهائي"
            WolfAssetBudgetStatus.FAIL -> errors += "الأصل تجاوز ميزانية الموارد المسجلة"
        }

        return WolfAssetGateResult(errors.isEmpty(), errors.distinct(), warnings.distinct())
    }
}

/** In-memory immutable registry loaded from the application-bundled manifest. */
class WolfAssetRegistry private constructor(
    val schemaVersion: Int,
    entries: List<WolfAssetRegistryEntry>,
    private val fileVerifier: WolfAssetFileVerifier
) {
    private val entriesById: Map<String, WolfAssetRegistryEntry> = entries.associateBy { it.assetId }
    val size: Int get() = entriesById.size

    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "إصدار مخطط سجل الأصول غير مدعوم: $schemaVersion" }
        require(entriesById.size == entries.size) { "معرّفات أصول مكررة داخل السجل" }
    }

    fun find(assetId: String): WolfAssetRegistryEntry? = entriesById[assetId]

    fun evaluate(assetId: String, request: WolfAssetUseRequest): WolfAssetGateResult {
        val entry = entriesById[assetId]
            ?: return WolfAssetGateResult(false, listOf("الأصل غير موجود في السجل"), emptyList())
        return WolfAssetQualityGate(entriesById, fileVerifier).evaluate(entry, request)
    }

    fun approvedAssets(request: WolfAssetUseRequest): List<WolfAssetRegistryEntry> =
        WolfAssetQualityGate(entriesById, fileVerifier).approvedAssets(request)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun fromJson(json: String, fileVerifier: WolfAssetFileVerifier): WolfAssetRegistry {
            val root = JSONObject(json)
            val version = root.getInt("schemaVersion")
            val array = root.getJSONArray("assets")
            val entries = (0 until array.length()).map { index -> parseEntry(array.getJSONObject(index)) }
            return WolfAssetRegistry(version, entries, fileVerifier)
        }

        fun loadBundled(context: Context): WolfAssetRegistry {
            val json = context.assets.open("wolf_asset_registry.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            return fromJson(json, WolfAssetFileVerifier(File(context.applicationInfo.sourceDir).parentFile
                ?.resolve("assets") ?: context.filesDir))
        }

        private fun parseEntry(item: JSONObject): WolfAssetRegistryEntry {
            val license = item.getJSONObject("license")
            val review = item.getJSONObject("review")
            val files = item.getJSONArray("files")
            val dependencies = item.optJSONArray("dependencies") ?: JSONArray()
            val tags = item.optJSONArray("tags") ?: JSONArray()
            val scopes = item.optJSONArray("approvedScopes") ?: JSONArray()
            val styleScopes = review.optJSONArray("approvedStyleScopes") ?: JSONArray()
            val runtimes = item.optJSONObject("compatibleRuntimes") ?: JSONObject()
            return WolfAssetRegistryEntry(
                assetId = item.getString("assetId"),
                name = item.getString("name"),
                version = item.getString("version"),
                category = enumValueOf(item.getString("category")),
                tags = (0 until tags.length()).map { tags.getString(it) }.toSet(),
                lifecycle = enumValueOf(item.getString("lifecycle")),
                license = WolfAssetLicenseEvidence(
                    creatorOrRightsHolder = license.optString("creatorOrRightsHolder"),
                    sourceName = license.optString("sourceName"),
                    sourceLocation = license.optString("sourceLocation"),
                    licenseName = license.optString("licenseName"),
                    licenseVersion = license.optString("licenseVersion"),
                    evidenceReference = license.optString("evidenceReference"),
                    verified = license.optBoolean("verified"),
                    commercialUse = license.optBoolean("commercialUse"),
                    modificationAllowed = license.optBoolean("modificationAllowed"),
                    redistributionAllowed = license.optBoolean("redistributionAllowed"),
                    appBundlingAllowed = license.optBoolean("appBundlingAllowed"),
                    attributionRequired = license.optBoolean("attributionRequired"),
                    attributionText = license.optString("attributionText"),
                    restrictions = license.optString("restrictions")
                ),
                files = (0 until files.length()).map { i ->
                    val file = files.getJSONObject(i)
                    WolfAssetFile(
                        relativePath = file.getString("relativePath"),
                        format = file.getString("format"),
                        byteSize = file.getLong("byteSize"),
                        sha256 = file.getString("sha256")
                    )
                },
                dependencies = (0 until dependencies.length()).map { dependencies.getString(it) }.toSet(),
                compatibleRuntimes = buildMap {
                    val keys = runtimes.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, runtimes.getString(key))
                    }
                },
                approvedScopes = (0 until scopes.length()).map { scopes.getString(it) }.toSet(),
                budgetStatus = enumValueOf(item.getString("budgetStatus")),
                review = WolfAssetReview(
                    reviewer = review.optString("reviewer"),
                    reviewedAtEpochMillis = review.optLong("reviewedAtEpochMillis"),
                    decisionReason = review.optString("decisionReason"),
                    rightsPassed = review.optBoolean("rightsPassed"),
                    visualPassed = review.optBoolean("visualPassed"),
                    technicalPassed = review.optBoolean("technicalPassed"),
                    realismScore = review.optInt("realismScore"),
                    visualStyle = enumValueOf(review.getString("visualStyle")),
                    approvedStyleScopes = (0 until styleScopes.length()).map { styleScopes.getString(it) }.toSet()
                )
            )
        }
    }
}
