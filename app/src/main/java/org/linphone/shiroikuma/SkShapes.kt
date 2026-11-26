package org.linphone.shiroikuma

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.LayoutInflaterCompat
import androidx.databinding.BindingAdapter
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — the fork's own shapes, drawn in code.
 *
 * The frames, pills and grounds the fork added (`drawable/sk_*.xml` that are not icons) hold no
 * colour in XML: each file is a colourless stub that keeps the name layouts refer to, and the
 * shape that is actually shown is built here, from colour roles of its own ([SkRoles]).
 *
 * How a view gets it: [install] puts one factory in front of an activity's layout inflater. It
 * lets AppCompat create the view as before, creates the view itself where AppCompat declines
 * (a plain `View`, a `ConstraintLayout`, …), and then [adopt]s it — if the layout gave it one of
 * our stubs as `android:background`, the stub is replaced by the shape built here. A background
 * that is chosen by a binding expression never passes through the inflater; those name their
 * shape with the `skShape` adapter instead.
 */
object SkShapes {
    private const val TAG = "SkShapes"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private val PREFIXES = arrayOf("android.widget.", "android.view.", "android.webkit.")
    private const val FULL_ROUND_DP = 100f

    // ------------------------------------------------------------------ the shapes

    /** The shape that stands for the stub [resId], or null when [resId] is not one of ours. */
    fun build(context: Context, resId: Int): Drawable? = when (resId) {
        R.drawable.sk_menu_frame -> rect(context, 8f, "menu_ground", "menu_box_frame", 2f)
        R.drawable.sk_settings_panel -> rect(context, 15f, "settings_group_ground", "settings_group_frame", 2f)
        R.drawable.sk_settings_group_header_closed ->
            rect(context, 15f, "settings_header_ground", "settings_group_frame", 2f)
        R.drawable.sk_settings_group_header_open ->
            rect(context, 0f, "settings_header_ground", "settings_group_frame", 2f, top = 15f)
        R.drawable.sk_settings_group_body -> LayerDrawable(
            arrayOf(rect(context, 0f, "settings_group_ground", "settings_group_frame", 2f, bottom = 15f)),
        ).apply { setLayerInset(0, 0, -dp(context, 2f), 0, 0) }
        R.drawable.sk_search_pill -> rect(context, FULL_ROUND_DP, null, "search_frame", 1.25f)
        R.drawable.sk_status_pill -> rect(context, 90f, "status_pill_ground", "status_pill_frame", 1.5f)
        R.drawable.sk_shape_filter_selected -> rect(context, 10f, "sk_yellow_wash", "search_frame", 1.25f)
        R.drawable.sk_badge_rin -> rect(context, 4f, "rin_badge_ground", null, 0f)
        R.drawable.sk_blocked_pill -> rect(context, FULL_ROUND_DP, "sk_blocked_pill", null, 0f)
        R.drawable.sk_online_id_pill -> rect(context, 5f, "sk_online_id_pill", null, 0f)
        R.drawable.sk_dialpad_button -> RippleDrawable(
            wash(context, "dial_key_wash"),
            rect(context, 16f, "dial_key_ground", "dial_key_frame", 3f),
            null,
        )
        R.drawable.sk_dialpad_digits_underline -> InsetDrawable(
            rect(context, 0f, null, "dial_underline", 2f),
            -dp(context, 2f),
            -dp(context, 2f),
            -dp(context, 2f),
            0,
        )
        R.drawable.sk_dialpad_panel_background -> {
            val corner = context.resources.getDimension(R.dimen.sk_dialpad_panel_corner)
            val edge = context.resources.getDimensionPixelSize(R.dimen.sk_dialpad_panel_edge)

            fun layer(role: String) = GradientDrawable().apply {
                setColor(SkRoles.color(context, role))
                cornerRadii = floatArrayOf(corner, corner, corner, corner, 0f, 0f, 0f, 0f)
            }
            LayerDrawable(arrayOf(layer("dial_panel_edge"), layer("dial_panel_ground"))).apply {
                setLayerInset(1, 0, edge, 0, 0)
            }
        }
        R.drawable.sk_round_outlined_button -> RippleDrawable(
            wash(context, "call_key_wash"),
            oval(context, "call_key_ground", "call_key_ring", 2f),
            null,
        )
        R.drawable.sk_round_filled_button -> RippleDrawable(
            wash(context, "call_main_wash"),
            oval(context, null, "call_main_ring", 3f),
            oval(context, "call_main_ring", null, 0f),
        )
        R.drawable.sk_round_danger_button -> RippleDrawable(
            wash(context, "call_key_wash"),
            oval(context, null, "sk_call_hangup", 3f),
            oval(context, "sk_call_hangup", null, 0f),
        )
        R.drawable.sk_danger_pill -> dangerPill(context)
        R.drawable.sk_call_card -> rect(context, 15f, "call_card_ground", "call_card_frame", 2f)
        // Answer is the screen's main action: the thick frame. Decline is the other one: thin.
        R.drawable.sk_call_answer_pill -> RippleDrawable(
            wash(context, "call_main_wash"),
            rect(context, FULL_ROUND_DP, null, "call_main_ring", 3f),
            rect(context, FULL_ROUND_DP, "call_main_ring", null, 0f),
        )
        R.drawable.sk_call_decline_pill -> RippleDrawable(
            wash(context, "call_key_wash"),
            rect(context, FULL_ROUND_DP, null, "sk_call_hangup", 1.25f),
            rect(context, FULL_ROUND_DP, "sk_call_hangup", null, 0f),
        )
        R.drawable.sk_count_badge -> rect(context, FULL_ROUND_DP, "tab_count_ground", null, 0f)
        else -> null
    }

    /** By the name a layout's binding expression gives: the stub's name without its `sk_`. */
    fun byName(context: Context, name: String): Drawable? = when (name) {
        "settings_group_header_open" -> build(context, R.drawable.sk_settings_group_header_open)
        "settings_group_header_closed" -> build(context, R.drawable.sk_settings_group_header_closed)
        else -> null
    }

    /** The confirming pill of a dialog that deletes: red in its frame, never filled. */
    private fun dangerPill(context: Context): Drawable {
        fun pill(ground: String?, frame: String) = rect(context, 48f, ground, frame, 3f)
        return StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), pill(null, "danger_pill_disabled"))
            addState(intArrayOf(android.R.attr.state_pressed), pill("danger_pill_wash", "danger_pill_frame"))
            addState(intArrayOf(), pill(null, "danger_pill_frame"))
        }
    }

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun wash(context: Context, role: String) = ColorStateList.valueOf(SkRoles.color(context, role))

    /**
     * A rectangle with [radius] dp corners — or, when [top] / [bottom] is given, only that pair
     * rounded — its ground and its frame each from a role; a null role leaves that part out.
     */
    private fun rect(
        context: Context,
        radius: Float,
        ground: String?,
        frame: String?,
        frameDp: Float,
        top: Float? = null,
        bottom: Float? = null,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        val density = context.resources.displayMetrics.density
        if (top != null || bottom != null) {
            val t = (top ?: 0f) * density
            val b = (bottom ?: 0f) * density
            cornerRadii = floatArrayOf(t, t, t, t, b, b, b, b)
        } else {
            cornerRadius = radius * density
        }
        setColor(if (ground != null) SkRoles.color(context, ground) else 0)
        if (frame != null && frameDp > 0f) {
            setStroke((frameDp * density + 0.5f).toInt().coerceAtLeast(1), SkRoles.color(context, frame))
        }
    }

    private fun oval(context: Context, ground: String?, ring: String?, ringDp: Float): GradientDrawable =
        rect(context, 0f, ground, ring, ringDp).apply { shape = GradientDrawable.OVAL }

    // ------------------------------------------------------------------ reaching the views

    /** Replace a stub the layout gave [view] as its background by the shape built here. */
    fun adopt(view: View, attrs: AttributeSet) {
        try {
            val background = attrs.getAttributeResourceValue(ANDROID_NS, "background", 0)
            val shape = when {
                background != 0 -> build(view.context, background)
                // The one stub that arrives through a style rather than an attribute.
                attrs.styleAttribute == R.style.sk_danger_dialog_button_label_style ->
                    build(view.context, R.drawable.sk_danger_pill)
                else -> null
            } ?: return
            // A background with padding of its own gives it to a view that has none; a view the
            // layout padded keeps the layout's. The same rule the platform applies at inflation.
            val left = view.paddingLeft
            val top = view.paddingTop
            val right = view.paddingRight
            val bottom = view.paddingBottom
            view.background = shape
            if (left != 0 || top != 0 || right != 0 || bottom != 0) view.setPadding(left, top, right, bottom)
        } catch (e: Exception) {
            Log.w(TAG, "Could not give a view its shape: $e")
        }
    }

    /**
     * Put the adopting factory in front of [activity]'s inflater. Call it before
     * `super.onCreate`, or AppCompat installs its own factory first and this one is refused.
     */
    fun install(activity: AppCompatActivity) {
        val inflater = activity.layoutInflater
        if (inflater.factory2 != null) return
        LayoutInflaterCompat.setFactory2(
            inflater,
            object : LayoutInflater.Factory2 {
                override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
                    val view = activity.delegate.createView(parent, name, context, attrs)
                        ?: create(inflater, context, name, attrs)
                    if (view != null) adopt(view, attrs)
                    return view
                }

                override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
                    onCreateView(null, name, context, attrs)
            },
        )
    }

    /**
     * Create the view AppCompat left to the platform, so that it can be adopted. Anything this
     * cannot build is answered with null and the platform builds it, as it always did.
     */
    private fun create(inflater: LayoutInflater, context: Context, name: String, attrs: AttributeSet): View? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        // Only a view that carries one of our stubs needs adopting; leave every other one alone.
        val background = attrs.getAttributeResourceValue(ANDROID_NS, "background", 0)
        if (background == 0 || !isStub(background)) return null
        return try {
            if (name.contains('.')) {
                inflater.createView(context, name, null, attrs)
            } else {
                PREFIXES.firstNotNullOfOrNull { prefix ->
                    try {
                        inflater.createView(context, name, prefix, attrs)
                    } catch (e: ClassNotFoundException) {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isStub(resId: Int): Boolean = when (resId) {
        R.drawable.sk_menu_frame, R.drawable.sk_settings_panel, R.drawable.sk_settings_group_header_closed,
        R.drawable.sk_settings_group_header_open, R.drawable.sk_settings_group_body, R.drawable.sk_search_pill,
        R.drawable.sk_status_pill, R.drawable.sk_shape_filter_selected, R.drawable.sk_badge_rin,
        R.drawable.sk_blocked_pill, R.drawable.sk_online_id_pill, R.drawable.sk_dialpad_button,
        R.drawable.sk_dialpad_digits_underline, R.drawable.sk_dialpad_panel_background,
        R.drawable.sk_round_outlined_button, R.drawable.sk_round_filled_button,
        R.drawable.sk_round_danger_button, R.drawable.sk_danger_pill, R.drawable.sk_call_card,
        R.drawable.sk_call_answer_pill, R.drawable.sk_call_decline_pill, R.drawable.sk_count_badge -> true
        else -> false
    }
}

/** A shape of [SkShapes] by name, for a background that a binding expression chooses. */
@BindingAdapter("skShape")
fun bindSkShape(view: View, name: String?) {
    if (name != null) SkShapes.byName(view.context, name)?.let { view.background = it }
}
