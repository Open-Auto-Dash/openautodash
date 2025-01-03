package com.openautodash.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.openautodash.R;
import com.openautodash.object.PlaceSearchResult;

import java.util.ArrayList;
import java.util.List;

public class SearchSuggestionsAdapter extends RecyclerView.Adapter<SearchSuggestionsAdapter.ViewHolder> {
    private List<PlaceSearchResult> suggestions = new ArrayList<>();
    private OnSuggestionClickListener listener;

    public interface OnSuggestionClickListener {
        void onSuggestionClick(PlaceSearchResult suggestion);
    }

    public void setOnSuggestionClickListener(OnSuggestionClickListener listener) {
        this.listener = listener;
    }

    public void updateSuggestions(List<PlaceSearchResult> newSuggestions) {
        suggestions.clear();
        suggestions.addAll(newSuggestions);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlaceSearchResult suggestion = suggestions.get(position);
        holder.primaryText.setText(suggestion.primaryText());
        holder.secondaryText.setText(suggestion.secondaryText());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSuggestionClick(suggestion);
            }
        });
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView primaryText;
        TextView secondaryText;

        ViewHolder(View itemView) {
            super(itemView);
            primaryText = itemView.findViewById(R.id.tv_suggestion_primary);
            secondaryText = itemView.findViewById(R.id.tv_suggestion_secondary);
        }
    }
}