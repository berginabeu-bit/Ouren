package com.focusedmind.app

import android.content.Context

object PremiumAccessManager {
    fun hasAccess(context: Context, product: String): Boolean =
        PremiumProducts.isKnown(product) && FocusedMindStore.premium(context, product)

    fun setValidated(context: Context, product: String, value: Boolean = true) {
        require(PremiumProducts.isKnown(product))
        FocusedMindStore.setPremium(context, product, value)
    }
}
