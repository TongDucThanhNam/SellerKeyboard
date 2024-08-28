package com.terasumi.sellerkeyboard.old

class Snippet {
    var id: Int = 0
    var title: String
    var content: String
    var imageUrl: String

    constructor(title: String, content: String, imageUrl: String) {
        this.title = title
        this.content = content
        this.imageUrl = imageUrl
    }

    constructor(id: Int, title: String, content: String, imageUrl: String) {
        this.id = id
        this.title = title
        this.content = content
        this.imageUrl = imageUrl
    }
}