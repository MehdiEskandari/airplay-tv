package io.github.mehdieskandari.airplaytv.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.mehdieskandari.airplaytv.AppLanguage
import io.github.mehdieskandari.airplaytv.BuildConfig
import io.github.mehdieskandari.airplaytv.R

/** Store builds never download APKs or request permission to install packages. */
object StoreLinks {
    val isStoreBuild get() = BuildConfig.STORE != "direct"
    enum class Action { DETAILS, UPDATE, REVIEW }

    fun open(context: Context, action: Action) {
        val id = context.packageName
        val myket = BuildConfig.STORE == "myket"
        val uri = if (myket) when (action) {
            Action.UPDATE -> "myket://check-update?id=$id"
            Action.REVIEW -> "myket://comment?id=$id"
            Action.DETAILS -> "myket://details?id=$id"
        } else "bazaar://details?id=$id"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setPackage(if (myket) "ir.mservices.market" else "com.farsitel.bazaar")
        if (launch(context, intent)) return
        // Also works on TVs without a store client; never fails with ActivityNotFoundException.
        openWeb(context, if (myket) "https://myket.ir/app/$id" else "https://cafebazaar.ir/app/$id")
    }

    fun openWeb(context: Context, url: String) {
        if (!launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
            Toast.makeText(AppLanguage.context(context), R.string.no_store_handler, Toast.LENGTH_LONG).show()
        }
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) { false
    } catch (_: SecurityException) { false }
}
