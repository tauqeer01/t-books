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
    object Reader : Screen("reader/{bookId}?page={page}") {
        fun createRoute(bookId: String, page: Int = -1): String = "reader/$bookId?page=$page"
    }
    object Legal : Screen("legal/{doc}") {
        fun createRoute(doc: String): String = "legal/$doc"
    }
    object Licenses : Screen("licenses")
    object BookDetails : Screen("book_details/{bookId}") {
        fun createRoute(bookId: String): String = "book_details/$bookId"
    }
}
