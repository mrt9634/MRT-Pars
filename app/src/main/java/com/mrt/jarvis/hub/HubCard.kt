package com.mrt.jarvis.hub

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * One card in the Hub.
 * - iconRes: vector or system drawable
 * - labelRes: string resource
 * - action: what happens when user selects this card
 */
data class HubCard(
    val id: String,
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
    val action: HubAction
)

/** What the Hub does when a card is opened. */
sealed class HubAction {
    object OpenMusic : HubAction()
    object OpenNavigation : HubAction()
    object OpenDashboard : HubAction()
    object OpenNotifications : HubAction()
    object OpenSettings : HubAction()
    object OpenConnected : HubAction()
    object OpenCommunication : HubAction()
    object OpenApps : HubAction() // <-- این خط جدید اضافه شد
    object Placeholder : HubAction()
}
