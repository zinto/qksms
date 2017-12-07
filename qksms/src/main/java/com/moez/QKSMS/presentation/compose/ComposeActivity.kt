package com.moez.QKSMS.presentation.compose

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import com.google.android.flexbox.FlexboxLayoutManager
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import com.moez.QKSMS.R
import com.moez.QKSMS.common.di.appComponent
import com.moez.QKSMS.common.util.extensions.setBackgroundTint
import com.moez.QKSMS.common.util.extensions.setVisible
import com.moez.QKSMS.common.util.extensions.showKeyboard
import com.moez.QKSMS.data.model.Contact
import com.moez.QKSMS.data.model.Message
import com.moez.QKSMS.presentation.base.QkActivity
import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.compose_activity.*
import kotlinx.android.synthetic.main.toolbar_chips.*
import javax.inject.Inject


class ComposeActivity : QkActivity<ComposeViewModel>(), ComposeView {

    override val viewModelClass = ComposeViewModel::class
    override val queryChangedIntent: Observable<CharSequence> by lazy { chipsAdapter.textChanges }
    override val chipSelectedIntent: Subject<Contact> by lazy { contactsAdapter.contactSelected }
    override val chipDeletedIntent: Subject<Contact> by lazy { chipsAdapter.chipDeleted }
    override val menuReadyIntent: Observable<Unit> = menu.map { Unit }
    override val callIntent: Subject<Unit> = PublishSubject.create()
    override val archiveIntent: Subject<Unit> = PublishSubject.create()
    override val deleteIntent: Subject<Unit> = PublishSubject.create()
    override val copyTextIntent: Subject<Message> = PublishSubject.create()
    override val forwardMessageIntent: Subject<Message> = PublishSubject.create()
    override val deleteMessageIntent: Subject<Message> = PublishSubject.create()
    override val textChangedIntent by lazy { message.textChanges() }
    override val attachIntent by lazy { attach.clicks() }
    override val sendIntent by lazy { send.clicks() }

    private val layoutManager by lazy { LinearLayoutManager(this).apply { stackFromEnd = true } }

    @Inject lateinit var chipsAdapter: ChipsAdapter
    @Inject lateinit var contactsAdapter: ContactAdapter
    @Inject lateinit var messageAdapter: MessagesAdapter

    init {
        appComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.compose_activity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        viewModel.bindView(this)

        chipsAdapter.view = chips

        chips.itemAnimator = null
        chips.layoutManager = FlexboxLayoutManager(this)
        chips.adapter = chipsAdapter

        contacts.itemAnimator = null
        contacts.layoutManager = LinearLayoutManager(this)
        contacts.adapter = contactsAdapter

        setupMessagesAdapter()

        messageList.layoutManager = layoutManager
        messageList.adapter = messageAdapter

        val states = arrayOf(
                intArrayOf(android.R.attr.state_enabled),
                intArrayOf(-android.R.attr.state_enabled))

        disposables += Observables
                .combineLatest(colors.theme, colors.textTertiary, { theme, textTertiary ->
                    ColorStateList(states, intArrayOf(theme, textTertiary))
                })
                .doOnNext { tintList -> attach.imageTintList = tintList }
                .doOnNext { tintList -> send.imageTintList = tintList }
                .subscribe()

        disposables += colors.background
                .subscribe { color -> composeBar.setBackgroundColor(color) }

        disposables += colors.composeBackground
                .subscribe { color -> window.decorView.setBackgroundColor(color) }

        disposables += colors.field
                .subscribe { color -> message.setBackgroundTint(color) }

        window.callback = ComposeWindowCallback(window.callback, this)
    }

    override fun render(state: ComposeState) {
        if (state.hasError) {
            finish()
            return
        }

        toolbarTitle.setVisible(!state.editingMode)
        chips.setVisible(state.editingMode)
        contacts.setVisible(state.contactsVisible)
        composeBar.setVisible(!state.contactsVisible)

        toolbar.menu.findItem(R.id.call)?.run {
            isVisible = !state.editingMode
        }

        toolbar.menu.findItem(R.id.archive)?.run {
            isVisible = !state.editingMode
            setTitle(if (state.archived) R.string.menu_unarchive else R.string.menu_archive)
        }

        toolbar.menu.findItem(R.id.delete)?.run {
            isVisible = !state.editingMode
        }

        if (chipsAdapter.data.isEmpty() && state.selectedContacts.isNotEmpty()) {
            message.showKeyboard()
        }

        if (chipsAdapter.data !== state.selectedContacts) {
            chipsAdapter.data = state.selectedContacts
        }

        if (contactsAdapter.data !== state.contacts) {
            contactsAdapter.data = state.contacts
        }

        if (messageAdapter.data !== state.messages) {
            messageAdapter.updateData(state.messages)
        }

        if (title != state.title) title = state.title
        if (message.text.toString() != state.draft) message.setText(state.draft)

        send.isEnabled = state.canSend
    }

    private fun setupMessagesAdapter() {
        messageAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)

                if (positionStart > 0) {
                    messageAdapter.notifyItemChanged(positionStart - 1)
                }

                // If we're at the bottom, scroll down to show new messages
                val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                if (positionStart >= messageAdapter.itemCount - 1 && lastVisiblePosition == positionStart - 1) {
                    messageList.scrollToPosition(positionStart)
                }
            }
        })

        messageAdapter.longClicks.subscribe { message ->
            AlertDialog.Builder(this)
                    .setItems(R.array.message_options, { _, row ->
                        when (row) {
                            0 -> copyTextIntent.onNext(message)
                            1 -> forwardMessageIntent.onNext(message)
                            2 -> deleteMessageIntent.onNext(message)
                        }
                    })
                    .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.compose, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.call -> callIntent.onNext(Unit)
            R.id.archive -> archiveIntent.onNext(Unit)
            R.id.delete -> deleteIntent.onNext(Unit)
            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

}