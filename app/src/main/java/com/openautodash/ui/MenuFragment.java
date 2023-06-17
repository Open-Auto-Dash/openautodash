package com.openautodash.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.openautodash.R;
import com.openautodash.adapters.MenuAdapter;
import com.openautodash.object.MenuItem;

import java.util.ArrayList;
import java.util.List;

public class MenuFragment extends Fragment implements MenuAdapter.OnMenuItemClickListener {
    private static final String TAG = "MenuFragment";

    //Menu
    private RecyclerView menuRecyclerView;
    private FrameLayout menuContainer;
    private MenuAdapter menuAdapter;

    private List<MenuItem> menuItemList;

    public MenuFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_menu, container, false);

        menuItemList = new ArrayList<>();

        //Menu
        menuRecyclerView = view.findViewById(R.id.rv_menu_items);
        menuContainer =view.findViewById(R.id.layout_menu_content);

        MenuItem menuItem = new MenuItem();

        menuItem.setTitle("Menu");
        menuItem.setIcon(getResources().getDrawable(R.drawable.ic_defrost_front));

        menuItemList.add(menuItem);

        menuAdapter = new MenuAdapter(getContext(), menuItemList);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        menuRecyclerView.setLayoutManager(layoutManager);
        menuRecyclerView.setItemAnimator(new DefaultItemAnimator());
        menuRecyclerView.setAdapter(menuAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onMenuItemClick(int position) {

    }
}
