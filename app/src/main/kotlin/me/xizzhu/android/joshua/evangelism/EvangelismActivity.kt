package me.xizzhu.android.joshua.evangelism

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.util.AtomicFile
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.FileNotFoundException
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import me.xizzhu.android.joshua.R
import me.xizzhu.android.joshua.infra.applySystemBarInsets
import org.json.JSONArray
import org.json.JSONObject

class EvangelismActivity : AppCompatActivity() {
    private lateinit var store: AtomicFile
    private lateinit var content: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private var contacts = JSONArray()
    private var screen = "contacts"
    private var selected = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(FLAG_SECURE)
        store = AtomicFile(File(noBackupFilesDir, "personal-evangelism.json"))
        try {
            contacts = JSONArray(store.readFully().toString(Charsets.UTF_8))
        } catch (_: FileNotFoundException) {
            // First launch.
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open tracker records.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        screen = savedInstanceState?.getString("screen") ?: "contacts"
        selected = savedInstanceState?.getInt("selected", -1) ?: -1
        val root = column()
        toolbar = MaterialToolbar(this).apply {
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationIconTint(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            navigationContentDescription = "Back"
            setNavigationOnClickListener { back() }
        }
        root.addView(toolbar, MATCH_PARENT, WRAP_CONTENT)
        content = column().apply { setPadding(dp(16), dp(8), dp(16), dp(24)) }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
        applySystemBarInsets(root)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen)
        outState.putInt("selected", selected)
        super.onSaveInstanceState(outState)
    }

    private fun back() {
        if (screen == "contacts") finish() else navigate("contacts")
    }

    private fun navigate(destination: String, index: Int = -1) {
        screen = destination
        selected = index
        render()
        (content.parent as ScrollView).scrollTo(0, 0)
    }

    private fun render() {
        content.removeAllViews()
        toolbar.title = when (screen) {
            "stats" -> "Statistics"
            "detail" -> contacts.optJSONObject(selected)?.optString("name") ?: getString(R.string.action_evangelism)
            else -> getString(R.string.action_evangelism)
        }
        when (screen) {
            "stats" -> statistics()
            "detail" -> contacts.optJSONObject(selected)?.let { contactDetails(it) } ?: navigate("contacts")
            else -> contactList()
        }
    }

    private fun indices() = (0 until contacts.length()).filter { contacts.optJSONObject(it) != null }

    private fun contactList() {
        val groups = indices().groupBy { status(contacts.getJSONObject(it)) }
        val active = groups[Status.ACTIVE].orEmpty().sortedBy { contacts.getJSONObject(it).optLong("due") }
        val due = active.count { isDue(contacts.getJSONObject(it)) }
        content.card {
            heading("Follow-up overview")
            label("${active.size} active · $due due or overdue")
            button("Add contact") { edit(null) }
            button("View statistics", secondary = true) { navigate("stats") }
        }
        if (contacts.length() == 0) {
            content.card {
                heading("No contacts yet")
                label("Add a contact to start tracking weekly follow-ups.")
            }
        }
        Status.values().forEach { state ->
            val entries = if (state == Status.ACTIVE) active else groups[state].orEmpty()
            if (entries.isNotEmpty()) content.heading(state.label)
            entries.forEach { index ->
                val record = contacts.getJSONObject(index)
                content.card {
                    heading(record.optString("name"))
                    label(if (state == Status.ACTIVE) dueLabel(record) else state.label)
                    val location = record.optString("location")
                    if (location.isNotBlank()) label(location)
                    button("View contact", secondary = true) { navigate("detail", index) }
                }
            }
        }
    }

    private fun statistics() {
        val records = indices().map { contacts.getJSONObject(it) }
        val active = records.filter { status(it) == Status.ACTIVE }
        val converted = records.count { status(it) == Status.CONVERTED }
        val joined = records.count { status(it) == Status.JOINED_CHURCH }
        content.card {
            heading("Contacts")
            label("Total · ${records.size}")
            label("Active · ${active.size}")
            label("Due or overdue · ${active.count(::isDue)}")
        }
        content.card {
            heading("Outcomes")
            label("Completed · ${converted + joined}")
            label("Converted · $converted")
            label("Joined a church · $joined")
            label("Follow-ups recorded · ${records.sumOf { it.optJSONArray("updates")?.length() ?: 0 }}")
        }
    }

    private fun contactDetails(record: JSONObject) {
        val state = status(record)
        val index = selected
        content.card {
            heading(if (state == Status.ACTIVE) dueLabel(record) else state.label)
            listOf("phone" to "Phone", "location" to "Location", "address" to "Address", "prayer" to "Prayer request")
                .forEach { (key, caption) ->
                    record.optString(key).takeIf { it.isNotBlank() }?.let {
                        heading(caption)
                        label(it)
                    }
                }
            if (state == Status.ACTIVE) button("Log follow-up") { followUp(index) }
            button("Edit contact", secondary = true) { edit(index) }
        }
        content.heading("Follow-up history")
        val history = record.optJSONArray("updates") ?: JSONArray()
        if (history.length() == 0) content.label("No follow-ups recorded.")
        for (position in history.length() - 1 downTo 0) {
            val entry = history.getJSONObject(position)
            content.card {
                heading(date(entry.optLong("date")))
                label(status(entry).label)
                entry.optString("discussion").takeIf { it.isNotBlank() }?.let { label(it) }
                entry.optString("prayer").takeIf { it.isNotBlank() }?.let { label("Prayer request: $it") }
            }
        }
        content.button("Delete contact", secondary = true) {
            MaterialAlertDialogBuilder(this).setTitle("Delete contact?")
                .setMessage("This removes the contact and their follow-up history from this device.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete") { _, _ ->
                    if (save { it.remove(index) }) navigate("contacts")
                }.create().showSecurely()
        }
    }

    private fun edit(index: Int?) {
        val record = index?.let { contacts.getJSONObject(it) }
        val form = column()
        val name = form.field("Name", record?.optString("name").orEmpty())
        val phone = form.field("Phone number", record?.optString("phone").orEmpty(), InputType.TYPE_CLASS_PHONE)
        val location = form.field("Location", record?.optString("location").orEmpty())
        val address = form.field("Address", record?.optString("address").orEmpty(), multiline = true)
        val prayer = form.field("Prayer request", record?.optString("prayer").orEmpty(), multiline = true)
        formDialog(if (index == null) "Add contact" else "Edit contact", form) {
            if (name.value().isBlank()) {
                name.error = "Enter a name"
                false
            } else save { rows ->
                val entry = index?.let { rows.getJSONObject(it) } ?: JSONObject()
                    .put("status", Status.ACTIVE.name)
                    .put("due", nextWeek())
                    .put("updates", JSONArray())
                    .also { rows.put(it) }
                entry.put("name", name.value()).put("phone", phone.value())
                    .put("location", location.value()).put("address", address.value()).put("prayer", prayer.value())
            }.also { if (it) render() }
        }
    }

    private fun followUp(index: Int) {
        val form = column()
        val discussion = form.field("Discussion summary", multiline = true)
        val prayer = form.field("Prayer request", multiline = true)
        form.heading("Outcome")
        val outcomes = android.widget.RadioGroup(this)
        val outcomeIds = Status.values().associateWith { View.generateViewId() }
        outcomeIds.forEach { (state, viewId) ->
            outcomes.addView(com.google.android.material.radiobutton.MaterialRadioButton(this).apply {
                id = viewId
                text = if (state == Status.ACTIVE) "Continue weekly follow-up" else state.label
                minimumHeight = dp(48)
            })
        }
        outcomes.check(outcomeIds.getValue(Status.ACTIVE))
        form.addView(outcomes)
        formDialog("Log follow-up", form) {
            val outcome = outcomeIds.entries.first { it.value == outcomes.checkedRadioButtonId }.key
            if (outcome == Status.ACTIVE && discussion.value().isBlank() && prayer.value().isBlank()) {
                discussion.error = "Enter a summary or prayer request"
                false
            } else save { rows ->
                val entry = rows.getJSONObject(index)
                entry.getJSONArray("updates").put(JSONObject().put("date", System.currentTimeMillis())
                    .put("discussion", discussion.value()).put("prayer", prayer.value()).put("status", outcome.name))
                entry.put("status", outcome.name)
                if (outcome == Status.ACTIVE) entry.put("due", nextWeek()) else entry.remove("due")
            }.also { if (it) render() }
        }
    }

    private fun formDialog(title: String, form: LinearLayout, onSave: () -> Boolean) {
        form.setPadding(dp(20), dp(8), dp(20), dp(8))
        val dialog = MaterialAlertDialogBuilder(this).setTitle(title)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (onSave()) dialog.dismiss()
            }
        }
        dialog.window?.setSoftInputMode(SOFT_INPUT_ADJUST_RESIZE)
        dialog.showSecurely()
    }

    private fun androidx.appcompat.app.AlertDialog.showSecurely() {
        window?.addFlags(FLAG_SECURE)
        show()
    }

    private fun save(update: (JSONArray) -> Unit): Boolean {
        try {
            val copy = JSONArray(contacts.toString())
            update(copy)
            val stream = store.startWrite()
            try {
                stream.write(copy.toString().toByteArray(Charsets.UTF_8))
                store.finishWrite(stream)
            } catch (error: Exception) {
                store.failWrite(stream)
                throw error
            }
            contacts = copy
            return true
        } catch (_: Exception) {
            Toast.makeText(this, "Could not save tracker changes.", Toast.LENGTH_LONG).show()
            return false
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun LinearLayout.card(children: LinearLayout.() -> Unit) {
        addView(MaterialCardView(this@EvangelismActivity).apply {
            radius = dp(16).toFloat()
            strokeWidth = dp(1)
            cardElevation = 0f
            addView(column().apply {
                setPadding(dp(16), dp(14), dp(16), dp(14))
                children()
            })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
    }

    private fun LinearLayout.heading(value: String) = label(value, true)

    private fun LinearLayout.label(value: String, heading: Boolean = false) {
        addView(TextView(context).apply {
            text = value
            textSize = if (heading) 19f else 16f
            if (heading) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            setPadding(0, dp(4), 0, dp(6))
        })
    }

    private fun LinearLayout.button(title: String, secondary: Boolean = false, click: () -> Unit) {
        val style = if (secondary) com.google.android.material.R.attr.materialButtonOutlinedStyle
            else com.google.android.material.R.attr.materialButtonStyle
        addView(MaterialButton(context, null, style).apply {
            text = title
            isAllCaps = false
            minimumHeight = dp(48)
            setOnClickListener { click() }
        }, MATCH_PARENT, WRAP_CONTENT)
    }

    private fun LinearLayout.field(
        title: String,
        value: String = "",
        type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
        multiline: Boolean = false
    ): TextInputEditText {
        val container = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle)
        container.hint = title
        val input = TextInputEditText(container.context).apply {
            setSingleLine(!multiline)
            inputType = if (multiline) type or InputType.TYPE_TEXT_FLAG_MULTI_LINE else type
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setText(value)
        }
        container.addView(input, MATCH_PARENT, WRAP_CONTENT)
        addView(container, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
        return input
    }

    private fun TextInputEditText.value() = text.toString().trim()
    private fun date(time: Long) = DateFormat.getDateInstance().format(Date(time))
    private fun isDue(record: JSONObject) = record.optLong("due") <= System.currentTimeMillis()
    private fun dueLabel(record: JSONObject): String {
        val due = record.optLong("due")
        val today = Calendar.getInstance().apply { startOfDay() }.timeInMillis
        return when {
            due < today -> "Overdue · ${date(due)}"
            isDue(record) -> "Due today"
            else -> "Next follow-up · ${date(due)}"
        }
    }
    private fun nextWeek() = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 7)
        startOfDay()
    }.timeInMillis

    private fun Calendar.startOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun status(record: JSONObject) = Status.values().firstOrNull { it.name == record.optString("status") } ?: Status.ACTIVE

    private enum class Status(val label: String) {
        ACTIVE("Active"), CONVERTED("Converted"), JOINED_CHURCH("Joined a church")
    }
}
