package com.github.tvbox.osc.ui.adapter;

import android.view.ViewGroup;

import com.github.tvbox.osc.base.BaseLazyFragment;

import java.util.List;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

/**
 * @user acer
 * @date 2018/12/4
 */

public class SettingPageAdapter extends FragmentPagerAdapter {
    public FragmentManager fragmentManager;
    public List<BaseLazyFragment> list;

    public SettingPageAdapter(FragmentManager fm) {
        super(fm);
    }

    public SettingPageAdapter(FragmentManager fm, List<BaseLazyFragment> list) {
        super(fm);
        this.fragmentManager = fm;
        this.list = list;
    }

    @Override
    public Fragment getItem(int position) {
        return list.get(position);
    }

    @Override
    public int getCount() {
        return list != null ? list.size() : 0;
    }

    @Override
    public Fragment instantiateItem(ViewGroup container, int position) {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
        fragmentManager.beginTransaction().show(fragment).commitAllowingStateLoss();
        return fragment;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        // super.destroyItem(container, position, object);
        Fragment fragment = list.get(position);
        fragmentManager.beginTransaction().hide(fragment).commitAllowingStateLoss();
    }
}
