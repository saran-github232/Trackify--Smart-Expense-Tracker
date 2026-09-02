package com.saran.expensemanager

object AdIds {
    val BANNER       = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/6300978111"
                       else "ca-app-pub-3640202358658644/5337372563"
    val INTERSTITIAL = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/1033173712"
                       else "ca-app-pub-3640202358658644/7987431104"
    val NATIVE       = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/2247696110"
                       else "ca-app-pub-3640202358658644/2840870032"
}
