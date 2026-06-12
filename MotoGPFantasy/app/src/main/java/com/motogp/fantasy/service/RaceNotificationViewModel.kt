package com.motogp.fantasy.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motogp.fantasy.data.repository.LeagueRepo
import com.motogp.fantasy.data.repository.RaceRepo
import com.motogp.fantasy.data.repository.UserRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RaceNotificationViewModel @Inject constructor(
    raceRepo: RaceRepo,
    userRepo: UserRepo,
    scheduler: RaceNotificationScheduler,
    leagueRepo: LeagueRepo,
    overtakeNotifier: LeagueOvertakeNotifier
) : ViewModel() {

    init {
        viewModelScope.launch {
            raceRepo.refresh()
            combine(raceRepo.races, userRepo.currentUser()) { races, user -> races to user }
                .collect { (races, user) -> scheduler.schedule(races, user) }
        }

        viewModelScope.launch {
            combine(userRepo.currentUser(), leagueRepo.myLeagues()) { user, leagues -> user to leagues }
                .collectLatest { (user, leagues) ->
                    overtakeNotifier.clearRuntimeState()
                    if (user?.notifyRivalOvertake != true || leagues.isEmpty()) {
                        return@collectLatest
                    }

                    coroutineScope {
                        leagues.forEach { league ->
                            launch {
                                leagueRepo.leaderboard(league.id).collect { entries ->
                                    overtakeNotifier.onLeaderboardChanged(
                                        leagueId = league.id,
                                        leagueName = league.name,
                                        entries = entries
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}
