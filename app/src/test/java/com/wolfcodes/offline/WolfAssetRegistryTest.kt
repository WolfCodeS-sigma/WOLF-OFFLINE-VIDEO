package com.wolfcodes.offline

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class WolfAssetRegistryTest {
    private lateinit var root: File
    private lateinit var packagedFile: File
    private lateinit var manifestFile: WolfAssetFile
    private lateinit var verifier: WolfAssetFileVerifier

    @Before
    fun setUp() {
        root = Files.createTempDirectory("wolf-asset-gate").toFile()
        packagedFile = File(root, "cinematic/forest.bin").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        manifestFile = WolfAssetFile(
            relativePath = "cinematic/forest.bin",
            format = "application/octet-stream",
            byteSize = packagedFile.length(),
            sha256 = WolfAssetFileVerifier.sha256(packagedFile)
        )
        verifier = WolfAssetFileVerifier(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun completeRealisticLicensedAssetPassesAndPendingBudgetIsOnlyWarning() {
        val entry = validEntry()
        val result = WolfAssetQualityGate(mapOf(entry.assetId to entry), verifier)
            .evaluate(entry, request())

        assertTrue(result.approved)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun nonCommercialOrUnverifiedLicenseFailsClosed() {
        val entry = validEntry().copy(
            license = validEntry().license.copy(commercialUse = false, verified = false)
        )
        val result = gate(entry).evaluate(entry, request())

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("تجاري") })
        assertTrue(result.errors.any { it.contains("دليل الترخيص") })
    }

    @Test
    fun unregisteredOrAlteredEntryCannotBeApproved() {
        val registered = validEntry()
        val gate = gate(registered)
        val unregistered = validEntry().copy(assetId = "wolf_unregistered_forest")
        val alteredRegistered = registered.copy(name = "Altered outside registry")

        val unregisteredResult = gate.evaluate(unregistered, request())
        val alteredResult = gate.evaluate(alteredRegistered, request())

        assertFalse(unregisteredResult.approved)
        assertFalse(alteredResult.approved)
        assertTrue(unregisteredResult.errors.any { it.contains("غير مسجل") })
        assertTrue(alteredResult.errors.any { it.contains("لا تطابق") })
    }

    @Test
    fun missingDescriptiveTagsBlockApproval() {
        val entry = validEntry().copy(tags = emptySet())
        val result = gate(entry).evaluate(entry, request())

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("وسوم الأصل الوصفية") })
    }

    @Test
    fun cartoonAndToyLikeAssetsAreRejected() {
        val entry = validEntry().copy(
            tags = setOf("cinematic", "cartoon"),
            review = validEntry().review.copy(visualStyle = WolfAssetVisualStyle.CARTOON)
        )
        val result = gate(entry).evaluate(entry, request())

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("وسم بصري") })
        assertTrue(result.errors.any { it.contains("النمط البصري") })
    }

    @Test
    fun alteredPackagedBytesFailHashVerification() {
        packagedFile.writeBytes(byteArrayOf(9, 9, 9))
        val entry = validEntry()
        val result = gate(entry).evaluate(entry, request())

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("لا يطابق السجل") })
    }

    @Test
    fun traversalAndAbsolutePathsAreRejected() {
        assertFalse(WolfAssetFileVerifier.isSafeRelativePath("../outside.bin"))
        assertFalse(WolfAssetFileVerifier.isSafeRelativePath("/outside.bin"))
        assertFalse(WolfAssetFileVerifier.isSafeRelativePath("cinematic/../../outside.bin"))
        assertTrue(WolfAssetFileVerifier.isSafeRelativePath("cinematic/forest.bin"))
    }

    @Test
    fun missingDependencyBlocksApproval() {
        val entry = validEntry().copy(dependencies = mapOf("wolf_missing_dependency" to "1.0.0"))
        val result = gate(entry).evaluate(entry, request())

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("اعتمادية غير موجودة") })
    }

    @Test
    fun cyclicDependenciesBlockApproval() {
        val first = validEntry()
        val second = validEntry().copy(
            assetId = "wolf_cinematic_forest_dependency",
            dependencies = mapOf(first.assetId to first.version)
        )
        val firstWithDependency = first.copy(dependencies = mapOf(second.assetId to second.version))
        val gate = WolfAssetQualityGate(
            mapOf(first.assetId to firstWithDependency, second.assetId to second),
            verifier
        )
        val result = gate.evaluate(firstWithDependency, request())

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("اعتمادية غير معتمدة") || it.contains("دورة") })
    }

    @Test
    fun approvalIsRestrictedToDeclaredScopeAndTestedRuntime() {
        val entry = validEntry()
        val scopeResult = gate(entry).evaluate(entry, request(intendedScope = "closeup_character"))
        val runtimeResult = gate(entry).evaluate(entry, request(targetRuntime = "UNTESTED_RENDERER"))

        val versionResult = gate(entry).evaluate(entry, request(targetRuntimeVersion = "2.0"))

        assertFalse(scopeResult.approved)
        assertFalse(runtimeResult.approved)
        assertFalse(versionResult.approved)
        assertTrue(scopeResult.errors.any { it.contains("نطاق الاستخدام") })
        assertTrue(runtimeResult.errors.any { it.contains("توافق مختبر") })
        assertTrue(versionResult.errors.any { it.contains("إصدار بيئة التشغيل") })
    }

    @Test
    fun dependencyVersionMustMatchPinnedVersion() {
        val dependency = validEntry().copy(version = "2.0.0")
        val parent = validEntry().copy(
            assetId = "wolf_cinematic_forest_parent",
            dependencies = mapOf(dependency.assetId to "1.0.0")
        )
        val result = WolfAssetQualityGate(
            mapOf(parent.assetId to parent, dependency.assetId to dependency),
            verifier
        ).evaluate(parent, request())

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("نسخة الاعتمادية") })
    }

    @Test
    fun dependencyWarningsArePropagatedToParentApproval() {
        val dependency = validEntry()
        val parent = validEntry().copy(
            assetId = "wolf_cinematic_forest_parent",
            dependencies = mapOf(dependency.assetId to dependency.version),
            budgetStatus = WolfAssetBudgetStatus.PASS,
            resourceMeasurements = mapOf("packagedBytes" to "5")
        )
        val result = WolfAssetQualityGate(
            mapOf(parent.assetId to parent, dependency.assetId to dependency),
            verifier
        ).evaluate(parent, request())

        assertTrue(result.approved)
        assertTrue(result.warnings.any { it.contains("معلقة") })
    }

    @Test
    fun unresolvedTechnicalProfileCannotPassAsCompatible() {
        val entry = validEntry().copy(
            technicalProfile = validEntry().technicalProfile + ("coordinateSystem" to "UNKNOWN")
        )
        val result = gate(entry).evaluate(entry, request())

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("غير محسومة") })
    }

    @Test
    fun pendingBudgetNeedsExplicitReviewerAcceptance() {
        val entry = validEntry().copy(
            review = validEntry().review.copy(budgetPendingAccepted = false)
        )
        val result = gate(entry).evaluate(entry, request())

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("قرارًا صريحًا") })
    }

    @Test
    fun failedResourceBudgetBlocksApproval() {
        val entry = validEntry().copy(budgetStatus = WolfAssetBudgetStatus.FAIL)
        val result = gate(entry).evaluate(entry, request())

        assertFalse(result.approved)
        assertTrue(result.errors.any { it.contains("ميزانية الموارد") })
    }

    @Test
    fun onlyApprovedEntriesAreReturnedToRuntime() {
        val approved = validEntry()
        val candidate = validEntry().copy(assetId = "wolf_candidate_asset", lifecycle = WolfAssetLifecycle.CANDIDATE)
        val gate = WolfAssetQualityGate(mapOf(approved.assetId to approved, candidate.assetId to candidate), verifier)

        assertEquals(listOf(approved.assetId), gate.approvedAssets(request()).map { it.assetId })
    }

    @Test
    fun bundledEmptyRegistryLoadsWithoutInventingAssets() {
        val registry = WolfAssetRegistry.fromJson(
            """{"schemaVersion":2,"assets":[]}""",
            verifier
        )

        assertEquals(0, registry.size)
        assertTrue(registry.approvedAssets(request()).isEmpty())
        assertFalse(registry.evaluate("wolf_not_present", request()).approved)
    }

    @Test
    fun missingDependenciesFieldIsNotTreatedAsAnEmptyDependencyList() {
        assertManifestRejected(validEntryJson().apply { remove("dependencies") })
    }

    @Test
    fun missingAttributionRequirementCannotDefaultToNoAttribution() {
        val asset = validEntryJson()
        asset.getJSONObject("license").remove("attributionRequired")
        assertManifestRejected(asset)
    }

    @Test
    fun licenseAndReviewFlagsMustBeExplicitJsonBooleans() {
        val asset = validEntryJson()
        asset.getJSONObject("license").put("attributionRequired", "false")
        assertManifestRejected(asset)
    }

    private fun assertManifestRejected(asset: JSONObject) {
        val manifest = JSONObject()
            .put("schemaVersion", WolfAssetRegistry.CURRENT_SCHEMA_VERSION)
            .put("assets", JSONArray().put(asset))
        val parsed = runCatching { WolfAssetRegistry.fromJson(manifest.toString(), verifier) }
        assertTrue("Malformed or incomplete manifest must fail closed", parsed.isFailure)
    }

    private fun validEntryJson(): JSONObject {
        val entry = validEntry()
        val license = entry.license
        val review = entry.review
        val licenseJson = JSONObject()
            .put("creatorOrRightsHolder", license.creatorOrRightsHolder)
            .put("sourceName", license.sourceName)
            .put("sourceLocation", license.sourceLocation)
            .put("licenseId", license.licenseId)
            .put("licenseName", license.licenseName)
            .put("licenseVersion", license.licenseVersion)
            .put("evidenceReference", license.evidenceReference)
            .put("verified", license.verified)
            .put("commercialUse", license.commercialUse)
            .put("modificationAllowed", license.modificationAllowed)
            .put("redistributionAllowed", license.redistributionAllowed)
            .put("appBundlingAllowed", license.appBundlingAllowed)
            .put("attributionRequired", license.attributionRequired)
            .put("attributionText", license.attributionText)
            .put("restrictions", license.restrictions)
            .put("acquiredAtEpochMillis", license.acquiredAtEpochMillis)
            .put("acquisitionMethod", license.acquisitionMethod)
            .put("provenanceRecordReference", license.provenanceRecordReference)
        val files = JSONArray()
        entry.files.forEach { file ->
            files.put(
                JSONObject()
                    .put("relativePath", file.relativePath)
                    .put("format", file.format)
                    .put("byteSize", file.byteSize)
                    .put("sha256", file.sha256)
            )
        }
        val dependencies = JSONObject().apply {
            entry.dependencies.forEach { (id, version) -> put(id, version) }
        }
        val technicalProfile = JSONObject().apply {
            entry.technicalProfile.forEach { (key, value) -> put(key, value) }
        }
        val resourceMeasurements = JSONObject().apply {
            entry.resourceMeasurements.forEach { (key, value) -> put(key, value) }
        }
        val runtimes = JSONObject().apply {
            entry.compatibleRuntimes.forEach { (key, value) -> put(key, value) }
        }
        val reviewJson = JSONObject()
            .put("reviewer", review.reviewer)
            .put("reviewedAtEpochMillis", review.reviewedAtEpochMillis)
            .put("decisionReason", review.decisionReason)
            .put("reviewEvidenceReference", review.reviewEvidenceReference)
            .put("rightsPassed", review.rightsPassed)
            .put("visualPassed", review.visualPassed)
            .put("technicalPassed", review.technicalPassed)
            .put("realismScore", review.realismScore)
            .put("budgetPendingAccepted", review.budgetPendingAccepted)
            .put("visualStyle", review.visualStyle.name)
            .put("approvedStyleScopes", JSONArray().apply {
                review.approvedStyleScopes.forEach { put(it) }
            })
        return JSONObject()
            .put("assetId", entry.assetId)
            .put("name", entry.name)
            .put("version", entry.version)
            .put("category", entry.category.name)
            .put("tags", JSONArray().apply { entry.tags.forEach { put(it) } })
            .put("lifecycle", entry.lifecycle.name)
            .put("license", licenseJson)
            .put("files", files)
            .put("dependencies", dependencies)
            .put("technicalProfile", technicalProfile)
            .put("resourceMeasurements", resourceMeasurements)
            .put("compatibleRuntimes", runtimes)
            .put("approvedScopes", JSONArray().apply { entry.approvedScopes.forEach { put(it) } })
            .put("budgetStatus", entry.budgetStatus.name)
            .put("review", reviewJson)
    }

    private fun gate(entry: WolfAssetRegistryEntry) =
        WolfAssetQualityGate(mapOf(entry.assetId to entry), verifier)

    private fun request(
        targetRuntime: String = "WOLF_ANDROID_CANVAS",
        targetRuntimeVersion: String = "1.0-tested",
        intendedScope: String = "environment_background"
    ) = WolfAssetUseRequest(targetRuntime, targetRuntimeVersion, intendedScope)

    private fun validEntry() = WolfAssetRegistryEntry(
        assetId = "wolf_cinematic_forest_001",
        name = "Cinematic forest environment",
        version = "1.0.0",
        category = WolfAssetCategory.ENVIRONMENT,
        tags = setOf("realistic", "forest", "night"),
        lifecycle = WolfAssetLifecycle.APPROVED,
        license = WolfAssetLicenseEvidence(
            creatorOrRightsHolder = "Verified Rights Holder",
            sourceName = "Documented Offline Source",
            sourceLocation = "offline:verified-source-record",
            licenseId = "WOLF-TEST-LICENSE-001",
            licenseName = "Commercial Redistribution License",
            licenseVersion = "1.0",
            evidenceReference = "licenses/forest-license.txt",
            verified = true,
            commercialUse = true,
            modificationAllowed = true,
            redistributionAllowed = true,
            appBundlingAllowed = true,
            attributionRequired = true,
            attributionText = "Credit required by license",
            restrictions = "None for the declared scope",
            acquiredAtEpochMillis = System.currentTimeMillis() - 1_000,
            acquisitionMethod = "Documented licensed acquisition",
            provenanceRecordReference = "provenance/forest-record.json"
        ),
        files = listOf(manifestFile),
        dependencies = emptyMap(),
        technicalProfile = mapOf(
            "coordinateSystem" to "Android Canvas 2D",
            "units" to "pixels",
            "importSettings" to "packaged unchanged",
            "rendererRequirements" to "WOLF_ANDROID_CANVAS",
            "geometryProfile" to "static environment mesh",
            "materialProfile" to "PBR material profile documented"
        ),
        resourceMeasurements = emptyMap(),
        compatibleRuntimes = mapOf("WOLF_ANDROID_CANVAS" to "1.0-tested"),
        approvedScopes = setOf("environment_background"),
        budgetStatus = WolfAssetBudgetStatus.PENDING,
        review = WolfAssetReview(
            reviewer = "WOLF Asset Review",
            reviewedAtEpochMillis = System.currentTimeMillis() - 1_000,
            decisionReason = "All mandatory rights, visual, and technical evidence reviewed",
            reviewEvidenceReference = "reviews/forest-review.json",
            rightsPassed = true,
            visualPassed = true,
            technicalPassed = true,
            realismScore = 5,
            budgetPendingAccepted = true,
            visualStyle = WolfAssetVisualStyle.CINEMATIC_REALISTIC,
            approvedStyleScopes = emptySet()
        )
    )
}
