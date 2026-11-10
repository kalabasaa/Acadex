package com.student.acadex

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.transition.TransitionManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Group key for notes that have no subject. Real subject ids are always > 0. */
private const val UNCATEGORIZED = 0L

private class NoteGroupViews(val key: Long, val body: View, val arrow: ImageView)

class NotesFragment : Fragment(R.layout.fragment_notes) {
    // the group that is open; kept on the fragment so it is still open after coming back from the editor
    private var openKey: Long? = null
    // 0 = Subject tab (grouped), 1 = Note tab (all notes, newest first)
    private var selectedTab = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val search = view.findViewById<TextInputEditText>(R.id.etSearch)
        val list = view.findViewById<LinearLayout>(R.id.notesList)
        val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())
        val inflater = LayoutInflater.from(requireContext())

        fun snippet(content: String): String {
            val flat = content.replace('\n', ' ').trim()
            return if (flat.length > 60) flat.take(60) + "…" else flat
        }

        fun refresh() {
            val q = search.text?.toString()?.trim().orEmpty()
            val searching = q.isNotEmpty()
            // one query: already newest-updated first, so every group keeps that order
            val notes = repo.notes(search = q.ifEmpty { null })

            if (selectedTab == 1) {
                list.fill(notes, R.layout.item_row) { v, n ->
                    v.bindRow(n.subjectCode, n.title, snippet(n.content), dateFmt.format(Date(n.updatedAt))) {
                        go(R.id.addNoteFragment, id = n.id)
                    }
                }
                return
            }
            val subjects = repo.subjects() // alphabetical
            val known = subjects.map { it.id }.toSet()
            // each note lands in exactly one group
            val bySubject = notes.groupBy { n -> n.subjectId?.takeIf { it in known } ?: UNCATEGORIZED }

            val groups = ArrayList<Triple<Long, String, List<Note>>>()
            subjects.forEach { s -> bySubject[s.id]?.let { groups.add(Triple(s.id, s.name, it)) } }
            bySubject[UNCATEGORIZED]?.let { groups.add(Triple(UNCATEGORIZED, "Uncategorized", it)) }

            if (openKey != null && groups.none { it.first == openKey }) openKey = null

            list.removeAllViews()
            if (groups.isEmpty()) {
                list.addView(inflater.inflate(R.layout.item_empty, list, false))
                return
            }

            val views = ArrayList<NoteGroupViews>()
            groups.forEach { (key, name, groupNotes) ->
                val card = inflater.inflate(R.layout.item_note_group, list, false)
                card.findViewById<TextView>(R.id.tvGroupName).text = name
                card.findViewById<TextView>(R.id.tvGroupCount).text =
                    if (groupNotes.size == 1) "1 note" else "${groupNotes.size} notes"
                val body = card.findViewById<LinearLayout>(R.id.groupNotes)
                val arrow = card.findViewById<ImageView>(R.id.arrowGroup)
                body.fill(groupNotes, R.layout.item_row) { v, n ->
                    v.bindRow(null, n.title, snippet(n.content), dateFmt.format(Date(n.updatedAt))) {
                        go(R.id.addNoteFragment, id = n.id)
                    }
                }
                val open = searching || openKey == key // while searching, every match is shown
                body.visibility = if (open) View.VISIBLE else View.GONE
                arrow.rotation = if (open) 180f else 0f
                val group = NoteGroupViews(key, body, arrow)
                views.add(group)

                // same pattern as the Settings accordions: delayed transition + 200ms arrow rotation
                card.findViewById<View>(R.id.groupHeader).setOnClickListener {
                    val isOpen = group.body.visibility == View.VISIBLE
                    TransitionManager.beginDelayedTransition(view as ViewGroup)
                    views.forEach { g ->
                        val show = if (g === group) !isOpen else (searching && g.body.visibility == View.VISIBLE)
                        g.body.visibility = if (show) View.VISIBLE else View.GONE
                        g.arrow.animate().rotation(if (show) 180f else 0f).setDuration(200).start()
                    }
                    openKey = if (isOpen) null else key
                }
                list.addView(card)
            }
        }

        val tabs = view.findViewById<TabLayout>(R.id.tabNotes)
        tabs.addTab(tabs.newTab().setText("Subject"), selectedTab == 0)
        tabs.addTab(tabs.newTab().setText("Note"), selectedTab == 1)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedTab = tab.position
                refresh()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })

        // a new note can always be created without a subject; if a subject group is open it is only pre-selected
        view.findViewById<View>(R.id.btnNewNote).setOnClickListener {
            go(R.id.addNoteFragment, subjectId = if (selectedTab == 0) openKey?.takeIf { it != UNCATEGORIZED } ?: 0L else 0L)
        }

        refresh()
    }
}

class AddNoteFragment : Fragment(R.layout.fragment_add_note) {
    private var subjectId: Long? = null
    private lateinit var backCallback: OnBackPressedCallback

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("subject", subjectId ?: 0L)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val subjects = repo.subjects()
        val title = view.findViewById<EditText>(R.id.etTitle)
        val content = view.findViewById<EditText>(R.id.etContent)
        val delete = view.findViewById<View>(R.id.btnDelete)
        val tvSubject = view.findViewById<TextView>(R.id.tvSubject)
        val arrow = view.findViewById<ImageView>(R.id.arrowSubject)

        val existing = repo.note(argId)
        val initialSubject: Long? = existing?.subjectId ?: argSubjectId.takeIf { it != 0L }
        subjectId = if (savedInstanceState?.containsKey("subject") == true) {
            savedInstanceState.getLong("subject").takeIf { it != 0L }
        } else initialSubject

        fun showSubject() {
            tvSubject.text = subjects.firstOrNull { it.id == subjectId }?.name ?: "No subject"
        }
        showSubject()

        // Subject dropdown: same chevron and 200ms rotation as the Settings accordions
        view.findViewById<View>(R.id.rowSubject).setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            popup.menu.add(0, 0, 0, "No subject")
            subjects.forEachIndexed { i, s -> popup.menu.add(0, i + 1, i + 1, s.name) }
            popup.setOnMenuItemClickListener { item ->
                subjectId = if (item.itemId == 0) null else subjects[item.itemId - 1].id
                showSubject()
                true
            }
            popup.setOnDismissListener { arrow.animate().rotation(0f).setDuration(200).start() }
            arrow.animate().rotation(180f).setDuration(200).start()
            popup.show()
        }

        if (existing != null) {
            title.setText(existing.title)
            content.setText(existing.content)
        }
        val initialTitle = existing?.title.orEmpty()
        val initialContent = existing?.content.orEmpty()

        fun isDirty() = title.text.toString() != initialTitle ||
            content.text.toString() != initialContent ||
            subjectId != initialSubject

        fun leave() {
            backCallback.isEnabled = false
            back()
        }

        fun save(): Boolean {
            val t = title.text.toString().trim()
            if (t.isEmpty()) {
                toast("Title is required")
                return false
            }
            val note = Note(
                id = existing?.id ?: 0,
                subjectId = subjectId,
                title = t,
                content = content.text.toString().trim(),
                createdAt = existing?.createdAt ?: 0
            )
            if (repo.saveNote(note)) {
                leave()
                return true
            }
            toast("Could not save")
            return false
        }

        view.findViewById<View>(R.id.btnSave).setOnClickListener { save() }

        if (existing != null) {
            delete.visibility = View.VISIBLE
            delete.setOnClickListener {
                confirm("Delete \"${existing.title}\"?") {
                    repo.deleteNote(existing.id)
                    leave()
                }
            }
        }

        // Back with unsaved text asks first instead of silently throwing it away
        backCallback = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (!isDirty()) {
                leave()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Save changes?")
                    .setPositiveButton("Save") { _, _ -> save() }
                    .setNegativeButton("Discard") { _, _ -> leave() }
                    .setNeutralButton("Keep editing", null)
                    .show()
            }
        }
    }
}
