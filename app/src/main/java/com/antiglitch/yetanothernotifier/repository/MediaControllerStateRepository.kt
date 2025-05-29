package com.antiglitch.yetanothernotifier.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface MediaControllerStateListener {
    fun onTransportStateChanged(state: MediaTransportState) {}
    fun onContentStateChanged(state: MediaContentState) {}
    fun onErrorStateChanged(state: MediaErrorState) {}
    fun onConnectionStateChanged(state: MediaControllerConnectionState) {}
}

class MediaControllerStateRepository {
    
    private val _transportState = MutableStateFlow(MediaTransportState())
    val transportState: StateFlow<MediaTransportState> = _transportState.asStateFlow()
    
    private val _contentState = MutableStateFlow(MediaContentState())
    val contentState: StateFlow<MediaContentState> = _contentState.asStateFlow()
    
    private val _errorState = MutableStateFlow(MediaErrorState())
    val errorState: StateFlow<MediaErrorState> = _errorState.asStateFlow()
    
    private val _connectionState = MutableStateFlow(MediaControllerConnectionState())
    val connectionState: StateFlow<MediaControllerConnectionState> = _connectionState.asStateFlow()
    
    private val listeners = mutableSetOf<MediaControllerStateListener>()
    
    fun addListener(listener: MediaControllerStateListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: MediaControllerStateListener) {
        listeners.remove(listener)
    }
    
    fun updateTransportState(state: MediaTransportState) {
        _transportState.value = state
        listeners.forEach { it.onTransportStateChanged(state) }
    }
    
    fun updateContentState(state: MediaContentState) {
        _contentState.value = state
        listeners.forEach { it.onContentStateChanged(state) }
    }
    
    fun updateErrorState(state: MediaErrorState) {
        _errorState.value = state
        listeners.forEach { it.onErrorStateChanged(state) }
    }
    
    fun updateConnectionState(state: MediaControllerConnectionState) {
        _connectionState.value = state
        listeners.forEach { it.onConnectionStateChanged(state) }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: MediaControllerStateRepository? = null
        
        fun getInstance(): MediaControllerStateRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaControllerStateRepository().also { INSTANCE = it }
            }
        }
    }
}
