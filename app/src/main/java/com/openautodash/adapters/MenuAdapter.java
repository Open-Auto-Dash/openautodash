package com.openautodash.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.openautodash.R;
import java.util.List;

public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.MenuItemViewHolder> {

    private Context context;
    private List<MenuItem> menuItems;
    private OnMenuItemClickListener listener;

    public MenuAdapter(Context context, List<MenuItem> menuItems){
        this.context = context;
        this.menuItems = menuItems;
    }

    @NonNull
    @Override
    public MenuItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragement_menu, parent, false);
        return new MenuItemViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull MenuItemViewHolder holder, int position) {
        MenuItem menuItem = menuItems.get(position);
        holder.icon.setImageDrawable(menuItems.get(position).getIcon());
        holder.title.setText(menuItems.get(position).getTitle());
        holder.bind(menuItem);
    }

    @Override
    public int getItemCount() {
        return menuItems.size();
    }

    public class MenuItemViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final ImageView icon;
        private final TextView title;

        public MenuItemViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_menu_item_icon);
            title = itemView.findViewById(R.id.tv_menu_item_name);
            itemView.setOnClickListener(this);
        }

        public void bind(MenuItem menuItem) {
            icon.setImageDrawable(menuItem.getIcon());
            title.setText(menuItem.getTitle());
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION && listener != null) {
                listener.onMenuItemClick(position);
            }
        }
    }

    public interface OnMenuItemClickListener {
        void onMenuItemClick(int position);
    }
}
