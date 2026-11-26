package org.linphone.shiroikuma

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.databinding.BindingAdapter
import coil3.dispose
import coil3.load
import coil3.request.ImageRequest

/**
 * shiroikuma-rindenwa fork — the other party's picture on a call screen, shown **whole** (白い熊,
 * 2026-11-26): no circle, no crop, no transformation; the view's own `fitCenter` fits a picture of
 * any shape into the card.
 *
 * A caller without a picture — or one whose picture cannot be read — gets the round stand-in
 * instead: the sibling tagged `sk_picture_fallback`. The picture view is made invisible then, not
 * gone: a view that is gone is never measured, and the loader waits for a size before it loads.
 */
@BindingAdapter("skWholePicture")
fun bindSkWholePicture(view: ImageView, path: String?) {
    val fallback = (view.parent as? ViewGroup)?.findViewWithTag<View>("sk_picture_fallback")

    fun show(picture: Boolean) {
        view.visibility = if (picture) View.VISIBLE else View.INVISIBLE
        fallback?.visibility = if (picture) View.INVISIBLE else View.VISIBLE
    }

    if (path.isNullOrEmpty() || path == "null") {
        view.dispose()
        view.setImageDrawable(null)
        show(false)
        return
    }
    view.load(path) {
        listener(object : ImageRequest.Listener {
            override fun onSuccess(request: ImageRequest, result: coil3.request.SuccessResult) = show(true)

            override fun onError(request: ImageRequest, result: coil3.request.ErrorResult) = show(false)
        })
    }
}
