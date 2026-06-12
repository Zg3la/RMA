package com.motogp.fantasy.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motogp.fantasy.data.repository.RaceRepo
import com.motogp.fantasy.data.repository.UserRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RaceNotificationViewModel @Inject constructor(
    raceRepo: RaceRepo,
    userRepo: UserRepo,
    scheduler: RaceNotificationScheduler
) : ViewModel() {

    init {
        viewModelScope.launch {
            raceRepo.refresh()
            combine(raceRepo.races, userRepo.currentUser()) { races, user -> races to user }
                .collect { (races, user) -> scheduler.schedule(races, user) }
        }
    }
}
