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
package org.linphone.ui.main.contacts.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.annotation.SuppressLint
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.databinding.ContactFavouriteListCellBinding
import org.linphone.databinding.ContactListCellBinding
import org.linphone.databinding.SkContactsSectionHeaderBinding
import org.linphone.shiroikuma.SkContacts
import org.linphone.shiroikuma.SkFavourites
import org.linphone.shiroikuma.SkStyler
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.Event

/**
 * shiroikuma fork: one row of the list — either a contact, or the letter heading standing above a
 * run of them. Upstream's adapter held bare contacts and drew the letter inside the first row of
 * each run, which cannot be tapped and so cannot fold.
 */
class ContactListItem(val contact: ContactAvatarModel?, val section: SkContacts.Section? = null)

class ContactsListAdapter(
    private val favourites: Boolean = false,
    private val disableLongClick: Boolean = false,
    /** shiroikuma fork: which list's fold state this adapter reads and writes. */
    private val sectionScope: String = SkContacts.SCOPE_CONTACTS
) : ListAdapter<ContactListItem, RecyclerView.ViewHolder>(ContactDiffCallback()) {
    companion object {
        private const val CONTACT_TYPE = 0
        private const val SECTION_TYPE = 1
    }

    var selectedAdapterPosition = -1

    /** The contacts as last submitted, so a fold can rebuild the display list without a refetch. */
    private var contacts: List<ContactAvatarModel> = emptyList()

    /** While filtering, every section stands open — a fold would silently change the result. */
    private var filtering = false

    /**
     * The favourites in their current arrangement. Held separately from [contacts] because a drag
     * has to compose with the drag before it: ItemTouchHelper reports each swap as it happens, and
     * reading back the already-submitted list would lose any move the diff had not applied yet.
     */
    private var favouriteOrder = mutableListOf<ContactAvatarModel>()

    val contactClickedEvent: MutableLiveData<Event<ContactAvatarModel>> by lazy {
        MutableLiveData()
    }

    val contactLongClickedEvent: MutableLiveData<Event<ContactAvatarModel>> by lazy {
        MutableLiveData()
    }

    /**
     * shiroikuma fork: the entry point the fragment submits through. Builds the letter headings
     * (kana rows, then A–Z, then ＃ — see [SkContacts]) around the contacts and submits the lot.
     * The favourites strip and a filtered list are never sectioned.
     */
    @UiThread
    fun submitContacts(list: List<ContactAvatarModel>, filtering: Boolean = false) {
        contacts = list
        this.filtering = filtering
        submitList(buildDisplayList())
    }

    private fun buildDisplayList(): List<ContactListItem> {
        val context = coreContext.context
        if (favourites) {
            // Tiles, never letters — and in the order 白い熊 dragged them into.
            favouriteOrder = SkFavourites.applyOrder(context, contacts) { it.id }.toMutableList()
            return favouriteOrder.map { ContactListItem(it) }
        }
        if (filtering || !SkContacts.sectionsEnabled(context)) {
            return contacts.map { ContactListItem(it) }
        }

        val buckets = LinkedHashMap<String, MutableList<ContactAvatarModel>>()
        for (contact in contacts) {
            // The reading when the address book has one, else the name — see skSectionKey.
            val title = SkContacts.sectionTitleFor(contact.skSectionKey ?: contact.sortingName)
            buckets.getOrPut(title) { mutableListOf() }.add(contact)
        }

        val items = mutableListOf<ContactListItem>()
        var previousExpanded = false
        // Members keep the order the core handed them in; only the sections themselves are ranked.
        for ((title, members) in buckets.entries.sortedBy { SkContacts.sectionRank(it.key) }) {
            val open = SkContacts.isExpanded(context, sectionScope, title)
            items.add(
                ContactListItem(
                    null,
                    SkContacts.Section(title, members.size, open, showTopDivider = open || previousExpanded)
                )
            )
            if (open) {
                members.forEach { items.add(ContactListItem(it)) }
            }
            previousExpanded = open
        }
        return items
    }

    /** Re-read the fold state and the UI page's settings, and redraw. */
    @UiThread
    @SuppressLint("NotifyDataSetChanged")
    fun rebuildSections() {
        if (contacts.isEmpty()) return
        submitList(buildDisplayList())
        notifyDataSetChanged()
    }

    /**
     * shiroikuma fork: a tile dragged from [from] to [to]. The new arrangement is stored at once,
     * so it survives the list being rebuilt under us — and a restart.
     */
    @UiThread
    fun moveFavourite(from: Int, to: Int) {
        if (from !in favouriteOrder.indices || to !in favouriteOrder.indices) return
        favouriteOrder.add(to, favouriteOrder.removeAt(from))
        SkFavourites.setOrder(coreContext.context, favouriteOrder.map { it.id })
        submitList(favouriteOrder.map { ContactListItem(it) })
    }

    override fun getItemViewType(position: Int): Int {
        return try {
            if (getItem(position).section != null) SECTION_TYPE else CONTACT_TYPE
        } catch (ioobe: IndexOutOfBoundsException) {
            CONTACT_TYPE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == SECTION_TYPE) {
            return SectionViewHolder(
                SkContactsSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
        if (favourites) {
            val binding: ContactFavouriteListCellBinding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.contact_favourite_list_cell,
                parent,
                false
            )
            val viewHolder = FavouriteViewHolder(binding)
            binding.apply {
                lifecycleOwner = parent.findViewTreeLifecycleOwner()

                setOnClickListener {
                    contactClickedEvent.value = Event(model!!)
                }

                setOnLongClickListener {
                    selectedAdapterPosition = viewHolder.bindingAdapterPosition
                    root.isSelected = true
                    contactLongClickedEvent.value = Event(model!!)
                    true
                }
            }
            return viewHolder
        } else {
            val binding: ContactListCellBinding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.contact_list_cell,
                parent,
                false
            )
            val viewHolder = ViewHolder(binding)
            binding.apply {
                lifecycleOwner = parent.findViewTreeLifecycleOwner()

                setOnClickListener {
                    contactClickedEvent.value = Event(model!!)
                }

                if (!disableLongClick) {
                    setOnLongClickListener {
                        selectedAdapterPosition = viewHolder.bindingAdapterPosition
                        root.isSelected = true
                        contactLongClickedEvent.value = Event(model!!)
                        true
                    }
                }
            }
            return viewHolder
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when {
            holder is SectionViewHolder -> holder.bind(item.section!!)
            favourites -> (holder as FavouriteViewHolder).bind(item.contact!!)
            else -> (holder as ViewHolder).bind(item.contact!!)
        }
    }

    fun resetSelection() {
        notifyItemChanged(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    inner class ViewHolder(
        val binding: ContactListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(contactModel: ContactAvatarModel) {
            with(binding) {
                model = contactModel

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                // shiroikuma fork: with letter headings on, the letter is written once in the
                // heading above the run — repeating it inside the row would say it twice, and its
                // reserved column would push the photo off the edge.
                sectioned = sectioned()
                firstContactStartingByThatLetter = if (sectioned()) {
                    false
                } else {
                    val previousItem = bindingAdapterPosition - 1
                    val previousLetter = if (previousItem >= 0) {
                        getItem(previousItem).contact?.sortingName?.get(0).toString()
                    } else {
                        ""
                    }
                    val currentLetter = contactModel.sortingName?.get(0).toString()
                    previousLetter.isEmpty() || currentLetter != previousLetter
                }

                SkStyler.styleContactRow(
                    row = binding.root,
                    avatar = binding.avatar.avatar,
                    name = binding.name,
                    number = binding.number,
                    divider = binding.separator
                )

                executePendingBindings()
            }
        }
    }

    inner class FavouriteViewHolder(
        val binding: ContactFavouriteListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(contactModel: ContactAvatarModel) {
            with(binding) {
                model = contactModel

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                SkStyler.styleFavouriteTile(binding.root, binding.avatar.avatar, binding.name)

                executePendingBindings()
            }
        }
    }

    /** shiroikuma fork: a letter heading — tapping it folds or unfolds its section, and remembers. */
    inner class SectionViewHolder(
        val binding: SkContactsSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(section: SkContacts.Section) {
            binding.skSectionTitle.text = "${section.title} (${section.count})"
            binding.skSectionFoldIndicator.text = if (section.expanded) {
                SkContacts.UNFOLDED_INDICATOR
            } else {
                SkContacts.FOLDED_INDICATOR
            }
            SkStyler.styleContactSection(
                holder = binding.root,
                divider = binding.skSectionDivider,
                title = binding.skSectionTitle,
                indicator = binding.skSectionFoldIndicator,
                underline = binding.skSectionUnderline,
                content = binding.skSectionContent,
                showDivider = section.showTopDivider
            )
            binding.root.setOnClickListener {
                SkContacts.toggleSection(binding.root.context, sectionScope, section.title)
                rebuildSections()
            }
        }
    }

    /** Whether the list is currently drawn with letter headings at all. */
    private fun sectioned(): Boolean =
        !favourites && !filtering && SkContacts.sectionsEnabled(coreContext.context)

    private class ContactDiffCallback : DiffUtil.ItemCallback<ContactListItem>() {
        override fun areItemsTheSame(oldItem: ContactListItem, newItem: ContactListItem): Boolean {
            val oldSection = oldItem.section
            val newSection = newItem.section
            if (oldSection != null || newSection != null) {
                return oldSection?.title == newSection?.title
            }
            return oldItem.contact?.id == newItem.contact?.id
        }

        override fun areContentsTheSame(oldItem: ContactListItem, newItem: ContactListItem): Boolean {
            val oldSection = oldItem.section
            val newSection = newItem.section
            if (oldSection != null || newSection != null) {
                return oldSection == newSection
            }
            val old = oldItem.contact ?: return false
            return newItem.contact?.compare(old) == true
        }
    }
}
