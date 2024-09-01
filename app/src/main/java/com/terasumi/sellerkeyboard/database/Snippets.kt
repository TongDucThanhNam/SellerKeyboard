package com.terasumi.sellerkeyboard.database

data class Snippets(
    val id: Int,
    val title: String,
    val content: String,
    val imageUrls: List<String>
)