package io.github.tomsadeh.puki.ui.big.kit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Retains a reading visit on rotation; releases its games on door exit or another session. */
internal class ReadingScreenModels(application: Application) : AndroidViewModel(application),
    ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override var viewModelStore = ViewModelStore()
        private set
    override val defaultViewModelProviderFactory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)

    fun reset() {
        viewModelStore.clear()
        viewModelStore = ViewModelStore()
    }

    override fun onCleared() { viewModelStore.clear() }
}
