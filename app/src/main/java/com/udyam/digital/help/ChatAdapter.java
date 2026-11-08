package com.udyam.digital.help;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.udyam.digital.R;

import java.util.ArrayList;
import java.util.List;

/** Chat bubbles: user on the right, bot and agent notices on the left. */
public final class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.Holder> {

    public static final class Message {
        public final String text;
        public final boolean fromUser;

        public Message(String text, boolean fromUser) {
            this.text = text;
            this.fromUser = fromUser;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final TextView bubble;

        Holder(View v) {
            super(v);
            row = v.findViewById(R.id.row);
            bubble = v.findViewById(R.id.bubble);
        }
    }

    private final List<Message> items = new ArrayList<>();

    public void add(Message m) {
        items.add(m);
        notifyItemInserted(items.size() - 1);
    }

    /** Replace the last bot message (used when the AI rewrite arrives). */
    public void replaceLastBot(String text) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (!items.get(i).fromUser) {
                items.set(i, new Message(text, false));
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void clear() {
        int n = items.size();
        items.clear();
        notifyItemRangeRemoved(0, n);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Message m = items.get(position);
        h.bubble.setText(m.text);
        h.row.setGravity(m.fromUser ? Gravity.END : Gravity.START);
        h.bubble.setBackgroundResource(m.fromUser ? R.drawable.bubble_user : R.drawable.bubble_bot);
        h.bubble.setTextColor(ContextCompat.getColor(h.bubble.getContext(),
                m.fromUser ? R.color.bubble_user_text : R.color.bubble_bot_text));
        h.bubble.setContentDescription((m.fromUser ? "You: " : "Assistant: ") + m.text);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
