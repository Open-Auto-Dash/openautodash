package com.openautodash.object;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;

import com.openautodash.R;
import com.openautodash.ui.menu.MenuControls;
import com.openautodash.ui.menu.MenuDisplay;

import java.util.ArrayList;
import java.util.List;

public class MenuItem {
    private Intent intent;
    private Fragment fragment;
    private Drawable icon;
    private String title;
    private String description;
    private boolean enabled;
    private boolean selected;
    private int checked;

    public MenuItem(){

    }

    public Intent getIntent() {
        return intent;
    }

    public void setIntent(Intent intent) {
        this.intent = intent;
    }

    public Fragment getFragment() {
        return fragment;
    }

    public void setFragment(Fragment fragment) {
        this.fragment = fragment;
    }

    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public int getChecked() {
        return checked;
    }

    public void setChecked(int checked) {
        this.checked = checked;
    }

    public List<MenuItem> initMenu(Context context){
        List<MenuItem> menuItems = new ArrayList<>();
        String[] title = {"Controls", "Display"};
        ArrayList<Drawable> icons = new ArrayList<>();
        ArrayList<Fragment> fragments = new ArrayList<>();

        icons.add(AppCompatResources.getDrawable(context, R.drawable.ic_menu_main_controls));
        fragments.add(new MenuDisplay());
        icons.add(AppCompatResources.getDrawable(context, R.drawable.ic_menu_main_display));
        fragments.add(new MenuControls());

        for(int i = 0; i < title.length; i++){
            MenuItem menuItem = new MenuItem();
            menuItem.setFragment(fragments.get(i));
            menuItem.setTitle(title[i]);
            menuItem.setIcon(icons.get(i));
            menuItems.add(menuItem);
        }
        return menuItems;
    }
}
