package com.terasumi.sellerkeyboard

import android.content.Context

object StringResources {
    lateinit var app_name: String
    lateinit var enable_seller_keyboard: String
    lateinit var welcome_message: String
    lateinit var enable_keyboard_prompt: String
    lateinit var homepage: String
    lateinit var add_snippet: String
    lateinit var edit_snippet: String
    lateinit var enter_label: String
    lateinit var enter_content: String
    lateinit var save: String
    lateinit var enable_keyboard: String
    lateinit var change_keyboard: String
    lateinit var voice: String
    lateinit var subtype: String
    lateinit var snippet_list: String
    lateinit var calculator: String
    lateinit var enter_expression: String
    lateinit var send_expression: String
    lateinit var send_result: String

    fun initialize(context: Context) {
        val resources = context.resources
        app_name = resources.getString(R.string.app_name)
        enable_seller_keyboard = resources.getString(R.string.enable_seller_keyboard)
        welcome_message = resources.getString(R.string.welcome_message)
        enable_keyboard_prompt = resources.getString(R.string.enable_keyboard_prompt)
        homepage = resources.getString(R.string.homepage)
        add_snippet = resources.getString(R.string.add_snippet)
        edit_snippet = resources.getString(R.string.edit_snippet)
        enter_label = resources.getString(R.string.enter_label)
        enter_content = resources.getString(R.string.enter_content)
        save = resources.getString(R.string.save)
        enable_keyboard = resources.getString(R.string.enable_keyboard)
        change_keyboard = resources.getString(R.string.change_keyboard)
        voice = resources.getString(R.string.voice)
        subtype = resources.getString(R.string.subtype)
        snippet_list = resources.getString(R.string.snippet_list)
        calculator = resources.getString(R.string.calculator)
        enter_expression = resources.getString(R.string.enter_expression)
        send_expression = resources.getString(R.string.send_expression)
        send_result = resources.getString(R.string.send_result)
    }
}