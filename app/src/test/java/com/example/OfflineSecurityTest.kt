package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class OfflineSecurityTest {

    @Test
    fun testManifestHasNoInternetOrProhibitedPermissions() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        val permissionNodes = doc.getElementsByTagName("uses-permission")
        val activePermissions = mutableSetOf<String>()
        for (i in 0 until permissionNodes.length) {
            val elem = permissionNodes.item(i) as? org.w3c.dom.Element ?: continue
            val nodeOp = elem.getAttribute("tools:node")
            if (nodeOp != "remove") {
                activePermissions.add(elem.getAttribute("android:name"))
            }
        }

        assertFalse("Manifest must NOT contain INTERNET permission", activePermissions.contains("android.permission.INTERNET"))
        assertFalse("Manifest must NOT contain ACCESS_NETWORK_STATE permission", activePermissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse("Manifest must NOT contain QUERY_ALL_PACKAGES permission", activePermissions.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse("Manifest must NOT contain SYSTEM_ALERT_WINDOW permission", activePermissions.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertFalse("Manifest must NOT contain BIND_ACCESSIBILITY_SERVICE permission", activePermissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
    }

    @Test
    fun testMergedManifestPermissionsAndExportedComponents() {
        val mergedCandidates = listOf(
            File("build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        )
        val manifestToInspect = mergedCandidates.first { it.exists() }

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestToInspect)
        val permissionNodes = doc.getElementsByTagName("uses-permission")
        val mergedPermissions = mutableSetOf<String>()
        for (i in 0 until permissionNodes.length) {
            val elem = permissionNodes.item(i) as? org.w3c.dom.Element ?: continue
            val nodeOp = elem.getAttribute("tools:node")
            if (nodeOp != "remove") {
                mergedPermissions.add(elem.getAttribute("android:name"))
            }
        }

        // Verify prohibited permissions are absent in merged manifest as well
        assertFalse(mergedPermissions.contains("android.permission.INTERNET"))
        assertFalse(mergedPermissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse(mergedPermissions.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse(mergedPermissions.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertFalse(mergedPermissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))

        // Verify only the 3 intended app components (MainActivity, VaultAutofillService, VaultCredentialProviderService) are exported
        val exportedAppComponents = mutableSetOf<String>()
        val componentTags = listOf("activity", "service", "receiver", "provider")

        for (tag in componentTags) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i) as? org.w3c.dom.Element ?: continue
                val name = elem.getAttribute("android:name")
                val exported = elem.getAttribute("android:exported")
                val isAppComponent = name.startsWith(".") || name.startsWith("com.example.")
                if (isAppComponent && exported == "true") {
                    exportedAppComponents.add(name.substringAfterLast('.'))
                }
                if (name.endsWith("AutofillConfirmationActivity")) {
                    assertEquals("AutofillConfirmationActivity must NOT be exported", "false", exported)
                }
            }
        }

        val expectedExported = setOf(
            "MainActivity",
            "VaultAutofillService",
            "VaultCredentialProviderService"
        )
        assertEquals(
            "Only MainActivity, VaultAutofillService, and VaultCredentialProviderService should be exported",
            expectedExported,
            exportedAppComponents
        )
    }

    @Test
    fun testForbiddenNetworkingLibrariesOnClasspath() {
        val forbiddenClassNames = listOf(
            "retrofit2.Retrofit",
            "com.google.firebase.FirebaseApp",
            "com.google.ai.client.generativeai.GenerativeModel"
        )

        for (className in forbiddenClassNames) {
            val isPresent = try {
                Class.forName(className)
                true
            } catch (e: ClassNotFoundException) {
                false
            }
            assertFalse("Forbidden network class '$className' must NOT be present on the classpath", isPresent)
        }
    }
}
