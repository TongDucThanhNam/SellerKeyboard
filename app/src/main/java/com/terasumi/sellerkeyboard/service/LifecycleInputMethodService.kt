package com.terasumi.sellerkeyboard.service

import android.content.Intent
import android.inputmethodservice.InputMethodService
import androidx.annotation.CallSuper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher

abstract class LifecycleInputMethodService : InputMethodService(), LifecycleOwner {
    protected val serviceLifecycleDispatcher = ServiceLifecycleDispatcher(this)

    @CallSuper
    override fun onCreate() {
        serviceLifecycleDispatcher.onServicePreSuperOnCreate()
        super.onCreate()
    }

    override fun onBindInput() {
        super.onBindInput()
        serviceLifecycleDispatcher.onServicePreSuperOnBind()
    }

    @CallSuper
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    @CallSuper
    override fun onDestroy() {
        serviceLifecycleDispatcher.onServicePreSuperOnDestroy()
        super.onDestroy()
    }
}