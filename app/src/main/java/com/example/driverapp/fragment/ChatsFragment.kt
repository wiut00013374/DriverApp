// fragment/ChatsFragment.kt
package com.example.driverapp.fragment

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.driverapp.R
import com.example.driverapp.adapters.ChatAdapter
import com.example.driverapp.data.Chat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatsFragment : Fragment() {

    companion object {
        private const val ARG_CHAT_ID = "arg_chat_id"

        fun newInstance(chatId: String): ChatsFragment {
            val fragment = ChatsFragment()
            val args = Bundle()
            args.putString(ARG_CHAT_ID, chatId)
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter // You'll need to create this
    private val chatsList = mutableListOf<Chat>()

    private val database by lazy { FirebaseDatabase.getInstance() } // Realtime Database
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chats, container, false) // Make sure you have this layout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewChats) // Make sure your layout has this ID
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ChatAdapter(chatsList) // You'll need to implement the ChatAdapter
        recyclerView.adapter = adapter

        loadChats()
    }
    private fun loadChats() {
        val currentUserId = auth.currentUser?.uid ?: return

        database.getReference("chats")
            .orderByChild("driverUid")  // Assuming you order chats by driverUid
            .equalTo(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    chatsList.clear()
                    for (chatSnapshot in snapshot.children) {
                        val chat = chatSnapshot.getValue(Chat::class.java)
                        chat?.let { chatsList.add(it) }
                    }
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}