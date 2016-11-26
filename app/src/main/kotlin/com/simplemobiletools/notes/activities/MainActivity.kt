package com.simplemobiletools.notes.activities

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.simplemobiletools.filepicker.dialogs.ConfirmationDialog
import com.simplemobiletools.filepicker.extensions.toast
import com.simplemobiletools.filepicker.extensions.value
import com.simplemobiletools.notes.MyWidgetProvider
import com.simplemobiletools.notes.R
import com.simplemobiletools.notes.TYPE_NOTE
import com.simplemobiletools.notes.databases.DBHelper
import com.simplemobiletools.notes.dialogs.NewNoteDialog
import com.simplemobiletools.notes.dialogs.OpenNoteDialog
import com.simplemobiletools.notes.dialogs.RenameNoteDialog
import com.simplemobiletools.notes.dialogs.WidgetNoteDialog
import com.simplemobiletools.notes.extensions.getTextSize
import com.simplemobiletools.notes.models.Note
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_note.*

class MainActivity : SimpleActivity() {
    private var mCurrentNote: Note? = null

    lateinit var mDb: DBHelper
    lateinit var mNotes: List<Note>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mDb = DBHelper.newInstance(applicationContext)
        mNotes = mDb.getNotes()
        updateSelectedNote(config.currentNoteId)

        notes_fab.setOnClickListener { displayNewNoteDialog() }
        notes_fab.viewTreeObserver.addOnGlobalLayoutListener {
            val heightDiff = notes_coordinator.rootView.height - notes_coordinator.height
            notes_fab.visibility = if (heightDiff > dpToPx(this, 200f)) View.INVISIBLE else View.VISIBLE
        }
    }

    fun dpToPx(context: Context, valueInDp: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valueInDp, context.resources.displayMetrics)

    override fun onResume() {
        super.onResume()
        invalidateOptionsMenu()
        notes_view.setTextSize(TypedValue.COMPLEX_UNIT_PX, applicationContext.getTextSize())
    }

    override fun onPause() {
        super.onPause()
        saveText()
    }

    override fun onDestroy() {
        super.onDestroy()
        config.isFirstRun = false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val shouldBeVisible = mNotes.size > 1
        menu.apply {
            findItem(R.id.rename_note).isVisible = shouldBeVisible
            findItem(R.id.open_note).isVisible = shouldBeVisible
            findItem(R.id.delete_note).isVisible = shouldBeVisible
            findItem(R.id.change_widget_note).isVisible = shouldBeVisible
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.open_note -> {
                displayOpenNoteDialog()
                true
            }
            R.id.rename_note -> {
                displayRenameDialog()
                true
            }
            R.id.share -> {
                shareText()
                true
            }
            R.id.change_widget_note -> {
                showWidgetNotePicker()
                true
            }
            R.id.delete_note -> {
                displayDeleteNotePrompt()
                true
            }
            R.id.settings -> {
                startActivity(Intent(applicationContext, SettingsActivity::class.java))
                true
            }
            R.id.about -> {
                startActivity(Intent(applicationContext, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showWidgetNotePicker() {
        WidgetNoteDialog(this)
    }

    private fun displayRenameDialog() {
        RenameNoteDialog(this, mDb, mCurrentNote!!) {
            mCurrentNote = it
            current_note_title.text = it.title
        }
    }

    private fun updateSelectedNote(id: Int) {
        saveText()
        mCurrentNote = mDb.getNote(id)
        mNotes = mDb.getNotes()
        if (mCurrentNote != null) {
            config.currentNoteId = id
            notes_view.setText(mCurrentNote!!.value)
            current_note_title.text = mCurrentNote!!.title
        }

        current_note_label.visibility = if (mNotes.size <= 1) View.GONE else View.VISIBLE
        current_note_title.visibility = if (mNotes.size <= 1) View.GONE else View.VISIBLE
        updateWidget(applicationContext)
    }

    fun displayNewNoteDialog() {
        NewNoteDialog(this, mDb) {
            saveText()
            val newNote = Note(0, it, "", TYPE_NOTE)
            val id = mDb.insertNote(newNote)
            updateSelectedNote(id)
            mNotes = mDb.getNotes()
            invalidateOptionsMenu()
        }
    }

    private fun displayDeleteNotePrompt() {
        val message = String.format(getString(R.string.delete_note_prompt_message), mCurrentNote!!.title)
        ConfirmationDialog(this, message) {
            deleteNote()
        }
    }

    private fun deleteNote() {
        if (mNotes.size <= 1)
            return

        mDb.deleteNote(mCurrentNote!!.id)
        mNotes = mDb.getNotes()

        val firstNoteId = mNotes[0].id
        updateSelectedNote(firstNoteId)
        config.widgetNoteId = firstNoteId
        invalidateOptionsMenu()
    }

    private fun displayOpenNoteDialog() {
        OpenNoteDialog(this) {
            updateSelectedNote(it)
        }
    }

    private fun saveText() {
        if (mCurrentNote == null)
            return

        val newText = notes_view.value
        val oldText = mCurrentNote!!.value
        if (newText != oldText) {
            toast(R.string.note_saved)
            mCurrentNote!!.value = newText
            mDb.updateNote(mCurrentNote!!)
        }

        hideKeyboard()
        updateWidget(applicationContext)
    }

    private fun shareText() {
        val text = notes_view.value
        if (text.isEmpty()) {
            toast(R.string.cannot_share_empty_text)
            return
        }

        val res = resources
        val shareTitle = res.getString(R.string.share_via)
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, res.getString(R.string.simple_note))
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            startActivity(Intent.createChooser(this, shareTitle))
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(notes_view.windowToken, 0)
    }

    fun updateWidget(context: Context) {
        val widgetManager = AppWidgetManager.getInstance(context)
        val ids = widgetManager.getAppWidgetIds(ComponentName(context, MyWidgetProvider::class.java))

        Intent(context, MyWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(this)
        }
    }
}
