package com.itangcent.idea.plugin.api.dashboard

import com.google.inject.Inject
import com.itangcent.idea.plugin.dialog.YapiDashboardDialog
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.util.UIUtils

class YapiDashBoard {

    @Inject
    private val actionContext: ActionContext? = null

    fun showDashBoardWindow() {
        val apiDashboardDialog = actionContext!!.instance { YapiDashboardDialog() }
        UIUtils.show(apiDashboardDialog)
    }
}