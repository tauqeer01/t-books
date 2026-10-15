package com.bookflow.app.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Library : Screen("library")
    object Collections : Screen("collections")
    object Search : Screen("search")
    object Settings : Screen("settings")
    object Notes : Screen("notes")
    object NoteEditor : Screen("note/{noteId}") {
        fun createRoute(noteId: String): String = "note/$noteId"
    }
    object Cards : Screen("cards")
    object Deck : Screen("deck/{deckId}") {
        fun createRoute(deckId: String): String = "deck/$deckId"
    }
    object CardEditor : Screen("card_editor?cardId={cardId}&deckId={deckId}") {
        fun createRoute(cardId: String? = null, deckId: String? = null): String = "card_editor?cardId=${cardId.orEmpty()}&deckId=${deckId.orEmpty()}"
    }
    object Review : Screen("review/{deckId}") {
        fun createRoute(deckId: String): String = "review/$deckId"
    }
    object CardStats : Screen("card_stats?deckId={deckId}") {
        fun createRoute(deckId: String? = null): String = "card_stats?deckId=${deckId.orEmpty()}"
    }
    object Reader : Screen("reader/{bookId}?page={page}") {
        fun createRoute(bookId: String, page: Int = -1): String = "reader/$bookId?page=$page"
    }
    object Legal : Screen("legal/{doc}") {
        fun createRoute(doc: String): String = "legal/$doc"
    }
    object Licenses : Screen("licenses")
    object ESign : Screen("esign")
    object SignEditor : Screen("sign_editor?bookId={bookId}&uri={uri}") {
        fun createRoute(bookId: String? = null, uri: android.net.Uri? = null): String =
            "sign_editor?bookId=${bookId.orEmpty()}&uri=${uri?.let { android.net.Uri.encode(it.toString()) }.orEmpty()}"
    }
    object BookDetails : Screen("book_details/{bookId}") {
        fun createRoute(bookId: String): String = "book_details/$bookId"
    }
}
