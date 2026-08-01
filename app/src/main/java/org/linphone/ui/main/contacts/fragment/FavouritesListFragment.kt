/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.contacts.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.shiroikuma.SkDimen
import org.linphone.shiroikuma.SkTheme
import org.linphone.databinding.FavouritesListFragmentBinding
import org.linphone.ui.main.contacts.adapter.ContactsListAdapter
import org.linphone.ui.main.contacts.viewmodel.ContactsListViewModel
import org.linphone.ui.main.fragment.AbstractMainFragment
import org.linphone.utils.Event

/**
 * shiroikuma fork: the Favorites tab, sitting beside Contacts in the bottom bar.
 *
 * Upstream had no such tab — favourites were a horizontal strip pinned above the Contacts list,
 * which cost that list its top and could show only a handful. This is the same screen as Contacts,
 * reusing its view model and its row adapter, bound to the favourites list instead: same rows,
 * same letter-heading machinery, same settable sizing.
 */
@UiThread
class FavouritesListFragment : AbstractMainFragment() {
    companion object {
        private const val TAG = "[Favourites List Fragment]"
        private const val DRAG_ALPHA = 0.9f
    }

    private lateinit var binding: FavouritesListFragmentBinding

    private lateinit var listViewModel: ContactsListViewModel

    private lateinit var adapter: ContactsListAdapter

    override fun onDefaultAccountChanged() {
        Log.i("$TAG Default account changed, refreshing favourites list")
        listViewModel.applyCurrentDefaultAccountFilter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ContactsListAdapter(favourites = true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FavouritesListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listViewModel = ViewModelProvider(this)[ContactsListViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel
        observeToastEvents(listViewModel)

        binding.favouritesListSwipeRefresh.isEnabled = false

        // shiroikuma fork: a tile grid, not a list — the sister address book's Favorites screen.
        // setHasFixedSize is deliberately NOT set: a dragged tile changes the grid's height.
        binding.favouritesList.layoutManager = GridLayoutManager(
            requireContext(),
            SkTheme.dimenDp(requireContext(), SkDimen.FAVOURITE_COLUMNS).coerceAtLeast(1)
        )
        binding.favouritesList.outlineProvider = outlineProvider
        attachDragToReorder()

        adapter.contactClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                sharedViewModel.showContactEvent.value = Event(model.friend.refKey.orEmpty())
            }
        }

        listViewModel.favouritesList.observe(viewLifecycleOwner) {
            adapter.submitContacts(it, filtering = listViewModel.isListFiltered.value == true)

            // Wait for the adapter to have items before handing it to the RecyclerView, otherwise
            // the scroll position isn't retained (same reason as the contacts list).
            if (binding.favouritesList.adapter != adapter) {
                binding.favouritesList.adapter = adapter
            }

            Log.i("$TAG Favourites list updated with [${it.size}] items")
            listViewModel.fetchInProgress.value = false
        }

        // AbstractMainFragment related
        listViewModel.title.value = getString(R.string.bottom_navigation_favourites_label)
        setViewModel(listViewModel)
        initViews(
            binding.slidingPaneLayout,
            binding.topBar,
            binding.bottomNavBar,
            R.id.favouritesListFragment
        )
    }

    override fun onResume() {
        super.onResume()

        // The column count is settable on the UI page, so re-read it on the way back in.
        val columns = SkTheme.dimenDp(requireContext(), SkDimen.FAVOURITE_COLUMNS).coerceAtLeast(1)
        (binding.favouritesList.layoutManager as? GridLayoutManager)?.let {
            if (it.spanCount != columns) it.spanCount = columns
        }
        adapter.rebuildSections()
    }

    /**
     * shiroikuma fork: long-press a tile and drag it anywhere on the grid. [ItemTouchHelper] is
     * what handles a grid properly — it swaps as the tile passes its neighbours, in all four
     * directions — and the adapter writes the new arrangement to preferences as it goes.
     */
    private fun attachDragToReorder() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveFavourite(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    // Same feel as the drawer's account list: the tile lifts and buzzes.
                    viewHolder?.itemView?.alpha = DRAG_ALPHA
                    viewHolder?.itemView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1f
            }

            override fun isLongPressDragEnabled(): Boolean = true
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.favouritesList)
    }
}
