package com.bookflow.app.presentation.screens.legal

/**
 * Publisher details shown in the privacy policy and terms.
 * After editing anything in this file, run `python3 scripts/export_legal_docs.py` to refresh the hosted copies
 * in docs/legal/ (Play requires the privacy policy at a public URL).
 */
object LegalInfo {
    const val PUBLISHER = "BookFlow"
    const val CONTACT_EMAIL = "aht.apps@alhadaftech.com"
    const val EFFECTIVE_DATE = "October 2, 2026"
}

data class LegalSection(val heading: String, val body: String)

enum class LegalDocument(val route: String, val title: String, val sections: List<LegalSection>) {
    PRIVACY(
        route = "privacy",
        title = "Privacy Policy",
        sections = listOf(
            LegalSection(
                "Summary",
                "BookFlow works entirely on your device. It has no accounts, no ads, no analytics and no tracking, " +
                    "and the app does not have permission to access the internet."
            ),
            LegalSection(
                "What BookFlow stores on your device",
                "• The PDFs you choose to open, read in place from where they are stored. PDFs opened from another app " +
                    "with \"Open with\" are copied into BookFlow's private storage so they keep working.\n" +
                    "• Your library details: titles, authors, reading position, collections and favorites.\n" +
                    "• Your highlights, notes, drawings and bookmarks.\n" +
                    "• Reading time per day for your reading goal.\n" +
                    "• Your settings and page thumbnails.\n\n" +
                    "This data never leaves your device through BookFlow."
            ),
            LegalSection(
                "File access",
                "BookFlow only opens files you pick in the Android file picker or send to it with \"Open with\". " +
                    "It does not scan your storage. Removing a book from BookFlow never deletes your original file."
            ),
            LegalSection(
                "Backups",
                "If Android backup is turned on for your device, Android may back up your BookFlow library details, " +
                    "annotations and settings to your Google account. PDF files are not included. You control this in " +
                    "your device's backup settings, and Google's privacy policy applies to those backups."
            ),
            LegalSection(
                "Crash reports",
                "If BookFlow crashes, a technical report (app version, device model, Android version and error details) " +
                    "is saved on your device. It is only sent if you choose to share it from Settings › Report a problem. " +
                    "If you have opted in to sharing diagnostics with Google, Google Play may also provide anonymous crash " +
                    "statistics to the developer."
            ),
            LegalSection(
                "Sharing",
                "BookFlow does not sell or share your data. When you use Share (for example sharing a PDF or an exported " +
                    "annotated copy), the file goes to the app you choose."
            ),
            LegalSection(
                "Deleting your data",
                "Remove individual books from your library, clear the cache in Settings, or uninstall BookFlow to delete " +
                    "all of its data from your device."
            ),
            LegalSection(
                "Children",
                "BookFlow is a general-audience reading app and does not knowingly collect personal information from anyone."
            ),
            LegalSection(
                "Changes and contact",
                "If this policy changes, the new version will be published with an updated effective date. " +
                    "Questions: ${LegalInfo.CONTACT_EMAIL}.\n\nEffective ${LegalInfo.EFFECTIVE_DATE}."
            )
        )
    ),
    TERMS(
        route = "terms",
        title = "Terms of Use",
        sections = listOf(
            LegalSection(
                "Using BookFlow",
                "BookFlow is provided by ${LegalInfo.PUBLISHER} to read, organize and annotate PDF documents on your device. " +
                    "By using the app you agree to these terms."
            ),
            LegalSection(
                "Your content",
                "You keep all rights to the documents you open and the notes you create. Only open documents you have " +
                    "the right to use. BookFlow does not upload or claim any rights to your content."
            ),
            LegalSection(
                "Your responsibility for your data",
                "Your library and annotations are stored on your device. Keep your own backups of important files; " +
                    "uninstalling the app or resetting your device can delete BookFlow's data."
            ),
            LegalSection(
                "No warranty",
                "BookFlow is provided \"as is\" without warranties of any kind. To the extent permitted by law, " +
                    "${LegalInfo.PUBLISHER} is not liable for any loss of data or damages arising from use of the app."
            ),
            LegalSection(
                "Open-source software",
                "BookFlow includes open-source components, each under its own license. See Settings › Open-source licenses."
            ),
            LegalSection(
                "Changes and contact",
                "These terms may be updated with a new effective date. Questions: ${LegalInfo.CONTACT_EMAIL}.\n\n" +
                    "Effective ${LegalInfo.EFFECTIVE_DATE}."
            )
        )
    );

    companion object {
        fun fromRoute(route: String?): LegalDocument = entries.firstOrNull { it.route == route } ?: PRIVACY
    }
}
