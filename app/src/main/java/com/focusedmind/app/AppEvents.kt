package com.focusedmind.app

import android.content.Context
import android.content.Intent

/** Process-local app events used to refresh Compose after background alarm actions. */
object AppEvents {
    const val ACTION_STORE_CHANGED = "com.focusedmind.app.STORE_CHANGED"
    const val EXTRA_COMMITMENT_ID = "commitment_id"

    fun storeChanged(context: Context, commitmentId: Long? = null) {
        val intent = Intent(ACTION_STORE_CHANGED).setPackage(context.packageName)
        if (commitmentId != null) intent.putExtra(EXTRA_COMMITMENT_ID, commitmentId)
        context.sendBroadcast(intent)
    }
}
