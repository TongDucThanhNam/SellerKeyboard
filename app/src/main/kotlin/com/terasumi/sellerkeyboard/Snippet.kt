package com.terasumi.sellerkeyboard

class Snippet {
    var id: Int = 0
    @JvmField
    var title: String
    @JvmField
    var content: String
    @JvmField
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
