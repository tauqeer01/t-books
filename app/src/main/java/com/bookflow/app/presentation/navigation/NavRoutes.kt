package com.bookflow.app.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Library : Screen("library")
    object Collections : Screen("collections")
    object Search : Screen("search")
    object Settings : Screen("settings")
    object Reader : Screen("reader/{bookId}?page={page}") {
        fun createRoute(bookId: String, page: Int = 125): String = "reader/$bookId?page=$page"
    }
    object BookDetails : Screen("book_details/{bookId}") {
        fun createRoute(bookId: String): String = "book_details/$bookId"
    }
}
